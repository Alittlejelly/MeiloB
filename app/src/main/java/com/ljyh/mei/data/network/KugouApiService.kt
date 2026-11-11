package com.ljyh.mei.data.network

import com.ljyh.mei.data.model.kugou.KugouCandidateResponse
import com.ljyh.mei.data.model.kugou.KugouDownloadResponse
import com.ljyh.mei.data.model.kugou.KugouSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * 酷狗歌词接口（BBPlayer 同款链路）。
 *
 * 三个端点分布在不同的 host 上，因此用绝对 URL（Retrofit 支持 @GET 完整 URL），
 * 只需共用一个无拦截器的 OkHttpClient。酷狗接口是 http 明文，
 * 应用已声明 usesCleartextTraffic。
 */
interface KugouApiService {

    @GET("http://mobilecdn.kugou.com/api/v3/search/song")
    suspend fun search(
        @Query("api_ver") apiVer: String = "1",
        @Query("area_code") areaCode: String = "1",
        @Query("correct") correct: String = "1",
        @Query("pagesize") pageSize: Int = 10,
        @Query("plat") plat: String = "2",
        @Query("tag") tag: String = "1",
        @Query("sver") sver: String = "5",
        @Query("showtype") showType: String = "10",
        @Query("page") page: Int = 1,
        @Query("keyword") keyword: String,
        @Query("version") version: String = "8990",
        @Header("User-Agent") userAgent: String = UA_SEARCH,
        @Header("UNI-User-Agent") uniUserAgent: String = UNI_UA,
    ): KugouSearchResponse

    @GET("http://krcs.kugou.com/search")
    suspend fun lyricCandidate(
        // BBPlayer 传 '%20-%20'（空格-短横-空格 的转义形态）；Retrofit 会自动编码 " - "
        @Query("keyword") keyword: String = " - ",
        @Query("ver") ver: String = "1",
        @Query("hash") hash: String,
        @Query("client") client: String = "mob",
        @Query("man") man: String = "yes",
    ): KugouCandidateResponse

    @GET("http://lyrics.kugou.com/download")
    suspend fun lyricDownload(
        @Query("charset") charset: String = "utf8",
        @Query("accesskey") accessKey: String,
        @Query("id") id: String,
        @Query("client") client: String = "mob",
        @Query("fmt") format: String = "lrc",
        @Query("ver") ver: String = "1",
    ): KugouDownloadResponse

    companion object {
        const val UA_SEARCH = "IPhone-8990-searchSong"
        const val UNI_UA = "iOS11.4-Phone8990-1009-0-WiFi"
    }
}
