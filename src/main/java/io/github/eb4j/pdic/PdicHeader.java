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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
@SuppressWarnings({"visibilitymodifier", "membername"})
final class PdicHeader {
    private static final int L_HEADERNAME = 100;    //   ヘッダー部文字列長
    private static final int L_DICTITLE = 40;        //   辞書タイトル名長

    // public String headername; //   辞書ヘッダータイトル
    // public String dictitle;   //   辞書名
    public short version;      //   辞書のバージョン
    // public short lword;        //   見出語の最大長
    // public short ljapa;        //   訳語の最大長
    public short blockSize;      //  (256 ) １ブロックのバイト数 固定
    public short indexBlock;     //   インデックスブロック数
    public short headerSize;     //   ヘッダーのバイト数
    // public short index_size;  //  ( ) インデックスのバイト数 未使用

    // public short nindex;       //  ( ) インデックスの要素の数 未使用
    // public short nblock;       //  ( ) 使用データブロック数 未使用
    // public int nword;    //   登録単語数

    // public byte dicorder;      //   辞書の順番
    // public byte dictype;       //   辞書の種別

    public byte attrlen;       //   単語属性の長さ
    public byte os;          // OS
    public boolean indexBlkbit;   // false:16bit, true:32bit
    public int extheader;      //   拡張ヘッダーサイズ
    public int nindex2;       //   インデックス要素の数
    // public int nblock2;       //   使用データブロック数

    // public int update_count;    //   辞書更新回数
    // public String dicident;      //   辞書識別子

    /**
     * コンストラクタ.
     */
    PdicHeader() {
    }

    /**
     * @param headerBlock ヘッダーデータ部分
     * @return 辞書バージョン
     */
    public int load(final ByteBuffer headerBlock) throws RuntimeException {
        int ret = 0;
        // Charset sjisset = Charset.forName("X-SJIS");

        byte[] headernamebuff = new byte[L_HEADERNAME];
        byte[] dictitlebuff = new byte[L_DICTITLE];

        headerBlock.flip();
        headerBlock.order(ByteOrder.LITTLE_ENDIAN);
        headerBlock.get(headernamebuff);
        // headername = sjisset.decode(ByteBuffer.wrap(headernamebuff)).toString();
        headerBlock.get(dictitlebuff);
        // dictitle = sjisset.decode(ByteBuffer.wrap(dictitlebuff)).toString();
        version = headerBlock.getShort();
        if ((version & 0xFF00) == 0x0500 || (version & 0xFF00) == 0x0600) {
            headerBlock.getShort();  // lword
            headerBlock.getShort();  // ljapa

            blockSize = headerBlock.getShort();
            indexBlock = headerBlock.getShort();
            headerSize = headerBlock.getShort();
            headerBlock.getShort();  // index_size
            headerBlock.getShort();  // empty_block
            headerBlock.getShort();  // nindex
            headerBlock.getShort();  // nblock

            headerBlock.getInt();  // nword

            headerBlock.get();  // dicorder
            headerBlock.get();  // dictype
            attrlen = headerBlock.get();
            os = headerBlock.get();

            headerBlock.getInt();  // ole_number

            // lid_dummy
            headerBlock.getShort();
            headerBlock.getShort();
            headerBlock.getShort();
            headerBlock.getShort();
            headerBlock.getShort();

            indexBlkbit = (headerBlock.get() != 0);
            headerBlock.get(); // dummy0
            extheader = headerBlock.getInt();
            headerBlock.getInt();  //empty_block2
            nindex2 = headerBlock.getInt();
            headerBlock.getInt();  // nblock2

            // 固定部分チェック
            if (attrlen == 1) {
                ret = version >> 8;
            }
        } else {
            throw new RuntimeException("Unsupported format");
        }
        return ret;
    }

}
