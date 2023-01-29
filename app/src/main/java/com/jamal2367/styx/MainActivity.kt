/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx

import android.content.Intent
import android.view.KeyEvent
import android.webkit.CookieManager
import com.jamal2367.styx.browser.activity.BrowserActivity
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity @Inject constructor() : BrowserActivity() {

    public override fun updateCookiePreference(): Completable = Completable.fromAction {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(userPreferences.cookiesEnabled)
    }

    override fun onNewIntent(intent: Intent) {
        handleNewIntent(intent)
        super.onNewIntent(intent)
    }

    override fun updateHistory(title: String?, url: String) = addItemToHistory(title, url)

    override fun isIncognito() = false

    override fun closeActivity() {
        performExitCleanUp()
        moveTaskToBack(true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_P ->
                    // Open a new private window
                    if (event.isShiftPressed) {
                        startActivity(IncognitoActivity.createIntent(this))
                        return true
                    }
            }
        }
        return super.dispatchKeyEvent(event)
    }

}
