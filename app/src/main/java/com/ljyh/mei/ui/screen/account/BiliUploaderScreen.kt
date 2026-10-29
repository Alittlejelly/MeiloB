package com.ljyh.mei.ui.screen.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ljyh.mei.data.model.bili.BiliSpaceCard
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.data.repository.BiliSpaceGroup
import com.ljyh.mei.di.repository.ColorRepository
import com.ljyh.mei.playback.queue.ListQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ljyh.mei.ui.component.playlist.rememberCoverBackground
import com.ljyh.mei.ui.glass.GlassButton
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.glass.IosTypography
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.main.home.biliCount
import com.ljyh.mei.ui.screen.main.home.biliThumb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BiliUploaderViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: android.content.Context,
    private val biliRepository: BiliRepository,
    private val colorRepository: ColorRepository,
) : ViewModel() {

    data class UiState(
        val mid: Long = 0,
        val card: Resource<BiliSpaceCard.Data> = Resource.Loading,
        val videos: Resource<List<BiliVideo>> = Resource.Loading,
        val totalCount: Int = 0,
        val groups: List<BiliSpaceGroup> = emptyList(),
        /** arc/search 被风控时只有最近 20 条，UI 标题相应变化。 */
        val fullList: Boolean = true,
        val loadingMore: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initializedForMid: Long = 0
    private var pageNum = 1
    private var exhausted = false

    fun getCachedColor(url: String) = colorRepository.getFromMemory(url)

    suspend fun getOrExtractColor(url: String) =
        colorRepository.getColorOrExtract(appContext, url)

    fun initialize(mid: Long) {
        if (initializedForMid == mid || mid <= 0) return
        initializedForMid = mid
        _state.value = UiState(mid = mid)
        pageNum = 1
        exhausted = false
        viewModelScope.launch {
            _state.value = _state.value.copy(card = biliRepository.spaceCard(mid))
        }
        viewModelScope.launch {
            when (val result = biliRepository.spaceArchivesPage(mid, pn = 1)) {
                is Resource.Success -> {
                    val page = result.data
                    pageNum = 1
                    exhausted = !page.hasMore
                    _state.value = _state.value.copy(
                        videos = Resource.Success(page.videos),
                        totalCount = page.totalCount,
                        fullList = page.full,
                    )
                }
                is Resource.Error -> _state.value = _state.value.copy(
                    videos = Resource.Error(result.message ?: "加载失败"),
                )
                Resource.Loading -> Unit
            }
        }
        viewModelScope.launch {
            val groups = biliRepository.spaceGroups(mid)
            if (groups is Resource.Success) {
                _state.value = _state.value.copy(groups = groups.data)
            }
        }
    }

    fun loadMore() {
        val mid = _state.value.mid
        if (mid <= 0 || exhausted || _state.value.loadingMore) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            val next = pageNum + 1
            when (val result = biliRepository.spaceArchivesPage(mid, pn = next)) {
                is Resource.Success -> {
                    val page = result.data
                    val existing = (_state.value.videos as? Resource.Success)?.data.orEmpty()
                    val fresh = page.videos.filter { new -> existing.none { it.aid == new.aid } }
                    if (fresh.isNotEmpty() || page.hasMore) pageNum = next
                    exhausted = !page.hasMore || fresh.isEmpty()
                    if (fresh.isNotEmpty()) {
                        _state.value = _state.value.copy(videos = Resource.Success(existing + fresh))
                    }
                }
                else -> exhausted = true
            }
            _state.value = _state.value.copy(loadingMore = false)
        }
    }
}

/** app 内 UP 主主页：仿上游歌手主页样式（方形 Hero + 封面取色背景 + 折叠工具栏 + 投稿列表）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliUploaderScreen(
    mid: Long,
    viewModel: BiliUploaderViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(mid) { viewModel.initialize(mid) }

    val uiState by viewModel.state.collectAsState()
    val cardData = (uiState.card as? Resource.Success)?.data
    val uploaderName = cardData?.card?.name.orEmpty().ifBlank { "UP 主" }
    val heroCover = biliThumb(cardData?.card?.face, 640).orEmpty()

    val background = rememberCoverBackground(
        coverUrl = heroCover,
        getCachedColor = viewModel::getCachedColor,
        getOrExtractColor = viewModel::getOrExtractColor,
        ownerKey = mid,
    )
    val colors = LocalGlassColors.current

    val scrollState = rememberLazyListState()
    val videos = (uiState.videos as? Resource.Success)?.data.orEmpty()

    fun playAll(startIndex: Int) {
        val connection = playerConnection ?: return
        if (videos.getOrNull(startIndex) == null) return
        scope.launch {
            val items = videos.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = UUID.randomUUID().toString(),
                    title = "$uploaderName 的投稿",
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 方形 Hero 的实际高度（页面宽度），用于折叠进度换算。
        val heroHeightPx = with(LocalDensity.current) { maxWidth.toPx() }
        val collapseProgress by remember(scrollState, heroHeightPx) {
            derivedStateOf {
                if (scrollState.firstVisibleItemIndex > 0) 1f
                else (scrollState.firstVisibleItemScrollOffset / (heroHeightPx * 0.6f).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
        }
        val blurDistancePx = with(LocalDensity.current) { 56.dp.toPx() }
        val blurProgress by remember(scrollState, blurDistancePx) {
            derivedStateOf {
                if (scrollState.firstVisibleItemIndex > 0) 1f
                else (scrollState.firstVisibleItemScrollOffset / blurDistancePx).coerceIn(0f, 1f)
            }
        }

        IosPinnedPage(
            title = uploaderName,
            bottomPadding = bottomPadding,
            collapseProgress = collapseProgress,
            topBarBlurProgress = blurProgress,
            onNavigateBack = navController::navigateUp,
            backgroundColor = background.color,
            backgroundBrush = background.brush,
        ) { contentPadding ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                // --- 1. Hero + 名片 ---
                item(key = "uploader-hero") {
                    Column(Modifier.fillMaxWidth()) {
                        if (heroCover.isNotBlank()) {
                            AsyncImage(
                                model = heroCover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        // 底部渐隐到页面渐变色，避免生硬接缝。
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to Color.Black,
                                                0.45f to Color.Black,
                                                0.70f to Color.Black.copy(alpha = 0.45f),
                                                1f to Color.Transparent,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                            )
                        } else {
                            Spacer(Modifier.height(24.dp))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                uploaderName,
                                style = IosTypography.title2,
                                color = colors.content,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            val sign = cardData?.card?.sign?.takeIf { it.isNotBlank() }
                            if (cardData != null || sign != null) {
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    buildString {
                                        if (cardData != null) append("${biliCount(cardData.follower)}粉丝")
                                        if (sign != null) {
                                            if (isNotEmpty()) append(" · ")
                                            append(sign)
                                        }
                                    },
                                    style = IosTypography.subheadline,
                                    color = colors.secondaryContent,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            GlassButton(
                                onClick = { playAll(0) },
                                enabled = videos.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SfIcon("play.fill", null, size = 15.dp, tint = colors.content)
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    if (uiState.totalCount > 0) "播放全部 · ${uiState.totalCount} 个投稿" else "播放全部",
                                    style = IosTypography.body,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.content,
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }

                // --- 2. 分组（合集/系列）---
                if (uiState.groups.isNotEmpty()) {
                    item(key = "uploader-groups-section") {
                        Text(
                            "分组",
                            style = IosTypography.headline,
                            color = colors.content,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 10.dp, bottom = 4.dp),
                        )
                    }
                    itemsIndexed(
                        uiState.groups,
                        key = { _, group -> "group_${group.id}" },
                        contentType = { _, _ -> "uploader-group" },
                    ) { _, group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Screen.BiliUploaderGroup.navigate(navController) {
                                        addPath(mid.toString())
                                        addPath(group.id.toString())
                                        addPath(android.net.Uri.encode(group.name))
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SfIcon("music.note.square.stack.fill", null, size = 20.dp, tint = colors.secondaryContent)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    group.name.ifBlank { "合集" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.content,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${group.total} 个内容",
                                    fontSize = 12.sp,
                                    color = colors.content.copy(alpha = 0.55f),
                                )
                            }
                            SfIcon("chevron.forward", null, size = 13.dp, tint = colors.tertiaryContent)
                        }
                        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                // --- 3. 投稿列表 ---
                item(key = "uploader-section") {
                    Text(
                        if (uiState.fullList) "全部投稿" else "最近投稿",
                        style = IosTypography.headline,
                        color = colors.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                when (val result = uiState.videos) {
                    is Resource.Success -> {
                        itemsIndexed(
                            videos,
                            key = { _, video -> video.aid },
                            contentType = { _, _ -> "uploader-row" },
                        ) { index, video ->
                            BiliTrackRow(video = video, onClick = { playAll(index) })
                            if (index < videos.lastIndex) {
                                HorizontalDivider(
                                    color = colors.separator,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            if (index == videos.lastIndex && uiState.fullList) {
                                LaunchedEffect(video.aid) { viewModel.loadMore() }
                            }
                        }
                        if (uiState.loadingMore) {
                            item(key = "uploader-loading") {
                                Text(
                                    "加载中…",
                                    fontSize = 13.sp,
                                    color = colors.content.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    is Resource.Error -> item(key = "uploader-error") {
                        Text(
                            result.message ?: "加载失败",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    Resource.Loading -> item(key = "uploader-list-loading") {
                        Text(
                            "加载中…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
