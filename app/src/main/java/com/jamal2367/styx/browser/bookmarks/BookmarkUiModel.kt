/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.bookmarks

import com.jamal2367.styx.browser.BookmarksView

/**
 * The UI model representing the current folder shown by the [BookmarksView].
 *
 * Created by anthonycr on 5/7/17.
 */
class BookmarkUiModel {

    /**
     * Sets the current folder that is being shown. Null represents the root folder.
     */
    var currentFolder: String? = null

    /**
     * Determines if the current folder is the root folder.
     *
     * @return true if the current folder is the root, false otherwise.
     */
    fun isCurrentFolderRoot(): Boolean = currentFolder == null

}
