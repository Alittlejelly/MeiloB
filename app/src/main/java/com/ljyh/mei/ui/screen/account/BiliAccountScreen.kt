package com.ljyh.mei.ui.screen.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ljyh.mei.data.model.bili.BiliAccount
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliFavoritesSync
import com.ljyh.mei.data.repository.BiliFavFolder
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.data.repository.biliFavPlaylistId
import com.ljyh.mei.ui.glass.GlassCard
import com.ljyh.mei.ui.glass.IosGroupedList
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiliAccountViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
    private val favoritesSync: BiliFavoritesSync,
) : ViewModel() {

    val account = biliRepository.accountFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _folders = MutableStateFlow<Resource<List<BiliFavFolder>>>(Resource.Loading)
    val folders: StateFlow<Resource<List<BiliFavFolder>>> = _folders.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val syncedPlaylists = favoritesSync.syncedPlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = when (val result = biliRepository.refreshAccount()) {
                is Resource.Success -> result.data
                else -> biliRepository.account()
            }
            if (user == null) {
                _folders.value = Resource.Success(emptyList())
            } else {
                _folders.value = biliRepository.favFolders(user.mid)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            biliRepository.logout()
            refresh()
        }
    }

    fun syncAllFolders(onResult: (String) -> Unit) {
        val folders = (_folders.value as? Resource.Success)?.data.orEmpty()
        if (folders.isEmpty() || _syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                var ok = 0
                var total = 0
                for (folder in folders) {
                    when (val result = favoritesSync.syncFolder(folder)) {
                        is Resource.Success -> {
                            ok++
                            total += result.data
                        }
                        else -> Unit
                    }
                }
                onResult("已同步 $ok 个收藏夹，共 $total 个内容")
            } finally {
                _syncing.value = false
            }
        }
    }
}

/** B 站账号主页：账号信息 + 收藏夹列表 + 同步入口。 */
@Composable
fun BiliAccountHomeScreen(
    viewModel: BiliAccountViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val account by viewModel.account.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val synced by viewModel.syncedPlaylists.collectAsState()

    IosPinnedListPage(
        title = "我的账号",
        onNavigateBack = navController::navigateUp,
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        val user = account
        if (user == null) {
            // 未登录（或会话刚被清除）：显示登录引导，不做自动跳转，
            // 否则账号信息流首次发射前的空窗会误跳登录页再闪回。
            item(key = "account-login-gate") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { Screen.BiliLogin.navigate(navController) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SfIcon("person.crop.circle", null, size = 42.dp)
                        Text(
                            "未登录，点击登录哔哩哔哩",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        } else {
            item(key = "account-hero") {
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(
                            model = user.avatar,
                            contentDescription = user.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalGlassColors.current.content,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append("Lv${user.level}")
                                    if (user.isVip) append(" · 大会员")
                                    append(" · 硬币 ${"%.1f".format(user.coin)}")
                                },
                                fontSize = 13.sp,
                                color = LocalGlassColors.current.content.copy(alpha = 0.6f),
                            )
                        }
                        Text(
                            "退出登录",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .clickable { viewModel.logout() }
                                .padding(8.dp),
                        )
                    }
                }
            }

            item(key = "sync-all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "我的收藏夹",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalGlassColors.current.content,
                    )
                    Text(
                        if (syncing) "同步中…" else "全部同步",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable(enabled = !syncing) {
                                viewModel.syncAllFolders { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(8.dp),
                    )
                }
            }

            when (val folderResult = folders) {
                is Resource.Success -> {
                    val syncedIds = synced.map { it.id }.toSet()
                    items(folderResult.data, key = { it.id }) { folder ->
                        IosGroupedList {
                            IosListRow(
                                title = folder.title.ifBlank { "收藏夹" },
                                subtitle = "${folder.mediaCount} 个内容" +
                                    if (biliFavPlaylistId(folder.id) in syncedIds) " · 已同步" else "",
                                systemName = "music.note.list",
                                showTopSeparator = false,
                                onClick = {
                                    Screen.BiliFavFolder.navigate(navController) {
                                        addPath(folder.id.toString())
                                        addPath(android.net.Uri.encode(folder.title.ifBlank { "收藏夹" }))
                                    }
                                },
                            )
                        }
                    }
                }
                is Resource.Error -> item(key = "folders-error") {
                    Text(
                        folderResult.message ?: "收藏夹加载失败",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Resource.Loading -> item(key = "folders-loading") {
                    Text(
                        "加载中…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
