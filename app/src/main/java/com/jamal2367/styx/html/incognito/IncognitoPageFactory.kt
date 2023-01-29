/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.html.incognito

import android.app.Application
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.webkit.URLUtil
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.R
import com.jamal2367.styx.constant.FILE
import com.jamal2367.styx.constant.UTF8
import com.jamal2367.styx.html.HtmlPageFactory
import com.jamal2367.styx.html.jsoup.*
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.search.SearchEngineProvider
import com.jamal2367.styx.utils.DrawableUtils
import com.jamal2367.styx.utils.ThemeUtils
import com.jamal2367.styx.utils.htmlColor
import dagger.Reusable
import io.reactivex.Single
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.net.URL
import javax.inject.Inject

/**
 * A factory for the incognito page.
 */
@Reusable
class IncognitoPageFactory @Inject constructor(
    private val application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val incognitoPageReader: IncognitoPageReader,
    private var userPreferences: UserPreferences,
    private var resources: Resources,
) : HtmlPageFactory {

    override fun buildPage(): Single<String> = Single
        .just(searchEngineProvider.provideSearchEngine())
        .map { (_, queryUrl, _) ->
            parse(incognitoPageReader.provideHtml()
                .replace("\${TITLE}", application.getString(R.string.incognito))
                .replace("\${backgroundColor}",
                    htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),
                        android.R.attr.colorBackground)))
                .replace("\${searchBarColor}",
                    htmlColor(ThemeUtils.getSearchBarColor(ThemeUtils.getSurfaceColor(BrowserApp.currentContext()))))
                .replace("\${searchBarTextColor}",
                    htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),
                        R.attr.colorControlNormal)))
                .replace("\${backgroundColor1}",
                    htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),
                        R.attr.colorPrimary)))
                .replace("\${backgroundColor2}",
                    htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),
                        android.R.attr.colorBackground)))
                .replace("\${accent}",
                    htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),
                        R.attr.colorSecondary)))
                .replace("\${search}", application.getString(R.string.search_incognito))
            ) andBuild {
                charset { UTF8 }
                body {
                    tag("script") {
                        html(
                            html()
                                .replace("\${BASE_URL}", queryUrl)
                                .replace("&", "\\u0026")
                        )
                    }

                    if (userPreferences.showShortcuts) {
                        val shortcuts = arrayListOf(userPreferences.link1,
                            userPreferences.link2,
                            userPreferences.link3,
                            userPreferences.link4)

                        id("edit_shortcuts") { text(resources.getString(R.string.edit_shortcuts)) }
                        id("apply") { text(resources.getString(R.string.apply)) }
                        id("close") { text(resources.getString(R.string.close)) }
                        id("link1click") { attr("href", shortcuts[0]) }
                        id("link2click") { attr("href", shortcuts[1]) }
                        id("link3click") { attr("href", shortcuts[2]) }
                        id("link4click") { attr("href", shortcuts[3]) }

                        shortcuts.forEachIndexed { index, element ->

                            if (!URLUtil.isValidUrl(element)) {
                                val icon = createIconByName('?')
                                val encoded = bitmapToBase64(icon)
                                id("link" + (index + 1)) {
                                    attr("src",
                                        "data:image/png;base64,$encoded")
                                }
                                return@forEachIndexed
                            }

                            val url = URL(element.replaceFirst("www.", ""))
                            val icon = createIconByName(url.host.first().uppercaseChar())
                            val encoded = bitmapToBase64(icon)

                            id("link" + (index + 1)) { attr("src", "$element/favicon.ico") }
                            id("link" + (index + 1)) {
                                attr("onerror",
                                    "this.src = 'data:image/png;base64,$encoded';")
                            }
                        }
                    } else {
                        id("shortcuts") { attr("style", "display: none;") }
                    }
                }
            }
        }
        .map { content -> Pair(createIncognitoPage(), content) }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use {
                it.write(content)
            }
        }
        .map { (page, _) -> "$FILE$page" }

    /**
     * Create the incognito page file.
     */
    fun createIncognitoPage() = File(application.filesDir, FILENAME)

    private fun createIconByName(name: Char): Bitmap {
        return DrawableUtils.createRoundedLetterImage(name, 64, 64, Color.GRAY)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    companion object {

        const val FILENAME = "private.html"

    }

}
