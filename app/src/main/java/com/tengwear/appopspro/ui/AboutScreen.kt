package com.tengwear.appopspro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.appopspro.ThemePrefs
import com.tengwear.appopspro.ui.theme.ThemeSeed
import com.tengwear.appopspro.ui.theme.contentOn

/** 交流反馈群号。手表上没法选中文本，所以另给一个「复制群号」按钮。 */
private const val GROUP_NUMBER = "1081401508"

private const val AUTHOR_TEXT = "TENG 与 AI"

private const val GROUP_TEXT =
    "欢迎加入没有一点活人感，全是潜水，没人发言，可以随便吐槽聊天，分享生活的交流反馈群 $GROUP_NUMBER"

private const val INTRO_TEXT =
    "AppOps Pro 是运行在 Wear OS 手表上的权限管理工具。它借助 Shizuku 以 shell 身份直接读写系统的 " +
            "AppOps 数据库，可以精确控制每一个应用的权限状态（允许 / 拒绝 / 询问 / 默认）。" +
            "除了权限管理，还提供命令行执行和应用冻结两个实用工具。"

private const val USAGE_TEXT =
    "· 首次使用请先安装并启动 Shizuku（或 Whizuku），本应用会自动弹窗请求授权\n" +
            "· 应用列表分「用户应用 / 系统应用 / 工具」三页，左右滑动切换\n" +
            "· 点任一应用查看并修改它的权限；默认只显示该应用实际用到的权限，点列表底部按钮可展开全部\n" +
            "· 命令行：以 shell 身份执行系统命令，支持管道与重定向，输出可一键复制\n" +
            "· 应用冻结：停用或恢复应用，被冻结的应用图标会从桌面消失，数据不丢失\n" +
            "· 关于本页：在应用列表页向上滑动即可打开"

/**
 * 关于页。
 *
 * 入口：应用列表底部「↑ 关于本应用」那一行直接点击（现在是独立导航路由
 * `ROUTE_ABOUT`，不再挂在外层纵向 Pager 上，表冠旋转也不会再误触跳到这里）。
 *
 * 用 [BackHandler] 拦截系统返回，回到应用列表页。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // 关于页现在是一个独立导航路由：系统返回键 / 右滑手势统一交给 SwipeDismissableNavHost
    // 处理（它会 popBackStack 回到应用列表）。这里再用 BackHandler 兜底硬件返回，避免直接退出。
    BackHandler(onBack = onBack)

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onBack, buttonSize = EdgeButtonSize.Small) {
                Text("返回")
            }
        }
    ) { contentPadding ->
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
                    Text("关于")
                }
            }

            item {
                AboutCard(
                    title = "作者",
                    body = AUTHOR_TEXT,
                    transformationSpec = transformationSpec
                )
            }

            item {
                AboutCard(
                    title = "交流反馈",
                    body = GROUP_TEXT,
                    transformationSpec = transformationSpec
                )
            }

            item {
                CopyGroupItem(
                    transformationSpec = transformationSpec,
                    onClick = {
                        context.copyToClipboard("交流反馈群号", GROUP_NUMBER)
                        context.toast("已复制群号 $GROUP_NUMBER")
                    }
                )
            }

            item {
                SectionHeaderItem(
                    text = "主题配色",
                    transformationSpec = transformationSpec
                )
            }

            item {
                DefaultThemeItem(
                    selected = ThemePrefs.current == ThemeSeed.Default,
                    onClick = { ThemePrefs.select(ThemeSeed.Default) },
                    transformationSpec = transformationSpec
                )
            }

            item {
                SystemThemeItem(
                    selected = ThemePrefs.current == ThemeSeed.System,
                    onClick = { ThemePrefs.select(ThemeSeed.System) },
                    transformationSpec = transformationSpec
                )
            }

            item {
                ThemeSwatchRow(
                    selected = ThemePrefs.current,
                    onSelect = { ThemePrefs.select(it) },
                    transformationSpec = transformationSpec
                )
            }

            item {
                SectionHeaderItem(
                    text = "应用介绍",
                    transformationSpec = transformationSpec
                )
            }

            item {
                AboutCard(
                    title = null,
                    body = INTRO_TEXT,
                    transformationSpec = transformationSpec
                )
            }

            item {
                SectionHeaderItem(
                    text = "使用说明",
                    transformationSpec = transformationSpec
                )
            }

            item {
                AboutCard(
                    title = null,
                    body = USAGE_TEXT,
                    transformationSpec = transformationSpec
                )
            }
        }
    }
}

/**
 * 一段说明文字卡片。
 *
 * 用圆角矩形而不是胶囊：胶囊是「可点击条目」的视觉，这里只是纯展示文本，
 * 用了胶囊会让人以为能点。
 */
@Composable
private fun TransformingLazyColumnItemScope.AboutCard(
    title: String?,
    body: String,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .transformedHeight(this, transformationSpec)
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = if (title == null) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 30,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 复制群号。手表上无法选中文本，只能靠这个按钮把群号带走。 */
@Composable
private fun TransformingLazyColumnItemScope.CopyGroupItem(
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "复制群号",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = GROUP_NUMBER,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

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
 * 「默认配色」行：回到原本的中性灰方案（出厂默认）。
 */
@Composable
private fun TransformingLazyColumnItemScope.DefaultThemeItem(
    selected: Boolean,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        secondaryContentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "默认配色",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = "原本的中性灰",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

/**
 * 「跟随系统」开关行：回到表盘动态取色 / 回退配色。
 */
@Composable
private fun TransformingLazyColumnItemScope.SystemThemeItem(
    selected: Boolean,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        secondaryContentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "跟随系统",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = "随表盘 / 系统自动取色",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

/**
 * 官方色阶的种子色选择：一排可横向滑动的圆形色块，选中的描边 + 打勾。
 *
 * 用手势横向滑动而不是纵向罗列 14 行，避免把关于页拉得过长；
 * 顶部标题下的「跟随系统」行负责回到系统配色。
 */
@Composable
private fun TransformingLazyColumnItemScope.ThemeSwatchRow(
    selected: ThemeSeed,
    onSelect: (ThemeSeed) -> Unit,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .transformedHeight(this, transformationSpec)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeSeed.colorSeeds.forEach { seed ->
                SeedSwatch(
                    // colorSeeds 已过滤掉无种子色的项，故此处必非空
                    color = seed.seed!!,
                    selected = selected == seed,
                    onClick = { onSelect(seed) }
                )
            }
        }
        Text(
            text = "当前：${selected.label}",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 单个圆形色块；选中时描边 + 打勾（勾色按底色亮度取黑/白，保证对比度）。 */
@Composable
private fun SeedSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ringColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, ringColor, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentOn(color),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
