package com.ljyh.mei.ui.screen.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliFavoritesSync
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.data.repository.biliFavPlaylistId
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.component.BiliCover
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import com.ljyh.mei.ui.navigation.MeiloXPinnedListPage
import io.github.neoruaa.glasmio.core.LocalGlassColors
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.main.home.biliCount
import com.ljyh.mei.ui.screen.main.home.biliDuration
import com.ljyh.mei.ui.screen.main.home.biliThumb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiliFavFolderViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
    private val favoritesSync: BiliFavoritesSync,
) : ViewModel() {

    data class UiState(
        val folderId: Long = 0,
        val title: String = "收藏夹",
        val videos: Resource<List<BiliVideo>> = Resource.Loading,
        val synced: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initializedForId: Long = 0

    fun initialize(folderId: Long, folderTitle: String) {
        if (initializedForId == folderId) return
        initializedForId = folderId
        _state.value = UiState(folderId = folderId, title = folderTitle)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                synced = biliFavPlaylistId(folderId) in favoritesSync.syncedFolderIds()
            )
        }
        refresh()
    }

    fun refresh() {
        val folderId = _state.value.folderId
        if (folderId <= 0) return
        viewModelScope.launch {
            _state.value = _state.value.copy(videos = Resource.Loading)
            _state.value = _state.value.copy(
                videos = biliRepository.favCollectionResources(folderId, pn = 1, ps = 50).let { result ->
                    when (result) {
                        is Resource.Success -> Resource.Success(result.data.videos)
                        is Resource.Error -> Resource.Error(result.message ?: "加载失败")
                        Resource.Loading -> Resource.Loading
                    }
                }
            )
        }
    }
}

/** B 站收藏夹详情：视频列表 + 播放 + 同步到本地歌单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliFavFolderScreen(
    folderId: Long,
    folderTitle: String,
    viewModel: BiliFavFolderViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderId) {
        viewModel.initialize(folderId, folderTitle)
    }

    val uiState by viewModel.state.collectAsState()
    val gridColumns = rememberBiliGridColumns()

    // 倒序：本地反转当前已加载的列表（收藏夹详情与收藏合集详情共用本页）。
    // 注意接口只取了第一页（ps=50），超过 50 个内容时倒序只作用于已加载部分。
    var reversedOrder by rememberSaveable { mutableStateOf(false) }
    val loadedVideos = (uiState.videos as? Resource.Success)?.data
    val displayVideos = if (reversedOrder) loadedVideos?.asReversed() else loadedVideos

    fun playAll(startIndex: Int) {
        val connection = playerConnection ?: return
        val list = displayVideos ?: return
        scope.launch {
            val items = list.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = "bili_fav_$folderId",
                    title = folderTitle,
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    MeiloXPinnedListPage(
        title = folderTitle,
        onNavigateBack = navController::navigateUp,
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        item(key = "folder-header") {
            val colors = LocalGlassColors.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val count = (uiState.videos as? Resource.Success)?.data?.size ?: 0
                Text(
                    "共 $count 个内容" + if (uiState.synced) " · 已同步" else "",
                    fontSize = 14.sp,
                    color = colors.content.copy(alpha = 0.6f),
                )
                // 倒序开关：反转列表显示顺序，「播放全部」和单曲点击同样按倒序入队。
                val active = reversedOrder
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(ContinuousRoundedRectangle(10.dp))
                        .background(colors.content.copy(alpha = if (active) 0.14f else 0.07f))
                        .clickable { reversedOrder = !reversedOrder }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    SfIcon("arrow.up.arrow.down", null, size = 13.dp, tint = colors.content)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "倒序",
                        fontSize = 13.sp,
                        color = colors.content.copy(alpha = if (active) 1f else 0.6f),
                    )
                }
            }
        }

        when (val result = uiState.videos) {
            is Resource.Success -> {
                val shown = displayVideos ?: result.data
                if (shown.isNotEmpty()) {
                    item(key = "play-all") {
                        GlasmioGroupedList {
                            GlasmioListRow(
                                title = "播放全部",
                                systemName = "play.fill",
                                showTopSeparator = false,
                                onClick = { playAll(0) },
                            )
                        }
                    }
                }
                biliVideoGridItems(
                    videos = shown,
                    columns = gridColumns,
                    key = { video -> "${video.aid}_${video.cid}" },
                ) { index, _ -> playAll(index) }
            }
            is Resource.Error -> item(key = "folder-error") {
                Text(
                    result.message ?: "加载失败",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
            Resource.Loading -> item(key = "folder-loading") {
                Text(
                    "加载中…",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

/** B 站视频行（封面 + 标题 + UP 主 + 时长），首页与收藏夹共用。 */
@Composable
fun BiliTrackRow(
    video: BiliVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverWidth: Dp = 130.dp,
    horizontalPadding: Dp = 16.dp,
    showDurationBadge: Boolean = true,
) {
    // 按封面实际像素宽解码：网格列宽只有几十 dp 时，不必整张按 320px 解。
    // 一屏二三十张封面时这一项直接决定解码内存和 GC 抖动。
    val coverDecodeWidth = with(LocalDensity.current) { coverWidth.roundToPx() }.coerceIn(64, 512)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BiliCover(
                imageUrl = biliThumb(video.cover).orEmpty(),
                decodeWidth = coverDecodeWidth,
                modifier = Modifier
                    .width(coverWidth)
                    .aspectRatio(16f / 10f)
                    .clip(ContinuousRoundedRectangle(10.dp)),
            )
            if (showDurationBadge) {
            Text(
                biliDuration(video.durationMs),
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(ContinuousRoundedRectangle(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
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

/**
 * 平板/横屏多列布局的列数（沿用上游 MeiloX 的按宽分列算法）：
 * 约每 180dp 一列，最少 2 列、最多 4 列；竖屏手机返回 1（单列行）。
 */
@Composable
fun rememberBiliGridColumns(): Int {
    val device = rememberDeviceInfo()
    val configuration = LocalConfiguration.current
    return if (device.isLandscape || device.isTablet) {
        (configuration.screenWidthDp.dp / 180.dp).toInt().coerceIn(2, 4)
    } else {
        1
    }
}

/**
 * B 站视频列表的懒加载条目：竖屏单列逐行；横屏/平板按 [columns] 分列成行，
 * 行内单元格等宽（weight 1f），不足补位，行尾可选分隔线。
 */
fun LazyListScope.biliVideoGridItems(
    videos: List<BiliVideo>,
    columns: Int,
    dividerColor: Color? = null,
    key: (BiliVideo) -> Any = { it.aid },
    onReachEnd: (() -> Unit)? = null,
    /** 网格模式下封面宽度。列数越多单元格越窄，封面必须跟着缩，否则文字会被挤没。 */
    gridCoverWidth: Dp = 100.dp,
    /** 单列模式下行内左右内距；页面本身已经给过留白的调用方传 0，避免叠加成双重边距。 */
    rowHorizontalPadding: Dp = 16.dp,
    onItemClick: (index: Int, video: BiliVideo) -> Unit,
) {
    if (columns <= 1) {
        itemsIndexed(
            videos,
            key = { _, video -> key(video) },
            contentType = { _, _ -> "bili-row" },
        ) { index, video ->
            BiliTrackRow(
                video = video,
                onClick = { onItemClick(index, video) },
                horizontalPadding = rowHorizontalPadding,
            )
            if (dividerColor != null && index < videos.lastIndex) {
                HorizontalDivider(
                    color = dividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (onReachEnd != null && index == videos.lastIndex) {
                LaunchedEffect(key(video)) { onReachEnd() }
            }
        }
        return
    }
    val rows = videos.chunked(columns)
    itemsIndexed(
        rows,
        key = { _, row -> row.joinToString("-") { key(it).toString() } },
        contentType = { _, _ -> "bili-row-group" },
    ) { rowIndex, row ->
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { slot, video ->
                    val globalIndex = rowIndex * columns + slot
                    BiliTrackRow(
                        video = video,
                        onClick = { onItemClick(globalIndex, video) },
                        modifier = Modifier.weight(1f),
                        coverWidth = gridCoverWidth,
                        horizontalPadding = 0.dp,
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            if (dividerColor != null && rowIndex < rows.lastIndex) {
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (onReachEnd != null && rowIndex == rows.lastIndex) {
            LaunchedEffect(row.first().aid) { onReachEnd() }
        }
    }
}
