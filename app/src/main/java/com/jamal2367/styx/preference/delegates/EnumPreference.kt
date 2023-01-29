/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.preference.delegates

import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.preference.IntEnum
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An [Enum] delegate that is backed by [SharedPreferences].
 */
class EnumPreference<T>(
    name: String,
    private val defaultValue: T,
    private val clazz: Class<T>,
    preferences: SharedPreferences,
) : ReadWriteProperty<Any, T> where T : Enum<T>, T : IntEnum {

    //private var backingInt: Int by preferences.intPreference(name, defaultValue.value)
    private var backingValue: String by preferences.stringPreference(name, defaultValue.toString())

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return clazz.enumConstants!!.first { it.toString() == backingValue } ?: defaultValue
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        backingValue = value.toString()
    }
}

/**
 * Creates a [T] enum from [SharedPreferences] with the provide arguments.
 */
inline fun <reified T> SharedPreferences.enumPreference(
    @StringRes stringRes: Int,
    defaultValue: T,
): ReadWriteProperty<Any, T> where T : Enum<T>, T : IntEnum = EnumPreference(
    BrowserApp.instance.resources.getString(stringRes),
    defaultValue,
    T::class.java,
    this
)
