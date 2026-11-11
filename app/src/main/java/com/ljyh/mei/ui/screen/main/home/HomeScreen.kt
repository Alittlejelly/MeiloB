package com.ljyh.mei.ui.screen.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ljyh.mei.R
import com.ljyh.mei.constants.HomeSectionSpacing
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.component.globalProfileHeaderAction
import com.ljyh.mei.ui.navigation.MeiloXPinnedListPage
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.SfIcon
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import com.ljyh.mei.ui.screen.account.biliVideoGridItems

/**
 * 首页：B 站音乐区投稿流。竖屏手机单列，平板/横屏按宽度分列成网格（与收藏夹一致），
 * 外加大标题折叠、顶部渐进模糊与下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // 列表状态交给 MeiloXPinnedListPage：它内部注册导航视口、算大标题折叠与顶部模糊，
    // 与收藏、搜索结果等页面用的是同一套页面骨架。
    val listState = rememberLazyListState()
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    val feed by viewModel.feed.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loadingMore by viewModel.loadingMoreLatest.collectAsState()
    val region by viewModel.region.collectAsState()
    val guessLike by viewModel.guessLike.collectAsState()

    val systemBarsPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val colors = LocalGlassColors.current
    val backgroundColor = if (colors.isDark) colors.groupedBackground else Color.White
    val gridColumns = rememberHomeGridColumns()

    fun playVideos(videos: List<BiliVideo>, startIndex: Int) {
        val connection = playerConnection ?: return
        if (videos.getOrNull(startIndex) == null) return
        scope.launch {
            val items = videos.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = "bili_${videos.getOrNull(startIndex)?.aid}_${System.currentTimeMillis()}",
                    title = viewModel.feedTitle,
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    // 切换分区后回到列表顶部，避免沿用上一分区的滚动位置。
    LaunchedEffect(region.rid) {
        listState.scrollToItem(0)
    }

    MeiloXPinnedListPage(
        title = stringResource(R.string.app_tab_home),
        listState = listState,
        bottomPadding = systemBarsPadding.calculateBottomPadding(),
        // 横向留白由页面统一给，行内不再各加 16dp。
        horizontalContentPadding = 16.dp,
        largeTitleHorizontalPadding = 12.dp,
        // 网格行本身就是"一行多格"，再用 24dp 的节间距会显得很散。
        verticalArrangement = Arrangement.spacedBy(if (gridColumns > 1) 8.dp else HomeSectionSpacing),
        backgroundColor = backgroundColor,
        actions = { listOf(globalProfileHeaderAction()) },
        listWrapper = { content ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
            ) { content() }
        },
    ) {
        when (val result = feed) {
            is Resource.Success -> {
                val videos = result.data
                if (!guessLike) {
                    item(key = "region-selector", contentType = "regions") {
                        RegionSelectorRow(
                            regions = viewModel.regions,
                            selected = region,
                            onSelect = viewModel::selectRegion,
                        )
                    }
                }
                if (videos.isEmpty()) {
                    item(key = "feed-empty", contentType = "empty") {
                        Text(
                            viewModel.feedEmptyText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        )
                    }
                }
                // 竖屏单列逐行；平板/横屏按宽度分列成网格，与收藏夹、搜索结果同一套排布。
                biliVideoGridItems(
                    videos = videos,
                    columns = gridColumns,
                    key = { video -> "bili_${video.aid}_${video.cid}" },
                    onReachEnd = { viewModel.loadMoreLatest() },
                    gridCoverWidth = homeGridCoverWidth(gridColumns),
                    // 页面已统一给 16dp 横向留白，行内不再重复。
                    rowHorizontalPadding = 0.dp,
                ) { index, _ -> playVideos(videos, index) }
                if (loadingMore) {
                    item(key = "feed-loading", contentType = "loading") {
                        Text(
                            "加载中…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            is Resource.Error -> item(key = "feed-error", contentType = "error") {
                ErrorRetry(
                    message = result.message ?: "加载失败",
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                )
            }

            Resource.Loading -> item(key = "feed-shimmer", contentType = "loading") {
                HomeShimmerBlocks()
            }
        }
    }
}

/** 首页分区横向选择条：胶囊样式，选中项高亮。 */
@Composable
private fun RegionSelectorRow(
    regions: List<com.ljyh.mei.data.model.bili.BiliRegion>,
    selected: com.ljyh.mei.data.model.bili.BiliRegion,
    onSelect: (com.ljyh.mei.data.model.bili.BiliRegion) -> Unit,
) {
    val selectedRid = selected.rid
    // 选中项不在可见范围时（如恢复的偏好分区靠后），滚动定位到它附近。
    val rowState = rememberLazyListState()
    LaunchedEffect(selectedRid) {
        val index = regions.indexOfFirst { it.rid == selectedRid }
        if (index >= 0) {
            val info = rowState.layoutInfo
            val visible = info.visibleItemsInfo.any { it.index == index }
            if (!visible) rowState.scrollToItem(index.coerceAtLeast(0))
        }
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items = regions, key = { _, r -> r.rid }) { _, r ->
            val isSelected = r.rid == selectedRid
            Text(
                r.label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else LocalGlassColors.current.content.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(ContinuousRoundedRectangle(18.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else LocalGlassColors.current.content.copy(alpha = 0.08f),
                    )
                    .clickable { onSelect(r) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * 首页的列数：竖屏手机单列（1），横屏/平板按可用宽度分列。
 *
 * 一列约占 200dp，上限 4 列 —— 5 列时每格只剩一百多 dp，封面和两行标题互相挤压，
 * 观感太密。手机/平板横屏下就是 2–4 列。
 */
@Composable
private fun rememberHomeGridColumns(): Int {
    val device = rememberDeviceInfo()
    val configuration = LocalConfiguration.current
    return if (device.isLandscape || device.isTablet) {
        (configuration.screenWidthDp.dp / 200.dp).toInt().coerceIn(2, 4)
    } else {
        1
    }
}

/** 网格模式下封面宽度随列数收缩，保证每格右侧始终留得下两行标题。 */
private fun homeGridCoverWidth(columns: Int): Dp = when {
    columns >= 4 -> 70.dp
    columns == 3 -> 92.dp
    else -> 118.dp
}

@Composable
private fun ErrorRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SfIcon("exclamationmark.triangle", contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text(
            "重试",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(ContinuousRoundedRectangle(10.dp))
                .clickable { onRetry() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

internal fun biliThumb(url: String?, width: Int = 320): String? {
    val raw = url ?: return null
    if (raw.contains("hdslb.com") && !raw.contains("@")) {
        return "${raw}@${width}w_${width * 5 / 8}h_1c.jpg"
    }
    return raw
}

internal fun biliDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun biliCount(count: Long): String = when {
    count >= 100_000_000 -> String.format(Locale.US, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(Locale.US, "%.1f万", count / 10_000.0)
    else -> count.toString()
}
