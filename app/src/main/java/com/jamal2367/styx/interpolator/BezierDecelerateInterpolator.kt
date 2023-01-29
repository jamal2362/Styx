/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.interpolator

import android.os.Parcel
import android.os.Parcelable
import android.view.animation.Interpolator
import androidx.core.view.animation.PathInterpolatorCompat

/**
 * Smooth bezier curve interpolator.
 */
class BezierDecelerateInterpolator : Interpolator, Parcelable {

    private val interpolator = PathInterpolatorCompat.create(0.25f, 0.1f, 0.25f, 1f)

    override fun getInterpolation(input: Float): Float = interpolator.getInterpolation(input)
    override fun writeToParcel(parcel: Parcel, flags: Int) {
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BezierDecelerateInterpolator> {
        override fun createFromParcel(parcel: Parcel): BezierDecelerateInterpolator {
            return BezierDecelerateInterpolator()
        }

        override fun newArray(size: Int): Array<BezierDecelerateInterpolator?> {
            return arrayOfNulls(size)
        }
    }

}
