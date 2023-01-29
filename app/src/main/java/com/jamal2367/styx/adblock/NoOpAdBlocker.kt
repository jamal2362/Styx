/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package com.jamal2367.styx.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dagger.Reusable
import javax.inject.Inject

/**
 * A no-op ad blocker implementation. Always returns false for [/isAd].
 */
@Reusable
class NoOpAdBlocker @Inject constructor() : AdBlocker {

    //override fun isAd(url: String) = false

    // unused element hiding currently disabled
    //override fun loadScript(uri: Uri): String? = null

    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? =
        null
}
