package com.ljyh.mei.data.network.bili

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * B 站请求拦截器：附加浏览器 UA / Referer / Cookie，并为 wbi 接口计算 w_rid 签名。
 */
class BiliInterceptor(
    private val cookieStore: BiliCookieStore,
) : Interceptor {

    /** Set-Cookie 落盘用的后台作用域（不该阻塞响应）。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 会话内存副本：热路径不碰 DataStore，只有进程首次请求会真的读盘。 */
    private fun snapshot(): BiliCookieStore.Snapshot = cookieStore.cachedSnapshot()

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
        // 与 BBPlayer 一致：所有接口统一用移动端 BiliApp UA + web 来源头。
        // 桌面 UA 与缺少浏览器指纹的组合更容易被空间接口风控。
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
                Timber.tag("BiliInterceptor").d(
                    "wbi %s img=%s sub=%s -> %s",
                    url.encodedPath,
                    snap.wbiImgKey.take(8),
                    snap.wbiSubKey.take(8),
                    query.substringAfterLast("w_rid=").take(8),
                )
            } else {
                Timber.tag("BiliInterceptor").w("wbi keys missing for %s", url.encodedPath)
            }
        }

        val response = chain.proceed(builder.url(signedUrl).build())

        // 捕获服务端下发的会话补充 cookie（buvid3/sid/b_nut），空间接口风控
        // 校验 cookie 组合完整性，缺 sid/b_nut 会被判伪造（-403）。
        val supplements = mutableMapOf<String, String>()
        response.headers("set-cookie").forEach { raw ->
            val pair = raw.substringBefore(';').trim().split('=', limit = 2)
            if (pair.size == 2 && pair[1].isNotBlank()) {
                when (pair[0]) {
                    "buvid3" -> supplements["buvid3"] = pair[1]
                    "sid" -> supplements["sid"] = pair[1]
                    "b_nut" -> supplements["b_nut"] = pair[1]
                }
            }
        }
        if (supplements.isNotEmpty()) {
            // 落盘放到后台：Set-Cookie 只是补充会话，没必要卡住这次响应。
            scope.launch {
                if (supplements["buvid3"]?.takeIf { it.isNotBlank() && it != snap.buvid3 } != null) {
                    cookieStore.applyBuvid(supplements["buvid3"], null)
                }
                cookieStore.applySessionSupplements(supplements)
            }
        }
        return response
    }

    private companion object {
        /** 参考同类客户端（BBPlayer）：所有 bili 接口统一走移动端 BiliApp UA。 */
        const val PASSPORT_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0_1 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Mobile/15E148 BiliApp/6.66.0"
    }
}
