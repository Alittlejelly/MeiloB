package com.ljyh.mei.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ljyh.mei.R
import com.ljyh.mei.data.model.bili.BiliAccount
import com.ljyh.mei.data.repository.BiliRepository
import io.github.neoruaa.glasmio.icons.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.navigation.meiloxHeaderAction
import io.github.neoruaa.glasmio.scaffold.GlasmioHeaderAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 顶栏头像入口的数据源。本分叉只登录哔哩哔哩，因此不再读取网易云 cookie /
 * 昵称偏好——那些值在这里永远为空，会让入口指回已被移除的网易云路由。
 */
@HiltViewModel
class BiliProfileViewModel @Inject constructor(
    biliRepository: BiliRepository,
) : ViewModel() {
    val account: StateFlow<BiliAccount?> = biliRepository.accountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun globalProfileHeaderAction(
    viewModel: BiliProfileViewModel = hiltViewModel(),
): GlasmioHeaderAction {
    val navController = LocalNavController.current
    val account by viewModel.account.collectAsState()
    val accountDescription = stringResource(R.string.account_home)
    return meiloxHeaderAction(
        key = "account",
        visualKey = listOf("account", account?.avatar.orEmpty(), account?.mid ?: 0L),
        contentDescription = account?.name?.takeIf(String::isNotBlank) ?: accountDescription,
        onClick = {
            if (account == null) Screen.BiliLogin.navigate(navController)
            else Screen.BiliAccount.navigate(navController)
        },
    ) {
        ProfileAvatar(account = account, description = accountDescription)
    }
}

@Composable
private fun ProfileAvatar(account: BiliAccount?, description: String) {
    // Shared actions reserve a 24dp glyph slot inside their 44dp hit surface.
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        val avatar = account?.avatar?.takeIf(String::isNotBlank)
        if (avatar == null || account == null) {
            SfIcon("person.crop.circle", null, Modifier.requiredSize(27.dp), size = 27.dp)
        } else {
            AsyncImage(
                model = avatar,
                contentDescription = account.name.takeIf(String::isNotBlank) ?: description,
                modifier = Modifier.requiredSize(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
