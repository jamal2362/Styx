/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.search

/**
 * The suggestion choices.
 *
 * Created by anthonycr on 2/19/18.
 */
enum class Suggestions(val index: Int) {
    NONE(0),
    GOOGLE(1),
    DUCK(2),
    BAIDU(3),
    NAVER(4);

    companion object {
        fun from(value: Int): Suggestions {
            return when (value) {
                0 -> NONE
                1 -> GOOGLE
                2 -> DUCK
                3 -> BAIDU
                4 -> NAVER
                else -> GOOGLE
            }
        }
    }
}