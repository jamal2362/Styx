/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser

import android.content.Intent
import android.webkit.URLUtil
import androidx.appcompat.app.AppCompatActivity
import com.jamal2367.styx.R
import com.jamal2367.styx.constant.FILE
import com.jamal2367.styx.constant.INTENT_ORIGIN
import com.jamal2367.styx.constant.Uris
import com.jamal2367.styx.html.bookmark.BookmarkPageFactory
import com.jamal2367.styx.html.homepage.HomePageFactory
import com.jamal2367.styx.html.incognito.IncognitoPageFactory
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.isSpecialUrl
import com.jamal2367.styx.view.FreezableBundleInitializer
import com.jamal2367.styx.view.StyxView
import com.jamal2367.styx.view.TabInitializer
import com.jamal2367.styx.view.UrlInitializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presenter in charge of keeping track of the current tab and setting the current tab of the
 * browser.
 */
@Singleton
class BrowserPresenter @Inject constructor(
    private val userPreferences: UserPreferences,
    private val homePageFactory: HomePageFactory,
    private val incognitoPageFactory: IncognitoPageFactory,
    private val bookmarkPageFactory: BookmarkPageFactory,
    private val logger: Logger,
) : styx.Component() {

    private var currentTab: StyxView? = null
    private var shouldClose: Boolean = false

    lateinit var view: BrowserView
    var isIncognito: Boolean = false
    lateinit var closedTabs: RecentTabsModel
    lateinit var tabsModel: TabsManager

    /**
     * Switch to the session with the given name
     */
    fun switchToSession(aSessionName: String) {
        // Don't do anything if given session name is already the current one or if such session does not exists
        if (!tabsModel.isInitialized
            || tabsModel.iCurrentSessionName == aSessionName
            || tabsModel.iSessions.none { s -> s.name == aSessionName }
        ) {
            return
        }

        tabsModel.isInitialized = false

        // Save current states
        tabsModel.saveState()
        // Change current session
        tabsModel.iCurrentSessionName = aSessionName
        // Save it again to preserve new current session name
        tabsModel.saveSessions()
        // Then reload our tabs
        setupTabs()
    }

    /**
     * Initializes our tab manager.
     */
    fun setupTabs(aIntent: Intent? = null) {
        iScopeMainThread.launch {
            delay(1L)
            val tabs = tabsModel.initializeTabs(view as AppCompatActivity, isIncognito)
            // At this point we always have at least a tab in the tab manager
            view.notifyTabViewInitialized()
            view.updateTabNumber(tabsModel.size())
            // Switch to persisted current tab
            tabChanged(if (tabsModel.savedRecentTabsIndices.isNotEmpty()) tabsModel.savedRecentTabsIndices.last() else tabsModel.positionOf(
                tabs.last()))
            // Only then can we open tab from external app on startup otherwise it is opened in the background somehow
            aIntent?.let { onNewIntent(aIntent) }
        }

        logger.log(TAG, "After from main")
    }

    /**
     * Notify the presenter that a change occurred to the current tab. Currently doesn't do anything
     * other than tell the view to notify the adapter about the change.
     *
     * @param tab the tab that changed, may be null.
     */
    fun tabChangeOccurred(tab: StyxView) = tab.let {
        view.notifyTabViewChanged(tabsModel.indexOfTab(it))
    }

    /**
     *
     */
    private fun onTabChanged(newTab: StyxView) {
        logger.log(TAG, "On tab changed")

        currentTab?.let {
            // blank after calling onPause followed by onResume.
            // it.onPause();
            it.isForeground = false
        }

        // Must come first so that frozen tabs are unfrozen
        // This will create frozen tab WebView, before that WebView is not available
        newTab.isForeground = true

        newTab.resumeTimers()
        newTab.onResume()

        view.updateProgress(newTab.progress)
        view.setBackButtonEnabled(newTab.canGoBack())
        view.setForwardButtonEnabled(newTab.canGoForward())
        view.updateUrl(newTab.url, false)
        view.setTabView(newTab.webView!!)
        val index = tabsModel.indexOfTab(newTab)
        if (index >= 0) {
            view.notifyTabViewChanged(tabsModel.indexOfTab(newTab))
        }

        // Must come late as it needs a webview
        view.updateSslState(newTab.currentSslState())

        currentTab = newTab
    }

    /**
     * Closes all tabs but the current tab.
     */
    fun closeAllOtherTabs() {

        while (tabsModel.last() != tabsModel.indexOfCurrentTab()) {
            deleteTab(tabsModel.last())
        }

        while (0 != tabsModel.indexOfCurrentTab()) {
            deleteTab(0)
        }
    }

    /**
     * SL: That's not quite working for some reason.
     * Close all tabs
     */
    private fun mapHomepageToCurrentUrl(): String = when (val homepage = userPreferences.homepage) {
        Uris.AboutHome -> "$FILE${homePageFactory.createHomePage()}"
        Uris.AboutBookmarks -> "$FILE${bookmarkPageFactory.createBookmarkPage(null)}"
        else -> homepage
    }

    private fun mapIncognitoToCurrentUrl(): String =
        when (val homepage = userPreferences.incognitoPage) {
            Uris.AboutIncognito -> "$FILE${incognitoPageFactory.createIncognitoPage()}"
            Uris.AboutBookmarks -> "$FILE${bookmarkPageFactory.createBookmarkPage(null)}"
            else -> homepage
        }

    /**
     * Deletes the tab at the specified position.
     *
     * @param position the position at which to delete the tab.
     */
    fun deleteTab(position: Int) {
        logger.log(TAG, "deleting tab...")
        val tabToDelete = tabsModel.getTabAtPosition(position) ?: return

        closedTabs.add(tabToDelete.saveState())

        val isShown = tabToDelete.isShown
        val shouldClose = shouldClose && isShown && tabToDelete.isNewTab
        val currentTab = tabsModel.currentTab

        if (!userPreferences.closeOnLastTab && tabsModel.currentTab == null && !isIncognito) {
            newTab(UrlInitializer(mapHomepageToCurrentUrl()), true)
        }

        val currentDeleted = tabsModel.deleteTab(position)
        if (currentDeleted) {
            tabChanged(tabsModel.indexOfCurrentTab())
        }

        val afterTab = tabsModel.currentTab
        view.notifyTabViewRemoved(position)

        if (afterTab == null) {
            if (userPreferences.closeOnLastTab) {
                view.closeBrowser()
            } else {
                if (isIncognito) {
                    newTab(UrlInitializer(mapIncognitoToCurrentUrl()), true)
                } else {
                    newTab(UrlInitializer(mapHomepageToCurrentUrl()), true)
                }
            }
            return
        } else if (afterTab !== currentTab) {
            view.notifyTabViewChanged(tabsModel.indexOfCurrentTab())
        }

        if (shouldClose && !isIncognito) {
            this.shouldClose = false
            view.closeActivity()
        }

        view.updateTabNumber(tabsModel.size())

        logger.log(TAG, "...deleted tab")
    }

    /**
     * Handle a new intent from the the main BrowserActivity.
     *
     * @param intent the intent to handle, may be null.
     */
    fun onNewIntent(intent: Intent?) = tabsModel.doOnceAfterInitialization {
        val url = when (intent?.action) {
            Intent.ACTION_WEB_SEARCH -> {
                tabsModel.extractSearchFromIntent(intent)
            }
            Intent.ACTION_SEND -> {
                // User shared text with our app
                if ("text/plain" == intent.type) {
                    // Get shared text
                    val clue = intent.getStringExtra(Intent.EXTRA_TEXT)
                    // Put it in the address bar if any
                    clue?.let { view.setAddressBarText(it) }
                }
                // Cancel other operation as we won't open a tab here
                null
            }
            else -> {
                intent?.dataString
            }
        }

        val tabHashCode = intent?.extras?.getInt(INTENT_ORIGIN, 0) ?: 0

        if (tabHashCode != 0 && url != null) {
            tabsModel.getTabForHashCode(tabHashCode)?.loadUrl(url)
        } else if (url != null) {
            if (URLUtil.isFileUrl(url)) {
                view.showBlockedLocalFileDialog {
                    newTab(UrlInitializer(url), true)
                    shouldClose = true
                    tabsModel.lastTab()?.isNewTab = true
                }
            } else {
                newTab(UrlInitializer(url), true)
                shouldClose = true
                tabsModel.lastTab()?.isNewTab = true
            }
        }
    }

    /**
     * Recover last closed tab.
     */
    fun recoverClosedTab(show: Boolean = true) {
        closedTabs.popLast()?.let { bundle ->
            TabModelFromBundle(bundle).let {
                if (it.url.isSpecialUrl()) {
                    // That's a special URL
                    newTab(tabsModel.tabInitializerForSpecialUrl(it.url), show)
                } else {
                    // That's an actual WebView bundle
                    newTab(FreezableBundleInitializer(it), show)
                }
            }
            view.showSnackbar(R.string.reopening_recent_tab)
        }
    }

    /**
     * Recover all closed tabs
     */
    fun recoverAllClosedTabs() {
        while (closedTabs.bundleStack.isNotEmpty()) {
            recoverClosedTab(false)
        }
    }

    /**
     * Loads a URL in the current tab.
     *
     * @param url the URL to load, must not be null.
     */
    fun loadUrlInCurrentView(url: String) {
        tabsModel.currentTab?.loadUrl(url)
    }

    /**
     * Notifies the presenter that we wish to switch to a different tab at the specified position.
     * If the position is not in the model, this method will do nothing.
     *
     * @param position the position of the tab to switch to.
     */
    fun tabChanged(position: Int) {
        if (position < 0 || position >= tabsModel.size()) {
            logger.log(TAG, "tabChanged invalid position: $position")
            return
        }

        logger.log(TAG, "tabChanged: $position")
        onTabChanged(tabsModel.switchToTab(position))
    }

    /**
     * Open a new tab with the specified URL. You can choose to show the tab or load it in the
     * background.
     *
     * @param tabInitializer the tab initializer to run after the tab as been created.
     * @param show whether or not to switch to this tab after opening it.
     * @return true if we successfully created the tab, false if we have hit max tabs.
     */
    fun newTab(tabInitializer: TabInitializer, show: Boolean): Boolean {
        logger.log(TAG, "New tab, show: $show")

        val startingTab = tabsModel.newTab(view as AppCompatActivity,
            tabInitializer,
            isIncognito,
            userPreferences.newTabPosition)
        if (tabsModel.size() == 1) {
            startingTab.resumeTimers()
        }

        view.notifyTabViewAdded()
        view.updateTabNumber(tabsModel.size())

        if (show) {
            onTabChanged(tabsModel.switchToTab(tabsModel.indexOfTab(startingTab)))
        } else {
            // We still need to add it to our recent tabs
            // Adding at the beginning of a Set is doggy though
            val recentTabs = tabsModel.iRecentTabs.toSet()
            tabsModel.iRecentTabs.clear()
            tabsModel.iRecentTabs.add(startingTab)
            tabsModel.iRecentTabs.addAll(recentTabs)
        }

        return true
    }

    fun onAutoCompleteItemPressed() {
        tabsModel.currentTab?.requestFocus()
    }

    companion object {
        private const val TAG = "BrowserPresenter"
    }

}
