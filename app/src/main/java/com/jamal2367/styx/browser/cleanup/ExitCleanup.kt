/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.cleanup

import android.webkit.WebView
import com.jamal2367.styx.browser.activity.BrowserActivity

/**
 * A command that runs as the browser instance is shutting down to clean up anything that needs to
 * be cleaned up. For instance, if the user has chosen to clear cache on exit or if incognito mode
 * is closing.
 */
interface ExitCleanup {

    /**
     * Clean up the instance of the browser with the provided [webView] and [context].
     */
    fun cleanUp(webView: WebView?, context: BrowserActivity)

}
