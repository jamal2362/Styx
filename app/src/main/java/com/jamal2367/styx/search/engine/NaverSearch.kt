/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.search.engine

import com.jamal2367.styx.R

/**
 * The Naver search engine.
 */
class NaverSearch : BaseSearchEngine(
    "file:///android_asset/naver.webp",
    "https://search.naver.com/search.naver?ie=utf8&query=",
    R.string.search_engine_naver
)
