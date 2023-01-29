/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser

import android.os.Bundle
import com.jamal2367.styx.extensions.popIfNotEmpty
import java.util.*

/**
 * A model that saves [Bundle] and returns the last returned one.
 */
class RecentTabsModel {

    val bundleStack: Stack<Bundle> = Stack()

    /**
     * Return the last closed tab as a [Bundle] or null if there is no previously opened tab.
     * Removes the [Bundle] from the queue after returning it.
     */
    fun popLast(): Bundle? = bundleStack.popIfNotEmpty()

    /**
     * Add the [savedBundle] to the queue. The next call to [popLast] will return this [Bundle].
     */
    fun add(savedBundle: Bundle) = bundleStack.add(savedBundle)

}
