package com.tengwear.appopspro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * 应用列表页的**第三页**：工具。
 *
 * 入口：
 * - 命令行执行（[onOpenShell]）
 * - 应用冻结（[onOpenFreeze]）
 * - 分辨率与 DPI 修改（[onOpenResolution]）
 * - 日志抓取（[onOpenLogs]）
 * - 实时性能监控（[onOpenStats]）
 *
 * 用独立的 `TransformingLazyColumnState`，**不复用应用列表那个** ——
 * 两页列表共用一个滚动状态时，切页会把上一页的 `firstVisibleItemIndex`
 * 带过来并被本页长度钳制，再切回去位置就乱了。
 */
@Composable
fun ToolsPage(
    contentPadding: PaddingValues,
    onOpenShell: () -> Unit,
    onOpenFreeze: () -> Unit,
    onOpenResolution: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

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
                Text("工具（5）")
            }
        }

        item {
            ToolEntryItem(
                icon = Icons.Filled.Terminal,
                title = "命令行执行",
                subtitle = "以 shell 身份执行系统命令",
                transformationSpec = transformationSpec,
                onClick = onOpenShell
            )
        }

        item {
            ToolEntryItem(
                icon = Icons.Filled.AcUnit,
                title = "应用冻结",
                subtitle = "停用 / 恢复应用",
                transformationSpec = transformationSpec,
                onClick = onOpenFreeze
            )
        }

        item {
            ToolEntryItem(
                icon = Icons.Filled.AspectRatio,
                title = "分辨率与 DPI",
                subtitle = "修改屏幕分辨率与显示密度",
                transformationSpec = transformationSpec,
                onClick = onOpenResolution
            )
        }

        item {
            ToolEntryItem(
                icon = Icons.Filled.BugReport,
                title = "日志抓取",
                subtitle = "按级别 / 关键字过滤 logcat",
                transformationSpec = transformationSpec,
                onClick = onOpenLogs
            )
        }

        item {
            ToolEntryItem(
                icon = Icons.Filled.Speed,
                title = "实时性能监控",
                subtitle = "CPU · 运存 · 存储 · 电量 · 耗电",
                transformationSpec = transformationSpec,
                onClick = onOpenStats
            )
        }

        // 列表底部放一份关于入口，跟应用列表那两页对齐 ——
        // 从任何一页都能直接点进关于页，不需要先滑回应用列表。
        item(key = "about_entry") {
            AboutEntryItem(
                transformationSpec = transformationSpec,
                onClick = onOpenAbout
            )
        }
    }
}

/** 工具入口行（自绘胶囊，与应用列表保持同一套视觉）。 */
@Composable
private fun TransformingLazyColumnItemScope.ToolEntryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = subtitle,
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
