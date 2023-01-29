/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.webkit.WebView
import com.jamal2367.styx.browser.TabModel
import com.jamal2367.styx.constant.Uris
import com.jamal2367.styx.di.DiskScheduler
import com.jamal2367.styx.di.MainScheduler
import com.jamal2367.styx.html.HtmlPageFactory
import com.jamal2367.styx.html.bookmark.BookmarkPageFactory
import com.jamal2367.styx.html.download.DownloadPageFactory
import com.jamal2367.styx.html.history.HistoryPageFactory
import com.jamal2367.styx.html.homepage.HomePageFactory
import com.jamal2367.styx.html.incognito.IncognitoPageFactory
import com.jamal2367.styx.preference.UserPreferences
import dagger.Reusable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

/**
 * An initializer that is run on a [StyxView] after it is created.
 */
interface TabInitializer {

    /**
     * Initialize the [WebView] instance held by the [StyxView]. If a url is loaded, the
     * provided [headers] should be used to load the url.
     */
    fun initialize(webView: WebView, headers: Map<String, String>)

    /**
     * Tab can't be initialized without a URL.
     * That's just how browsers work: one tab, one URL.
     */
    fun url(): String

}

/**
 * An initializer that loads a [url].
 */
class UrlInitializer(private val url: String) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        webView.loadUrl(url, headers)
    }

    override fun url(): String {
        return url
    }

}

/**
 * An initializer that displays the page set as the user's homepage preference.
 */
@Reusable
class HomePageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
    private val startPageInitializer: StartPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        val homepage = userPreferences.homepage

        when (homepage) {
            Uris.AboutHome -> startPageInitializer
            Uris.AboutBookmarks -> bookmarkPageInitializer
            else -> UrlInitializer(homepage)
        }.initialize(webView, headers)
    }

    override fun url(): String {
        return Uris.StyxHome
    }

}

/**
 * An initializer that displays the page set as the user's incognito homepage preference.
 */
@Reusable
class IncognitoPageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
    private val startIncognitoPageInitializer: StartIncognitoPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        val homepage = userPreferences.homepage

        when (homepage) {
            Uris.AboutHome -> startIncognitoPageInitializer
            Uris.AboutBookmarks -> bookmarkPageInitializer
            else -> UrlInitializer(homepage)
        }.initialize(webView, headers)
    }

    override fun url(): String {
        return Uris.StyxIncognito
    }

}

/**
 * An initializer that displays the start page.
 */
@Reusable
class StartPageInitializer @Inject constructor(
    homePageFactory: HomePageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler,
) : HtmlPageFactoryInitializer(homePageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.StyxStart
    }
}

/**
 * An initializer that displays the start incognito page.
 */
@Reusable
class StartIncognitoPageInitializer @Inject constructor(
    incognitoPageFactory: IncognitoPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler,
) : HtmlPageFactoryInitializer(incognitoPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.StyxIncognito
    }
}

/**
 * An initializer that displays the bookmark page.
 */
@Reusable
class BookmarkPageInitializer @Inject constructor(
    bookmarkPageFactory: BookmarkPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler,
) : HtmlPageFactoryInitializer(bookmarkPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.StyxBookmarks
    }
}

/**
 * An initializer that displays the download page.
 */
@Reusable
class DownloadPageInitializer @Inject constructor(
    downloadPageFactory: DownloadPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler,
) : HtmlPageFactoryInitializer(downloadPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.StyxDownloads
    }
}

/**
 * An initializer that displays the history page.
 */
@Reusable
class HistoryPageInitializer @Inject constructor(
    historyPageFactory: HistoryPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler,
) : HtmlPageFactoryInitializer(historyPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.StyxHistory
    }
}

/**
 * An initializer that loads the url built by the [HtmlPageFactory].
 */
abstract class HtmlPageFactoryInitializer(
    private val htmlPageFactory: HtmlPageFactory,
    @DiskScheduler private val diskScheduler: Scheduler,
    @MainScheduler private val foregroundScheduler: Scheduler,
) : TabInitializer {

    @SuppressLint("CheckResult")
    override fun initialize(webView: WebView, headers: Map<String, String>) {
        htmlPageFactory
            .buildPage()
            .subscribeOn(diskScheduler)
            .observeOn(foregroundScheduler)
            .subscribeBy(onSuccess = { webView.loadUrl(it, headers) })
    }

}

/**
 * An initializer that sets the [WebView] as the target of the [resultMessage]. Used for
 * `target="_blank"` links.
 */
class ResultMessageInitializer(private val resultMessage: Message) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        resultMessage.apply {
            (obj as WebView.WebViewTransport).webView = webView
        }.sendToTarget()
    }

    override fun url(): String {
        /** We don't know our URL at this stage, it will only be loaded in the WebView by whatever is handling the message sent above.
         * That's ok though as we implemented a special case to handle this situation in [StyxView.initializeContent]
         */
        return ""
    }

}

/**
 * An initializer that restores the [WebView] state using the [bundle].
 */
abstract class BundleInitializer(private val bundle: Bundle?) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        bundle?.let { webView.restoreState(it) }
    }

}

/**
 * should be initially set on the tab.
 */
class FreezableBundleInitializer(
    val tabModel: TabModel,
) : BundleInitializer(tabModel.webView) {
    override fun url(): String {
        return tabModel.url
    }
}

/**
 * An initializer that does not load anything into the [WebView].
 */
@Reusable
class NoOpInitializer @Inject constructor() : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) = Unit

    override fun url(): String {
        return Uris.StyxNoop
    }

}

