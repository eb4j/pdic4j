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
import org.junit.jupiter.api.function.Executable
import java.io.File

class PdicDictionaryTest {

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    val file: File by lazy { File(this.javaClass.getResource("/Sample.dic").toURI().path) }
    val cache: File by lazy { File(file.absolutePath + ".idx") }

    @Test
    fun getEntriesPredictive() {
        PdicDictionary.loadDictionary(file, cache)
            .getEntriesPredictive("japan").forEach {
                assertAll(
                    Executable { assertEquals("こんにちは", it.trans) },
                    Executable { assertEquals("japanese", it.index) },
                    Executable { assertEquals("Japanese", it.disp) },
                    Executable { assertEquals(0, it.attr) },
                    Executable { assertNull(it.phone) },
                    Executable { assertNull(it.sample) }
                )
                return
            }
    }

    @Test
    fun getEntriesWithCache() {
        PdicDictionary.loadDictionary(file, cache)
            .getEntries("japanese").forEach {
                assertAll(
                    Executable { assertEquals("こんにちは", it.trans) },
                    Executable { assertEquals("japanese", it.index) },
                    Executable { assertEquals("Japanese", it.disp) },
                    Executable { assertEquals(0, it.attr) },
                    Executable { assertNull(it.phone) },
                    Executable { assertNull(it.sample) }
                )
                return
            }
    }

    @Test
    fun getEntriesWithoutCache() {
        val pdicDictionary = PdicDictionary.loadDictionary(file, null)
        assertEquals(10, pdicDictionary.maxSearchCount)
        pdicDictionary.setMaxSearchCount(20)
        assertEquals(20, pdicDictionary.maxSearchCount)
        pdicDictionary.getEntries("japanese").forEach {
                assertAll(
                    Executable { assertEquals("こんにちは", it.trans) },
                    Executable { assertEquals("japanese", it.index) },
                    Executable { assertEquals("Japanese", it.disp) },
                    Executable { assertEquals(0, it.attr) },
                    Executable { assertNull(it.phone) },
                    Executable { assertNull(it.sample) }
                )
                return
        }
    }
}
