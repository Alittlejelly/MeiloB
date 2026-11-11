package com.ljyh.mei.ui.screen.account

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import com.ljyh.mei.data.network.clearNeteaseAccountSession
import com.ljyh.mei.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Clears the NetEase webview cookies and the stored account session. */
suspend fun logoutNetease(context: android.content.Context) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
    context.dataStore.edit { it.clearNeteaseAccountSession() }
}
