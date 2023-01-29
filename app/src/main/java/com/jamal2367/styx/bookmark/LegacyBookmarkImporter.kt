/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.bookmark

import com.jamal2367.styx.database.Bookmark
import com.jamal2367.styx.database.bookmark.BookmarkExporter
import java.io.InputStream
import javax.inject.Inject

/**
 * A [BookmarkImporter] that imports bookmark files that were produced by BookmarkExporter.
 */
class LegacyBookmarkImporter @Inject constructor() : BookmarkImporter {

    override fun importBookmarks(inputStream: InputStream): List<Bookmark.Entry> {
        return BookmarkExporter.importBookmarksFromFileStream(inputStream)
    }

}
