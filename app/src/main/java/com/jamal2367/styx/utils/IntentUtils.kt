/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_RESOLVED_FILTER
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.jamal2367.styx.R
import com.jamal2367.styx.constant.INTENT_ORIGIN
import java.net.URISyntaxException
import java.util.regex.Matcher
import java.util.regex.Pattern


class IntentUtils(private val mActivity: Activity) {
    /**
     *
     * @param tab
     * @param url
     * @return
     */
    fun intentForUrl(tab: WebView?, url: String): Intent? {
        var intent: Intent
        intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (ex: URISyntaxException) {
            Log.w("Browser", "Bad URI " + url + ": " + ex.message)
            return null
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mActivity.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) == null
            } else {
                mActivity.packageManager.resolveActivity(intent, 0) == null
            }) {
            // SL: Not sure what that special case is for
            val packagename = intent.getPackage()
            return if (packagename != null) {
                intent = Intent(
                    Intent.ACTION_VIEW, Uri.parse(
                        "market://search?q=pname:"
                                + packagename
                    )
                )
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                //mActivity.startActivity(intent);
                intent
            } else {
                null
            }
        }
        if (tab != null) {
            intent.putExtra(INTENT_ORIGIN, tab.hashCode())
        }
        val m: Matcher = ACCEPTED_URI_SCHEMA.matcher(url)
        return if (m.matches() && !isSpecializedHandlerAvailable(intent)) {
            null
        } else intent
    }

    fun startActivityForIntent(aIntent: Intent): Boolean {
        try {
            if (mActivity.startActivityIfNeeded(aIntent, -1)) {
                return true
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return false
    }

    /**
     * Search for intent handlers that are specific to this URL aka, specialized
     * apps like google maps or youtube
     */
    private fun isSpecializedHandlerAvailable(intent: Intent): Boolean {
        val pm = mActivity.packageManager
        val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(GET_RESOLVED_FILTER.toLong()))
        } else {
            pm.queryIntentActivities(intent, GET_RESOLVED_FILTER)
        }
        if (handlers.isEmpty()) {
            return false
        }
        for (resolveInfo in handlers) {
            val filter = resolveInfo.filter
                ?: // No intent filter matches this intent?
                // Error on the side of staying in the browser, ignore
                continue
            // NOTICE: Use of && instead of || will cause the browser
            // to launch a new intent for every URL, using OR only
            // launches a new one if there is a non-browser app that
            // can handle it.
            // Previously we checked the number of data paths, but it is unnecessary
            // filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0
            if (filter.countDataAuthorities() == 0) {
                // Generic handler, skip
                continue
            }
            return true
        }
        return false
    }

    /**
     * Shares a URL to the system.
     *
     * @param url   the URL to share. If the URL is null
     * or a special URL, no sharing will occur.
     * @param title the title of the URL to share. This
     * is optional.
     */
    fun shareUrl(url: String?, title: String?) {
        if (url != null && !url.isSpecialUrl()) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            if (title != null) {
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, url)
            mActivity.startActivity(
                Intent.createChooser(
                    shareIntent,
                    mActivity.getString(R.string.dialog_title_share)
                )
            )
        }
    }

    companion object {
        private val ACCEPTED_URI_SCHEMA = Pattern.compile(
            "(?i)((?:http|https|file)://|(?:inline|data|about|javascript):|(:.*:.*@))(.*)"
        )
    }
}