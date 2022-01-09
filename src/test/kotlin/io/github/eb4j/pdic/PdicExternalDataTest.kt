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
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.Assumptions
import java.io.File

class PdicExternalDataTest {

    @Test
    fun getEntriesWithCJDictionary() {
        val file = File("/tmp/chinese.dic")
        Assumptions.assumeTrue(file.isFile())
        val dictionary = PdicDictionary.loadDictionary(file, null)
        Assertions.assertNotNull(dictionary)
        dictionary.getEntries("语言学").forEach {
                Assertions.assertAll(
                    Executable { Assertions.assertEquals("yu3yan2xue2\n言語学", it.translation) },
                    Executable { Assertions.assertEquals("语言学", it.indexWord) },
                    Executable { Assertions.assertEquals("语言学", it.headWord) },
                )
                return
            }
        Assertions.fail<String>("Entry not found")
    }

}