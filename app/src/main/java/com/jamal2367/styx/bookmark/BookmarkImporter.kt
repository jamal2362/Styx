/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.bookmark

import com.jamal2367.styx.database.Bookmark
import java.io.InputStream

/**
 * An importer that imports [Bookmark.Entry] from an [InputStream]. Supported formats are details of
 * the implementation.
 */
interface BookmarkImporter {

    /**
     * Synchronously converts an [InputStream] to a [List] of [Bookmark.Entry].
     */
    fun importBookmarks(inputStream: InputStream): List<Bookmark.Entry>

}
