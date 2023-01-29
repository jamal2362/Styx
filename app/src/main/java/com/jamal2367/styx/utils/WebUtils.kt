/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.utils

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import com.jamal2367.styx.database.history.HistoryRepository
import io.reactivex.Scheduler

/**
 * Copyright 8/4/2015 Anthony Restaino
 */
object WebUtils {
    fun clearCookies() {
        val c = CookieManager.getInstance()
        c.removeAllCookies(null)
    }

    fun clearWebStorage() {
        WebStorage.getInstance().deleteAllData()
    }

    fun clearHistory(
        context: Context,
        historyRepository: HistoryRepository,
        databaseScheduler: Scheduler,
    ) {
        historyRepository.deleteHistory()
            .subscribeOn(databaseScheduler)
            .subscribe()
        val webViewDatabase = WebViewDatabase.getInstance(context)
        webViewDatabase.clearHttpAuthUsernamePassword()
        context.cacheDir.deleteRecursively()
    }

    fun clearCache(context: Context) {
        context.cacheDir.deleteRecursively()
    }

}