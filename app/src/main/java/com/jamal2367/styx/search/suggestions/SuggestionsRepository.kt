/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.search.suggestions

import com.jamal2367.styx.database.SearchSuggestion
import io.reactivex.Single

/**
 * A repository for search suggestions.
 */
interface SuggestionsRepository {

    /**
     * Creates a [Single] that fetches the search suggestion results for the provided query.
     *
     * @param rawQuery the raw query to retrieve the results for.
     * @return a [Single] that emits the list of results for the query.
     */
    fun resultsForSearch(rawQuery: String): Single<List<SearchSuggestion>>

}
