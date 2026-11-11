package com.ljyh.mei.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.size.Size
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext

/**
 * 列表用的轻量封面：无 crossfade、按实际显示尺寸解码，滚动时比通用封面组件更省。
 *
 * 解码尺寸由调用方按控件宽度算（见 [com.ljyh.mei.ui.screen.account.BiliTrackRow]）：
 * 网格里一列可能只有 70dp，固定解 320px 等于白白多解 4 倍的像素，
 * 一屏二三十张封面时会直接顶到 GC。
 */
@Composable
fun BiliCover(
    imageUrl: String,
    modifier: Modifier = Modifier,
    decodeWidth: Int = 320,
    crossfade: Boolean = false,
) {
    val context = LocalContext.current
    // 列表项滚动时会反复组合，请求对象本身也缓存起来，避免每次重组重建 builder。
    val model = remember(context, imageUrl, decodeWidth, crossfade) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(decodeWidth, decodeWidth * 10 / 16))
            .crossfade(crossfade)
            .build()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
