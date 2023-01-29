package com.jamal2367.styx.di

import android.content.ClipboardManager
import android.content.SharedPreferences
import com.jamal2367.styx.adblock.AbpBlockerManager
import com.jamal2367.styx.adblock.AbpUserRules
import com.jamal2367.styx.adblock.NoOpAdBlocker
import com.jamal2367.styx.browser.TabsManager
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.database.history.HistoryRepository
import com.jamal2367.styx.dialog.StyxDialogBuilder
import com.jamal2367.styx.download.DownloadHandler
import com.jamal2367.styx.favicon.FaviconModel
import com.jamal2367.styx.html.homepage.HomePageFactory
import com.jamal2367.styx.js.InvertPage
import com.jamal2367.styx.js.SetMetaViewport
import com.jamal2367.styx.js.TextReflow
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.network.NetworkConnectivityModel
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.search.SearchEngineProvider
import com.jamal2367.styx.ssl.SslWarningPreferences
import com.jamal2367.styx.view.webrtc.WebRtcPermissionsModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.Scheduler


/**
 * Provide access to all our injectable classes.
 * Virtual fields can't resolve qualifiers for some reason.
 * Therefore we use functions where qualifiers are needed.
 *
 * Just add your class here if you need it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltEntryPoint {

    val bookmarkRepository: BookmarkRepository
    val userPreferences: UserPreferences
    @AppsPrefs
    fun appsSharedPreferences(): SharedPreferences
    val historyRepository: HistoryRepository
    @DatabaseScheduler
    fun databaseScheduler(): Scheduler
    @NetworkScheduler
    fun networkScheduler(): Scheduler
    @DiskScheduler
    fun diskScheduler(): Scheduler
    @MainScheduler
    fun mainScheduler(): Scheduler
    val searchEngineProvider: SearchEngineProvider
    val sslWarningPreferences: SslWarningPreferences
    val logger: Logger
    val textReflowJs: TextReflow
    val invertPageJs: InvertPage
    val setMetaViewport: SetMetaViewport
    val homePageFactory: HomePageFactory
    val abpBlockerManager: AbpBlockerManager
    val noopBlocker: NoOpAdBlocker
    val dialogBuilder: StyxDialogBuilder
    val networkConnectivityModel: NetworkConnectivityModel
    val faviconModel: FaviconModel
    val webRtcPermissionsModel: WebRtcPermissionsModel
    val abpUserRules: AbpUserRules
    val downloadHandler: DownloadHandler
    var tabsManager: TabsManager
    var clipboardManager: ClipboardManager
}