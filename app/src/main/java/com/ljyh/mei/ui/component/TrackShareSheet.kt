package com.ljyh.mei.ui.component

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.ljyh.mei.data.model.MediaMetadata

/** 用系统分享面板分享歌曲标题/歌手，替代原来的网易云分享面板。 */
@Composable
fun TrackShareSheet(
    metadata: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(metadata.id) {
        val text = buildString {
            append(metadata.title)
            val artists = metadata.artists.joinToString(" / ") { it.name }
            if (artists.isNotBlank()) append(" - ").append(artists)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "分享")) }
        onDismiss()
    }
}
