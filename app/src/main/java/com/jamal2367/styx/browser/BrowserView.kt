/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser

import android.view.View
import androidx.annotation.StringRes
import com.jamal2367.styx.ssl.SslState

interface BrowserView {

    fun setTabView(view: View)

    fun updateUrl(url: String?, isLoading: Boolean)

    fun updateProgress(progress: Int)

    fun updateTabNumber(number: Int)

    fun updateSslState(sslState: SslState)

    fun closeBrowser()

    fun closeActivity()

    fun showBlockedLocalFileDialog(onPositiveClick: () -> Unit)

    fun showSnackbar(@StringRes resource: Int)

    fun setForwardButtonEnabled(enabled: Boolean)

    fun setBackButtonEnabled(enabled: Boolean)

    fun notifyTabViewRemoved(position: Int)

    fun notifyTabViewAdded()

    fun notifyTabViewChanged(position: Int)

    fun notifyTabViewInitialized()

    fun setAddressBarText(aText: String)
}
