/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser

import android.app.Application
import com.jamal2367.styx.R
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.Utils
import com.jamal2367.styx.utils.isSpecialUrl
import dagger.Reusable
import javax.inject.Inject

/**
 * A UI model for the search box.
 */
@Reusable
class SearchBoxModel @Inject constructor(
    private val userPreferences: UserPreferences,
    application: Application,
) {

    private val untitledTitle: String = application.getString(R.string.untitled)

    /**
     * Returns the contents of the search box based on a variety of factors.
     *
     *  - The user's preference to show either the URL, domain, or page title
     *  - Whether or not the current page is loading
     *  - Whether or not the current page is a Styx generated page.
     *
     * This method uses the URL, title, and loading information to determine what
     * should be displayed by the search box.
     *
     * @param url       the URL of the current page.
     * @param title     the title of the current page, if known.
     * @param isLoading whether the page is currently loading or not.
     * @return the string that should be displayed by the search box.
     */
    fun getDisplayContent(url: String, title: String?, isLoading: Boolean): String =
        when {
            url.isSpecialUrl() -> titleToDisplay(title)
            isLoading -> url
            else -> when (userPreferences.urlBoxContentChoice) {
                SearchBoxDisplayChoice.DOMAIN -> safeDomain(url)
                SearchBoxDisplayChoice.URL -> url
                SearchBoxDisplayChoice.TITLE -> titleToDisplay(title)
                SearchBoxDisplayChoice.TRIMMED -> Utils.trimmedProtocolFromURL(url)
            }
        }

    private fun safeDomain(url: String) = Utils.getDisplayDomainName(url)

    /**
     * Provide title the display from given title
     */
    private fun titleToDisplay(title: String?) =
        if (title?.isEmpty() == false) {
            title
        } else {
            untitledTitle
        }

}
