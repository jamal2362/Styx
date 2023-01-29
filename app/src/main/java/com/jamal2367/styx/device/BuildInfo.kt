/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.device

/**
 * A representation of the info for the current build.
 */
data class BuildInfo(val buildType: BuildType)

/**
 * The types of builds that this instance of the app could be.
 */
enum class BuildType {
    DEBUG,
    RELEASE
}
