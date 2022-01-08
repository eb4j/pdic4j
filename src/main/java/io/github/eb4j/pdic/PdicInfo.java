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

import com.ibm.icu.charset.CharsetICU;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
@SuppressWarnings("membername")
class PdicInfo {
    protected File file;
    protected int bodyPtr;
    protected List<PdicElement> searchResults = new ArrayList<>();

    protected int start;
    protected int size;
    protected int blockBits;
    protected int nIndex;
    protected int blocksize;
    protected boolean match;
    protected int searchmax; // 最大検索件数
    protected String dictName; // 辞書名

    protected int[] indexPtr;

    protected Charset mainCharset = CharsetICU.forNameICU("BOCU-1");

    protected AnalyzeBlock analyze;
    protected int lastIndex = 0;
    protected PdicInfoCache pdicInfoCache;

    private RandomAccessFile sourceStream = null;

    @SuppressWarnings("avoidinlineconditionals")
    PdicInfo(final File file, final int start, final int size, final int nindex, final boolean blockbits,
             final int blocksize) {
        this.file = file;
        this.start = start;
        this.size = size;
        this.nIndex = nindex;
        blockBits = (blockbits) ? 4 : 2;
        this.blocksize = blocksize;
        searchmax = 10;

        try {
            sourceStream = new RandomAccessFile(file, "r");
            analyze = new AnalyzeBlock();
            pdicInfoCache = new PdicInfoCache(sourceStream, this.start, this.size);
        } catch (FileNotFoundException ignored) {
        }
    }

    /**
     * インデックス領域を検索.
     *
     * @return index of block
     */
    public int searchIndexBlock(final String word) {
        int min = 0;
        int max = nIndex - 1;

        ByteBuffer buffer = Utils.encodetoByteBuffer(mainCharset, word);
        int limit = buffer.limit();
        byte[] bytes = new byte[limit];
        System.arraycopy(buffer.array(), 0, bytes, 0, limit);
        for (int i = 0; i < 32; i++) {
            if ((max - min) <= 1) {
                return min;
            }
            final int look = (int) (((long) min + max) / 2);
            final int len = indexPtr[look + 1] - indexPtr[look] - blockBits;
            final int comp = pdicInfoCache.compare(bytes, 0, bytes.length, indexPtr[look], len);
            if (comp < 0) {
                max = look;
            } else if (comp > 0) {
                min = look;
            } else {
                return look;
            }
        }
        return min;
    }

    /**
     * Read index blocks.
     *
     * @return true when successfully read block, otherwise false.
     */
    public boolean readIndexBlock(final File indexcache) {
        if (sourceStream != null) {
            bodyPtr = start + size; // 本体位置=( index開始位置＋インデックスのサイズ)
            if (indexcache != null) {
                try (FileInputStream fis = new FileInputStream(indexcache)) {
                    byte[] buff = new byte[(nIndex + 1) * 4];
                    int readlen = fis.read(buff);
                    if (readlen == buff.length) {
                        final int indexlen = nIndex;
                        indexPtr = new int[nIndex + 1];
                        int ptr = 0;
                        for (int i = 0; i <= indexlen; i++) {
                            int b;
                            int dat;
                            b = buff[ptr++];
                            b &= 0xFF;
                            dat = b;
                            b = buff[ptr++];
                            b &= 0xFF;
                            dat |= (b << 8);
                            b = buff[ptr++];
                            b &= 0xFF;
                            dat |= (b << 16);
                            b = buff[ptr++];
                            b &= 0xFF;
                            dat |= (b << 24);
                            indexPtr[i] = dat;
                        }
                        return true;
                    }
                } catch (IOException ignored) {
                }
            }

            // インデックスの先頭から見出し語のポインタを拾っていく
            final int nindex = nIndex;
            indexPtr =  new int[nindex + 1]; // インデックスポインタの配列確保
            if (pdicInfoCache.createIndex(blockBits, nindex, indexPtr)) {
                byte[] buff = new byte[indexPtr.length * 4];
                int p = 0;
                for (int c = 0; c <= nindex; c++) {
                    int data = indexPtr[c];
                    buff[p++] = (byte) (data & 0xFF);
                    data >>= 8;
                    buff[p++] = (byte) (data & 0xFF);
                    data >>= 8;
                    buff[p++] = (byte) (data & 0xFF);
                    data >>= 8;
                    buff[p++] = (byte) (data & 0xFF);
                }
                if (indexcache != null) {
                    try (FileOutputStream fos = new FileOutputStream(indexcache)) {
                        fos.write(buff, 0, buff.length);
                    } catch (IOException ignored) {
                    }
                }
                return true;
            }
        }
        indexPtr = null;
        return false;
    }

    /**
     * num個目の見出し語の実体が入っているブロック番号を返す.
     */
    public int getBlockNo(final int num) {
        int blkptr = indexPtr[num] - blockBits;
        lastIndex = num;
        if (blockBits == 4) {
            return pdicInfoCache.getInt(blkptr);
        } else {
            return pdicInfoCache.getShort(blkptr);
        }
    }

    boolean isMatch() {
        return match;
    }

    public String getFilename() {
        return file.getName();
    }

    public int getSearchMax() {
        return searchmax;
    }

    public void setSearchMax(final int m) {
        searchmax = m;
    }

    public void setDicName(final String b) {
        dictName = b;
    }

    public String getDicName() {
        return dictName;
    }

    // 単語を検索する
    public boolean searchWord(final String word) {
        // 検索結果クリア
        int cnt = 0;
        searchResults.clear();

        int ret = searchIndexBlock(word);
        match = false;
        boolean searchret = false;
        while (true) {
            // 最終ブロックは超えない
            if (ret < nIndex) {
                // 該当ブロック読み出し
                int block = getBlockNo(ret++);
                byte[] pblk = readBlockData(block);
                if (pblk != null) {
                    analyze.setBuffer(pblk);
                    analyze.setSearch(word);
                    searchret = analyze.searchWord();
                    // 未発見でEOBの時のみもう一回、回る
                    if (!searchret && analyze.isEob()) {
                        continue;
                    }
                }
            }
            // 基本一回で抜ける
            break;
        }
        if (searchret) {
            // 前方一致するものだけ結果に入れる
            do {
                PdicElement res = analyze.getRecord();
                if (res == null) {
                    break;
                }
                // 完全一致するかチェック
                if (res.getIndex().compareTo(word) == 0) {
                    match = true;
                }
                searchResults.add(res);

                cnt++;
                // 取得最大件数超えたら打ち切り
            } while (cnt < searchmax && hasMoreResult(true));
        }
        return match;
    }

    // 前方一致する単語の有無を返す
    boolean searchPrefix(final String word) {
        int ret = searchIndexBlock(word);

        for (int blk = 0; blk < 2; blk++) {
            // 最終ブロックは超えない
            if (ret + blk >= nIndex) {
                break;
            }
            int block = getBlockNo(ret + blk);

            // 該当ブロック読み出し
            byte[] pblk = readBlockData(block);

            if (pblk != null) {
                analyze.setBuffer(pblk);
                analyze.setSearch(word);

                if (analyze.searchWord()) {
                    return true;
                }
            }
        }
        return false;
    }

    List<PdicElement> getResult() {
        return searchResults;
    }

    public List<PdicElement> getMoreResult() {
        searchResults.clear();
        if (analyze != null) {
            int cnt = 0;
            // 前方一致するものだけ結果に入れる
            while (cnt < searchmax && hasMoreResult(true)) {
                PdicElement res = analyze.getRecord();
                if (res == null) {
                    break;
                }
                searchResults.add(res);
                cnt++;
            }
        }
        return searchResults;
    }

    public boolean hasMoreResult(final boolean incrementptr) {
        boolean result = analyze.hasMoreResult(incrementptr);
        if (!result) {
            if (analyze.isEob()) {    // EOBなら次のブロック読み出し
                int nextindex = lastIndex + 1;
                // 最終ブロックは超えない
                if (nextindex < nIndex) {
                    int block = getBlockNo(nextindex);

                    // 該当ブロック読み出し
                    byte[] pblk = readBlockData(block);

                    if (pblk != null) {
                        analyze.setBuffer(pblk);
                        result = analyze.hasMoreResult(incrementptr);
                    }
                }
            }
        }
        return result;
    }

    /**
     * データブロックを読み込み.
     *
     * @param blkno
     * @return 読み込まれたデータブロック
     */
    byte[] readBlockData(final int blkno) {
        byte[] buff = new byte[0x200];
        byte[] pbuf = buff;
        try {
            sourceStream.seek(bodyPtr + (long) blkno * blocksize);

            // 1ブロック分読込(１セクタ分先読み)
            if (sourceStream.read(pbuf, 0, 0x200) < 0) {
                return null;
            }

            // 長さ取得
            int len = ((int) (pbuf[0])) & 0xFF;
            len |= (((int) (pbuf[1])) & 0xFF) << 8;

            // ブロック長判定
            if ((len & 0x8000) != 0) { // 32bit
                len &= 0x7FFF;
            }
            if (len > 0) {
                // ブロック不足分読込
                if (len * blocksize > 0x200) {
                    pbuf = new byte[blocksize * len];
                    System.arraycopy(buff, 0, pbuf, 0, 0x200);
                    if (sourceStream.read(pbuf, 0x200, len * blocksize - 0x200) < 0) {
                        return null;
                    }
                }
            } else {
                pbuf = null;
            }
            return pbuf;
        } catch (IOException ignored) {
        }
        return null;
    }

}
