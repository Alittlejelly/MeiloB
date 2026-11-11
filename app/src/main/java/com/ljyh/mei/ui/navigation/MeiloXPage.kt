package com.ljyh.mei.ui.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import com.ljyh.mei.R
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.ui.GlasmioIconButton

/** A mounted entry publishes chrome; the navigation host owns only its button surfaces. */
internal class MeiloXPageHeader {
    var spec by mutableStateOf(GlasmioHeaderSpec(""))
    var viewport by mutableStateOf<BottomNavigationViewport?>(null)
    var mounted = 0
}

internal class MeiloXPageHeaders {
    private val entries = mutableMapOf<Any, MeiloXPageHeader>()
    private var retained = emptySet<Any>()
    fun entry(key: Any): MeiloXPageHeader = entries.getOrPut(key) { MeiloXPageHeader() }
    fun retain(keys: Set<Any>) {
        retained = keys
        prune()
    }
    fun unmount(header: MeiloXPageHeader) { header.mounted--; prune() }
    private fun prune() { entries.entries.removeAll { (key, value) -> key !in retained && value.mounted == 0 } }
}

internal val LocalMeiloXSearchTopPadding = compositionLocalOf { 0.dp }
private val LocalMeiloXPageHeader = staticCompositionLocalOf<MeiloXPageHeader?> { null }

@Composable
internal fun registerMeiloXNavigationViewport(listState: LazyListState) {
    val viewport = remember(listState) {
        object : BottomNavigationViewport {
            override val rangePx: Float get() = listState.scrollIndicatorState?.let {
                (it.contentSize - it.viewportSize).coerceAtLeast(0).toFloat()
            } ?: 0f
            override val distanceToStartPx: Float get() =
                if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat()
                else Float.POSITIVE_INFINITY
            override val atStart: Boolean get() = !listState.canScrollBackward
            override val atEnd: Boolean get() = !listState.canScrollForward
            override val isScrolling: Boolean get() = listState.isScrollInProgress
        }
    }
    registerMeiloXNavigationViewport(viewport)
}

@Composable
internal fun registerMeiloXNavigationViewport(gridState: LazyGridState) {
    val viewport = remember(gridState) {
        object : BottomNavigationViewport {
            override val rangePx: Float get() = gridState.scrollIndicatorState?.let {
                (it.contentSize - it.viewportSize).coerceAtLeast(0).toFloat()
            } ?: 0f
            override val distanceToStartPx: Float get() =
                if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset.toFloat()
                else Float.POSITIVE_INFINITY
            override val atStart: Boolean get() = !gridState.canScrollBackward
            override val atEnd: Boolean get() = !gridState.canScrollForward
            override val isScrolling: Boolean get() = gridState.isScrollInProgress
        }
    }
    registerMeiloXNavigationViewport(viewport)
}

@Composable
private fun registerMeiloXNavigationViewport(viewport: BottomNavigationViewport) {
    val header = LocalMeiloXPageHeader.current
    DisposableEffect(header, viewport) {
        header?.viewport = viewport
        onDispose {
            if (header != null && header.viewport === viewport) header.viewport = null
        }
    }
}

@Composable
internal fun MeiloXPageEntry(
    key: MeiRoute,
    headers: MeiloXPageHeaders,
    content: @Composable () -> Unit,
) {
    // Glasmio's clock publishes NavEntry.contentKey, which this app sets to the route string.
    val header = remember(headers, key.route) { headers.entry(key.route) }
    DisposableEffect(header) {
        header.mounted++
        onDispose { headers.unmount(header) }
    }
    CompositionLocalProvider(LocalMeiloXPageHeader provides header) {
        Box(Modifier.fillMaxSize().testTag("MeiloXPage:${key.route}")) {
            content()
            if (!rememberDeviceInfo().isLandscape || LocalGlasmioSidebarExpanded.current) {
                GlasmioPageTitle(key.route, Modifier.testTag("MeiloXPageTitle:${key.route}"))
            }
        }
    }
}

/** Capture page typography/colors without replacing the shared host's backdrop or menu context. */
@Composable
fun meiloxHeaderAction(
    key: Any,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    visualKey: Any = key,
    menuAnchor: GlasmioMenuAnchorState? = null,
    content: @Composable () -> Unit,
): GlasmioHeaderAction {
    val colors = LocalGlassColors.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    return GlasmioHeaderAction(key, visualKey, contentDescription, onClick, enabled, menuAnchor) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            CompositionLocalProvider(
                LocalGlassColors provides colors,
                LocalGlassContentColor provides colors.content,
                LocalContentColor provides colors.content,
            ) { content() }
        }
    }
}

@Composable
fun MeiloXPinnedPage(
    title: String,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    navigateBackContentDescription: String = stringResource(R.string.navigation_back),
    actions: @Composable () -> List<GlasmioHeaderAction> = { emptyList() },
    actionsStyle: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
    actionsSeparationProgress: Float? = null,
    collapseProgress: Float = 1f,
    backgroundColor: Color? = null,
    topBarBlurProgress: Float = 0f,
    /**
     * 上面两个进度的"绘制阶段读取"入口。滚动进度每帧都在变，从组合阶段读它会让整页
     * （含列表内容）跟着每帧重组。传 provider 后这些值只在绘制时求值，画面不变。
     */
    collapseProgressProvider: (() -> Float)? = null,
    topBarBlurProgressProvider: (() -> Float)? = null,
    backgroundBrush: Brush? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val header = LocalMeiloXPageHeader.current
    val colors = LocalGlassColors.current
    val currentCollapse = rememberUpdatedState(collapseProgress)
    val currentEdge = rememberUpdatedState(topBarBlurProgress)
    val collapseSource = rememberUpdatedState(collapseProgressProvider)
    val edgeSource = rememberUpdatedState(topBarBlurProgressProvider)
    val scrollState = remember {
        // 闭包只读 rememberUpdatedState 的容器，因此 provider 换实例也不会读到旧值。
        GlasmioHeaderScrollState(
            { collapseSource.value?.invoke() ?: currentCollapse.value },
            { edgeSource.value?.invoke() ?: currentEdge.value },
        )
    }
    val leading = onNavigateBack?.let { navigateBack ->
        listOf(GlasmioHeaderGroup("navigation", listOf(meiloxHeaderAction(
            key = "back",
            contentDescription = navigateBackContentDescription,
            onClick = navigateBack,
        ) { SfIcon(SfSymbol.ChevronBack, null, mirrored = true) })))
    }.orEmpty()
    val hidesHeading = rememberDeviceInfo().isLandscape && !LocalGlasmioSidebarExpanded.current
    val trailingActions = actions()
    val spec = GlasmioHeaderSpec(
        title = if (hidesHeading) "" else title,
        subtitle = subtitle.takeUnless { hidesHeading },
        leading = leading,
        trailing = if (trailingActions.isEmpty()) emptyList() else listOf(
            GlasmioHeaderGroup("actions", trailingActions, actionsStyle),
        ),
        scrollState = scrollState,
        colors = colors,
    )
    SideEffect {
        if (header != null) {
            header.spec = spec
        }
    }
    if (header == null) {
        // Standalone previews and isolated component tests keep a local toolbar.
        GlasmioPinnedPage(
            title = if (hidesHeading) "" else title, subtitle = subtitle.takeUnless { hidesHeading }, bottomPadding = bottomPadding, modifier = modifier,
            onNavigateBack = onNavigateBack, navigateBackContentDescription = navigateBackContentDescription,
            actions = {
                trailingActions.forEach { action ->
                    key(action.key) {
                        GlasmioIconButton(action.onClick, enabled = action.enabled,
                            modifier = action.menuAnchor?.let { Modifier.glasmioMenuAnchor(it) } ?: Modifier,
                        ) { action.content() }
                    }
                }
            },
            actionsStyle = actionsStyle, actionsSeparationProgress = actionsSeparationProgress,
            // 这条分支没有页面宿主，只能取即时值（组合阶段读取一次，属于冷路径）。
            collapseProgress = collapseProgressProvider?.invoke() ?: collapseProgress,
            backgroundColor = backgroundColor,
            topBarBlurProgress = topBarBlurProgressProvider?.invoke() ?: topBarBlurProgress,
            backgroundBrush = backgroundBrush,
            background = background, content = { padding ->
                val extra = LocalMeiloXSearchTopPadding.current
                content(PaddingValues(start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding() + extra,
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = padding.calculateBottomPadding()))
            },
        )
        return
    }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val padding = PaddingValues(
        start = IosPageHorizontalPadding, top = status + 72.dp + LocalMeiloXSearchTopPadding.current,
        end = IosPageHorizontalPadding, bottom = bottomPadding + 24.dp,
    )
    GlasmioPageBody(
        contentPadding = padding, modifier = modifier,
        backgroundColor = backgroundColor ?: colors.groupedBackground,
        backgroundBrush = backgroundBrush, background = background,
        topBarBlurProgress = topBarBlurProgress,
        topBarBlurProgressProvider = topBarBlurProgressProvider, content = content,
    )
}

/** The same page chrome remains available while a retained route reconnects to its service. */
@Composable
fun MeiloXLoadingPage(title: String, onNavigateBack: (() -> Unit)? = null) {
    MeiloXPinnedPage(title = title, onNavigateBack = onNavigateBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalGlassColors.current.accent)
        }
    }
}

@Composable
fun MeiloXPinnedListPage(
    title: String,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    horizontalContentPadding: Dp = IosPageHorizontalPadding,
    topContentPaddingAdjustment: Dp = 0.dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    showsLargeTitle: Boolean = true,
    collapseEnterBreakpoint: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
    collapseExitBreakpoint: Dp = GlasmioHeaderCollapseDefaults.ExitBreakpoint,
    collapseAnimationSpec: AnimationSpec<Float> = GlasmioHeaderCollapseDefaults.Spring,
    largeTitleHorizontalPadding: Dp = 4.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    onNavigateBack: (() -> Unit)? = null,
    navigateBackContentDescription: String = stringResource(R.string.navigation_back),
    actions: @Composable () -> List<GlasmioHeaderAction> = { emptyList() },
    actionsStyle: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
    actionsSeparationProgress: Float? = null,
    backgroundColor: Color? = null,
    backgroundBrush: Brush? = null,
    bottomPaddingValues: PaddingValues? = null,
    /** 包裹列表的外层（如首页的下拉刷新）；列表仍是本组件自己的 LazyColumn。 */
    listWrapper: (@Composable (@Composable () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    registerMeiloXNavigationViewport(listState)
    // 这里只拿 State 容器、不解包：滚动进度每帧都在变，一旦在组合阶段读取，
    // 整页（含 LazyColumn 的 item lambda）就会跟着每个滚动像素重组。
    val collapseState = rememberGlasmioHeaderCollapseState(listState,
        collapseEnterBreakpoint, collapseExitBreakpoint, collapseAnimationSpec)
    val edgeState = rememberGlasmioHeaderScrollEdgeState(listState, collapseEnterBreakpoint)
    val showTitle = showsLargeTitle && (!rememberDeviceInfo().isLandscape || LocalGlasmioSidebarExpanded.current)
    val collapseProvider: () -> Float = if (showTitle) ({ collapseState.value }) else ({ 1f })
    MeiloXPinnedPage(
        title = title, subtitle = subtitle, bottomPadding = bottomPadding, modifier = modifier,
        onNavigateBack = onNavigateBack, navigateBackContentDescription = navigateBackContentDescription,
        actions = actions, actionsStyle = actionsStyle, actionsSeparationProgress = actionsSeparationProgress,
        collapseProgressProvider = collapseProvider,
        topBarBlurProgressProvider = { edgeState.value },
        backgroundColor = backgroundColor, backgroundBrush = backgroundBrush,
    ) { padding ->
        val listPadding = remember(padding, horizontalContentPadding, bottomPaddingValues, topContentPaddingAdjustment) {
            object : PaddingValues by padding {
                override fun calculateTopPadding() = padding.calculateTopPadding() + topContentPaddingAdjustment
                override fun calculateLeftPadding(layoutDirection: LayoutDirection) = horizontalContentPadding
                override fun calculateRightPadding(layoutDirection: LayoutDirection) = horizontalContentPadding
                override fun calculateBottomPadding() = padding.calculateBottomPadding() +
                    (bottomPaddingValues?.calculateBottomPadding() ?: 0.dp)
            }
        }
        key(listState) {
            val list: @Composable () -> Unit = {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding, verticalArrangement = verticalArrangement,
                ) {
                    if (showTitle) item(key = "ios-large-title:$title") {
                        Column(Modifier.fillMaxWidth().graphicsLayer {
                            // 折叠进度是弹簧动画值：在图层里读它，只失效绘制，不触发重组。
                            val collapse = collapseProvider().coerceIn(0f, 1f)
                            alpha = 1f - collapse
                            scaleX = 1f - .04f * collapse
                            scaleY = scaleX
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                            // 等价于原来的 Modifier.blur(6.dp * collapse)（Rectangle 边缘 = CLAMP + 裁剪）。
                            val radiusPx = 6.dp.toPx() * collapse
                            clip = true
                            renderEffect = if (radiusPx > 0.01f) {
                                androidx.compose.ui.graphics.BlurEffect(
                                    radiusPx, radiusPx, androidx.compose.ui.graphics.TileMode.Clamp,
                                )
                            } else null
                        }.offset(y = (-10).dp)
                            .padding(horizontal = largeTitleHorizontalPadding, vertical = 6.dp),
                        ) {
                            Text(title, style = IosTypography.largeTitle, fontWeight = FontWeight.Bold,
                                color = LocalGlassColors.current.content)
                            subtitle?.let { Text(it, style = IosTypography.subheadline,
                                color = LocalGlassColors.current.secondaryContent, modifier = Modifier.padding(top = 3.dp)) }
                        }
                    }
                    content()
                }
            }
            if (listWrapper != null) listWrapper(list) else list()
        }
    }
}
