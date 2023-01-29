/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.bookmarks

import android.graphics.Bitmap
import com.jamal2367.styx.database.Bookmark

/**
 * The data model representing a [Bookmark] in a list.
 *
 * @param bookmark The bookmark backing this view model, either an entry or a folder.
 * @param icon The icon for this bookmark.
 */
data class BookmarksViewModel(
    val bookmark: Bookmark,
    var icon: Bitmap? = null,
)
