package com.tengwear.appopspro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.tengwear.appopspro.model.AppOpInfo
import com.tengwear.appopspro.model.InstalledApp
import com.tengwear.appopspro.ui.AppListPermissionDialog
import com.tengwear.appopspro.ui.AboutScreen
import com.tengwear.appopspro.ui.AppListScreen
import com.tengwear.appopspro.ui.AppSearchScreen
import com.tengwear.appopspro.ui.AppStatsScreen
import com.tengwear.appopspro.ui.FreezeScreen
import com.tengwear.appopspro.ui.LogScreen
import com.tengwear.appopspro.ui.ResolutionScreen
import com.tengwear.appopspro.ui.PermissionScreen
import com.tengwear.appopspro.ui.ShellScreen
import com.tengwear.appopspro.ui.ShizukuPermissionDialog
import com.tengwear.appopspro.ui.theme.AppOpsProTheme
import kotlinx.coroutines.launch

private const val ROUTE_APP_LIST = "app_list"
private const val ROUTE_APP_SEARCH = "app_search"
private const val ROUTE_PERMISSIONS = "permissions/{packageName}"
private const val ROUTE_SHELL = "shell"
private const val ROUTE_FREEZE = "freeze"
private const val ROUTE_RESOLUTION = "resolution"
private const val ROUTE_LOGS = "logs"
private const val ROUTE_STATS = "stats"
private const val ROUTE_ABOUT = "about"

/** 路由参数名。 */
private const val ARG_PACKAGE = "packageName"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuManager.init()
        // 注入 applicationContext：AppOpsRepository 需要 PackageManager 来读
        // 「目标应用声明了哪些权限」和「权限的保护级别」，据此推导每个 op 能否写入。
        AppOpsRepository.init(applicationContext)
        // 搜索历史落盘（SharedPreferences）
        SearchHistoryStore.init(applicationContext)
        // 日志过滤词历史落盘
        LogFilterHistoryStore.init(applicationContext)
        // 首次启动标记（用于自动展开关于页）
        UserPrefs.init(applicationContext)
        // 主题种子色（关于页可选，落盘）
        ThemePrefs.init(applicationContext)

        setContent {
            AppOpsProTheme {
                AppScaffold {
                    AppOpsProApp()
                }
            }
        }
    }

    override fun onDestroy() {
        ShizukuManager.destroy()
        super.onDestroy()
    }
}

@Composable
private fun AppOpsProApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Shizuku 状态 ───────────────────────────────────
    var binderAvailable by remember { mutableStateOf(ShizukuManager.isBinderAvailable) }
    var permissionGranted by remember { mutableStateOf(ShizukuManager.isPermissionGranted) }
    var shizukuReady by remember { mutableStateOf(ShizukuManager.isReady()) }

    var showPermissionDialog by remember {
        mutableStateOf(!ShizukuManager.isReady())
    }

    LaunchedEffect(Unit) {
        ShizukuManager.onPermissionStateChanged = { granted ->
            permissionGranted = granted
            binderAvailable = ShizukuManager.isBinderAvailable
            shizukuReady = granted && binderAvailable
            if (shizukuReady) showPermissionDialog = false
        }
        binderAvailable = ShizukuManager.isBinderAvailable
        permissionGranted = ShizukuManager.isPermissionGranted
        shizukuReady = ShizukuManager.isReady()
    }

    // ── 应用列表权限状态 ───────────────────────────────
    var showAppListDialog by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableStateOf(0) }

    // ── 数据状态 ───────────────────────────────────────
    val installedApps = remember { mutableStateListOf<InstalledApp>() }
    val currentAppOps = remember { mutableStateListOf<AppOpInfo>() }
    var isLoadingApps by remember { mutableStateOf(false) }
    var isLoadingOps by remember { mutableStateOf(false) }
    var isGrantingAll by remember { mutableStateOf(false) }

    // 命令行页的输出日志。放在这里而不是页面内部 —— 返回上级再进来不会丢历史输出。
    val shellLog = remember { mutableStateListOf<ShizukuShell.Result>() }
    var isRunningCommand by remember { mutableStateOf(false) }

    // 应用冻结操作进行中
    var isFreezing by remember { mutableStateOf(false) }

    // 权限索引：`packageName → 该应用有记录的 opCode 集合`，供搜索页按权限检索。
    // 惰性构建（首次进入搜索页时），避免拖慢冷启动。
    var opIndex by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var isIndexLoading by remember { mutableStateOf(false) }

    // 当前正在查看权限的应用（由路由参数解析得到）
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }

    // 注意：必须用 Wear 提供的 rememberSwipeDismissableNavController()，
    // 它会注册 WearNavigator。若用 androidx.navigation 的 rememberNavController()，
    // 会抛 "Could not find Navigator with name wear-navigator" 而崩溃。
    val navController = rememberSwipeDismissableNavController()

    // 加载已安装应用 + 权限检查
    LaunchedEffect(shizukuReady, reloadTrigger) {
        if (!shizukuReady) return@LaunchedEffect
        isLoadingApps = true
        try {
            // ★ 检查应用列表权限
            if (!AppOpsRepository.hasAppListPermission(context)) {
                showAppListDialog = true
                installedApps.clear()
                return@LaunchedEffect
            }

            val apps = AppOpsRepository.getInstalledApps(context.applicationContext)
            installedApps.clear()
            installedApps.addAll(apps)
            // 应用列表变化后权限索引失效，下次进搜索页重新构建
            opIndex = emptyMap()

            if (apps.isEmpty()) {
                showAppListDialog = true
            }
        } catch (_: Exception) {
        } finally {
            isLoadingApps = false
        }
    }

    // 加载选中应用的 AppOps
    LaunchedEffect(selectedApp) {
        val app = selectedApp ?: return@LaunchedEffect
        isLoadingOps = true
        currentAppOps.clear()
        try {
            val ops = AppOpsRepository.getAppOpsForPackage(app.uid, app.packageName)
            currentAppOps.addAll(ops)
        } catch (_: Exception) {
        } finally {
            isLoadingOps = false
        }
    }

    /** 惰性构建权限索引（搜索页按权限检索所需）。 */
    fun ensureOpIndex() {
        if (opIndex.isNotEmpty() || isIndexLoading || installedApps.isEmpty()) return
        scope.launch {
            isIndexLoading = true
            try {
                opIndex = AppOpsRepository.getOpCodesByPackage(installedApps.toList())
            } catch (_: Exception) {
            } finally {
                isIndexLoading = false
            }
        }
    }

    // ── 导航 ───────────────────────────────────────────
    //
    // 用 SwipeDismissableNavHost 而不是手写 if/else 切换状态：
    // 手写导航下，系统返回手势（右滑）等同于「退出 Activity」，所以在二级页面右滑会直接退出应用。
    // 交给 NavHost 托管后，右滑 = popBackStack，即「返回上一级」，
    // 只有在起始页右滑才会退出，这才是 Android 的标准层级行为。
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = ROUTE_APP_LIST,
        // 起始页不允许右滑返回，避免滑一下把应用关了
        userSwipeEnabled = false
    ) {
        composable(ROUTE_APP_LIST) {
            AppListScreen(
                apps = installedApps,
                isLoading = isLoadingApps,
                shizukuReady = shizukuReady,
                onAppClick = { app ->
                    navController.navigate("permissions/${Uri.encode(app.packageName)}")
                },
                onRetryShizuku = { showPermissionDialog = true },
                onOpenSearch = {
                    ensureOpIndex()
                    navController.navigate(ROUTE_APP_SEARCH)
                },
                onOpenShell = { navController.navigate(ROUTE_SHELL) },
                onOpenFreeze = { navController.navigate(ROUTE_FREEZE) },
                onOpenResolution = { navController.navigate(ROUTE_RESOLUTION) },
                onOpenLogs = { navController.navigate(ROUTE_LOGS) },
                onOpenStats = { navController.navigate(ROUTE_STATS) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) }
            )
        }

        composable(ROUTE_APP_SEARCH) {
            // 从深层返回或进程恢复时索引可能还没建好，这里兜底再触发一次
            LaunchedEffect(Unit) { ensureOpIndex() }

            AppSearchScreen(
                apps = installedApps,
                opIndex = opIndex,
                isIndexLoading = isIndexLoading,
                onAppClick = { app ->
                    navController.navigate("permissions/${Uri.encode(app.packageName)}")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SHELL) {
            ShellScreen(
                log = shellLog,
                isRunning = isRunningCommand,
                isShizukuReady = shizukuReady,
                onRun = { command ->
                    if (!isRunningCommand) scope.launch {
                        isRunningCommand = true
                        try {
                            // ShizukuShell.run 内部切到 IO 线程，不会阻塞界面
                            shellLog.add(ShizukuShell.run(command))
                        } finally {
                            isRunningCommand = false
                        }
                    }
                },
                onClearLog = { shellLog.clear() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_FREEZE) {
            FreezeScreen(
                apps = installedApps,
                isLoading = isLoadingApps,
                isWorking = isFreezing,
                onToggleFreeze = { app, frozen ->
                    if (!isFreezing) scope.launch {
                        isFreezing = true
                        try {
                            val result = AppFreezeRepository.setFrozen(
                                context = context.applicationContext,
                                packageName = app.packageName,
                                selfPackage = context.packageName,
                                frozen = frozen
                            )
                            if (result.success) {
                                // 只更新这一条，不整表重载 —— 153 个应用连图标重新解码要 1~2s
                                val idx = installedApps.indexOfFirst {
                                    it.packageName == app.packageName
                                }
                                if (idx >= 0) {
                                    installedApps[idx] =
                                        installedApps[idx].copy(isEnabled = !frozen)
                                }
                            }
                            Toast.makeText(
                                context,
                                "${app.appLabel}：${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isFreezing = false
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_RESOLUTION) {
            ResolutionScreen(
                isShizukuReady = shizukuReady,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_LOGS) {
            LogScreen(
                apps = installedApps,
                isShizukuReady = shizukuReady,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_STATS) {
            AppStatsScreen(
                apps = installedApps,
                isShizukuReady = shizukuReady,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_PERMISSIONS) { backStackEntry ->
            val pkg = backStackEntry.arguments?.getString(ARG_PACKAGE).orEmpty()
            val app = installedApps.firstOrNull { it.packageName == pkg }

            // 路由参数变化时同步 selectedApp，触发 AppOps 加载
            LaunchedEffect(pkg) {
                selectedApp = app
            }

            if (app == null) {
                // 应用列表尚未加载完成或该包已卸载：让导航回退，避免停在空白页
                LaunchedEffect(pkg) {
                    navController.popBackStack()
                }
            } else {
                PermissionScreen(
                    appLabel = app.appLabel,
                    packageName = app.packageName,
                    appOps = currentAppOps,
                    isLoading = isLoadingOps,
                    isGranting = isGrantingAll,
                    onToggle = { appOp, allowed ->
                        val newMode =
                            if (allowed) AppOpInfo.MODE_ALLOWED else AppOpInfo.MODE_IGNORED
                        scope.launch {
                            val success = AppOpsRepository.setAppOpMode(
                                opCode = appOp.opCode,
                                uid = app.uid,
                                packageName = app.packageName,
                                mode = newMode,
                                opName = appOp.opName,
                                writable = appOp.writable
                            )
                            if (success) {
                                val idx = currentAppOps.indexOfFirst { it.opCode == appOp.opCode }
                                if (idx >= 0) currentAppOps[idx] = appOp.copy(mode = newMode)
                            } else {
                                // 写入未生效。分两种原因给出明确提示，避免「点了没反应」
                                // 被误认为界面卡死：
                                //  · 预判不可写（特殊访问类权限且应用未声明）
                                //  · 预判可写但被系统拒绝（回读不一致）
                                val reason = if (!appOp.writable) {
                                    "「${appOp.label}」由系统保护，无法修改"
                                } else {
                                    "「${appOp.label}」设置失败：系统拒绝写入"
                                }
                                Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onGrantAll = { targets ->
                        if (!isGrantingAll && targets.isNotEmpty()) scope.launch {
                            isGrantingAll = true
                            try {
                                val result = AppOpsRepository.grantAllAppOpModes(
                                    uid = app.uid,
                                    packageName = app.packageName,
                                    ops = targets
                                )
                                // 写入后整体重载：UI 状态一律以系统真实存储值为准，
                                // 不在本地「假装成功」（部分 op 会被服务端静默丢弃）。
                                val reloaded = AppOpsRepository.getAppOpsForPackage(
                                    app.uid, app.packageName
                                )
                                if (reloaded.isNotEmpty()) {
                                    currentAppOps.clear()
                                    currentAppOps.addAll(reloaded)
                                }
                                Toast.makeText(
                                    context,
                                    "成功 ${result.success} 项 · 失败 ${result.failed} · " +
                                            "跳过 ${result.skipped}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isGrantingAll = false
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    // ── Shizuku 权限弹窗 ───────────────────────────────
    ShizukuPermissionDialog(
        visible = showPermissionDialog && !shizukuReady,
        binderAvailable = binderAvailable,
        permissionGranted = permissionGranted,
        onConfirm = {
            if (!ShizukuManager.isBinderAvailable) {
                ShizukuManager.destroy()
                ShizukuManager.init()
                binderAvailable = ShizukuManager.isBinderAvailable
                permissionGranted = ShizukuManager.isPermissionGranted
                shizukuReady = ShizukuManager.isReady()
                if (shizukuReady) showPermissionDialog = false
            } else if (!ShizukuManager.isPermissionGranted) {
                ShizukuManager.requestPermission()
            } else {
                showPermissionDialog = false
            }
        },
        onDismiss = { showPermissionDialog = false }
    )

    // ── 应用列表权限引导弹窗 ───────────────────────────
    AppListPermissionDialog(
        visible = showAppListDialog,
        onOpenSettings = {
            showAppListDialog = false
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        },
        onRetry = {
            showAppListDialog = false
            reloadTrigger++
        },
        onDismiss = { showAppListDialog = false }
    )
}
