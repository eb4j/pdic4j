/*
 * PDIC4j, a PDIC dictionary access library.
 * Copyright (C) 2021 Hiroshi Miura.
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

package io.github.eb4j.pdic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
public class PdicDictionary {
    private final DictionaryData dictionaryData;
    private static final int HEADER_SIZE = 256;

    /**
     * Default maximum count of returned entry of search results.
     */
    public static final int DEFAULT_MAX_RESULTS = 10;

    public PdicDictionary(final DictionaryData dictionaryData) {
        this.dictionaryData = dictionaryData;
    }

    /**
     * Look up word from index.
     * @param word keyword to search.
     * @return list of result as PdicElement.
     * @throws IOException when error occurred.
     */
    public List<PdicElement> getEntries(@NotNull final String word) throws IOException {
        if (dictionaryData.searchWord(word) && dictionaryData.hasExactMatch()) {
            return dictionaryData.getResult().stream()
                    .filter(en -> en.getIndexWord().equals(word))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Look up word from index by prefix search.
     * @param word keyword to search.
     * @return list of result as PdicElement.
     * @throws IOException when error occurred.
     */
    public List<PdicElement> getEntriesPredictive(@NotNull final String word) throws IOException {
        if (dictionaryData.searchWord(word)) {
            return dictionaryData.getResult();
        }
        return Collections.emptyList();
    }

    /**
     * Set maximum counts of resulted entries.
     * @param count max count.
     */
    public void setMaxSearchCount(final int count) {
        dictionaryData.setSearchMax(count);
    }

    /**
     * Get maximum counts of resulted entries.
     * @return max search count.
     */
    public int getMaxSearchCount() {
        return dictionaryData.getSearchMax();
    }

    /**
     * PDIC/Unicode Dictionary loader.
     * @param file .dic file object.
     * @param cacheFile index cache file object, or null when don't cache.
     * @return PdicDicitonary object.
     * @throws IOException when file read and parse failed.
     */
    public static PdicDictionary loadDictionary(@NotNull final File file, @Nullable final File cacheFile)
            throws IOException {
        DictionaryData dicInfo;
        if (!file.isFile()) {
            throw new IOException("Target file is not a file.");
        }
        PdicHeader header;
        ByteBuffer headerbuff = ByteBuffer.allocate(HEADER_SIZE);
        try (FileInputStream srcStream = new FileInputStream(file);
             FileChannel srcChannel = srcStream.getChannel()) {
            int len = srcChannel.read(headerbuff);
            srcChannel.close();
            if (len != HEADER_SIZE) {
                throw new RuntimeException("Failed to read dictionary.");
            }
            header = new PdicHeader();
            if (header.load(headerbuff) == 0) {
                throw new RuntimeException("Failed to read dictionary.");
            }
            // Unicode辞書 かつ ver5以上のみ許容
            if ((header.version & 0xFF00) < 0x0500 || header.os != 0x20) {
                throw new RuntimeException("Unsupported dictionary version");
            }
            dicInfo = new DictionaryData(file, header.headerSize + header.extheader,
                    header.blockSize * header.indexBlock, header.nindex2, header.indexBlkbit,
                    header.blockSize, DEFAULT_MAX_RESULTS);
            if (!dicInfo.readIndexBlock(cacheFile)) {
                throw new RuntimeException("Failed to load dictionary index");
            }
        }
        return new PdicDictionary(dicInfo);
    }
}
