package com.ljyh.mei.ui.screen.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.ljyh.mei.ui.component.BiliCover
import com.ljyh.mei.ui.component.GlobalProfileAvatarButton
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.glass.IosTypography
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 首页：B 站音乐区投稿流，单列纵向列表 + 下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val listState = rememberLazyListState()
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    val feed by viewModel.feed.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loadingMore by viewModel.loadingMoreLatest.collectAsState()
    val region by viewModel.region.collectAsState()

    val systemBarsPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val collapseDistancePx = with(LocalDensity.current) { 56.dp.toPx() }
    val collapseProgress by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    fun playVideos(videos: List<BiliVideo>, startIndex: Int) {
        val connection = playerConnection ?: return
        if (videos.getOrNull(startIndex) == null) return
        scope.launch {
            val items = videos.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = "bili_${videos.getOrNull(startIndex)?.aid}_${System.currentTimeMillis()}",
                    title = region.label,
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

    IosPinnedPage(
        title = stringResource(R.string.app_tab_home),
        bottomPadding = systemBarsPadding.calculateBottomPadding(),
        collapseProgress = collapseProgress,
        backgroundColor = if (LocalGlassColors.current.isDark) LocalGlassColors.current.groupedBackground else Color.White,
        actions = { GlobalProfileAvatarButton() },
    ) { pinnedPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
        ) {
            when (val result = feed) {
                is Resource.Success -> {
                    val videos = result.data
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = pinnedPadding.calculateTopPadding() - 4.dp,
                            bottom = pinnedPadding.calculateBottomPadding() + HomeSectionSpacing,
                        ),
                        verticalArrangement = Arrangement.spacedBy(HomeSectionSpacing),
                    ) {
                        item(key = "ios-large-title", contentType = "title") {
                            Text(
                                stringResource(R.string.app_tab_home),
                                style = IosTypography.largeTitle,
                                color = LocalGlassColors.current.content,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        item(key = "region-selector", contentType = "regions") {
                            RegionSelectorRow(
                                regions = viewModel.regions,
                                selected = region,
                                onSelect = viewModel::selectRegion,
                            )
                        }
                        if (videos.isEmpty()) {
                            item(key = "feed-empty", contentType = "empty") {
                                Text(
                                    "${region.label}暂时没有新投稿",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                )
                            }
                        }
                        itemsIndexed(
                            items = videos,
                            key = { _, video -> "bili_${video.aid}_${video.cid}" },
                            contentType = { _, _ -> "bili-row" },
                        ) { index, video ->
                            MusicZoneRow(
                                video = video,
                                onClick = { playVideos(videos, index) },
                            )
                            if (index == videos.lastIndex) {
                                LaunchedEffect(video.aid) { viewModel.loadMoreLatest() }
                            }
                        }
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
                }

                is Resource.Error -> {
                    ErrorRetry(
                        message = result.message ?: "加载失败",
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = pinnedPadding.calculateTopPadding()),
                    )
                }

                Resource.Loading -> {
                    HomeShimmer(
                        contentPadding = PaddingValues(
                            top = pinnedPadding.calculateTopPadding(),
                            bottom = pinnedPadding.calculateBottomPadding() + HomeSectionSpacing,
                        ),
                    )
                }
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
        contentPadding = PaddingValues(horizontal = 16.dp),
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

/** 分区投稿行（左封面右标题，单列纵向）。 */
@Composable
private fun MusicZoneRow(
    video: BiliVideo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BiliCover(
            imageUrl = biliThumb(video.cover).orEmpty(),
            modifier = Modifier
                .weight(1f)
                .height(76.dp)
                .clip(ContinuousRoundedRectangle(10.dp)),
        )
        Column(
            modifier = Modifier.weight(1.9f),
        ) {
            Text(
                video.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = LocalGlassColors.current.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(video.uploader.orEmpty())
                    if (video.playCount > 0) {
                        append(" · ")
                        append(biliCount(video.playCount))
                        append("播放")
                    }
                },
                fontSize = 12.sp,
                color = LocalGlassColors.current.content.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
