/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.search.engine

import com.jamal2367.styx.R

/**
 * The Baidu search engine.
 */
class BaiduSearch : BaseSearchEngine(
    "file:///android_asset/baidu.webp",
    "https://www.baidu.com/s?wd=",
    R.string.search_engine_baidu
)
