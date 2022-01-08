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
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
     * byte配列の本文文字列をCharBufferに変換する.
     */
    static CharBuffer decodetoCharBuffer(final Charset cs, final byte[] array, final int pos, final int len) {
        return cs.decode(ByteBuffer.wrap(array, pos, len));
    }

    /**
     * 本文の文字列をByteBufferに変換する.
     */
    static ByteBuffer encodetoByteBuffer(final Charset cs, final String str) {
        return cs.encode(str);
    }

    /**
     * インデックス領域を検索.
     *
     * @return index of block
     */
    public int searchIndexBlock(final String word) {
        int min = 0;
        int max = nIndex - 1;

        ByteBuffer buffer = encodetoByteBuffer(mainCharset, word);
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

    /**
     * 次の０までの長さを返す.
     *
     * @param array target byte array
     * @param pos start position
     * @return length of index.
     */
    static int getLengthToNextZero(final byte[] array, final int pos) {
        return ArrayUtils.indexOf(array, (byte) 0, pos) - pos;
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

    final class AnalyzeBlock {
        private final Charset mainCharset = CharsetICU.forNameICU("BOCU-1");
        private byte[] buff;
        private boolean longField;
        private byte[] searchWord;
        private int foundPtr = -1;
        private int nextPtr = -1;
        private final byte[] compBuff = new byte[1024];
        private int compLen = 0;
        private boolean eob = false;

        AnalyzeBlock() {
        }

        public void setBuffer(final byte[] newBuff) {
            buff = newBuff;
            longField = ((buff[1] & 0x80) != 0);
            nextPtr = 2;
            eob = false;
            compLen = 0;
        }

        public void setSearch(final String word) {
            ByteBuffer buffer = encodetoByteBuffer(mainCharset, word);
            searchWord = new byte[buffer.limit()];
            System.arraycopy(buffer.array(), 0, searchWord, 0, buffer.limit());
        }

        public boolean isEob() {
            return eob;
        }

        /**
         * ブロックデータの中から指定語を探す.
         */
        public boolean searchWord() {
            final byte[] bytes = searchWord;
            final boolean longfield = longField;
            final byte[] compbuff = compBuff;
            final int wordlen = bytes.length;

            foundPtr = -1;

            // 訳語データ読込
            int ptr = nextPtr;
            nextPtr = -1;
            while (true) {
                int flen = 0;
                int retptr = ptr;
                int b;

                b = buff[ptr++];
                flen |= (b & 0xFF);

                b = buff[ptr++];
                b <<= 8;
                flen |= (b & 0xFF00);

                if (longfield) {
                    b = buff[ptr++];
                    b <<= 16;
                    flen |= (b & 0xFF0000);

                    b = buff[ptr++];
                    b <<= 24;
                    flen |= (b & 0x7F000000);
                }
                if (flen == 0) {
                    eob = true;
                    break;
                }
                int qtr = ptr;
                ptr += flen + 1;
                ptr++;


                // 圧縮長
                int complen = buff[qtr++];
                complen &= 0xFF;

                // 見出し語属性 skip
                qtr++;

                // 見出し語圧縮位置保存
                // while ((compbuff[complen++] = buff[qtr++]) != 0) ;
                int indexStringLen = getLengthToNextZero(buff, qtr) + 1;
                System.arraycopy(buff, qtr, compbuff, complen, indexStringLen);
                qtr += indexStringLen;
                complen += indexStringLen;

                // 見出し語の方が短ければ不一致
                if (complen < wordlen) {
                    continue;
                }


                // 前方一致で比較
                boolean equal = true;
                for (int i = 0; i < wordlen; i++) {

                    if (compbuff[i] != bytes[i]) {
                        equal = false;
                        int cc = compbuff[i];
                        cc &= 0xFF;
                        int cw = bytes[i];
                        cw &= 0xFF;
                        // 超えてたら打ち切る
                        if (cc > cw) {
                            return false;
                        }
                        break;
                    }
                }
                if (equal) {
                    foundPtr = retptr;
                    nextPtr = ptr;
                    compLen = complen - 1;
                    return true;
                }
            }
            return false;
        }

        /**
         * 最後の検索結果の単語を返す.
         *
         * @return search result
         */
        PdicElement getRecord() {
            if (foundPtr == -1) {
                return null;
            }
            final PdicElement.PdicElementBuilder elementBuilder = new PdicElement.PdicElementBuilder();
            String indexstr = decodetoCharBuffer(mainCharset, compBuff, 0, compLen).toString();
            elementBuilder.setIndex(indexstr);
            // ver6対応 見出し語が、<検索インデックス><TAB><表示用文字列>の順に
            // 設定されていてるので、分割する。
            // それ以前のverではdispに空文字列を保持させる。
            final int tab = indexstr.indexOf('\t');
            if (tab == -1) {
                elementBuilder.setDisp("");
            } else {
                elementBuilder.setIndex(indexstr.substring(0, tab));
                elementBuilder.setDisp(indexstr.substring(tab + 1));
            }

            final boolean longfield = longField;
            byte attr;

            // 訳語データ読込
            int ptr = foundPtr;

            if (longfield) {
                ptr += 4;
            } else {
                ptr += 2;
            }
            int qtr = ptr;

            // 圧縮長
            // int complen = buff[qtr++];
            // complen &= 0xFF;
            qtr++;

            // 見出し語属性 skip
            attr = buff[qtr++];

            // 見出し語 skip
            qtr += getLengthToNextZero(buff, qtr) + 1;

            // 訳語
            if ((attr & 0x10) != 0) { // 拡張属性ありの時
                int trnslen = getLengthToNextZero(buff, qtr);
                elementBuilder.setTrans(decodetoCharBuffer(mainCharset, buff, qtr, trnslen)
                                .toString()
                                .replace("\r", "")
                        );
                qtr += trnslen; // 次のNULLまでスキップ

                // 拡張属性取得
                byte eatr;
                while (true) {
                    eatr = buff[qtr++];
                    if ((eatr & 0x80) != 0) {
                        break;
                    }
                    if ((eatr & (0x10 | 0x40)) == 0) { // バイナリOFF＆圧縮OFFの場合
                        if ((eatr & 0x0F) == 0x01) { // 用例
                            int len = getLengthToNextZero(buff, qtr);
                            elementBuilder.setSample(decodetoCharBuffer(mainCharset, buff, qtr, len)
                                    .toString()
                                    .replace("\r", "")
                            );
                            qtr += len; // 次のNULLまでスキップ
                        } else if ((eatr & 0x0F) == 0x02) { // 発音
                            int len = getLengthToNextZero(buff, qtr);
                            elementBuilder.setPhone(decodetoCharBuffer(mainCharset, buff, qtr, len).toString());
                            qtr += len; // 次のNULLまでスキップ
                        }
                    } else {
                        // バイナリ属性か圧縮属性が来たら打ち切り
                        break;
                    }
                }
            } else {
                // 残り全部が訳文
                elementBuilder.setTrans(decodetoCharBuffer(mainCharset, buff, qtr, nextPtr - qtr)
                        .toString()
                        .replace("\r", "")
                );
            }
            return elementBuilder.build();
        }

        // 次の項目が検索語に前方一致するかチェックする
        public boolean hasMoreResult(final boolean incrementptr) {
            byte[] word;
            final byte[] compbuff = compBuff;

            // next search
            if (foundPtr == -1) {
                return false;
            }
            word = searchWord;
            int wordlen = word.length;

            // 訳語データ読込
            int ptr = nextPtr;

            int retptr = ptr;
            int flen;
            int b;

            b = buff[ptr++];
            flen = (b & 0xFF);

            b = buff[ptr++];
            b <<= 8;
            flen |= (b & 0xFF00);

            if (longField) {
                b = buff[ptr++];
                b <<= 16;
                flen |= (b & 0xFF0000);

                b = buff[ptr++];
                b <<= 24;
                flen |= (b & 0x7F000000);
            }
            if (flen == 0) {
                eob = true;
                return false;
            }
            int qtr = ptr;

            // 圧縮長
            int complen = buff[qtr++];
            complen &= 0xFF;

            // 見出し語属性 skip
            qtr++;

            // 見出し語圧縮位置保存
            int indexStringLen = getLengthToNextZero(buff, qtr) + 1;
            System.arraycopy(buff, qtr, compbuff, complen, indexStringLen);
            complen += indexStringLen;

            // 見出し語の方が短ければ不一致
            if (complen < wordlen) {
                return false;
            }

            // 前方一致で比較
            boolean equal = true;
            for (int i = 0; i < wordlen; i++) {
                if (compbuff[i] != word[i]) {
                    equal = false;
                    int cc = compbuff[i];
                    cc &= 0xFF;
                    int cw = word[i];
                    cw &= 0xFF;
                    // 超えてたら打ち切る
                    if (cc > cw) {
                        return false;
                    }
                    break;
                }
            }
            if (equal && incrementptr) {
                foundPtr = retptr;
                nextPtr = ptr + flen + 2;
                compLen = complen - 1;
            }
            return equal;
        }
    }
}
