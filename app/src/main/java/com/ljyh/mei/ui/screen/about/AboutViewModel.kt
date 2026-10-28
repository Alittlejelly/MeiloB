package com.ljyh.mei.ui.screen.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.di.repository.QQSongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val qqSongRepo: QQSongRepository
) : ViewModel() {

    var lastResult: String = ""
        private set

    fun deleteAllQQSongs() {
        viewModelScope.launch {
            try {
                qqSongRepo.deleteAll()
                lastResult = "QQ音乐ID数据已清除"
            } catch (e: Exception) {
                lastResult = "清除失败: ${e.message}"
            }
        }
    }
}
