package com.ljyh.mei.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ljyh.mei.BuildConfig
import com.ljyh.mei.R
import com.ljyh.mei.ui.component.utils.rememberAlertContent
import com.ljyh.mei.utils.ApkInstaller
import com.ljyh.mei.utils.VersionUpdateChecker
import com.ljyh.mei.utils.VersionUpdateResult
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VersionUpdateAlert(
    result: VersionUpdateResult?,
    onDismiss: () -> Unit,
    /** release 里取到的 APK 资产；拿不到时回退成「打开更新页」。 */
    apk: VersionUpdateChecker.ApkAsset? = null,
) {
    val visible = result != null
    val displayedResult = rememberAlertContent(result) ?: return
    val context = LocalContext.current
    when (val result = displayedResult) {
        is VersionUpdateResult.UpdateAvailable -> {
            val scope = rememberCoroutineScope()
            // 状态都按 result 记住：弹窗在关闭动画期间会继续组合，用 LaunchedEffect 会重入。
            var progress by remember(result) { mutableStateOf<Int?>(null) }
            var downloaded by remember(result) { mutableStateOf<File?>(null) }
            var failure by remember(result) { mutableStateOf<String?>(null) }
            var busy by remember(result) { mutableStateOf(false) }

            fun startDownload() {
                val asset = apk ?: return
                if (busy) return
                busy = true
                failure = null
                progress = 0
                scope.launch {
                    try {
                        val file = ApkInstaller.download(context, asset.url, asset.tag) { progress = it }
                        downloaded = file
                        if (ApkInstaller.canInstall(context)) {
                            if (!ApkInstaller.install(context, file)) {
                                failure = context.getString(R.string.about_update_install_failed)
                            }
                        } else {
                            failure = context.getString(R.string.about_update_permission_hint)
                            ApkInstaller.openInstallPermissionSettings(context)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = context.getString(
                            R.string.about_update_download_failed,
                            error.message ?: error.javaClass.simpleName,
                        )
                    } finally {
                        busy = false
                        progress = null
                    }
                }
            }

            val message = buildString {
                append(
                    stringResource(
                        R.string.about_update_available_message,
                        result.latestTag,
                        BuildConfig.VERSION_NAME,
                    ),
                )
                when {
                    progress != null -> append("\n\n").append(
                        stringResource(R.string.about_update_downloading, progress ?: 0),
                    )
                    downloaded != null && failure == null -> append("\n\n").append(
                        stringResource(R.string.about_update_installing),
                    )
                }
                failure?.let { append("\n\n").append(it) }
            }

            GlasmioAlertDialog(
                visible = visible,
                onDismissRequest = { if (!busy) onDismiss() },
                title = stringResource(R.string.about_update_available_title),
                message = message,
                buttons = listOf(
                    GlasmioAlertButtonSpec(
                        label = stringResource(R.string.cancel),
                        role = GlasmioAlertButtonRole.Cancel,
                        onClick = { if (!busy) onDismiss() },
                    ),
                    // 已经下载好但安装没拉起来（例如没给「安装未知应用」）：再来一次只重试安装。
                    if (downloaded != null && apk != null) {
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.about_update_download),
                            onClick = {
                                val file = downloaded
                                if (file != null && ApkInstaller.canInstall(context) &&
                                    !ApkInstaller.install(context, file)
                                ) {
                                    failure = context.getString(R.string.about_update_install_failed)
                                } else if (file != null) {
                                    failure = context.getString(R.string.about_update_permission_hint)
                                    ApkInstaller.openInstallPermissionSettings(context)
                                }
                            },
                        )
                    } else if (apk != null) {
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.about_update_download),
                            onClick = { startDownload() },
                        )
                    } else {
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.about_open_update),
                            onClick = {
                                onDismiss()
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(result.releaseUrl)),
                                    )
                                }
                            },
                        )
                    },
                ),
            )
        }

        VersionUpdateResult.UpToDate -> {
            GlasmioAlertDialog(
                visible = visible,
                onDismissRequest = onDismiss,
                title = stringResource(R.string.about_update_up_to_date_title),
                message = stringResource(
                    R.string.about_update_up_to_date_message,
                    BuildConfig.VERSION_NAME,
                ),
                buttons = listOf(
                    GlasmioAlertButtonSpec(
                        label = stringResource(R.string.done),
                        role = GlasmioAlertButtonRole.Cancel,
                        onClick = onDismiss,
                    ),
                ),
            )
        }

        VersionUpdateResult.Failed -> {
            GlasmioAlertDialog(
                visible = visible,
                onDismissRequest = onDismiss,
                title = stringResource(R.string.about_update_failed_title),
                message = stringResource(R.string.about_update_failed_message),
                buttons = listOf(
                    GlasmioAlertButtonSpec(
                        label = stringResource(R.string.done),
                        role = GlasmioAlertButtonRole.Cancel,
                        onClick = onDismiss,
                    ),
                ),
            )
        }
    }
}
