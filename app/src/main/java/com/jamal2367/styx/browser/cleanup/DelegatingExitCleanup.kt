/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.cleanup

import android.webkit.WebView
import com.jamal2367.styx.Capabilities
import com.jamal2367.styx.MainActivity
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.isSupported
import javax.inject.Inject

/**
 * Exit cleanup that determines which sort of cleanup to do at runtime. It determines which cleanup
 * to perform based on the API version and whether we are in incognito mode or normal mode.
 */
class DelegatingExitCleanup @Inject constructor(
    private val basicIncognitoExitCleanup: BasicIncognitoExitCleanup,
    private val enhancedIncognitoExitCleanup: EnhancedIncognitoExitCleanup,
    private val normalExitCleanup: NormalExitCleanup,
) : ExitCleanup {
    override fun cleanUp(webView: WebView?, context: BrowserActivity) {
        when {
            context is MainActivity -> normalExitCleanup.cleanUp(webView, context)
            Capabilities.FULL_INCOGNITO.isSupported -> enhancedIncognitoExitCleanup.cleanUp(webView,
                context)
            else -> basicIncognitoExitCleanup.cleanUp(webView, context)
        }
    }
}
