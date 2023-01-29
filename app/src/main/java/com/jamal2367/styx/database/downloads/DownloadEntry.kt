/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.database.downloads

/**
 * An entry in the downloads database.
 *
 * @param url The URL of the original download.
 * @param title The file name.
 * @param contentSize The user readable content size.
 */
data class DownloadEntry(
    val url: String,
    val title: String,
    val contentSize: String,
)
