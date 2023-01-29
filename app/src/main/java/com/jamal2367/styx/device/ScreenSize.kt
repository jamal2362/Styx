/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.device

import android.content.Context
import android.content.res.Configuration
import dagger.Reusable
import javax.inject.Inject

/**
 * A model used to determine the screen size info.
 *
 * Created by anthonycr on 2/19/18.
 */
@Reusable
class ScreenSize @Inject constructor(private val context: Context) {

    fun isTablet(): Boolean =
        context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_XLARGE

}
