package com.ljyh.mei.ui.screen.main.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.constants.BiliGuessLikeEnabledKey
import com.ljyh.mei.constants.BiliHomeRegionKey
import com.ljyh.mei.data.model.bili.BILI_HOME_REGIONS
import com.ljyh.mei.data.model.bili.BILI_MUSIC_RID
import com.ljyh.mei.data.model.bili.BiliAccount
import com.ljyh.mei.data.model.bili.BiliRegion
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliFavFolder
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val biliRepository: BiliRepository,
) : ViewModel() {
    val account: StateFlow<BiliAccount?> = biliRepository.accountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val regions: List<BiliRegion> = BILI_HOME_REGIONS

    private val _region = MutableStateFlow(BILI_HOME_REGIONS.first { it.rid == BILI_MUSIC_RID })
    val region: StateFlow<BiliRegion> = _region.asStateFlow()

    private val _feed = MutableStateFlow<Resource<List<BiliVideo>>>(Resource.Loading)
    val feed: StateFlow<Resource<List<BiliVideo>>> = _feed.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadingMoreLatest = MutableStateFlow(false)
    val loadingMoreLatest: StateFlow<Boolean> = _loadingMoreLatest.asStateFlow()

    private var latestPage = 1
    private var latestExhausted = false

    private val _folders = MutableStateFlow<Resource<List<BiliFavFolder>>>(Resource.Loading)
    val folders: StateFlow<Resource<List<BiliFavFolder>>> = _folders.asStateFlow()

    /** 猜你喜欢开关（设置 → 内容）。开启后首页用公开收藏夹内容推荐，不读取隐私收藏夹。 */
    private val _guessLike = MutableStateFlow(false)
    val guessLike: StateFlow<Boolean> = _guessLike.asStateFlow()

    /** 当前信息流标题（播放队列命名用）。 */
    val feedTitle: String
        get() = if (_guessLike.value) GUESS_LIKE_TITLE else _region.value.label

    /** 信息流为空时的提示文案。 */
    val feedEmptyText: String
        get() = if (_guessLike.value) {
            "公开收藏夹里还没有内容，先去收藏一些视频吧"
        } else {
            "${_region.value.label}暂时没有新投稿"
        }

    init {
        viewModelScope.launch {
            val stored = appContext.dataStore.data.first()[BiliHomeRegionKey]
                ?: BILI_MUSIC_RID
            _region.value = BILI_HOME_REGIONS.firstOrNull { it.rid == stored }
                ?: BILI_HOME_REGIONS.first()
            refresh()
        }
        // 开关实时生效：设置页切换后首页立即换源，无需重启。
        viewModelScope.launch {
            appContext.dataStore.data
                .map { it[BiliGuessLikeEnabledKey] ?: false }
                .collectLatest { enabled ->
                    if (enabled != _guessLike.value) {
                        _guessLike.value = enabled
                        feedGeneration++
                        loaded.clear()
                        _feed.value = Resource.Loading
                        refresh()
                    }
                }
        }
        viewModelScope.launch {
            account.collectLatest { user ->
                if (user == null) {
                    _folders.value = Resource.Success(emptyList())
                } else {
                    loadFolders(user.mid)
                }
            }
        }
    }

    private val loaded = mutableListOf<BiliVideo>()

    /** 每次换队/切分区/切模式的加载归属一个代际，晚到的响应按旧代际丢弃。 */
    private var feedGeneration = 0

    // ---- 猜你喜欢推荐池 ----

    private val guessPool = mutableListOf<BiliVideo>()
    private var guessPoolLoaded = false
    private var guessPoolCursor = 0

    private companion object {
        const val GUESS_LIKE_TITLE = "猜你喜欢"
        const val GUESS_PAGE_SIZE = 30
        const val GUESS_FOLDER_CAP = 8
        const val GUESS_FOLDER_PAGE_SIZE = 20
        const val GUESS_FOLDER_ITEM_CAP = 40
        const val GUESS_POOL_TARGET = 240
    }

    /** 切换首页分区并持久化选择。 */
    fun selectRegion(target: BiliRegion) {
        if (target.rid == _region.value.rid) return
        _region.value = target
        feedGeneration++
        loaded.clear()
        _feed.value = Resource.Loading
        viewModelScope.launch {
            appContext.dataStore.edit { it[BiliHomeRegionKey] = target.rid }
        }
        refresh()
    }

    fun refresh() {
        val generation = ++feedGeneration
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (_guessLike.value) refreshGuessLike(generation) else refreshRegion(generation)
            } finally {
                if (generation == feedGeneration) _isRefreshing.value = false
            }
        }
    }

    private suspend fun refreshRegion(generation: Int) {
        val rid = _region.value.rid
        latestPage = 1
        latestExhausted = false
        when (val result = biliRepository.latestInZone(rid, pn = 1, ps = 30)) {
            is Resource.Success -> {
                if (generation != feedGeneration) return
                loaded.clear()
                loaded.addAll(result.data.distinctBy { it.aid })
                if (loaded.size < 30) latestExhausted = true
                _feed.value = Resource.Success(loaded.toList())
            }
            is Resource.Error -> if (generation == feedGeneration && _feed.value !is Resource.Success) {
                _feed.value = Resource.Error(result.message ?: "加载失败")
            }
            Resource.Loading -> Unit
        }
    }

    /** 猜你喜欢：重建推荐池并展示第一批。 */
    private suspend fun refreshGuessLike(generation: Int) {
        val user = account.value
        if (user == null) {
            if (generation == feedGeneration) {
                _feed.value = Resource.Error("登录后才能使用猜你喜欢")
            }
            return
        }
        val pool = buildGuessPool(user.mid)
        if (generation != feedGeneration) return
        when (pool) {
            null -> if (_feed.value !is Resource.Success) {
                _feed.value = Resource.Error("收藏夹加载失败，下拉重试")
            }
            else -> {
                guessPool.clear()
                guessPool.addAll(pool)
                guessPoolLoaded = true
                guessPoolCursor = minOf(GUESS_PAGE_SIZE, pool.size)
                _feed.value = Resource.Success(pool.take(guessPoolCursor))
            }
        }
    }

    /**
     * 只读用户**自建且公开**（visibility==10）的收藏夹；收藏来的合集不读取
     * （其内容属于他人，超出"用户自己的收藏夹"范围）。
     * 返回 null 表示所有请求都失败；空列表表示确实没有可用内容。
     */
    private suspend fun buildGuessPool(mid: Long): List<BiliVideo>? = coroutineScope {
        when (val foldersResult = biliRepository.favFolders(mid)) {
            is Resource.Success -> {
                val publicFolders = foldersResult.data
                    .filter { it.isPublic && it.mediaCount > 0 }
                    .sortedByDescending { it.mediaCount }
                    .take(GUESS_FOLDER_CAP)
                if (publicFolders.isEmpty()) return@coroutineScope emptyList()
                val pages = publicFolders.map { folder ->
                    async {
                        val perFolder = mutableListOf<BiliVideo>()
                        var pn = 1
                        while (perFolder.size < GUESS_FOLDER_ITEM_CAP) {
                            val result = biliRepository.favFolderResources(
                                folder.id, pn, ps = GUESS_FOLDER_PAGE_SIZE,
                            )
                            if (result !is Resource.Success) break
                            perFolder += result.data.videos
                            if (!result.data.hasMore) break
                            pn++
                        }
                        perFolder
                    }
                }.awaitAll()
                // 收藏夹列表成功即视为可用：单个文件夹读失败只少一路来源，
                // 不应把整个推荐判为失败（返回 null 会让首页显示错误而非部分内容）。
                pages.flatten().distinctBy { it.aid }
            }
            else -> null
        }
    }

    /** 当前分区投稿分页加载。 */
    fun loadMoreLatest() {
        if (_guessLike.value) {
            loadMoreGuess()
            return
        }
        if (latestExhausted || _loadingMoreLatest.value || _feed.value !is Resource.Success) return
        val rid = _region.value.rid
        val generation = feedGeneration
        viewModelScope.launch {
            _loadingMoreLatest.value = true
            try {
                val next = latestPage + 1
                when (val result = biliRepository.latestInZone(rid, pn = next)) {
                    is Resource.Success -> {
                        if (generation != feedGeneration) return@launch
                        if (result.data.isEmpty()) {
                            latestExhausted = true
                        } else {
                            latestPage = next
                            val fresh = result.data.filter { new -> loaded.none { it.aid == new.aid } }
                            if (fresh.isEmpty()) {
                                latestExhausted = true
                            } else {
                                loaded.addAll(fresh)
                                _feed.value = Resource.Success(loaded.toList())
                            }
                        }
                    }
                    else -> Unit
                }
            } finally {
                if (generation == feedGeneration) _loadingMoreLatest.value = false
            }
        }
    }

    /** 猜你喜欢：从推荐池取下一批；池子见底时重建一次。 */
    private fun loadMoreGuess() {
        if (_loadingMoreLatest.value || _feed.value !is Resource.Success) return
        if (guessPoolCursor < guessPool.size) {
            guessPoolCursor = minOf(guessPoolCursor + GUESS_PAGE_SIZE, guessPool.size)
            _feed.value = Resource.Success(guessPool.take(guessPoolCursor))
        } else if (guessPool.isNotEmpty() && guessPool.size < GUESS_POOL_TARGET) {
            // 池子小于一屏多时值得重新拉取；否则视为收藏内容已放完。
            refresh()
        }
    }

    private fun loadFolders(mid: Long) {
        viewModelScope.launch {
            _folders.value = Resource.Loading
            _folders.value = biliRepository.favFolders(mid)
        }
    }

    /** 点击搜索结果等缺少 cid 的条目前，先补齐视频详情。 */
    suspend fun resolveVideo(video: BiliVideo): BiliVideo? = when {
        video.cid > 0 -> video
        video.bvid != null -> (biliRepository.videoDetail(video.bvid) as? Resource.Success)?.data
        else -> null
    }
}
