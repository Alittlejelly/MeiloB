package com.ljyh.mei.data.network.bili

import com.ljyh.mei.data.model.bili.BiliArchiveStat
import com.ljyh.mei.data.model.bili.BiliCaptcha
import com.ljyh.mei.data.model.bili.BiliFavFolderCollected
import com.ljyh.mei.data.model.bili.BiliFavFolderListAll
import com.ljyh.mei.data.model.bili.BiliFavResourceList
import com.ljyh.mei.data.model.bili.BiliFingerSpi
import com.ljyh.mei.data.model.bili.BiliNav
import com.ljyh.mei.data.model.bili.BiliPlayUrl
import com.ljyh.mei.data.model.bili.BiliQrGenerate
import com.ljyh.mei.data.model.bili.BiliQrPoll
import com.ljyh.mei.data.model.bili.BiliRanking
import com.ljyh.mei.data.model.bili.BiliRegionDynamic
import com.ljyh.mei.data.model.bili.BiliSearchAll
import com.ljyh.mei.data.model.bili.BiliSmsLogin
import com.ljyh.mei.data.model.bili.BiliSmsSend
import com.ljyh.mei.data.model.bili.BiliVideoDetail
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * B 站网页版公开接口。wbi 类接口的签名由 [BiliInterceptor] 统一追加。
 */
interface BiliApiService {

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    suspend fun qrGenerate(): BiliQrGenerate

    /** 返回原始 Response 以读取 Set-Cookie（登录凭证必须保留原始编码值）。 */
    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/poll")
    suspend fun qrPoll(@Query("qrcode_key") qrcodeKey: String): Response<BiliQrPoll>

    @GET("https://passport.bilibili.com/x/passport-login/captcha")
    suspend fun loginCaptcha(
        @Query("source") source: String = "main_web",
        @Query("t") t: Long,
    ): BiliCaptcha

    @POST("https://passport.bilibili.com/x/passport-login/web/sms/send")
    @FormUrlEncoded
    suspend fun smsSend(
        @Field("cid") cid: String,
        @Field("tel") tel: String,
        @Field("source") source: String = "main_mini_login",
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String,
    ): BiliSmsSend

    /** 返回原始 Response 以读取 Set-Cookie。 */
    @POST("https://passport.bilibili.com/x/passport-login/web/login/sms")
    @FormUrlEncoded
    suspend fun smsLogin(
        @Field("cid") cid: String,
        @Field("tel") tel: String,
        @Field("code") code: String,
        @Field("source") source: String = "main_mini_login",
        @Field("captcha_key") captchaKey: String,
        @Field("keep") keep: String = "1",
    ): Response<BiliSmsLogin>

    @GET("x/web-interface/nav")
    suspend fun nav(): BiliNav

    @GET("x/frontend/finger/spi")
    suspend fun fingerSpi(): BiliFingerSpi

    @GET("x/web-interface/ranking/v2")
    suspend fun ranking(
        @Query("rid") rid: Int,
        @Query("type") type: String = "all",
    ): BiliRanking

    /** 分区最新投稿（dynamic/region 已下线，newlist 为现行接口）。 */
    @GET("x/web-interface/newlist")
    suspend fun newList(
        @Query("rid") rid: Int,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
    ): BiliRegionDynamic

    @GET("x/web-interface/view")
    suspend fun view(@Query("bvid") bvid: String): BiliVideoDetail

    /** view 接口也接受 aid（archive/stat 已下线，不能用于 aid→bvid）。 */
    @GET("x/web-interface/view")
    suspend fun viewByAid(@Query("aid") aid: Long): BiliVideoDetail

    @GET("x/player/wbi/playurl")
    suspend fun playUrl(
        @Query("bvid") bvid: String?,
        @Query("aid") aid: Long?,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 0,
        @Query("fnval") fnval: Int = 16,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("voice_balance") voiceBalance: String = "1",
    ): BiliPlayUrl

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun favFolders(
        @Query("up_mid") upMid: Long,
    ): BiliFavFolderListAll

    /** 收藏的合集 / 收藏的文件夹。 */
    @GET("x/v3/fav/folder/collected/list")
    suspend fun favFolderCollected(
        @Query("up_mid") upMid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web",
    ): BiliFavFolderCollected

    /** 合集（season）内容列表，medias 形状与收藏夹内容一致。 */
    @GET("x/space/fav/season/list")
    suspend fun favSeasonList(
        @Query("season_id") seasonId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
    ): BiliFavResourceList

    @GET("x/v3/fav/resource/list")
    suspend fun favResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20,
    ): BiliFavResourceList

    @GET("x/web-interface/wbi/search/all/v2")
    suspend fun searchAll(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("platform") platform: String = "web",
    ): BiliSearchAll
}
