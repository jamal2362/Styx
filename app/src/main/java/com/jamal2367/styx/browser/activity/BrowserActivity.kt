package com.jamal2367.styx.browser.activity

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.View.*
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebView
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.customview.widget.ViewDragHelper
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.anthonycr.grant.PermissionsManager
import com.github.ahmadaghazadeh.editor.widget.CodeEditor
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.IncognitoActivity
import com.jamal2367.styx.R
import com.jamal2367.styx.adblock.allowlist.AllowListModel
import com.jamal2367.styx.browser.*
import com.jamal2367.styx.browser.bookmarks.BookmarksDrawerView
import com.jamal2367.styx.browser.cleanup.ExitCleanup
import com.jamal2367.styx.browser.sessions.SessionsPopupWindow
import com.jamal2367.styx.browser.tabs.TabsDesktopView
import com.jamal2367.styx.browser.tabs.TabsDrawerView
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.database.Bookmark
import com.jamal2367.styx.database.HistoryEntry
import com.jamal2367.styx.database.SearchSuggestion
import com.jamal2367.styx.database.WebPage
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.database.history.HistoryRepository
import com.jamal2367.styx.databinding.ActivityMainBinding
import com.jamal2367.styx.databinding.ToolbarContentBinding
import com.jamal2367.styx.di.*
import com.jamal2367.styx.dialog.BrowserDialog
import com.jamal2367.styx.dialog.DialogItem
import com.jamal2367.styx.dialog.StyxDialogBuilder
import com.jamal2367.styx.extensions.*
import com.jamal2367.styx.html.bookmark.BookmarkPageFactory
import com.jamal2367.styx.html.history.HistoryPageFactory
import com.jamal2367.styx.html.homepage.HomePageFactory
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.notifications.IncognitoNotification
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.reading.ReadingActivity
import com.jamal2367.styx.search.SearchEngineProvider
import com.jamal2367.styx.search.SuggestionsAdapter
import com.jamal2367.styx.settings.NewTabPosition
import com.jamal2367.styx.settings.activity.SettingsActivity
import com.jamal2367.styx.settings.fragment.DisplaySettingsFragment.Companion.MAX_BROWSER_TEXT_SIZE
import com.jamal2367.styx.settings.fragment.DisplaySettingsFragment.Companion.MIN_BROWSER_TEXT_SIZE
import com.jamal2367.styx.ssl.SslState
import com.jamal2367.styx.ssl.createSslDrawableForState
import com.jamal2367.styx.ssl.showSslDialog
import com.jamal2367.styx.utils.*
import com.jamal2367.styx.utils.Utils.adjustBottomSheet
import com.jamal2367.styx.view.*
import com.jamal2367.styx.view.SearchView
import com.jamal2367.styx.view.find.FindResults
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import java.io.IOException
import java.net.URL
import java.util.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

abstract class BrowserActivity : ThemedBrowserActivity(), BrowserView, UIController, OnClickListener, OnKeyboardVisibilityListener {

    // Notifications
    private lateinit var channelid: String

    // Current tab view being displayed
    private var currentTabView: View? = null

    // Full Screen Video Views
    private var fullscreenContainerView: FrameLayout? = null
    private var videoView: VideoView? = null
    private var customView: View? = null

    // Adapter
    private var suggestionsAdapter: SuggestionsAdapter? = null

    // Callback
    private var customViewCallback: CustomViewCallback? = null
    private var uploadMessageCallback: ValueCallback<Uri>? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Primitives
    private var isFullScreen: Boolean = false
    private var hideStatusBar: Boolean = false
    private var isImmersiveMode = false
    private var verticalTabBar: Boolean = false
    private var swapBookmarksAndTabs: Boolean = false

    private var originalOrientation: Int = 0
    private var currentUiColor = Color.BLACK
    var currentToolBarTextColor = Color.BLACK
    private var keyDownStartTime: Long = 0
    private var searchText: String? = null
    private var cameraPhotoPath: String? = null

    private var findResult: FindResults? = null

    // The singleton BookmarkManager
    @Inject lateinit var bookmarkManager: BookmarkRepository
    @Inject lateinit var historyModel: HistoryRepository
    @Inject lateinit var searchBoxModel: SearchBoxModel
    @Inject lateinit var searchEngineProvider: SearchEngineProvider
    @Inject lateinit var inputMethodManager: InputMethodManager
    @Inject lateinit var clipboardManager: ClipboardManager
    @Inject lateinit var notificationManager: NotificationManager
    @Inject @field:DiskScheduler lateinit var diskScheduler: Scheduler
    @Inject @field:DatabaseScheduler lateinit var databaseScheduler: Scheduler
    @Inject @field:MainScheduler lateinit var mainScheduler: Scheduler
    @Inject lateinit var tabsManager: TabsManager
    @Inject lateinit var homePageFactory: HomePageFactory
    @Inject lateinit var bookmarkPageFactory: BookmarkPageFactory
    @Inject lateinit var historyPageFactory: HistoryPageFactory
    @Inject lateinit var historyPageInitializer: HistoryPageInitializer
    @Inject lateinit var downloadPageInitializer: DownloadPageInitializer
    @Inject lateinit var homePageInitializer: HomePageInitializer
    @Inject lateinit var bookmarkPageInitializer: BookmarkPageInitializer
    @Inject @field:MainHandler lateinit var mainHandler: Handler
    @Inject lateinit var logger: Logger
    @Inject lateinit var bookmarksDialogBuilder: StyxDialogBuilder
    @Inject lateinit var exitCleanup: ExitCleanup
    @Inject internal lateinit var allowListModel: AllowListModel

    // HTTP
    private lateinit var queue: RequestQueue

    // Image
    private var webPageBitmap: Bitmap? = null
    private val backgroundDrawable = ColorDrawable()
    private var incognitoNotification: IncognitoNotification? = null

    var presenter: BrowserPresenter? = null
    private var tabsView: TabsView? = null
    private var bookmarksView: BookmarksDrawerView? = null

    // Menu
    private lateinit var popupMenu: BrowserPopupMenu
    lateinit var sessionsMenu: SessionsPopupWindow
    private lateinit var tabsDialog: BottomSheetDialog
    private lateinit var bookmarksDialog: BottomSheetDialog

    // Binding
    lateinit var iBinding: ActivityMainBinding
    lateinit var iBindingToolbarContent: ToolbarContentBinding

    // Toolbar Views
    private lateinit var searchView: SearchView
    private lateinit var buttonSessions: ImageButton

    // Settings
    private var showCloseTabButton = false

    private val longPressBackRunnable = Runnable {
        //showCloseDialog(tabsManager.positionOf(tabsManager.currentTab))
    }

    /**
     * Determines if the current browser instance is in incognito mode or not.
     */
    abstract fun isIncognito(): Boolean

    /**
     * Choose the behavior when the controller closes the view.
     */
    abstract override fun closeActivity()

    /**
     * Choose what to do when the browser visits a website.
     *
     * @param title the title of the site visited.
     * @param url the url of the site visited.
     */
    abstract override fun updateHistory(title: String?, url: String)

    /**
     * An observable which asynchronously updates the user's cookie preferences.
     */
    protected abstract fun updateCookiePreference(): Completable

    /**
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(this)

        if (BrowserApp.instance.justStarted) {
            BrowserApp.instance.justStarted = false
            // Since amazingly on Android you can't tell when your app is closed we do exit cleanup on start-up, go figure
            // See: https://github.com/Slion/Fulguris/issues/106
            performExitCleanUp()
        }

        iBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        queue = Volley.newRequestQueue(this)

        createPopupMenu()
        createSessionsMenu()
        tabsDialog = BottomSheetDialog(this)
        bookmarksDialog = BottomSheetDialog(this)

        if (isIncognito()) {
            incognitoNotification = IncognitoNotification(this, notificationManager)
        }
        tabsManager.addTabNumberChangedListener {
            if (isIncognito()) {
                if (it == 0) {
                    incognitoNotification?.hide()
                } else {
                    incognitoNotification?.show(it)
                }
            }
        }

        presenter = BrowserPresenter(
                this,
                isIncognito(),
                userPreferences,
                tabsManager,
                mainScheduler,
                homePageFactory,
                bookmarkPageFactory,
                RecentTabsModel(),
                logger
        )

        setKeyboardVisibilityListener(this)

        initialize(savedInstanceState)

        tabsManager.doAfterInitialization {
            if (userPreferences.useBottomSheets) {
                if (tabsDialog.isShowing) {
                    // Upon session switch we need to do that otherwise our status bar padding could be wrong
                    mainHandler.postDelayed({
                        adjustBottomSheet()
                    }, 100 )
                }
            }
            mainHandler.postDelayed({ checkForUpdates(this) }, 1000)
        }

        // Hook in buttons with onClick handler
        iBindingToolbarContent.buttonReload.setOnClickListener(this)
    }

    /**
     *
     */
    private fun setKeyboardVisibilityListener(onKeyboardVisibilityListener: OnKeyboardVisibilityListener) {
        val parentView = (findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0)
        parentView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var alreadyOpen = false
            private val defaultKeyboardHeightDP = 100
            private val EstimatedKeyboardDP = defaultKeyboardHeightDP + 48
            private val rect: Rect = Rect()
            override fun onGlobalLayout() {
                val estimatedKeyboardHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, EstimatedKeyboardDP.toFloat(), parentView.resources.displayMetrics).toInt()
                parentView.getWindowVisibleDisplayFrame(rect)
                val heightDiff: Int = parentView.rootView.height - (rect.bottom - rect.top)
                val isShown = heightDiff >= estimatedKeyboardHeight
                if (isShown == alreadyOpen) {
                    return
                }
                alreadyOpen = isShown
                onKeyboardVisibilityListener.onVisibilityChanged(isShown)
            }
        })
    }

    /**
     *
     */
    override fun onVisibilityChanged(visible: Boolean) {
        if(userPreferences.navbar){
            val extraBar = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            if(visible){
                extraBar.visibility = GONE
            }
            else{
                extraBar.visibility = VISIBLE
            }
        }
    }

    /**
     *
     */
    private fun createSessionsMenu() {
        sessionsMenu = SessionsPopupWindow(layoutInflater)
    }

    // Used to avoid running that too many times, by keeping a reference to it we can cancel that runnable
    // That works around graphical glitches happening when run too many times
    private var onSizeChangeRunnable : Runnable = Runnable {}

    // Used to cancel that runnable as needed
    private var resetBackgroundColorRunnable : Runnable = Runnable {}

    /**
     * Used for both tabs and bookmarks.
     */
    @Suppress("DEPRECATION")
    private fun createBottomSheetDialog(aContentView: View) : BottomSheetDialog {
        val dialog = BottomSheetDialog(this)

        // Set up BottomSheetDialog
        dialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        dialog.window?.setFlags(window.attributes.flags, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // dialog.window?.setFlags(dialog.window?.attributes!!.flags, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        // dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        // Needed to make sure our bottom sheet shows below our session pop-up
        // dialog.window?.attributes?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        //

        // We need to set private data member edgeToEdgeEnabled to true to get full screen effect
        // That won't be needed past material:1.4.0-alpha02
        val field = BottomSheetDialog::class.java.getDeclaredField("edgeToEdgeEnabled")
        field.isAccessible = true
        field.setBoolean(dialog, true)
        //
        aContentView.removeFromParent()
        dialog.setContentView(aContentView)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isDraggable = !userPreferences.lockedDrawers

        // Make sure dialog top padding and status bar icons color are updated whenever our dialog is resized
        // Since we keep recreating our dialogs every time we open them we should not accumulate observers here
        (aContentView.parent as View).onSizeChange {
            // This is designed so that callbacks are cancelled unless or timeout expires
            // That avoids spamming adjustBottomSheet while our view is animated our dragged
            mainHandler.removeCallbacks(onSizeChangeRunnable)
            onSizeChangeRunnable = Runnable {adjustBottomSheet() }
            mainHandler.postDelayed(onSizeChangeRunnable, 100)
        }

        return dialog
    }


    /**
     *
     */
    @Suppress("DEPRECATION")
    private fun createTabsDialog() {
        tabsDialog.dismiss() // Defensive
        // Workaround issue with black icons during transition after first use
        // See: https://github.com/material-components/material-components-android/issues/2168
        tabsDialog = createBottomSheetDialog(tabsView as View)
        // Once our bottom sheet is open we want it to scroll to current tab
        tabsDialog.setOnShowListener { scrollToCurrentTab() }
        /*tabsDialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    scrollToCurrentTab()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        }
        )*/

        // Set up BottomSheetDialog
        tabsDialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        tabsDialog.window?.setFlags(window.attributes.flags, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        //tabsDialog.window?.setFlags(tabsDialog.window?.attributes!!.flags, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        //tabsDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        // Needed to make sure our bottom sheet shows below our session pop-up
        //tabsDialog.window?.attributes?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        //

        // We need to set private data member edgeToEdgeEnabled to true to get full screen effect
        // That won't be needed past material:1.4.0-alpha02
        val field = BottomSheetDialog::class.java.getDeclaredField("edgeToEdgeEnabled")
        field.isAccessible = true
        field.setBoolean(tabsDialog, true)
        //
        (tabsView as View).removeFromParent()
        tabsDialog.setContentView(tabsView as View)
        tabsDialog.behavior.skipCollapsed = true
    }

    /**
     *
     */
    @Suppress("DEPRECATION")
    private fun createBookmarksDialog() {
        bookmarksDialog.dismiss() // Defensive
        // Workaround issue with black icons during transition after first use.
        // See: https://github.com/material-components/material-components-android/issues/2168
        bookmarksDialog = createBottomSheetDialog(bookmarksView as View)

        // Define what to do once our drawer it opened
        //iBinding.drawerLayout.onceOnDrawerOpened {
        bookmarksView?.iBinding?.listBookmarks?.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        //}
        // Open bookmarks drawer
        // Set up BottomSheetDialog
        bookmarksDialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        bookmarksDialog.window?.setFlags(window.attributes.flags, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // Needed to make sure our bottom sheet shows below our session pop-up
        //bookmarksDialog.window?.attributes?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        //

        // We need to set private data member edgeToEdgeEnabled to true to get full screen effect
        // That won't be needed past material:1.4.0-alpha02
        val field = BottomSheetDialog::class.java.getDeclaredField("edgeToEdgeEnabled")
        field.isAccessible = true
        field.setBoolean(bookmarksDialog, true)
        //
        bookmarksView.removeFromParent()
        bookmarksDialog.setContentView(bookmarksView as View)
        bookmarksDialog.behavior.skipCollapsed = true
    }

    /**
     * Open our sessions pop-up menu.
     */
    private fun showSessions() {
        // If using horizontal tab bar or if our tab drawer is open
        if (!verticalTabBar || tabsDialog.isShowing) {
            // Use sessions button as anchor
            buttonSessions.let { sessionsMenu.show(it) }
        } else {
            // Otherwise use main menu button as anchor
            iBindingToolbarContent.buttonMore.let { sessionsMenu.show(it) }
        }
    }

    /**
     *
     */
    private fun createPopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        popupMenu.contentView
        popupMenu.apply {
            // Bind our actions
            onMenuItemClicked(iBinding.menuItemSessions) { executeAction(R.id.action_sessions) }
            onMenuItemClicked(iBinding.menuItemNewTab) { executeAction(R.id.action_new_tab) }
            onMenuItemClicked(iBinding.menuItemIncognito) { executeAction(R.id.menuItemIncognito) }
            onMenuItemClicked(iBinding.menuItemCloseIncognito) { executeAction(R.id.menuItemCloseIncognito) }
            onMenuItemClicked(iBinding.menuItemPrint) { executeAction(R.id.menuItemPrint) }
            onMenuItemClicked(iBinding.menuItemHistory) { executeAction(R.id.menuItemHistory) }
            onMenuItemClicked(iBinding.menuItemDownloads) { executeAction(R.id.menuItemDownloads) }
            onMenuItemClicked(iBinding.menuItemAddBookmark) { executeAction(R.id.menuItemAddBookmark) }
            onMenuItemClicked(iBinding.menuItemShare) { executeAction(R.id.menuItemShare) }
            onMenuItemClicked(iBinding.menuItemFind) { executeAction(R.id.menuItemFind) }
            onMenuItemClicked(iBinding.menuItemPageTools) { executeAction(R.id.menuItemPageTools) }
            onMenuItemClicked(iBinding.menuItemAddToHome) { executeAction(R.id.menuItemAddToHome) }
            onMenuItemClicked(iBinding.menuItemTranslate) { executeAction(R.id.menuItemTranslate) }
            onMenuItemClicked(iBinding.menuItemReaderMode) { executeAction(R.id.menuItemReaderMode) }
            onMenuItemClicked(iBinding.menuItemSettings) { executeAction(R.id.menuItemSettings) }
            onMenuItemClicked(iBinding.menuItemDesktopMode) { executeAction(R.id.menuItemDesktopMode) }
            onMenuItemClicked(iBinding.menuItemDarkMode) { executeAction(R.id.menuItemDarkMode) }

            // Popup menu action shortcut icons
            onMenuItemClicked(iBinding.menuShortcutRefresh) { executeAction(R.id.menuShortcutRefresh) }
            onMenuItemClicked(iBinding.menuShortcutHome) { executeAction(R.id.menuShortcutHome) }
            onMenuItemClicked(iBinding.menuShortcutForward) { executeAction(R.id.menuShortcutForward) }
            onMenuItemClicked(iBinding.menuShortcutBack) { executeAction(R.id.menuShortcutBack) }
            onMenuItemClicked(iBinding.menuShortcutBookmarks) { executeAction(R.id.menuShortcutBookmarks) }

        }
    }

    /**
     *
     */
    private fun showPopupMenu() {
        popupMenu.show(iBindingToolbarContent.buttonMore)
    }

    /**
     * Needed to be able to display system notifications
     */
    private fun createNotificationChannel() {
        // Is that string visible in system UI somehow?
        channelid = "Styx Channel ID"
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.downloads)
            val descriptionText = getString(R.string.downloads_notification_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelid, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Provide primary color, typically used as default toolbar color.
     */
    private val primaryColor: Int
        get() {
            // If current tab is using forced dark mode and we do not use a dark theme…
            return if (tabsManager.currentTab?.darkMode == true && !useDarkTheme) {
                // …then override primary color…
                Color.BLACK
            } else {
                // …otherwise just use current theme surface color.
                ThemeUtils.getSurfaceColor(this)
            }
        }

    /**
     *
     */
    private fun initialize(savedInstanceState: Bundle?) {

        createNotificationChannel()

        iBindingToolbarContent = ToolbarContentBinding.inflate(layoutInflater, iBinding.toolbarInclude.toolbar, true)

        showCloseTabButton = userPreferences.showCloseTabButton
        swapBookmarksAndTabs = userPreferences.bookmarksAndTabsSwapped

        // initialize background ColorDrawable
        backgroundDrawable.color = primaryColor

        // Drawer stutters otherwise
        //iBinding.leftDrawer.setLayerType(LAYER_TYPE_NONE, null)
        //iBinding.rightDrawer.setLayerType(LAYER_TYPE_NONE, null)


        iBinding.drawerLayout.addDrawerListener(DrawerLocker())

        webPageBitmap = drawable(R.drawable.ic_webpage).toBitmap()

        // Is that still needed
        val customView = iBinding.toolbarInclude.toolbar
        customView.layoutParams = customView.layoutParams.apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        // Show incognito icon in more menu button
        if (isIncognito()) {
            iBindingToolbarContent.buttonMore.setImageResource(R.drawable.ic_incognito_24)
        }

        iBindingToolbarContent.tabsButton.setOnClickListener(this)
        iBindingToolbarContent.homeButton.setOnClickListener(this)
        iBindingToolbarContent.buttonActionBack.setOnClickListener{executeAction(R.id.button_action_back)}
        iBindingToolbarContent.buttonActionForward.setOnClickListener{executeAction(R.id.button_action_forward)}

        createTabsView()

        //createTabsDialog()
        bookmarksView = BookmarksDrawerView(this, this, userPreferences = userPreferences)
        //createBookmarksDialog()

        // create the search EditText in the ToolBar
        searchView = iBindingToolbarContent.addressBarInclude.search.apply {
            iBindingToolbarContent.addressBarInclude.searchSslStatus.setOnClickListener {
                tabsManager.currentTab?.let { tab ->
                    tab.sslCertificate?.let { showSslDialog(it, tab.currentSslState()) }
                }
            }
            iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
            //setMenuItemIcon(R.id.action_reload, R.drawable.ic_action_refresh)
            //toolbar?.menu?.findItem(R.id.action_reload)?.let { it.icon = ContextCompat.getDrawable(this@BrowserActivity, R.drawable.ic_action_refresh) }

            val searchListener = SearchListenerClass()
            setOnKeyListener(searchListener)
            onFocusChangeListener = searchListener
            setOnEditorActionListener(searchListener)
            onPreFocusListener = searchListener
            addTextChangedListener(StyleRemovingTextWatcher())

            initializeSearchSuggestions(this)
        }

        // initialize search background color
        setSearchBarColors(primaryColor)

        var intent: Intent? = if (savedInstanceState == null) {
            intent
        } else {
            null
        }

        val launchedFromHistory = intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

        if (launchedFromHistory) {
            intent = null
        }

        presenter?.setupTabs(intent)
        setIntent(null)

        if (userPreferences.lockedDrawers) {
            lockDrawers()
        }

        // Enable swipe to refresh
        iBinding.contentFrame.setOnRefreshListener {
            tabsManager.currentTab?.reload()
            mainHandler.postDelayed({ iBinding.contentFrame.isRefreshing = false }, 1000)   // Stop the loading spinner after one second
        }

        // iBinding.uiLayout.layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, iBinding.uiLayout.layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING))
        // Disabling animations which are not so nice
        iBinding.uiLayout.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        iBinding.uiLayout.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)


        iBindingToolbarContent.buttonMore.setOnClickListener {
            // Web page is loosing focus as we open our menu
            // Actually this was causing our search field to gain focus on HTC One M8 - Android 6
            // currentTabView?.clearFocus()
            // Check if virtual keyboard is showing
            if (inputMethodManager.isActive) {
                // Open our menu with a slight delay giving enough time for our virtual keyboard to close
                mainHandler.postDelayed({ showPopupMenu() }, 100)

            } else {
                // Display our popup menu instantly
                showPopupMenu()
            }
        }

    }

    /**
     *
     */
    private fun closePanelTabs() {
        iBinding.drawerLayout.closeDrawer(getTabDrawer())
        tabsDialog.dismiss()
    }

    /**
     *
     */
    private fun closePanelBookmarks() {
        iBinding.drawerLayout.closeDrawer(getBookmarkDrawer())
        bookmarksDialog.dismiss()
    }

    /**
     * Used to create or recreate our tabs view according to current settings.
     */
    private fun createTabsView() {

        verticalTabBar = userPreferences.verticalTabBar

        // Close tab drawer if we are switching to a configuration that's not using it
        if (tabsView!=null && !verticalTabBar && iBinding.drawerLayout.isDrawerOpen(getTabDrawer()) /*&& tabsView is TabsDrawerView*/) {
            // That's done to prevent user staring at an empty open drawer after changing configuration
            iBinding.drawerLayout.closeDrawer(getTabDrawer())
        }

        // Remove existing tab view if any
        (tabsView as View?)?.removeFromParent()
        // Instantiate our view
        tabsView = if (verticalTabBar) {
            TabsDrawerView(this)
        } else {
            TabsDesktopView(this)
        }
        createTabsDialog()
        // Add it to proper parent
        addTabsViewToParent()

        buttonSessions = (tabsView as View).findViewById(R.id.action_sessions)

        if (verticalTabBar) {
            iBindingToolbarContent.tabsButton.isVisible = true
            iBindingToolbarContent.homeButton.isVisible = false
            iBinding.toolbarInclude.tabBarContainer.isVisible = false
        } else {
            iBindingToolbarContent.tabsButton.isVisible = false
            iBindingToolbarContent.homeButton.isVisible = true
            iBinding.toolbarInclude.tabBarContainer.isVisible = true
        }

        if (userPreferences.navbar) {
            iBindingToolbarContent.tabsButton.isVisible = false
        }

        if (userPreferences.navbar && !userPreferences.verticalTabBar) {
            iBindingToolbarContent.homeButton.isVisible = false
        }

        if (userPreferences.longClickTab) {
        iBindingToolbarContent.tabsButton.setOnLongClickListener {
            presenter?.newTab(homePageInitializer, true)
            true }
        }
    }

    /**
     *
     */
    private fun addTabsViewToParent() {
        val v = (tabsView as View)
        if (verticalTabBar && userPreferences.useBottomSheets) {
            // Check if our tabs list already belongs to our bottom sheet
            if (tabsDialog.findViewById<ViewGroup>(R.id.tabs_list) != v.findViewById<ViewGroup>(R.id.tabs_list)) {
                // It was not found, just put it there then
                v.removeFromParent()
                tabsDialog.setContentView(v)
            }
        } else {
            // Check if our tab view is already in place
            if (v.parent != getTabBarContainer()) {
                // It was not, lets put it there then
                v.removeFromParent()
                getTabBarContainer().addView(v)
            }
        }

    }

    /**
     *
     */
    private fun getBookmarksContainer(): ViewGroup = if (swapBookmarksAndTabs) {
        iBinding.leftDrawer
    } else {
        iBinding.rightDrawer
    }

    /**
     *
     */
    private fun getTabBarContainer(): ViewGroup = if (verticalTabBar) {
        if (swapBookmarksAndTabs) {
            iBinding.rightDrawer
        } else {
            iBinding.leftDrawer
        }
    } else {
        iBinding.toolbarInclude.tabBarContainer
    }

    /**
     *
     */
    private fun getBookmarkDrawer(): View = if (swapBookmarksAndTabs) {
        iBinding.leftDrawer
    } else {
        iBinding.rightDrawer
    }

    /**
     *
     */
    private fun getTabDrawer(): View = if (swapBookmarksAndTabs) {
        iBinding.rightDrawer
    } else {
        iBinding.leftDrawer
    }

    /**
     *
     */
    protected fun panicClean() {
        logger.log(TAG, "Closing browser")
        tabsManager.newTab(this, NoOpInitializer(), false, NewTabPosition.END_OF_TAB_LIST)
        tabsManager.switchToTab(0)
        tabsManager.clearSavedState()

        historyPageFactory.deleteHistoryPage().subscribe()
        closeBrowser()
        // System exit needed in the case of receiving
        // the panic intent since finish() isn't completely
        // closing the browser
        exitProcess(1)
    }

    /**
     *
     */
    private inner class SearchListenerClass : OnKeyListener,
        OnEditorActionListener,
        OnFocusChangeListener,
        SearchView.PreFocusListener {

        override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    searchView.let {
                        if (it.listSelection == ListView.INVALID_POSITION) {
                            // No suggestion pop up item selected, just trigger a search then
                            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                            searchTheWeb(it.text.toString())
                        } else {
                            // An item in our selection pop up is selected, just action it
                            doSearchSuggestionAction(it, it.listSelection)
                        }
                    }
                    tabsManager.currentTab?.requestFocus()
                    return true
                }
                else -> {
                }
            }
            return false
        }

        override fun onEditorAction(arg0: TextView, actionId: Int, arg2: KeyEvent?): Boolean {
            // hide the keyboard and search the web when the enter key
            // button is pressed
            if (actionId == EditorInfo.IME_ACTION_GO
                || actionId == EditorInfo.IME_ACTION_DONE
                || actionId == EditorInfo.IME_ACTION_NEXT
                || actionId == EditorInfo.IME_ACTION_SEND
                || actionId == EditorInfo.IME_ACTION_SEARCH
                || arg2?.action == KeyEvent.KEYCODE_ENTER) {
                searchView.let {
                    inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                    searchTheWeb(it.text.toString())
                }

                tabsManager.currentTab?.requestFocus()
                return true
            }
            return false
        }

        override fun onFocusChange(v: View, hasFocus: Boolean) {
            val currentView = tabsManager.currentTab

            if (currentView != null) {
                setIsLoading(currentView.progress < 100)

                if (!hasFocus) {
                    updateUrl(currentView.url, false)
                } else if (hasFocus) {
                    showUrl()
                    // Select all text so that user conveniently start typing or copy current URL
                    (v as SearchView).selectAll()
                    iBindingToolbarContent.addressBarInclude.searchSslStatus.visibility = GONE
                }
            }

            if (!hasFocus) {
                iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
                searchView.let {
                    inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                }
            }
        }

        override fun onPreFocus() {
            // SL: hopefully not needed anymore
            // That was never working with keyboard
            //val currentView = tabsManager.currentTab ?: return
            //val url = currentView.url
            //if (!url.isSpecialUrl()) {
            //    if (searchView.hasFocus() == false) {
            //        searchView.setText(url)
            //    }
            //}
        }
    }

    /**
     * Called when search view gains focus
     */
    private fun showUrl() {
        val currentView = tabsManager.currentTab ?: return
        val url = currentView.url
        if (!url.isSpecialUrl()) {
                searchView.setText(url)
        }
        else {
            // Special URLs like home page and history just show search field then
            searchView.setText("")
        }
    }

    var drawerOpened : Boolean = false
    var drawerOpening : Boolean = false
    var drawerClosing : Boolean = false

    /**
     *
     */
    private inner class DrawerLocker : DrawerLayout.DrawerListener {

        override fun onDrawerClosed(v: View) {
            drawerOpened = false
            drawerClosing = false
            drawerOpening = false

            // Trying to sort out our issue with touch input reaching through drawer into address bar
            //toolbar_layout.isEnabled = true

            // This was causing focus problems when switching directly from tabs drawer to bookmarks drawer
            //currentTabView?.requestFocus()

            if (userPreferences.lockedDrawers) return // Drawers remain locked
            val tabsDrawer = getTabDrawer()
            val bookmarksDrawer = getBookmarkDrawer()

            if (v === tabsDrawer) {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, bookmarksDrawer)
            } else if (verticalTabBar) {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, tabsDrawer)
            }

        }

        override fun onDrawerOpened(v: View) {

            drawerOpened = true
            drawerClosing = false
            drawerOpening = false

            // Trying to sort out our issue with touch input reaching through drawer into address bar
            //toolbar_layout.isEnabled = false

            if (userPreferences.lockedDrawers) return // Drawers remain locked

            val tabsDrawer = getTabDrawer()
            val bookmarksDrawer = getBookmarkDrawer()

            if (v === tabsDrawer) {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, bookmarksDrawer)
            } else {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, tabsDrawer)
            }
        }

        override fun onDrawerSlide(v: View, arg: Float) = Unit

        override fun onDrawerStateChanged(arg: Int) {

            // Make sure status bar icons have the proper color set when we start opening and closing a drawer
            // We set status bar icon color according to current theme
            if (arg == ViewDragHelper.STATE_SETTLING) {
                if (!drawerOpened) {
                    drawerOpening = true
                    // Make sure icons on status bar remain visible
                    // We should really check the primary theme color and work out its luminance but that should do for now
                    window.setStatusBarIconsColor(!useDarkTheme && !userPreferences.useBlackStatusBar)
                }
                else {
                    drawerClosing = true
                    // Restore previous system UI visibility flag
                    setToolbarColor()
                }

            }
        }

    }

    /**
     *
     */
    private fun lockDrawers()
    {
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getTabDrawer())
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getBookmarkDrawer())
    }

    /**
     *
     */
    private fun unlockDrawers()
    {
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, getTabDrawer())
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, getBookmarkDrawer())
    }

    /**
     * Set toolbar color corresponding to the current tab
     */
    private fun setToolbarColor()
    {
        val currentView = tabsManager.currentTab
        if (isColorMode() && currentView != null && currentView.htmlMetaThemeColor!=Color.TRANSPARENT && !currentView.darkMode) {
            // Web page does specify theme color, use it much like Google Chrome does
            mainHandler.post {applyToolbarColor(currentView.htmlMetaThemeColor)}
        }
        else if (isColorMode() && currentView?.favicon != null && !currentView.darkMode) {
            // Web page has favicon, use it to extract page theme color
            changeToolbarBackground(currentView.favicon, Color.TRANSPARENT, null)
        } else {
            // That should be the primary color from current theme
            mainHandler.post {applyToolbarColor(primaryColor)}
        }
    }

    /**
     *
     */
    private fun initFullScreen(configuration: Configuration) {
        isFullScreen = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            userPreferences.hideToolBarInPortrait
        }
        else {
            userPreferences.hideToolBarInLandscape
        }
    }

    private var wasToolbarsBottom = false

    /**
     * Setup our tool bar as collapsible or always on according to orientation and user preferences
     */
    private fun setupToolBar(configuration: Configuration) {
        initFullScreen(configuration)
        initializeToolbarHeight(configuration)
        showActionBar()
        setToolbarColor()
        setFullscreenIfNeeded(configuration)

        // Put our toolbar where it belongs, top or bottom according to user preferences
        iBinding.toolbarInclude.apply {
            if (userPreferences.toolbarsBottom) {
                // Move toolbar to the bottom
                root.removeFromParent()?.addView(root)
                // Rearrange it so that it is upside down
                // Put tab bar at the bottom
                tabBarContainer.removeFromParent()?.addView(tabBarContainer)
                // Put progress bar at the top
                progressView.removeFromParent()?.addView(progressView,0)
                // Take care of tab drawer if any
                (tabsView as? TabsDrawerView)?.apply {
                    // Put our tab list on top then to push toolbar to the bottom
                    iBinding.tabsList.removeFromParent()?.addView(iBinding.tabsList,0)
                    // Use reversed layout from bottom to top
                    (iBinding.tabsList.layoutManager as? LinearLayoutManager)?.reverseLayout = true
                }

                // Take care of bookmarks drawer
                bookmarksView?.apply {
                    // Put our list on top then to push toolbar to the bottom
                    iBinding.listBookmarks.removeFromParent()?.addView(iBinding.listBookmarks,0)
                    // Use reversed layout from bottom to top
                    (iBinding.listBookmarks.layoutManager as? LinearLayoutManager)?.reverseLayout = true
                }

                // Deal with session menu
                sessionsMenu.animationStyle = R.style.AnimationMenuBottom
                (sessionsMenu.iBinding.recyclerViewSessions.layoutManager as? LinearLayoutManager)?.reverseLayout = true
                // Move sessions menu toolbar to the bottom
                sessionsMenu.iBinding.toolbar.apply{removeFromParent()?.addView(this)}

                // Set popup menus animations
                popupMenu.animationStyle = R.style.AnimationMenuBottom
                // Move popup menu toolbar to the bottom
                popupMenu.iBinding.header.apply{removeFromParent()?.addView(this)}

                // Set search dropdown anchor to avoid gap
                searchView.dropDownAnchor = R.id.address_bar_include

            } else {
                // Move toolbar to the top
                root.removeFromParent()?.addView(root, 0)
                //iBinding.uiLayout.addView(root, 0)
                // Rearrange it so that it is the right way up
                // Put tab bar at the bottom
                tabBarContainer.removeFromParent()?.addView(tabBarContainer,0)
                // Put progress bar at the top
                progressView.removeFromParent()?.addView(progressView)
                // Take care of tab drawer if any
                (tabsView as? TabsDrawerView)?.apply {
                    // Put our tab list at the bottom
                    iBinding.tabsList.removeFromParent()?.addView(iBinding.tabsList)
                    // Use straight layout from top to bottom
                    (iBinding.tabsList.layoutManager as? LinearLayoutManager)?.reverseLayout = false
                }

                // Take care of bookmarks drawer
                bookmarksView?.apply {
                    // Put our list at the bottom
                    iBinding.listBookmarks.removeFromParent()?.addView(iBinding.listBookmarks)
                    // Use reversed layout from bottom to top
                    (iBinding.listBookmarks.layoutManager as? LinearLayoutManager)?.reverseLayout = false
                }

                // Set popup menus animations
                popupMenu.animationStyle = R.style.AnimationMenu
                sessionsMenu.animationStyle = R.style.AnimationMenu
                // Move popup menu toolbar to the top
                popupMenu.iBinding.header.apply{removeFromParent()?.addView(this, 0)}
            }
        }

        wasToolbarsBottom = userPreferences.toolbarsBottom
    }

    /**
     *
     */
    private fun setupToolBar() {
        // Check if our tool bar is long enough to display extra buttons
        val threshold = iBindingToolbarContent.buttonActionBack.width *10
        // If our tool bar is longer than 10 action buttons then we show extra buttons
        (iBinding.toolbarInclude.toolbar.width>threshold).let{
            iBindingToolbarContent.buttonActionBack.isVisible = it
            iBindingToolbarContent.buttonActionForward.isVisible = it
        }
    }

    /**
     *
     */
    private fun initializePreferences() {

        val currentSearchEngine = searchEngineProvider.provideSearchEngine()
        searchText = currentSearchEngine.queryUrl

        updateCookiePreference().subscribeOn(diskScheduler).subscribe()

        val extraBar = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (!userPreferences.verticalTabBar) {
            iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getTabDrawer())
        }

        if (userPreferences.useBottomSheets) {
            iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getTabDrawer())
            iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getBookmarkDrawer())
        }

        if (!userPreferences.navbar) {
            extraBar.visibility = GONE
        } else {
            extraBar.visibility = VISIBLE
            if (!userPreferences.verticalTabBar) {
                extraBar.menu.removeItem(R.id.tabs)
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getTabDrawer())
            }
            extraBar.setOnItemSelectedListener { item ->
                when(item.itemId) {
                    R.id.tabs -> {
                        openTabs()
                        true
                    }
                    R.id.bookmarks -> {
                        openBookmarks()
                        true
                    }
                    R.id.forward -> {
                        tabsManager.currentTab?.goForward()
                        true
                    }
                    R.id.back -> {
                        tabsManager.currentTab?.goBack()
                        true
                    }
                    R.id.home -> {
                        if (userPreferences.homepageInNewTab) {
                            presenter?.newTab(homePageInitializer, true)
                        } else {
                            // Why not through presenter? We need some serious refactoring at some point
                            tabsManager.currentTab?.loadHomePage()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     *
     */
    public override fun onWindowVisibleToUserAfterResume() {
        super.onWindowVisibleToUserAfterResume()
    }

    /**
     *
     */
    private fun actionFocusTextField() {
        if (!isToolBarVisible()) {
            showActionBar()
        }
        searchView.requestFocus()
    }

    /**
     *
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (searchView.hasFocus()) {
                searchTheWeb(searchView.text.toString())
            }
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyDownStartTime = System.currentTimeMillis()
            mainHandler.postDelayed(longPressBackRunnable, ViewConfiguration.getLongPressTimeout().toLong())
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     *
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mainHandler.removeCallbacks(longPressBackRunnable)
            if (System.currentTimeMillis() - keyDownStartTime > ViewConfiguration.getLongPressTimeout()) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    // For CTRL+TAB implementation
    private var iRecentTabIndex = -1
    private var iCapturedRecentTabsIndices : Set<StyxView>? = null

    /**
     *
     */
    private fun copyRecentTabsList()
    {
        // Fetch snapshot of our recent tab list
        iCapturedRecentTabsIndices = tabsManager.iRecentTabs.toSet()
        iRecentTabIndex = iCapturedRecentTabsIndices?.size?.minus(1) ?: -1
        //logger.log(TAG, "Recent indices snapshot: iCapturedRecentTabsIndices")
    }

    /**
     * Initiate Ctrl + Tab session if one is not already started.
     */
    private fun startCtrlTab()
    {
        if (iCapturedRecentTabsIndices==null)
        {
            copyRecentTabsList()
        }
    }

    /**
     * Reset ctrl + tab session if one was started.
     * Typically used when creating or deleting tabs.
     */
    private fun resetCtrlTab()
    {
        if (iCapturedRecentTabsIndices!=null)
        {
            copyRecentTabsList()
        }
    }

    /**
     * Stop ctrl + tab session.
     * Typically when the ctrl key is released.
     */
    private fun stopCtrlTab()
    {
        iCapturedRecentTabsIndices?.let {
            // Replace our recent tabs list by putting our captured one back in place making sure the selected tab is going back on top
            // See: https://github.com/Slion/Fulguris/issues/56
            tabsManager.iRecentTabs = it.toMutableSet()
            val tab = tabsManager.iRecentTabs.elementAt(iRecentTabIndex)
            tabsManager.iRecentTabs.remove(tab)
            tabsManager.iRecentTabs.add(tab)
        }

        iRecentTabIndex = -1
        iCapturedRecentTabsIndices = null
        //logger.log(TAG,"CTRL+TAB: Reset")
    }

    /**
     * Manage our key events.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {

        if (event.action == KeyEvent.ACTION_UP && (event.keyCode==KeyEvent.KEYCODE_CTRL_LEFT||event.keyCode==KeyEvent.KEYCODE_CTRL_RIGHT)) {
            // Exiting CTRL+TAB mode
            stopCtrlTab()
        }

        // Keyboard shortcuts
        if (event.action == KeyEvent.ACTION_DOWN) {

            // Used this to debug control usage on emulator as both ctrl and alt just don't work on emulator
            //val isCtrlOnly  = if (Build.PRODUCT.contains("sdk")) { true } else KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON)
            val isCtrlOnly  = KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON)
            val isCtrlShiftOnly  = KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

            when (event.keyCode) {
                // Toggle status bar visibility
                KeyEvent.KEYCODE_F10 -> {
                    setFullscreen(!statusBarHidden, false)
                    return true
                }
                // Toggle tool bar visibility
                KeyEvent.KEYCODE_F11 -> {
                    toggleToolBar()
                    return true
                }
                // Reload current tab
                KeyEvent.KEYCODE_F5 -> {
                    // Refresh current tab
                    tabsManager.currentTab?.reload()
                    return true
                }

                // Shortcut to focus text field
                KeyEvent.KEYCODE_F6 -> {
                    actionFocusTextField()
                    return true
                }

                // Move forward if WebView has focus
                KeyEvent.KEYCODE_FORWARD -> {
                    if (tabsManager.currentTab?.webView?.hasFocus() == true && tabsManager.currentTab?.canGoForward() == true) {
                        tabsManager.currentTab?.goForward()
                        return true
                    }
                }

                // This is actually being done in onBackPressed and doBackAction
                //KeyEvent.KEYCODE_BACK -> {
                //    if (tabsManager.currentTab?.webView?.hasFocus() == true && tabsManager.currentTab?.canGoBack() == true) {
                //        tabsManager.currentTab?.goBack()
                //        return true
                //    }
                //}
            }

            if (isCtrlOnly) {
                // Ctrl + tab number for direct tab access
                tabsManager.let {
                    if (KeyEvent.KEYCODE_0 <= event.keyCode && event.keyCode <= KeyEvent.KEYCODE_9) {
                        val nextIndex = if (event.keyCode > it.last() + KeyEvent.KEYCODE_1 || event.keyCode == KeyEvent.KEYCODE_0) {
                            // Go to the last tab for 0 or if not enough tabs
                            it.last()
                        } else {
                            // Otherwise access any of the first nine tabs
                            event.keyCode - KeyEvent.KEYCODE_1
                        }
                        presenter?.tabChanged(nextIndex)
                        return true
                    }
                }
            }

            if (isCtrlShiftOnly) {
                // Ctrl + Shift + session number for direct session access
                tabsManager.let {
                    if (KeyEvent.KEYCODE_0 <= event.keyCode && event.keyCode <= KeyEvent.KEYCODE_9) {
                        val nextIndex = if (event.keyCode > it.iSessions.count() + KeyEvent.KEYCODE_1 || event.keyCode == KeyEvent.KEYCODE_0) {
                            // Go to the last session if not enough sessions or KEYCODE_0
                            it.iSessions.count()-1
                        } else {
                            // Otherwise access any of the first nine sessions
                            event.keyCode - KeyEvent.KEYCODE_1
                        }
                        presenter?.switchToSession(it.iSessions[nextIndex].name)
                        return true
                    }
                }
            }

            // CTRL+TAB for tab cycling logic
            if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_TAB) {

                // Entering CTRL+TAB mode
                startCtrlTab()

                    iCapturedRecentTabsIndices?.let{

                        // Reversing can be done with those three modifiers notably to make it easier with two thumbs on F(x)tec Pro1
                        if (event.isShiftPressed or event.isAltPressed or event.isFunctionPressed) {
                            // Go forward one tab
                            iRecentTabIndex++
                            if (iRecentTabIndex>=it.size) iRecentTabIndex=0

                        } else {
                            // Go back one tab
                            iRecentTabIndex--
                            if (iRecentTabIndex<0) iRecentTabIndex=iCapturedRecentTabsIndices?.size?.minus(1) ?: -1
                        }

                        //logger.log(TAG, "Switching to $iRecentTabIndex : $iCapturedRecentTabsIndices")

                        if (iRecentTabIndex >= 0) {
                            // We worked out which tab to switch to, just do it now
                            presenter?.tabChanged(tabsManager.indexOfTab(it.elementAt(iRecentTabIndex)))
                            //mainHandler.postDelayed({presenter?.tabChanged(tabsManager.indexOfTab(it.elementAt(iRecentTabIndex)))}, 300)
                        }
                    }

                //logger.log(TAG,"Tab: down discarded")
                return true
            }

            when {
                isCtrlOnly -> when (event.keyCode) {
                    KeyEvent.KEYCODE_F -> {
                        // Search in page
                        showFindInPageControls(findViewById<EditText>(R.id.search_query).text.toString())
                        return true
                    }
                    KeyEvent.KEYCODE_T -> {
                        // Open new tab
                        presenter?.newTab(homePageInitializer, true)
                        resetCtrlTab()
                        return true
                    }
                    KeyEvent.KEYCODE_W -> {
                        // Close current tab
                        tabsManager.let { presenter?.deleteTab(it.indexOfCurrentTab()) }
                        resetCtrlTab()
                        return true
                    }
                    KeyEvent.KEYCODE_Q -> {
                        // Close browser
                        closeBrowser()
                        return true
                    }
                    // Mostly there because on F(x)tec Pro1 F5 switches off keyboard backlight
                    KeyEvent.KEYCODE_R -> {
                        // Refresh current tab
                        tabsManager.currentTab?.reload()
                        return true
                    }
                    // Show tab drawer displaying all pages
                    KeyEvent.KEYCODE_P -> {
                        toggleTabs()
                        return true
                    }
                    // Meritless shortcut matching Chrome's default
                    KeyEvent.KEYCODE_L -> {
                        actionFocusTextField()
                        return true
                    }
                    KeyEvent.KEYCODE_B -> {
                        toggleBookmarks()
                        return true
                    }
                    // Text zoom in and out
                    KeyEvent.KEYCODE_MINUS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = max(settings.textZoom - 5, MIN_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                        return true
                    }
                    KeyEvent.KEYCODE_EQUALS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = min(settings.textZoom + 5, MAX_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                        return true
                    }
                }

                isCtrlShiftOnly -> when (event.keyCode) {
                    KeyEvent.KEYCODE_T -> {
                        toggleTabs()
                        return true
                    }
                    KeyEvent.KEYCODE_S -> {
                        toggleSessions()
                        return true
                    }
                    KeyEvent.KEYCODE_B -> {
                        executeAction(R.id.menuItemAddBookmark)
                        return true
                    }
                    // Text zoom in and out
                    KeyEvent.KEYCODE_MINUS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = max(settings.textZoom - 1, MIN_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                    }
                    KeyEvent.KEYCODE_EQUALS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = min(settings.textZoom + 1, MAX_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                    }
                }

                event.keyCode == KeyEvent.KEYCODE_SEARCH -> {
                    // Highlight search field
                    searchView.requestFocus()
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Used to close empty tab after opening link.
     */
    fun closeCurrentTabIfEmpty() {
        // Had to delay that otherwise we could get there too early on the url still contains the download link
        // URL is later on reset to null by WebView internal mechanics.
        mainHandler.postDelayed({
            if ((currentTabView as? WebViewEx)?.url.isNullOrBlank()) {
                tabsManager.let { presenter?.deleteTab(it.indexOfCurrentTab()) }
            }
        }, 500)
    }


    /**
     *
     */
    override fun executeAction(@IdRes id: Int): Boolean {

        val currentView = tabsManager.currentTab
        val currentUrl = currentView?.url

        when (id) {
            android.R.id.home -> {
                if (showingBookmarks()) {
                    closePanelBookmarks()
                }
                return true
            }

            R.id.menuShortcutBack -> {
                if (currentView?.canGoBack() == true) {
                    currentView.goBack()
                }
                return true
            }
            R.id.menuShortcutForward -> {
                if (currentView?.canGoForward() == true) {
                    currentView.goForward()
                }
                return true
            }
            R.id.menuItemAddToHome -> {
                currentView?.url?.let { it ->
                    HistoryEntry(it, currentView.title).also {
                        Utils.createShortcut(this, it, currentView.favicon ?: webPageBitmap!!)
                        logger.log(TAG, "Creating shortcut: ${it.title} ${it.url}")
                    }
                }
                return true
            }
            R.id.action_new_tab -> {
                presenter?.newTab(homePageInitializer, true)
                return true
            }
            R.id.menuShortcutRefresh -> {
                if (searchView.hasFocus()) {
                    searchView.setText("")
                } else {
                    refreshOrStop()
                }
                return true
            }
            R.id.menuItemIncognito -> {
                startActivity(IncognitoActivity.createIntent(this))
                return true
            }
            R.id.menuItemCloseIncognito -> {
                closeActivity()
                return true
            }
            R.id.menuItemShare -> {
                IntentUtils(this).shareUrl(currentUrl, currentView?.title)
                return true
            }
            R.id.menuItemPrint -> {
                (currentTabView as WebViewEx).print()
                return true
            }
            R.id.menuShortcutBookmarks -> {
                openBookmarks()
                return true
            }
            R.id.menuItemSettings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            R.id.menuItemPageTools -> {
                showPageToolsDialog(this, userPreferences)
                return true
            }
            R.id.menuItemHistory -> {
                openHistory()
                return true
            }
            R.id.menuItemDownloads -> {
                openDownloads()
                return true
            }
            R.id.menuItemAddBookmark -> {
                addBookmark(currentView!!.title, currentUrl!!)
                return true
            }
            R.id.menuItemFind -> {
                showFindInPageControls(findViewById<EditText>(R.id.search_query).text.toString())
                return true
            }
            R.id.menuItemTranslate -> {
                val locale = Locale.getDefault()
                currentView?.loadUrl("https://www.translatetheweb.com/?from=&to=$locale&dl=$locale&a=$currentUrl")
                //currentView?.loadUrl("https://translate.google.com/translate?sl=auto&tl=$locale&u=" + currentUrl!!)
                return true
            }
            R.id.menuItemReaderMode -> {
                if (currentUrl != null) {
                    ReadingActivity.launch(this, currentUrl, false)
                }
                return true
            }
            R.id.action_restore_page -> {
                presenter?.recoverClosedTab()
                return true
            }
            R.id.action_restore_all_pages -> {
                presenter?.recoverAllClosedTabs()
                return true
            }

            R.id.action_close_all_tabs -> {
                presenter?.closeAllOtherTabs()
                return true
            }
            R.id.menuShortcutHome -> {
                if (userPreferences.homepageInNewTab) {
                    presenter?.newTab(homePageInitializer, true)
                } else {
                    // Why not through presenter? We need some serious refactoring at some point
                    tabsManager.currentTab?.loadHomePage()
                }
                closePanels(null)
                return true
            }
            R.id.menuItemDesktopMode -> {
                tabsManager.currentTab?.apply {
                    toggleDesktopUserAgent()
                    reload()
                }
                return true
            }
            R.id.menuItemDarkMode -> {
                tabsManager.currentTab?.apply {
                    toggleDarkMode()
                    // Calling setToolbarColor directly from here causes that old bug with WebView not resizing when hiding toolbar and not showing newly loaded WebView to resurface.
                    // Even doing a post does not fix it. However doing a long enough postDelayed does the trick.
                    mainHandler.postDelayed({setToolbarColor()},100)
                }
                return true
            }
            R.id.action_sessions -> {
                showSessions()
                return true
            }

            else -> return false
        }
    }

    /**
     * Legacy from menu framework. Since we are using custom popup window as menu we don't need this anymore.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
            return if (executeAction(item.itemId)) true else super.onOptionsItemSelected(item)
    }

    /**
     * By using a manager, adds a bookmark and notifies third parties about that.
     */
    private fun addBookmark(title: String, url: String) {
        val bookmark = Bookmark.Entry(url, title, 0, Bookmark.Folder.Root)
        bookmarksDialogBuilder.showAddBookmarkDialog(this, this, bookmark)
    }

    /**
     *
     */
    @SuppressLint("CheckResult")
    private fun deleteBookmark(title: String, url: String) {
        bookmarkManager.deleteBookmark(Bookmark.Entry(url, title, 0, Bookmark.Folder.Root))
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { boolean ->
                if (boolean) {
                    handleBookmarksChange()
                }
            }
    }

    /**
     *
     */
    private fun showFindInPageControls(text: String) {
        findViewById<View>(R.id.findInPageInclude).visibility = VISIBLE
        findViewById<TextView>(R.id.search_query).text = text
        findViewById<ImageButton>(R.id.button_next).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_back).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_quit).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_search).setOnClickListener(this)


        findViewById<TextView>(R.id.search_query).addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
    }

    /**
     *
     */
    private fun isLoading() : Boolean = tabsManager.currentTab?.let {
        it.progress < 100
    } ?: false

    /**
     * Enable or disable pull-to-refresh according to user preferences and state
     */
    private fun setupPullToRefresh(configuration: Configuration) {
        if (!userPreferences.pullToRefreshInPortrait && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                || !userPreferences.pullToRefreshInLandscape && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // User does not want to use pull to refresh
            iBinding.contentFrame.isEnabled = false
            iBindingToolbarContent.buttonReload.visibility = VISIBLE
            return
        }
        iBinding.contentFrame.isEnabled = currentTabView?.canScrollVertically()?:false
        iBindingToolbarContent.buttonReload.visibility = if (iBinding.contentFrame.isEnabled && !isLoading()) GONE else VISIBLE
    }

    /**
     * Reset our tab bar if needed.
     * Notably used after configuration change.
     */
    private fun setupTabBar(): Boolean {
        // Check if our tab bar style changed
        if (verticalTabBar!=userPreferences.verticalTabBar) {
            // We either coming or going to desktop like horizontal tab bar, tabs panel should be closed then
            mainHandler.post {closePanelTabs()}
            // Tab bar style changed recreate our tab bar then
            createTabsView()
            tabsView?.tabsInitialized()
            mainHandler.postDelayed({scrollToCurrentTab()},1000)
            return true
        }

        return false
    }

    /**
     * Tells if web page color should be applied to tool and status bar
     */
    override fun isColorMode(): Boolean = userPreferences.colorModeEnabled

    override fun getTabModel(): TabsManager = tabsManager

    /**
     *
     */
    override fun showCloseDialog(position: Int) {
        if (position < 0) {
            return
        }
        BrowserDialog.show(this, R.string.dialog_title_close_browser,
                DialogItem(title = R.string.close_tab) {
                    presenter?.deleteTab(position)
                },
                DialogItem(title = R.string.close_other_tabs) {
                    presenter?.closeAllOtherTabs()
                },
                DialogItem(title = R.string.close_all_tabs, onClick = this::closeBrowser))
    }

    /**
     *
     */
    override fun notifyTabViewRemoved(position: Int) {
        logger.log(TAG, "Notify Tab Removed: $position")
        tabsView?.tabRemoved(position)
        // Notify user a tab was closed with an option to recover it
        if (userPreferences.showUndo) {
            makeSnackbar(
                getString(R.string.notify_tab_closed), Snackbar.LENGTH_SHORT, if (userPreferences.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                .setAction(R.string.button_undo) {
                    presenter?.recoverClosedTab()
                }.show()
        }
    }

    /**
     *
     */
    override fun notifyTabViewAdded() {
        logger.log(TAG, "Notify Tab Added")
        tabsView?.tabAdded()
    }

    /**
     *
     */
    override fun notifyTabViewChanged(position: Int) {
        logger.log(TAG, "Notify Tab Changed: $position")
        tabsView?.tabChanged(position)
        setToolbarColor()
        setupPullToRefresh(resources.configuration)
    }

    /**
     *
     */
    override fun notifyTabViewInitialized() {
        logger.log(TAG, "Notify Tabs Initialized")
        tabsView?.tabsInitialized()
    }

    /**
     *
     */
    override fun updateSslState(sslState: SslState) {
        iBindingToolbarContent.addressBarInclude.searchSslStatus.setImageDrawable(createSslDrawableForState(sslState))

        if (!searchView.hasFocus()) {
            iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
        }
    }

    /**
     *
     */
    private fun ImageView.updateVisibilityForContent() {
        drawable?.let { visibility = VISIBLE } ?: run { visibility = GONE }
    }

    /**
     *
     */
    override fun tabChanged(tab: StyxView) {
        // SL: Is this being called way too many times?
        presenter?.tabChangeOccurred(tab)
        // SL: Putting this here to update toolbar background color was a bad idea
        // That somehow freezes the WebView after switching between a few tabs on F(x)tec Pro1 at least (Android 9)
        //initializePreferences()
    }

    /**
     *
     */
    private fun setupToolBarButtons() {
        // Manage back and forward buttons state
        tabsManager.currentTab?.apply {
            iBindingToolbarContent.buttonActionBack.apply {
                isEnabled = canGoBack()
                // Since we set buttons color ourselves we need this to show it is disabled
                alpha = if (isEnabled) 1.0f else 0.25f
            }

            iBindingToolbarContent.buttonActionForward.apply {
                isEnabled = canGoForward()
                // Since we set buttons color ourselves we need this to show it is disabled
                alpha = if (isEnabled) 1.0f else 0.25f
            }
        }
    }

    /**
     *
     */
    override fun removeTabView() {
        logger.log(TAG, "Remove the tab view")

        currentTabView.removeFromParent()
        currentTabView?.onFocusChangeListener = null
        currentTabView = null
    }

    /**
     * This function is central to browser tab switching.
     * It swaps our previous WebView with our new WebView.
     *
     * @param view Input is in fact a WebViewEx.
     */
    override fun setTabView(view: View) {
        if (currentTabView == view) {
            return
        }

        logger.log(TAG, "Setting the tab view")
        view.removeFromParent()
        currentTabView.removeFromParent()

        iBinding.contentFrame.resetTarget() // Needed to make it work together with swipe to refresh
        iBinding.contentFrame.addView(view, 0, MATCH_PARENT)
        view.requestFocus()

        // Remove existing focus change observer before we change our tab
        currentTabView?.onFocusChangeListener = null
        // Change our tab
        currentTabView = view
        // Close virtual keyboard if we loose focus
        currentTabView.onFocusLost { inputMethodManager.hideSoftInputFromWindow(iBinding.uiLayout.windowToken, 0) }
        showActionBar()

        // Make sure current tab is visible in tab list
        scrollToCurrentTab()
    }

    /**
     *
     */
    override fun showBlockedLocalFileDialog(onPositiveClick: Function0<Unit>) {
        MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_blocked_local)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_open) { _, _ -> onPositiveClick.invoke() }
            .resizeAndShow()
    }

    override fun showSnackbar(@StringRes resource: Int) = snackbar(resource, if (userPreferences.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)

    /**
     *
     */
    override fun tabCloseClicked(position: Int) {
        presenter?.deleteTab(position)
    }

    /**
     *
     */
    override fun tabClicked(position: Int) {
        // Switch tab
        presenter?.tabChanged(position)
        // Keep the drawer open while the tab change animation in running
        // Has the added advantage that closing of the drawer itself should be smoother as the webview had a bit of time to load
        mainHandler.postDelayed({ closePanels(null) }, 350)
    }

    /**
     * This is the callback from 'new tab' button on page drawer
     */
    override fun newTabButtonClicked() {
        // First close drawer
        closePanels(null)
        // Then slightly delay page loading to give enough time for the drawer to close without stutter
        mainHandler.postDelayed({
            presenter?.newTab(
                    homePageInitializer,
                    true
            )
        }, 300)
    }

    /**
     *
     */
    override fun newTabButtonLongClicked() {
        presenter?.recoverClosedTab()
    }

    /**
     *
     */
    @SuppressLint("CheckResult")
    override fun bookmarkButtonClicked() {
        val currentTab = tabsManager.currentTab
        val url = currentTab?.url
        val title = currentTab?.title
        if (url == null || title == null) {
            return
        }

        if (!url.isSpecialUrl()) {
            bookmarkManager.isBookmark(url)
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { boolean ->
                    if (boolean) {
                        deleteBookmark(title, url)
                    } else {
                        addBookmark(title, url)
                    }
                }
        }
    }

    /**
     *
     */
    override fun bookmarkItemClicked(entry: Bookmark.Entry) {
        if (userPreferences.bookmarkInNewTab) {
            presenter?.newTab(UrlInitializer(entry.url), true)
        } else {
            presenter?.loadUrlInCurrentView(entry.url)
        }
        // keep any jank from happening when the drawer is closed after the URL starts to load
        mainHandler.postDelayed({ closePanels(null) }, 150)
    }

    /**
     * Is that supposed to reload our history page if it changes?
     * Are we rebuilding our history page every time our history is changing?
     * Meaning every time we load a web page?
     * Thankfully not, apparently.
     */
    @SuppressLint("CheckResult")
    override fun handleHistoryChange() {
        historyPageFactory
            .buildPage()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(onSuccess = { tabsManager.currentTab?.reload() })
    }

    /**
     *
     */
    protected fun handleNewIntent(intent: Intent) {
        presenter?.onNewIntent(intent)
    }

    /**
     *
     */
    protected fun performExitCleanUp() {
        exitCleanup.cleanUp(tabsManager.currentTab?.webView, this)
    }

    /**
     *
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        logger.log(TAG, "onConfigurationChanged")

        setFullscreenIfNeeded(newConfig)
        setupTabBar()
        setupToolBar(newConfig)
        // Can't find a proper event to do that after the configuration changes were applied so we just delay it
        mainHandler.postDelayed({ setupToolBar(); setupPullToRefresh(newConfig) }, 300)
        popupMenu.dismiss() // As it wont update somehow
        // Make sure our drawers adjust accordingly
        iBinding.drawerLayout.requestLayout()
    }

    /**
     *
     */
    private fun initializeToolbarHeight(configuration: Configuration) =
        iBinding.uiLayout.doOnLayout {
            val toolbarSize = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                R.dimen.toolbar_height_portrait
            } else {
                R.dimen.toolbar_height_landscape
            }

            iBinding.toolbarInclude.toolbar.layoutParams.height = dimen(toolbarSize)
            iBinding.toolbarInclude.toolbar.minimumHeight = toolbarSize
            iBinding.toolbarInclude.toolbar.requestLayout()
        }

    /**
     *
     */
    override fun closeBrowser() {
        currentTabView.removeFromParent()
        performExitCleanUp()
        finish()
    }

    /**
     *
     */
    override fun onPause() {
        super.onPause()
        logger.log(TAG, "onPause")
        tabsManager.pauseAll()

        // Dismiss any popup menu
        popupMenu.dismiss()
        sessionsMenu.dismiss()
    }

    /**
     *
     */
    override fun onBackPressed() {
        doBackAction()
    }

    /**
     *
     */
    private fun doBackAction() {
        val currentTab = tabsManager.currentTab
        if (showingTabs()) {
            closePanelTabs()
        } else if (showingBookmarks()) {
            bookmarksView?.navigateBack()
        } else {
            if (currentTab != null) {
                logger.log(TAG, "onBackPressed")
                if (searchView.hasFocus()) {
                    currentTab.requestFocus()
                } else if (currentTab.canGoBack()) {
                    if (!currentTab.isShown) {
                        onHideCustomView()
                    } else {
                        if (isToolBarVisible()) {
                            currentTab.goBack()
                        } else {
                            showActionBar()
                        }
                    }
                } else {
                    if (customView != null || customViewCallback != null) {
                        onHideCustomView()
                    } else {
                        if (isToolBarVisible()) {
                            presenter?.deleteTab(tabsManager.positionOf(currentTab))
                        } else {
                            showActionBar()
                        }
                    }
                }
            } else {
                logger.log(TAG, "This shouldn't happen ever")
                super.onBackPressed()
            }
        }

    }

    /**
     *
     */
    protected fun saveOpenTabsIfNeeded() {
        if (userPreferences.restoreTabsOnStartup) {
            tabsManager.saveState()
        }
        else {
            tabsManager.clearSavedState()
        }
    }

    /**
     * Amazingly this is not called when closing our app from Task list.
     * See: https://developer.android.com/reference/android/app/Activity.html#onDestroy()
     */
    override fun onDestroy() {
        logger.log(TAG, "onDestroy")

        queue.cancelAll(TAG)

        incognitoNotification?.hide()

        mainHandler.removeCallbacksAndMessages(null)

        presenter?.shutdown()

        super.onDestroy()
    }

    /**
     *
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        tabsManager.shutdown()
    }

    /**
     *
     */
    override fun onResume() {
        super.onResume()
        logger.log(TAG, "onResume")
        // Check if some settings changes require application restart
        if (swapBookmarksAndTabs != userPreferences.bookmarksAndTabsSwapped
                || showCloseTabButton != userPreferences.showCloseTabButton
        ) {
            restart()
        }

        if (userPreferences.lockedDrawers) {
            lockDrawers()
        }
        else {
            unlockDrawers()
        }

        if (userPreferences.bookmarksChanged) {
            handleBookmarksChange()
            userPreferences.bookmarksChanged = false
        }

        if (userPreferences.incognito) {
            WebUtils.clearHistory(this, historyModel, databaseScheduler)
            WebUtils.clearCookies()
        }

        suggestionsAdapter?.let {
            it.refreshPreferences()
            it.refreshBookmarks()
        }
        tabsManager.resumeAll()
        initializePreferences()

        if (!setupTabBar()) {
            // use Bottom sheets settings could have changed
            addTabsViewToParent()
        }

        setupBookmarksView()
        setupToolBar(resources.configuration)
        mainHandler.postDelayed({ setupToolBar() }, 500)
        setupPullToRefresh(resources.configuration)

        // We think that's needed in case there was a rotation while in the background
        iBinding.drawerLayout.requestLayout()

        //intent?.let {logger.log(TAG, it.toString())}

        handleBookmarksChange()
    }

    /**
     * We need to make sure bookmarks are shown at the right place
     * Potentially moving them from the bottom sheets back to the drawers
     */
    private fun setupBookmarksView() {
        if (userPreferences.useBottomSheets) {
            createBookmarksDialog()
        } else {
            bookmarksView.removeFromParent()
            getBookmarksContainer().addView(bookmarksView)
        }

    }

    /**
     * searches the web for the query fixing any and all problems with the input
     * checks if it is a search, url, etc.
     */
    private fun searchTheWeb(query: String) {
        val currentTab = tabsManager.currentTab
        if (query.isEmpty()) {
            return
        }
        val searchUrl = "$searchText$QUERY_PLACE_HOLDER"

        val (url, isSearch) = smartUrlFilter(query.trim(), true, searchUrl)

        if ((userPreferences.searchInNewTab && isSearch) or (userPreferences.urlInNewTab && !isSearch)) {
            // Create a new tab according to user preference
            // That's also done in LightningView.loadURL
            when {
                url.isHomeUri() -> {
                    presenter?.newTab(homePageInitializer, true)
                }
                url.isBookmarkUri() -> {
                    presenter?.newTab(bookmarkPageInitializer, true)
                }
                url.isHistoryUri() -> {
                    presenter?.newTab(historyPageInitializer, true)
                }
                else -> {
                    presenter?.newTab(UrlInitializer(url), true)
                }
            }
        }
        else if (currentTab != null) {
            // User don't want us the create a new tab
            currentTab.stopLoading()
            presenter?.loadUrlInCurrentView(url)
        }
    }

    /**
     *
     */
    private fun setStatusBarColor(color: Int, darkIcons: Boolean) {

        // You don't want this as it somehow prevents smooth transition of tool bar when opening drawer
        //window.statusBarColor = R.color.transparent
        backgroundDrawable.color = color
        window.setBackgroundDrawable(backgroundDrawable)
        // That if statement is preventing us to change the icons color while a drawer is showing
        // That's typically the case when user open a drawer before the HTML meta theme color was delivered
        //if (!tabsDialog.isShowing && !bookmarksDialog.isShowing)
        if (drawerClosing || !drawerOpened) // Do not update icons color if drawer is opened
        {
            // Make sure the status bar icons are still readable
            window.setStatusBarIconsColor(darkIcons && !userPreferences.useBlackStatusBar)
        }
    }

    /**
     *
     */
    private fun applyToolbarColor(color: Int) {
        // Workout a foreground color that will be working with our background color
        currentToolBarTextColor = foregroundColorFromBackgroundColor(color)
        // Change search view text color
        searchView.setTextColor(currentToolBarTextColor)
        searchView.setHintTextColor(DrawableUtils.mixColor(0.5f, currentToolBarTextColor, color))
        // Change tab counter color
        iBindingToolbarContent.tabsButton.apply {
            textColor = currentToolBarTextColor
            invalidate()
        }
        // Change tool bar home button color, needed when using desktop style tabs
        iBindingToolbarContent.homeButton.setColorFilter(currentToolBarTextColor)
        iBindingToolbarContent.buttonActionBack.setColorFilter(currentToolBarTextColor)
        iBindingToolbarContent.buttonActionForward.setColorFilter(currentToolBarTextColor)

        if (userPreferences.navbar) {
            iBindingToolbarContent.buttonActionBack.isVisible = false
            iBindingToolbarContent.buttonActionForward.isVisible = false
        }

        // Needed to delay that as otherwise disabled alpha state didn't get applied
        mainHandler.postDelayed({ setupToolBarButtons() }, 500)

        // Change reload icon color
        // setMenuItemColor(R.id.action_reload, currentToolBarTextColor)
        // SSL status icon color
        iBindingToolbarContent.addressBarInclude.searchSslStatus.setColorFilter(currentToolBarTextColor)
        // Toolbar buttons filter
        iBindingToolbarContent.buttonMore.setColorFilter(currentToolBarTextColor)
        iBindingToolbarContent.buttonReload.setColorFilter(currentToolBarTextColor)

        // Pull to refresh spinner color also follow current theme
        iBinding.contentFrame.setProgressBackgroundColorSchemeColor(color)
        iBinding.contentFrame.setColorSchemeColors(currentToolBarTextColor)

        // Color also applies to the following backgrounds as they show during tool bar show/hide animation
        iBinding.uiLayout.setBackgroundColor(color)
        iBinding.contentFrame.setBackgroundColor(color)

        val webViewEx: WebViewEx? = currentTabView as? WebViewEx

        webViewEx?.let {
            // Now also set WebView background color otherwise it is just white and we don't want that.
            // This one is going to be a problem as it will break some websites such as bbc.com.
            // Make sure we reset our background color after page load, thanks bbc.com and bbc.com/news for not defining background color.
            if (iBinding.toolbarInclude.progressView.progress >= 1
                // Don't reset background color back to white on empty urls, that prevents displaying large empty white pages and blinding users in dark mode.
                // When opening some download links a tab is spawned first with the download URL and later that URL is set back to null.
                // Luckily our delayed call and the absence of invalidate prevents a flicker to white screen.
                && !webViewEx.url.isNullOrBlank()) {
                // We delay that to avoid some web sites including default startup page to flash white on app startup
                mainHandler.removeCallbacks(resetBackgroundColorRunnable)
                resetBackgroundColorRunnable = Runnable {
                    webViewEx.setBackgroundColor(Color.WHITE)
                    // We do not want to apply that color on the spot though.
                    // It does not make sense anyway since it is a delayed call.
                    // It also still causes a flicker notably when a tab is spawned by a download link.
                    //webViewEx.invalidate()
                }
                mainHandler.postDelayed(resetBackgroundColorRunnable, 100)
            } else {
                mainHandler.removeCallbacks(resetBackgroundColorRunnable)
                webViewEx.setBackgroundColor(color)
                // Make sure that color is applied on the spot for earlier color change when loading tabs
                webViewEx.invalidate()
            }
        }

        // No animation for now
        // Toolbar background color
        iBinding.toolbarInclude.toolbarLayout.setBackgroundColor(color)
        iBinding.toolbarInclude.progressView.mProgressColor = color
        // Search text field color
        setSearchBarColors(color)

        // Progress bar background color
        DrawableUtils.mixColor(0.5f, color, Color.WHITE).let {
            // Set progress bar background color making sure it isn't too bright
            // That's notably making it more visible on lequipe.fr and bbc.com/sport
            // We hope this is going to work with most white themed website too
            if (ColorUtils.calculateLuminance(it)>0.75) {
                iBinding.toolbarInclude.progressView.setBackgroundColor(Color.BLACK)
            }
            else {
                iBinding.toolbarInclude.progressView.setBackgroundColor(it)
            }
        }

        // Then the color of the status bar itself
        setStatusBarColor(color, currentToolBarTextColor == Color.BLACK)

        // Remove that if ever we re-enable color animation below
        currentUiColor = color
        // Needed for current tab color update in desktop style tabs
        tabsView?.tabChanged(tabsManager.indexOfCurrentTab())

        /*
        // Define our color animation
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                val animatedColor = DrawableUtils.mixColor(interpolatedTime, currentUiColor, color)
                if (shouldverticalTabBar) {
                    backgroundDrawable.color = animatedColor
                    mainHandler.post { window.setBackgroundDrawable(backgroundDrawable) }
                } else {
                    tabBackground?.tint(animatedColor)
                }
                currentUiColor = animatedColor
                toolbar_layout.setBackgroundColor(animatedColor)
                searchBackground?.background?.tint(
                        // Set search background a little lighter
                        // SL: See also Utils.mixTwoColors, why do we have those two functions?
                        getSearchBarColor(animatedColor)
                )
            }
        }
        animation.duration = 300
        toolbar_layout.startAnimation(animation)

         */

    }

    /**
     * Animates the color of the toolbar from one color to another. Optionally animates
     * the color of the tab background, for use when the tabs are displayed on the top
     * of the screen.
     *
     * @param favicon the Bitmap to extract the color from
     * @param color HTML meta theme color. Color.TRANSPARENT if not available.
     * @param tabBackground the optional LinearLayout to color
     */
    override fun changeToolbarBackground(favicon: Bitmap?, color: Int, tabBackground: Drawable?) {

        val defaultColor = primaryColor

        if (!isColorMode()) {
            // Put back the theme color then
            applyToolbarColor(defaultColor)
        }
        else if (color != Color.TRANSPARENT
                // Do not apply meta color if forced dark mode
                && tabsManager.currentTab?.darkMode != true)
        {
            // We have a meta theme color specified in our page HTML, use it
            applyToolbarColor(color)
        }
        else if (favicon==null
                // Use default color if forced dark mode
                || tabsManager.currentTab?.darkMode == true)
        {
            // No HTML meta theme color and no favicon, use app theme color then
            applyToolbarColor(defaultColor)
        }
        else {
            Palette.from(favicon).generate { palette ->
                // OR with opaque black to remove transparency glitches
                val colors = Color.BLACK or (palette?.getVibrantColor(defaultColor) ?: defaultColor)
                applyToolbarColor(colors)
            }
        }
    }

    /**
     * Set our search bar color for focused and non focused state
     */
    private fun setSearchBarColors(aColor: Int) {
        iBindingToolbarContent.addressBarInclude.root.apply {
            val stateListDrawable = background as StateListDrawable
            // Order may matter depending of states declared in our background drawable
            // See: [R.drawable.card_bg_elevate]
            stateListDrawable.drawableForState(android.R.attr.state_focused).tint(ThemeUtils.getSearchBarFocusedColor(aColor))
            stateListDrawable.drawableForState(android.R.attr.state_enabled).tint(ThemeUtils.getSearchBarColor(aColor))
        }
    }

    @ColorInt
    override fun getUiColor(): Int = currentUiColor

    /**
     *
     */
    override fun updateUrl(url: String?, isLoading: Boolean) {
        if (url == null || searchView.hasFocus()) {
            return
        }
        val currentTab = tabsManager.currentTab
        bookmarksView?.handleUpdatedUrl(url)

        val currentTitle = currentTab?.title

        searchView.setText(searchBoxModel.getDisplayContent(url, currentTitle, isLoading))
    }

    /**
     *
     */
    override fun updateTabNumber(number: Int) {
        iBindingToolbarContent.tabsButton.updateCount(number)
    }

    /**
     *
     */
    override fun updateProgress(progress: Int) {
        setIsLoading(progress < 100)
        iBinding.toolbarInclude.progressView.progress = progress
    }

    /**
     *
     */
    protected fun addItemToHistory(title: String?, url: String) {
        if (url.isSpecialUrl()) {
            return
        }

        historyModel.visitHistoryEntry(url, title)
            .subscribeOn(databaseScheduler)
            .subscribe()
    }

    /**
     * method to generate search suggestions for the AutoCompleteTextView from
     * previously searched URLs
     */
    private fun initializeSearchSuggestions(getUrl: AutoCompleteTextView) {
        suggestionsAdapter = SuggestionsAdapter(this, isIncognito())
        suggestionsAdapter?.onSuggestionInsertClick = {
            if (it is SearchSuggestion) {
                getUrl.setText(it.title)
                getUrl.setSelection(it.title.length)
            } else {
                getUrl.setText(it.url)
                getUrl.setSelection(it.url.length)
            }
        }
        getUrl.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            doSearchSuggestionAction(getUrl, position)
        }
        getUrl.setAdapter(suggestionsAdapter)
    }

    /**
     *
     */
    private fun doSearchSuggestionAction(getUrl: AutoCompleteTextView, position: Int) {
        val url = when (val selection = suggestionsAdapter?.getItem(position) as WebPage) {
            is HistoryEntry,
            is Bookmark.Entry -> selection.url
            is SearchSuggestion -> selection.title
            else -> null
        } ?: return
        getUrl.setText(url)
        searchTheWeb(url)
        inputMethodManager.hideSoftInputFromWindow(getUrl.windowToken, 0)
        presenter?.onAutoCompleteItemPressed()
    }

    /**
     * function that opens the HTML history page in the browser
     */
    private fun openHistory() {
        presenter?.newTab(
                historyPageInitializer,
                true
        )
    }

    /**
     * Display downloads folder one way or another
     */
    private fun openDownloads() {
        startActivity(Utils.getIntentForDownloads(this, userPreferences.downloadDirectory))
        // Our built-in downloads list did not display downloaded items properly
        // Not sure why, consider fixing it or just removing it altogether at some point
        //presenter?.newTab(downloadPageInitializer,true)
    }

    /**
     *
     */
    private fun showingBookmarks() : Boolean {
        return bookmarksDialog.isShowing || iBinding.drawerLayout.isDrawerOpen(getBookmarkDrawer())
    }

    /**
     *
     */
    private fun showingTabs() : Boolean {
        return tabsDialog.isShowing || iBinding.drawerLayout.isDrawerOpen(getTabDrawer())
    }

    /**
     * helper function that opens the bookmark drawer
     */
    private fun openBookmarks() {
        if (showingTabs()) {
            closePanelTabs()
        }
        if (userPreferences.useBottomSheets) {
            //createBookmarksDialog()
            bookmarksDialog.show()

            // See: https://github.com/material-components/material-components-android/issues/2165
            mainHandler.postDelayed({
                bookmarksDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }, 100)
        } else {
            // Define what to do once our drawer it opened
            //iBinding.drawerLayout.onceOnDrawerOpened {
            iBinding.drawerLayout.findViewById<RecyclerView>(R.id.list_bookmarks)?.apply {
            // Focus first item in our list
            findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
            //}
            // Open bookmarks drawer
            iBinding.drawerLayout.openDrawer(getBookmarkDrawer())
        }
    }

    /**
     *
     */
    private fun toggleBookmarks() {
        if (showingBookmarks()) {
            closePanelBookmarks()
        } else {
            openBookmarks()
        }
    }

    /**
     * Open our tab list, works for both drawers and bottom sheets.
     */
    private fun openTabs() {
        if (showingBookmarks()) {
            closePanelBookmarks()
        }

        // Loose focus on current tab web page
        // Actually this was causing our search field to gain focus on HTC One M8 - Android 6
        // currentTabView?.clearFocus()
        // That's needed for focus issue when opening with tap on button
        val tabListView = (tabsView as ViewGroup).findViewById<RecyclerView>(R.id.tabs_list)
        tabListView?.requestFocus()

        if (userPreferences.useBottomSheets) {
            //createTabsDialog()
            tabsDialog.show()

            // See: https://github.com/material-components/material-components-android/issues/2165
            mainHandler.postDelayed({
                tabsDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }, 100)
        } else {
            // Open our tab list drawer
            iBinding.drawerLayout.openDrawer(getTabDrawer())
            //iBinding.drawerLayout.onceOnDrawerOpened {
            // Looks like we can do that without delays for drawers
            scrollToCurrentTab()
            //}
        }

    }

    /**
     * Scroll to current tab.
     */
    private fun scrollToCurrentTab() {

        /*if (userPreferences.useBottomSheets && tabsView is TabsDrawerView && !(tabsDialog.isShowing && tabsDialog.behavior.state == BottomSheetBehavior.STATE_EXPANDED)) {
            return
        }*/

        val tabListView = (tabsView as ViewGroup).findViewById<RecyclerView>(R.id.tabs_list)
        // Set focus
        // Find our recycler list view
        tabListView?.apply {
            // Get current tab index and layout manager
            val index = tabsManager.indexOfCurrentTab()
            val lm = layoutManager as LinearLayoutManager
            // Check if current item is currently visible
            if (lm.findFirstCompletelyVisibleItemPosition() <= index && index <= lm.findLastCompletelyVisibleItemPosition()) {
                // We don't need to scroll as current item is already visible
                // Just focus our current item then for best keyboard navigation experience
                findViewHolderForAdapterPosition(tabsManager.indexOfCurrentTab())?.itemView?.requestFocus()
            } else {
                // Our current item is not completely visible, we need to scroll then
                // Once scroll is complete we will focus our current item
                onceOnScrollStateIdle { findViewHolderForAdapterPosition(tabsManager.indexOfCurrentTab())?.itemView?.requestFocus() }
                // Trigger scroll
                smoothScrollToPosition(index)
            }
        }

    }

    /**
     * Toggle tab list visibility
     */
    private fun toggleTabs() {
        if (showingTabs()) {
            closePanelTabs()
        } else {
            openTabs()
        }
    }

    /**
     * Toggle tab list visibility
     */
    private fun toggleSessions() {
        // isShowing always return false for some reason
        // Therefore toggle is not working however one can use Esc to close menu.
        if (sessionsMenu.isShowing) {
            sessionsMenu.dismiss()
        } else {
            showSessions()
        }
    }

    /**
     * This method closes any open drawer and executes the runnable after the drawers are closed.
     *
     * @param runnable an optional runnable to run after the drawers are closed.
     */
    protected fun closePanels(runnable: (() -> Unit)?) {
        closePanelTabs()
        closePanelBookmarks()
        runnable?.invoke()
    }

    /**
     *
     */
    override fun setForwardButtonEnabled(enabled: Boolean) {
        popupMenu.iBinding.menuShortcutForward.isEnabled = enabled
        tabsView?.setGoForwardEnabled(enabled)
    }

    /**
     *
     */
    override fun setBackButtonEnabled(enabled: Boolean) {
        popupMenu.iBinding.menuShortcutBack.isEnabled = enabled
        tabsView?.setGoBackEnabled(enabled)
    }

    /**
     * opens a file chooser
     * param ValueCallback is the message from the WebView indicating a file chooser
     * should be opened
     */
    @Suppress("DEPRECATION")
    override fun openFileChooser(uploadMsg: ValueCallback<Uri>) {
        uploadMessageCallback = uploadMsg
        startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, getString(R.string.title_file_chooser)), FILE_CHOOSER_REQUEST_CODE)
    }

    /**
     * used to allow uploading into the browser
     */
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val results: Array<Uri>? = if (resultCode == RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    cameraPhotoPath?.let { arrayOf(it.toUri()) }
                } else {
                    data.dataString?.let { arrayOf(it.toUri()) }
                }
            } else {
                null
            }

            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     *
     */
    @Suppress("DEPRECATION")
    override fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>) {
        this.filePathCallback?.onReceiveValue(null)
        this.filePathCallback = filePathCallback

        // Create the File where the photo should go
        val intentArray: Array<Intent> = try {
            arrayOf(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra("PhotoPath", cameraPhotoPath)
                putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(Utils.createImageFile().also { file ->
                            cameraPhotoPath = "file:${file.absolutePath}"
                        })
                )
            })
        } catch (ex: IOException) {
            // Error occurred while creating the File
            logger.log(TAG, "Unable to create Image File", ex)
            emptyArray()
        }

        startActivityForResult(Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            })
            putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
        }, FILE_CHOOSER_REQUEST_CODE)
    }

    /**
     *
     */
    override fun onShowCustomView(view: View, callback: CustomViewCallback, requestedOrientation: Int) {
        val currentTab = tabsManager.currentTab
        if (customView != null) {
            try {
                callback.onCustomViewHidden()
            } catch (e: Exception) {
                logger.log(TAG, "Error hiding custom view", e)
            }

            return
        }

        try {
            view.keepScreenOn = true
        } catch (e: SecurityException) {
            logger.log(TAG, "WebView is not allowed to keep the screen on")
        }

        originalOrientation = getRequestedOrientation()
        customViewCallback = callback
        customView = view

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        val decorView = window.decorView as FrameLayout

        fullscreenContainerView = FrameLayout(this)
        fullscreenContainerView?.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
        if (view is FrameLayout) {
            val child = view.focusedChild
            if (child is VideoView) {
                videoView = child
                child.setOnErrorListener(VideoCompletionListener())
                child.setOnCompletionListener(VideoCompletionListener())
            }
        } else if (view is VideoView) {
            videoView = view
            view.setOnErrorListener(VideoCompletionListener())
            view.setOnCompletionListener(VideoCompletionListener())
        }
        decorView.addView(fullscreenContainerView, COVER_SCREEN_PARAMS)
        fullscreenContainerView?.addView(customView, COVER_SCREEN_PARAMS)
        decorView.requestLayout()
        setFullscreen(enabled = true, immersive = true)
        currentTab?.setVisibility(INVISIBLE)
    }

    /**
     *
     */
    override fun onHideCustomView() {
        val currentTab = tabsManager.currentTab
        if (customView == null || customViewCallback == null || currentTab == null) {
            if (customViewCallback != null) {
                try {
                    customViewCallback?.onCustomViewHidden()
                } catch (e: Exception) {
                    logger.log(TAG, "Error hiding custom view", e)
                }

                customViewCallback = null
            }
            return
        }
        logger.log(TAG, "onHideCustomView")
        currentTab.setVisibility(VISIBLE)
        currentTab.requestFocus()
        try {
            customView?.keepScreenOn = false
        } catch (e: SecurityException) {
            logger.log(TAG, "WebView is not allowed to keep the screen on")
        }

        setFullscreenIfNeeded(resources.configuration)
        if (fullscreenContainerView != null) {
            val parent = fullscreenContainerView?.parent as ViewGroup
            parent.removeView(fullscreenContainerView)
            fullscreenContainerView?.removeAllViews()
        }

        fullscreenContainerView = null
        customView = null

        logger.log(TAG, "VideoView is being stopped")
        videoView?.stopPlayback()
        videoView?.setOnErrorListener(null)
        videoView?.setOnCompletionListener(null)
        videoView = null

        try {
            customViewCallback?.onCustomViewHidden()
        } catch (e: Exception) {
            logger.log(TAG, "Error hiding custom view", e)
        }

        customViewCallback = null
        requestedOrientation = originalOrientation
    }

    /**
     *
     */
    private inner class VideoCompletionListener : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

        override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean = false

        override fun onCompletion(mp: MediaPlayer) = onHideCustomView()

    }

    /**
     *
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logger.log(TAG, "onWindowFocusChanged")
        if (hasFocus) {
            setFullscreen(hideStatusBar, isImmersiveMode)
        }
    }

    /**
     *
     */
    override fun onBackButtonPressed() {
        if (closeTabsPanelIfOpen()) {
            val currentTab = tabsManager.currentTab
            if (currentTab?.canGoBack() == true) {
                currentTab.goBack()
            } else if (currentTab != null) {
                tabsManager.let { presenter?.deleteTab(it.positionOf(currentTab)) }
            }
        } else closeBookmarksPanelIfOpen()
    }

    /**
     *
     */
    override fun onForwardButtonPressed() {
        val currentTab = tabsManager.currentTab
        if (currentTab?.canGoForward() == true) {
            currentTab.goForward()
            closePanels(null)
        }
    }

    /**
     *
     */
    override fun onHomeButtonPressed() {
        executeAction(R.id.menuShortcutHome)
    }

    /**
     *
     */
    @Suppress("DEPRECATION")
    private val fullScreenFlags = (SYSTEM_UI_FLAG_LAYOUT_STABLE
            or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or SYSTEM_UI_FLAG_FULLSCREEN
            or SYSTEM_UI_FLAG_IMMERSIVE_STICKY)


    /**
     * Hide the status bar according to orientation and user preferences
     */
    private fun setFullscreenIfNeeded(configuration: Configuration) {
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setFullscreen(userPreferences.hideStatusBarInPortrait, false)
        }
        else {
            setFullscreen(userPreferences.hideStatusBarInLandscape, false)
        }
    }

    private var statusBarHidden = false

    /**
     * This method sets whether or not the activity will display
     * in full-screen mode (i.e. the ActionBar will be hidden) and
     * whether or not immersive mode should be set. This is used to
     * set both parameters correctly as during a full-screen video,
     * both need to be set, but other-wise we leave it up to user
     * preference.
     *
     * @param enabled   true to enable full-screen, false otherwise
     * @param immersive true to enable immersive mode, false otherwise
     */
    @Suppress("DEPRECATION")
    private fun setFullscreen(enabled: Boolean, immersive: Boolean) {
        hideStatusBar = enabled
        isImmersiveMode = immersive
        val window = window
        val decor = window.decorView
        if (enabled) {
            if (immersive) {
                decor.systemUiVisibility = decor.systemUiVisibility or fullScreenFlags
            } else {
                decor.systemUiVisibility = decor.systemUiVisibility and fullScreenFlags.inv()
            }
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN)
            statusBarHidden = true
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decor.systemUiVisibility = decor.systemUiVisibility and fullScreenFlags.inv()
            statusBarHidden = false
        }
    }

    /**
     * This method handles the JavaScript callback to create a new tab.
     * Basically this handles the event that JavaScript needs to create
     * a popup.
     *
     * @param resultMsg the transport message used to send the URL to
     * the newly created WebView.
     */
    override fun onCreateWindow(resultMsg: Message) {
        presenter?.newTab(ResultMessageInitializer(resultMsg), true)
    }

    /**
     * Closes the specified [StyxView]. This implements
     * the JavaScript callback that asks the tab to close itself and
     * is especially helpful when a page creates a redirect and does
     * not need the tab to stay open any longer.
     *
     * @param tab the StyxView to close, delete it.
     */
    override fun onCloseWindow(tab: StyxView) {
        presenter?.deleteTab(tabsManager.positionOf(tab))
    }

    /**
     * Hide the ActionBar if we are in full-screen
     */
    override fun hideActionBar() {
        if (isFullScreen) {
            doHideToolBar()
        }
    }

    /**
     * Display the ActionBar if it was hidden
     */
    override fun showActionBar() {
        logger.log(TAG, "showActionBar")
        iBinding.toolbarInclude.toolbarLayout.visibility = VISIBLE
    }

    var webView: WebView? = null
        private set

    /**
     *
     */
    private fun doHideToolBar() {
        val currentView = tabsManager.currentTab ?: return
        val url = currentView.url
        if (url.isSpecialUrl()) {
            isToolBarVisible()
        } else {
            iBinding.toolbarInclude.toolbarLayout.visibility = GONE
        }
    }

    private fun isToolBarVisible() = iBinding.toolbarInclude.toolbarLayout.visibility == VISIBLE

    private fun toggleToolBar() : Boolean
    {
        return if (isToolBarVisible()) {
            doHideToolBar()
            currentTabView?.requestFocus()
            false
        } else {
            showActionBar()
            iBindingToolbarContent.buttonMore.requestFocus()
            true
        }
    }

    /**
     *
     */
    override fun handleBookmarksChange() {
        val currentTab = tabsManager.currentTab
        if (currentTab != null && currentTab.url.isBookmarkUrl()) {
            currentTab.loadBookmarkPage()
        }
        currentTab?.url?.let { bookmarksView?.handleUpdatedUrl(it) }
        suggestionsAdapter?.refreshBookmarks()
    }

    /**
     *
     */
    override fun handleDownloadDeleted() {
        val currentTab = tabsManager.currentTab
        if (currentTab != null && currentTab.url.isDownloadsUrl()) {
            currentTab.loadDownloadsPage()
        }
        if (currentTab != null) {
            bookmarksView?.handleUpdatedUrl(currentTab.url)
        }
    }

    /**
     *
     */
    override fun handleBookmarkDeleted(bookmark: Bookmark) {
        bookmarksView?.handleBookmarkDeleted(bookmark)
        handleBookmarksChange()
    }

    /**
     *
     */
    override fun handleNewTab(newTabType: StyxDialogBuilder.NewTab, url: String) {
        val urlInitializer = UrlInitializer(url)
        when (newTabType) {
            StyxDialogBuilder.NewTab.FOREGROUND -> presenter?.newTab(urlInitializer, true)
            StyxDialogBuilder.NewTab.BACKGROUND -> presenter?.newTab(urlInitializer, false)
            StyxDialogBuilder.NewTab.INCOGNITO -> {
                closePanels { }
                val intent = IncognitoActivity.createIntent(this, url.toUri())
                startActivity(intent)
                overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)
            }
        }
    }

    /**
     * This method lets the search bar know that the page is currently loading
     * and that it should display the stop icon to indicate to the user that
     * pressing it stops the page from loading
     * That should also animate the transition I guess.
     */
    private fun setIsLoading(isLoading: Boolean) {
        if (!searchView.hasFocus()) {
            iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
        }

        iBindingToolbarContent.buttonReload.setImageResource(if (isLoading) R.drawable.round_clear_24 else R.drawable.round_refresh_24)

        setupPullToRefresh(resources.configuration)
    }


    /**
     * handle presses on the refresh icon in the search bar, if the page is
     * loading, stop the page, if it is done loading refresh the page.
     * See setIsFinishedLoading and setIsLoading for displaying the correct icon
     */
    private fun refreshOrStop() {
        val currentTab = tabsManager.currentTab
        if (currentTab != null) {
            if (currentTab.progress < 100) {
                currentTab.stopLoading()
            } else {
                currentTab.reload()
            }
        }
    }

    /**
     * Handle the click event for the views that are using
     * this class as a click listener. This method should
     * distinguish between the various views using their IDs.
     *
     * @param v the view that the user has clicked
     */
    override fun onClick(v: View) {
        val currentTab = tabsManager.currentTab ?: return
        when (v.id) {
            R.id.home_button -> currentTab.apply { requestFocus(); loadHomePage() }
            R.id.tabs_button -> openTabs()
            R.id.button_reload -> refreshOrStop()
            R.id.button_next -> findResult?.nextResult()
            R.id.button_back -> findResult?.previousResult()
            R.id.button_quit -> {
                findResult?.clearResults()
                findResult = null
                findViewById<View>(R.id.findInPageInclude).visibility = GONE
            }
            R.id.button_search -> {
                showFindInPageControls(findViewById<EditText>(R.id.search_query).text.toString())
                findResult = presenter?.findInPage(findViewById<EditText>(R.id.search_query).text.toString())
            }
        }
    }

    /**
     * Handle the callback that permissions requested have been granted or not.
     * This method should act upon the results of the permissions request.
     *
     * @param requestCode  the request code sent when initially making the request
     * @param permissions  the array of the permissions that was requested
     * @param grantResults the results of the permissions requests that provides
     * information on whether the request was granted or not
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        PermissionsManager.getInstance().notifyPermissionsChange(permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * If the drawer is open, close it and return true. Return false otherwise.
     */
    private fun closeTabsPanelIfOpen(): Boolean =
            if (showingTabs()) {
                closePanelTabs()
            true
        } else {
            false
        }

    /**
     * If the drawer is open, close it and return true. Return false otherwise.
     */
    private fun closeBookmarksPanelIfOpen(): Boolean =
            if (showingBookmarks()) {
                closePanelBookmarks()
                true
            } else {
                false
            }

    private var iLastTouchUpPosition: Point = Point()

    /**
     *
     */
    override fun dispatchTouchEvent(anEvent: MotionEvent?): Boolean {

        when (anEvent?.action) {
            MotionEvent.ACTION_UP -> {
                iLastTouchUpPosition.x = anEvent.x.toInt()
                iLastTouchUpPosition.y = anEvent.y.toInt()

            }
        }
        return super.dispatchTouchEvent(anEvent)
    }

    /**
     * Check for update on github
     */
    private fun checkForUpdates(context: Context) {
        val url = getString(R.string.github_update_check_url)
        val request = StringRequest(Request.Method.GET, url, { reply ->
                val latestVersion = Gson().fromJson(reply, JsonObject::class.java).get("tag_name").asString
                val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                if (latestVersion != current) {
                    // We have an update available, tell our user about it
                    makeCSnackbar(
                        getString(R.string.app_name) + " " + latestVersion + " " + getString(R.string.update_available), 10000, if (userPreferences.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                        .setAction(R.string.show) {
                            val releaseurl = getString(R.string.url_app_home_page)
                            val i = Intent(Intent.ACTION_VIEW)
                            i.data = Uri.parse(releaseurl)
                            // Not sure that does anything
                            i.putExtra("SOURCE", "SELF")
                            startActivity(i)
                        }.show()
                }
        }, { error ->
            Log.w(TAG, "Update check failed", error)
        })

        request.tag = TAG
        queue.add(request)
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
     * Show the page tools dialog.
     */
    @SuppressLint("CutPasteId")
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun showPageToolsDialog(context: Context, userPreferences: UserPreferences) {
        val currentTab = tabsManager.currentTab ?: return
        val isAllowedAds = allowListModel.isUrlAllowedAds(currentTab.url)
        val whitelistString = if (isAllowedAds) {
            R.string.dialog_adblock_enable_for_site
        } else {
            R.string.dialog_adblock_disable_for_site
        }
        val arrayOfURLs = userPreferences.javaScriptBlocked
        val strgs: Array<String> = if (arrayOfURLs.contains(", ")) {
            arrayOfURLs.split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else {
            arrayOfURLs.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        }
        val jsEnabledString = if (userPreferences.javaScriptChoice == JavaScriptChoice.BLACKLIST && !stringContainsItemFromList(currentTab.url, strgs) || userPreferences.javaScriptChoice == JavaScriptChoice.WHITELIST && stringContainsItemFromList(currentTab.url, strgs)) {
            R.string.allow_javascript
        } else{
            R.string.blocked_javascript
        }

        BrowserDialog.showWithIcons(context, context.getString(R.string.dialog_tools_title),
            DialogItem(
                icon = context.drawable(R.drawable.outline_remove_circle_outline_24),
                colorTint = context.attrColor(R.attr.colorPrimary).takeIf { isAllowedAds },
                title = whitelistString
            ) {
                if (isAllowedAds) {
                    allowListModel.removeUrlFromAllowList(currentTab.url)
                } else {
                    allowListModel.addUrlToAllowList(currentTab.url)
                }
                tabsManager.currentTab?.reload()
            },
            DialogItem(
                icon = context.drawable(R.drawable.ic_baseline_code_24),
                title = R.string.page_source
            ) {
                currentTab.webView?.evaluateJavascript("""(function() {
                        return "<html>" + document.getElementsByTagName('html')[0].innerHTML + "</html>";
                     })()""".trimMargin()) {
                    // Hacky workaround for weird WebView encoding bug
                    var name = it?.replace("\\u003C", "<")
                    name = name?.replace("\\n", System.getProperty("line.separator").toString())
                    name = name?.replace("\\t", "")
                    name = name?.replace("\\\"", "\"")
                    name = name?.substring(1, name.length - 1)

                    val builder = MaterialAlertDialogBuilder(context)
                    val inflater = this.layoutInflater
                    builder.setTitle(R.string.page_source)
                    val dialogLayout = inflater.inflate(R.layout.dialog_view_source, null)
                    val editText = dialogLayout.findViewById<CodeEditor>(R.id.dialog_multi_line)
                    editText.setText(name, 1)
                    builder.setView(dialogLayout)
                    builder.setNegativeButton(R.string.action_cancel) { _, _ -> }
                    builder.setPositiveButton(R.string.action_ok) { _, _ ->
                        editText.setText(editText.text?.toString()?.replace("\'", "\\\'"), 1)
                        currentTab.loadUrl("javascript:(function() { document.documentElement.innerHTML = '" + editText.text.toString() + "'; })()")
                    }
                    builder.show()
                }
            },
            DialogItem(
                icon= context.drawable(R.drawable.ic_script_add),
                title = R.string.inspect
            ){
                val builder = MaterialAlertDialogBuilder(context)
                val inflater = this.layoutInflater
                builder.setTitle(R.string.inspect)
                val dialogLayout = inflater.inflate(R.layout.dialog_code_editor, null)
                val codeView: CodeView = dialogLayout.findViewById(R.id.dialog_multi_line)
                codeView.text.toString()
                builder.setView(dialogLayout)
                builder.setNegativeButton(R.string.action_cancel) { _, _ -> }
                builder.setPositiveButton(R.string.action_ok) { _, _ -> currentTab.loadUrl("javascript:(function() {" + codeView.text.toString() + "})()") }
                builder.show()
            },
            DialogItem(
                icon = context.drawable(R.drawable.outline_script_text_key_outline),
                colorTint = context.attrColor(R.attr.colorPrimary).takeIf { userPreferences.javaScriptChoice == JavaScriptChoice.BLACKLIST && !stringContainsItemFromList(currentTab.url, strgs) || userPreferences.javaScriptChoice == JavaScriptChoice.WHITELIST && stringContainsItemFromList(currentTab.url, strgs) },
                title = jsEnabledString
            ) {
                val url = URL(currentTab.url)
                if (userPreferences.javaScriptChoice != JavaScriptChoice.NONE) {
                    if (!stringContainsItemFromList(currentTab.url, strgs)) {
                        if (userPreferences.javaScriptBlocked == "") {
                            userPreferences.javaScriptBlocked = url.host
                        } else {
                            userPreferences.javaScriptBlocked = userPreferences.javaScriptBlocked + ", " + url.host
                        }
                    } else {
                        if (!userPreferences.javaScriptBlocked.contains(", " + url.host)) {
                            userPreferences.javaScriptBlocked = userPreferences.javaScriptBlocked.replace(url.host, "")
                        } else {
                            userPreferences.javaScriptBlocked = userPreferences.javaScriptBlocked.replace(", " + url.host, "")
                        }
                    }
                } else {
                    userPreferences.javaScriptChoice = JavaScriptChoice.WHITELIST
                }
                tabsManager.currentTab?.reload()
                Handler(Looper.getMainLooper()).postDelayed({
                    tabsManager.currentTab?.reload()
                }, 250)
            },
            DialogItem(
                icon = context.drawable(R.drawable.cookie_outline),
                title = R.string.edit_cookies
            ) {

                val cookieManager = CookieManager.getInstance()
                if (cookieManager.getCookie(currentTab.url) != null) {
                    val builder = MaterialAlertDialogBuilder(context)
                    val inflater = this.layoutInflater
                    builder.setTitle(R.string.site_cookies)
                    val dialogLayout = inflater.inflate(R.layout.dialog_code_editor, null)
                    val codeView: CodeView = dialogLayout.findViewById(R.id.dialog_multi_line)
                    codeView.setText(cookieManager.getCookie(currentTab.url))
                    builder.setView(dialogLayout)
                    builder.setNegativeButton(R.string.action_cancel) { _, _ -> }
                    builder.setPositiveButton(R.string.action_ok) { _, _ ->
                        val cookiesList = codeView.text.toString().split(";")
                        cookiesList.forEach { item ->
                            CookieManager.getInstance().setCookie(currentTab.url, item)
                        }
                    }
                    builder.show()
                }

            }
        )

    }

    companion object {

        private const val TAG = "BrowserActivity"

        private const val FILE_CHOOSER_REQUEST_CODE = 1111

        // Constant
        private val MATCH_PARENT = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        private val COVER_SCREEN_PARAMS = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    }

}
