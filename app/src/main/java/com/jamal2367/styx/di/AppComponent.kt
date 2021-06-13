package com.jamal2367.styx.di

import android.app.Application
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.ThemedActivity
import com.jamal2367.styx.adblock.AbpBlocker
import com.jamal2367.styx.adblock.BloomFilterAdBlocker
import com.jamal2367.styx.adblock.NoOpAdBlocker
import com.jamal2367.styx.browser.BrowserPopupMenu
import com.jamal2367.styx.browser.SearchBoxModel
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.browser.activity.ThemedBrowserActivity
import com.jamal2367.styx.browser.bookmarks.BookmarksAdapter
import com.jamal2367.styx.browser.bookmarks.BookmarksDrawerView
import com.jamal2367.styx.browser.sessions.SessionsPopupWindow
import com.jamal2367.styx.browser.tabs.TabsDrawerView
import com.jamal2367.styx.device.BuildInfo
import com.jamal2367.styx.dialog.StyxDialogBuilder
import com.jamal2367.styx.download.StyxDownloadListener
import com.jamal2367.styx.reading.ReadingActivity
import com.jamal2367.styx.search.SuggestionsAdapter
import com.jamal2367.styx.settings.activity.SettingsActivity
import com.jamal2367.styx.settings.activity.ThemedSettingsActivity
import com.jamal2367.styx.settings.fragment.*
import com.jamal2367.styx.view.StyxChromeClient
import com.jamal2367.styx.view.StyxView
import com.jamal2367.styx.view.StyxWebClient
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [(AppModule::class), (AppBindsModule::class)])
interface AppComponent {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: Application): Builder

        @BindsInstance
        fun buildInfo(buildInfo: BuildInfo): Builder

        fun build(): AppComponent
    }

    fun inject(activity: BrowserActivity)

    fun inject(fragment: ImportExportSettingsFragment)

    fun inject(builder: StyxDialogBuilder)

    fun inject(styxView: StyxView)

    fun inject(activity: ThemedBrowserActivity)

    fun inject(app: BrowserApp)

    fun inject(activity: ReadingActivity)

    fun inject(webClient: StyxWebClient)

    fun inject(activity: SettingsActivity)

    fun inject(activity: ThemedSettingsActivity)

    fun inject(listener: StyxDownloadListener)

    fun inject(fragment: PrivacySettingsFragment)

    fun inject(fragment: ExtensionsSettingsFragment)

    fun inject(suggestionsAdapter: SuggestionsAdapter)

    fun inject(chromeClient: StyxChromeClient)

    fun inject(searchBoxModel: SearchBoxModel)

    fun inject(generalSettingsFragment: GeneralSettingsFragment)

    fun inject(displaySettingsFragment: DisplaySettingsFragment)

    fun inject(adBlockSettingsFragment: AdBlockSettingsFragment)

    fun inject(aboutSettingsFragment: AboutSettingsFragment)

    fun inject(bookmarksView: BookmarksDrawerView)

    fun inject(popupMenu: BrowserPopupMenu)

    fun inject(popupMenu: SessionsPopupWindow)

    fun inject(appsSettingsFragment: AppsSettingsFragment)

    fun inject(themedActivity: ThemedActivity)

    fun inject(tabsDrawerView: TabsDrawerView)

    fun inject(bookmarksAdapter: BookmarksAdapter)

    fun provideBloomFilterAdBlocker(): BloomFilterAdBlocker

    fun provideAbpAdBlocker(): AbpBlocker

    fun provideNoOpAdBlocker(): NoOpAdBlocker

}
