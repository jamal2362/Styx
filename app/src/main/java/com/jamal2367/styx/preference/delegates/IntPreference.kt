/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.preference.delegates

import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.jamal2367.styx.BrowserApp
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An [Int] delegate that is backed by [SharedPreferences].
 */
private class IntPreferenceDelegate(
    private val name: String,
    private val defaultValue: Int,
    private val preferences: SharedPreferences,
) : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Int =
        preferences.getInt(name, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
        preferences.edit().putInt(name, value).apply()
    }

}

/**
 * Creates a [Int] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.intPreference(
    @StringRes stringRes: Int,
    defaultValue: Int,
): ReadWriteProperty<Any, Int> =
    IntPreferenceDelegate(BrowserApp.instance.resources.getString(stringRes), defaultValue, this)
