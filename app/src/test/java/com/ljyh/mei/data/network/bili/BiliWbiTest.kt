package com.ljyh.mei.data.network.bili

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * wbi 签名的已知答案测试。
 *
 * 空间接口对签名错误只回 `-403 访问权限不足`，看不出任何细节，所以这里把
 * 参考实现（BBPlayer encWbi / bilibili-API-collect）算出的结果钉死，
 * 避免以后改动编码或密钥提取时又静默签错。
 */
class BiliWbiTest {

    private val imgKey = "7cd084941338484aae1ad9425b84077c"
    private val subKey = "4932caff0ff746eab6f01bf08b70ac45"
    private val wts = 1_700_000_000L

    @Test
    fun `mixin key follows the permutation table`() {
        assertEquals("ea1db124af3c7062474693fa704f4ff8", BiliWbi.mixinKey(imgKey, subKey))
    }

    @Test
    fun `signed query matches reference implementation`() {
        val query = BiliWbi.signedQuery(
            params = listOf(
                "mid" to "946974",
                "pn" to "1",
                "ps" to "30",
                "keyword" to "",
            ),
            imgKey = imgKey,
            subKey = subKey,
            wts = wts,
        )
        // 参数按 key 升序，w_rid 固定排在最后。
        assertEquals(
            "keyword=&mid=946974&pn=1&ps=30&wts=1700000000&w_rid=7adc16d69d99841f6d3e3915913bd849",
            query,
        )
    }

    @Test
    fun `key from url strips extension and query`() {
        assertEquals(
            imgKey,
            BiliWbi.keyFromUrl("https://i0.hdslb.com/bfs/wbi/$imgKey.png"),
        )
        assertEquals(
            imgKey,
            BiliWbi.keyFromUrl("https://i0.hdslb.com/bfs/wbi/$imgKey.png?v=1"),
        )
        assertEquals(imgKey, BiliWbi.keyFromUrl(imgKey))
        assertEquals("", BiliWbi.keyFromUrl(null))
        assertEquals("", BiliWbi.keyFromUrl(""))
    }

    @Test
    fun `values drop the five reserved characters before encoding`() {
        val query = BiliWbi.signedQuery(
            params = listOf("keyword" to "a!b'c(d)e*f"),
            imgKey = imgKey,
            subKey = subKey,
            wts = wts,
        )
        assertEquals(true, query.startsWith("keyword=abcdef&"))
    }
}
