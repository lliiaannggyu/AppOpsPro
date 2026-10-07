package com.tengwear.appopspro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.appopspro.model.AppOpInfo
import com.tengwear.appopspro.model.AppOpsCatalog

/**
 * 单个应用的权限列表页（Wear M3E）。
 *
 * ## 默认只显示「该应用需要的权限」
 *
 * 全量 147 条 op 里，绝大多数与目标应用无关（应用根本没声明对应权限），
 * 全部铺出来既干扰阅读，其中还有一批是**改不动的**（见下）。
 * 因此默认只展示 [AppOpInfo.relevant] 的条目 —— 即「应用清单里声明了对应权限」
 * 或「已经被显式设置过（非默认态）」的项；其余折叠起来，由列表里的
 * 「显示全部权限」按钮展开。
 *
 * ## 不可修改的条目
 *
 * `AppOpsService` 对**特殊访问类权限且应用未声明**的 op 会静默丢弃写入
 * （实测 `cmd appops set` 与 root 身份同样无效，详见 `AppOpsRepository` 顶部注释）。
 * 这类条目 [AppOpInfo.writable] 为 false，列表里置灰、加锁图标、不可点击，
 * 并显示「系统保护」而不是「默认」—— 避免用户以为点了没反应。
 *
 * 每行结构：`[图标] 权限名称` + 状态徽标 + 状态尾标，整卡可点击切换允许/拒绝。
 * 列表项统一用自绘 [BarCapsule]，只传 `transformedHeight` 驱动堆叠过渡。
 */
@Composable
fun PermissionScreen(
    appLabel: String,
    packageName: String,
    appOps: List<AppOpInfo>,
    isLoading: Boolean,
    isGranting: Boolean,
    onToggle: (AppOpInfo, Boolean) -> Unit,
    onGrantAll: (List<AppOpInfo>) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var showGrantConfirm by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }

    // 列表内搜索：针对**当前应用的全部 op**（含默认被折叠的「其他权限」），
    // 这样即使某项默认没显示，也能直接搜出来改，不必先展开全部再翻。
    var permQuery by remember { mutableStateOf("") }
    val searching = permQuery.isNotBlank()
    val searchHits = if (searching) appOps.filter { it.matches(permQuery) } else emptyList()

    // ⚠️ 这里刻意**不用 `remember`** 缓存派生结果。
    // `appOps` 是 MainActivity 里的 `mutableStateListOf`，实例标识恒定不变，
    // `remember(appOps)` 只会在首次组合（列表还是空的）时计算一次，
    // 之后异步加载进来的数据永远不会反映到 UI 上（症状：列表空白、计数全 0）。
    // 直接在组合期计算即可 —— 读取 SnapshotStateList 会自动订阅快照变更，
    // 且 147 条数据的过滤/分组开销可忽略。
    val relevantOps = appOps.filter { it.relevant }
    val otherOps = appOps.filter { !it.relevant }
    val hiddenCount = otherOps.size

    // 默认视图 = 相关权限；展开后 = 全量
    val visibleOps = if (showAll) appOps else relevantOps

    // 「一键授予」只作用于**当前可见且可写**的条目 —— 所见即所授
    val grantTargets = visibleOps.filter { it.writable }

    val enabledCount = visibleOps.count { it.mode == AppOpInfo.MODE_ALLOWED }
    val deniedCount = visibleOps.count { it.mode == AppOpInfo.MODE_IGNORED }
    val presentCount = visibleOps.count { it.isPresent }
    val lockedCount = visibleOps.count { !it.writable }

    // 按 GROUP_ORDER 分节；分组内保持 opCode 升序（AppOpsCatalog 本身即按 code 排序）
    val relevantGrouped = AppOpsCatalog.GROUP_ORDER.mapNotNull { g ->
        relevantOps.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it }
    }
    val otherGrouped = AppOpsCatalog.GROUP_ORDER.mapNotNull { g ->
        otherOps.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it }
    }

    // 展开开关在列表中的下标：4 个固定前置项（应用名 / 搜索框 / 汇总 / 一键授予）
    // + 可能出现的授权进度项 + 「相关权限」各分组的（1 个组标题 + N 条权限）。
    val toggleIndex = 4 + (if (isGranting) 1 else 0) +
            relevantGrouped.sumOf { 1 + it.second.size }

    // 展开后把开关滚到视口顶部，让「其他权限」立刻出现在按钮下方 ——
    // 否则用户点完只会看到标签变了，还得自己往下翻才知道到底展开了什么。
    // 搜索态下不渲染开关，此时不能滚动（下标无意义）。
    LaunchedEffect(showAll) {
        if (showAll && !searching) listState.animateScrollToItem(toggleIndex)
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onBack,
                buttonSize = EdgeButtonSize.Small
            ) {
                Text("返回")
            }
        }
    ) { contentPadding ->
        if (isLoading) {
            PermissionLoadingContent(contentPadding)
        } else if (appOps.isEmpty()) {
            PermissionEmptyContent(contentPadding)
        } else {
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                // 必须给 fillMaxSize：TransformingLazyColumn 需要正确的视口高度才能算出
                // 各项的 scrollProgress，进而驱动 transformedHeight 的缩放/淡出。
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            text = appLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                item {
                    SearchField(
                        query = permQuery,
                        onQueryChange = { permQuery = it },
                        onClear = { permQuery = "" },
                        placeholder = "搜索权限",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        // 这一行比应用搜索页更靠下（距圆心更近），可用弦宽更大，可以放宽一点
                        widthFraction = 0.66f
                    )
                }

                if (searching) {
                    // ── 搜索态：只留扁平结果列表 ────────────────
                    // 刻意隐藏汇总 / 一键授予 / 分组标题 —— 搜索时用户只关心「有没有这一项」，
                    // 其余元素只会把结果挤出屏幕。
                    if (searchHits.isEmpty()) {
                        item {
                            SearchEmptyItem(
                                query = permQuery,
                                transformationSpec = transformationSpec
                            )
                        }
                    } else {
                        item {
                            SectionHeaderItem(
                                text = "找到 ${searchHits.size} 项",
                                transformationSpec = transformationSpec
                            )
                        }
                        items(searchHits, key = { "hit_${it.opCode}" }) { appOp ->
                            AppOpCapsuleItem(
                                appOp = appOp,
                                // 应用未声明对应权限的项降级显示，但仍可点击修改
                                dimmed = !appOp.relevant,
                                enabled = !isGranting,
                                transformationSpec = transformationSpec,
                                onToggle = { allowed -> onToggle(appOp, allowed) }
                            )
                        }
                    }
                } else {
                    item {
                        PermissionSummaryItem(
                            packageName = packageName,
                            visible = visibleOps.size,
                            total = appOps.size,
                            present = presentCount,
                            allowed = enabledCount,
                            denied = deniedCount,
                            locked = lockedCount,
                            transformationSpec = transformationSpec
                        )
                    }

                    item {
                        GrantAllItem(
                            enabled = !isGranting && grantTargets.isNotEmpty(),
                            count = grantTargets.size,
                            transformationSpec = transformationSpec,
                            onClick = { showGrantConfirm = true }
                        )
                    }

                    if (isGranting) {
                        item { GrantingProgressItem(transformationSpec) }
                    }

                    // ── ① 相关权限（默认视图，始终展示）──────────
                    relevantGrouped.forEach { (group, ops) ->
                        item(key = "rel_header_$group") {
                            SectionHeaderItem(
                                text = "$group（${ops.size}）",
                                transformationSpec = transformationSpec
                            )
                        }
                        items(ops, key = { "rel_${it.opCode}" }) { appOp ->
                            AppOpCapsuleItem(
                                appOp = appOp,
                                dimmed = false,
                                enabled = !isGranting,
                                transformationSpec = transformationSpec,
                                onToggle = { allowed -> onToggle(appOp, allowed) }
                            )
                        }
                    }

                    // ── ② 展开 / 收起开关 ─────────────────────────
                    //
                    // ⚠️ 这个按钮必须夹在「相关权限」和「其他权限」**之间**，不能放在整个列表末尾。
                    //    放在末尾时：展开后新增的条目会插到按钮**上方**，用户停在原位看不到任何变化，
                    //    按钮反而被挤到 140 多项之后 —— 既看不出展开成功，也几乎点不到收起，
                    //    表现就是「这个按钮没用」。
                    //    放在中间后：新增条目出现在按钮正下方，点完立刻可见；
                    //    按钮位置不动，可以再次点击收起。
                    if (hiddenCount > 0) {
                        item(key = "expand_toggle") {
                            ExpandToggleItem(
                                showAll = showAll,
                                hiddenCount = hiddenCount,
                                transformationSpec = transformationSpec,
                                onToggle = { showAll = !showAll }
                            )
                        }
                    }

                    // ── ③ 其他权限（仅展开后展示，视觉降级）──────
                    if (showAll) {
                        otherGrouped.forEach { (group, ops) ->
                            item(key = "other_header_$group") {
                                SectionHeaderItem(
                                    text = "其他 · $group（${ops.size}）",
                                    transformationSpec = transformationSpec
                                )
                            }
                            items(ops, key = { "other_${it.opCode}" }) { appOp ->
                                AppOpCapsuleItem(
                                    appOp = appOp,
                                    // 这些条目应用并未声明对应权限，视觉上降级，
                                    // 免得用户以为它们也是这个应用在用的权限。
                                    dimmed = true,
                                    enabled = !isGranting,
                                    transformationSpec = transformationSpec,
                                    onToggle = { allowed -> onToggle(appOp, allowed) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    GrantAllConfirmDialog(
        visible = showGrantConfirm,
        appLabel = appLabel,
        opCount = grantTargets.size,
        onConfirm = {
            showGrantConfirm = false
            onGrantAll(grantTargets)
        },
        onDismiss = { showGrantConfirm = false }
    )
}

/**
 * 展开 / 收起开关。
 *
 * 折叠态文案带上待展开条数，让用户明确知道「被藏起来了多少」，
 * 而不是以为这个应用就只有这几项权限。
 *
 * 位置要求见调用处注释：必须夹在「相关权限」与「其他权限」之间，
 * 新增条目才会出现在按钮下方，点击后立刻可见。
 */
@Composable
private fun TransformingLazyColumnItemScope.ExpandToggleItem(
    showAll: Boolean,
    hiddenCount: Int,
    transformationSpec: TransformationSpec,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onToggle,
        leading = {
            Icon(
                imageVector = if (showAll) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = if (showAll) "只看相关权限" else "显示全部权限",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = if (showAll) "收起 $hiddenCount 项" else "还有 $hiddenCount 项未显示",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * 权限名匹配：中文显示名 / `android:xxx` opName / 权限字符串 任一包含关键词即可。
 *
 * 用 `opName`（如 `android:camera`）而不是系统短名（`CAMERA`）来覆盖英文搜索 ——
 * 搜 "camera" 能命中 `android:camera`，省掉一次 `AppOpsCatalog.BY_NAME` 查表。
 */
private fun AppOpInfo.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    return label.contains(q, ignoreCase = true) ||
            opName.contains(q, ignoreCase = true) ||
            (permission?.contains(q, ignoreCase = true) == true)
}

/** 列表内搜索无结果时的提示。 */
@Composable
private fun TransformingLazyColumnItemScope.SearchEmptyItem(
    query: String,
    transformationSpec: TransformationSpec
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .transformedHeight(this, transformationSpec),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "没有匹配「$query」的权限",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 分组标题（比应用名标题低一级，避免与顶部 ListHeader 抢视觉）。
 */
@Composable
private fun TransformingLazyColumnItemScope.SectionHeaderItem(
    text: String,
    transformationSpec: TransformationSpec
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
            .transformedHeight(this, transformationSpec)
    )
}

/**
 * 「一键授予全部权限」操作项。
 *
 * 用 `tertiaryContainer` 而非中性灰 —— 这是整页唯一的**动作**，需要与状态行区分开；
 * 同时刻意避开 primary 的蓝色（用户明确要求列表不铺蓝）。
 *
 * @param count 本次会实际写入的条数（当前可见 ∩ 可写）
 */
@Composable
private fun TransformingLazyColumnItemScope.GrantAllItem(
    enabled: Boolean,
    count: Int,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (enabled) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (enabled) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (enabled) onClick else null,
        leading = {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "一键授予可见权限",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = "共 $count 项可改",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 授权进行中的进度提示。上百条写入需要一定时间，必须给出反馈。 */
@Composable
private fun TransformingLazyColumnItemScope.GrantingProgressItem(
    transformationSpec: TransformationSpec
) {
    val transition = rememberInfiniteTransition(label = "granting")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grantProgress"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .transformedHeight(this, transformationSpec),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = "正在授予全部权限…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 权限行（自绘条形胶囊 + 堆叠）。
 *
 * 结构参考 `jisuanqi2/PeriodicTableScreen.kt` 的 `ElementRow`：
 * 卡片背景完全自绘（[BarCapsule] 内部 `clip + background`），列表项只负责
 * `Modifier.transformedHeight`，从而获得平滑的「收窄 + 变暗」堆叠过渡。
 *
 * 三种视觉状态：
 * 1. **已允许** —— primaryContainer 蓝底（权限状态语义色，不属「列表不铺蓝」约束范围）；
 * 2. **可修改的其余状态** —— 中性灰底，状态区分交给徽标颜色；
 * 3. **不可修改**（[AppOpInfo.writable] = false）—— 置灰、加锁、不可点击。
 *
 * @param dimmed 展开全部时，用于把「应用未声明」的条目视觉降级
 */
@Composable
private fun TransformingLazyColumnItemScope.AppOpCapsuleItem(
    appOp: AppOpInfo,
    dimmed: Boolean,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onToggle: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val clickable = enabled && appOp.writable
    val checked = appOp.isAllowed && appOp.writable

    val container = when {
        !appOp.writable -> scheme.surfaceContainer
        checked -> scheme.primaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        !appOp.writable -> scheme.onSurfaceVariant
        checked -> scheme.onPrimaryContainer
        dimmed -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }

    BarCapsule(
        containerColor = container,
        contentColor = content,
        secondaryContentColor = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        // 只用 transformedHeight 驱动堆叠，不叠加 SurfaceTransformation —— 参考实现的写法
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (clickable) { { onToggle(!checked) } } else null,
        leading = {
            Icon(
                imageVector = iconForAppOp(appOp.opName),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = appOp.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            if (appOp.writable) {
                StatusBadge(status = appOpStatusOf(appOp.mode))
            } else {
                Text(
                    text = "系统保护 · 不可修改",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailing = {
            if (appOp.writable) {
                AppOpStatePill(checked = checked)
            } else {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "不可修改",
                    tint = scheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    )
}

/**
 * 状态尾标：一颗勾/横的小圆点，直观表达当前是否允许。
 * 相比 SwitchButton 的滑块，圆点在缩小后的胶囊里辨识度更高。
 */
@Composable
private fun AppOpStatePill(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (checked) scheme.primary else scheme.surfaceContainerHigh
    val fg = if (checked) scheme.onPrimary else scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Remove,
            contentDescription = if (checked) "已允许" else "未允许",
            tint = fg,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * 状态徽标：一个圆点 + 文案，颜色取自状态视觉描述。
 */
@Composable
private fun StatusBadge(status: AppOpStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(status.color)
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = status.color,
            maxLines = 1
        )
    }
}

/**
 * 汇总条目：展示应用包名与权限统计。
 *
 * 「相关 / 全部」分开计数，是为了让用户清楚当前看到的是过滤后的视图 ——
 * 否则一个只声明了 2 个权限的应用突然只剩 2 行，会被误以为数据没加载出来。
 * 「不可修改」单列一行，解释为什么有些项点不动。
 */
@Composable
private fun TransformingLazyColumnItemScope.PermissionSummaryItem(
    packageName: String,
    visible: Int,
    total: Int,
    present: Int,
    allowed: Int,
    denied: Int,
    locked: Int,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .transformedHeight(this, transformationSpec),
    ) {
        Text(
            text = packageName,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "显示 $visible/$total · 允许 $allowed · 拒绝 $denied · 已设 $present",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (locked > 0) {
            Text(
                text = "其中 $locked 项受系统保护，无法修改",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionLoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LoadingIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "正在加载权限…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionEmptyContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "无可管理权限",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "未读取到该应用的 AppOps 条目。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
    )
}
