/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.utils

import android.app.Application
import android.graphics.*
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.jamal2367.styx.R
import com.jamal2367.styx.utils.Utils.dpToPx
import kotlin.math.abs

object DrawableUtils {

    /**
     * Creates a rounded square of a certain color with
     * a character imprinted in white on it.
     *
     * @param character the character to write on the image.
     * @param width     the width of the final image.
     * @param height    the height of the final image.
     * @param color     the background color of the rounded square.
     * @return a valid bitmap of a rounded square with a character on it.
     */
    fun createRoundedLetterImage(
        character: Char,
        width: Int,
        height: Int,
        color: Int,
    ): Bitmap {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint()
        paint.color = color
        val boldText = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.typeface = boldText
        paint.textSize = dpToPx(14f).toFloat()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        val radius = dpToPx(8f)
        val outer = RectF(0F, 0F, canvas.width.toFloat(), canvas.height.toFloat())
        canvas.drawRoundRect(outer, radius.toFloat(), radius.toFloat(), paint)
        val xPos = canvas.width / 2
        val yPos = (canvas.height / 2 - (paint.descent() + paint.ascent()) / 2).toInt()
        paint.color = Color.WHITE
        canvas.drawText(character.toString(), xPos.toFloat(), yPos.toFloat(), paint)
        return image
    }

    /**
     * Hashes a character to one of four colors:
     * blue, green, red, or orange.
     *
     * @param character the character to hash.
     * @param app       the application needed to get the color.
     * @return one of the above colors, or black something goes wrong.
     */
    @ColorInt
    fun characterToColorHash(character: Char, app: Application): Int {
        val smallHash = Character.getNumericValue(character) % 13
        return when (abs(smallHash)) {
            0 -> ContextCompat.getColor(app, R.color.bookmark_red)
            1 -> ContextCompat.getColor(app, R.color.bookmark_pink)
            2 -> ContextCompat.getColor(app, R.color.bookmark_purple)
            3 -> ContextCompat.getColor(app, R.color.bookmark_deep_purple)
            4 -> ContextCompat.getColor(app, R.color.bookmark_indigo)
            5 -> ContextCompat.getColor(app, R.color.bookmark_blue)
            6 -> ContextCompat.getColor(app, R.color.bookmark_light_blue)
            7 -> ContextCompat.getColor(app, R.color.bookmark_cyan)
            8 -> ContextCompat.getColor(app, R.color.bookmark_teal)
            9 -> ContextCompat.getColor(app, R.color.bookmark_green)
            10 -> ContextCompat.getColor(app, R.color.bookmark_deep_orange)
            11 -> ContextCompat.getColor(app, R.color.bookmark_brown)
            12 -> ContextCompat.getColor(app, R.color.bookmark_blue_grey)
            else -> Color.BLACK
        }
    }

    fun mixColor(fraction: Float, startValue: Int, endValue: Int): Int {
        val startA = startValue shr 24 and 0xff
        val startR = startValue shr 16 and 0xff
        val startG = startValue shr 8 and 0xff
        val startB = startValue and 0xff
        val endA = endValue shr 24 and 0xff
        val endR = endValue shr 16 and 0xff
        val endG = endValue shr 8 and 0xff
        val endB = endValue and 0xff
        return startA + (fraction * (endA - startA)).toInt() shl 24 or (
                startR + (fraction * (endR - startR)).toInt() shl 16) or (
                startG + (fraction * (endG - startG)).toInt() shl 8) or
                startB + (fraction * (endB - startB)).toInt()
    }
}