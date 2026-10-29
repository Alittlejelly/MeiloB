package com.ljyh.mei.ui.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.glass.IosGroupedList
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BiliUploaderGroupViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
) : ViewModel() {

    data class UiState(
        val mid: Long = 0,
        val seasonId: Long = 0,
        val videos: Resource<List<BiliVideo>> = Resource.Loading,
        val totalCount: Int = 0,
        val loadingMore: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initializedForSeason: Long = 0
    private var pageNum = 1
    private var exhausted = false

    fun initialize(mid: Long, seasonId: Long) {
        if (initializedForSeason == seasonId || mid <= 0 || seasonId <= 0) return
        initializedForSeason = seasonId
        _state.value = UiState(mid = mid, seasonId = seasonId)
        pageNum = 1
        exhausted = false
        loadPage(reset = true)
    }

    fun loadMore() {
        if (exhausted || _state.value.loadingMore) return
        val current = (_state.value.videos as? Resource.Success)?.data ?: return
        if (current.size >= _state.value.totalCount && _state.value.totalCount > 0) {
            exhausted = true
            return
        }
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        val s = _state.value
        viewModelScope.launch {
            if (reset) _state.value = s.copy(videos = Resource.Loading)
            else _state.value = _state.value.copy(loadingMore = true)
            val next = if (reset) 1 else pageNum + 1
            when (val result = biliRepository.seasonArchives(s.mid, s.seasonId, pageNum = next)) {
                is Resource.Success -> {
                    val page = result.data
                    val merged = if (reset) page.videos else {
                        val existing = (_state.value.videos as? Resource.Success)?.data.orEmpty()
                        existing + page.videos.filter { new -> existing.none { it.aid == new.aid } }
                    }
                    pageNum = next
                    exhausted = !page.hasMore
                    _state.value = _state.value.copy(
                        videos = Resource.Success(merged),
                        totalCount = merged.size + if (page.hasMore) 1 else 0,
                        loadingMore = false,
                    )
                }
                is Resource.Error -> {
                    exhausted = true
                    _state.value = _state.value.copy(
                        videos = if (reset) Resource.Error(result.message ?: "加载失败") else _state.value.videos,
                        loadingMore = false,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }
}

/** UP 主分组（合集/系列）详情：分页投稿列表，点击播放。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliUploaderGroupScreen(
    mid: Long,
    seasonId: Long,
    groupName: String,
    viewModel: BiliUploaderGroupViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(mid, seasonId) { viewModel.initialize(mid, seasonId) }

    val uiState by viewModel.state.collectAsState()
    val videos = (uiState.videos as? Resource.Success)?.data.orEmpty()

    fun playAll(startIndex: Int) {
        val connection = playerConnection ?: return
        if (videos.getOrNull(startIndex) == null) return
        scope.launch {
            val items = videos.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = UUID.randomUUID().toString(),
                    title = groupName,
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    IosPinnedListPage(
        title = groupName.ifBlank { "分组" },
        onNavigateBack = navController::navigateUp,
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        item(key = "group-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "共 ${videos.size} 个内容",
                    fontSize = 14.sp,
                    color = LocalGlassColors.current.content.copy(alpha = 0.6f),
                )
            }
        }
        when (val result = uiState.videos) {
            is Resource.Success -> {
                if (videos.isNotEmpty()) {
                    item(key = "play-all") {
                        IosGroupedList {
                            IosListRow(
                                title = "播放全部",
                                systemName = "play.fill",
                                showTopSeparator = false,
                                onClick = { playAll(0) },
                            )
                        }
                    }
                }
                itemsIndexed(videos, key = { _, v -> v.aid }, contentType = { _, _ -> "group-row" }) { index, video ->
                    BiliTrackRow(video = video, onClick = { playAll(index) })
                    if (index == videos.lastIndex) {
                        LaunchedEffect(video.aid) { viewModel.loadMore() }
                    }
                }
                if (uiState.loadingMore) {
                    item(key = "group-loading") {
                        Text(
                            "加载中…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            is Resource.Error -> item(key = "group-error") {
                Text(
                    result.message ?: "加载失败",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Resource.Loading -> item(key = "group-list-loading") {
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
