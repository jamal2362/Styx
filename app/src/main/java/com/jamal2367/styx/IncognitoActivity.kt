/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import com.jamal2367.styx.browser.activity.BrowserActivity
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import javax.inject.Inject

@AndroidEntryPoint
class IncognitoActivity @Inject constructor() : BrowserActivity() {

    override fun provideThemeOverride(): AppTheme = AppTheme.DARK

    public override fun updateCookiePreference(): Completable = Completable.fromAction {
        val cookieManager = CookieManager.getInstance()
        if (Capabilities.FULL_INCOGNITO.isSupported) {
            cookieManager.setAcceptCookie(userPreferences.cookiesEnabled)
        } else {
            cookieManager.setAcceptCookie(userPreferences.incognitoCookiesEnabled)
        }
    }

    override fun onNewIntent(intent: Intent) {
        handleNewIntent(intent)
        super.onNewIntent(intent)
    }

    override fun updateHistory(title: String?, url: String) = Unit

    override fun isIncognito() = true

    override fun closeActivity() = closePanels(::closeBrowser)

    companion object {
        /**
         * Creates the intent with which to launch the activity. Adds the reorder to front flag.
         */
        fun createIntent(context: Context, uri: Uri? = null) =
            Intent(context, IncognitoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                data = uri
            }
    }
}
