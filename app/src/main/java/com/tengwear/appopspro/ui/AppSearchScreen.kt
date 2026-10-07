package com.tengwear.appopspro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
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
import com.tengwear.appopspro.SearchHistoryStore
import com.tengwear.appopspro.model.AppOpsCatalog
import com.tengwear.appopspro.model.InstalledApp

/**
 * 一条搜索命中记录。
 *
 * @param app 命中的应用
 * @param matchedOp 因权限命中时，第一个命中的权限；仅按应用名命中时为 null
 * @param matchedOpCount 该应用命中的权限条数（应用名命中但权限未命中时为 0）
 * @param matchedByApp 是否按应用名 / 包名命中
 */
data class AppSearchHit(
    val app: InstalledApp,
    val matchedOp: AppOpsCatalog.Op?,
    val matchedOpCount: Int,
    val matchedByApp: Boolean
)

/**
 * 搜索页（应用 + 权限双维度），带搜索历史。
 *
 * 设计说明：
 *  搜索框用共用的 [SearchField] 自绘（Wear M3E 1.5.0 没有 `TextField`），
 *  进入页面自动聚焦并弹出键盘，减少一次点击。
 *
 * 搜索历史：
 *  空查询时展示最近几条历史（最多 8 条，见 `SearchHistoryStore`），点一条即回填并重新搜索；
 *  只在**用户真正提交**时记录（按下键盘搜索键，或点开了某个结果），
 *  否则历史会被「相」「相机」「相机权」这类半成品塞满。
 *
 * 匹配规则（任一命中即可）：
 *  - 应用名 / 包名 包含关键词
 *  - 该应用**已记录的**某个 AppOp 的 中文名 / 系统短名 / opName / 权限字符串 包含关键词
 *
 * 权限维度的匹配依赖 [opIndex]（由调用方预先读取，见
 * `AppOpsRepository.getOpCodesByPackage`）—— 手表端应用数量有限，
 * 一次建立索引后搜索即是纯内存过滤。
 */
@Composable
fun AppSearchScreen(
    apps: List<InstalledApp>,
    opIndex: Map<String, Set<String>>,
    isIndexLoading: Boolean,
    onAppClick: (InstalledApp) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(SearchHistoryStore.load()) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // ⚠️ 必须把 apps / opIndex 作为 remember 的 key：
    // 它们是**普通参数**而非 Compose State，写在 derivedStateOf 里不会被追踪；
    // 若用无参 `remember { derivedStateOf { ... } }`，lambda 会一直闭包住首次传入的
    // 空 opIndex，导致索引异步建好后搜索结果仍然为空。
    // query 是 mutableStateOf，作为 State 读取可正常触发重算，无需入 key。
    val results by remember(apps, opIndex) {
        derivedStateOf {
            val q = query.trim()
            if (q.isEmpty()) {
                emptyList()
            } else {
                apps.mapNotNull { app ->
                    val matchedByApp = app.appLabel.contains(q, ignoreCase = true) ||
                            app.packageName.contains(q, ignoreCase = true)

                    val matchedOps = opIndex[app.packageName]
                        .orEmpty()
                        .mapNotNull { AppOpsCatalog.BY_NAME[it] }
                        .filter { op ->
                            op.label.contains(q, ignoreCase = true) ||
                                    op.name.contains(q, ignoreCase = true) ||
                                    op.opName.contains(q, ignoreCase = true) ||
                                    (op.permission?.contains(q, ignoreCase = true) == true)
                        }

                    if (!matchedByApp && matchedOps.isEmpty()) {
                        null
                    } else {
                        AppSearchHit(
                            app = app,
                            matchedOp = matchedOps.firstOrNull(),
                            matchedOpCount = matchedOps.size,
                            matchedByApp = matchedByApp
                        )
                    }
                }
                    // 应用名命中优先（用户搜的通常是应用），其次按名称排序
                    .sortedWith(
                        compareByDescending<AppSearchHit> { it.matchedByApp }
                            .thenBy { it.app.appLabel.lowercase() }
                    )
            }
        }
    }

    // 进入页面即聚焦输入框，自动唤起键盘
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    placeholder = "搜应用或权限",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    focusRequester = focusRequester,
                    // 按下键盘搜索键 = 用户提交了这次搜索，此时才记入历史
                    onSearchAction = {
                        if (query.isNotBlank()) history = SearchHistoryStore.add(query)
                    }
                )
            }

            when {
                // ── 空查询：优先展示搜索历史 ──────────────
                query.isBlank() -> {
                    if (history.isEmpty()) {
                        item {
                            HintItem(
                                text = if (isIndexLoading) {
                                    "正在读取各应用权限…"
                                } else {
                                    "可搜应用名、包名或权限名\n如「相机」「位置」「存储」"
                                },
                                transformationSpec = transformationSpec
                            )
                        }
                    } else {
                        item {
                            ListHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec)
                            ) {
                                Text("最近搜索")
                            }
                        }
                        items(history, key = { "history_$it" }) { keyword ->
                            HistoryItem(
                                keyword = keyword,
                                transformationSpec = transformationSpec,
                                onClick = { query = keyword }
                            )
                        }
                        item {
                            ClearHistoryItem(
                                transformationSpec = transformationSpec,
                                onClick = { history = SearchHistoryStore.clear() }
                            )
                        }
                    }
                }

                results.isEmpty() -> item {
                    HintItem(
                        text = if (isIndexLoading) {
                            "正在读取各应用权限…"
                        } else {
                            "没有匹配「$query」的应用或权限"
                        },
                        transformationSpec = transformationSpec
                    )
                }

                else -> {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text("找到 ${results.size} 个")
                        }
                    }
                    items(results, key = { it.app.packageName }) { hit ->
                        SearchResultItem(
                            hit = hit,
                            transformationSpec = transformationSpec,
                            onClick = {
                                // 点开结果 = 这次搜索有价值，此时才记入历史
                                if (query.isNotBlank()) {
                                    history = SearchHistoryStore.add(query)
                                }
                                onAppClick(hit.app)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一条搜索历史（自绘胶囊，点一下回填搜索词）。
 */
@Composable
private fun TransformingLazyColumnItemScope.HistoryItem(
    keyword: String,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 清空全部搜索历史。 */
@Composable
private fun TransformingLazyColumnItemScope.ClearHistoryItem(
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "清空搜索历史",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * 搜索结果项（自绘条形胶囊 + 堆叠），与应用列表 / 权限列表保持同一套视觉。
 *
 * 副标题按命中维度切换：
 *  - 因权限命中 → 显示命中的权限名（可能还有「+N」），这才是用户想看到的信息
 *  - 仅因应用名命中 → 显示包名（与原来一致）
 */
@Composable
private fun TransformingLazyColumnItemScope.SearchResultItem(
    hit: AppSearchHit,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val app = hit.app
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
                text = hit.secondaryText(),
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

/** 结果项副标题文案。 */
private fun AppSearchHit.secondaryText(): String {
    val op = matchedOp
    if (op == null) return app.packageName
    val more = if (matchedOpCount > 1) " 等 ${matchedOpCount} 项" else ""
    return "权限：${op.label}$more"
}

@Composable
private fun TransformingLazyColumnItemScope.HintItem(
    text: String,
    transformationSpec: TransformationSpec
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .transformedHeight(this, transformationSpec),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
