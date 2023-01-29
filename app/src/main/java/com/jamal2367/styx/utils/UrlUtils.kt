/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

@file:JvmName("UrlUtils")

package com.jamal2367.styx.utils

import android.util.Patterns
import android.webkit.URLUtil
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.constant.FILE
import com.jamal2367.styx.constant.Schemes
import com.jamal2367.styx.constant.Uris
import com.jamal2367.styx.html.bookmark.BookmarkPageFactory
import com.jamal2367.styx.html.download.DownloadPageFactory
import com.jamal2367.styx.html.history.HistoryPageFactory
import com.jamal2367.styx.html.homepage.HomePageFactory
import com.jamal2367.styx.html.incognito.IncognitoPageFactory
import java.util.*
import java.util.regex.Pattern

/**
 * Attempts to determine whether user input is a URL or search terms.  Anything with a space is
 * passed to search if [canBeSearch] is true.
 *
 * Converts to lowercase any mistakenly upper-cased scheme (i.e., "Http://" converts to
 * "http://")
 *
 * @param canBeSearch if true, will return a search url if it isn't a valid  URL. If false,
 * invalid URLs will return null.
 * @return original or modified URL.
 */
fun smartUrlFilter(url: String, canBeSearch: Boolean, searchUrl: String): Pair<String, Boolean> {
    var inUrl = url.trim()
    val hasSpace = inUrl.contains(' ')
    val matcher = ACCEPTED_URI_SCHEMA.matcher(inUrl)
    if (matcher.matches()) {
        // force scheme to lowercase
        val scheme = requireNotNull(matcher.group(1)) { "matches() implies this is non null" }
        val lcScheme = scheme.lowercase(Locale.getDefault())
        if (lcScheme != scheme) {
            inUrl = lcScheme + matcher.group(2)
        }
        if (hasSpace && Patterns.WEB_URL.matcher(inUrl).matches()) {
            inUrl = inUrl.replace(" ", URL_ENCODED_SPACE)
        }
        return Pair(inUrl, false)
    }
    if (!hasSpace) {
        if (Patterns.WEB_URL.matcher(inUrl).matches()) {
            return Pair(URLUtil.guessUrl(inUrl), false)
        }
    }

    return if (canBeSearch) {
        Pair(URLUtil.composeSearchUrl(inUrl, searchUrl, QUERY_PLACE_HOLDER), true)
    } else {
        Pair("", false)
    }
}

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isBookmarkUri(): Boolean =
    this == Uris.StyxBookmarks || this == Uris.AboutBookmarks

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isHomeUri(): Boolean =
    this == Uris.StyxHome || this == Uris.AboutHome

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isIncognitoUri(): Boolean =
    this == Uris.StyxIncognito || this == Uris.AboutIncognito

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isHistoryUri(): Boolean =
    this == Uris.StyxHistory || this == Uris.AboutHistory

/**
 * Returns whether the given url is the bookmarks/history page or a normal website
 */
fun String?.isSpecialUrl(): Boolean =
    this != null
            && (this.startsWith(FILE + BrowserApp.instance.filesDir)
            && (this.endsWith(BookmarkPageFactory.FILENAME)
            || this.endsWith(DownloadPageFactory.FILENAME)
            || this.endsWith(HistoryPageFactory.FILENAME)
            || this.endsWith(HomePageFactory.FILENAME)
            || this.endsWith(IncognitoPageFactory.FILENAME))
            /*|| this.startsWith(Schemes.Styx + "://")*/)

/**
 * Check if this URL is using the specified scheme.
 */
fun String?.isScheme(aScheme: String): Boolean =
    this != null
            && this.startsWith("$aScheme:")


/**
 * Check if this URL is using any application specific schemes.
 */
fun String?.isAppScheme(): Boolean =
    isScheme(Schemes.Styx)
            || isScheme(Schemes.About)

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isBookmarkUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(BookmarkPageFactory.FILENAME)

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isDownloadsUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(DownloadPageFactory.FILENAME)

/**
 * Determines if the url is a url for the history page.
 *
 * @return true if the url is a history url, false otherwise.
 */
fun String?.isHistoryUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(HistoryPageFactory.FILENAME)

/**
 * Determines if the url is a url for the start page.
 *
 * @return true if the url is a start page url, false otherwise.
 */
fun String?.isStartPageUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(HomePageFactory.FILENAME)

/**
 * Determines if the url is a url for the incognito page.
 *
 * @return true if the url is a incognito page url, false otherwise.
 */
fun String?.isIncognitoPageUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(IncognitoPageFactory.FILENAME)

private val ACCEPTED_URI_SCHEMA =
    Pattern.compile("(?i)((?:http|https|file)://|(?:inline|data|about|javascript|styx):|(:.*:.*@))(.*)")
const val QUERY_PLACE_HOLDER = "%s"
private const val URL_ENCODED_SPACE = "%20"
