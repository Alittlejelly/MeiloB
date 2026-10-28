package com.ljyh.mei.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.ljyh.mei.di.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import com.ljyh.mei.ui.screen.about.AboutScreen
import com.ljyh.mei.ui.screen.history.HistoryScreen
import com.ljyh.mei.ui.screen.local.LocalMusicScreen
import com.ljyh.mei.ui.screen.local.LocalSongListScreen
import com.ljyh.mei.ui.screen.main.home.HomeHubScreen
import com.ljyh.mei.ui.navigation.LibraryPage
import com.ljyh.mei.ui.screen.main.library.LibraryScreen
import com.ljyh.mei.ui.screen.playlist.PlaylistScreen
import com.ljyh.mei.ui.screen.setting.AppearanceSettings
import com.ljyh.mei.ui.screen.setting.ContentsSetting
import com.ljyh.mei.ui.screen.setting.DownloadManageScreen
import com.ljyh.mei.ui.screen.setting.DownloadSetting
import com.ljyh.mei.ui.screen.setting.StorageManagementScreen
import com.ljyh.mei.ui.screen.setting.GeneralSettings
import com.ljyh.mei.ui.screen.setting.PlaySetting
import com.ljyh.mei.ui.screen.setting.EqualizerSettings
import com.ljyh.mei.ui.screen.setting.SettingScreen
import com.ljyh.mei.ui.screen.log.LogScreen
import com.ljyh.mei.ui.screen.account.BiliFavFolderScreen
import com.ljyh.mei.ui.screen.account.BiliLoginScreen
import com.ljyh.mei.ui.screen.account.BiliAccountHomeScreen
import com.ljyh.mei.ui.screen.search.SearchLandingScreen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun navigationEntry(
    route: String,
    scrollBehavior: TopAppBarScrollBehavior,
    isNavigationTab: Boolean = false,
) {
    when {
        route == Screen.Home.route -> HomeHubScreen()
        route == Screen.Library.route -> LibraryScreen(isNavigationTab = isNavigationTab)
        route.startsWith("${Screen.LibraryCategory.route}/") -> {
            LibraryPage.entries.firstOrNull {
                it.name == route.substringAfter("${Screen.LibraryCategory.route}/")
            }?.let { LibraryScreen(category = it) }
        }
        route == Screen.Search.route -> SearchLandingScreen(isNavigationTab = isNavigationTab)
        route == Screen.BiliLogin.route -> BiliLoginScreen()
        route == Screen.BiliAccount.route -> BiliAccountHomeScreen()
        route.startsWith("${Screen.BiliFavFolder.route}/") -> {
            val args = route.substringAfter("${Screen.BiliFavFolder.route}/").split('/', limit = 2)
            val folderId = args.firstOrNull()?.toLongOrNull()
            if (folderId != null) {
                BiliFavFolderScreen(
                    folderId = folderId,
                    folderTitle = args.getOrNull(1)
                        ?.let { URLDecoder.decode(it, "UTF-8") }
                        .orEmpty()
                        .ifBlank { "收藏夹" },
                )
            }
        }
        route == Screen.Test.route -> Test()
        route == Screen.Setting.route -> SettingScreen(scrollBehavior)
        route == Screen.AppearanceSettings.route -> AppearanceSettings(scrollBehavior)
        route == Screen.GeneralSettings.route -> GeneralSettings()
        route == Screen.ContentSettings.route -> ContentsSetting(scrollBehavior)
        route == Screen.PlaySettings.route -> PlaySetting(scrollBehavior)
        route == Screen.EqualizerSettings.route -> EqualizerSettings()
        route == Screen.DownloadSettings.route -> DownloadSetting(scrollBehavior)
        route == Screen.StorageManagement.route -> StorageManagementScreen()
        route == Screen.DownloadManage.route -> DownloadManageScreen(
            scrollBehavior = scrollBehavior,
            isNavigationTab = isNavigationTab,
        )
        route == Screen.LocalMusic.route -> LocalMusicScreen(scrollBehavior)
        route == Screen.About.route -> AboutScreen()
        route == Screen.Log.route -> LogScreen()
        route == Screen.History.route -> HistoryScreen(isNavigationTab = isNavigationTab)
        route.startsWith("${Screen.LocalSongList.route}/") -> {
            val args = route.substringAfter("${Screen.LocalSongList.route}/").split('/', limit = 2)
            if (args.size == 2) {
                val type = args[0]
                val name = args[1]
                val filterValue: String
                val title: String
                when (type) {
                    "folder" -> {
                        filterValue = URLDecoder.decode(name, "UTF-8")
                        title = filterValue.substringAfterLast('/').ifEmpty {
                            filterValue.substringAfterLast(":")
                        }
                    }
                    "artist", "album" -> {
                        filterValue = name
                        title = name
                    }
                    else -> {
                        filterValue = name
                        title = stringResource(com.ljyh.mei.R.string.local_music_all_songs)
                    }
                }
                LocalSongListScreen(
                    filterType = if (type == "folder") "folder" else type,
                    filterValue = filterValue,
                    title = title,
                    scrollBehavior = scrollBehavior,
                )
            }
        }
        route.startsWith("${Screen.PlayList.route}/") -> {
            route.substringAfter("${Screen.PlayList.route}/").toLongOrNull()
                ?.let { PlaylistScreen(id = it) }
        }
        else -> error("Unknown Mei navigation route: $route")
    }
}
