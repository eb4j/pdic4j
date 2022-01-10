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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import static org.junit.Assert.*

class PdicDictionaryTest {

    def file = new File(PdicDictionaryTest.class.getResource("/Sample.dic").toURI().path)
    def cache = new File(file.absolutePath + ".idx")

    // @Test
    void getEntriesPredictive() {
        PdicDictionary dictionary = PdicDictionary.loadDictionary(file, cache)
        def elements = dictionary.getEntriesPredictive("japan")
        assertTrue(elements.size() > 0)
        def ele = elements.get(0)
        assertEquals("こんにちは", ele.translation)
        assertEquals("japanese", ele.indexWord)
        assertEquals("Japanese", ele.headWord)
        assertEquals(0, ele.attribute)
        assertNull(ele.pronunciation)
        assertNull(ele.example)
    }

    @Test
    void getEntriesWithRTL() {
        PdicDictionary dictionary = PdicDictionary.loadDictionary(file, cache)
        assertNotNull(dictionary)
        def ele = dictionary.getEntries("persian").get(0)
        assertEquals("سلام علیکم", ele.translation)
        assertEquals("persian", ele.indexWord)
        assertEquals("Persian", ele.headWord)
        assertEquals(0x00, ele.attribute)
        assertNull(ele.pronunciation)
        assertNull(ele.example)
    }

    @Test
    void getEntriesWithAttribute() {
        PdicDictionary dictionary = PdicDictionary.loadDictionary(file, cache)
        assertNotNull(dictionary)
        def ele = dictionary.getEntries("vietnamese").get(0)
        assertEquals("Chào anh,Chào chi", ele.translation)
        assertEquals("vietnamese", ele.indexWord)
        assertEquals("Vietnamese", ele.headWord)
        assertEquals(0x10, ele.attribute)
        assertNull(ele.pronunciation)
        assertNull(ele.example)
    }

    @Test
    void getEntriesWithCacheCreation() {
        def newCache = Files.createTempDirectory "pdic4j"
        Files.deleteIfExists newCache
        def ele = PdicDictionary.loadDictionary(file, newCache.resolve("Sample.dic.idx").toFile())
            .getEntries("japanese").get(0)
        assertEquals("こんにちは", ele.translation)
        assertEquals("japanese", ele.indexWord)
        assertEquals("Japanese", ele.headWord)
        assertEquals(0, ele.attribute)
        assertNull(ele.pronunciation)
        assertNull(ele.example)
    }

    @Test
    void getEntriesWithCache() {
        def ele = PdicDictionary.loadDictionary(file, cache)
            .getEntries("japanese").get(0)
        assertEquals("こんにちは", ele.translation)
        assertEquals("japanese", ele.indexWord)
        assertEquals("Japanese", ele.headWord)
        assertEquals(0, ele.attribute)
        assertNull(ele.pronunciation)
        assertNull(ele.example)
    }

    @Test
    void getEntriesWithoutCache() {
        def pdicDictionary = PdicDictionary.loadDictionary(file, null)
        assertEquals(10, pdicDictionary.maxSearchCount)
        pdicDictionary.setMaxSearchCount(20)
        assertEquals(20, pdicDictionary.maxSearchCount)
        def ele = pdicDictionary.getEntries("japanese").get(0)
        assertEquals("こんにちは", ele.translation)
        assertEquals("japanese", ele.indexWord)
        assertEquals("Japanese", ele.headWord)
        assertEquals(0, ele.attribute)
        assertNull(ele.pronunciation)
        assertNull(ele.example)
    }
}
