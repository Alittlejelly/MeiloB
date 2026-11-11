package com.ljyh.mei.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ljyh.mei.constants.UserAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 应用内更新：把 release 资产里的 APK 下载到 cache，然后交给系统安装器。
 *
 * 注意三点（都是 Android 侧硬性要求，不是能绕过的）：
 * 1. APK 必须与已安装版本同签名，否则系统直接拒绝安装；
 * 2. `versionCode` 不能比已安装的小，同版本可以覆盖安装；
 * 3. Android 8 起需要 `REQUEST_INSTALL_PACKAGES` + 用户手动允许「安装未知应用」。
 */
object ApkInstaller {

    private const val MIME_APK = "application/vnd.android.package-archive"
    private const val UPDATE_DIR = "updates"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // APK 有 28MB 左右，读超时给足，避免弱网下频繁失败。
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 下载 APK，下载中的百分比通过 [onProgress] 回调（0-100）。 */
    suspend fun download(
        context: Context,
        url: String,
        versionTag: String,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val safeTag = versionTag.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val target = File(directory, "MeiloB-$safeTag.apk")
        val partial = File(directory, "MeiloB-$safeTag.apk.part")
        partial.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty response body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
            if (partial.length() <= 0L) throw IOException("downloaded file is empty")
            target.delete()
            if (!partial.renameTo(target)) throw IOException("could not move downloaded apk")
            onProgress(100)
            target
        } catch (cancelled: CancellationException) {
            partial.delete()
            throw cancelled
        } catch (error: Exception) {
            partial.delete()
            Timber.tag("ApkInstaller").w(error, "download failed: $url")
            throw error
        }
    }

    /** Android 8+ 需要用户对「安装未知应用」逐应用授权。 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** 跳到本应用的「安装未知应用」授权页。 */
    fun openInstallPermissionSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /** 拉起系统安装器；失败返回 false，由调用方提示手动下载。 */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.onFailure {
        Timber.tag("ApkInstaller").w(it, "install intent failed")
    }.isSuccess
}
