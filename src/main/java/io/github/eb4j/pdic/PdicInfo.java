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
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
@SuppressWarnings("membername")
class PdicInfo {
    protected File file;
    protected int mBodyptr;
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

    protected Charset mainCharset;
    protected Charset phoneCharset;
    protected WeakHashMap<String, ByteBuffer> enchdeCache = new WeakHashMap<>();

    protected AnalyzeBlock analyze;
    protected int mLastIndex = 0;
    protected PdicInfoCache pdicInfoCache;

    private RandomAccessFile sourceStream = null;

    @SuppressWarnings("avoidinlineconditionals")
    PdicInfo(final File file, final int start, final int size, final int nindex, final boolean blockbits,
             final int blocksize) {
        this.file = file;
        this.start = start;
        this.size = size;
        nIndex = nindex;
        blockBits = (blockbits) ? 4 : 2;
        this.blocksize = blocksize;
        searchmax = 10;

        phoneCharset = CharsetICU.forNameICU("BOCU-1");
        mainCharset = CharsetICU.forNameICU("BOCU-1");
        try {
            sourceStream = new RandomAccessFile(this.file, "r");
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

        ByteBuffer buffer = enchdeCache.get(word);
        if (buffer == null) {
            buffer = encodetoByteBuffer(mainCharset, word);
            enchdeCache.put(word, buffer);
        }
        int limit = buffer.limit();
        byte[] bytes = new byte[limit];
        System.arraycopy(buffer.array(), 0, bytes, 0, limit);
        int wordlen = bytes.length;

        int[] indexPtr = this.indexPtr;
        int blockbits = blockBits;
        PdicInfoCache pdicInfoCache = this.pdicInfoCache;

        for (int i = 0; i < 32; i++) {
            if ((max - min) <= 1) {
                return min;
            }
            final int look = (int) (((long) min + max) / 2);
            final int len = indexPtr[look + 1] - indexPtr[look] - blockbits;
            final int comp = pdicInfoCache.compare(bytes, 0, wordlen, indexPtr[look], len);
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
            mBodyptr = start + size; // 本体位置=( index開始位置＋インデックスのサイズ)
            if (indexcache != null) {
                try (FileInputStream fis = new FileInputStream(indexcache)) {
                    byte[] buff = new byte[(nIndex + 1) * 4];
                    int readlen = fis.read(buff);
                    if (readlen == buff.length) {
                        final int indexlen = nIndex;
                        final int[] indexptr = new int[nIndex + 1];
                        indexPtr = indexptr;
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
                            indexptr[i] = dat;
                        }
                        return true;
                    }
                } catch (IOException ignored) {
                }
            }

            // インデックスの先頭から見出し語のポインタを拾っていく
            final int nindex = nIndex;
            final int[] indexPtr =  new int[nindex + 1]; // インデックスポインタの配列確保
            this.indexPtr = indexPtr;
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
        mLastIndex = num;
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
        // int len = 0;
        // while (array[pos + len] != 0)
        //     len++;
        // return len;
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

        boolean match = false;

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
                    if (!searchret && analyze.mEob) {
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
                int nextindex = mLastIndex + 1;
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
            sourceStream.seek(mBodyptr + (long) blkno * blocksize);

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
        private byte[] mBuff;
        private boolean mLongfield;
        private byte[] mWord;
        private int mFoundPtr = -1;
        private int mNextPtr = -1;
        private final byte[] mCompbuff = new byte[1024];
        private int mCompLen = 0;
        private boolean mEob = false;

        AnalyzeBlock() {
        }

        public void setBuffer(final byte[] buff) {
            mBuff = buff;
            mLongfield = ((buff[1] & 0x80) != 0);
            ByteBuffer mBB = ByteBuffer.wrap(buff);
            mBB.order(ByteOrder.LITTLE_ENDIAN);
            mNextPtr = 2;
            mEob = false;
            mCompLen = 0;
        }

        public void setSearch(final String word) {
            ByteBuffer buffer = encodetoByteBuffer(mainCharset, word);
            enchdeCache.put(word, buffer);
            mWord = new byte[buffer.limit()];
            System.arraycopy(buffer.array(), 0, mWord, 0, buffer.limit());
        }

        public boolean isEob() {
            return mEob;
        }

        /**
         * ブロックデータの中から指定語を探す.
         */
        public boolean searchWord() {
            final byte[] bytes = mWord;
            final byte[] buff = mBuff;
            final boolean longfield = mLongfield;
            final byte[] compbuff = mCompbuff;
            final int wordlen = bytes.length;

            mFoundPtr = -1;

            // 訳語データ読込
            int ptr = mNextPtr;
            mNextPtr = -1;
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
                    mEob = true;
                    break;
                }
                int qtr = ptr;
                ptr += flen + 1;
                ptr++;


                // 圧縮長
                int complen = (int) buff[qtr++];
                complen &= 0xFF;

                // 見出し語属性 skip
                qtr++;

                // 見出し語圧縮位置保存
                while ((compbuff[complen++] = buff[qtr++]) != 0) ;

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
                    mFoundPtr = retptr;
                    mNextPtr = ptr;
                    mCompLen = complen - 1;
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
            if (mFoundPtr == -1) {
                return null;
            }
            final PdicElement.PdicElementBuilder res = new PdicElement.PdicElementBuilder();
            String indexstr = decodetoCharBuffer(mainCharset, mCompbuff, 0, mCompLen).toString();
            res.setIndex(indexstr);
            // ver6対応 見出し語が、<検索インデックス><TAB><表示用文字列>の順に
            // 設定されていてるので、分割する。
            // それ以前のverではdispに空文字列を保持させる。
            final int tab = indexstr.indexOf('\t');
            if (tab == -1) {
                res.setDisp("");
            } else {
                res.setIndex(indexstr.substring(0, tab));
                res.setDisp(indexstr.substring(tab + 1));
            }

            final byte[] buff = mBuff;
            final boolean longfield = mLongfield;
            byte attr = 0;

            // 訳語データ読込
            int ptr = mFoundPtr;

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
            while (buff[qtr++] != 0) ;

            // 訳語
            if ((attr & 0x10) != 0) { // 拡張属性ありの時
                int trnslen = getLengthToNextZero(buff, qtr);
                res.setTrans(decodetoCharBuffer(mainCharset, buff, qtr, trnslen).toString().replace("\r", ""));
                qtr += trnslen; // 次のNULLまでスキップ

                // 拡張属性取得
                byte eatr;
                while (((eatr = buff[qtr++]) & 0x80) == 0) {
                    if ((eatr & (0x10 | 0x40)) == 0) { // バイナリOFF＆圧縮OFFの場合
                        if ((eatr & 0x0F) == 0x01) { // 用例
                            int len = getLengthToNextZero(buff, qtr);
                            res.setSample(decodetoCharBuffer(mainCharset, buff, qtr, len).toString().replace("\r", ""));
                            qtr += len; // 次のNULLまでスキップ
                        } else if ((eatr & 0x0F) == 0x02) { // 発音
                            int len = getLengthToNextZero(buff, qtr);
                            res.setPhone(decodetoCharBuffer(phoneCharset, buff, qtr, len).toString());
                            qtr += len; // 次のNULLまでスキップ
                        }
                    } else {
                        // バイナリ属性か圧縮属性が来たら打ち切り
                        break;
                    }
                }
            } else {
                // 残り全部が訳文
                res.setTrans(decodetoCharBuffer(mainCharset, buff, qtr, mNextPtr - qtr).toString().replace("\r", ""));
            }
            return res.build();
        }

        // 次の項目が検索語に前方一致するかチェックする
        public boolean hasMoreResult(final boolean incrementptr) {
            byte[] word;
            final byte[] buff = mBuff;
            final boolean longfield = mLongfield;
            final byte[] compbuff = mCompbuff;

            // next search
            if (mFoundPtr == -1) {
                return false;
            }
            word = mWord;

            int wordlen = word.length;

            // 訳語データ読込
            int ptr = mNextPtr;

            int retptr = ptr;
            int flen;
            int b;

            b = buff[ptr++];
            flen = (b & 0xFF);

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
                mEob = true;
                return false;
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
            while ((compbuff[complen++] = buff[qtr++]) != 0) ;

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
                mFoundPtr = retptr;
                mNextPtr = ptr;
                mCompLen = complen - 1;
            }
            return equal;
        }
    }
}
