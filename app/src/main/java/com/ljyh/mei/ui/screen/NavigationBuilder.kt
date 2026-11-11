package com.ljyh.mei.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.navigation.MeiloXPinnedPage
import com.ljyh.mei.ui.screen.main.library.LibraryScreen
import com.ljyh.mei.ui.screen.playlist.PlaylistScreen
import com.ljyh.mei.ui.screen.setting.AppearanceSettings
import com.ljyh.mei.ui.screen.setting.ContentsSetting
import com.ljyh.mei.ui.screen.setting.DownloadManageScreen
import com.ljyh.mei.ui.screen.setting.DownloadSetting
import com.ljyh.mei.ui.screen.setting.StorageManagementScreen
import com.ljyh.mei.ui.screen.setting.GeneralSettings
import com.ljyh.mei.ui.screen.setting.LyricsSettings
import com.ljyh.mei.ui.screen.setting.PlaySetting
import com.ljyh.mei.ui.screen.setting.EqualizerSettings
import com.ljyh.mei.ui.screen.setting.SettingScreen
import com.ljyh.mei.ui.screen.log.LogScreen
import com.ljyh.mei.ui.screen.search.BiliSearchResultScreen
import com.ljyh.mei.ui.screen.search.SearchLandingScreen
import com.ljyh.mei.ui.screen.account.BiliFavFolderScreen
import com.ljyh.mei.ui.screen.account.BiliLoginScreen
import com.ljyh.mei.ui.screen.account.BiliAccountHomeScreen
import com.ljyh.mei.ui.screen.account.BiliUploaderScreen
import com.ljyh.mei.ui.screen.account.BiliUploaderGroupScreen
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    composable(Screen.Home.route) {
        HomeHubScreen()
    }

    composable(Screen.Library.route) {
        LibraryScreen()
    }

    composable(
        route = "${Screen.LibraryCategory.route}/{page}",
        arguments = listOf(navArgument("page") { type = NavType.StringType }),
    ) { entry ->
        LibraryPage.entries.firstOrNull { it.name == entry.arguments?.getString("page") }?.let {
            LibraryScreen(category = it)
        }
    }

    composable(Screen.Search.route) {
        SearchLandingScreen()
    }

    composable(
        route = "${Screen.PlayList.route}/{id}",
        arguments = listOf(navArgument("id") { type = NavType.LongType }),
    ) {
        PlaylistScreen(id = it.arguments!!.getLong("id"))
    }

    composable(Screen.Test.route) {
        Test()
    }

    composable(Screen.Setting.route) {
        SettingScreen(scrollBehavior)
    }

    composable(Screen.AppearanceSettings.route) {
        AppearanceSettings(scrollBehavior)
    }

    composable(Screen.GeneralSettings.route) {
        GeneralSettings()
    }

    composable(Screen.LyricsSettings.route) {
        LyricsSettings()
    }

    composable(Screen.ContentSettings.route) {
        ContentsSetting(scrollBehavior)
    }

    composable(Screen.PlaySettings.route) {
        PlaySetting(scrollBehavior)
    }

    composable(Screen.EqualizerSettings.route) {
        EqualizerSettings()
    }

    composable(Screen.DownloadSettings.route) {
        DownloadSetting(scrollBehavior)
    }

    composable(Screen.StorageManagement.route) {
        StorageManagementScreen()
    }

    composable(Screen.DownloadManage.route) {
        DownloadManageScreen(scrollBehavior)
    }

    composable(Screen.LocalMusic.route) {
        LocalMusicScreen(scrollBehavior)
    }

    composable(
        route = "${Screen.LocalSongList.route}/{type}/{name}",
        arguments = listOf(
            navArgument("type") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType }
        )
    ) {
        val type = it.arguments?.getString("type") ?: "all"
        val name = it.arguments?.getString("name") ?: ""
        val context = LocalContext.current

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

    composable(Screen.About.route) {
        AboutScreen()
    }

    composable(Screen.Log.route) {
        LogScreen()
    }

    composable(Screen.History.route) {
        HistoryScreen()
    }

    // ---- Bilibili ----
    composable(Screen.BiliLogin.route) {
        BiliLoginScreen()
    }

    composable(Screen.BiliAccount.route) {
        BiliAccountHomeScreen()
    }

    composable(
        route = "${Screen.BiliFavFolder.route}/{folderId}/{folderTitle}",
        arguments = listOf(
            navArgument("folderId") { type = NavType.LongType },
            navArgument("folderTitle") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        BiliFavFolderScreen(
            folderId = entry.arguments!!.getLong("folderId"),
            folderTitle = URLDecoder.decode(
                entry.arguments?.getString("folderTitle").orEmpty(), "UTF-8",
            ).ifBlank { "收藏夹" },
        )
    }

    composable(
        route = "${Screen.BiliUploader.route}/{mid}",
        arguments = listOf(navArgument("mid") { type = NavType.LongType }),
    ) { entry ->
        BiliUploaderScreen(mid = entry.arguments!!.getLong("mid"))
    }

    composable(
        route = "${Screen.BiliUploaderGroup.route}/{mid}/{seasonId}/{groupName}",
        arguments = listOf(
            navArgument("mid") { type = NavType.LongType },
            navArgument("seasonId") { type = NavType.LongType },
            navArgument("groupName") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        BiliUploaderGroupScreen(
            mid = entry.arguments!!.getLong("mid"),
            seasonId = entry.arguments!!.getLong("seasonId"),
            groupName = URLDecoder.decode(
                entry.arguments?.getString("groupName").orEmpty(), "UTF-8",
            ),
        )
    }
}

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
        route == Screen.Search.route -> SearchLandingScreen()
        route.startsWith("${Screen.SearchResult.route}/") -> {
            // 搜索结果是 B 站全站搜索结果，多出来的类型段（保留兼容）不再参与请求。
            val keyword = route.substringAfter("${Screen.SearchResult.route}/")
                .substringBefore('/')
                .let { URLDecoder.decode(it, "UTF-8") }
            if (keyword.isNotBlank()) BiliSearchResultScreen(keyword = keyword)
        }
        route.startsWith("${Screen.PlayList.route}/") -> {
            route.substringAfter("${Screen.PlayList.route}/").toLongOrNull()
                ?.let { PlaylistScreen(id = it) }
        }
        route == Screen.Test.route -> Test()
        route == Screen.Setting.route -> SettingScreen(scrollBehavior)
        route == Screen.AppearanceSettings.route -> AppearanceSettings(scrollBehavior)
        route == Screen.GeneralSettings.route -> GeneralSettings()
        route == Screen.LyricsSettings.route -> LyricsSettings()
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

        // ---- Bilibili ----
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
        route.startsWith("${Screen.BiliUploader.route}/") -> {
            val mid = route.substringAfter("${Screen.BiliUploader.route}/").toLongOrNull()
            if (mid != null) BiliUploaderScreen(mid = mid)
        }
        route.startsWith("${Screen.BiliUploaderGroup.route}/") -> {
            val args = route.substringAfter("${Screen.BiliUploaderGroup.route}/").split('/', limit = 3)
            val mid = args.getOrNull(0)?.toLongOrNull()
            val seasonId = args.getOrNull(1)?.toLongOrNull()
            if (mid != null && seasonId != null) {
                BiliUploaderGroupScreen(
                    mid = mid,
                    seasonId = seasonId,
                    groupName = args.getOrNull(2)
                        ?.let { URLDecoder.decode(it, "UTF-8") }
                        .orEmpty(),
                )
            }
        }
        // 本分叉没有网易云歌手数据源。「前往UP主」在个别条目上拿不到 isBili 标记时
        // 仍会拼出 artist/<mid>，这里统一落到 UP 主空间页，避免空白页。
        route.startsWith("${Screen.Artist.route}/") -> {
            val mid = route.substringAfter("${Screen.Artist.route}/").toLongOrNull()
            if (mid != null && mid > 0L) BiliUploaderScreen(mid = mid)
        }
        // 未知/已下线的路由（网易云相关页面在本分叉里已经移除）不再抛异常。
        // 直接抛错会让整个 Activity 闪退，这里退化成可返回的空页面。
        else -> {
            Timber.tag("MeiNavigation").e("Unknown Mei navigation route: %s", route)
            UnknownRoutePage(route = route)
        }
    }
}

@Composable
private fun UnknownRoutePage(route: String) {
    val navController = LocalNavController.current
    MeiloXPinnedPage(
        title = stringResource(com.ljyh.mei.R.string.app_name),
        onNavigateBack = { navController.navigateUp() },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "页面暂不可用（$route）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
