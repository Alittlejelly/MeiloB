package com.ljyh.mei.ui.screen.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.navigation.MeiloXPinnedListPage
import com.ljyh.mei.ui.screen.account.biliVideoGridItems
import com.ljyh.mei.ui.screen.account.rememberBiliGridColumns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 搜索结果的每页条数，与哔哩哔哩 web 搜索的默认页大小一致。 */
private const val BILI_SEARCH_PAGE_SIZE = 20

/**
 * 全站搜索（`x/web-interface/wbi/search/all/v2`）的分页状态。
 * 只保留视频结果：本分叉播放的是 B 站视频的 DASH 音频流。
 */
@HiltViewModel
class BiliSearchViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
) : ViewModel() {

    data class UiState(
        val keyword: String = "",
        val videos: Resource<List<BiliVideo>> = Resource.Loading,
        val page: Int = 1,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadedKeyword: String? = null

    /** 同一关键词重复进入（返回上一页再进来）时不重新请求。 */
    fun search(keyword: String) {
        if (keyword.isBlank()) return
        if (keyword == loadedKeyword && _state.value.videos is Resource.Success) return
        loadedKeyword = keyword
        _state.value = UiState(keyword = keyword, videos = Resource.Loading)
        load(page = 1, append = false)
    }

    fun retry() {
        val keyword = _state.value.keyword
        if (keyword.isBlank()) return
        _state.value = _state.value.copy(videos = Resource.Loading, endReached = false)
        load(page = 1, append = false)
    }

    fun loadMore() {
        val current = _state.value
        if (current.keyword.isBlank() || current.loadingMore || current.endReached) return
        if (current.videos !is Resource.Success) return
        load(page = current.page + 1, append = true)
    }

    private fun load(page: Int, append: Boolean) {
        val keyword = _state.value.keyword
        if (keyword.isBlank()) return
        viewModelScope.launch {
            if (append) _state.value = _state.value.copy(loadingMore = true)
            when (val result = biliRepository.search(keyword, page)) {
                is Resource.Success -> {
                    val previous = if (append) {
                        (_state.value.videos as? Resource.Success)?.data.orEmpty()
                    } else {
                        emptyList()
                    }
                    val merged = (previous + result.data).distinctBy { it.aid to it.cid }
                    _state.value = _state.value.copy(
                        videos = Resource.Success(merged),
                        page = page,
                        loadingMore = false,
                        endReached = result.data.isEmpty() || result.data.size < BILI_SEARCH_PAGE_SIZE,
                    )
                }

                is Resource.Error -> _state.value = if (append) {
                    _state.value.copy(loadingMore = false, endReached = true)
                } else {
                    _state.value.copy(
                        videos = Resource.Error(result.message ?: "搜索失败"),
                        loadingMore = false,
                        endReached = false,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }
}

/** 搜结果页：B 站全站视频搜索结果，点任意一条按顺序播放音频流。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliSearchResultScreen(
    keyword: String,
    viewModel: BiliSearchViewModel = hiltViewModel(),
) {
    val insets = LocalPlayerAwareWindowInsets.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val uiState by viewModel.state.collectAsState()
    val gridColumns = rememberBiliGridColumns()

    LaunchedEffect(keyword) {
        viewModel.search(keyword)
    }

    val videos = (uiState.videos as? Resource.Success)?.data.orEmpty()

    fun playFrom(startIndex: Int) {
        val connection = playerConnection ?: return
        if (videos.getOrNull(startIndex) == null) return
        scope.launch {
            val items = videos.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = "bili_search_${keyword}_${System.currentTimeMillis()}",
                    title = "搜索：$keyword",
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    MeiloXPinnedListPage(
        title = keyword,
        subtitle = when (val result = uiState.videos) {
            is Resource.Success -> "哔哩哔哩 · ${result.data.size} 个结果"
            else -> "哔哩哔哩全站搜索"
        },
        bottomPadding = insets.asPaddingValues().calculateBottomPadding(),
        listState = listState,
    ) {
        when (val result = uiState.videos) {
            is Resource.Success -> {
                if (result.data.isEmpty()) {
                    item(key = "search-empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("没有找到相关内容", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                biliVideoGridItems(
                    videos = result.data,
                    columns = gridColumns,
                    key = { video -> "${video.aid}_${video.cid}" },
                    onReachEnd = viewModel::loadMore,
                ) { index, _ -> playFrom(index) }
                if (uiState.loadingMore) {
                    item(key = "search-loading-more") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

            is Resource.Error -> item(key = "search-error") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        result.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            Resource.Loading -> item(key = "search-loading") {
                Box(
                    Modifier.fillMaxSize().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
