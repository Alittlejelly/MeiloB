package com.ljyh.mei.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ljyh.mei.R
import com.ljyh.mei.constants.BiliAvatarKey
import com.ljyh.mei.constants.BiliNicknameKey
import com.ljyh.mei.constants.BiliSessdataKey
import com.ljyh.mei.ui.glass.GlassIconButton
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.rememberPreference

/** Shared account entry shown on every primary tab's pinned navigation bar. */
@Composable
fun GlobalProfileAvatarButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val (sessdata) = rememberPreference(BiliSessdataKey, "")
    val (avatarUrl) = rememberPreference(BiliAvatarKey, "")
    val (nickname) = rememberPreference(BiliNicknameKey, "")
    val accountDescription = stringResource(R.string.account_home)

    GlassIconButton(
        onClick = {
            if (sessdata.isBlank()) {
                Screen.BiliLogin.navigate(navController)
            } else {
                Screen.BiliAccount.navigate(navController)
            }
        },
        modifier = modifier,
    ) {
        if (avatarUrl.isBlank() || sessdata.isBlank()) {
            SfIcon(
                systemName = "person.crop.circle",
                contentDescription = accountDescription,
                size = 27.dp,
            )
        } else {
            AsyncImage(
                model = avatarUrl,
                contentDescription = nickname.ifBlank { accountDescription },
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
