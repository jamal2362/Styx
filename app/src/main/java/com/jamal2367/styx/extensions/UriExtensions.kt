/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.extensions

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.OpenableColumns
import com.jamal2367.styx.BrowserApp

/**
 * Fetches file name for this Uri
 * See: https://stackoverflow.com/a/25005243/3969362
 */
var Uri.fileName: String?
    @SuppressLint("Range")
    get() {
        var result: String? = null
        if (scheme.equals("content")) {
            val cursors = BrowserApp.instance.contentResolver.query(this,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null)
            cursors.use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = path
            result?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    result = it.substring(cut + 1)
                }
            }
        }
        return result
    }
    @Suppress("UNUSED_PARAMETER")
    private set(value) {
    }
	