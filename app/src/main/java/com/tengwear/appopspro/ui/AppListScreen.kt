package com.tengwear.appopspro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import com.tengwear.appopspro.model.InstalledApp

/**
 * 应用列表页（Wear M3E）。
 *
 * 只有一个横向 [HorizontalPager]，三页：用户应用 / 系统应用 / 工具。
 * 表冠旋转、上下滑动都只作用于当前列表本身，不会被外层纵向 Pager 劫持去「关于」页 ——
 * 关于页改为独立的导航路由（见 [com.tengwear.appopspro.MainActivity] 的 `ROUTE_ABOUT`），
 * 只能从列表底部「↑ 关于本应用」那一行点进去。
 *
 * Pager **始终挂载**（即使应用还在加载 / Shizuku 未就绪），所以加载期间左右滑动切换页面也正常；
 * 加载 / 未就绪 / 本页无应用 时，对应页内显示状态提示，而不是整页替换掉 Pager。
 */
@Composable
fun AppListScreen(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    shizukuReady: Boolean,
    onAppClick: (InstalledApp) -> Unit,
    onRetryShizuku: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenShell: () -> Unit,
    onOpenFreeze: () -> Unit,
    onOpenResolution: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenBatchGrant: () -> Unit,
    onOpenAbout: () -> Unit
) {
    AppListContent(
        apps = apps,
        isLoading = isLoading,
        shizukuReady = shizukuReady,
        onAppClick = onAppClick,
        onRetryShizuku = onRetryShizuku,
        onOpenSearch = onOpenSearch,
        onOpenShell = onOpenShell,
        onOpenFreeze = onOpenFreeze,
        onOpenResolution = onOpenResolution,
        onOpenLogs = onOpenLogs,
        onOpenStats = onOpenStats,
        onOpenBatchGrant = onOpenBatchGrant,
        onOpenAbout = onOpenAbout
    )
}

/** 列表主体：搜索入口 + 三页横向应用列表。Pager 始终挂载，状态提示渲染在对应页内。 */
@Composable
private fun AppListContent(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    shizukuReady: Boolean,
    onAppClick: (InstalledApp) -> Unit,
    onRetryShizuku: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenShell: () -> Unit,
    onOpenFreeze: () -> Unit,
    onOpenResolution: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenBatchGrant: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val userApps by remember {
        derivedStateOf { apps.filter { !it.isSystem } }
    }
    val systemApps by remember {
        derivedStateOf { apps.filter { it.isSystem } }
    }

    // ⚠️ 每页各自持有一个滚动状态。两页共用一个 state 时，切页会把上一页的
    // firstVisibleItemIndex 带过来并被本页长度钳制，再切回去位置就乱了。
    // ScreenScaffold 只接一个 state，这里传「当前页」那个，滚动指示器才跟着走。
    val userListState = rememberTransformingLazyColumnState()
    val systemListState = rememberTransformingLazyColumnState()
    // 3 页：用户应用 / 系统应用 / 工具
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val activeListState = if (pagerState.currentPage == 0) userListState else systemListState
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = activeListState) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(
                    state = pagerState
                ),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> AppListPage(
                        listState = userListState,
                        contentPadding = contentPadding,
                        headerText = "用户应用（${userApps.size}）",
                        apps = userApps,
                        isLoading = isLoading,
                        shizukuReady = shizukuReady,
                        onRetryShizuku = onRetryShizuku,
                        transformationSpec = transformationSpec,
                        onAppClick = onAppClick,
                        onOpenSearch = onOpenSearch,
                        onOpenAbout = onOpenAbout
                    )
                    1 -> AppListPage(
                        listState = systemListState,
                        contentPadding = contentPadding,
                        headerText = "系统应用（${systemApps.size}）",
                        apps = systemApps,
                        isLoading = isLoading,
                        shizukuReady = shizukuReady,
                        onRetryShizuku = onRetryShizuku,
                        transformationSpec = transformationSpec,
                        onAppClick = onAppClick,
                        onOpenSearch = null,
                        onOpenAbout = onOpenAbout
                    )
                    2 -> ToolsPage(
                        contentPadding = contentPadding,
                        onOpenShell = onOpenShell,
                        onOpenFreeze = onOpenFreeze,
                        onOpenResolution = onOpenResolution,
                        onOpenLogs = onOpenLogs,
                        onOpenStats = onOpenStats,
                        onOpenBatchGrant = onOpenBatchGrant,
                        onOpenAbout = onOpenAbout
                    )
                }
            }

            HorizontalPageIndicator(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun AppListPage(
    listState: TransformingLazyColumnState,
    contentPadding: PaddingValues,
    headerText: String,
    apps: List<InstalledApp>,
    isLoading: Boolean,
    shizukuReady: Boolean,
    onRetryShizuku: () -> Unit,
    transformationSpec: TransformationSpec,
    onAppClick: (InstalledApp) -> Unit,
    onOpenSearch: (() -> Unit)?,
    onOpenAbout: () -> Unit
) {
    // 未就绪 / 加载中 / 本页无应用：显示对应状态提示。
    // 注意此时仍在横向 Pager 内，左右滑动切换页面照常可用。
    if (!shizukuReady) {
        ShizukuNotReadyContent(onRetry = onRetryShizuku, contentPadding = contentPadding)
        return
    }
    if (isLoading) {
        LoadingContent(contentPadding)
        return
    }
    if (apps.isEmpty()) {
        EmptyContent(contentPadding)
        return
    }

    TransformingLazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec)
            ) {
                Text(
                    text = headerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onOpenSearch != null) {
            item {
                SearchEntryItem(
                    onClick = onOpenSearch,
                    transformationSpec = transformationSpec
                )
            }
        }

        items(apps, key = { it.packageName }) { app ->
            AppBarItemTransformed(
                app = app,
                transformationSpec = transformationSpec,
                onClick = { onAppClick(app) }
            )
        }

        // 列表末尾：既提示「上滑可打开关于页」，也提供直接点击的入口。
        // 只靠手势的话，列表长的时候用户很难意识到还有下一页。
        item(key = "about_entry") {
            AboutEntryItem(
                transformationSpec = transformationSpec,
                onClick = onOpenAbout
            )
        }
    }
}

/**
 * 「↑ 关于本应用」入口。
 *
 * 用 secondaryContainer 区分于普通应用项（用户明确要求列表不铺蓝，
 * 这里用中性一点的次要容器色而不是 primaryContainer）。
 *
 * 在每个列表页（用户应用 / 系统应用 / 工具）底部都放一份，
 * 这样从任何一页都能直接点进关于页，不再依赖外层 Pager 的上滑切换。
 */
@Composable
fun TransformingLazyColumnItemScope.AboutEntryItem(
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "关于本应用",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = "上滑或点击打开",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * 搜索入口：一个带放大镜图标的条形胶囊，点击进入搜索页。
 *
 * 与权限列表保持同一套视觉：自绘 [BarCapsule] + 只传 `transformedHeight`，
 * 堆叠过渡才是平滑的「收窄 + 变暗」。不用 M3E `Button`（自带容器绘制，会和
 * 列表的 `transformedHeight` 叠加，堆叠变生硬）。
 * 配色刻意避开蓝色：底色用中性灰 `surfaceContainerHigh`，与普通应用项一致。
 */
@Composable
private fun TransformingLazyColumnItemScope.SearchEntryItem(
    onClick: () -> Unit,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(AppListItemIconSize)
            )
        },
        label = {
            Text(
                text = "搜索应用或权限",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * 应用列表项（自绘条形胶囊 + 堆叠）。
 *
 * 视觉与权限列表的 `AppOpCapsuleItem` 完全一致：同一个 [BarCapsule] 组件、
 * 同样的 `surfaceContainerHigh` 中性灰底、同样的 `transformedHeight` 堆叠驱动。
 *
 * 行内布局：`[应用图标] [应用名 / 包名] [展开箭头]`。
 * 配色**不使用蓝色**，避免整屏铺满 M3E 主色块。
 */
@Composable
private fun TransformingLazyColumnItemScope.AppBarItemTransformed(
    app: InstalledApp,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            AppIcon(
                icon = app.icon,
                contentDescription = app.appLabel
            )
        },
        label = {
            Text(
                text = app.appLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
private fun ShizukuNotReadyContent(
    onRetry: () -> Unit,
    contentPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Shizuku 未就绪",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "需要 Shizuku 授权才能管理应用权限。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("去授权")
            }
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
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
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "正在加载应用…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未找到已安装应用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 列表项左侧图标尺寸（搜索放大镜）。
 *
 * 22dp 与 [BarCapsule] 的 leading 插槽尺寸一致，保证不同列表项的图标列宽统一；
 * 应用图标本身在 [AppIcon] 内也是 24dp 圆形，视觉上对齐。
 */
private val AppListItemIconSize = 22.dp
