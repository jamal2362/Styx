/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.jamal2367.styx.Capabilities
import com.jamal2367.styx.R
import com.jamal2367.styx.database.history.HistoryRepository
import com.jamal2367.styx.di.DatabaseScheduler
import com.jamal2367.styx.di.MainScheduler
import com.jamal2367.styx.dialog.BrowserDialog
import com.jamal2367.styx.dialog.DialogItem
import com.jamal2367.styx.extensions.snackbar
import com.jamal2367.styx.isSupported
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.WebUtils
import com.jamal2367.styx.view.StyxView
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import io.reactivex.Scheduler
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PrivacySettingsFragment : AbstractSettingsFragment() {

    @Inject
    internal lateinit var historyRepository: HistoryRepository
    @Inject
    internal lateinit var userPreferences: UserPreferences
    @Inject
    @field:DatabaseScheduler
    internal lateinit var databaseScheduler: Scheduler
    @Inject
    @field:MainScheduler
    internal lateinit var mainScheduler: Scheduler

    override fun providePreferencesXmlResource() = R.xml.preference_privacy

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        clickablePreference(preference = SETTINGS_CLEARCACHE, onClick = this::clearCache)
        clickablePreference(preference = SETTINGS_CLEARHISTORY, onClick = this::clearHistoryDialog)
        clickablePreference(preference = SETTINGS_CLEARCOOKIES, onClick = this::clearCookiesDialog)
        clickablePreference(preference = SETTINGS_CLEARWEBSTORAGE, onClick = this::clearWebStorage)

        switchPreference(
            preference = SETTINGS_LOCATION,
            isChecked = userPreferences.locationEnabled,
            onCheckChange = { userPreferences.locationEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_THIRDPCOOKIES,
            isChecked = userPreferences.blockThirdPartyCookiesEnabled,
            isEnabled = Capabilities.THIRD_PARTY_COOKIE_BLOCKING.isSupported,
            onCheckChange = { userPreferences.blockThirdPartyCookiesEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_SAVEPASSWORD,
            isChecked = userPreferences.savePasswordsEnabled,
            onCheckChange = { userPreferences.savePasswordsEnabled = it }
            // From Android O auto-fill framework is used instead
        ).isVisible = Build.VERSION.SDK_INT < Build.VERSION_CODES.O

        switchPreference(
            preference = SETTINGS_CACHEEXIT,
            isChecked = userPreferences.clearCacheExit,
            onCheckChange = { userPreferences.clearCacheExit = it }
        )

        switchPreference(
            preference = SETTINGS_HISTORYEXIT,
            isChecked = userPreferences.clearHistoryExitEnabled,
            onCheckChange = { userPreferences.clearHistoryExitEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_COOKIEEXIT,
            isChecked = userPreferences.clearCookiesExitEnabled,
            onCheckChange = { userPreferences.clearCookiesExitEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_WEBSTORAGEEXIT,
            isChecked = userPreferences.clearWebStorageExitEnabled,
            onCheckChange = { userPreferences.clearWebStorageExitEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_DONOTTRACK,
            isChecked = userPreferences.doNotTrackEnabled,
            onCheckChange = { userPreferences.doNotTrackEnabled = it }
        )

        switchPreference(
            preference = getString(R.string.pref_key_webrtc),
            isChecked = userPreferences.webRtcEnabled && Capabilities.WEB_RTC.isSupported,
            isEnabled = Capabilities.WEB_RTC.isSupported,
            onCheckChange = { userPreferences.webRtcEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_IDENTIFYINGHEADERS,
            isChecked = userPreferences.removeIdentifyingHeadersEnabled,
            summary = "${StyxView.HEADER_REQUESTED_WITH}, ${StyxView.HEADER_WAP_PROFILE}",
            onCheckChange = { userPreferences.removeIdentifyingHeadersEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_INCOGNITO,
            isChecked = userPreferences.incognito,
            onCheckChange = { userPreferences.incognito = it }
        )

    }

    @SuppressLint("CheckResult")
    private fun clearHistoryDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            aContext = activity as AppCompatActivity,
            title = R.string.title_clear_history,
            message = R.string.dialog_history,
            positiveButton = DialogItem(title = R.string.action_yes) {
                clearHistory()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe {
                        (activity as AppCompatActivity).snackbar(R.string.message_clear_history)
                    }
            },
            negativeButton = DialogItem(title = R.string.action_no) {},
            onCancel = {}
        )
    }

    @SuppressLint("CheckResult")
    private fun clearCookiesDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            aContext = activity as AppCompatActivity,
            title = R.string.title_clear_cookies,
            message = R.string.dialog_cookies,
            positiveButton = DialogItem(title = R.string.action_yes) {
                clearCookies()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe {
                        (activity as AppCompatActivity).snackbar(R.string.message_cookies_cleared)
                    }
            },
            negativeButton = DialogItem(title = R.string.action_no) {},
            onCancel = {}
        )
    }

    private fun clearCache() {
        WebView(requireNotNull(activity)).apply {
            clearCache(true)
            destroy()
        }
        deleteCache(requireContext())
        (activity as AppCompatActivity).snackbar(R.string.message_cache_cleared)
    }

    private fun deleteCache(context: Context) {
        try {
            val dir = context.cacheDir
            deleteDir(dir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun deleteDir(dir: File?): Boolean {
        return if (dir != null && dir.isDirectory) {
            val children = dir.list()
            for (i in children.indices) {
                val success = deleteDir(File(dir, children[i]))
                if (!success) {
                    return false
                }
            }
            dir.delete()
        } else if (dir != null && dir.isFile) {
            dir.delete()
        } else {
            false
        }
    }

    private fun clearHistory(): Completable = Completable.fromAction {
        val activity = activity
        if (activity != null) {
            WebUtils.clearHistory(activity, historyRepository, databaseScheduler)
        } else {
            throw RuntimeException("Activity was null in clearHistory")
        }
    }

    private fun clearCookies(): Completable = Completable.fromAction {
        val activity = activity
        if (activity != null) {
            WebUtils.clearCookies()
        } else {
            throw RuntimeException("Activity was null in clearCookies")
        }
    }

    private fun clearWebStorage() {
        WebUtils.clearWebStorage()
        (activity as AppCompatActivity).snackbar(R.string.message_web_storage_cleared)
    }

    companion object {
        private const val SETTINGS_LOCATION = "location"
        private const val SETTINGS_THIRDPCOOKIES = "third_party"
        private const val SETTINGS_SAVEPASSWORD = "password"
        private const val SETTINGS_CACHEEXIT = "clear_cache_exit"
        private const val SETTINGS_HISTORYEXIT = "clear_history_exit"
        private const val SETTINGS_COOKIEEXIT = "clear_cookies_exit"
        private const val SETTINGS_CLEARCACHE = "clear_cache"
        private const val SETTINGS_CLEARHISTORY = "clear_history"
        private const val SETTINGS_CLEARCOOKIES = "clear_cookies"
        private const val SETTINGS_CLEARWEBSTORAGE = "clear_webstorage"
        private const val SETTINGS_WEBSTORAGEEXIT = "clear_webstorage_exit"
        private const val SETTINGS_DONOTTRACK = "do_not_track"
        private const val SETTINGS_IDENTIFYINGHEADERS = "remove_identifying_headers"
        private const val SETTINGS_INCOGNITO = "start_incognito"
    }

}
