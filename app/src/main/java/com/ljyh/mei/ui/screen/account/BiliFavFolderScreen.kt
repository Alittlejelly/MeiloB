package com.ljyh.mei.ui.screen.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliFavoritesSync
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.data.repository.biliFavPlaylistId
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.ui.component.BiliCover
import com.ljyh.mei.ui.glass.IosGroupedList
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.screen.main.home.biliCount
import com.ljyh.mei.ui.screen.main.home.biliDuration
import com.ljyh.mei.ui.screen.main.home.biliThumb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiliFavFolderViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
    private val favoritesSync: BiliFavoritesSync,
) : ViewModel() {

    data class UiState(
        val folderId: Long = 0,
        val title: String = "收藏夹",
        val videos: Resource<List<BiliVideo>> = Resource.Loading,
        val synced: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initializedForId: Long = 0

    fun initialize(folderId: Long, folderTitle: String) {
        if (initializedForId == folderId) return
        initializedForId = folderId
        _state.value = UiState(folderId = folderId, title = folderTitle)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                synced = biliFavPlaylistId(folderId) in favoritesSync.syncedFolderIds()
            )
        }
        refresh()
    }

    fun refresh() {
        val folderId = _state.value.folderId
        if (folderId <= 0) return
        viewModelScope.launch {
            _state.value = _state.value.copy(videos = Resource.Loading)
            _state.value = _state.value.copy(
                videos = biliRepository.favCollectionResources(folderId, pn = 1, ps = 50).let { result ->
                    when (result) {
                        is Resource.Success -> Resource.Success(result.data.videos)
                        is Resource.Error -> Resource.Error(result.message ?: "加载失败")
                        Resource.Loading -> Resource.Loading
                    }
                }
            )
        }
    }
}

/** B 站收藏夹详情：视频列表 + 播放 + 同步到本地歌单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliFavFolderScreen(
    folderId: Long,
    folderTitle: String,
    viewModel: BiliFavFolderViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderId) {
        viewModel.initialize(folderId, folderTitle)
    }

    val uiState by viewModel.state.collectAsState()

    fun playAll(startIndex: Int) {
        val connection = playerConnection ?: return
        val list = (uiState.videos as? Resource.Success)?.data ?: return
        scope.launch {
            val items = list.map { it.toMediaItem() }
            connection.playQueue(
                ListQueue(
                    id = "bili_fav_$folderId",
                    title = folderTitle,
                    items = items.map { it.mediaId to it },
                    startIndex = startIndex.coerceAtMost(items.lastIndex),
                )
            )
        }
    }

    IosPinnedListPage(
        title = folderTitle,
        onNavigateBack = navController::navigateUp,
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        item(key = "folder-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val count = (uiState.videos as? Resource.Success)?.data?.size ?: 0
                Text(
                    "共 $count 个内容" + if (uiState.synced) " · 已同步" else "",
                    fontSize = 14.sp,
                    color = LocalGlassColors.current.content.copy(alpha = 0.6f),
                )
            }
        }

        when (val result = uiState.videos) {
            is Resource.Success -> {
                if (result.data.isNotEmpty()) {
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
                itemsIndexed(
                    result.data,
                    key = { _, video -> "${video.aid}_${video.cid}" },
                    contentType = { _, _ -> "fav-row" },
                ) { index, video ->
                    BiliTrackRow(video = video, onClick = { playAll(index) })
                }
            }
            is Resource.Error -> item(key = "folder-error") {
                Text(
                    result.message ?: "加载失败",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
            Resource.Loading -> item(key = "folder-loading") {
                Text(
                    "加载中…",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

/** B 站视频行（封面 + 标题 + UP 主 + 时长），首页与收藏夹共用。 */
@Composable
fun BiliTrackRow(
    video: BiliVideo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BiliCover(
                imageUrl = biliThumb(video.cover).orEmpty(),
                modifier = Modifier
                    .width(130.dp)
                    .aspectRatio(16f / 10f)
                    .clip(ContinuousRoundedRectangle(10.dp)),
            )
            Text(
                biliDuration(video.durationMs),
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(ContinuousRoundedRectangle(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = LocalGlassColors.current.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(video.uploader.orEmpty())
                    if (video.playCount > 0) {
                        append(" · ")
                        append(biliCount(video.playCount))
                        append("播放")
                    }
                },
                fontSize = 12.sp,
                color = LocalGlassColors.current.content.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
