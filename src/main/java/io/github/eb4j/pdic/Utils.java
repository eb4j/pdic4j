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

import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

final class Utils {

    /**
     * Hide utility class constructor.
     */
    private Utils() { }

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
     * 次の０までの長さを返す.
     *
     * @param array target byte array
     * @param pos start position
     * @return length of index.
     */
    static int getLengthToNextZero(final byte[] array, final int pos) {
        return ArrayUtils.indexOf(array, (byte) 0, pos) - pos;
    }
}
