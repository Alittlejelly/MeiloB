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
import androidx.compose.foundation.layout.Spacer
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
import com.ljyh.mei.ui.component.globalProfileHeaderAction
import com.ljyh.mei.ui.navigation.MeiloXPinnedPage
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioCard
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import androidx.compose.ui.platform.LocalDensity
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.navigation.LibraryPage
import com.ljyh.mei.ui.navigation.registerMeiloXNavigationViewport
import io.github.neoruaa.glasmio.scaffold.rememberGlasmioHeaderScrollEdgeState
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.account.rememberBiliGridColumns
import com.ljyh.mei.ui.screen.main.library.BiliLibraryViewModel
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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

    val pages = LibraryPage.biliPages
    // 持久化的默认页 preference 或恢复出来的 library_category 路由仍可能指向
    // 上游已被裁掉的页面（歌曲/专辑/播客…）。落到那些页时 when 会走 else 分支，
    // 整页空白，所以这里统一夹到本分叉真实存在的两页。
    val page = selectedPage.takeIf { it in pages } ?: LibraryPage.Favorites
    val listState = rememberLazyListState()
    // 媒体库同样自持 LazyColumn，需要把视口交给导航滚动状态。
    registerMeiloXNavigationViewport(listState)
    val tabPosition = remember {
        Animatable(pages.indexOf(page).coerceAtLeast(0).toFloat(), 0.001f)
    }
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val collapseDistance = with(LocalDensity.current) { 56.dp.toPx() }
    // 只拿 State 容器、不在组合阶段解包：滚动偏移每帧都在变，一旦在这里读它，
    // 整页（含 LazyColumn 的 item lambda）就会随每个滚动像素重组。
    val collapseProgressState = remember(listState, collapseDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val headerEdgeState = rememberGlasmioHeaderScrollEdgeState(listState)
    val title = stringResource(if (isCategoryPage) page.titleRes else R.string.app_tab_library)
    var isPullRefreshing by remember(page) { mutableStateOf(false) }
    val pullRefreshing = (isPullRefreshing && biliRefreshing)
    val biliGridColumns = rememberBiliGridColumns()
    // 刷新请求发出后等 ViewModel 真正跑完再收起指示器。原先这个标记置位后从不复位，
    // 之后每次后台刷新都会被当成“正在下拉刷新”。
    LaunchedEffect(isPullRefreshing) {
        if (!isPullRefreshing) return@LaunchedEffect
        withTimeoutOrNull(3_000) { snapshotFlow { biliRefreshing }.first { it } }
        snapshotFlow { biliRefreshing }.first { !it }
        isPullRefreshing = false
    }
    // 往回撤多少算“取消”，对齐 iOS 下拉刷新的手感。
    val refreshCancelPx = with(LocalDensity.current) { 16.dp.toPx() }
    val canPullRefresh = biliAccount != null
    fun requestRefresh() {
        if (!canPullRefresh || pullRefreshing) return
        isPullRefreshing = true
        // 下拉刷新必须绕过列表缓存，否则看到的是刚缓存的那份。
        biliFavorites.refresh(force = true)
    }

    MeiloXPinnedPage(
        title = title,
        bottomPadding = insets.calculateBottomPadding(),
        collapseProgressProvider = { collapseProgressState.value },
        // 与首页一致：滚动时标题栏下方需要渐进模糊。
        topBarBlurProgressProvider = { headerEdgeState.value },
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
        actions = {
            if (isNavigationTab) listOf(globalProfileHeaderAction()) else emptyList()
        },
    ) { contentPadding ->
        val pullState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Material3 在 onPreFling 里按“松手那一刻的拉力”决定是否刷新，而顶部回弹会
                // 吃掉往回拖的位移，拉力一直挂在阈值上 —— 于是越过回弹后往回滑也会刷新。
                // 这里改成自己跟手势：越过阈值后手指若又往上收了 16dp，就认为是在回撤，松手不刷新。
                .pointerInput(canPullRefresh, refreshCancelPx) {
                    if (!canPullRefresh) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lowestY = down.position.y
                        var armed = false
                        var retracted = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.position.y > lowestY) lowestY = change.position.y
                            if (lowestY - change.position.y > refreshCancelPx) retracted = true
                            if (pullState.distanceFraction >= 0.999f) armed = true
                            if (!change.pressed) break
                        }
                        if (armed && !retracted) requestRefresh()
                    }
                }
                .pullToRefresh(
                    isRefreshing = pullRefreshing,
                    enabled = canPullRefresh,
                    // 触发条件改由上面的手势判定，这里不再让它自己触发。
                    onRefresh = {},
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
                        GlasmioSegmentedControl(
                            items = pages.map { it to stringResource(it.titleRes) },
                            selected = page,
                            onSelected = onPageSelect,
                            positionAnimation = tabPosition,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                when (page) {
                    LibraryPage.Favorites -> biliFolderPage(
                        state = biliCreated,
                        isLoggedIn = biliAccount != null,
                        emptyText = "还没有收藏夹内容",
                        folderKeyPrefix = "bili_fav_",
                        fallbackTitle = "收藏夹",
                        navController = navController,
                        columns = biliGridColumns,
                    )

                    LibraryPage.Collections -> biliFolderPage(
                        state = biliCollected,
                        isLoggedIn = biliAccount != null,
                        emptyText = "还没有收藏合集",
                        folderKeyPrefix = "bili_col_",
                        fallbackTitle = "收藏合集",
                        navController = navController,
                        columns = biliGridColumns,
                    )

                    else -> Unit
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
    columns: Int = 1,
) {
    if (!isLoggedIn) {
        item(key = "${folderKeyPrefix}login-gate") {
            GlasmioCard(
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
                val openFolder: (com.ljyh.mei.data.repository.BiliFavFolder) -> Unit = { folder ->
                    Screen.BiliFavFolder.navigate(navController) {
                        addPath(folder.id.toString())
                        addPath(android.net.Uri.encode(folder.title.ifBlank { fallbackTitle }))
                    }
                }
                if (columns <= 1) {
                    itemsIndexed(
                        folders,
                        key = { _, folder -> folderKeyPrefix + folder.id },
                        contentType = { _, _ -> "bili-folder-row" },
                    ) { _, folder ->
                        BiliFavFolderRow(folder, fallbackTitle) { openFolder(folder) }
                    }
                } else {
                    itemsIndexed(
                        folders.chunked(columns),
                        key = { _, row -> folderKeyPrefix + row.joinToString("-") { it.id.toString() } },
                        contentType = { _, _ -> "bili-folder-row-grid" },
                    ) { _, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { folder ->
                                Box(modifier = Modifier.weight(1f)) {
                                    BiliFavFolderRow(folder, fallbackTitle) { openFolder(folder) }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
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
        alpha = io.github.neoruaa.glasmio.core.LocalGroupedListBackgroundAlpha.current.coerceIn(0f, 1f),
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
            io.github.neoruaa.glasmio.core.LocalGroupedListIconColor provides colors.accent,
            androidx.compose.material3.LocalContentColor provides colors.content,
        ) {
            content()
        }
    }
}
