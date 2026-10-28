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
import javax.inject.Inject

/** 库 → 歌单页：B 站收藏页（我的收藏夹 + 收藏合集）。 */
@HiltViewModel
class BiliLibraryViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
) : ViewModel() {

    val account: StateFlow<BiliAccount?> = biliRepository.accountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _created = MutableStateFlow<Resource<List<BiliFavFolder>>>(Resource.Loading)
    val created: StateFlow<Resource<List<BiliFavFolder>>> = _created.asStateFlow()

    private val _collected = MutableStateFlow<Resource<List<BiliFavFolder>>>(Resource.Loading)
    val collected: StateFlow<Resource<List<BiliFavFolder>>> = _collected.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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

    fun refresh(mid: Long? = account.value?.mid) {
        mid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _created.value = biliRepository.favFolders(mid)
                _collected.value = biliRepository.favCollectedFolders(mid)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
