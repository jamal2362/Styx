package com.jamal2367.styx.adblock

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.util.PatternsCompat
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.R
import com.jamal2367.styx.adblocker.EmptyInputStream
import com.jamal2367.styx.adblocker.core.AbpLoader
import com.jamal2367.styx.adblocker.core.ContentRequest
import com.jamal2367.styx.adblocker.core.CosmeticFiltering
import com.jamal2367.styx.adblocker.core.FilterContainer
import com.jamal2367.styx.adblocker.filter.abp.ABP_PREFIX_ALLOW
import com.jamal2367.styx.adblocker.filter.abp.ABP_PREFIX_DENY
import com.jamal2367.styx.adblocker.filter.abp.ABP_PREFIX_DISABLE_ELEMENT_PAGE
import com.jamal2367.styx.adblocker.filter.unified.element.ElementContainer
import com.jamal2367.styx.adblocker.filter.unified.getFilterDir
import com.jamal2367.styx.adblocker.getContentType
import com.jamal2367.styx.adblocker.repository.abp.AbpDao
import com.jamal2367.styx.okhttp3.internal.publicsuffix.PublicSuffix
import com.jamal2367.styx.utils.ThemeUtils
import com.jamal2367.styx.utils.htmlColor
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.HashMap

@Singleton
class AbpBlocker @Inject constructor(
    private val application: Application
    ) : AdBlocker {
//    @Inject internal lateinit var userPreferences: UserPreferences

    // filter lists
    private var exclusionList: FilterContainer? = null
    private var blockList: FilterContainer? = null
    private var userWhitelist: FilterContainer? = null
    private var userBlockList: FilterContainer? = null

    // if i want mining/malware block, it should be separate lists so they don't get disabled when not blocking ads
    // or actually... i could just fill the blockList with all that is desired, and disable filling with adblock lists if ad blocking is there
    // no. then exclusions would come before malware/mining detection. not good.
    private var miningList: FilterContainer? = null // copy from yuzu?
    private var malwareList: FilterContainer? = null // copy from smartcookieweb/styx?

    // TODO: element hiding
    //  not sure if this actually works (did not in a test), maybe it's crucial to inject the js at the right point
    //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
    private var elementHideExclusionList: FilterContainer? = null
    private var elementHideList: ElementContainer? = null
    private var elementBlocker: CosmeticFiltering? = null
    var useElementHide = true // should load from preferences

    // cache for 3rd party check, allows significantly faster checks
    private val thirdPartyCache = mutableMapOf<String, Boolean>()
    private val thirdPartyCacheSize = 100

    private val dummyImage: ByteArray = readByte(application.applicationContext.resources.assets.open("blank.webp"))
    private val dummy = WebResourceResponse("text/plain", "UTF-8", EmptyInputStream())

    private var isAbpIgnoreGenericElement = false // TODO: just copied, what is this?

    private var waitForLoading: CountDownLatch? = null



    override fun isAd(url: String) = false // for now...

    init {
        // load lists. this takes about a second on S4 mini for easylist alone... probably worse with more lists
        // maybe do it in a different way using this reactivex stuff?
        update()
        runBlocking {
            GlobalScope.launch(Dispatchers.IO) {
                // necessary because updateAll might download new lists
                // maybe make better? and allow some auto-update settings, like update on wifi only
                AbpListUpdater(application.applicationContext).updateAll(false) // updates all entities in AbpDao
            }
            update() // again? should be done only if anything updated -> use the broadcast from FillList (old updater)?
        }
    }

//----------------------- from yuzu adblocker (mostly AdBlockController) --------------------//
    // slightly adjusted, currently not used
    fun updateAsync() {
        waitForLoading = CountDownLatch(1)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val abpLoader = AbpLoader(application.applicationContext.getFilterDir(), AbpDao(application.applicationContext).getAll())
                val block = async {
                    FilterContainer().also {
                        abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::plusAssign)
                    }
                }
                val exclusions = async {
                    FilterContainer().also {
                        abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::plusAssign)
                    }
                }
                if (useElementHide) { // TODO: add preference and get from userPreferences
                    val disableCosmetic = async {
                        FilterContainer().also {
                            abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign)
                        }
                    }
                    val elementFilter = async {
                        ElementContainer().also {
                            abpLoader.loadAllElementFilter().forEach(it::plusAssign)
                        }
                    }

                    elementBlocker = CosmeticFiltering(disableCosmetic.await(), elementFilter.await())
                }

                blockList = block.await()
                exclusionList = exclusions.await()
                //adBlocker = Blocker(exclusions.await(), block.await())

                // does this actually do anything? even in yuzu it appears to be never read
                isAbpIgnoreGenericElement = false //AppPrefs.isAbpIgnoreGenericElement.get()
            } finally {
                waitForLoading?.countDown()
                waitForLoading = null
            }
        }
    } // use once i understand what is going on...

    // more adjusted
    fun update() {
        val abpLoader = AbpLoader(application.applicationContext.getFilterDir(), AbpDao(application.applicationContext).getAll())
        blockList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::plusAssign) }
        exclusionList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::plusAssign) }

        if (useElementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }
        isAbpIgnoreGenericElement = false //AppPrefs.isAbpIgnoreGenericElement.get()
    }

    fun createDummy(uri: Uri): WebResourceResponse {
        val mimeType = getMimeType(uri.toString())
        return if (mimeType.startsWith("image/")) {
            WebResourceResponse("image/png", null, ByteArrayInputStream(dummyImage))
        } else {
            dummy
        }
    }

    // TODO: replace the quoted resource strings by something "real"
    fun createMainFrameDummy(uri: Uri, pattern: String): WebResourceResponse {
        val blocked = application.getString(R.string.ad_block_blocked_page)
        val filter = application.getString(R.string.ad_block_blocked_filter)
        val background = htmlColor(ThemeUtils.getSurfaceColor(BrowserApp.currentContext()))
        val background1 = htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),R.attr.trackColor))
        val text = htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),R.attr.colorOnPrimary))
        val builder = StringBuilder("<meta charset=utf-8>" +
                "<meta content=\"width=device-width,initial-scale=1,minimum-scale=1\"name=viewport>" +
                "<style>body{padding:5px 15px;background:$background}body,p{text-align:center;color:$text}p{margin:20px 0 0}" +
                "pre{margin:5px 0;padding:5px;background:$background1}}</style>")
            .append("<p>")
            .append(blocked)
            .append("<pre>")
            .append(uri)
            .append("</pre><p>")
            .append(filter)
            .append("<pre>")
            .append(pattern)
            .append("</pre>")

        return getNoCacheResponse("text/html", builder)
    }

    override fun loadScript(uri: Uri): String? {
        val cosmetic = elementBlocker ?: return null

        return cosmetic.loadScript(uri)
    }

    // copied here to allow modified 3rd party detection
    private fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(this.url, pageUri))

    // modified to use cache for the slow part, decreases average time by 50-70%
    fun is3rdParty(url: Uri, pageUri: Uri): Boolean {
        val hostName = url.host ?: return true
        val pageHost = pageUri.host ?: return true

        if (hostName == pageHost) return false

        val cacheEntry = hostName + pageHost
        val cached = thirdPartyCache[cacheEntry]
        if (cached != null)
            return cached

        val ipPattern = PatternsCompat.IP_ADDRESS
        if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches())
            return cache3rdPartyResult(true, cacheEntry)

        val db = PublicSuffix.get()

        return cache3rdPartyResult(db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost), cacheEntry)
    }

 //----------------------- not from yuzu any more ------------------------//

    // cache 3rd party check result
    private fun cache3rdPartyResult(is3rdParty: Boolean, cacheEntry: String): Boolean {
        thirdPartyCache[cacheEntry] = is3rdParty
        if (thirdPartyCache.size > thirdPartyCacheSize)
            thirdPartyCache.remove(thirdPartyCache.keys.first())
        return is3rdParty
    }

    // return null if not blocked, else some WebResourceResponse
    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // pageUrl might be "" (usually when opening something in a new tab)
        //  in this case everything gets blocked because of the pattern "|https://"
        //  this is blocked for some domains, and apparently no domain also means it's blocked
        //  so some workaround here (maybe do something else?)
        val contentRequest = request.getContentRequest(if (pageUrl == "") request.url else Uri.parse(pageUrl))

        // check user lists
        // then mining/malware
        // then ads
        when {
            userWhitelist?.get(contentRequest) != null -> {
                return null
            }
            userBlockList?.get(contentRequest) != null -> {
                return getBlockResponse(request)
            }
            miningList?.get(contentRequest) != null -> {
                return getBlockResponse(request)
            }
            malwareList?.get(contentRequest) != null -> {
                return getBlockResponse(request)
            }
            exclusionList?.get(contentRequest) != null -> {
                return null
            }
            blockList?.get(contentRequest) != null -> {
                return getBlockResponse(request)
            }
        }
        return null
    }

    // TODO: change to forward some string
    //  maybe just a reason (which list), maybe also get the pattern?
    private fun getBlockResponse(request: WebResourceRequest): WebResourceResponse {
        return if (request.isForMainFrame)
            createMainFrameDummy(request.url, "just ignore the filter/pattern for now, maybe add later")
        else
            createDummy(request.url)
    }

    // stuff from yuzu browser modules/core/.../utility
    companion object {
        private const val BUFFER_SIZE = 1024 * 8

        @Throws(IOException::class)
        fun readByte(inpputStream: InputStream): ByteArray {
            val buffer = ByteArray(BUFFER_SIZE)
            val bout = ByteArrayOutputStream()
            var n: Int
            while (inpputStream.read(buffer).also { n = it } >= 0) {
                bout.write(buffer, 0, n)
            }
            return bout.toByteArray()
        }

        private const val MIME_TYPE_UNKNOWN = "application/octet-stream"
        fun getMimeType(fileName: String): String {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = fileName.substring(lastDot + 1).lowercase(Locale.getDefault())
                return getMimeTypeFromExtension(extension)
            }
            return "application/octet-stream"
        }

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

        fun getNoCacheResponse(mimeType: String, sequence: CharSequence): WebResourceResponse {
            return getNoCacheResponse(
                mimeType, ByteArrayInputStream(
                    sequence.toString().toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            )
        }

        fun getNoCacheResponse(mimeType: String, stream: InputStream): WebResourceResponse {
            val response = WebResourceResponse(mimeType, "UTF-8", stream)
            response.responseHeaders =
                HashMap<String, String>().apply { put("Cache-Control", "no-cache") }
            return response
        }
    }
}
