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
import java.nio.file.Path
import kotlin.io.path.deleteExisting

class PdicDictionaryTest {

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    val file: File by lazy { File(this.javaClass.getResource("/Sample.dic").toURI().path) }
    val cache: File by lazy { File(file.absolutePath + ".idx") }

    @Test
    fun getEntriesPredictive() {
        PdicDictionary.loadDictionary(file, cache)
            .getEntriesPredictive("japan").forEach {
                assertAll(
                    Executable { assertEquals("こんにちは", it.translation) },
                    Executable { assertEquals("japanese", it.indexWord) },
                    Executable { assertEquals("Japanese", it.headWord) },
                    Executable { assertEquals(0, it.attribute) },
                    Executable { assertNull(it.pronunciation) },
                    Executable { assertNull(it.example) }
                )
                return
            }
    }

    @Test
    fun getEntriesWithRTL() {
        PdicDictionary.loadDictionary(file, cache)
            .getEntries("persian").forEach {
                assertAll(
                    Executable { assertEquals("سلام علیکم", it.translation) },
                    Executable { assertEquals("persian", it.indexWord) },
                    Executable { assertEquals("Persian", it.headWord) },
                    Executable { assertEquals(0x00, it.attribute) },
                    Executable { assertNull(it.pronunciation) },
                    Executable { assertNull(it.example) }
                )
                return
            }
    }

    @Test
    fun getEntriesWithAttribute() {
        PdicDictionary.loadDictionary(file, cache)
            .getEntries("vietnamese").forEach {
                assertAll(
                    Executable { assertEquals("Chào anh,Chào chi", it.translation) },
                    Executable { assertEquals("vietnamese", it.indexWord) },
                    Executable { assertEquals("Vietnamese", it.headWord) },
                    Executable { assertEquals(0x10, it.attribute) },
                    Executable { assertNull(it.pronunciation) },
                    Executable { assertNull(it.example) }
                )
                return
            }
    }

    @Test
    fun getEntriesWithCacheCreation() {
        val newCache: Path = kotlin.io.path.createTempDirectory("pdic4j")
        PdicDictionary.loadDictionary(file, newCache.resolve("Sample.dic.idx").toFile())
            .getEntries("japanese").forEach {
                assertAll(
                    Executable { assertEquals("こんにちは", it.translation) },
                    Executable { assertEquals("japanese", it.indexWord) },
                    Executable { assertEquals("Japanese", it.headWord) },
                    Executable { assertEquals(0, it.attribute) },
                    Executable { assertNull(it.pronunciation) },
                    Executable { assertNull(it.example) }
                )
                return
            }
        newCache.deleteExisting()
    }

    @Test
    fun getEntriesWithCache() {
        PdicDictionary.loadDictionary(file, cache)
            .getEntries("japanese").forEach {
                assertAll(
                    Executable { assertEquals("こんにちは", it.translation) },
                    Executable { assertEquals("japanese", it.indexWord) },
                    Executable { assertEquals("Japanese", it.headWord) },
                    Executable { assertEquals(0, it.attribute) },
                    Executable { assertNull(it.pronunciation) },
                    Executable { assertNull(it.example) }
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
                    Executable { assertEquals("こんにちは", it.translation) },
                    Executable { assertEquals("japanese", it.indexWord) },
                    Executable { assertEquals("Japanese", it.headWord) },
                    Executable { assertEquals(0, it.attribute) },
                    Executable { assertNull(it.pronunciation) },
                    Executable { assertNull(it.example) }
                )
                return
        }
    }
}
