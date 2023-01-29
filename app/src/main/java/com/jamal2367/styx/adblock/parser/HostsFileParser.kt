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

package com.jamal2367.styx.adblock.parser

import com.jamal2367.styx.database.adblock.Host
import com.jamal2367.styx.extensions.*
import com.jamal2367.styx.log.Logger
import java.io.BufferedReader

/**
 * A single threaded parser for a hosts file.
 */
class HostsFileParser(
    private val logger: Logger,
) {

    private val lineBuilder = StringBuilder()

    /**
     * Parse the lines of the [input] from a hosts file and return the list of [String] domains held
     * in that file.
     */
    fun parseInput(input: BufferedReader): List<Host> {
        val time = System.currentTimeMillis()

        val domains = ArrayList<Host>(100)

        input.use { inputStreamReader ->
            inputStreamReader.forEachLine {
                parseLine(it, domains)
            }
        }

        logger.log(TAG, "Parsed ad list in: ${(System.currentTimeMillis() - time)} ms")

        return domains
    }

    /**
     * Parse a [line] from a hosts file and populate the [parsedList] with the extracted hosts.
     */
    private fun parseLine(line: String, parsedList: MutableList<Host>) {
        lineBuilder.setLength(0)
        lineBuilder.append(line)
        if (lineBuilder.isNotEmpty() && lineBuilder[0] != COMMENT_CHAR) {
            lineBuilder.inlineReplace(LOCAL_IP_V4, EMPTY)
            lineBuilder.inlineReplace(LOCAL_IP_V4_ALT, EMPTY)
            lineBuilder.inlineReplace(LOCAL_IP_V6, EMPTY)
            lineBuilder.inlineReplaceChar(TAB_CHAR, SPACE_CHAR)

            val comment = lineBuilder.indexOfChar(COMMENT_CHAR)
            if (comment > 0) {
                lineBuilder.setLength(comment)
            } else if (comment == 0) {
                return
            }

            lineBuilder.inlineTrim()

            if (lineBuilder.isNotEmpty() && !lineBuilder.stringEquals(LOCALHOST)) {
                while (lineBuilder.containsChar(SPACE_CHAR)) {
                    val space = lineBuilder.indexOfChar(SPACE_CHAR)
                    val partial = lineBuilder.substringToBuilder(0, space)
                    partial.inlineTrim()

                    val partialLine = partial.toString()

                    // Add string to list
                    if (partialLine.contains('.'))
                        parsedList.add(Host(partialLine))
                    lineBuilder.inlineReplace(partialLine, EMPTY)
                    lineBuilder.inlineTrim()
                }
                if (lineBuilder.isNotEmpty() && lineBuilder.containsChar('.')) {
                    // Add string to list.
                    parsedList.add(Host(lineBuilder.toString()))
                }
            }
        }
    }

    companion object {
        private const val TAG = "HostsFileParser"

        private const val LOCAL_IP_V4 = "127.0.0.1"
        private const val LOCAL_IP_V4_ALT = "0.0.0.0"
        private const val LOCAL_IP_V6 = "::1"
        private const val LOCALHOST = "localhost"
        private const val COMMENT_CHAR = '#'
        private const val TAB_CHAR = '\t'
        private const val SPACE_CHAR = ' '
        private const val EMPTY = ""
    }
}
