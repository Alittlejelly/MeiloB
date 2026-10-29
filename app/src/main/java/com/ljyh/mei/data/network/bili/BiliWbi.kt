package com.ljyh.mei.data.network.bili

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * B 站 WBI 签名（w_rid）。
 * 算法与 bilibili-API-collect 中的参考实现一致：从 nav 接口取 img_key / sub_key，
 * 按固定置换表混排出 32 位 mixin_key，对排序后的查询参数 + wts 做 MD5。
 */
object BiliWbi {

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52,
    )

    const val WTS_PARAM = "wts"

    /** 参考实现要求从参数值里剔除的字符。 */
    private const val FILTERED_CHARS = "!'()*"

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val builder = StringBuilder()
        for (index in MIXIN_KEY_ENC_TAB) {
            val ch = raw.getOrNull(index)
            if (ch != null) builder.append(ch)
        }
        return builder.take(32).toString()
    }

    /**
     * 从 nav 下发的 wbi 图片地址里取出密钥。
     * 形如 `https://i0.hdslb.com/bfs/wbi/7cd084941338....png`，**必须去掉扩展名**：
     * 带 `.png` 拼出的 mixin key 会算错 w_rid，服务端判定签名不符，空间接口
     * 直接拒绝（访问权限不足 -403 / 风控校验失败 -352），搜索、播放等宽松接口
     * 则照常放行——所以这个 bug 只会表现为"UP 主主页只给 20 条"。
     */
    fun keyFromUrl(url: String?): String =
        url?.substringBefore('?')?.substringAfterLast('/')?.substringBefore('.') ?: ""

    /** 对已按 key 排序的参数（不含 w_rid）计算签名，返回 (wts, w_rid)。 */
    fun sign(params: List<Pair<String, String>>, imgKey: String, subKey: String, wts: Long): Pair<Long, String> {
        val mixin = mixinKey(imgKey, subKey)
        val query = queryOf(params + (WTS_PARAM to wts.toString()))
        val wRid = md5Hex(query + mixin)
        return wts to wRid
    }

    /**
     * 与 JS encodeURIComponent 等价的百分号编码，并像参考实现那样先滤掉
     * `!'()*`。签名和实际发出的 URL 必须用同一套编码：OkHttp 的
     * addQueryParameter 不编码 `{}[]:,`，而浏览器会编码成 %7B 等，两边不一致时
     * 服务端按收到的串重算 w_rid 就对不上。
     */
    fun encodeValue(value: String): String = URLEncoder
        .encode(value.filterNot { it in FILTERED_CHARS }, Charsets.UTF_8)
        .replace("+", "%20")

    /** 按 key 升序拼出查询串（不含 w_rid）。 */
    private fun queryOf(params: List<Pair<String, String>>): String =
        params.sortedBy { it.first }
            .joinToString("&") { (k, v) -> "${encodeValue(k)}=${encodeValue(v)}" }

    /** 按签名顺序（key 升序）拼出最终查询串，`w_rid` 固定排在最后。 */
    fun signedQuery(
        params: List<Pair<String, String>>,
        imgKey: String,
        subKey: String,
        wts: Long,
    ): String {
        val (w, wRid) = sign(params, imgKey, subKey, wts)
        return queryOf(params + (WTS_PARAM to w.toString())) + "&w_rid=$wRid"
    }

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
