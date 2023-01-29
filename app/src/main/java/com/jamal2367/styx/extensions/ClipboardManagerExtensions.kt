/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.extensions

import android.content.ClipData
import android.content.ClipboardManager

/**
 * Copies the [text] to the clipboard with the label `URL`.
 */
fun ClipboardManager.copyToClipboard(text: String) {
    setPrimaryClip(ClipData.newPlainText("URL", text))
}
