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

package io.github.eb4j.pdic;

import com.ibm.icu.charset.CharsetICU;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

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
        ByteBuffer buffer = Utils.encodetoByteBuffer(mainCharset, word);
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
            int indexStringLen = Utils.getLengthToNextZero(buff, qtr) + 1;
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
        String indexstr = Utils.decodetoCharBuffer(mainCharset, compBuff, 0, compLen).toString();
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
        qtr += Utils.getLengthToNextZero(buff, qtr) + 1;

        // 訳語
        if ((attr & 0x10) != 0) { // 拡張属性ありの時
            int trnslen = Utils.getLengthToNextZero(buff, qtr);
            elementBuilder.setTrans(Utils.decodetoCharBuffer(mainCharset, buff, qtr, trnslen)
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
                        int len = Utils.getLengthToNextZero(buff, qtr);
                        elementBuilder.setSample(Utils.decodetoCharBuffer(mainCharset, buff, qtr, len)
                                .toString()
                                .replace("\r", "")
                        );
                        qtr += len; // 次のNULLまでスキップ
                    } else if ((eatr & 0x0F) == 0x02) { // 発音
                        int len = Utils.getLengthToNextZero(buff, qtr);
                        elementBuilder.setPhone(Utils.decodetoCharBuffer(mainCharset, buff, qtr, len).toString());
                        qtr += len; // 次のNULLまでスキップ
                    }
                } else {
                    // バイナリ属性か圧縮属性が来たら打ち切り
                    break;
                }
            }
        } else {
            // 残り全部が訳文
            elementBuilder.setTrans(Utils.decodetoCharBuffer(mainCharset, buff, qtr, nextPtr - qtr)
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
        int indexStringLen = Utils.getLengthToNextZero(buff, qtr) + 1;
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
