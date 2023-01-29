/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.di

import com.jamal2367.styx.browser.cleanup.DelegatingExitCleanup
import com.jamal2367.styx.browser.cleanup.ExitCleanup
import com.jamal2367.styx.database.adblock.UserRulesDatabase
import com.jamal2367.styx.database.adblock.UserRulesRepository
import com.jamal2367.styx.database.bookmark.BookmarkDatabase
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.database.downloads.DownloadsDatabase
import com.jamal2367.styx.database.downloads.DownloadsRepository
import com.jamal2367.styx.database.history.HistoryDatabase
import com.jamal2367.styx.database.history.HistoryRepository
import com.jamal2367.styx.ssl.SessionSslWarningPreferences
import com.jamal2367.styx.ssl.SslWarningPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependency injection module used to bind implementations to interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AppBindsModule {

    @Binds
    fun bindsExitCleanup(delegatingExitCleanup: DelegatingExitCleanup): ExitCleanup

    @Binds
    fun bindsBookmarkModel(bookmarkDatabase: BookmarkDatabase): BookmarkRepository

    @Binds
    fun bindsDownloadsModel(downloadsDatabase: DownloadsDatabase): DownloadsRepository

    @Binds
    fun bindsHistoryModel(historyDatabase: HistoryDatabase): HistoryRepository

    @Binds
    fun bindsSslWarningPreferences(sessionSslWarningPreferences: SessionSslWarningPreferences): SslWarningPreferences

    @Binds
    fun bindsAbpRulesRepository(apbRulesDatabase: UserRulesDatabase): UserRulesRepository
}
