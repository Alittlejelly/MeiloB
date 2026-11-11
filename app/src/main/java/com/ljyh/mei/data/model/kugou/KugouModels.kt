package com.ljyh.mei.data.model.kugou

import com.google.gson.annotations.SerializedName

/**
 * 酷狗歌词获取三步链路的响应模型。
 *
 * 参考 BBPlayer (bbplayer-app/BBPlayer) 的 kugou/api.ts 实现：
 * 1. [KugouSearchResponse] 搜索歌曲拿到 hash；
 * 2. [KugouCandidateResponse] 用 hash 换歌词 candidate(id + accesskey)；
 * 3. [KugouDownloadResponse] 用 id + accesskey 下载 base64 编码的 LRC 文本。
 */
data class KugouSearchResponse(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("data") val data: Data? = null,
) {
    data class Data(
        @SerializedName("info") val info: List<SongInfo>? = null,
    )

    data class SongInfo(
        @SerializedName("hash") val hash: String? = null,
        @SerializedName("songname") val songName: String? = null,
        @SerializedName("singername") val singerName: String? = null,
        /** 秒 */
        @SerializedName("duration") val duration: Int = 0,
    )
}

data class KugouCandidateResponse(
    @SerializedName("candidates") val candidates: List<Candidate>? = null,
) {
    data class Candidate(
        @SerializedName("id") val id: String? = null,
        @SerializedName("accesskey") val accessKey: String? = null,
    )
}

data class KugouDownloadResponse(
    @SerializedName("content") val content: String? = null,
)
