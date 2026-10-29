package com.ljyh.mei.data.network.bili

import androidx.datastore.preferences.core.edit
import com.ljyh.mei.constants.BiliAvatarKey
import com.ljyh.mei.constants.BiliBuvid3Key
import com.ljyh.mei.constants.BiliBuvid4Key
import com.ljyh.mei.constants.BiliUuidKey
import com.ljyh.mei.constants.BiliCoinKey
import com.ljyh.mei.constants.BiliDedeUserIdCkMd5Key
import com.ljyh.mei.constants.BiliDedeUserIdKey
import com.ljyh.mei.constants.BiliJctKey
import com.ljyh.mei.constants.BiliLevelKey
import com.ljyh.mei.constants.BiliNicknameKey
import com.ljyh.mei.constants.BiliSessdataKey
import com.ljyh.mei.constants.BiliSidKey
import com.ljyh.mei.constants.BiliBnutKey
import com.ljyh.mei.constants.BiliVipKey
import com.ljyh.mei.constants.BiliWbiImgKeyKey
import com.ljyh.mei.constants.BiliWbiKeyAtKey
import com.ljyh.mei.constants.BiliWbiSubKeyKey
import com.ljyh.mei.data.model.bili.BiliAccount
import com.ljyh.mei.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.content.Context
import javax.inject.Inject
import kotlin.random.Random
import javax.inject.Singleton

/**
 * B 站网页版会话（Cookie）与 WBI 密钥的持久化。
 */
@Singleton
class BiliCookieStore @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val store get() = appContext.dataStore

    data class Snapshot(
        val sessdata: String = "",
        val biliJct: String = "",
        val dedeUserId: String = "",
        val dedeUserIdCkMd5: String = "",
        val buvid3: String = "",
        val buvid4: String = "",
        /** 设备标识 `_uuid`：真实浏览器/客户端必有，Gaia 风控用它绑定 buvid3。 */
        val uuid: String = "",
        val sid: String = "",
        val bNut: String = "",
        val nickname: String = "",
        val avatar: String = "",
        val level: Int = 0,
        val coin: Double = 0.0,
        val isVip: Boolean = false,
        val wbiImgKey: String = "",
        val wbiSubKey: String = "",
        val wbiKeyAt: Long = 0,
    ) {
        val isLoggedIn: Boolean
            get() = sessdata.isNotBlank() && dedeUserId.isNotBlank()

        fun cookieHeader(): String = buildList {
            if (buvid3.isNotBlank()) add("buvid3=$buvid3")
            if (buvid4.isNotBlank()) add("buvid4=$buvid4")
            if (sid.isNotBlank()) add("sid=$sid")
            if (bNut.isNotBlank()) add("b_nut=$bNut")
            if (uuid.isNotBlank()) add("_uuid=$uuid")
            if (dedeUserId.isNotBlank()) add("DedeUserID=$dedeUserId")
            if (dedeUserIdCkMd5.isNotBlank()) add("DedeUserID__ckMd5=$dedeUserIdCkMd5")
            if (sessdata.isNotBlank()) add("SESSDATA=$sessdata")
            if (biliJct.isNotBlank()) add("bili_jct=$biliJct")
        }.joinToString("; ")
    }

    fun snapshotFlow(): Flow<Snapshot> = store.data.map { p ->
        Snapshot(
            sessdata = p[BiliSessdataKey].orEmpty(),
            biliJct = p[BiliJctKey].orEmpty(),
            dedeUserId = p[BiliDedeUserIdKey].orEmpty(),
            dedeUserIdCkMd5 = p[BiliDedeUserIdCkMd5Key].orEmpty(),
            buvid3 = p[BiliBuvid3Key].orEmpty(),
            buvid4 = p[BiliBuvid4Key].orEmpty(),
            uuid = p[BiliUuidKey].orEmpty(),
            sid = p[BiliSidKey].orEmpty(),
            bNut = p[BiliBnutKey].orEmpty(),
            nickname = p[BiliNicknameKey].orEmpty(),
            avatar = p[BiliAvatarKey].orEmpty(),
            level = p[BiliLevelKey]?.toIntOrNull() ?: 0,
            coin = (p[BiliCoinKey] ?: 0L) / 100.0,
            isVip = (p[BiliVipKey] ?: 0L) > 0,
            wbiImgKey = BiliWbi.keyFromUrl(p[BiliWbiImgKeyKey]),
            wbiSubKey = BiliWbi.keyFromUrl(p[BiliWbiSubKeyKey]),
            wbiKeyAt = p[BiliWbiKeyAtKey] ?: 0L,
        )
    }

    suspend fun snapshot(): Snapshot = snapshotFlow().first()

    val accountFlow: Flow<BiliAccount?> = snapshotFlow().map { s ->
        if (!s.isLoggedIn) null else BiliAccount(
            mid = s.dedeUserId.toLongOrNull() ?: 0,
            name = s.nickname,
            avatar = s.avatar,
            level = s.level,
            coin = s.coin,
            isVip = s.isVip,
        )
    }

    suspend fun account(): BiliAccount? = accountFlow.first()

    suspend fun applyLoginCookies(cookies: Map<String, String>) {
        store.edit { p ->
            cookies["SESSDATA"]?.let { p[BiliSessdataKey] = it }
            cookies["bili_jct"]?.let { p[BiliJctKey] = it }
            cookies["DedeUserID"]?.let { p[BiliDedeUserIdKey] = it }
            cookies["DedeUserID__ckMd5"]?.let { p[BiliDedeUserIdCkMd5Key] = it }
            cookies["sid"]?.let { p[BiliSidKey] = it }
            cookies["b_nut"]?.let { p[BiliBnutKey] = it }
        }
    }

    suspend fun applyUserInfo(
        mid: Long,
        name: String,
        avatar: String,
        level: Int,
        coin: Double,
        isVip: Boolean,
    ) {
        store.edit { p ->
            p[BiliDedeUserIdKey] = mid.toString()
            p[BiliNicknameKey] = name
            p[BiliAvatarKey] = avatar
            p[BiliLevelKey] = level.toString()
            p[BiliCoinKey] = (coin * 100).toLong()
            p[BiliVipKey] = if (isVip) 1L else 0L
        }
    }

    suspend fun applyBuvid(b3: String?, b4: String?) {
        store.edit { p ->
            b3?.takeIf { it.isNotBlank() }?.let { p[BiliBuvid3Key] = it }
            b4?.takeIf { it.isNotBlank() }?.let { p[BiliBuvid4Key] = it }
        }
    }

    suspend fun applyUuid(uuid: String) {
        if (uuid.isBlank()) return
        store.edit { it[BiliUuidKey] = uuid }
    }

    /**
     * `_uuid` 只在首次生成并持久化（保持不变才像真实设备；每次启动都换会与
     * buvid3 对不上）。格式与浏览器一致：
     * `8-4-4-4-12` 大写十六进制 + 时间戳后 5 位 + `infoc`。
     */
    suspend fun ensureUuid(): String {
        val existing = snapshot().uuid
        if (existing.isNotBlank()) return existing
        val hex = { n: Int ->
            buildString { repeat(n) { append("0123456789ABCDEF"[Random.nextInt(16)]) } }
        }
        val generated = buildString {
            append(hex(8)).append('-').append(hex(4)).append('-').append(hex(4))
            append('-').append(hex(4)).append('-').append(hex(12))
            append(System.currentTimeMillis().toString().takeLast(5)).append("infoc")
        }
        applyUuid(generated)
        return generated
    }

    /** 持久化响应头下发的会话补充 cookie（sid/b_nut）。 */
    suspend fun applySessionSupplements(cookies: Map<String, String>) {
        store.edit { p ->
            cookies["sid"]?.takeIf { it.isNotBlank() }?.let { p[BiliSidKey] = it }
            cookies["b_nut"]?.takeIf { it.isNotBlank() }?.let { p[BiliBnutKey] = it }
        }
    }

    suspend fun applyWbiKeys(imgKey: String, subKey: String) {
        val img = BiliWbi.keyFromUrl(imgKey)
        val sub = BiliWbi.keyFromUrl(subKey)
        if (img.isBlank() || sub.isBlank()) return
        store.edit { p ->
            p[BiliWbiImgKeyKey] = img
            p[BiliWbiSubKeyKey] = sub
            p[BiliWbiKeyAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun logout() {
        store.edit { p ->
            p.remove(BiliSessdataKey)
            p.remove(BiliJctKey)
            p.remove(BiliDedeUserIdKey)
            p.remove(BiliDedeUserIdCkMd5Key)
            p.remove(BiliNicknameKey)
            p.remove(BiliAvatarKey)
            p.remove(BiliLevelKey)
            p.remove(BiliCoinKey)
            p.remove(BiliVipKey)
        }
    }
}
