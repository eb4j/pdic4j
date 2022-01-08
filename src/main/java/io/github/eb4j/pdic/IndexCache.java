package io.github.eb4j.pdic;

import com.ibm.icu.charset.CharsetICU;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
class IndexCache {
    private final boolean fix;
    private final int blockSize;
    private final RandomAccessFile randomAccessFile;
    private final int start;
    private final int size;
    private final WeakHashMap<Integer, WeakReference<byte[]>> mMap = new WeakHashMap<>();
    private byte[] fixedBuffer;

    IndexCache(final RandomAccessFile file, final int start, final int size) {
        randomAccessFile = file;
        this.start = start;
        this.size = size;
        if (this.size < 1024 * 512) {
            fix = true;
            blockSize = this.size;
        } else {
            fix = false;
            blockSize = 1024;
        }
    }

    byte[] getSegment(final int segment) {
        byte[] segmentData = null;

        if (fix) {
            if (fixedBuffer == null) {
                fixedBuffer = new byte[size];
                try {
                    randomAccessFile.seek(start);
                    if (randomAccessFile.read(fixedBuffer, 0, size) >= 0) {
                        return fixedBuffer;
                    }
                } catch (IOException ignored) {
                }
            }
        }

        WeakReference<byte[]> ref = mMap.get(segment);
        if (ref != null) {
            segmentData = ref.get();
        }
        if (segmentData == null) {
            segmentData = new byte[blockSize];
            try {
                randomAccessFile.seek(start + (long) segment * blockSize);
                int len = randomAccessFile.read(segmentData, 0, blockSize);
                if (len == blockSize || len == size % blockSize) {
                    mMap.put(segment, new WeakReference<>(segmentData));
                } else {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }
        return segmentData;
    }


    public int getShort(final int ptr) {
        int segment = ptr / blockSize;
        int address = ptr % blockSize;
        byte[] segmentdata = getSegment(segment++);

        int dat = 0;
        if (segmentdata != null) {
            int b;
            b = segmentdata[address++];
            b &= 0xFF;
            dat |= b;

            if (address >= blockSize) {
                address %= blockSize;
                segmentdata = getSegment(segment);
            }
            b = segmentdata[address];
            b &= 0xFF;
            dat |= (b << 8);
        }
        return dat;
    }

    public int getInt(final int ptr) {
        int segment = ptr / blockSize;
        int address = ptr % blockSize;
        byte[] segmentdata = getSegment(segment++);

        int dat = 0;
        if (segmentdata != null) {
            int b;
            b = segmentdata[address++];
            b &= 0xFF;
            dat |= b;
            if (address >= blockSize) {
                address %= blockSize;
                segmentdata = getSegment(segment++);
            }
            b = segmentdata[address++];
            b &= 0xFF;
            dat |= (b << 8);
            if (address >= blockSize) {
                address %= blockSize;
                segmentdata = getSegment(segment++);
            }
            b = segmentdata[address++];
            b &= 0xFF;
            dat |= (b << 16);
            if (address >= blockSize) {
                address %= blockSize;
                segmentdata = getSegment(segment);
            }
            b = segmentdata[address];
            b &= 0x7F;
            dat |= (b << 24);
        }
        return dat;
    }

    @SuppressWarnings("finalparameters")
    private static int compareArrayAsUnsigned(byte[] aa, int pa, int la, byte[] ab, int pb, int lb) {
        while (la-- > 0) {
            short sa = aa[pa++];
            if (lb-- > 0) {
                short sb = ab[pb++];
                if (sa != sb) {
                    sa &= 0xFF;
                    sb &= 0xFF;
                    return (sa - sb);
                }
            } else {
                return 1;
            }
        }
        if (lb > 0) {
            short sb = ab[pb];
            if (sb == 0x09) {        // 比較対象の'\t'は'\0'とみなす
                return 0;
            }
            return -1;
        }
        return 0;
    }

    /**
     *
     * @param aa
     * @param pa
     * @param la
     * @param ptr
     * @param len
     * @return
     */
    @SuppressWarnings("finalparameters")
    public int compare(final byte[] aa, final int pa, final int la, final int ptr, final int len) {
        int segment = ptr / blockSize;
        int address = ptr % blockSize;
        byte[] segmentdata = getSegment(segment++);

        if (segmentdata == null) {
            return -1;
        }

        if (len < 0) {
            return 1;
        }

        if (address + len < blockSize) {
            Utils.decodetoCharBuffer(CharsetICU.forNameICU("BOCU-1"), segmentdata, address, len);
            return compareArrayAsUnsigned(aa, pa, la, segmentdata, address, len);
        } else {
            int lena = blockSize - address;
            int leno = Math.min(la, lena);
            int ret = compareArrayAsUnsigned(aa, pa, leno, segmentdata, address, lena);
            Utils.decodetoCharBuffer(CharsetICU.forNameICU("BOCU-1"), segmentdata, address, lena);
            if (ret != 0) {
                return ret;
            }
            if (la < lena) {
                return -1;
            }
            address = 0;
            segmentdata = getSegment(segment);
            Utils.decodetoCharBuffer(CharsetICU.forNameICU("BOCU-1"), segmentdata, address, len - lena);
            return compareArrayAsUnsigned(aa, pa + lena, la - lena, segmentdata, address, len - lena);
        }
    }

    /**
     * Create index of words.
     * @param blockBits
     * @param nIndex
     * @param indexPtr
     * @return true when success, otherwise false.
     */
    public boolean createIndex(final int blockBits, final int nIndex, final int[] indexPtr) {
        // インデックスの先頭から見出し語のポインタを拾っていく
        int blockSize = 64 * 1024;
        int[] params = new int[]{0, 0, nIndex, blockSize, blockBits, 1, 0};

        boolean hasNext = true;
        for (int i = 0; hasNext; i++) {
            hasNext = countIndexWords(params, getSegment(i), indexPtr);
        }
        indexPtr[params[0]] = params[1] + blockBits; // ターミネータを入れておく
        return true;
    }

    private boolean countIndexWords(final int[] params, final byte[] buff, final int[] indexPtr) {
        int curidx = params[0];
        int curptr = params[1];
        int max = params[2];
        int buffmax = params[3];
        int blockbits = params[4];
        int found = params[5];
        int ignore = params[6];

        int i = 0;

        for (; i < buffmax && curidx < max; i++) {
            if (ignore > 0) {
                ignore--;
            } else if (found != 0) {
                int ptr = curptr + i + blockbits;  // ブロック番号サイズポインタを進める
                indexPtr[curidx++] = ptr;          // 見出し語部分のポインタを保存
                ignore = blockbits - 1;
                found = 0;
            } else if (buff[i] == 0) {
                found = 1;
            }
        }

        params[0] = curidx;
        params[1] = curptr + i;
        params[5] = found;
        params[6] = ignore;
        return curidx < max;
    }

}
