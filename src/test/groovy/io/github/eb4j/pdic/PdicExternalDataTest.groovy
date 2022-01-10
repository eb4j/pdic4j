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

import static org.junit.Assert.*

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions


class PdicExternalDataTest {

    @Test
    void getEntriesWithCJDictionary() {
        def file = new File("/tmp/chinese.dic")
        Assumptions.assumeTrue(file.isFile())
        def dictionary = PdicDictionary.loadDictionary(file, null)
        assertNotNull(dictionary)
        def ele = dictionary.getEntries("语言学").get(0)
        assertEquals("语言学", ele.indexWord)
        assertTrue(ele.translation.contains("言語学"))
        assertTrue(ele.headWord.contains("语言学"))
    }
}