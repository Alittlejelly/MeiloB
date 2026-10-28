package com.ljyh.mei.ui.navigation

import androidx.annotation.StringRes
import com.ljyh.mei.R
import com.ljyh.mei.ui.glass.SfSymbol

enum class ContentFeature {
    Downloads,
    ListeningHistory,
}


enum class LibraryPage(
    @get:StringRes val titleRes: Int,
    val symbol: SfSymbol,
    val requiredFeature: ContentFeature? = null,
) {
    Favorites(R.string.library_page_fav_folders, SfSymbol.MusicNoteList),
    Collections(R.string.library_page_fav_collections, SfSymbol.MusicNoteSquareStackFilled),
}

