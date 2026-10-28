package com.ljyh.mei.data.model.bili

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.util.UnstableApi
import com.ljyh.mei.data.model.MediaMetadata

/** B 站视频在播放器中的 mediaId 前缀。 */
const val BILI_MEDIA_ID_PREFIX = "bili_"

/** aid 与 cid 都未知的临时 mediaId（等待视频详情补齐）。 */
const val BILI_MEDIA_ID_UNKNOWN_CID = "bili_0_0"

/** 已登录的 B 站账号（本地会话缓存）。 */
data class BiliAccount(
    val mid: Long,
    val name: String,
    val avatar: String,
    val level: Int,
    val coin: Double,
    val isVip: Boolean,
)

/** B 站接口返回的封面多为 http 协议，统一升级 https（更快，且部分场景明文会被拦）。 */
fun biliHttpsUrl(raw: String?): String? {
    val url = raw?.takeIf { it.isNotBlank() } ?: return null
    return when {
        url.startsWith("https://") -> url
        url.startsWith("http://") -> "https://" + url.substringAfter("http://")
        url.startsWith("//") -> "https:$url"
        else -> url
    }
}

/** mediaId 形如 `bili_{aid}_{cid}`。 */
object BiliMediaId {
    fun create(aid: Long, cid: Long): String = "${BILI_MEDIA_ID_PREFIX}${aid}_${cid}"

    fun isBili(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(BILI_MEDIA_ID_PREFIX)

    data class Parts(val aid: Long, val cid: Long)

    fun parse(mediaId: String): Parts? {
        if (!mediaId.startsWith(BILI_MEDIA_ID_PREFIX)) return null
        val body = mediaId.removePrefix(BILI_MEDIA_ID_PREFIX)
        val aid = body.substringBefore('_').toLongOrNull() ?: return null
        val cid = body.substringAfter('_', "").toLongOrNull() ?: return null
        return Parts(aid, cid)
    }
}

data class BiliVideo(
    val aid: Long,
    val bvid: String?,
    val cid: Long,
    val title: String,
    val uploader: String?,
    val uploaderMid: Long,
    val cover: String?,
    /** 毫秒 */
    val durationMs: Long,
    val playCount: Long = 0,
    val danmakuCount: Long = 0,
    val likeCount: Long = 0,
) {
    val mediaId: String
        get() = BiliMediaId.create(aid, cid)
}

fun BiliArchive.toBiliVideo(): BiliVideo = BiliVideo(
    aid = aid,
    bvid = bvid,
    cid = cid,
    title = title.orEmpty(),
    uploader = uploaderName,
    uploaderMid = uploaderMid,
    cover = biliHttpsUrl(pic),
    durationMs = duration * 1000L,
    playCount = playCount,
    danmakuCount = danmakuCount,
    likeCount = likeCount,
)

fun BiliVideoDetail.Data.toBiliVideo(): BiliVideo = BiliVideo(
    aid = aid,
    bvid = bvid,
    cid = cid,
    title = title.orEmpty(),
    uploader = owner?.name,
    uploaderMid = owner?.mid ?: 0,
    cover = biliHttpsUrl(pic),
    durationMs = duration * 1000L,
    playCount = stat?.view ?: 0,
    likeCount = stat?.like ?: 0,
    danmakuCount = stat?.danmaku ?: 0,
)

fun BiliFavResourceList.Data.Media.toBiliVideo(): BiliVideo = BiliVideo(
    aid = id,
    bvid = bvid,
    cid = cid,
    title = title.orEmpty(),
    uploader = upper?.name,
    uploaderMid = upper?.mid ?: 0,
    cover = biliHttpsUrl(cover),
    durationMs = duration * 1000L,
    playCount = cntInfo?.play ?: 0,
    danmakuCount = cntInfo?.danmaku ?: 0,
)

fun BiliSearchAll.Data.Block.Item.toBiliVideo(): BiliVideo = BiliVideo(
    aid = aid,
    bvid = bvid,
    // 搜索结果不返回 cid，播放前需要先拉取视频详情。
    cid = 0,
    title = title.orEmpty().replace(Regex("</?em[^>]*>"), ""),
    uploader = author,
    uploaderMid = mid,
    cover = biliHttpsUrl(pic?.takeIf { it.startsWith("http") || it.startsWith("//") }),
    durationMs = parseBiliDuration(duration),
    playCount = play,
    danmakuCount = danmaku,
)

fun parseBiliDuration(raw: String?): Long {
    val parts = raw?.trim()?.split(":")?.map { it.toLongOrNull() ?: 0L } ?: return 0
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    } * 1000
}

fun BiliVideo.toMediaMetadata() = MediaMetadata(
    id = aid,
    title = title,
    coverUrl = cover.orEmpty(),
    artists = listOf(MediaMetadata.Artist(id = uploaderMid, name = uploader.orEmpty())),
    duration = durationMs,
    album = MediaMetadata.Album(id = uploaderMid, title = "哔哩哔哩"),
    isBili = true,
)

/** 构建可直接入队的完整 MediaItem（B 站条目不走网易云的按需补齐流程）。 */
@OptIn(UnstableApi::class)
fun BiliVideo.toMediaItem(): MediaItem {
    val metadata = toMediaMetadata()
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(mediaId) // 占位，播放时由 ResolvingDataSource 解析
        .setCustomCacheKey(mediaId)
        .setMimeType("audio/mp4")
        .setTag(metadata)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(uploader.orEmpty())
                .setArtist(uploader.orEmpty())
                .setAlbumTitle("哔哩哔哩")
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setArtworkUri((cover ?: "").let { android.net.Uri.parse(it) })
                .setExtras(Bundle().apply {
                    putLong("duration", durationMs)
                    putString("bvid", bvid)
                    putLong("cid", cid)
                })
                .build()
        )
        .build()
}
