package com.ljyh.mei.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import timber.log.Timber

@OptIn(UnstableApi::class)
class MusicLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception

        // 媒体地址解析失败几乎都是瞬时网络问题（DNS 抖动、连接被重置）。以前对
        // SourceNotFoundException 一律不重试，一次抖动就会让整首歌直接报错；这里改成
        // 短延迟重试，重试会重新走一遍 ResolvingDataSource（重新取 playurl），
        // 而不是复用失败结果。总次数仍由基类的 getMinimumLoadableRetryCount 兜底。
        if (exception is SourceNotFoundException) {
            Timber.tag("MusicLoadErrorHandlingPolicy").w(
                "source resolve failed: %s", exception.message,
            )
            return SOURCE_RESOLVE_RETRY_DELAY_MS
        }

        // CDN 明确拒绝（403/416 等）时把状态码和地址打出来，否则日志里只有
        // "BAD_HTTP_STATUS"，无法判断是地址过期、风控还是缓存 key 串了。
        if (exception is HttpDataSource.InvalidResponseCodeException) {
            Timber.tag("MusicLoadErrorHandlingPolicy").w(
                "http %s %s", exception.responseCode, exception.dataSpec.uri,
            )
            // 404 说明稿件/音频确实不在了，重试没有意义。
            if (exception.responseCode == 404) return C.TIME_UNSET
        }

        Timber.tag("MusicLoadErrorHandlingPolicy").d(loadErrorInfo.toString())

        // 其他网络错误，使用默认的指数退避重试
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private companion object {
        /** 解析重试间隔：足够等到 DNS/连接恢复，又不至于让用户干等。 */
        const val SOURCE_RESOLVE_RETRY_DELAY_MS = 700L
    }
}
