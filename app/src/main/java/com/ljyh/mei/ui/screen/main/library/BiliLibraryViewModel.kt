package com.ljyh.mei.ui.screen.main.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.model.bili.BiliAccount
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliFavFolder
import com.ljyh.mei.data.repository.BiliRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** 库 → 歌单页：B 站收藏页（我的收藏夹 + 收藏合集）。 */
@HiltViewModel
class BiliLibraryViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
) : ViewModel() {

    val account: StateFlow<BiliAccount?> = biliRepository.accountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, biliRepository.cachedAccount())

    private val _created = MutableStateFlow<Resource<List<BiliFavFolder>>>(Resource.Loading)
    val created: StateFlow<Resource<List<BiliFavFolder>>> = _created.asStateFlow()

    private val _collected = MutableStateFlow<Resource<List<BiliFavFolder>>>(Resource.Loading)
    val collected: StateFlow<Resource<List<BiliFavFolder>>> = _collected.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 已经为哪个 mid 做过"空列表 → 会话校验"，见 [revalidateIfBothEmpty]。 */
    private var revalidatedFor: Long? = null

    init {
        viewModelScope.launch {
            account.collectLatest { user ->
                if (user == null) {
                    _created.value = Resource.Success(emptyList())
                    _collected.value = Resource.Success(emptyList())
                } else {
                    refresh(user.mid)
                }
            }
        }
    }

    fun refresh(mid: Long? = account.value?.mid, force: Boolean = false) {
        mid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _created.value = biliRepository.favFolders(mid, force)
                _collected.value = biliRepository.favCollectedFolders(mid, force = force)
                revalidateIfBothEmpty(mid)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 两个列表都空时校验一次会话。
     *
     * 失效的 SESSDATA 不会让 created/list-all 报错：B 站按匿名处理，只返回公开收藏夹，
     * 私有夹全被过滤，于是 code=0 但 list 为空，UI 只能显示「还没有收藏夹内容」。
     * 这里主动打一次 nav：确认真的掉登录就清 cookie，让界面回到「登录 B 站」，
     * 而不是停在一个看起来正常的空列表上。
     */
    private suspend fun revalidateIfBothEmpty(mid: Long) {
        // 每个 mid 只校验一次：refreshAccount() 成功时会写偏好，进而让 accountFlow
        // 再发一轮、collectLatest 又触发 refresh，没有这个闸门会变成自激循环。
        if (revalidatedFor == mid) return
        val created = _created.value
        val collected = _collected.value
        val bothEmpty = created is Resource.Success && created.data.isEmpty() &&
            collected is Resource.Success && collected.data.isEmpty()
        if (!bothEmpty) return
        revalidatedFor = mid
        Timber.tag("BiliDiag").w("fav lists empty for mid=%s, revalidating session", mid)
        when (val check = biliRepository.refreshAccount()) {
            is Resource.Success -> if (check.data == null) {
                Timber.tag("BiliDiag").w("session invalid, clearing stored cookies")
                biliRepository.logout()
            }
            is Resource.Error -> Timber.tag("BiliDiag").w(
                "session revalidation failed: %s", check.message,
            )
            Resource.Loading -> Unit
        }
    }
}
