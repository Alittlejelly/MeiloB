package com.ljyh.mei.ui.screen.main.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    init {
        viewModelScope.launch {
            val stored = appContext.dataStore.data.first()[BiliHomeRegionKey]
                ?: BILI_MUSIC_RID
            _region.value = BILI_HOME_REGIONS.firstOrNull { it.rid == stored }
                ?: BILI_HOME_REGIONS.first()
            refresh()
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

    /** 每次换队/切分区的加载归属一个代际，晚到的响应按旧代际丢弃。 */
    private var feedGeneration = 0

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
        val rid = _region.value.rid
        val generation = ++feedGeneration
        viewModelScope.launch {
            _isRefreshing.value = true
            latestPage = 1
            latestExhausted = false
            try {
                when (val result = biliRepository.latestInZone(rid, pn = 1, ps = 30)) {
                    is Resource.Success -> {
                        if (generation != feedGeneration) return@launch
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
            } finally {
                if (generation == feedGeneration) _isRefreshing.value = false
            }
        }
    }

    /** 当前分区投稿分页加载。 */
    fun loadMoreLatest() {
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
