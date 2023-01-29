/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.search.suggestions

import android.app.Application
import com.jamal2367.styx.R
import com.jamal2367.styx.constant.UTF8
import com.jamal2367.styx.database.SearchSuggestion
import com.jamal2367.styx.extensions.map
import com.jamal2367.styx.extensions.preferredLocale
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.preference.UserPreferences
import io.reactivex.Single
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The search suggestions provider for the Naver search engine.
 */
class NaverSuggestionsModel(
    okHttpClient: Single<OkHttpClient>,
    requestFactory: RequestFactory,
    application: Application,
    logger: Logger,
    userPreferences: UserPreferences,
) : BaseSuggestionsModel(okHttpClient,
    requestFactory,
    UTF8,
    application.preferredLocale,
    logger,
    userPreferences) {

    private val searchSubtitle = application.getString(R.string.suggestion)

    // https://ac.search.naver.com/nx/ac?q=$query&q_enc=UTF-8&st=100&frm=nv&r_format=json&r_enc=UTF-8&r_unicode=0&t_koreng=1&ans=2&run=2&rev=4&con=1
    override fun createQueryUrl(query: String, language: String): HttpUrl =
        HttpUrl.Builder()
            .scheme("https")
            .host("ac.search.naver.com")
            .encodedPath("/nx/ac")
            .addEncodedQueryParameter("q", query)
            .addQueryParameter("q_enc", "UTF-8")
            .addQueryParameter("st", "100")
            .addQueryParameter("frm", "nv")
            .addQueryParameter("r_format", "json")
            .addQueryParameter("r_enc", "UTF-8")
            .addQueryParameter("r_unicode", "0")
            .addQueryParameter("t_koreng", "1")
            .addQueryParameter("ans", "2")
            .addQueryParameter("run", "2")
            .addQueryParameter("rev", "4")
            .addQueryParameter("con", "1")
            .build()

    override fun parseResults(responseBody: ResponseBody): List<SearchSuggestion> {
        return JSONObject(responseBody.string())
            .getJSONArray("items")
            .getJSONArray(0)
            .map { it as JSONArray }
            .map { it[0] as String }
            .map { SearchSuggestion("$searchSubtitle \"$it\"", it) }
    }

}
