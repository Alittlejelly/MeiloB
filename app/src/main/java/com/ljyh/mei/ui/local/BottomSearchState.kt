package com.ljyh.mei.ui.local

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Command bus between the shared bottom search toolbar (composed by MainActivity above the
 * navigation entries) and the search destination, which owns the real search state. The
 * destination registers its submit handler while it is on screen; the toolbar invokes it.
 */
class BottomSearchState {
    var submit: ((String) -> Unit)? = null
    var setText: ((String) -> Unit)? = null
}

val LocalBottomSearchState = staticCompositionLocalOf { BottomSearchState() }
