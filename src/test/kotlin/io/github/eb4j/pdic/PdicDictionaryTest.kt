/*
 * PDIC4j, a PDIC dictionary access library.
 * Copyright (C) 2022 Hiroshi Miura.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eb4j.pdic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class PdicDictionaryTest {

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    val file: File by lazy { File(this.javaClass.getResource("/Sample.dic").toURI().path) }

    @Test
    fun getEntries() {
        PdicDictionary.loadDictionary(file, null)
            .getEntries("japanese").forEach {
            assertEquals("こんにちは", it.trans)
        }
    }
}
