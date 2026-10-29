package com.ljyh.mei.data.network.bili

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * B 站请求拦截器：附加浏览器 UA / Referer / Cookie，并为 wbi 接口计算 w_rid 签名。
 */
class BiliInterceptor(
    private val cookieStore: BiliCookieStore,
) : Interceptor {

    private fun snapshot(): BiliCookieStore.Snapshot =
        runBlocking { cookieStore.snapshot() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        if (!host.endsWith("bilibili.com") && !host.endsWith("bilibili.cn") && !host.endsWith("bilivideo.com") &&
            !host.endsWith("bilivideo.cn") && !host.endsWith("hdslb.com") && !host.endsWith("acgvideo.com") &&
            !host.endsWith("biliapi.net") && !host.endsWith("biliapi.com")
        ) {
            return chain.proceed(request)
        }
        val snap = snapshot()
        // 与同类客户端一致：默认走移动端 BiliApp UA（桌面 UA 更容易触发风控）。
        // UA/Referer/Origin 允许接口用 @Headers 覆盖（空间接口要求 web UA + space 来源）。
        val builder = request.newBuilder()
        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", PASSPORT_USER_AGENT)
        }
        if (request.header("Referer") == null) {
            builder.header("Referer", "https://www.bilibili.com/")
        }
        if (request.header("Origin") == null) {
            builder.header("Origin", "https://www.bilibili.com")
        }

        if (request.url.host.endsWith("bilibili.com") || request.url.host.endsWith("bilivideo.com")) {
            val cookie = snap.cookieHeader()
            if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        }

        val url = request.url
        var signedUrl = url
        if (url.encodedPath.contains("/wbi/")) {
            if (snap.wbiImgKey.isNotBlank() && snap.wbiSubKey.isNotBlank()) {
                val params = mutableListOf<Pair<String, String>>()
                for (name in url.queryParameterNames) {
                    if (name == BiliWbi.WTS_PARAM || name == "w_rid") continue
                    val value = url.queryParameter(name) ?: continue
                    params.add(name to value)
                }
                // 整条查询串用签名同款 encodeURIComponent 重建：若走
                // addQueryParameter，OkHttp 对 {}[]:,+/ 的编码与签名不一致，
                // 服务端重算 w_rid 必然对不上（空间接口表现为 -403）。
                val query = BiliWbi.signedQuery(params, snap.wbiImgKey, snap.wbiSubKey, System.currentTimeMillis() / 1000)
                signedUrl = url.newBuilder()
                    .encodedQuery(query)
                    .build()
            } else {
                Timber.tag("BiliInterceptor").w("wbi keys missing for %s", url.encodedPath)
            }
        }

        val response = chain.proceed(builder.url(signedUrl).build())

        // 捕获服务端下发的 buvid3，后续搜索等接口需要该 Cookie。
        response.headers("set-cookie")
            .filter { it.startsWith("buvid3=") }
            .firstOrNull()
            ?.let { raw ->
                val value = raw.removePrefix("buvid3=").substringBefore(';')
                if (value.isNotBlank() && value != snap.buvid3) {
                    runBlocking { cookieStore.applyBuvid3(value) }
                }
            }
        return response
    }

    companion object {
        const val BILI_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"

        /** 参考同类客户端：passport 登录接口使用移动端 BiliApp UA。 */
        const val PASSPORT_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0_1 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Mobile/15E148 BiliApp/6.66.0"
    }
}
