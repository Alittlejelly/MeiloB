package com.ljyh.mei.ui.screen.main.library.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ljyh.mei.R
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.component.GlobalProfileAvatarButton
import com.ljyh.mei.ui.glass.GlassCard
import com.ljyh.mei.ui.glass.GlassSegmentedControl
import com.ljyh.mei.ui.glass.IosPinnedPage
import com.ljyh.mei.ui.glass.IosTypography
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import androidx.compose.ui.platform.LocalDensity
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.navigation.LibraryPage
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.main.library.BiliLibraryViewModel

/** 库（收藏）：顶部分为「收藏夹 / 收藏合集」两栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMobileLayout(
    isNavigationTab: Boolean,
    selectedPage: LibraryPage,
    onPageSelect: (LibraryPage) -> Unit,
    isCategoryPage: Boolean = false,
) {
    val navController = LocalNavController.current
    val biliFavorites: BiliLibraryViewModel = hiltViewModel()
    val biliAccount by biliFavorites.account.collectAsState()
    val biliCreated by biliFavorites.created.collectAsState()
    val biliCollected by biliFavorites.collected.collectAsState()
    val biliRefreshing by biliFavorites.isRefreshing.collectAsState()

    val pages = LibraryPage.entries.toList()
    val listState = rememberLazyListState()
    val tabPosition = remember {
        Animatable(pages.indexOf(selectedPage).coerceAtLeast(0).toFloat(), 0.001f)
    }
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val collapseDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val collapseProgress by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val title = stringResource(if (isCategoryPage) selectedPage.titleRes else R.string.app_tab_library)
    var isPullRefreshing by remember(selectedPage) { mutableStateOf(false) }
    val pullRefreshing = (isPullRefreshing && biliRefreshing)

    IosPinnedPage(
        title = title,
        bottomPadding = insets.calculateBottomPadding(),
        collapseProgress = collapseProgress,
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
        actions = {
            if (isNavigationTab) GlobalProfileAvatarButton()
        },
    ) { contentPadding ->
        val pullState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = pullRefreshing,
                    enabled = biliAccount != null,
                    onRefresh = {
                        isPullRefreshing = true
                        biliFavorites.refresh()
                    },
                    state = pullState,
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "ios-large-title:$title") {
                    Text(
                        title,
                        style = IosTypography.largeTitle,
                        color = LocalGlassColors.current.content,
                        modifier = Modifier
                            .offset(y = (-10).dp)
                            .padding(vertical = 6.dp),
                    )
                }
                if (!isCategoryPage) {
                    item(key = "library-pages") {
                        GlassSegmentedControl(
                            items = pages.map { it to stringResource(it.titleRes) },
                            selected = selectedPage,
                            onSelected = onPageSelect,
                            positionAnimation = tabPosition,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                when (selectedPage) {
                    LibraryPage.Favorites -> biliFolderPage(
                        state = biliCreated,
                        isLoggedIn = biliAccount != null,
                        emptyText = "还没有收藏夹内容",
                        folderKeyPrefix = "bili_fav_",
                        fallbackTitle = "收藏夹",
                        navController = navController,
                    )

                    LibraryPage.Collections -> biliFolderPage(
                        state = biliCollected,
                        isLoggedIn = biliAccount != null,
                        emptyText = "还没有收藏合集",
                        folderKeyPrefix = "bili_col_",
                        fallbackTitle = "收藏合集",
                        navController = navController,
                    )
                }
            }
            if (biliAccount != null) {
                PullToRefreshDefaults.Indicator(
                    isRefreshing = pullRefreshing,
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
                )
            }
        }
    }
}

private fun LazyListScope.biliFolderPage(
    state: Resource<List<com.ljyh.mei.data.repository.BiliFavFolder>>,
    isLoggedIn: Boolean,
    emptyText: String,
    folderKeyPrefix: String,
    fallbackTitle: String,
    navController: com.ljyh.mei.ui.navigation.MeiNavigator,
) {
    if (!isLoggedIn) {
        item(key = "${folderKeyPrefix}login-gate") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                onClick = { Screen.BiliLogin.navigate(navController) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SfIcon("person.crop.circle", null, size = 42.dp)
                    Text(
                        "登录 B 站后查看收藏内容",
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }
    when (state) {
        is Resource.Loading -> item(key = "${folderKeyPrefix}loading") {
            Text(
                "加载中…",
                fontSize = 14.sp,
                color = LocalGlassColors.current.content.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
        is Resource.Error -> item(key = "${folderKeyPrefix}error") {
            Text(
                state.message ?: "加载失败",
                fontSize = 14.sp,
                color = LocalGlassColors.current.content.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
        is Resource.Success -> {
            val folders = state.data
            if (folders.isEmpty()) {
                item(key = "${folderKeyPrefix}empty") {
                    Text(
                        emptyText,
                        fontSize = 14.sp,
                        color = LocalGlassColors.current.content.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                itemsIndexed(
                    folders,
                    key = { _, folder -> folderKeyPrefix + folder.id },
                    contentType = { _, _ -> "bili-folder-row" },
                ) { _, folder ->
                    BiliFavFolderRow(folder, fallbackTitle) {
                        Screen.BiliFavFolder.navigate(navController) {
                            addPath(folder.id.toString())
                            addPath(android.net.Uri.encode(folder.title.ifBlank { fallbackTitle }))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiliFavFolderRow(
    folder: com.ljyh.mei.data.repository.BiliFavFolder,
    fallbackTitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            SfIcon("music.note.list", null, size = 20.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.title.ifBlank { fallbackTitle },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = LocalGlassColors.current.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                folder.mediaCount.toString() + " 个内容",
                fontSize = 12.sp,
                color = LocalGlassColors.current.content.copy(alpha = 0.55f),
            )
        }
        SfIcon("chevron.forward", null, size = 13.dp, tint = LocalGlassColors.current.content.copy(alpha = 0.35f))
    }
}

internal fun <T> LazyListScope.groupedLazyItems(
    items: List<T>,
    key: ((T) -> Any)? = null,
    contentType: Any? = "grouped-row",
    firstItemTopPadding: Dp = 0.dp,
    horizontalPadding: Dp = 0.dp,
    itemContent: @Composable (item: T, index: Int) -> Unit,
) {
    itemsIndexed(
        items = items,
        key = key?.let { itemKey -> { _, item -> itemKey(item) } },
        contentType = { _, _ -> contentType },
    ) { index, item ->
        GroupedLazyListRow(
            index = index,
            itemCount = items.size,
            modifier = Modifier.padding(
                start = horizontalPadding,
                top = if (index == 0) firstItemTopPadding else 0.dp,
                end = horizontalPadding,
            ),
        ) {
            itemContent(item, index)
        }
    }
}

@Composable
private fun GroupedLazyListRow(
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalGlassColors.current
    val background = colors.elevatedBackground.copy(
        alpha = com.ljyh.mei.ui.glass.LocalGroupedListBackgroundAlpha.current.coerceIn(0f, 1f),
    )
    val shape = when {
        itemCount == 1 -> com.kyant.capsule.ContinuousRoundedRectangle(26.dp)
        index == 0 -> com.kyant.capsule.ContinuousRoundedRectangle(topStart = 26.dp, topEnd = 26.dp)
        index == itemCount - 1 -> com.kyant.capsule.ContinuousRoundedRectangle(bottomStart = 26.dp, bottomEnd = 26.dp)
        else -> androidx.compose.ui.graphics.RectangleShape
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background, shape),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.ljyh.mei.ui.glass.LocalGroupedListIconColor provides colors.accent,
            androidx.compose.material3.LocalContentColor provides colors.content,
        ) {
            content()
        }
    }
}
