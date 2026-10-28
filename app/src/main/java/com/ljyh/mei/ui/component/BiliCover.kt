package com.ljyh.mei.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.size.Size
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * 列表用的轻量封面：无 crossfade、固定解码尺寸提示，滚动时比通用封面组件更省。
 */
@Composable
fun BiliCover(
    imageUrl: String,
    modifier: Modifier = Modifier,
    decodeWidth: Int = 320,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .size(Size(decodeWidth, decodeWidth * 10 / 16))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
