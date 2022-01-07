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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
public class PdicDictionary {
    private final File srcFile;
    private final PdicInfo dicInfo;

    public PdicDictionary(File srcFile, PdicInfo dicInfo) {
        this.srcFile = srcFile;
        this.dicInfo = dicInfo;
    }

    public List<PdicElement> getEntries(final String word) {
        List<PdicElement> result = null;
        if (dicInfo.searchWord(word)) {
            result = dicInfo.getResult();
        }
        return result;
    }

    public static PdicDictionary loadDictionary(final File file, final File cacheFile) throws IOException {
        PdicInfo dicInfo;
        if (!file.isFile()) {
            throw new IOException("Target file is not a file.");
        }
        final int headerSize = 256;
        PdicHeader header; // ヘッダー
        ByteBuffer headerbuff = ByteBuffer.allocate(headerSize);
        try (FileInputStream srcStream = new FileInputStream(file);
             FileChannel srcChannel = srcStream.getChannel()) {
            int len = srcChannel.read(headerbuff);
            srcChannel.close();
            if (len != headerSize) {
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
            dicInfo = new PdicInfo(file, header.header_size + header.extheader,
                    header.block_size * header.index_block, header.nindex2, header.index_blkbit,
                    header.block_size);
            if (!dicInfo.readIndexBlock(cacheFile)) {
                throw new RuntimeException("Failed to load dictionary index");
            }
        }
        dicInfo.setDicName(file.getName());
        return new PdicDictionary(file, dicInfo);
    }
}
