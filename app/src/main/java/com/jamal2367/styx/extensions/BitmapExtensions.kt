/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.extensions

import android.graphics.*

/**
 * Creates and returns a new favicon which is the same as the provided favicon but with horizontal
 * and vertical padding of 4dp
 *
 * @return the padded bitmap.
 */
fun Bitmap.pad(): Bitmap {
    return this
}

/**
 * Return a new bitmap containing this bitmap with inverted colors.
 * See: https://gist.github.com/moneytoo/87e3772c821cb1e86415
 */
fun Bitmap.invert(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val matrixGrayscale = ColorMatrix()
    matrixGrayscale.setSaturation(0f)
    val matrixInvert = ColorMatrix()
    matrixInvert.set(
        floatArrayOf(
            -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
            0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
            0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
    )
    matrixInvert.preConcat(matrixGrayscale)
    val filter = ColorMatrixColorFilter(matrixInvert)
    paint.colorFilter = filter
    canvas.drawBitmap(this, 0f, 0f, paint)
    return bitmap
}
