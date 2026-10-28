package com.ljyh.mei.data.repository

import com.ljyh.mei.data.repository.BiliFavFolder
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.room.Playlist
import com.ljyh.mei.data.model.room.PlaylistSongCrossRef
import com.ljyh.mei.data.model.room.PlaylistType
import com.ljyh.mei.data.model.room.Song
import com.ljyh.mei.data.model.room.SourceType
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.di.repository.LocalPlaylistRepository
import com.ljyh.mei.di.repository.PlaylistSongCrossRefRepository
import com.ljyh.mei.di.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val BILI_FAV_PLAYLIST_PREFIX = "bili_fav_"

fun biliFavPlaylistId(folderId: Long): String = "$BILI_FAV_PLAYLIST_PREFIX$folderId"

fun BiliVideo.toSongEntity() = Song(
    id = mediaId,
    title = title,
    artist = listOfNotNull(uploader?.takeIf { it.isNotBlank() }),
    album = "哔哩哔哩",
    cover = cover.orEmpty(),
    duration = durationMs,
    sourceType = SourceType.STREAM,
)

/**
 * 把 B 站收藏夹同步为本地歌单（Room），离线可浏览、可播放。
 */
@Singleton
class BiliFavoritesSync @Inject constructor(
    private val biliRepository: BiliRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val songRepository: SongRepository,
    private val crossRefRepository: PlaylistSongCrossRefRepository,
) {

    /** 同步单个收藏夹，返回导入的视频数量。 */
    suspend fun syncFolder(folder: BiliFavFolder): Resource<Int> {
        val playlistId = biliFavPlaylistId(folder.id)
        val (title, videos) = when (val result = biliRepository.favFolderAll(folder.id)) {
            is Resource.Success -> result.data
            is Resource.Error -> return Resource.Error(result.message ?: "收藏夹内容加载失败")
            Resource.Loading -> return Resource.Loading
        }
        if (videos.isEmpty()) return Resource.Error("收藏夹是空的")

        songRepository.insertSongs(videos.map { it.toSongEntity() })
        localPlaylistRepository.insertPlaylist(
            Playlist(
                id = playlistId,
                title = title.ifBlank { folder.title.ifBlank { "收藏夹" } },
                cover = videos.firstOrNull()?.cover.orEmpty(),
                author = "bilibili",
                authorName = "B站收藏夹",
                authorAvatar = "",
                count = videos.size,
                type = PlaylistType.USER,
                description = "同步自哔哩哔哩收藏夹",
            )
        )
        crossRefRepository.deleteByPlaylist(playlistId)
        crossRefRepository.insertAll(
            videos.mapIndexed { index, video ->
                PlaylistSongCrossRef(playlistId = playlistId, songId = video.mediaId, sortOrder = index)
            }
        )
        return Resource.Success(videos.size)
    }

    /** 已同步到本地的收藏夹歌单（实时）。 */
    fun syncedPlaylists(): Flow<List<Playlist>> =
        localPlaylistRepository.getAllPlaylist().map { rows ->
            rows.filter { it.id.startsWith(BILI_FAV_PLAYLIST_PREFIX) }
        }

    suspend fun syncedFolderIds(): Set<String> =
        localPlaylistRepository.getAllPlaylist().first()
            .filter { it.id.startsWith(BILI_FAV_PLAYLIST_PREFIX) }
            .map { it.id }
            .toSet()
}
