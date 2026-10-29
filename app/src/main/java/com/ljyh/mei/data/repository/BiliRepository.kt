package com.ljyh.mei.data.repository

import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.ljyh.mei.data.model.bili.BILI_AUDIO_ID_EXHIGH
import com.ljyh.mei.data.model.bili.BILI_AUDIO_ID_HIRES
import com.ljyh.mei.data.model.bili.BILI_AUDIO_ID_STANDARD
import com.ljyh.mei.data.model.bili.BILI_MUSIC_RID
import com.ljyh.mei.data.model.bili.BiliAccount
import com.ljyh.mei.data.model.bili.BiliArchive
import com.ljyh.mei.data.model.bili.BiliSpaceCard
import com.ljyh.mei.data.model.bili.BiliVideo
import com.ljyh.mei.data.model.bili.biliHttpsUrl
import com.ljyh.mei.data.model.bili.parseBiliDuration
import com.ljyh.mei.data.model.bili.toBiliVideo
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.network.bili.BiliApiService
import com.ljyh.mei.data.network.bili.BiliCookieStore
import com.ljyh.mei.data.network.bili.BiliWbi
import com.ljyh.mei.data.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class BiliMusicFeed(
    val ranking: List<BiliVideo>,
    val latest: List<BiliVideo>,
)

data class BiliAudioStream(
    val url: String,
    val audioId: Int,
    val mimeType: String?,
    val codecs: String?,
    val bandwidth: Long,
)

sealed class BiliQrLoginState {
    data object Waiting : BiliQrLoginState()
    data object Scanned : BiliQrLoginState()
    data class Confirmed(val cookies: Map<String, String>) : BiliQrLoginState()
    data object Expired : BiliQrLoginState()
    data class Error(val message: String) : BiliQrLoginState()
}

data class BiliFavFolder(
    val id: Long,
    val title: String,
    val mediaCount: Int,
    /** 自建收藏夹的可见性：visibility==10 为公开。猜你喜欢只读公开夹。 */
    val isPublic: Boolean = true,
)

data class BiliFavFolderPage(
    val title: String,
    val videos: List<BiliVideo>,
    val hasMore: Boolean,
)

/** UP 主空间分组（合集/系列）条目。 */
data class BiliSpaceGroup(
    val id: Long,
    val name: String,
    val total: Int,
)

/** UP 主投稿分页结果。full=false 表示仅 medialist 最近 20 条（arc/search 被风控）。 */
data class BiliSpaceArchivePage(
    val videos: List<BiliVideo>,
    val totalCount: Int,
    val hasMore: Boolean,
    val full: Boolean,
)

/**
 * B 站音乐内容仓库：音乐区首页、扫码登录、账号、收藏夹、搜索与音频流解析。
 */
@Singleton
class BiliRepository @Inject constructor(
    private val api: BiliApiService,
    private val cookieStore: BiliCookieStore,
) {
    val accountFlow: Flow<BiliAccount?> = cookieStore.accountFlow

    suspend fun account(): BiliAccount? = cookieStore.account()

    suspend fun isLoggedIn(): Boolean = cookieStore.snapshot().isLoggedIn

    suspend fun applyLoginCookies(cookies: Map<String, String>) = cookieStore.applyLoginCookies(cookies)

    suspend fun logout() = cookieStore.logout()

    /**
     * 启动时校验本地会话：凭证失效（旧版本登录写入的坏 Cookie 等）时清除，
     * 让界面回到未登录状态，而不是带着无效 SESSDATA 处处请求失败。
     */
    suspend fun validateSession() {
        if (!cookieStore.snapshot().isLoggedIn) return
        when (val result = refreshAccount()) {
            is Resource.Success -> if (result.data == null) {
                Timber.tag("BiliRepository").i("stored bili session invalid, clearing cookies")
                cookieStore.logout()
            }
            else -> Unit
        }
    }

    // ---- 音乐区 ----

    suspend fun musicZone(): Resource<BiliMusicFeed> = withContext(Dispatchers.IO) {
        runCatching { ensureSession() }
        coroutineScope { 
            val ranking = async { safeApiCall { api.ranking(rid = BILI_MUSIC_RID) } }
            val latest = async { safeApiCall { api.newList(rid = BILI_MUSIC_RID, pn = 1, ps = 30) } }
            val rankingResult = ranking.await()
            val latestResult = latest.await()
            if (rankingResult is Resource.Error && latestResult is Resource.Error) {
                return@coroutineScope Resource.Error(rankingResult.message ?: "B站音乐区加载失败")
            }
            Resource.Success(
                BiliMusicFeed(
                    ranking = (rankingResult as? Resource.Success)?.data?.data?.list.orEmpty()
                        .map(BiliArchive::toBiliVideo),
                    latest = (latestResult as? Resource.Success)?.data?.data?.archives.orEmpty()
                        .map(BiliArchive::toBiliVideo),
                )
            )
        }
    }

    suspend fun latestInZone(rid: Int, pn: Int, ps: Int = 20): Resource<List<BiliVideo>> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.newList(rid = rid, pn = pn, ps = ps) }.let { result ->
                when (result) {
                    is Resource.Success -> Resource.Success(
                        result.data?.data?.archives.orEmpty().map(BiliArchive::toBiliVideo)
                    )
                    is Resource.Error -> Resource.Error(result.message ?: "最新投稿加载失败")
                    Resource.Loading -> Resource.Loading
                }
            }
        }

    /** 音乐区最新投稿（保留兼容入口）。 */
    suspend fun latestMusicZone(pn: Int, ps: Int = 20): Resource<List<BiliVideo>> =
        latestInZone(BILI_MUSIC_RID, pn, ps)

    // ---- 账号 ----

    /** 拉取用户信息并写入本地会话；未登录时返回 null。 */
    suspend fun refreshAccount(): Resource<BiliAccount?> = withContext(Dispatchers.IO) {
        when (val result = safeApiCall { api.nav() }) {
            is Resource.Success -> {
                val body = result.data
                val data = body?.data
                // wbi 密钥无论登录与否都下发（匿名 nav 返回 code -101 但带 wbi_img），
                // playurl / 搜索的 wbi 签名依赖它，必须始终保存。
                data?.wbiImg?.let { wbi ->
                    cookieStore.applyWbiKeys(
                        imgKey = BiliWbi.keyFromUrl(wbi.imgUrl),
                        subKey = BiliWbi.keyFromUrl(wbi.subUrl),
                    )
                }
                if (body?.code != 0 || data == null || !data.isLogin) {
                    Resource.Success(null)
                } else {
                    cookieStore.applyUserInfo(
                        mid = data.mid,
                        name = data.uname.orEmpty(),
                        avatar = data.face.orEmpty(),
                        level = data.levelInfo?.currentLevel ?: 0,
                        // nav 接口里 money 才是硬币，coin 是付费的 B 币。
                        coin = data.money,
                        isVip = data.vipStatus > 0,
                    )
                    Resource.Success(
                        BiliAccount(
                            mid = data.mid,
                            name = data.uname.orEmpty(),
                            avatar = data.face.orEmpty(),
                            level = data.levelInfo?.currentLevel ?: 0,
                            coin = data.money,
                            isVip = data.vipStatus > 0,
                        )
                    )
                }
            }
            is Resource.Error -> Resource.Error(result.message ?: "获取账号信息失败")
            Resource.Loading -> Resource.Loading
        }
    }

    // ---- 扫码登录 ----

    suspend fun qrGenerate(): Resource<Pair<String, String>> = withContext(Dispatchers.IO) {
        safeApiCall { api.qrGenerate() }.let { result ->
            when (result) {
                is Resource.Success -> {
                    val data = result.data?.data
                    val url = data?.url.orEmpty()
                    val key = data?.qrcodeKey.orEmpty()
                    if (url.isNotBlank() && key.isNotBlank()) Resource.Success(url to key)
                    else Resource.Error("二维码生成失败")
                }
                is Resource.Error -> Resource.Error(result.message ?: "二维码生成失败")
                Resource.Loading -> Resource.Loading
            }
        }
    }

    suspend fun qrPoll(qrcodeKey: String): BiliQrLoginState = withContext(Dispatchers.IO) {
        try {
            val response = api.qrPoll(qrcodeKey)
            val data = response.body()?.data
                ?: return@withContext BiliQrLoginState.Error("二维码状态获取失败")
            when (data.code) {
                0 -> {
                    // 登录凭证只存在于 Set-Cookie 响应头（原始编码值）；
                    // 从 data.url 解码取值会破坏 SESSDATA 的 %2C 转义，导致凭证失效。
                    val cookies = parseSetCookieHeaders(response.headers().values("Set-Cookie"))
                        .ifEmpty { parseCookieUrlRaw(data.url) }
                    if (cookies.isEmpty()) BiliQrLoginState.Error("登录凭证解析失败")
                    else BiliQrLoginState.Confirmed(cookies)
                }
                86038 -> BiliQrLoginState.Expired
                86090 -> BiliQrLoginState.Scanned
                86101 -> BiliQrLoginState.Waiting
                else -> BiliQrLoginState.Error(data.message ?: "二维码登录失败(${data.code})")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("BiliRepository").d(e, "qr poll failed")
            BiliQrLoginState.Waiting
        }
    }

    /**
     * 从 Set-Cookie 头解析登录凭证。使用 OkHttp 的 Cookie.parse 保留原始值
     * （SESSDATA 里的 %2C 等转义必须原样保留）。
     */
    private fun parseSetCookieHeaders(headers: List<String>): Map<String, String> {
        if (headers.isEmpty()) return emptyMap()
        val url = "https://passport.bilibili.com/".toHttpUrlOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        // OkHttp 已把多个 Set-Cookie 按行分开；每行直接解析，不能按逗号拆（Expires 含逗号）。
        for (line in headers) {
            val cookie = okhttp3.Cookie.parse(url, line.trim()) ?: continue
            if (cookie.name in LOGIN_COOKIE_NAMES) result[cookie.name] = cookie.value
        }
        return result
    }

    /** 兜底：从跳转 url 中按原始编码解析（不做 URL 解码）。 */
    private fun parseCookieUrlRaw(rawUrl: String?): Map<String, String> {
        val url = rawUrl ?: return emptyMap()
        val query = url.substringAfter('?', "")
        return query.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .filter { it.first in LOGIN_COOKIE_NAMES }
            .toMap()
    }

    // ---- 手机号短信登录 ----

    suspend fun loginCaptchaToken(): Resource<Pair<String, Pair<String, String>?>> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.loginCaptcha(t = System.currentTimeMillis()) }.let { result ->
                when (result) {
                    is Resource.Success -> {
                        val data = result.data?.data
                        val token = data?.token.orEmpty()
                        if (token.isBlank()) {
                            Resource.Error("获取验证码 token 失败")
                        } else {
                            val gt = data?.geetest?.takeIf { !it.gt.isNullOrBlank() && !it.challenge.isNullOrBlank() }
                            Resource.Success(token to gt?.let { (it.gt ?: "") to (it.challenge ?: "") })
                        }
                    }
                    is Resource.Error -> Resource.Error(result.message ?: "获取验证码 token 失败")
                    Resource.Loading -> Resource.Loading
                }
            }
        }

    suspend fun sendLoginSms(
        tel: String,
        token: String,
        challenge: String,
        validate: String,
        seccode: String,
    ): Resource<String> = withContext(Dispatchers.IO) {
        safeApiCall { api.smsSend(cid = "86", tel = tel, token = token, challenge = challenge, validate = validate, seccode = seccode) }
            .let { result ->
                when (result) {
                    is Resource.Success -> {
                        val body = result.data
                        if (body?.code != 0) {
                            Resource.Error(body?.message ?: "发送验证码失败(${body?.code})")
                        } else {
                            Resource.Success(body.data?.captchaKey.orEmpty())
                        }
                    }
                    is Resource.Error -> Resource.Error(result.message ?: "发送验证码失败")
                    Resource.Loading -> Resource.Loading
                }
            }
    }

    suspend fun loginWithSmsCode(
        tel: String,
        code: String,
        captchaKey: String,
    ): BiliQrLoginState = withContext(Dispatchers.IO) {
        try {
            val response = api.smsLogin(cid = "86", tel = tel, code = code, captchaKey = captchaKey)
            val body = response.body()
            if (body?.code != 0) {
                BiliQrLoginState.Error(body?.message ?: "登录失败(${body?.code})")
            } else {
                val cookies = parseSetCookieHeaders(response.headers().values("Set-Cookie"))
                if (cookies.isEmpty()) BiliQrLoginState.Error("登录成功但未获取到凭证")
                else BiliQrLoginState.Confirmed(cookies)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("BiliRepository").d(e, "sms login failed")
            BiliQrLoginState.Error("登录请求失败: ${e.message}")
        }
    }

    // ---- 收藏夹 ----

    suspend fun favFolders(mid: Long): Resource<List<BiliFavFolder>> = withContext(Dispatchers.IO) {
        runCatching { ensureSession() }
        safeApiCall { api.favFolders(upMid = mid) }.let { result ->
            when (result) {
                is Resource.Success -> {
                    if (result.data?.code != 0) {
                        Timber.tag("BiliDiag").w(
                            "favFolders code=%s msg=%s mid=%s",
                            result.data?.code, result.data?.message, mid,
                        )
                        return@withContext Resource.Error(
                            result.data?.message ?: "收藏夹加载失败(${result.data?.code})"
                        )
                    }
                    val list = result.data?.data?.list.orEmpty()
                    Timber.tag("BiliDiag").i(
                        "favFolders ok mid=%s count=%s sample=%s",
                        mid, list.size,
                        list.take(3).map { "${it.id}:attr=${it.attr}" },
                    )
                    Resource.Success(
                        list.map {
                            BiliFavFolder(
                                id = it.id,
                                title = it.title.orEmpty(),
                                mediaCount = it.mediaCount,
                                // attr bit0 = 1 表示私有收藏夹。
                                isPublic = it.attr and 1 == 0,
                            )
                        }
                    )
                }
                is Resource.Error -> {
                    Timber.tag("BiliDiag").w("favFolders http error mid=%s msg=%s", mid, result.message)
                    Resource.Error(result.message ?: "收藏夹加载失败")
                }
                Resource.Loading -> Resource.Loading
            }
        }
    }

    suspend fun favFolderResources(folderId: Long, pn: Int, ps: Int = 20): Resource<BiliFavFolderPage> =
        withContext(Dispatchers.IO) {
            // x/v3/fav/resource/list rejects ps above its page-size cap with -400.
            val pageSize = ps.coerceIn(1, 20)
            safeApiCall { api.favResources(mediaId = folderId, pn = pn, ps = pageSize) }.let { result ->
                when (result) {
                    is Resource.Success -> {
                        // 收藏内容接口需要有效登录态；-101/-412 等必须显式报错，不能吞成空列表。
                        if (result.data?.code != 0) {
                            Timber.tag("BiliDiag").w(
                                "favResources code=%s msg=%s media_id=%s",
                                result.data?.code, result.data?.message, folderId,
                            )
                            return@withContext Resource.Error(
                                result.data?.message ?: "收藏夹内容加载失败(${result.data?.code})"
                            )
                        }
                        val data = result.data?.data
                        val raw = data?.medias.orEmpty()
                        val kept = raw.filter { it.bvid != null && it.attr and 1 == 0 }
                        Timber.tag("BiliDiag").i(
                            "favResources ok media_id=%s raw=%s kept=%s title=%s",
                            folderId, raw.size, kept.size, data?.info?.title,
                        )
                        Resource.Success(
                            BiliFavFolderPage(
                                title = data?.info?.title.orEmpty(),
                                videos = kept.map { it.toBiliVideo() },
                                hasMore = data?.hasMore == true,
                            )
                        )
                    }
                    is Resource.Error -> {
                        Timber.tag("BiliDiag").w("favResources http error media_id=%s msg=%s", folderId, result.message)
                        Resource.Error(result.message ?: "收藏夹内容加载失败")
                    }
                    Resource.Loading -> Resource.Loading
                }
            }
        }

    /** 收藏的合集 / 收藏的文件夹列表。 */
    suspend fun favCollectedFolders(mid: Long, pn: Int = 1): Resource<List<BiliFavFolder>> =
        withContext(Dispatchers.IO) {
            runCatching { ensureSession() }
            safeApiCall { api.favFolderCollected(upMid = mid, pn = pn) }.let { result ->
                when (result) {
                    is Resource.Success -> {
                        if (result.data?.code != 0) {
                            return@withContext Resource.Error(
                                result.data?.message ?: "收藏合集加载失败(${result.data?.code})"
                            )
                        }
                        Resource.Success(
                            result.data?.data?.list.orEmpty().map {
                                BiliFavFolder(id = it.id, title = it.title.orEmpty(), mediaCount = it.mediaCount)
                            }
                        )
                    }
                    is Resource.Error -> Resource.Error(result.message ?: "收藏合集加载失败")
                    Resource.Loading -> Resource.Loading
                }
            }
        }

    /**
     * 打开一个收藏条目：普通收藏夹走 resource/list；
     * 合集（season）不支持 media_id 查询，自动回退到 season/list。
     * 两条链路对非法 id 的容忍度不同（一个 -400、一个 code=0 空壳），
     * 因此以"有内容"为唯一采纳标准，任何单边空壳都不会吞掉另一边的结果。
     */
    suspend fun favCollectionResources(folderId: Long, pn: Int = 1, ps: Int = 20): Resource<BiliFavFolderPage> {
        val primary = favFolderResources(folderId, pn, ps)
        if (primary is Resource.Success && primary.data?.videos?.isNotEmpty() == true) {
            return primary
        }
        val fallback = favSeasonResources(folderId, pn, ps)
        if (fallback is Resource.Success && fallback.data?.videos?.isNotEmpty() == true) {
            return fallback
        }
        // Neither side produced content: prefer a well-formed empty folder over errors,
        // and the primary endpoint's verdict over the fallback's.
        return when {
            primary is Resource.Success -> primary
            fallback is Resource.Success -> fallback
            else -> primary
        }
    }

    private suspend fun favSeasonResources(folderId: Long, pn: Int, ps: Int): Resource<BiliFavFolderPage> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.favSeasonList(seasonId = folderId, pn = pn, ps = ps.coerceIn(1, 20)) }.let { result ->
                when (result) {
                    is Resource.Success -> {
                        // The fallback must not swallow API-level errors: HTTP 200 with
                        // code != 0 (e.g. -400) used to surface as an empty folder.
                        if (result.data?.code != 0) {
                            Timber.tag("BiliDiag").w(
                                "favSeasonList code=%s msg=%s season_id=%s",
                                result.data?.code, result.data?.message, folderId,
                            )
                            return@withContext Resource.Error(
                                result.data?.message ?: "收藏内容加载失败(${result.data?.code})"
                            )
                        }
                        val data = result.data?.data
                        val kept = data?.medias
                            ?.filter { it.bvid != null && it.attr and 1 == 0 }
                            .orEmpty()
                        Timber.tag("BiliDiag").i(
                            "favSeasonList ok season_id=%s kept=%s title=%s",
                            folderId, kept.size, data?.info?.title,
                        )
                        Resource.Success(
                            BiliFavFolderPage(
                                title = data?.info?.title.orEmpty(),
                                videos = kept.map { it.toBiliVideo() },
                                hasMore = data?.hasMore == true,
                            )
                        )
                    }
                    is Resource.Error -> {
                        Timber.tag("BiliDiag").w("favSeasonList http error season_id=%s msg=%s", folderId, result.message)
                        result
                    }
                    Resource.Loading -> Resource.Loading
                }
            }
        }

    /** 拉取收藏夹全部视频（用于同步到本地歌单），返回 (标题, 视频)。 */
    suspend fun favFolderAll(folderId: Long): Resource<Pair<String, List<BiliVideo>>> =
        withContext(Dispatchers.IO) {
            val title = StringBuilder()
            val videos = mutableListOf<BiliVideo>()
            var pn = 1
            while (pn <= 50) {
                val page = favCollectionResources(folderId, pn, ps = 50)
                when (page) {
                    is Resource.Success -> {
                        if (title.isEmpty()) title.append(page.data?.title.orEmpty())
                        val batch = page.data?.videos.orEmpty()
                        videos += batch
                        if (page.data?.hasMore != true || batch.isEmpty()) break
                        pn++
                    }
                    is Resource.Error -> {
                        if (videos.isEmpty()) return@withContext Resource.Error(page.message ?: "收藏夹内容加载失败")
                        break
                    }
                    Resource.Loading -> break
                }
            }
            Resource.Success(title.toString() to videos.toList())
        }

    // ---- 搜索 ----

    /** UP 主空间名片（昵称/头像/签名/粉丝数）。 */
    suspend fun spaceCard(mid: Long): Resource<BiliSpaceCard.Data> = withContext(Dispatchers.IO) {
        ensureSession()
        safeApiCall { api.spaceCard(mid) }.let { result ->
            when (result) {
                is Resource.Success -> {
                    if (result.data?.code != 0 || result.data.data == null) {
                        Timber.tag("BiliDiag").w("spaceCard code=%s msg=%s mid=%s", result.data?.code, result.data?.message, mid)
                        Resource.Error(result.data?.message ?: "UP 主信息加载失败(${result.data?.code})")
                    } else {
                        Resource.Success(result.data.data!!)
                    }
                }
                is Resource.Error -> Resource.Error(result.message ?: "UP 主信息加载失败")
                Resource.Loading -> Resource.Loading
            }
        }
    }

    /**
     * UP 主投稿分页。优先 web arc/search（真实格式 dm 指纹 + 精确 space Referer + wbi，
     * 可 pn 翻页读全），被风控时回退 medialist（仅最近 20 条）。
     * 同一进程内失败过一次即熔断，不再徒劳重试拖慢打开速度。
     */
    @Volatile
    private var arcSearchBlocked = false

    suspend fun spaceArchivesPage(
        mid: Long,
        pn: Int = 1,
        ps: Int = 30,
    ): Resource<BiliSpaceArchivePage> = withContext(Dispatchers.IO) {
        if (arcSearchBlocked && pn == 1) {
            return@withContext spaceArchives(mid)
        }
        ensureSession()
        var attempt = safeApiCall {
            api.spaceArcSearch(mid = mid, pn = pn, ps = ps.coerceIn(1, 50))
        }
        if (pn == 1 && (attempt as? Resource.Success)?.data?.code != 0) {
            // -403 访问权限不足 是"签名没被接受"的通用回应（wbi 密钥过期或缺失时
            // 拦截器发不出 w_rid）。强制换一次密钥再试，仍失败才回退。
            runCatching { refreshAccount() }
            attempt = safeApiCall {
                api.spaceArcSearch(mid = mid, pn = pn, ps = ps.coerceIn(1, 50))
            }
        }
        val arc = (attempt as? Resource.Success)?.data
        if (arc?.code == 0) {
            val vlist = arc.data?.list?.vlist.orEmpty()
            val total = arc.data?.page?.count ?: 0
            return@withContext Resource.Success(
                BiliSpaceArchivePage(
                    videos = vlist.map { a ->
                        BiliVideo(
                            aid = a.aid,
                            bvid = a.bvid,
                            // vlist 不返回 cid；播放时按 aid 兜底解析。
                            cid = 0,
                            title = a.title.orEmpty(),
                            uploader = a.author,
                            uploaderMid = a.mid.takeIf { it > 0 } ?: mid,
                            cover = biliHttpsUrl(a.pic),
                            durationMs = parseBiliDuration(a.length),
                            playCount = a.play,
                            danmakuCount = a.videoReview,
                        )
                    },
                    totalCount = total,
                    hasMore = pn * ps < total,
                    full = true,
                ),
            )
        }
        if (pn > 1) {
            // 翻页中途被风控：已加载的完整列表照常展示，标记无更多。
            return@withContext Resource.Success(BiliSpaceArchivePage(emptyList(), 0, hasMore = false, full = true))
        }
        Timber.tag("BiliDiag").w(
            "spaceArcSearch code=%s msg=%s mid=%s buvid3=%s b_nut=%s sid=%s -> fallback medialist",
            arc?.code, arc?.message, mid,
            if (cookieStore.snapshot().buvid3.isNotBlank()) "set" else "MISSING",
            if (cookieStore.snapshot().bNut.isNotBlank()) "set" else "MISSING",
            if (cookieStore.snapshot().sid.isNotBlank()) "set" else "MISSING",
        )
        arcSearchBlocked = true
        spaceArchives(mid)
    }

    /** UP 主最近投稿（medialist 接口实测固定返回前 20 条，无翻页参数）。
     * 返回 (最近投稿, 投稿总数)；完整内容通过分组（seasonArchives）浏览。
     */
    suspend fun spaceArchives(mid: Long): Resource<BiliSpaceArchivePage> = withContext(Dispatchers.IO) {
        ensureSession()
        safeApiCall { api.spaceMedialist(bizId = mid) }.let { result ->
            when (result) {
                is Resource.Success -> {
                    val body = result.data
                    if (body?.code != 0) {
                        Timber.tag("BiliDiag").w(
                            "spaceMedialist code=%s msg=%s mid=%s", body?.code, body?.message, mid,
                        )
                        Resource.Error(body?.message ?: "投稿列表加载失败(${body?.code})")
                    } else {
                        val videos = body?.data?.mediaList.orEmpty()
                            .filter { it.attr == 0 && it.type == 2 }
                            .map { media ->
                                BiliVideo(
                                    aid = media.id,
                                    bvid = media.bvId,
                                    // medialist 不返回 cid；播放时按 aid 兜底解析。
                                    cid = 0,
                                    title = media.title.orEmpty(),
                                    uploader = media.upper?.name,
                                    uploaderMid = media.upper?.mid ?: mid,
                                    cover = biliHttpsUrl(media.cover),
                                    durationMs = media.duration * 1000L,
                                    playCount = media.cntInfo?.play ?: 0,
                                    danmakuCount = media.cntInfo?.danmaku ?: 0,
                                )
                            }
                        val total = body?.data?.totalCount ?: videos.size
                        Resource.Success(
                            BiliSpaceArchivePage(
                                videos = videos,
                                totalCount = total,
                                hasMore = false,
                                full = false,
                            ),
                        )
                    }
                }
                is Resource.Error -> Resource.Error(result.message ?: "投稿列表加载失败")
                Resource.Loading -> Resource.Loading
            }
        }
    }

    /** UP 主空间分组（合集+系列），按展示顺序合并。 */
    suspend fun spaceGroups(mid: Long): Resource<List<BiliSpaceGroup>> = withContext(Dispatchers.IO) {
        safeApiCall { api.spaceSeasonsSeries(mid) }.let { result ->
            when (result) {
                is Resource.Success -> {
                    val body = result.data
                    if (body?.code != 0) {
                        Timber.tag("BiliDiag").w(
                            "spaceSeasonsSeries code=%s msg=%s mid=%s", body?.code, body?.message, mid,
                        )
                        Resource.Error(body?.message ?: "分组加载失败(${body?.code})")
                    } else {
                        val lists = body?.data?.itemsLists
                        val groups = lists?.seasonsList.orEmpty().mapNotNull { entry ->
                            entry.meta?.takeIf { it.id > 0 }?.let {
                                BiliSpaceGroup(id = it.id, name = it.name.orEmpty(), total = it.total)
                            }
                        } + lists?.seriesList.orEmpty().mapNotNull { entry ->
                            entry.meta?.takeIf { it.id > 0 }?.let {
                                BiliSpaceGroup(id = it.id, name = it.name.orEmpty(), total = it.total)
                            }
                        }
                        Resource.Success(groups)
                    }
                }
                is Resource.Error -> Resource.Error(result.message ?: "分组加载失败")
                Resource.Loading -> Resource.Loading
            }
        }
    }

    /** 分组内投稿（页码分页）。 */
    suspend fun seasonArchives(
        mid: Long,
        seasonId: Long,
        pageNum: Int,
        pageSize: Int = 20,
    ): Resource<BiliFavFolderPage> = withContext(Dispatchers.IO) {
        safeApiCall {
            api.seasonArchives(mid = mid, seasonId = seasonId, pageNum = pageNum, pageSize = pageSize.coerceIn(1, 100))
        }.let { result ->
            when (result) {
                is Resource.Success -> {
                    val body = result.data
                    if (body?.code != 0) {
                        Timber.tag("BiliDiag").w(
                            "seasonArchives code=%s msg=%s season=%s", body?.code, body?.message, seasonId,
                        )
                        Resource.Error(body?.message ?: "分组内容加载失败(${body?.code})")
                    } else {
                        val videos = body?.data?.archives.orEmpty().map { it.toBiliVideo() }
                        val total = body?.data?.page?.total ?: 0
                        Resource.Success(
                            BiliFavFolderPage(
                                title = "",
                                videos = videos,
                                hasMore = pageNum * pageSize < total,
                            ),
                        )
                    }
                }
                is Resource.Error -> Resource.Error(result.message ?: "分组内容加载失败")
                Resource.Loading -> Resource.Loading
            }
        }
    }

    suspend fun search(keyword: String, page: Int = 1): Resource<List<BiliVideo>> =
        withContext(Dispatchers.IO) {
            ensureSession()
            safeApiCall { api.searchAll(keyword = keyword, page = page) }.let { result ->
                when (result) {
                    is Resource.Success -> Resource.Success(
                        result.data?.data?.result
                            .orEmpty()
                            .firstOrNull { it.resultType == "video" }
                            ?.data.orEmpty()
                            .filter { it.bvid != null }
                            .map { it.toBiliVideo() }
                    )
                    is Resource.Error -> Resource.Error(result.message ?: "搜索失败")
                    Resource.Loading -> Resource.Loading
                }
            }
        }

    private suspend fun ensureBuvid() {
        val snap = cookieStore.snapshot()
        // buvid3/buvid4 由 spi 接口下发（网页首跳同源），两者配套；
        // 缺一就补齐，缺失 buvid3 会被风控直接 412。
        if (snap.buvid3.isBlank() || snap.buvid4.isBlank()) {
            runCatching { api.fingerSpi() }.getOrNull()?.data?.let { spi ->
                cookieStore.applyBuvid(spi.b3.orEmpty(), spi.b4.orEmpty())
            }
        }
        cookieStore.ensureUuid()
    }

    /**
     * wbi 签名密钥来自 nav 接口（未登录也返回）。playurl / 搜索等 wbi 接口必需。
     * 密钥会周期轮换，缺失或超过 12 小时都重新拉取。
     */
    suspend fun ensureWbiKeys() {
        val snapshot = cookieStore.snapshot()
        val stale = System.currentTimeMillis() - snapshot.wbiKeyAt > WBI_KEY_TTL_MS
        if (snapshot.wbiImgKey.isBlank() || snapshot.wbiSubKey.isBlank() || stale) {
            runCatching { refreshAccount() }
        }
    }

    /**
     * 会话预检：buvid3/buvid4 + `_uuid`（设备指纹，风控必需）+ wbi 密钥。
     * 播放、搜索、收藏、空间等入口调用前先跑一遍。wbi 密钥是空间接口 -403
     * 的直接来源，所以密钥缺失/过期时会强制刷新。
     */
    suspend fun ensureSession() {
        runCatching { ensureBuvid() }
        runCatching { ensureWbiKeys() }
    }

    private companion object {
        const val WBI_KEY_TTL_MS = 12 * 60 * 60 * 1000L
        val LOGIN_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid", "b_nut", "buvid3", "buvid4")
    }

    // ---- 播放 ----

    /** 搜索结果等缺少 cid 的场景：按 bvid 补齐视频详情。 */
    suspend fun videoDetail(bvid: String): Resource<BiliVideo> = withContext(Dispatchers.IO) {
        safeApiCall { api.view(bvid) }.let { result ->
            when (result) {
                is Resource.Success -> {
                    val body = result.data
                    if (body?.code != 0 || body.data == null) {
                        Resource.Error(body?.message ?: "视频信息获取失败")
                    } else {
                        Resource.Success(body.data.toBiliVideo())
                    }
                }
                is Resource.Error -> Resource.Error(result.message ?: "视频信息获取失败")
                Resource.Loading -> Resource.Loading
            }
        }
    }

    /** 只有 aid 的旧条目（如历史快照）的兜底：view?aid= 一次拿到 bvid+cid。 */
    suspend fun resolveVideoByAid(aid: Long): BiliVideo? = withContext(Dispatchers.IO) {
        try {
            val body = api.viewByAid(aid)
            if (body.code == 0 && body.data != null) body.data.toBiliVideo() else null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("BiliRepository").d(e, "resolve video by aid failed")
            null
        }
    }

    /**
     * wbi playurl 接口只接受 bvid+cid（实测传 aid 返回 -400），
     * 只有 aid 的场景先用 view?aid= 换取 bvid。
     */
    suspend fun resolveBvidByAid(aid: Long): String? = withContext(Dispatchers.IO) {
        try {
            val body = api.viewByAid(aid)
            body.data?.bvid?.takeIf { it.isNotBlank() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("BiliRepository").d(e, "resolve bvid by aid failed")
            null
        }
    }

    suspend fun resolveAudioStream(aid: Long, bvid: String?, cid: Long): BiliAudioStream =
        withContext(Dispatchers.IO) {
            ensureSession()
            val response = api.playUrl(
                bvid = bvid?.takeIf { it.isNotBlank() },
                aid = aid.takeIf { it > 0 },
                cid = cid,
            )
            check(response.code == 0) { response.message ?: "音频地址获取失败" }
            val dash = response.data?.dash ?: error("该视频没有可播放的音频流")
            val candidates = buildList {
                dash.audio?.let { addAll(it) }
                dash.flac?.let { add(it) }
            }.filter { !it.url.isNullOrBlank() }
            // 杜比流（30250/30251）为 EC-3，部分设备无法解码：有其他选择时优先跳过。
            val decodable = candidates.filter { it.id !in setOf(30250, 30251) }
            val stream = (decodable.ifEmpty { candidates }).maxByOrNull { it.id }
                ?: error("该视频没有可播放的音频流")
            val audioId = when {
                stream.id == BILI_AUDIO_ID_HIRES -> BILI_AUDIO_ID_HIRES
                stream.id > BILI_AUDIO_ID_EXHIGH -> BILI_AUDIO_ID_EXHIGH
                stream.id > BILI_AUDIO_ID_STANDARD -> BILI_AUDIO_ID_EXHIGH
                else -> BILI_AUDIO_ID_STANDARD
            }
            BiliAudioStream(
                url = stream.url!!,
                audioId = audioId,
                mimeType = stream.mime?.takeIf { it.startsWith("audio/") } ?: "audio/mp4",
                codecs = stream.codecs,
                bandwidth = stream.bandwidth,
            )
        }
}
