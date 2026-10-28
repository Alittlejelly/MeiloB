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

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val builder = StringBuilder()
        for (index in MIXIN_KEY_ENC_TAB) {
            val ch = raw.getOrNull(index)
            if (ch != null) builder.append(ch)
        }
        return builder.take(32).toString()
    }

    fun keyFromUrl(url: String?): String =
        url?.substringBefore('?')?.substringAfterLast('/', "") ?: ""

    /** 对已按 key 排序的参数（不含 w_rid）计算签名，返回 (wts, w_rid)。 */
    fun sign(params: List<Pair<String, String>>, imgKey: String, subKey: String, wts: Long): Pair<Long, String> {
        val mixin = mixinKey(imgKey, subKey)
        val query = (params + (WTS_PARAM to wts.toString()))
            .sortedBy { it.first }
            .joinToString("&") { (k, v) -> "${encodeValue(k)}=${encodeValue(v)}" }
        val wRid = md5Hex(query + mixin)
        return wts to wRid
    }

    /** 等价于 JS 的 encodeURIComponent。 */
    private fun encodeValue(value: String): String = URLEncoder
        .encode(value, Charsets.UTF_8)
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%2A", "*")

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
