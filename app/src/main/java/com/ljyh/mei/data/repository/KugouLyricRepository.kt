package com.ljyh.mei.data.repository

import com.ljyh.mei.data.network.KugouApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.Base64

/** 酷狗搜索命中的歌曲（hash 为歌词链路主键）。 */
data class KugouSong(
    val hash: String,
    val title: String,
    val artist: String,
    /** 秒 */
    val durationSec: Int,
)

/**
 * 酷狗歌词仓库（BBPlayer 方案移植）。
 *
 * 链路：search(keyword) -> lyricCandidate(hash) -> lyricDownload(id, accesskey)，
 * 下载内容是 base64 编码的 LRC 文本。
 */
@Singleton
class KugouLyricRepository @Inject constructor(
    private val api: KugouApiService,
) {

    suspend fun search(keyword: String, limit: Int = 10): List<KugouSong> {
        return try {
            val resp = api.search(keyword = keyword, pageSize = limit)
            if (resp.status != 1) return emptyList()
            resp.data?.info.orEmpty().mapNotNull { song ->
                val hash = song.hash?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                KugouSong(
                    hash = hash,
                    title = song.songName.orEmpty().ifBlank { song.singerName.orEmpty() },
                    artist = song.singerName.orEmpty(),
                    durationSec = song.duration,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("KugouRepo").w(e, "search failed: $keyword")
            emptyList()
        }
    }

    /**
     * 在前 [candidates] 个搜索结果中挑选与 [durationMs] 相差 ≤ [maxDiffSec] 秒的精确时长匹配，
     * 找不到则退回第一条（与 BBPlayer QQ/酷狗策略一致）。
     */
    fun pickBestMatch(songs: List<KugouSong>, durationMs: Long, candidates: Int = 5): KugouSong? {
        if (songs.isEmpty()) return null
        val targetSec = (durationMs / 1000.0).toInt()
        val head = songs.take(candidates)
        return head.firstOrNull { kotlin.math.abs(it.durationSec - targetSec) <= 3 } ?: head.first()
    }

    /** 下载 LRC 文本；失败返回 null。 */
    suspend fun fetchLrc(hash: String): String? {
        return try {
            val candidateResp = api.lyricCandidate(hash = hash)
            val candidate = candidateResp.candidates?.firstOrNull()
                ?.takeIf { !it.id.isNullOrBlank() && !it.accessKey.isNullOrBlank() }
            if (candidate == null) {
                Timber.tag("KugouRepo").w("no lyric candidates for hash=$hash")
                return null
            }
            val download = api.lyricDownload(accessKey = candidate.accessKey!!, id = candidate.id!!)
            val content = download.content?.takeIf { it.isNotBlank() } ?: return null
            runCatching {
                String(Base64.getDecoder().decode(content), Charsets.UTF_8)
            }.onFailure {
                Timber.tag("KugouRepo").w(it, "base64 decode failed for hash=$hash")
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("KugouRepo").w(e, "fetchLrc failed for hash=$hash")
            null
        }
    }

    /** 一步到位：搜索关键词 -> 时长匹配 -> 下载 LRC。 */
    suspend fun searchAndFetchLrc(keyword: String, durationMs: Long): Pair<KugouSong, String>? {
        val songs = search(keyword)
        val best = pickBestMatch(songs, durationMs) ?: return null
        val lrc = fetchLrc(best.hash) ?: return null
        return best to lrc
    }
}
