/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.os.Bundle
import androidx.webkit.WebViewCompat
import com.jamal2367.styx.BuildConfig
import com.jamal2367.styx.R

class AboutSettingsFragment : AbstractSettingsFragment() {

    override fun providePreferencesXmlResource() = R.xml.preference_about

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        var webview = resources.getString(R.string.unknown)
        var devtools = resources.getString(R.string.unknown)
        val opendevui = resources.getString(R.string.open_devtools)

        WebViewCompat.getCurrentWebViewPackage(requireContext())?.let {
            webview = it.versionName
        }

        WebViewCompat.getCurrentWebViewPackage(requireContext())?.let {
            devtools = opendevui
        }

        clickablePreference(
            preference = SETTINGS_VERSION,
            summary = "${getString(R.string.pref_app_version_summary)} ${BuildConfig.VERSION_NAME} (${
                getString(R.string.app_version_name)
            })"
        )

        clickablePreference(
            preference = WEBVIEW_VERSION,
            summary = webview
        )

        clickablePreference(
            preference = SETTINGS_DEV_UI,
            summary = devtools
        )
    }

    companion object {
        private const val SETTINGS_DEV_UI = "pref_devtools"
        private const val SETTINGS_VERSION = "pref_version"
        private const val WEBVIEW_VERSION = "pref_webview"
    }
}