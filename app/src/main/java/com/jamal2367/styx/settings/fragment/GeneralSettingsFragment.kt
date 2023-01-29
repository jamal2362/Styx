/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.URLUtil
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.Capabilities
import com.jamal2367.styx.R
import com.jamal2367.styx.browser.JavaScriptChoice
import com.jamal2367.styx.browser.SuggestionNumChoice
import com.jamal2367.styx.constant.TEXT_ENCODINGS
import com.jamal2367.styx.constant.Uris
import com.jamal2367.styx.dialog.BrowserDialog
import com.jamal2367.styx.extensions.resizeAndShow
import com.jamal2367.styx.extensions.withSingleChoiceItems
import com.jamal2367.styx.isSupported
import com.jamal2367.styx.preference.USER_AGENTS_ORDERED
import com.jamal2367.styx.preference.USER_AGENT_CUSTOM
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.preference.userAgent
import com.jamal2367.styx.search.SearchEngineProvider
import com.jamal2367.styx.search.Suggestions
import com.jamal2367.styx.search.engine.BaseSearchEngine
import com.jamal2367.styx.search.engine.CustomSearch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The general settings of the app.
 */
@AndroidEntryPoint
class GeneralSettingsFragment : AbstractSettingsFragment() {

    @Inject
    lateinit var searchEngineProvider: SearchEngineProvider
    @Inject
    lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_general

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        clickableDynamicPreference(
            preference = SETTINGS_USER_AGENT,
            summary = userAgentSummary(),
            onClick = ::showUserAgentChooserDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_HOME,
            summary = homePageUrlToDisplayTitle(userPreferences.homepage),
            onClick = ::showHomePageDialog
        )

        switchPreference(
            preference = SETTINGS_SHOW_SSL,
            isChecked = userPreferences.ssl,
            onCheckChange = { userPreferences.ssl = it }
        )

        clickableDynamicPreference(
            preference = SETTINGS_SEARCH_ENGINE,
            summary = getSearchEngineSummary(searchEngineProvider.provideSearchEngine()),
            onClick = ::showSearchProviderDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_SUGGESTIONS,
            summary = searchSuggestionChoiceToTitle(Suggestions.from(userPreferences.searchSuggestionChoice)),
            onClick = ::showSearchSuggestionsDialog
        )

        val stringArray = resources.getStringArray(R.array.suggestion_name_array)

        clickableDynamicPreference(
            preference = SETTINGS_SUGGESTIONS_NUM,
            summary = stringArray[userPreferences.suggestionChoice.value],
            onClick = ::showSuggestionNumPicker
        )

        clickableDynamicPreference(
            preference = getString(R.string.pref_key_default_text_encoding),
            summary = userPreferences.textEncoding,
            onClick = this::showTextEncodingDialogPicker
        )

        val incognitoCheckboxPreference = switchPreference(
            preference = getString(R.string.pref_key_cookies_incognito),
            isEnabled = !Capabilities.FULL_INCOGNITO.isSupported,
            isVisible = !Capabilities.FULL_INCOGNITO.isSupported,
            isChecked = if (Capabilities.FULL_INCOGNITO.isSupported) {
                userPreferences.cookiesEnabled
            } else {
                userPreferences.incognitoCookiesEnabled
            },
            summary = if (Capabilities.FULL_INCOGNITO.isSupported) {
                getString(R.string.incognito_cookies_pie)
            } else {
                null
            },
            onCheckChange = { userPreferences.incognitoCookiesEnabled = it }
        )

        switchPreference(
            preference = getString(R.string.pref_key_cookies),
            isChecked = userPreferences.cookiesEnabled,
            onCheckChange = {
                userPreferences.cookiesEnabled = it
                if (Capabilities.FULL_INCOGNITO.isSupported) {
                    incognitoCheckboxPreference.isChecked = it
                }
            }
        )

        clickableDynamicPreference(
            preference = SETTINGS_BLOCK_JAVASCRIPT,
            summary = userPreferences.javaScriptChoice.toSummary(),
            onClick = ::showJavaScriptPicker
        )

        switchPreference(
            preference = SETTINGS_FORCE_ZOOM,
            isChecked = userPreferences.forceZoom,
            onCheckChange = { userPreferences.forceZoom = it }
        )

        clickablePreference(
            preference = SETTINGS_IMAGE_URL,
            summary = getString(R.string.image_url_summary),
            onClick = ::showImageUrlPicker
        )

        switchPreference(
            preference = SETTINGS_SHORTCUTS,
            isChecked = userPreferences.showShortcuts,
            onCheckChange = { userPreferences.showShortcuts = it }
        )
    }

    private fun showTextEncodingDialogPicker(summaryUpdater: SummaryUpdater) {
        activity?.let {
            MaterialAlertDialogBuilder(it).apply {
                setTitle(resources.getString(R.string.text_encoding))

                val currentChoice = TEXT_ENCODINGS.indexOf(userPreferences.textEncoding)

                setSingleChoiceItems(TEXT_ENCODINGS, currentChoice) { _, which ->
                    userPreferences.textEncoding = TEXT_ENCODINGS[which]
                    summaryUpdater.updateSummary(TEXT_ENCODINGS[which])
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }.resizeAndShow()
        }
    }

    private fun showSuggestionNumPicker(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
            setTitle(R.string.suggest)
            val stringArray = resources.getStringArray(R.array.suggestion_name_array)
            val values = SuggestionNumChoice.values().map {
                Pair(it, when (it) {
                    SuggestionNumChoice.TWO -> stringArray[0]
                    SuggestionNumChoice.THREE -> stringArray[1]
                    SuggestionNumChoice.FOUR -> stringArray[2]
                    SuggestionNumChoice.FIVE -> stringArray[3]
                    SuggestionNumChoice.SIX -> stringArray[4]
                    SuggestionNumChoice.SEVEN -> stringArray[5]
                    SuggestionNumChoice.EIGHT -> stringArray[6]
                    SuggestionNumChoice.NINE -> stringArray[7]
                    SuggestionNumChoice.TEN -> stringArray[8]
                })
            }
            withSingleChoiceItems(values, userPreferences.suggestionChoice) {
                updateSearchNum(it, summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun updateSearchNum(choice: SuggestionNumChoice, summaryUpdater: SummaryUpdater) {
        val stringArray = resources.getStringArray(R.array.suggestion_name_array)

        userPreferences.suggestionChoice = choice
        summaryUpdater.updateSummary(stringArray[choice.value])
    }

    private fun showImageUrlPicker() {
        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.image_url,
                R.string.hint_url,
                userPreferences.imageUrlString,
                R.string.action_ok) { s ->
                userPreferences.imageUrlString = s
            }
        }
    }

    private fun userAgentSummary() =
        resources.getString(USER_AGENTS_ORDERED[userPreferences.userAgentChoice]
            ?: R.string.agent_default) +
                activity?.application?.let { ":\n" + userPreferences.userAgent(it) }

    private fun showUserAgentChooserDialog(summaryUpdater: SummaryUpdater) {
        activity?.let { activity ->
            val userAgentChoices = USER_AGENTS_ORDERED.keys.toTypedArray()
            val currentAgentIndex = userAgentChoices.indexOf(userPreferences.userAgentChoice)
                .let { if (it == -1) 0 else it }
            BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
                setTitle(resources.getString(R.string.title_user_agent))
                setSingleChoiceItems(
                    USER_AGENTS_ORDERED.values.map { resources.getString(it) }.toTypedArray(),
                    currentAgentIndex)
                { _, which ->
                    userPreferences.userAgentChoice = userAgentChoices[which]
                    if (userAgentChoices[which] == USER_AGENT_CUSTOM)
                        showCustomUserAgentPicker()

                    summaryUpdater.updateSummary(userAgentSummary())
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }
    }

    private fun showCustomUserAgentPicker() {
        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.title_user_agent,
                R.string.title_user_agent,
                userPreferences.userAgentString,
                R.string.action_ok) { s ->
                userPreferences.userAgentString = s
            }
        }
    }

    private fun homePageUrlToDisplayTitle(url: String): String = when (url) {
        Uris.AboutHome -> resources.getString(R.string.action_homepage)
        Uris.AboutBlank -> resources.getString(R.string.action_blank)
        Uris.AboutBookmarks -> resources.getString(R.string.action_bookmarks)
        else -> url
    }

    private fun showHomePageDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(R.string.home)
                val n = when (userPreferences.homepage) {
                    Uris.AboutHome -> 0
                    Uris.AboutBlank -> 1
                    Uris.AboutBookmarks -> 2
                    else -> 3
                }

                setSingleChoiceItems(R.array.homepage, n) { _, which ->
                    when (which) {
                        0 -> {
                            userPreferences.homepage = Uris.AboutHome
                            summaryUpdater.updateSummary(resources.getString(R.string.action_homepage))
                        }
                        1 -> {
                            userPreferences.homepage = Uris.AboutBlank
                            summaryUpdater.updateSummary(resources.getString(R.string.action_blank))
                        }
                        2 -> {
                            userPreferences.homepage = Uris.AboutBookmarks
                            summaryUpdater.updateSummary(resources.getString(R.string.action_bookmarks))
                        }
                        3 -> {
                            showCustomHomePagePicker(summaryUpdater)
                        }
                    }
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }
    }

    private fun showCustomHomePagePicker(summaryUpdater: SummaryUpdater) {
        val currentHomepage: String = if (!URLUtil.isAboutUrl(userPreferences.homepage)) {
            userPreferences.homepage
        } else {
            "https://www.google.com"
        }

        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.title_custom_homepage,
                R.string.title_custom_homepage,
                currentHomepage,
                R.string.action_ok) { url ->
                userPreferences.homepage = url
                summaryUpdater.updateSummary(url)
            }
        }
    }

    private fun getSearchEngineSummary(baseSearchEngine: BaseSearchEngine): String {
        return if (baseSearchEngine is CustomSearch) {
            baseSearchEngine.queryUrl
        } else {
            getString(baseSearchEngine.titleRes)
        }
    }

    private fun convertSearchEngineToString(searchEngines: List<BaseSearchEngine>): Array<CharSequence> =
        searchEngines.map { getString(it.titleRes) }.toTypedArray()

    private fun showSearchProviderDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(resources.getString(R.string.title_search_engine))

                val searchEngineList = searchEngineProvider.provideAllSearchEngines()

                val chars = convertSearchEngineToString(searchEngineList)

                val n = userPreferences.searchChoice

                setSingleChoiceItems(chars, n) { _, which ->
                    val searchEngine = searchEngineList[which]

                    // Store the search engine preference
                    val preferencesIndex =
                        searchEngineProvider.mapSearchEngineToPreferenceIndex(searchEngine)
                    userPreferences.searchChoice = preferencesIndex

                    if (searchEngine is CustomSearch) {
                        // Show the URL picker
                        showCustomSearchDialog(searchEngine, summaryUpdater)
                    } else {
                        // Set the new search engine summary
                        summaryUpdater.updateSummary(getSearchEngineSummary(searchEngine))
                    }
                }
                setPositiveButton(R.string.action_ok, null)
            }
        }
    }

    private fun showCustomSearchDialog(customSearch: CustomSearch, summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showEditText(
                it as AppCompatActivity,
                R.string.search_engine_custom,
                R.string.search_engine_custom,
                userPreferences.searchUrl,
                R.string.action_ok
            ) { searchUrl ->
                userPreferences.searchUrl = searchUrl
                summaryUpdater.updateSummary(getSearchEngineSummary(customSearch))
            }

        }
    }

    private fun JavaScriptChoice.toSummary(): String {
        val stringArray = resources.getStringArray(R.array.block_javascript)
        return when (this) {
            JavaScriptChoice.NONE -> stringArray[0]
            JavaScriptChoice.WHITELIST -> userPreferences.siteBlockNames
            JavaScriptChoice.BLACKLIST -> userPreferences.siteBlockNames
        }
    }

    private fun showJavaScriptPicker(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
            setTitle(R.string.block_javascript)
            val stringArray = resources.getStringArray(R.array.block_javascript)
            val values = JavaScriptChoice.values().map {
                Pair(it, when (it) {
                    JavaScriptChoice.NONE -> stringArray[0]
                    JavaScriptChoice.WHITELIST -> stringArray[1]
                    JavaScriptChoice.BLACKLIST -> stringArray[2]
                })
            }
            withSingleChoiceItems(values, userPreferences.javaScriptChoice) {
                updateJavaScriptChoice(it, activity as Activity, summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun updateJavaScriptChoice(
        choice: JavaScriptChoice,
        activity: Activity,
        summaryUpdater: SummaryUpdater,
    ) {
        if (choice == JavaScriptChoice.WHITELIST || choice == JavaScriptChoice.BLACKLIST) {
            showManualJavaScriptPicker(activity, summaryUpdater, choice)
        }

        userPreferences.javaScriptChoice = choice
        summaryUpdater.updateSummary(choice.toSummary())
    }

    @SuppressLint("InflateParams")
    private fun showManualJavaScriptPicker(
        activity: Activity,
        summaryUpdater: SummaryUpdater,
        choice: JavaScriptChoice,
    ) {
        val v = activity.layoutInflater.inflate(R.layout.site_block, null)
        val blockedSites = v.findViewById<TextView>(R.id.siteBlock)
        // Limit the number of characters since the port needs to be of type int
        // Use input filters to limit the EditText length and determine the max
        // length by using length of integer MAX_VALUE

        blockedSites.text = userPreferences.javaScriptBlocked

        BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
            setTitle(R.string.block_sites_title)
            setView(v)
            setPositiveButton(R.string.action_ok) { _, _ ->
                val js = blockedSites.text.toString()
                userPreferences.javaScriptBlocked = js
                if (choice.toString() == "BLACKLIST") {
                    summaryUpdater.updateSummary(getText(R.string.listed_javascript).toString())
                } else {
                    summaryUpdater.updateSummary(getText(R.string.unlisted_javascript).toString())
                }

            }
        }
    }

    private fun searchSuggestionChoiceToTitle(choice: Suggestions): String =
        when (choice) {
            Suggestions.NONE -> getString(R.string.search_suggestions_off)
            Suggestions.GOOGLE -> getString(R.string.powered_by_google)
            Suggestions.DUCK -> getString(R.string.powered_by_duck)
            Suggestions.NAVER -> getString(R.string.powered_by_naver)
            Suggestions.BAIDU -> getString(R.string.powered_by_baidu)
        }

    private fun showSearchSuggestionsDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(resources.getString(R.string.search_suggestions))

                val currentChoice =
                    when (Suggestions.from(userPreferences.searchSuggestionChoice)) {
                        Suggestions.GOOGLE -> 0
                        Suggestions.DUCK -> 1
                        Suggestions.NAVER -> 2
                        Suggestions.BAIDU -> 3
                        Suggestions.NONE -> 4
                    }

                setSingleChoiceItems(R.array.suggestions, currentChoice) { _, which ->
                    val suggestionsProvider = when (which) {
                        0 -> Suggestions.GOOGLE
                        1 -> Suggestions.DUCK
                        2 -> Suggestions.NAVER
                        3 -> Suggestions.BAIDU
                        4 -> Suggestions.NONE
                        else -> Suggestions.GOOGLE
                    }
                    userPreferences.searchSuggestionChoice = suggestionsProvider.index
                    summaryUpdater.updateSummary(searchSuggestionChoiceToTitle(suggestionsProvider))
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }
    }

    companion object {
        private const val SETTINGS_SUGGESTIONS_NUM = "suggestions_number"
        private const val SETTINGS_USER_AGENT = "agent"
        private const val SETTINGS_HOME = "home"
        private const val SETTINGS_SEARCH_ENGINE = "search"
        private const val SETTINGS_SUGGESTIONS = "suggestions_choice"
        private const val SETTINGS_BLOCK_JAVASCRIPT = "block_javascript"
        private const val SETTINGS_FORCE_ZOOM = "force_zoom"
        private const val SETTINGS_SHOW_SSL = "show_ssl"
        private const val SETTINGS_IMAGE_URL = "image_url"
        private const val SETTINGS_SHORTCUTS = "show_shortcuts"
    }
}
