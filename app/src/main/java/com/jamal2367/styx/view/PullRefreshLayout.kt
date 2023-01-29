/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package com.jamal2367.styx.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.lang.reflect.Method
import kotlin.math.abs

class PullRefreshLayout(context: Context, attrs: AttributeSet?) :
    SwipeRefreshLayout(context, attrs) {
    private val mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var mTouchDownX = 0f

    //private var mTouchDownY = 0f
    private var mIntercept = true

    private val iMethodEnsureTarget: Method =
        SwipeRefreshLayout::class.java.getDeclaredMethod("ensureTarget")
    private val iFieldTarget = SwipeRefreshLayout::class.java.getDeclaredField("mTarget")

    // I vaguely remember that Kotlin init blocks are called in order of declaration.
    // Since this comes after iMethodEnsureTarget was set we should be fine.
    init {
        iMethodEnsureTarget.isAccessible = true
        iFieldTarget.isAccessible = true
    }

    /**
     * Calls private method [SwipeRefreshLayout.ensureTarget].
     */
    private fun callEnsureTarget() {
        iMethodEnsureTarget.invoke(this)
    }

    /**
     * Get value of private field [SwipeRefreshLayout.mTarget].
     */
    private fun getTarget(): View? {
        return iFieldTarget.get(this) as View?
    }

    /**
     *
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        // Prevent crash when SwipeRefreshLayout does not have a target
        callEnsureTarget()
        if (getTarget() == null) {
            return false
        }

        return super.onTouchEvent(ev)
    }

    /**
     *
     */

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Prevent crash when SwipeRefreshLayout does not have a target
        callEnsureTarget()
        if (getTarget() == null) {
            return false
        }

        when (event.action) {
            // Mark X position of our touchdown
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x
                //mTouchDownY = event.y
                mIntercept = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mIntercept) {
                    return false
                }
                // Check if we think user is scrolling vertically
                val eventX = event.x
                val xDiff = abs(eventX - mTouchDownX)
                if (xDiff > mTouchSlop) {
                    // User is scrolling horizontally do not intercept inputs
                    // Thus preventing pull-to-refresh to trigger while we scroll horizontally
                    mIntercept = false
                }
            }
        }

        if (mIntercept) {
            return super.onInterceptTouchEvent(event)
        }

        return false
    }

}
