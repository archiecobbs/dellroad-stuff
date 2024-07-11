
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicLong;

import org.dellroad.stuff.string.ByteArrayEncoder;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BitwiseStreamTest extends TestSupport {

    @Test
    public void testSimple() throws Exception {

        // Write data: 0b00101011 0b00101011
        final ByteArrayOutputStream obuf = new ByteArrayOutputStream();
        try (final BitwiseOutputStream output = new BitwiseOutputStream(obuf)) {
            output.writeBit(true);
            output.writeBit(true);
            output.writeBit(false);
            output.writeBits(0xe5, 7);
            final BitSet set = new BitSet();
            set.set(0, false);
            set.set(1, true);
            set.set(2, false);
            set.set(3, true);
            output.writeBits(set, 4);
        }
        this.log.info("bitwise testSimple: ibuf: 0x{}", bytes(obuf.toByteArray()));

        // Read data
        final ByteArrayInputStream ibuf = new ByteArrayInputStream(obuf.toByteArray());
        try (final BitwiseInputStream input = new BitwiseInputStream(ibuf)) {
            Assert.assertEquals(input.readBit(), 1);
            Assert.assertEquals(input.read(), 0b10010101);
            Assert.assertEquals(input.readBit(), 1);
            Assert.assertEquals(input.readBit(), 0);
            final AtomicLong x = new AtomicLong(~0L);
            input.readBits(x, 2);
            Assert.assertEquals(x.get(), 0x0000000000000001L);
            final BitSet set = input.readBits(2);
            Assert.assertEquals(set.get(0), true);
            Assert.assertEquals(set.get(1), false);
            Assert.assertEquals(input.read(), -1);
        }
    }

    @Test
    public void testRandomReadWrite() throws Exception {
        for (int i = 0; i < 1000; i++) {
            this.log.debug("BitwiseTest: START #{}", i);

            // Create random BitSet
            final byte[] array = new byte[this.randInt(141)];
            this.random.nextBytes(array);
            final BitSet expected = BitSet.valueOf(array);
            final int numBits = this.randInt(array.length * 8);
            if (numBits < expected.length())
                expected.clear(numBits, expected.length());
            this.log.debug("BitwiseTest: EXPECTED: 0x{}, numBits={}", bytes(expected), numBits);

            // Write into buffer
            final ByteArrayOutputStream obuf = new ByteArrayOutputStream();
            try (final BitwiseOutputStream output = new BitwiseOutputStream(obuf)) {
                this.writeRandomly(expected, numBits, output);
            }

            // Display buffer
            final byte[] data = obuf.toByteArray();
            this.log.debug("BitwiseTest: DATA: 0x{}", bytes(data));

            // Read from buffer
            final ByteArrayInputStream ibuf = new ByteArrayInputStream(data);
            final BitSet actual;
            try (final BitwiseInputStream input = new BitwiseInputStream(ibuf)) {
                actual = this.readRandomly(numBits, input);
            }

            // Display buffer
            this.log.debug("BitwiseTest: ACTUAL: 0x{}", bytes(actual));

            // Verify
            Assert.assertEquals(bytes(actual), bytes(expected));
            this.log.debug("BitwiseTest: FINISH #{}", i);
        }
    }

    private void writeRandomly(BitSet bits, final int numBits, BitwiseOutputStream output) throws IOException {
        int index = 0;
        readLoop:
            while (index < numBits) {
                switch (this.randInt(5)) {

                // Write a single bit
                case 0:
                {
                    this.log.debug("0@{}: writeBit({})", index, bits.get(index) ? 1 : 0);
                    output.writeBit(bits.get(index++));
                    break;
                }

                // Write a single byte
                case 1:
                {
                    final int remain = numBits - index;
                    if (remain < 8)
                        break;
                    int value = 0;
                    for (int i = 0; i < 8; i++) {
                        if (bits.get(index++))
                            value |= 1 << i;
                    }
                    value |= this.random.nextInt() << 8;
                    this.log.debug("1@{}: write({})", index - 8, String.format("0x%02x", (value & 0xff)));
                    output.write(value);
                    break;
                }

                // Write a byte[] array
                case 2:
                {
                    final int remain = numBits - index;
                    final int len = this.randInt(Math.min(87, remain / 8));
                    final int extra = this.randInt(len * 2);
                    final int off = this.randInt(extra);
                    final byte[] buf = new byte[len + extra];
                    this.random.nextBytes(buf);
                    for (int j = 0; j < len; j++) {
                        int value = 0;
                        for (int i = 0; i < 8; i++) {
                            if (bits.get(index++))
                                value |= 1 << i;
                        }
                        buf[off + j] = (byte)value;
                    }
                    this.log.debug("2@{}: write(0x{})",
                      index - len * 8, ByteArrayEncoder.encode(Arrays.copyOfRange(buf, off, off + len)));
                    output.write(buf, off, len);
                    break;
                }

                // Write some bits in a long
                case 3:
                {
                    final int remain = numBits - index;
                    final int len = this.randInt(Math.min(64, remain));
                    long value = this.random.nextLong();
                    for (int i = 0; i < len; i++) {
                        if (bits.get(index++))
                            value |= 1L << i;
                        else
                            value &= ~(1L << i);
                    }
                    this.log.debug("3@{}: writeLong({})", index - len, String.format("0x%016x, %d", value, len));
                    output.writeBits(value, len);
                    break;
                }

                // Write a BitSet
                case 4:
                {
                    final int remain = numBits - index;
                    final int len = this.randInt(remain);
                    final BitSet x = new BitSet();
                    for (int i = 0; i < len; i++)
                        x.set(i, bits.get(index++));
                    this.log.debug("4@{}: write({}, {})", index - len, x, len);
                    output.writeBits(x, len);
                    break;
                }

            default:
                assert false;
                break;
            }
        }
    }

    private BitSet readRandomly(final int numBits, BitwiseInputStream input) throws IOException {
        final BitSet bits = new BitSet();
        int index = 0;
    readLoop:
        while (true) {
            final int remain = numBits - index;
            if (remain < 0)
                break;                      // we read into the padding bytes at the end
            switch (this.randInt(5)) {

            // Read a single bit
            case 0:
            {
                final int x = input.readBit();
                this.log.debug("0@{}: readBit() -> {}", index, x);
                if (x == -1)
                    break readLoop;
                bits.set(index++, x == 1);
                break;
            }

            // Read a single byte
            case 1:
            {
                if (remain < 8 && remain != 0)      // avoid fractional tail
                    break;
                final int x = input.read();
                this.log.debug("1@{}: read() -> {}", index, x == -1 ? "-1" : String.format("0x%02x", x));
                assert (x == -1) == (remain == 0) : "x=" + x + ",remain=" + remain;
                if (x == -1)
                    break readLoop;
                for (int i = 0; i < 8; i++)
                    bits.set(index++, (x & (1 << i)) != 0);
                break;
            }

            // Read a byte[] array
            case 2:
            {
                if (remain < 8 && remain != 0)      // avoid fractional tail
                    break;
                final int len = this.randInt(Math.min(87, remain / 8));
                final int extra = this.randInt(len * 2);
                final int off = this.randInt(extra);
                final byte[] buf = new byte[len + extra];
                final int r = len == buf.length && this.random.nextBoolean() ?
                  input.read(buf) : input.read(buf, off, len);
                this.log.debug("2@{}: read(byte[], {}, {}) -> {}", index, off, len, r);
                assert len == 0 || (r == -1) == (remain == 0) : "r=" + r + ",remain=" + remain + ",len=" + len;
                if (r == -1)
                    break readLoop;
                assert r >= 0;
                final int padBefore = off;
                final int padAfter = buf.length - off - r;
                Assert.assertEquals(Arrays.copyOfRange(buf, 0, padBefore), new byte[padBefore]);
                Assert.assertEquals(Arrays.copyOfRange(buf, off + r, buf.length), new byte[padAfter]);
                for (int j = 0; j < r; j++) {
                    final int x = buf[off + j];
                    for (int i = 0; i < 8; i++)
                        bits.set(index++, (x & (1 << i)) != 0);
                }
                break;
            }

            // Read some bits in a long
            case 3:
            {
                final AtomicLong word = new AtomicLong();
                final int len = this.randInt(Math.min(64, remain + 17));
                final int count = input.readBits(word, len);
                this.log.debug("3@{}: readBitsLong({}) -> {} {}", index, len, count,
                  count == -1 ? "N/A" : String.format("0x%016x", word.get()));
                if (count == -1)
                    break readLoop;
                final long x = word.get();
                for (int i = 0; i < count; i++)
                    bits.set(index++, (x & (1L << i)) != 0);
                for (int i = count; i < 64; i++)
                    assert (x & (1L << i)) == 0;
                break;
            }

            // Read a BitSet
            case 4:
            {
                final int len = this.randInt(remain);
                final BitSet x = input.readBits(len);
                this.log.debug("4@{}: readBits({}) -> {}", index, len, x);
                for (int i = 0; i < len; i++)
                    bits.set(index++, x.get(i));
                break;
            }

            default:
                assert false;
                break;
            }
        }

        // Done
        return bits;
    }

    private int randInt(int max) {
        return max == 0 ? 0 : this.random.nextInt(max);
    }

    private String bytes(BitSet bits) {
        return bytes(bits.toByteArray());
    }

    private String bytes(byte[] data) {
        data = data.clone();
        for (int i = 0, j = data.length - 1; i < j; i++, j--) {
            byte temp = data[i];
            data[i] = data[j];
            data[j] = temp;
        }
        return ByteArrayEncoder.encode(data);
    }
}
