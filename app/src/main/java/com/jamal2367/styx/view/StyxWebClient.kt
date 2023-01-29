/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.view

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.MailTo
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.view.LayoutInflater
import android.webkit.*
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.BuildConfig
import com.jamal2367.styx.R
import com.jamal2367.styx.adblock.AbpBlockerManager
import com.jamal2367.styx.adblock.AdBlocker
import com.jamal2367.styx.adblock.NoOpAdBlocker
import com.jamal2367.styx.browser.JavaScriptChoice
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.constant.FILE
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.di.HiltEntryPoint
import com.jamal2367.styx.extensions.resizeAndShow
import com.jamal2367.styx.extensions.snackbar
import com.jamal2367.styx.js.InvertPage
import com.jamal2367.styx.js.SetMetaViewport
import com.jamal2367.styx.js.TextReflow
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.ssl.SslState
import com.jamal2367.styx.ssl.SslWarningPreferences
import com.jamal2367.styx.utils.*
import com.jamal2367.styx.view.StyxView.Companion.KFetchMetaThemeColorTries
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URISyntaxException
import kotlin.math.abs

class StyxWebClient(
    private val activity: AppCompatActivity,
    private val styxView: StyxView,
) : WebViewClient() {

    private val uiController: UIController = activity as UIController
    private val intentUtils = IntentUtils(activity)

    private val hiltEntryPoint =
        EntryPointAccessors.fromApplication(activity.applicationContext, HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val preferences: SharedPreferences = hiltEntryPoint.appsSharedPreferences()
    private val sslWarningPreferences: SslWarningPreferences = hiltEntryPoint.sslWarningPreferences
    val logger: Logger = hiltEntryPoint.logger
    private val textReflowJs: TextReflow = hiltEntryPoint.textReflowJs
    private val invertPageJs: InvertPage = hiltEntryPoint.invertPageJs
    private val setMetaViewport: SetMetaViewport = hiltEntryPoint.setMetaViewport
    private val abpBlockerManager: AbpBlockerManager = hiltEntryPoint.abpBlockerManager
    private val noopBlocker: NoOpAdBlocker = hiltEntryPoint.noopBlocker

    private var adBlock: AdBlocker

    private var urlWithSslError: String? = null

    @Volatile
    private var isRunning = false
    private var zoomScale = 0.0f

    private var currentUrl: String = ""

    //private var elementHide = userPreferences.elementHide

    var sslState: SslState = SslState.None
        private set(value) {
            field = value
            uiController.updateSslState(field)
        }

    init {
        adBlock = chooseAdBlocker()
    }

    fun updatePreferences() {
        adBlock = chooseAdBlocker()
    }

    private fun chooseAdBlocker(): AdBlocker = if (userPreferences.adBlockEnabled) {
        abpBlockerManager
    } else {
        noopBlocker
    }

    /**
     * Overrides [WebViewClient.shouldInterceptRequest].
     * Looks like we need to intercept our custom URLs here to implement support for styx and about scheme.
     * comment Helium314: adBLock.shouldBock always never blocks if url.isSpecialUrl() or url.isAppScheme(), could be moved here
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        // returns some dummy response if blocked, null if not blocked
        return adBlock.shouldBlock(request, currentUrl)
    }

    override fun onLoadResource(view: WebView, url: String?) {
        super.onLoadResource(view, url)
        if (styxView.desktopMode) {
            // That's needed for desktop mode support
            // See: https://stackoverflow.com/a/60621350/3969362
            // See: https://stackoverflow.com/a/39642318/3969362
            // Note how we compute our initial scale to be zoomed out and fit the page
            // Pick the proper settings desktop width according to current orientation
            (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_PORTRAIT).let { portrait ->
                view.evaluateJavascript(setMetaViewport.provideJs().replaceFirst("\$width\$",
                    (if (portrait) userPreferences.desktopWidthInPortrait else userPreferences.desktopWidthInLandscape).toString()),
                    null)
            }
        }
    }

    /**
     *
     */
    private fun updateUrlIfNeeded(url: String, isLoading: Boolean) {
        // Update URL unless we are dealing with our special internal URL
        (url.isSpecialUrl()).let { dontDoUpdate ->
            uiController.updateUrl(if (dontDoUpdate) styxView.url else url, isLoading)
        }
    }

    /**
     * Overrides [WebViewClient.onPageFinished]
     */
    @SuppressLint("CheckResult", "SetJavaScriptEnabled")
    override fun onPageFinished(view: WebView, url: String) {
        if (view.isShown) {
            updateUrlIfNeeded(url, false)
            uiController.setBackButtonEnabled(view.canGoBack())
            uiController.setForwardButtonEnabled(view.canGoForward())
            view.postInvalidate()
        }

        if (view.title == null || view.title!!.isEmpty()) {
            styxView.titleInfo.setTitle(activity.getString(R.string.untitled))
        } else {
            styxView.titleInfo.setTitle(view.title)
        }

        if (styxView.invertPage) {
            view.evaluateJavascript(invertPageJs.provideJs(), null)
        }

        /*
        if (elementHide) {
            adBlock.loadScript(Uri.parse(currentUrl))?.let {
                view.evaluateJavascript(it, null)
            }
            // takes around half a second, but not sure what that tells me
        }*/

        if (url.contains(BuildConfig.APPLICATION_ID + "/files/homepage.html")) {
            view.evaluateJavascript("javascript:(function() {" + "link1var = '" + userPreferences.link1 + "';" + "})();",
                null)
            view.evaluateJavascript("javascript:(function() {" + "link2var = '" + userPreferences.link2 + "';" + "})();",
                null)
            view.evaluateJavascript("javascript:(function() {" + "link3var = '" + userPreferences.link3 + "';" + "})();",
                null)
            view.evaluateJavascript("javascript:(function() {" + "link4var = '" + userPreferences.link4 + "';" + "})();",
                null)
        }

        if (userPreferences.forceZoom) {
            view.loadUrl(
                "javascript:(function() { document.querySelector('meta[name=\"viewport\"]').setAttribute(\"content\",\"width=device-width\"); })();"
            )
        }

        uiController.tabChanged(styxView)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        currentUrl = url

        // Only set the SSL state if there isn't an error for the current URL.
        if (urlWithSslError != url) {
            sslState = if (URLUtil.isHttpsUrl(url)) {
                SslState.Valid
            } else {
                SslState.None
            }
        }
        styxView.titleInfo.setFavicon(null)
        if (styxView.isShown) {
            updateUrlIfNeeded(url, true)
            uiController.showActionBar()
        }

        // Try to fetch meta theme color a few times
        styxView.fetchMetaThemeColorTries = KFetchMetaThemeColorTries

        if (url.contains(BuildConfig.APPLICATION_ID + "/files/homepage.html")) {
            view.evaluateJavascript("""(function() {
                return localStorage.getItem("shouldUpdate");})()""".trimMargin()) { it ->
                if (it.substring(1, it.length - 1) == "yes") {
                    view.evaluateJavascript("""(function() {return localStorage.getItem("link1");})()""".trimMargin()) {
                        userPreferences.link1 = it.substring(1, it.length - 1)
                        view.evaluateJavascript("""(function() {localStorage.setItem("shouldUpdate", "no");})()""") {}
                    }
                    view.evaluateJavascript("""(function() {return localStorage.getItem("link2");})()""".trimMargin()) {
                        userPreferences.link2 = it.substring(1, it.length - 1)
                        view.evaluateJavascript("""(function() {localStorage.setItem("shouldUpdate", "no");})()""") {}
                    }
                    view.evaluateJavascript("""(function() {return localStorage.getItem("link3");})()""".trimMargin()) {
                        userPreferences.link3 = it.substring(1, it.length - 1)
                        view.evaluateJavascript("""(function() {localStorage.setItem("shouldUpdate", "no");})()""") {}
                    }
                    view.evaluateJavascript("""(function() {return localStorage.getItem("link4");})()""".trimMargin()) {
                        userPreferences.link4 = it.substring(1, it.length - 1)
                        view.evaluateJavascript("""(function() {localStorage.setItem("shouldUpdate", "no");})()""") {}
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        uiController.newTabButtonClicked()
                    }, 100)
                }
            }
        }

        if (userPreferences.javaScriptChoice === JavaScriptChoice.BLACKLIST) {
            if (userPreferences.javaScriptBlocked !== "" && userPreferences.javaScriptBlocked !== " ") {
                val arrayOfURLs = userPreferences.javaScriptBlocked
                var strgs: Array<String> =
                    arrayOfURLs.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                if (arrayOfURLs.contains(", ")) {
                    strgs = arrayOfURLs.split(", ".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                }
                if (!stringContainsItemFromList(url, strgs)) {
                    if (url.contains("file:///android_asset") or url.contains("about:blank")) {
                        return
                    } else {
                        view.settings.javaScriptEnabled = false
                    }
                } else {
                    return
                }
            }
        } else if (userPreferences.javaScriptChoice === JavaScriptChoice.WHITELIST) run {
            if (userPreferences.javaScriptBlocked !== "" && userPreferences.javaScriptBlocked !== " ") {
                val arrayOfURLs = userPreferences.javaScriptBlocked
                var strgs: Array<String> =
                    arrayOfURLs.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                if (arrayOfURLs.contains(", ")) {
                    strgs = arrayOfURLs.split(", ".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                }
                if (stringContainsItemFromList(url, strgs)) {
                    if (url.contains("file:///android_asset") or url.contains("about:blank")) {
                        return
                    } else {
                        view.settings.javaScriptEnabled = false
                    }
                } else {
                    return
                }
            }
        }

        uiController.tabChanged(styxView)
    }

    private fun stringContainsItemFromList(inputStr: String, items: Array<String>): Boolean {
        for (i in items.indices) {
            if (inputStr.contains(items[i])) {
                return true
            }
        }
        return false
    }

    /**
     *
     */
    @SuppressLint("InflateParams")
    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) {
        MaterialAlertDialogBuilder(activity).apply {
            val dialogView =
                LayoutInflater.from(activity).inflate(R.layout.dialog_auth_request, null)

            val realmLabel = dialogView.findViewById<TextView>(R.id.auth_request_realm_textview)
            val name = dialogView.findViewById<EditText>(R.id.auth_request_username_edittext)
            val password = dialogView.findViewById<EditText>(R.id.auth_request_password_edittext)

            realmLabel.text = activity.getString(R.string.label_realm, realm)

            setView(dialogView)
            setTitle(R.string.title_sign_in)
            setCancelable(true)
            setPositiveButton(R.string.title_sign_in) { _, _ ->
                val user = name.text.toString()
                val pass = password.text.toString()
                handler.proceed(user.trim(), pass.trim())
                logger.log(TAG, "Attempting HTTP Authentication")
            }
            setNegativeButton(R.string.action_cancel) { _, _ ->
                handler.cancel()
            }
        }.resizeAndShow()
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("ResourceType")
    override fun onReceivedError(
        webview: WebView,
        errorCode: Int,
        error: String,
        failingUrl: String,
    ) {
        if (errorCode != -1) {
            val output = ByteArrayOutputStream()
            val bitmap =
                ResourcesCompat.getDrawable(activity.resources, R.raw.warning, activity.theme)
                    ?.toBitmap()
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, output)
            val imageBytes: ByteArray = output.toByteArray()
            val imageString =
                "data:image/png;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val tip = activity.getString(R.string.error_tip)
            val tip2 = activity.getString(R.string.error_tip2)
            val background = htmlColor(ThemeUtils.getSurfaceColor(BrowserApp.currentContext()))
            val text = htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),
                R.attr.colorControlNormal))
            val script = """(function() {
        document.getElementsByTagName('style')[0].innerHTML += "body { pointer-events: none; user-select: none; margin: 30px; padding-top: 40px; text-align: center; background-color: ${background}; color: ${text};} img{width: 128px; height: 128px; margin-bottom: 20px;}"
        document.getElementsByTagName('p')[0].innerHTML = '${tip}';
        document.getElementsByTagName('p')[0].innerHTML += '<br><br>${tip2}';
        var img = document.getElementsByTagName('img')[0]
        img.src = "$imageString"
        img.width = ${bitmap?.width}
        img.height = ${bitmap?.height}
        })()"""
            webview.stopLoading()
            webview.evaluateJavascript(script) {}
        }
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        if (view.isShown && styxView.userPreferences.textReflowEnabled) {
            if (isRunning)
                return
            val changeInPercent = abs(100 - 100 / zoomScale * newScale)
            if (changeInPercent > 2.5f && !isRunning) {
                isRunning = view.postDelayed({
                    zoomScale = newScale
                    view.evaluateJavascript(textReflowJs.provideJs()) { isRunning = false }
                }, 100)
            }

        }
    }

    @SuppressLint("InflateParams", "WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(webView: WebView, handler: SslErrorHandler, error: SslError) {
        val urlMatcher = webView.url?.replace(Regex("^https?://"), "")
        if (!urlMatcher?.let { error.url.contains(it) }!!) {
            handler.proceed()
            webView.url?.let {
                sslWarningPreferences.rememberBehaviorForDomain(it,
                    SslWarningPreferences.Behavior.PROCEED)
            }
        }

        urlWithSslError = webView.url
        sslState = SslState.Invalid(error)

        when (sslWarningPreferences.recallBehaviorForDomain(webView.url)) {
            SslWarningPreferences.Behavior.PROCEED -> return handler.proceed()
            SslWarningPreferences.Behavior.CANCEL -> return handler.cancel()
            null -> Unit
        }

        val errorCodeMessageCodes = getAllSslErrorMessageCodes(error)

        val stringBuilder = StringBuilder()
        for (messageCode in errorCodeMessageCodes) {
            stringBuilder.append(" - ").append(activity.getString(messageCode)).append('\n')
        }
        val alertMessage =
            activity.getString(R.string.message_insecure_connection, stringBuilder.toString())

        val ba = activity as BrowserActivity

        if (!userPreferences.ssl) {
            handler.proceed()
            ba.snackbar(errorCodeMessageCodes[0])
            return
        }

        MaterialAlertDialogBuilder(activity).apply {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_ssl_warning, null)
            val dontAskAgain = view.findViewById<CheckBox>(R.id.checkBoxDontAskAgain)
            setTitle(activity.getString(R.string.title_warning))
            setMessage(alertMessage)
            setCancelable(true)
            setView(view)
            setOnCancelListener { handler.cancel() }
            setPositiveButton(activity.getString(R.string.action_yes)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    sslWarningPreferences.rememberBehaviorForDomain(webView.url!!,
                        SslWarningPreferences.Behavior.PROCEED)
                }
                handler.proceed()
            }
            setNegativeButton(activity.getString(R.string.action_no)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    sslWarningPreferences.rememberBehaviorForDomain(webView.url!!,
                        SslWarningPreferences.Behavior.CANCEL)
                }
                handler.cancel()
            }
        }.resizeAndShow()
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(activity.getString(R.string.title_form_resubmission))
            setMessage(activity.getString(R.string.message_form_resubmission))
            setCancelable(true)
            setPositiveButton(activity.getString(R.string.action_yes)) { _, _ ->
                resend.sendToTarget()
            }
            setNegativeButton(activity.getString(R.string.action_no)) { _, _ ->
                dontResend.sendToTarget()
            }
        }.resizeAndShow()
    }

    // We use this to prevent opening such dialogs multiple times
    private var exAppLaunchDialog: AlertDialog? = null

    /**
     * Overrides [WebViewClient.shouldOverrideUrlLoading].
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val headers = styxView.requestHeaders

        if (styxView.isIncognito) {
            // If we are in incognito, immediately load, we don't want the url to leave the app
            return continueLoadingUrl(view, url, headers)
        }
        if (URLUtil.isAboutUrl(url)) {
            // If this is an about page, immediately load, we don't need to leave the app
            return continueLoadingUrl(view, url, headers)
        }

        if (isMailOrTelOrIntent(url, view)) {
            // If it was a mailto: link, or an intent, or could be launched elsewhere, do that
            return true
        }

        val intent = intentUtils.intentForUrl(view, url)
        intent?.let {
            // Check if that external app is already known
            val prefKey = activity.getString(R.string.settings_app_prefix) + Uri.parse(url).host
            if (preferences.contains(prefKey)) {
                return if (preferences.getBoolean(prefKey, false)) {
                    // Trusted app, just launch it on the stop and abort loading
                    intentUtils.startActivityForIntent(intent)
                    true
                } else {
                    // User does not want use to use this app
                    false
                }
            }

            // We first encounter that app ask user if we should use it?
            // We will keep loading even if an external app is available the first time we encounter it.
            (activity as BrowserActivity).mainHandler.postDelayed({
                if (exAppLaunchDialog == null) {
                    exAppLaunchDialog =
                        MaterialAlertDialogBuilder(activity).setTitle(R.string.dialog_title_third_party_app)
                            .setMessage(R.string.dialog_message_third_party_app)
                            .setPositiveButton(activity.getText(R.string.yes)) { dialog, _ ->
                                // Handle Ok
                                intentUtils.startActivityForIntent(intent)
                                dialog.dismiss()
                                exAppLaunchDialog = null
                                // Remember user choice
                                preferences.edit().putBoolean(prefKey, true).apply()
                            }
                            .setNegativeButton(activity.getText(R.string.no)) { dialog, _ ->
                                // Handle Cancel
                                dialog.dismiss()
                                exAppLaunchDialog = null
                                // Remember user choice
                                preferences.edit().putBoolean(prefKey, false).apply()
                            }
                            .create()
                    exAppLaunchDialog?.show()
                }
            }, 1000)
        }

        // If none of the special conditions was met, continue with loading the url
        return continueLoadingUrl(view, url, headers)
    }

    private fun continueLoadingUrl(
        webView: WebView,
        url: String,
        headers: Map<String, String>,
    ): Boolean {
        if (!URLUtil.isNetworkUrl(url)
            && !URLUtil.isFileUrl(url)
            && !URLUtil.isAboutUrl(url)
            && !URLUtil.isDataUrl(url)
            && !URLUtil.isJavaScriptUrl(url)
        ) {
            webView.stopLoading()
            return true
        }
        return when {
            headers.isEmpty() -> false
            else -> {
                webView.loadUrl(url, headers)
                true
            }
        }
    }

    private fun isMailOrTelOrIntent(url: String, view: WebView): Boolean {
        if (url.startsWith("mailto:")) {
            val mailTo = MailTo.parse(url)
            val i = Utils.newEmailIntent(mailTo.to, mailTo.subject, mailTo.body, mailTo.cc)
            activity.startActivity(i)
            view.reload()
            return true
        } else if (url.startsWith("tel:")) {
            val i = Intent(Intent.ACTION_DIAL)
            i.data = Uri.parse(url)
            activity.startActivity(i)
            view.reload()
            return true
        } else if (url.startsWith("intent://")) {
            val intent = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (ignored: URISyntaxException) {
                null
            }

            if (intent != null) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null
                try {
                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    logger.log(TAG, "ActivityNotFoundException")
                }

                return true
            }
        } else if (URLUtil.isFileUrl(url) && !url.isSpecialUrl()) {
            val file = File(url.replace(FILE, ""))

            if (file.exists()) {
                val newMimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(Utils.guessFileExtension(file.toString()))

                val intent = Intent(Intent.ACTION_VIEW)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val contentUri = FileProvider.getUriForFile(activity,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    file)
                intent.setDataAndType(contentUri, newMimeType)

                try {
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    println("StyxWebClient: cannot open downloaded file")
                }

            } else {
                activity.snackbar(R.string.message_open_download_fail)
            }
            return true
        }
        return false
    }

    private fun getAllSslErrorMessageCodes(error: SslError): List<Int> {
        val errorCodeMessageCodes = ArrayList<Int>(1)

        if (error.hasError(SslError.SSL_DATE_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_date_invalid)
        }
        if (error.hasError(SslError.SSL_EXPIRED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_expired)
        }
        if (error.hasError(SslError.SSL_IDMISMATCH)) {
            errorCodeMessageCodes.add(R.string.message_certificate_domain_mismatch)
        }
        if (error.hasError(SslError.SSL_NOTYETVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_not_yet_valid)
        }
        if (error.hasError(SslError.SSL_UNTRUSTED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_untrusted)
        }
        if (error.hasError(SslError.SSL_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_invalid)
        }

        return errorCodeMessageCodes
    }

    companion object {

        private const val TAG = "StyxWebClient"

    }
}
