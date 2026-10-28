package com.ljyh.mei.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.utils.dataStore
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.account.BiliTrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

@HiltViewModel
class BiliSearchViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** B 站全站搜索接口需要登录（SESSDATA），未登录时结果为空。 */
    val account: kotlinx.coroutines.flow.StateFlow<com.ljyh.mei.data.model.bili.BiliAccount?> =
        biliRepository.accountFlow.let { flow ->
            val state = MutableStateFlow<com.ljyh.mei.data.model.bili.BiliAccount?>(null)
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    data class UiState(
        val history: List<String> = emptyList(),
        val results: Resource<List<BiliVideo>> = Resource.Loading,
        val searchedKeyword: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var page = 1
    private var exhausted = false
    private val loaded = mutableListOf<BiliVideo>()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(history = decodeSearchHistory(readHistoryRaw()))
        }
    }

    private suspend fun readHistoryRaw(): String? =
        appContext.dataStore.data.first()[SearchHistoryKey]

    fun search(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        if (account.value == null) return
        searchJob?.cancel()
        exhausted = false
        page = 1
        loaded.clear()
        _state.value = _state.value.copy(results = Resource.Loading, searchedKeyword = query)
        searchJob = viewModelScope.launch {
            appContext.recordSearchHistory(query)
            _state.value = _state.value.copy(history = decodeSearchHistory(readHistoryRaw()))
            when (val result = biliRepository.search(query, page = 1)) {
                is Resource.Success -> {
                    loaded.addAll(result.data.distinctBy { it.aid })
                    if (result.data.isEmpty()) exhausted = true
                    _state.value = _state.value.copy(results = Resource.Success(loaded.toList()))
                }
                is Resource.Error -> _state.value =
                    _state.value.copy(results = Resource.Error(result.message ?: "搜索失败"))
                Resource.Loading -> Unit
            }
        }
    }

    fun loadMore() {
        if (exhausted || searchJob?.isActive == true) return
        val keyword = _state.value.searchedKeyword
        if (keyword.isEmpty()) return
        searchJob = viewModelScope.launch {
            val next = page + 1
            when (val result = biliRepository.search(keyword, page = next)) {
                is Resource.Success -> {
                    if (result.data.isEmpty()) {
                        exhausted = true
                    } else {
                        page = next
                        loaded.addAll(result.data.filter { new -> loaded.none { it.aid == new.aid } })
                        _state.value = _state.value.copy(results = Resource.Success(loaded.toList()))
                    }
                }
                else -> exhausted = true
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            appContext.clearSearchHistory()
            _state.value = _state.value.copy(history = emptyList())
        }
    }

    /** 搜索结果缺少 cid：按 bvid 补齐视频详情。 */
    suspend fun resolveVideo(video: BiliVideo): BiliVideo = when {
        video.cid > 0 -> video
        video.bvid == null -> video
        else -> (biliRepository.videoDetail(video.bvid) as? Resource.Success)?.data ?: video
    }
}

/** B 站搜索页：关键词搜索视频，结果可直接播放。 */
@Composable
fun SearchLandingScreen(
    isNavigationTab: Boolean = false,
    viewModel: BiliSearchViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val state by viewModel.state.collectAsState()
    val account by viewModel.account.collectAsState()

    // 底部共享搜索工具栏（MainActivity 组合）通过总线驱动本页的全站搜索。
    val bottomSearch = com.ljyh.mei.ui.local.LocalBottomSearchState.current
    DisposableEffect(bottomSearch, viewModel) {
        bottomSearch.submit = { keyword ->
            keyboardController?.hide()
            viewModel.search(keyword)
        }
        onDispose { bottomSearch.submit = null }
    }

    fun playVideos(videos: List<BiliVideo>, startIndex: Int) {
        val connection = playerConnection ?: return
        if (videos.getOrNull(startIndex) == null) return
        scope.launch {
            // 只解析点击的这条（缺 cid 的其余条目由播放器按 aid 兜底解析），
            // 否则要串行请求整页视频详情，点击会卡好几秒毫无反应。
            val clicked = viewModel.resolveVideo(videos[startIndex])
            val items = videos.map { video ->
                if (video.aid == clicked.aid) clicked else video
            }.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = "bili_search_" + System.currentTimeMillis(),
                    title = "搜索：" + state.searchedKeyword,
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    IosPinnedListPage(
        title = "搜索",
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        if (account == null) {
            item(key = "search-login-gate") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SfIcon("person.crop.circle", contentDescription = null, size = 40.dp, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "登录 B 站后可使用全站搜索",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "去登录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { Screen.BiliLogin.navigate(navController) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        } else if (state.searchedKeyword.isEmpty()) {
            if (state.history.isNotEmpty()) {
                item(key = "history-clear") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "搜索历史",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalGlassColors.current.content,
                        )
                        Text(
                            "清空",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable { viewModel.clearHistory() }
                                .padding(6.dp),
                        )
                    }
                }
                itemsIndexed(
                    state.history,
                    key = { _, item -> "history_$item" },
                    contentType = { _, _ -> "history" },
                ) { _, keyword ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.search(keyword)
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SfIcon("timer", contentDescription = null, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            keyword,
                            fontSize = 14.sp,
                            color = LocalGlassColors.current.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            when (val result = state.results) {
                is Resource.Success -> {
                    if (result.data.isEmpty()) {
                        item(key = "search-empty") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "没有找到相关内容",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        result.data,
                        key = { _, video -> "result_${video.aid}_${video.cid}" },
                        contentType = { _, _ -> "result" },
                    ) { index, video ->
                        BiliTrackRow(video = video, onClick = { playVideos(result.data, index) })
                        if (index == result.data.lastIndex) {
                            LaunchedEffect(video.aid) { viewModel.loadMore() }
                        }
                    }
                }
                is Resource.Error -> item(key = "search-error") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            result.message ?: "搜索失败",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                Resource.Loading -> item(key = "search-loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "搜索中…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
