/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.di

import android.app.Application
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.anthonycr.mezzanine.MezzanineGenerator
import com.jamal2367.styx.BuildConfig
import com.jamal2367.styx.device.BuildInfo
import com.jamal2367.styx.device.BuildType
import com.jamal2367.styx.html.ListPageReader
import com.jamal2367.styx.html.bookmark.BookmarkPageReader
import com.jamal2367.styx.html.homepage.HomePageReader
import com.jamal2367.styx.html.incognito.IncognitoPageReader
import com.jamal2367.styx.js.InvertPage
import com.jamal2367.styx.js.SetMetaViewport
import com.jamal2367.styx.js.TextReflow
import com.jamal2367.styx.js.ThemeColor
import com.jamal2367.styx.log.AndroidLogger
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.log.NoOpLogger
import com.jamal2367.styx.search.suggestions.RequestFactory
import com.jamal2367.styx.utils.FileUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    fun provideBuildInfo(): BuildInfo = BuildInfo(when {
        BuildConfig.DEBUG -> BuildType.DEBUG
        else -> BuildType.RELEASE
    })

    @Provides
    @MainHandler
    fun provideMainHandler() = Handler(Looper.getMainLooper())

    @Provides
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    fun provideResources(application: Application): Resources = application.resources

    @Provides
    @UserPrefs
    @Singleton
    fun provideUserSharedPreferences(application: Application): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(application.applicationContext)

    @Provides
    @AdBlockPrefs
    fun provideAdBlockPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences("content_blocker_settings", 0)

    @Provides
    @AppsPrefs
    fun provideAppsPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences("apps_settings", 0)

    @Provides
    fun providesAssetManager(application: Application): AssetManager = application.assets

    @Provides
    fun providesClipboardManager(application: Application) =
        application.getSystemService<ClipboardManager>()!!

    @Provides
    fun providesInputMethodManager(application: Application) =
        application.getSystemService<InputMethodManager>()!!

    @Provides
    fun providesDownloadManager(application: Application) =
        application.getSystemService<DownloadManager>()!!

    @Provides
    fun providesConnectivityManager(application: Application) =
        application.getSystemService<ConnectivityManager>()!!

    @Provides
    fun providesNotificationManager(application: Application) =
        application.getSystemService<NotificationManager>()!!

    @Provides
    fun providesWindowManager(application: Application) =
        application.getSystemService<WindowManager>()!!

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    @Provides
    fun providesShortcutManager(application: Application) =
        application.getSystemService<ShortcutManager>()!!

    @Provides
    @DatabaseScheduler
    @Singleton
    fun providesIoThread(): Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Provides
    @DiskScheduler
    @Singleton
    fun providesDiskThread(): Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Provides
    @NetworkScheduler
    @Singleton
    fun providesNetworkThread(): Scheduler =
        Schedulers.from(ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, LinkedBlockingDeque()))

    @Provides
    @MainScheduler
    @Singleton
    fun providesMainThread(): Scheduler = AndroidSchedulers.mainThread()

    @Singleton
    @Provides
    fun providesSuggestionsCacheControl(): CacheControl =
        CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build()

    @Singleton
    @Provides
    fun providesSuggestionsRequestFactory(cacheControl: CacheControl): RequestFactory =
        object : RequestFactory {
            override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String): Request {
                return Request.Builder().url(httpUrl)
                    .addHeader("Accept-Charset", encoding)
                    .cacheControl(cacheControl)
                    .build()
            }
        }

    private fun createInterceptorWithMaxCacheAge(maxCacheAgeSeconds: Long) = Interceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header("cache-control", "max-age=$maxCacheAgeSeconds, max-stale=$maxCacheAgeSeconds")
            .build()
    }

    @Singleton
    @Provides
    @SuggestionsClient
    fun providesSuggestionsHttpClient(application: Application): Single<OkHttpClient> =
        Single.fromCallable {
            val intervalDay = TimeUnit.DAYS.toSeconds(1)
            val suggestionsCache = File(application.cacheDir, "suggestion_responses")

            return@fromCallable OkHttpClient.Builder()
                .cache(Cache(suggestionsCache, FileUtils.megabytesToBytes(1)))
                .addNetworkInterceptor(createInterceptorWithMaxCacheAge(intervalDay))
                .build()
        }.cache()

    @Provides
    @Singleton
    fun provideLogger(buildInfo: BuildInfo): Logger = if (buildInfo.buildType == BuildType.DEBUG) {
        AndroidLogger()
    } else {
        NoOpLogger()
    }

    @Provides
    fun providesListPageReader(): ListPageReader = MezzanineGenerator.ListPageReader()

    @Provides
    fun providesHomePageReader(): HomePageReader = MezzanineGenerator.HomePageReader()

    @Provides
    fun providesIncognitoPageReader(): IncognitoPageReader =
        MezzanineGenerator.IncognitoPageReader()

    @Provides
    fun providesBookmarkPageReader(): BookmarkPageReader = MezzanineGenerator.BookmarkPageReader()

    @Provides
    fun providesTextReflow(): TextReflow = MezzanineGenerator.TextReflow()

    @Provides
    fun providesThemeColor(): ThemeColor = MezzanineGenerator.ThemeColor()

    @Provides
    fun providesInvertPage(): InvertPage = MezzanineGenerator.InvertPage()

    @Provides
    fun providesSetMetaViewport(): SetMetaViewport = MezzanineGenerator.SetMetaViewport()

}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestionsClient

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainHandler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UserPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AdBlockPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AppsPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DiskScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NetworkScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DatabaseScheduler
