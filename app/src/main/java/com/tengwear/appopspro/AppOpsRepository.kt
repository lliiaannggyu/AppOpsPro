package com.tengwear.appopspro

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tengwear.appopspro.model.AppOpInfo
import com.tengwear.appopspro.model.AppOpsCatalog
import com.tengwear.appopspro.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object AppOpsRepository {

    private const val TAG = "AppOpsPro"
    private const val IAPP_OPS_DESCRIPTOR = "com.android.internal.app.IAppOpsService"

    /** 图标解码尺寸（px）。48dp @ xxhdpi 足够手表列表显示，避免无谓内存占用。 */
    private const val ICON_SIZE_PX = 144

    /** 供 [resolveWritable] 查权限保护级别用。由 [init] 注入，未注入时退化为保守判定。 */
    @Volatile
    private var appContext: Context? = null

    /** 在 Application / Activity 启动时注入，只需一次。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        resolveDeviceOpCodes(context.applicationContext)
    }

    /**
     * 运行期把目录里的稳定 opName（字符串）解析为当前设备系统版本上真实的 op 整数编号。
     *
     * ⚠️ opCode 与 Android 版本强绑定：本目录（147 条）是按 API 34 导出的，
     *    旧系统上高位 op 不存在、个别历史编号还会变动（如 activity_recognition 曾为 47，
     *    API 34 起为 79）。若直接拿 API 34 的硬编码 code 去旧系统查询/写入，会命中错误的 op，
     *    表现为「某些权限查不到 / 改不动 / 改到别的 op 上」。
     *
     * 解法：用**稳定的 opName 字符串**（如 `android:camera`）通过反射查本机映射，得到真实 code。
     *    它在本机恒等于目录里的 code（目录本就取自本机 dump），所以：
     *    · 在 API 34（含测试机）上，解析结果与原硬编码完全一致，行为无变化；
     *    · 在更低版本上，自动只保留该版本存在的 op 并用其真实 code，功能不受影响。
     *
     * 之所以走反射而非公开 API：公开 `AppOpsManager` 只暴露「按 op 字符串」的方法
     * （`checkOp(String,…)` / `OPSTR_*`），而我们要喂给 `IAppOpsService.getOpsForPackage` /
     * `setMode` 的是**整数 code**（隐藏方法 `strOpToOp` 才能做字符串→整数转换，且不在公开 SDK）。
     * 反射被隐藏 API 限制挡掉时退回硬编码 code（在 API 34 上仍完全一致），保证不崩。
     */
    @Volatile
    private var deviceCodeByName: Map<String, Int> = emptyMap()

    /** [deviceCodeByName] 的逆映射：系统返回的 op 整数编号 → 目录里的 opName（用于搜索索引）。 */
    @Volatile
    private var nameByDeviceCode: Map<Int, String> = emptyMap()

    /** 当前设备存在的全部 op 整数编号，用于一次性喂给 `getOpsForPackage` / `getPackagesForOps`。 */
    @Volatile
    private var deviceCodes: IntArray = IntArray(0)

    private fun resolveDeviceOpCodes(context: Context) {
        val aom = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val byName = LinkedHashMap<String, Int>()
        var source = "hardcoded"
        if (aom != null) {
            // 优先用隐藏方法 strOpToOp(String)，其次读内部静态映射 sOpStrToOp，
            // 二者都把「稳定 opName 字符串」映射为本机真实 op code。
            val viaMethod = resolveViaStrOpToOp()
            if (viaMethod != null) {
                source = "strOpToOp"
                for (op in AppOpsCatalog.ALL) {
                    val code = viaMethod(op.opName)
                    if (code >= 0) byName[op.opName] = code
                }
            } else {
                val mapped = resolveViaStrToOpMap()
                source = if (mapped.isNotEmpty()) "sOpStrToOp" else "none"
                byName.putAll(mapped)
            }
        }
        // 兜底：反射被隐藏 API 限制挡掉 / 系统服务取不到 → 退回硬编码（API 34 上完全一致）。
        if (byName.isEmpty()) {
            for (op in AppOpsCatalog.ALL) byName[op.opName] = op.code
            source = "hardcoded"
        }
        deviceCodeByName = byName
        nameByDeviceCode = byName.entries.associate { (n, c) -> c to n }
        deviceCodes = byName.values.toIntArray()
        Log.d(TAG, "resolveDeviceOpCodes: source=$source 本机识别 ${byName.size}/${AppOpsCatalog.ALL.size} 条 op")
    }

    /**
     * 通过反射调用隐藏静态方法 `AppOpsManager.strOpToOp(String)`，把 opName 映射为本机 code。
     * 该方法自框架早期即存在，但不在公开 SDK 里，故用反射调用；
     * 被隐藏 API 限制挡掉（抛 NoSuchMethodException）时返回 null，交由 [resolveViaStrToOpMap] 兜底。
     */
    private fun resolveViaStrOpToOp(): ((String) -> Int)? {
        val m = try {
            AppOpsManager::class.java.getMethod("strOpToOp", String::class.java)
        } catch (_: Exception) {
            return null
        }
        return { opName ->
            try {
                (m.invoke(null, opName) as? Int) ?: -1
            } catch (_: Exception) {
                -1
            }
        }
    }

    /**
     * 反射读取 AppOpsManager 内部静态映射 `sOpStrToOp: HashMap<String,Integer>`
     * （opName → 本机 code），作为 [resolveViaStrOpToOp] 失败时的备用来源。
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveViaStrToOpMap(): Map<String, Int> {
        return try {
            val f = AppOpsManager::class.java.getDeclaredField("sOpStrToOp")
            f.isAccessible = true
            (f.get(null) as? HashMap<String, Int>)?.toMap().orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 「一键授予全部」的结果统计。 */
    data class GrantAllResult(
        /** 回读确认写入生效的条数。 */
        val success: Int,
        /** 写入后回读不一致的条数（被系统拒绝 / op 不受支持）。 */
        val failed: Int,
        /** 因受系统保护而直接跳过的条数。 */
        val skipped: Int
    ) {
        val total: Int get() = success + failed + skipped
    }

    // ── transaction code（动态获取，避免硬编码错位）─────

    /**
     * IAppOpsService.getOpsForPackage(int uid, String packageName, int[] ops)
     *     → List<AppOpsManager.PackageOps>
     *
     * 用于读取 **package 级** 的存储模式（`cmd appops get` 展示的 `OP: mode` 那一行）。
     *
     * ⚠️ 不用 `checkOperation`：它返回的是**综合裁决值**
     * （uid 级覆盖 + 运行时权限 + 包级模式一起算），与 `setMode` 写入的存储值语义不对称，
     * 会导致「写 allow 读回 ignore」的假失败（典型：`READ_EXTERNAL_STORAGE`）。
     */
    private val txGetOpsForPackage: Int by lazy {
        try {
            SystemServiceHelper.getTransactionCode(
                "com.android.internal.app.IAppOpsService\$Stub",
                "getOpsForPackage"
            )
        } catch (e: Exception) {
            Log.e(TAG, "get getOpsForPackage code failed", e)
            -1
        }
    }

    /**
     * IAppOpsService.setMode(int code, int uid, String packageName, int mode)
     *
     * 写 **package 级** 模式（`cmd appops set <pkg>` 的行为）。
     * 这是本应用唯一的写入通道。
     *
     * ⚠️ 不使用 `setUidMode`（uid 级）的原因见 [setAppOpMode] 注释：
     * 部分 op 的 uid 级模式是粘性的，改不动且会在裁决时压制包级设置。
     */
    private val txSetMode: Int by lazy {
        try {
            SystemServiceHelper.getTransactionCode(
                "com.android.internal.app.IAppOpsService\$Stub",
                "setMode"
            )
        } catch (e: Exception) {
            Log.e(TAG, "get setMode code failed", e)
            -1
        }
    }

    /**
     * IAppOpsService.getPackagesForOps(int[] ops) → List&lt;AppOpsManager.PackageOps&gt;
     *
     * 一次取回**所有**包在指定 ops 上的记录，用于建立「按权限搜索应用」的索引。
     * 相比逐包 `getOpsForPackage`（152 个包 = 152 次 Binder 往返），这里只要 1 次。
     */
    private val txGetPackagesForOps: Int by lazy {
        try {
            SystemServiceHelper.getTransactionCode(
                "com.android.internal.app.IAppOpsService\$Stub",
                "getPackagesForOps"
            )
        } catch (e: Exception) {
            Log.e(TAG, "get getPackagesForOps code failed", e)
            -1
        }
    }

    // ── 「哪些 op 改不动」的判定 ────────────────────────
    //
    // 背景（Android 14 实测结论，别再用写死的黑名单）：
    //
    //   `IAppOpsService.setMode/setUidMode` 对一部分 op 是**静默空操作** ——
    //   transact 不抛异常、`cmd appops set` 也返回 0，但 `getOpsForPackage` 读回来
    //   仍是 MODE_DEFAULT。实测把 adbd 提到 **root（uid=0）** 后结果完全一样，
    //   因此这**不是权限不足**，也无法靠换写入通道（setUidMode / checkOperation）绕开。
    //
    //   对照实验（同一台 API 34 模拟器）：
    //     · `com.tengwear.ttsbookm3e` 未声明 WRITE_SETTINGS  → 写入后仍 default
    //     · `com.google.android.apps.wearable.systemui` 声明了 WRITE_SETTINGS → 写入成功
    //     · `MANAGE_EXTERNAL_STORAGE` 在声明它的应用上可写、在没声明的应用上不可写
    //   即：**op 能否写入，取决于「目标应用是否在清单里声明了它对应的权限」**。
    //
    //   实测统计（147 条 op 逐条 `set allow` + 回读，目标应用 ttsbookm3e）：
    //     115 条成功、19 条写入被丢弃、11 条连记录都不建。
    //   那 19 条全部满足「有对应权限 且 应用未声明」，与上面的规律完全吻合。
    //
    //   规律的一个例外：由**运行时权限（dangerous）**托底的 op（CAMERA / RECORD_AUDIO /
    //   READ_CONTACTS / COARSE_LOCATION / READ_EXTERNAL_STORAGE …）即使应用没声明也能写入 ——
    //   这类 op 的裁决权交给运行时权限，AppOps 层不设限。故判定时要看权限的保护级别。
    //
    //   综上，[resolveWritable] 用「权限声明情况 + 权限保护级别」推导可写性；
    //   同时保留 [setAppOpMode] 的写后回读校验作为兜底，任何推导错误都会被回读抓出来，
    //   并记入 [unwritableCache]，当次会话内不再重复尝试。

    /** 运行期探测到的「写不进去」条目，key = `packageName#opCode`。 */
    private val unwritableCache: MutableSet<String> =
        java.util.Collections.synchronizedSet(HashSet())

    /** 权限保护级别缓存（`permissionName → 是否 dangerous`）。 */
    private val runtimePermissionCache = HashMap<String, Boolean>()

    private fun unwritableKey(packageName: String, opCode: Int) = "$packageName#$opCode"

    /**
     * 该权限是否是**运行时权限**（dangerous）。
     *
     * 运行时权限托底的 op 由权限系统裁决，AppOps 层不限制写入 —— 实测 CAMERA 等
     * 在未声明的应用上也能成功 setMode，与 WRITE_SETTINGS 这类特殊访问权限行为相反。
     */
    private fun isRuntimePermission(permission: String): Boolean {
        val pm = appContext?.packageManager ?: return false
        synchronized(runtimePermissionCache) {
            runtimePermissionCache[permission]?.let { return it }
        }
        val result = try {
            val info = pm.getPermissionInfo(permission, 0)
            // PROTECTION_MASK_BASE = 0x0f 是 @hide 常量，这里用字面量；
            // 必须先掩掉 flag 位，否则 SIGNATURE_OR_SYSTEM(=3) 会被误判成 dangerous(=1)。
            (info.protectionLevel and 0x0f) == PermissionInfo.PROTECTION_DANGEROUS
        } catch (e: Exception) {
            false
        }
        synchronized(runtimePermissionCache) { runtimePermissionCache[permission] = result }
        return result
    }

    /**
     * 推导某个 op 能否写入系统。
     *
     * @param permission 该 op 对应的权限；null 表示非权限类 op
     * @param declared 目标应用是否声明了 [permission]
     */
    private fun resolveWritable(
        permission: String?,
        declared: Boolean,
        packageName: String,
        opCode: Int
    ): Boolean {
        // 运行期已经确认写不进去的，本次会话内直接否定
        if (unwritableKey(packageName, opCode) in unwritableCache) return false
        // 非权限类 op（音频音量 / 后台运行 / 剪贴板 …）不受权限约束，始终可写
        if (permission == null) return true
        // 应用声明了权限 → 可写
        if (declared) return true
        // 运行时权限托底 → 未声明也可写
        if (isRuntimePermission(permission)) return true
        // 特殊访问类权限且未声明 → 服务端必然丢弃写入
        return false
    }

    /** 读取目标应用在清单里声明的权限集合。 */
    private fun declaredPermissionsOf(packageName: String): Set<String> {
        val pm = appContext?.packageManager ?: return emptySet()
        return try {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "getDeclaredPermissions failed: $packageName", e)
            emptySet()
        }
    }

    // ── 应用列表权限检查 ───────────────────────────────

    /**
     * 检查应用列表权限是否正常。
     * 如果 getInstalledPackages 只返回自己（数量 ≤ 1），说明权限被限制了。
     */
    fun hasAppListPermission(context: Context): Boolean {
        return try {
            val packages = context.packageManager.getInstalledPackages(0)
            packages.size > 1
        } catch (e: Exception) {
            Log.e(TAG, "hasAppListPermission check failed", e)
            false
        }
    }

    // ── 已安装应用列表（本地 PackageManager）─────────

    suspend fun getInstalledApps(context: Context): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = try {
                // ⚠️ 必须带 MATCH_DISABLED_COMPONENTS：被 `pm disable-user` 冻结的应用
                // 默认不会出现在查询结果里，那样用户就再也解冻不了它了。
                pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
            } catch (e: Exception) {
                Log.e(TAG, "getInstalledPackages failed", e)
                emptyList()
            }
            Log.d(TAG, "Installed apps count: ${packages.size}")

            packages
                .filter { it.applicationInfo != null }
                .mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    if (pkg.packageName == context.packageName) return@mapNotNull null

                    val label = try {
                        appInfo.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }
                    val isSystem =
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    InstalledApp(
                        packageName = pkg.packageName,
                        appLabel = label,
                        uid = appInfo.uid,
                        isSystem = isSystem,
                        isEnabled = appInfo.enabled,
                        icon = loadAppIcon(pm, appInfo)
                    )
                }
                .sortedBy { it.appLabel.lowercase() }
        }

    /**
     * 把应用图标解码为 [ImageBitmap]。
     *
     * 这里在 IO 线程同步解码，而不是把 Drawable 传出去延迟到 UI 线程加载 ——
     * 手表端应用数量有限（通常几十个），一次性解码可避免滚动时逐帧抖动。
     * 解码失败不影响应用条目本身，返回 null 由 UI 回退到占位图标。
     */
    private fun loadAppIcon(
        pm: PackageManager,
        appInfo: ApplicationInfo
    ): ImageBitmap? = try {
        val drawable = appInfo.loadIcon(pm)
        val bitmap = Bitmap.createBitmap(
            ICON_SIZE_PX,
            ICON_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        Log.w(TAG, "loadIcon failed for ${appInfo.packageName}", e)
        null
    }

    // ── appops 系统服务 Binder ─────────────────────────

    private fun getAppOpsBinder(): IBinder? {
        return try {
            val raw = SystemServiceHelper.getSystemService("appops") ?: run {
                Log.e(TAG, "appops service is null")
                return null
            }
            ShizukuBinderWrapper(raw)
        } catch (e: Exception) {
            Log.e(TAG, "getAppOpsBinder failed", e)
            null
        }
    }

    // ── 查询某个应用的 AppOps 列表 ─────────────────────

    /**
     * 读取指定应用在**全量目录**（[AppOpsCatalog.ALL]，147 条）下的权限状态。
     *
     * 系统里没有记录的 op 也会返回，`mode` 取 [AppOpInfo.MODE_DEFAULT] 且 `isPresent = false` ——
     * 这样用户才能对「从未被设置过」的权限（例如 `android:manage_external_storage`）也执行授权，
     * 否则这些 op 会永远不出现在界面上、也就永远改不了。
     *
     * 一次 `getOpsForPackage` 取回全部，比逐个 `checkOperation` 快得多，且是 pkg 级存储值。
     *
     * 同时为每条 op 计算 [AppOpInfo.relevant]（是否属于「该应用需要的权限」）与
     * [AppOpInfo.writable]（能否真正写入），供 UI 过滤与置灰。
     */
    suspend fun getAppOpsForPackage(
        uid: Int,
        packageName: String
    ): List<AppOpInfo> = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isReady()) {
            Log.w(TAG, "Shizuku not ready")
            return@withContext emptyList()
        }
        val binder = getAppOpsBinder() ?: return@withContext emptyList()

        val modes = queryPackageOpModes(binder, uid, packageName, deviceCodes)
        val declared = declaredPermissionsOf(packageName)

        // 只遍历本机真实存在的 op（deviceCodeByName 已按系统版本过滤），
        // opCode 一律用「本机解析出的真实编号」，保证查询/写入都命中正确的 op。
        val results = AppOpsCatalog.ALL.mapNotNull { op ->
            val devCode = deviceCodeByName[op.opName] ?: return@mapNotNull null
            val stored = modes[devCode]
            val isDeclared = op.permission != null && op.permission in declared
            AppOpInfo(
                opName = op.opName,
                opCode = devCode,
                label = op.label,
                mode = stored ?: AppOpInfo.MODE_DEFAULT,
                group = op.group,
                isPresent = stored != null,
                permission = op.permission,
                permissionDeclared = isDeclared,
                writable = resolveWritable(op.permission, isDeclared, packageName, devCode)
            )
        }
        Log.d(
            TAG,
            "AppOps for $packageName: 全量=${results.size} 有记录=${modes.size} " +
                    "已声明权限=${declared.size} " +
                    "相关=${results.count { it.relevant }} " +
                    "可写=${results.count { it.writable }}"
        )
        results
    }

    /**
     * 批量读取多个应用的 op 记录，用于「按权限搜索应用」建立索引。
     *
     * 首选 [queryAllPackagesOpModes]（单次 `getPackagesForOps`）：
     * 逐包 `getOpsForPackage` 在本机 152 个应用上实测要 ~19s（每次往返约 125ms），
     * 单次调用则可压到毫秒级。若单次调用因返回体过大触发 Binder 事务上限，
     * 会自动回退到逐包模式（结果一致，只是慢）。
     *
     * @return `packageName → 该应用在系统里有记录的 opCode 集合`
     */
    suspend fun getOpCodesByPackage(
        apps: List<InstalledApp>
    ): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isReady()) return@withContext emptyMap()
        val binder = getAppOpsBinder() ?: return@withContext emptyMap()

        val started = System.currentTimeMillis()
        val bulk = queryAllPackagesOpModes(binder, deviceCodes)
        if (bulk.isNotEmpty()) {
            Log.d(
                TAG,
                "权限索引建立完成（单次调用）：${bulk.size} 个应用有记录，" +
                        "耗时 ${System.currentTimeMillis() - started}ms"
            )
            return@withContext bulk
        }

        // ── 回退：逐包查询 ────────────────────────────
        // 索引存 opName（跨版本稳定）而非 code（跨版本会变），保证搜索在不同系统上都对得准。
        val index = HashMap<String, Set<String>>(apps.size)
        for (app in apps) {
            val modes = queryPackageOpModes(
                binder, app.uid, app.packageName, deviceCodes
            )
            if (modes.isNotEmpty()) {
                index[app.packageName] = modes.keys.mapNotNull { nameByDeviceCode[it] }.toSet()
            }
        }
        Log.d(
            TAG,
            "权限索引建立完成（逐包回退）：${index.size}/${apps.size} 个应用有记录，" +
                    "耗时 ${System.currentTimeMillis() - started}ms"
        )
        index
    }

    /**
     * IAppOpsService.getPackagesForOps(int[] ops)
     *
     * reply 结构与 [queryPackageOpModes] 完全相同（`List&lt;PackageOps&gt;`），
     * 只是每项的 `mPackageName` 不再恒等于入参，需要真正读出来。
     *
     * @return `packageName → opCode 集合`；失败返回空 Map，由调用方回退到逐包查询。
     */
    private fun queryAllPackagesOpModes(
        binder: IBinder,
        opCodes: IntArray
    ): Map<String, Set<String>> {
        if (txGetPackagesForOps < 0) return emptyMap()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IAPP_OPS_DESCRIPTOR)
            data.writeIntArray(opCodes)
            binder.transact(txGetPackagesForOps, data, reply, 0)
            reply.readException()

            // 索引存 opName（跨版本稳定），故每读到一个系统返回的整数 code，
            // 用 nameByDeviceCode 反查成 opName；查不到（本机不存在的 op）直接丢弃。
            val out = HashMap<String, MutableSet<String>>()
            val pkgCount = reply.readInt()
            if (pkgCount > 0) {
                repeat(pkgCount) {
                    val present = reply.readInt()   // writeTypedObject 的存在标记
                    if (present == 0) return@repeat
                    val pkgName = reply.readString()
                    reply.readInt()                 // mUid
                    val entryCount = reply.readInt()
                    if (pkgName != null) {
                        val set = out.getOrPut(pkgName) { HashSet(entryCount) }
                        repeat(entryCount) {
                            val op = reply.readInt()
                            reply.readInt()         // mode（索引只关心「有没有记录」）
                            reply.readMap(LinkedHashMap<Any, Any>(), null)
                            nameByDeviceCode[op]?.let { set.add(it) }
                        }
                    } else {
                        // 包名为 null 时仍需消费掉条目，否则后续解析错位
                        repeat(entryCount) {
                            reply.readInt()
                            reply.readInt()
                            reply.readMap(LinkedHashMap<Any, Any>(), null)
                        }
                    }
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "queryAllPackagesOpModes failed, 将回退到逐包查询", e)
            emptyMap()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * IAppOpsService.getOpsForPackage(int uid, String packageName, int[] ops)
     *     → List&lt;AppOpsManager.PackageOps&gt;
     *
     * 读取 **package 级** 的存储模式，返回 `opCode → mode` 映射。
     *
     * ⚠️ 为什么不用 `checkOperation`：
     * `checkOperation` 返回的是**综合裁决值**（uid 级覆盖 + 运行时权限 + 包级模式一起算），
     * 而 `setMode` 写的是**包级存储值**。两者语义不对称会导致「写 allow 读回 ignore」的
     * 假失败 —— 典型例子是 `READ_EXTERNAL_STORAGE`：应用通过系统授予的运行时权限会在
     * uid 级留下 `ignore`，`checkOperation` 因此恒返回 ignore，但 pkg 级存储值其实是 allow。
     *
     * 这里改为读存储值，与写入（`setMode`）**语义对称**，也和 `cmd appops get` 展示的一致。
     */
    private fun queryPackageOpModes(
        binder: IBinder,
        uid: Int,
        packageName: String,
        opCodes: IntArray
    ): Map<Int, Int> {
        if (txGetOpsForPackage < 0) return emptyMap()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IAPP_OPS_DESCRIPTOR)
            data.writeInt(uid)
            data.writeString(packageName)
            data.writeIntArray(opCodes)
            binder.transact(txGetOpsForPackage, data, reply, 0)
            reply.readException()

            // ── reply 结构（已用 dexdump 反汇编 framework.jar 逐条核对）─────────────
            //
            //   [status]              ← writeNoException()，由 readException() 消费
            //   [count N]             ← Parcel.writeTypedList()：writeInt(N)；null 列表写 -1
            //   重复 N 次：
            //     [present=1]         ←   writeTypedObject() 每项先写「存在标记」（null 写 0）
            //     [PackageOps]        ←   PackageOps.writeToParcel()
            //        writeString(mPackageName)
            //        writeInt(mUid)
            //        writeInt(mEntries.size()) = M
            //        重复 M 次：
            //          writeInt(mOp)                    ← OpEntry 直接写，无存在标记
            //          writeInt(mMode)
            //          writeMap(mAttributedOpEntries)   ← 必须消费，否则后续解析错位
            //
            // ⚠️ 关键坑：writeTypedList 内部走 writeTypedObject，**每项多一个 int 存在标记**。
            // 早期实现漏了这个标记，把字符串长度当成 entryCount，读出 3014765 这种垃圾值。
            // 权威依据：framework 里 IAppOpsService$Stub$Proxy.getOpsForPackage（transaction=19）
            // 用 `Parcel.createTypedArrayList(PackageOps.CREATOR)` 读 reply，与之严格对应。
            //
            // ⚠️ 之所以手工逐字段解析：`PackageOps`/`OpEntry` 是 @hide 嵌套类，SDK 里不存在；
            // dev.rikka.hidden:stub 的存根又缺 `CREATOR`（只在真机上才有），编译期无法用
            // CREATOR 反序列化。故按上面的真实字段序手写读取。
            val result = mutableMapOf<Int, Int>()
            val pkgCount = reply.readInt()
            if (pkgCount > 0) {
                repeat(pkgCount) {
                    val present = reply.readInt()   // writeTypedObject 的存在标记
                    if (present == 0) return@repeat
                    reply.readString()              // mPackageName（按包名查询，恒等于入参）
                    reply.readInt()                 // mUid
                    val entryCount = reply.readInt()
                    repeat(entryCount) {
                        val op = reply.readInt()    // OpEntry.mOp
                        val mode = reply.readInt()  // OpEntry.mMode
                        // OpEntry 尾部还有个 Map（mAttributedOpEntries），必须消费掉，
                        // 否则会打乱后续条目的读取位置。
                        // readMap 需要一个非 null 的 Map 实例作为写入目标，读出来即可丢弃。
                        reply.readMap(LinkedHashMap<Any, Any>(), null)
                        result[op] = mode
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "queryPackageOpModes failed: uid=$uid pkg=$packageName", e)
            emptyMap()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * 读取单个 op 的 **package 级存储模式**（供写入后的回读校验使用）。
     * @return 0..3，读不到返回 -1
     */
    private fun queryPackageOpMode(
        binder: IBinder,
        opCode: Int,
        uid: Int,
        packageName: String
    ): Int = queryPackageOpModes(binder, uid, packageName, intArrayOf(opCode))[opCode] ?: -1

    // ── 修改某个 AppOp 的 mode ─────────────────────────

    /**
     * 写入某个 AppOp 的 mode，并**回读 package 级存储值**校验。
     *
     * ## 只写 package 级（`setMode`），为什么不写 uid 级
     *
     * 早期实现曾同时写 uid 级（`setUidMode`）与 package 级，实测发现**有害**：
     * - 部分 op 的 uid 级模式是**粘性**的（例如 `READ_EXTERNAL_STORAGE`，
     *   被系统/权限框架设为 ignore 后，连 `setUidMode(allow)` 和 `cmd appops reset`
     *   都改不回来）；
     * - 该残留的 uid 级 ignore 会在 `checkOperation` 裁决时**压制** package 级设置，
     *   导致「明明写入成功却读回旧值」的假失败。
     *
     * 而 package 级（`setMode`）读写对称、可自由增删改，且正是 `cmd appops set <pkg>`
     * 的行为。因此这里统一走 package 级单通道。
     *
     * ## 回读为什么读存储值而不是 `checkOperation`
     *
     * `checkOperation` 返回**综合裁决值**（uid 级覆盖 + 运行时权限 + 包级模式），
     * 与 `setMode` 写入的**存储值**语义不对称，会造成假失败。
     * 这里用 [queryPackageOpMode] 读存储值，与写入语义对称。
     *
     * @return true 仅当回读的存储值等于期望的 [mode]。
     */
    suspend fun setAppOpMode(
        opCode: Int,
        uid: Int,
        packageName: String,
        mode: Int,
        opName: String? = null,
        writable: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isReady()) return@withContext false
        val binder = getAppOpsBinder() ?: return@withContext false

        // 预判不可写（特殊访问类权限未声明）：服务端必然丢弃，直接返回失败，
        // 省去一次无意义的 binder 往返，也让 UI 能立刻给出「为什么改不了」。
        if (!writable) {
            Log.w(TAG, "setAppOpMode skipped (not writable): op=$opName pkg=$packageName")
            return@withContext false
        }

        val ok = writeMode(binder, txSetMode, opCode, uid, packageName, mode, "setMode")
        if (!ok) {
            Log.e(TAG, "setAppOpMode call failed: op=$opCode uid=$uid pkg=$packageName")
            return@withContext false
        }

        // 回读校验：读 package 级存储值（与写入语义对称）。
        // 这也是可写性推导的兜底 —— 推导错了会被这里抓出来并记入负缓存。
        val actual = queryPackageOpMode(binder, opCode, uid, packageName)
        if (actual != mode) {
            Log.w(
                TAG,
                "setAppOpMode not effective: op=$opCode uid=$uid pkg=$packageName " +
                        "expected=$mode actual=$actual —— 记入不可写缓存"
            )
            unwritableCache.add(unwritableKey(packageName, opCode))
            return@withContext false
        }
        true
    }

    /**
     * 一键把给定应用的全部 op 置为「允许」。
     *
     * 性能取舍：**逐条写、一次性读**。
     * 若沿用 [setAppOpMode] 的「写一条读一条」，147 条会产生 294 次 Binder 往返；
     * 这里写完后只做 **一次** `getOpsForPackage` 回读全部，往返降到 148 次。
     *
     * 回读仍然读 **package 级存储值**（与写入语义对称），只把回读结果等于
     * [AppOpInfo.MODE_ALLOWED] 的条目计为成功 —— 保证「界面显示成功」等价于「系统里真的改了」。
     *
     * [AppOpInfo.writable] 为 false 的条目**直接跳过**并计入 `skipped`，
     * 不参与写入也不计入 `failed` —— 它们本来就改不动，算作失败会误导用户。
     */
    suspend fun grantAllAppOpModes(
        uid: Int,
        packageName: String,
        ops: List<AppOpInfo>
    ): GrantAllResult = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isReady()) {
            return@withContext GrantAllResult(0, 0, ops.size)
        }
        val binder = getAppOpsBinder() ?: return@withContext GrantAllResult(0, 0, ops.size)

        var skipped = 0
        val attempted = ArrayList<Int>(ops.size)
        for (op in ops) {
            // 预判不可写的直接跳过（省掉必然被丢弃的 binder 往返），
            // 并在结果里如实计入 skipped，而不是让它混进 failed 里当「写入失败」。
            if (!op.writable) {
                skipped++
                continue
            }
            writeMode(
                binder, txSetMode, op.opCode, uid, packageName,
                AppOpInfo.MODE_ALLOWED, "grantAll"
            )
            attempted.add(op.opCode)
        }

        val stored = queryPackageOpModes(binder, uid, packageName, attempted.toIntArray())
        var success = 0
        for (code in attempted) {
            if (stored[code] == AppOpInfo.MODE_ALLOWED) {
                success++
            } else {
                // 推导为「可写」但实际被丢弃 → 记入负缓存，下次直接判为不可写
                unwritableCache.add(unwritableKey(packageName, code))
            }
        }
        val result = GrantAllResult(
            success = success,
            failed = attempted.size - success,
            skipped = skipped
        )
        Log.d(
            TAG,
            "grantAllAppOpModes pkg=$packageName " +
                    "success=${result.success} failed=${result.failed} skipped=${result.skipped}"
        )
        result
    }

    /**
     * 单次 package 级模式写入（`setMode`）。
     *
     * @return 调用是否被服务端接受（不含回读校验）
     */
    private fun writeMode(
        binder: IBinder,
        txCode: Int,
        opCode: Int,
        uid: Int,
        packageName: String,
        mode: Int,
        tag: String
    ): Boolean {
        if (txCode < 0) return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IAPP_OPS_DESCRIPTOR)
            data.writeInt(opCode)
            data.writeInt(uid)
            data.writeString(packageName)
            data.writeInt(mode)
            binder.transact(txCode, data, reply, 0)
            reply.readException()
            true
        } catch (e: Exception) {
            Log.w(TAG, "$tag call failed: op=$opCode uid=$uid pkg=$packageName mode=$mode", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
