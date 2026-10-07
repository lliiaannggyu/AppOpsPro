package com.tengwear.appopspro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
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
import com.tengwear.appopspro.AppFreezeRepository
import com.tengwear.appopspro.model.InstalledApp

/**
 * 应用冻结页，分 **用户应用 / 系统应用** 两页（左右滑动切换）。
 *
 * 冻结 = `pm disable-user --user 0 <pkg>`：应用图标消失、进程被杀、无法被拉起，
 * 数据保留；解冻即恢复。具体实现与安全护栏见 [AppFreezeRepository]。
 *
 * 分两页的原因：系统应用有一百多个，混在一起会把用户应用挤到很后面；
 * 而且系统应用本来就不该随便冻，分开更符合使用心智。
 *
 * 每页内**已冻结的排最前** —— 用户进这个页面最常见的目的就是把某个应用解冻回来，
 * 让它出现在第一屏比按字母序有用得多。
 *
 * 受保护的系统组件（SystemUI / Launcher / Settings 等）显示为不可点击，
 * 因为一旦停用它们，手表就没有界面可以再操作回来了。
 */
@Composable
fun FreezeScreen(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    isWorking: Boolean,
    onToggleFreeze: (InstalledApp, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val selfPackage = LocalContext.current.packageName

    var query by remember { mutableStateOf("") }
    var pendingConfirm by remember { mutableStateOf<InstalledApp?>(null) }

    // ⚠️ 每页各自持有一个滚动状态：两页共用一个 state 时，切页会把上一页的
    // firstVisibleItemIndex 带过来并被本页长度钳制，再切回去位置就乱了。
    // ScreenScaffold 只接一个 state，这里传「当前页」那个，滚动指示器才跟着走。
    val userListState = rememberTransformingLazyColumnState()
    val systemListState = rememberTransformingLazyColumnState()
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val activeListState = if (pagerState.currentPage == 0) userListState else systemListState

    val filtered = apps.filter {
        query.isBlank() ||
                it.appLabel.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
    }
    // 已冻结优先（sortedBy(false < true)，isEnabled=false 排前面）
    val userApps = filtered.filter { !it.isSystem }.sortedBy { it.isEnabled }
    val systemApps = filtered.filter { it.isSystem }.sortedBy { it.isEnabled }

    ScreenScaffold(
        scrollState = activeListState,
        edgeButton = {
            EdgeButton(onClick = onBack, buttonSize = EdgeButtonSize.Small) {
                Text("返回")
            }
        }
    ) { contentPadding ->
        if (isLoading) {
            FreezeLoadingContent(contentPadding)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(
                        state = pagerState
                    ),
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    FreezePage(
                        listState = if (page == 0) userListState else systemListState,
                        contentPadding = contentPadding,
                        headerText = if (page == 0) {
                            "用户应用（${userApps.size}）"
                        } else {
                            "系统应用（${systemApps.size}）"
                        },
                        apps = if (page == 0) userApps else systemApps,
                        query = query,
                        onQueryChange = { query = it },
                        selfPackage = selfPackage,
                        isWorking = isWorking,
                        onClick = { pendingConfirm = it }
                    )
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

    pendingConfirm?.let { app ->
        FreezeConfirmDialog(
            app = app,
            willFreeze = app.isEnabled,
            onConfirm = {
                pendingConfirm = null
                onToggleFreeze(app, app.isEnabled)
            },
            onDismiss = { pendingConfirm = null }
        )
    }
}

/** 单页内容：标题 + 搜索框 + 统计 + 应用列表。 */
@Composable
private fun FreezePage(
    listState: TransformingLazyColumnState,
    contentPadding: PaddingValues,
    headerText: String,
    apps: List<InstalledApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    selfPackage: String,
    isWorking: Boolean,
    onClick: (InstalledApp) -> Unit
) {
    val transformationSpec = rememberTransformationSpec()
    val frozenCount = apps.count { !it.isEnabled }

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
                    .padding(horizontal = 8.dp)
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

        item {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClear = { onQueryChange("") },
                placeholder = "搜索应用",
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                widthFraction = 0.66f
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .transformedHeight(this, transformationSpec)
            ) {
                Text(
                    text = "共 ${apps.size} 个 · 已冻结 $frozenCount 个" +
                            if (isWorking) " · 正在处理…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (apps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .transformedHeight(this, transformationSpec),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "这里没有应用"
                        } else {
                            "没有匹配「$query」的应用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                FreezeItem(
                    app = app,
                    protected = AppFreezeRepository.isProtected(app.packageName, selfPackage),
                    enabled = !isWorking,
                    transformationSpec = transformationSpec,
                    onClick = { onClick(app) }
                )
            }
        }
    }
}

/** 冻结 / 解冻确认弹窗。冻结会让应用从桌面消失，必须二次确认。 */
@Composable
private fun FreezeConfirmDialog(
    app: InstalledApp,
    willFreeze: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (willFreeze) "冻结应用？" else "解冻应用？",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = buildString {
                    append("「${app.appLabel}」")
                    if (willFreeze) {
                        append("将被停用：图标从桌面消失、进程被杀、无法被拉起，数据不会丢失。")
                        if (app.isSystem) {
                            append("\n⚠️ 这是系统应用，停用可能影响手表功能。")
                        }
                    } else {
                        append("将恢复为正常状态。")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        // ⚠️ 不要给 Wear M3E AlertDialog 的按钮加 fillMaxWidth：
        // confirm / dismiss 是同一行布局，各自 fillMaxWidth 会让第二个按钮被挤出屏幕。
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (willFreeze) "冻结" else "解冻")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 单个应用的冻结行。
 *
 * 视觉沿用权限列表那套：状态用尾标药丸表达，而不是整行铺色。
 * - 已冻结 → `error` 色药丸
 * - 正常 → `primary` 色药丸
 * - 受保护 → 锁图标 + 「不可冻结」，整行不可点击
 */
@Composable
private fun TransformingLazyColumnItemScope.FreezeItem(
    app: InstalledApp,
    protected: Boolean,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val frozen = !app.isEnabled
    val clickable = enabled && !protected

    BarCapsule(
        containerColor = if (frozen) scheme.surfaceContainer else scheme.surfaceContainerHigh,
        contentColor = if (frozen) scheme.onSurfaceVariant else scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (clickable) onClick else null,
        leading = {
            AppIcon(icon = app.icon, contentDescription = app.appLabel)
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
            if (protected) {
                Text(
                    text = "系统保护 · 不可冻结",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = if (frozen) "已冻结" else app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (frozen) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailing = {
            if (protected) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "不可冻结",
                    tint = scheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                FreezeStatePill(frozen = frozen)
            }
        }
    )
}

/** 状态尾标：冻结 = 雪花，正常 = 对勾。 */
@Composable
private fun FreezeStatePill(frozen: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (frozen) scheme.error else scheme.primary
    val fg = if (frozen) scheme.onError else scheme.onPrimary
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (frozen) Icons.Filled.AcUnit else Icons.Filled.CheckCircle,
            contentDescription = if (frozen) "已冻结" else "正常",
            tint = fg,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun FreezeLoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "正在读取应用列表…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
