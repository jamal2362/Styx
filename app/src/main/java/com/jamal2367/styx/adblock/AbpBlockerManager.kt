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

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.collection.LruCache
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.R
import com.jamal2367.styx.adblock.AbpBlocker.Companion.addHeader
import com.jamal2367.styx.adblock.AbpBlocker.Companion.removeHeader
import com.jamal2367.styx.constant.FILE
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.okhttp3.internal.publicsuffix.PublicSuffix
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.ThemeUtils
import com.jamal2367.styx.utils.htmlColor
import com.jamal2367.styx.utils.isAppScheme
import com.jamal2367.styx.utils.isSpecialUrl
import jp.hazuki.yuzubrowser.adblock.EmptyInputStream
import jp.hazuki.yuzubrowser.adblock.core.AbpLoader
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.getContentType
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.internal.toHeaderList
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(DelicateCoroutinesApi::class)
@Singleton
class AbpBlockerManager @Inject constructor(
    private val application: Application,
    abpListUpdater: AbpListUpdater,
    abpUserRules: AbpUserRules,
    val userPreferences: UserPreferences,
    private val logger: Logger,
) : AdBlocker {

    // use a map of filterContainers instead of several separate containers
    private val filterContainers = blockerPrefixes.associateWith { FilterContainer() }

    // store whether lists are loaded (and delay any request until loading is done)
    private var listsLoaded = false

    private val okHttpClient by lazy {
        OkHttpClient()
            .newBuilder()
            .cookieJar(WebkitCookieManager(CookieManager.getInstance()))
            .build()
    } // we only need it for some filters, so don't create if not necessary

    private val thirdPartyCache = ThirdPartyLruCache(100)

    private val blocker = AbpBlocker(abpUserRules, filterContainers)

    private val cacheDir by lazy { FILE + application.cacheDir.absolutePath }
    /*    // element hiding
        //  doesn't work, but maybe it's crucial to inject the js at the right point
        //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
        private var elementBlocker: CosmeticFiltering? = null
        var elementHide = userPreferences.elementHide
    */

    init {
        // hilt always loads blocker, even if not used
        //  thus we load the lists only if blocker is actually enabled
        if (userPreferences.adBlockEnabled)
            GlobalScope.launch(Dispatchers.Default) {
                loadLists()

                // update all enabled entities/blocklists
                // may take a while depending on how many lists need update, and on internet connection
                if (abpListUpdater.updateAll(false)) { // returns true if anything was updated
                    removeJointLists()
                    loadLists() // update again if files have changed
                }
            }
    }

    fun removeJointLists() {
        val filterDir = application.applicationContext.getFilterDir()
        blockerPrefixes.forEach { File(filterDir, it).delete() }
    }

    // load lists
    //  and create files containing filters from all enabled entities (without duplicates)
    fun loadLists() {
        val filterDir = application.applicationContext.getFilterDir()

        // call loadFile for all prefixes and be done if all return true
        // asSequence() should not load all lists and then check, but fail faster if there is a problem
        if (blockerPrefixes.asSequence().map { loadFileToContainer(File(filterDir, it), it) }
                .all { it }) {
            listsLoaded = true
            return
        }
        // loading failed or joint lists don't exist: load the normal way and create joint lists

        val entities = AbpDao(application.applicationContext).getAll()
        val abpLoader = AbpLoader(filterDir, entities)

        val filters = blockerPrefixes.associateWith { prefix ->
            abpLoader.loadAll(prefix).toSet()
                .sanitize(abpLoader.loadAll(ABP_PREFIX_BADFILTER + prefix).toSet())
        }

        blockerPrefixes.forEach { prefix ->
            filterContainers[prefix]!!.clear() // clear container, or disabled filter lists will still be active
            filterContainers[prefix]!!.also { filters[prefix]!!.forEach(it::addWithTag) }
        }
        listsLoaded = true

        // create joint files
        // tags will be created again, this is unnecessary, but fast enough to not care about it very much
        blockerPrefixes.forEach { prefix ->
            writeFile(prefix, filters[prefix]!!)
        }

        /*if (elementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }*/
    }

    private fun loadFileToContainer(file: File, prefix: String): Boolean {
        if (file.exists()) {
            try {
                file.inputStream().buffered().use { ins ->
                    val reader = FilterReader(ins)
                    if (!reader.checkHeader())
                        return false
                    if (isModify(prefix))
                        reader.readAllModifyFilters()
                            .forEach(filterContainers[prefix]!!::addWithTag)
                    else
                        reader.readAll().forEach(filterContainers[prefix]!!::addWithTag)
                    // check 2nd "header" at end of the file, to avoid accepting partially written file
                    return reader.checkHeader()
                }
            } catch (e: IOException) { // nothing to do, returning false anyway
            }
        }
        return false
    }

    private fun writeFile(prefix: String, filters: Collection<Pair<String, UnifiedFilter>>?) {
        if (filters == null) return // better throw error, should not happen
        val file = File(application.applicationContext.getFilterDir(), prefix)
        val writer = FilterWriter()
        file.outputStream().buffered().use {
            if (isModify(prefix))
            // use !! to get error if filter.modify is null
                writer.writeModifyFiltersWithTag(it, filters.toList())
            else
                writer.writeWithTag(it, filters.toList())
            it.close()
        }
    }

    // returns null if not blocked, else some WebResourceResponse
    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // always allow special URLs, app scheme and cache dir (used for favicons)
        request.url.toString().let {
            if (it.isSpecialUrl() || it.isAppScheme() || it.startsWith(cacheDir))
                return null
        }

        // create contentRequest
        // pageUrl can be "" (when opening something in a new tab, or manually entering a URL)
        //  in this case everything gets blocked because of the pattern "|https://"
        //  this is blocked for some specific page domains
        //   and if pageUrl.host == null, domain check return true (in UnifiedFilter.kt)
        //   same for is3rdParty
        // if switching pages (via link or pressing back), pageUrl is still the old url, messing up 3rd party checks
        // -> fix both by setting pageUrl to requestUrl if request.isForMainFrame
        //  is there any way a request for main frame can be a 3rd party request? then a different fix would be required
        val contentRequest = request.getContentRequest(
            if (request.isForMainFrame || pageUrl.isBlank()) request.url else pageUrl.toUri()
        )

        // wait until blocklists are loaded
        //  web request stuff does not run on main thread, so thread.sleep should be ok
        while (!listsLoaded) {
            Thread.sleep(50)
        }

        // blocker shouldBlock
        val response = blocker.shouldBlock(contentRequest) ?: return null

        when (response) {
            is BlockResponse -> {
                return if (request.isForMainFrame)
                    createMainFrameDummy(request.url, response.blockList, response.pattern)
                else when (contentRequest.type) {
                    ContentRequest.TYPE_OTHER -> BlockResourceResponse(RES_EMPTY)
                    ContentRequest.TYPE_IMAGE -> BlockResourceResponse(RES_1X1)
                    ContentRequest.TYPE_SUB_DOCUMENT -> BlockResourceResponse(RES_NOOP_HTML)
                    ContentRequest.TYPE_SCRIPT -> BlockResourceResponse(RES_NOOP_JS)
                    ContentRequest.TYPE_MEDIA -> BlockResourceResponse(RES_NOOP_MP3)
                    else -> BlockResourceResponse(RES_EMPTY)
                }.toWebResourceResponse()
            }
            is BlockResourceResponse -> return response.toWebResourceResponse()
            is ModifyResponse -> {
                if (
                // okhttp accepts only ws, wss, http, https, can't build a request otherwise
                    request.url.scheme !in okHttpAcceptedSchemes
                    // modify filter implementation still has some problems, allow users to disable
                    || userPreferences.modifyFilters == 0
                    // for some reason, requests done via okhttp on main frame may cause problems
                    //  occurs for example on heise.de
                    //  allow users to disable on main frame only
                    || (request.isForMainFrame && userPreferences.modifyFilters == 1)
                    // webresourcerequest does not contain request body, but these request types must or can have a body
                    || request.method == "POST" || request.method == "PUT" || request.method == "PATCH" || request.method == "DELETE"
                // TODO: update in a way that a body can be provided, try https://github.com/KonstantinSchubert/request_data_webviewclient
                )
                    return null
                try {
                    val newRequest = Request.Builder()
                        .url(response.url)
                        .method(response.requestMethod,
                            null) // use same method, no body to copy from WebResourceRequest
                        .headers(response.requestHeaders.toHeaders())
                        .build()
                    val webResponse = okHttpClient.newCall(newRequest).execute()
                    if (response.addResponseHeaders == null && response.removeResponseHeaders == null)
                        return webResponse.toWebResourceResponse(null)
                    val headers = webResponse.headers.toMap()
                    response.addResponseHeaders?.forEach { headers.addHeader(it) }
                    response.removeResponseHeaders?.forEach { headers.removeHeader(it) }
                    return webResponse.toWebResourceResponse(headers)
                } catch (e: Exception) {
                    // connection problems
                    // problems when building okhttp Request
                    // problems when creating WebResourceResponse
                    logger.log(TAG, "error while doing modified request for ${response.url}: ", e)
                    return null // allow webview to try again, even though this should be modified...
                }
            }
            else -> logger.log(TAG, "unknown blocker response type: ${response.javaClass}")
        }
        return null
    }

    // moved from jp.hazuki.yuzubrowser.adblock/AdBlock.kt to allow modified 3rd party detection
    private fun WebResourceRequest.getContentRequest(pageUri: Uri): ContentRequest {
        val pageHost = pageUri.host?.lowercase()
        return ContentRequest(url,
            pageHost,
            getContentType(pageUri),
            is3rdParty(url, pageHost),
            requestHeaders,
            method)
    }

    // initially based on jp.hazuki.yuzubrowser.adblock/AdBlock.kt
    private fun is3rdParty(url: Uri, pageHost: String?): Int {
        val hostName = url.host?.lowercase() ?: return THIRD_PARTY
        if (pageHost == null) return THIRD_PARTY

        if (hostName == pageHost) return STRICT_FIRST_PARTY

        return if (thirdPartyCache["$hostName/$pageHost"]!!) // thirdPartyCache.Create can't return null!
            THIRD_PARTY
        else
            FIRST_PARTY
    }

    // builder part from yuzu: jp.hazuki.yuzubrowser.adblock/AdBlockController.kt
    private fun createMainFrameDummy(
        uri: Uri,
        blockList: String,
        pattern: String,
    ): WebResourceResponse {
        val reasonString = when (blockList) {
            USER_BLOCKED -> application.resources.getString(R.string.page_blocked_list_user,
                pattern)
            ABP_PREFIX_IMPORTANT -> application.resources.getString(R.string.page_blocked_list_malware,
                pattern)
            ABP_PREFIX_DENY -> application.resources.getString(R.string.page_blocked_list_ad,
                pattern) // should only be ABP_PREFIX_DENY
            else -> {
                logger.log(TAG, "unexpected blocklist when creating main frame dummy: $blockList")
                application.resources.getString(R.string.page_blocked_list_ad, pattern)
            }
        }

        val requestBlocked = application.getString(R.string.request_blocked)
        val pageBlocked = application.getString(R.string.page_blocked)
        val blockedReason = application.getString(R.string.page_blocked_reason)
        val background = htmlColor(ThemeUtils.getSurfaceColor(BrowserApp.currentContext()))
        val background1 =
            htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(), R.attr.colorTertiary))
        val text =
            htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(), R.attr.colorControlNormal))
        val builder = StringBuilder("<meta charset=utf-8>" +
                "<meta content=\"width=device-width,initial-scale=1,minimum-scale=1\"name=viewport>" +
                "<style>body{padding:5px 15px;background:$background}body,p{text-align:center;color:$text}p{margin:20px 0 0}" +
                "pre{margin:5px 0;padding:5px;border-radius:25px;background:$background1}}</style><title>")
            .append(requestBlocked)
            .append("</title><p>")
            .append(pageBlocked)
            .append("<pre>")
            .append(uri)
            .append("</pre><p>")
            .append(blockedReason)
            .append("<pre>")
            .append(reasonString)
            .append("</pre>")

        return getNoCacheResponse("text/html", builder)
    }

    private fun Response.toWebResourceResponse(modifiedHeaders: Map<String, String>?): WebResourceResponse {
        // content-type usually has format "text/html, charset=utf-8" or "text/html"
        val contentType = (header("content-type") ?: "text/plain").split(';')
        // WebResourceResponse doesn't accept codes in this range, but okhttp response sometimes to have them
        val responseCode = if (code < 300 || code > 399) code else 200
        return WebResourceResponse(
            contentType.first(),
            if (contentType.size > 1 && contentType[1].lowercase().startsWith("charset="))
                contentType[1].substringAfter('=')
            else null,
            responseCode,
            message.let { it.ifEmpty { "OK" } }, // must not be empty, but sometimes okhttp response has empty message
            modifiedHeaders ?: headers.toMap(),
            body?.byteStream() ?: EmptyInputStream()
        )
    }

    private fun Headers.toMap(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        toHeaderList().forEach {
            map[it.name.utf8()] = it.value.utf8()
        }
        return map
    }

    // TODO: load from file every time? is there some caching in the background? cache files using by lazy?
    private fun BlockResourceResponse.toWebResourceResponse(): WebResourceResponse {
        val mimeType = getMimeType(filename)
        return WebResourceResponse(
            mimeType,
            if (mimeType.startsWith("application") || mimeType.startsWith("text"))
                "utf-8"
            else null,
            application.assets.open("blocker_resources/$filename")
        )
    }

    /*
    // element hiding
    override fun loadScript(uri: Uri): String? {
        val cosmetic = elementBlocker ?: return null
        return cosmetic.loadScript(uri)
        return null
    }
     */

    companion object {
        val blockerPrefixes = listOf(
            ABP_PREFIX_ALLOW,
            ABP_PREFIX_DENY,
            ABP_PREFIX_MODIFY,
            ABP_PREFIX_MODIFY_EXCEPTION,
            ABP_PREFIX_IMPORTANT,
            ABP_PREFIX_IMPORTANT_ALLOW,
            ABP_PREFIX_REDIRECT,
            ABP_PREFIX_REDIRECT_EXCEPTION,
        )
        val badfilterPrefixes = blockerPrefixes.map { ABP_PREFIX_BADFILTER + it }

        fun isModify(prefix: String) = prefix in listOf(ABP_PREFIX_MODIFY,
            ABP_PREFIX_MODIFY_EXCEPTION,
            ABP_PREFIX_REDIRECT,
            ABP_PREFIX_REDIRECT_EXCEPTION)

        private const val TAG = "AbpBlocker"

        private val okHttpAcceptedSchemes = listOf("https", "http", "ws", "wss")

        // from jp.hazuki.yuzubrowser.core.utility.utils/FileUtils.kt
        const val MIME_TYPE_UNKNOWN = "application/octet-stream"

        fun getMimeType(fileName: String): String {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = fileName.substring(lastDot + 1).lowercase()
                    // strip potentially leftover parameters and fragment
                    .substringBefore('?').substringBefore('#')
                return getMimeTypeFromExtension(extension)
            }
            return MIME_TYPE_UNKNOWN
        }

        // from jp.hazuki.yuzubrowser.core.utility.utils/FileUtils.kt
        fun getMimeTypeFromExtension(extension: String): String {
            return when (extension) {
                "js" -> "application/javascript"
                "mhtml", "mht" -> "multipart/related"
                "json" -> "application/json"
                else -> {
                    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    if (type.isNullOrEmpty()) {
                        MIME_TYPE_UNKNOWN
                    } else {
                        type
                    }
                }
            }
        }

        // from jp.hazuki.yuzubrowser.core.utility.extensions/HtmlExtensions.kt
        fun getNoCacheResponse(mimeType: String, sequence: CharSequence): WebResourceResponse {
            return getNoCacheResponse(
                mimeType, ByteArrayInputStream(
                    sequence.toString().toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            )
        }

        // from jp.hazuki.yuzubrowser.core.utility.extensions/HtmlExtensions.kt
        private fun getNoCacheResponse(mimeType: String, stream: InputStream): WebResourceResponse {
            val response = WebResourceResponse(mimeType, "UTF-8", stream)
            response.responseHeaders =
                HashMap<String, String>().apply { put("Cache-Control", "no-cache") }
            return response
        }

        fun Collection<Pair<String, UnifiedFilter>>.sanitize(badFilters: Collection<Pair<String, UnifiedFilter>>): List<Pair<String, UnifiedFilter>> {
            val badFilterFilters = badFilters.map { it.second }

            // TODO: badfilter should also work with wildcard domain matching as described on https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters#badfilter-modifier
            //  resp. https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#badfilter
            //  -> if badfilter matches filter only ignoring domains -> remove matching domains from the filter, also match wildcard
            val filters = filterNot {
                badFilterFilters.contains(it.second)
            }

            // TODO: remove filters contained in others
            //  e.g. ||example.com^ and ||ads.example.com^, or ||example.com^ and ||example.com^$third-party
            //  use tags for the first, and hashCode without 3rd party for the second?

            // TODO: combine filters that are the same except for domains or content type
            //  but this may be slow and not worth much work...

            // maybe limit checks to certain types where such things occur more often?

            return filters
        }

    }

    private class ThirdPartyLruCache(size: Int) : LruCache<String, Boolean>(size) {
        override fun create(key: String): Boolean {
            return key.split('/').let { is3rdParty(it[0], it[1]) }
        }

        private fun is3rdParty(hostName: String, pageHost: String): Boolean {
            val ipPattern = PatternsCompat.IP_ADDRESS
            if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches())
                return true
            val db = PublicSuffix.get()
            return db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost)
        }
    }
}

private class WebkitCookieManager(private val cookieManager: CookieManager) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        when (val cookies = cookieManager.getCookie(url.toString())) {
            null -> emptyList()
            else -> cookies.split("; ").mapNotNull { Cookie.parse(url, it) }
        }
}
