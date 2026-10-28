package com.ljyh.mei.ui.screen.main.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ljyh.mei.R
import com.ljyh.mei.constants.LibraryDefaultPageKey
import com.ljyh.mei.constants.LibraryStyle
import com.ljyh.mei.constants.LibraryStyleKey
import com.ljyh.mei.ui.component.GlobalProfileAvatarButton
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.navigation.LibraryPage
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.main.library.component.LibraryMobileLayout
import com.ljyh.mei.utils.dataStore
import com.ljyh.mei.utils.toEnum

internal fun resolveLibraryDefaultPage(value: String, style: LibraryStyle): LibraryPage =
    LibraryPage.entries.firstOrNull { it.name == value } ?: LibraryPage.Favorites

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    isNavigationTab: Boolean = false,
    category: LibraryPage? = null,
) {
    val context = LocalContext.current
    val preferenceState by context.dataStore.data.collectAsState(initial = null)
    val preferences = preferenceState ?: return
    val libraryStyle = preferences[LibraryStyleKey].toEnum(LibraryStyle.Default)
    val defaultPagePreference = preferences[LibraryDefaultPageKey].orEmpty()
    val defaultPage = resolveLibraryDefaultPage(defaultPagePreference, libraryStyle)
    val navController = LocalNavController.current
    var defaultPageApplied by rememberSaveable { mutableStateOf(false) }
    var selectedPage by rememberSaveable { mutableStateOf(defaultPage) }
    LaunchedEffect(Unit) {
        if (!defaultPageApplied) {
            defaultPageApplied = true
            if (category == null && libraryStyle == LibraryStyle.AppleMusic) {
                Screen.LibraryCategory.navigate(navController) { addPath(selectedPage.name) }
            }
        }
    }
    if (category == null && libraryStyle == LibraryStyle.AppleMusic) {
        LibraryCategoryList(isNavigationTab)
        return
    }
    val displayedPage = category ?: selectedPage

    LibraryMobileLayout(
        isNavigationTab = isNavigationTab,
        selectedPage = displayedPage,
        onPageSelect = { selectedPage = it },
        isCategoryPage = category != null,
    )
}

@Composable
private fun LibraryCategoryList(isNavigationTab: Boolean) {
    val categories = LibraryPage.entries
    val navController = LocalNavController.current
    val colors = LocalGlassColors.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    IosPinnedListPage(
        title = stringResource(R.string.app_tab_library),
        bottomPadding = insets.calculateBottomPadding(),
        horizontalContentPadding = 6.dp,
        largeTitleHorizontalPadding = 14.dp,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        backgroundColor = if (colors.isDark) colors.groupedBackground else Color.White,
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
        actions = { if (isNavigationTab) GlobalProfileAvatarButton() },
    ) {
        categories.forEachIndexed { index, page ->
            item(key = "library-category:${page.name}") {
                IosListRow(
                    title = stringResource(page.titleRes),
                    leading = { SfIcon(page.symbol, null, tint = colors.accent) },
                    showTopSeparator = index > 0,
                    onClick = {
                        Screen.LibraryCategory.navigate(navController) { addPath(page.name) }
                    },
                )
            }
        }
    }
}
