package com.ljyh.mei.ui.screen

import androidx.annotation.StringRes
import com.ljyh.mei.R
import com.ljyh.mei.ui.glass.SfSymbol
import com.ljyh.mei.ui.navigation.MeiNavigator
import com.ljyh.mei.ui.navigation.ContentFeature

sealed class Screen(val route:String) {
    data object Home:Screen("home")
    data object BiliLogin:Screen("bili/login")
    data object BiliAccount:Screen("bili/account")
    data object BiliFavFolder:Screen("bili_fav")
    data object BiliUploader:Screen("bili_uploader")
    data object BiliUploaderGroup:Screen("bili_uploader_group")
    data object Library:Screen("library")
    data object LibraryCategory:Screen("library_category")
    data object PlayList:Screen("playlist")
    data object Setting:Screen("setting")
    data object ContentSettings:Screen(("setting/content"))
    data object AppearanceSettings:Screen("setting/appearance")
    data object GeneralSettings:Screen("setting/general")
    data object PlaySettings:Screen("setting/play")
    data object EqualizerSettings:Screen("setting/equalizer")
    data object DownloadSettings:Screen("setting/download")
    data object StorageManagement:Screen("setting/storage")
    data object DownloadManage:Screen("download_manage")
    data object LocalMusic:Screen("local_music")
    data object LocalSongList:Screen("local_songs")
    data object History: Screen("history")
    data object Search: Screen("search")
    data object Test:Screen("test")
    data object About: Screen("about")
    data object Log: Screen("log")

    inline fun navigate(
        navController: MeiNavigator,
        builder: NavigationBuilder.() -> Unit = {}
    ) {
        navController.navigate(NavigationBuilder(route).apply(builder).build())
    }

    companion object {
        val MainScreens: List<Screen>
            get() = listOf(Home, Library, DownloadManage, History, Search, Setting)
    }
}

enum class Index(
    val route: String,
    @get:StringRes val labelRes: Int,
    val symbol: SfSymbol,
    val requiredFeature: ContentFeature? = null,
) {
    Home(Screen.Home.route, R.string.app_tab_home, SfSymbol.HouseFilled),
    Library(Screen.Library.route, R.string.app_tab_library, SfSymbol.MusicNoteSquareStackFilled),
    Downloads(Screen.DownloadManage.route, R.string.app_tab_library_downloads, SfSymbol.DownloadFilled, ContentFeature.Downloads),
    History(Screen.History.route, R.string.app_tab_library_history, SfSymbol.ClockFilled, ContentFeature.ListeningHistory),
    Settings(Screen.Setting.route, R.string.settings, SfSymbol.Settings),

    ;

    companion object {
        val DefaultOrder = listOf(Home, Library, Settings)
    }
}



class NavigationBuilder(
    route: String
) {
    private var finalRoute: String = route
    private val query: MutableMap<String, String> = hashMapOf()

    fun addPath(path: String) {
        finalRoute += "/$path"
    }

    fun addQuery(key: String, value: String) {
        query += key to value
    }

    fun build(): String = if (query.isEmpty()) {
        finalRoute
    } else {
        "$finalRoute${
            query.entries.joinToString(
                separator = "&",
                prefix = "?"
            ) { "${it.key}=${it.value}" }
        }"
    }
}
