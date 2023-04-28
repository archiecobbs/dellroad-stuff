
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.BitSet;

/**
 * A bit-oriented {@link OutputStream}.
 *
 * <p>
 * Instances support writing arbitrary numbers of individual bits. Bits are written to the underlying
 * {@link OutputStream} in groups of eight (i.e., whole bytes, obviously), where the bits in each written
 * byte are ordered from least significant to most significant bit.
 *
 * <p>
 * Of course, instances also support writing traditional byte-oriented data: any bytes written are handled
 * as if each of the eight bits were written individually, in order from least significant to most significant.
 *
 * <p>
 * When instances are closed, if the output bitstream is not currently aligned to a byte boundary (i.e.,
 * {@link #bitOffset} would return a non-zero value), then padding of up to seven zero bits is written (as if by
 * {@link #padToByteBoundary padToByteBoundary()}), and then the underlying stream is closed.
 *
 * <p>
 * As an example, writing the bits {@code 0b101001101111} and then invoking {@link #close} would result in
 * {@code 0x6d} {@code 0x0e} being written to the underlying stream. The same output would result if {@code 0b111},
 * {@code 0x4d}, and then {@code 0b001} were written.
 *
 * @see BitwiseInputStream
 */
public class BitwiseOutputStream extends FilterOutputStream {

    private long bufBits;       // output buffer
    private int bufLen;         // the number of bits in "bufBits"

    /**
     * Constructor.
     *
     * @param out underlying output
     */
    public BitwiseOutputStream(OutputStream out) {
        super(out);
    }

// OutputStream

    @Override
    public void close() throws IOException {
        this.padToByteBoundary();
        super.close();
    }

    @Override
    public void flush() throws IOException {
        while (this.bufLen >= 8)
            this.outputByte();
        super.flush();
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {

        // Sanity check
        if (off < 0 || len < 0 || (long)off + (long)len > buf.length)
            throw new IndexOutOfBoundsException();

        // First, flush any whole bytes from the output buffer
        while (this.bufLen >= 8)
            this.outputByte();

        // Optimize the byte-aligned case
        if (this.bufLen == 0) {
            this.out.write(buf, off, len);
            return;
        }

        // Copy caller's data so we can modify it
        final byte[] copy = new byte[len];
        System.arraycopy(buf, off, copy, 0, len);
        buf = copy;

        // Stick remaining buffer bits onto the front of the buffer, and save what pushes out the other end
        this.bufBits = BitwiseInputStream.shiftInBits(this.bufBits, this.bufLen, buf, 0, buf.length);

        // Now do a normal bulk write
        this.out.write(buf, 0, buf.length);
    }

    @Override
    public void write(int b) throws IOException {

        // First, flush any whole bytes from the output buffer
        while (this.bufLen >= 8)
            this.outputByte();

        // Optimize the byte-aligned case
        if (this.bufLen == 0) {
            this.out.write(b);
            return;
        }

        // Write non-aligned bits
        this.writeBits(b, 8);
    }

// Public methods

    /**
     * Write some bits from a {@link BitSet}.
     *
     * @param bits where to get the bits
     * @param len the number of bits to write
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if {@code bits} is null
     * @throws IllegalArgumentException if {@code len} is negative
     */
    public void writeBits(BitSet bits, int len) throws IOException {

        // Sanity check
        if (bits == null)
            throw new IllegalArgumentException("null bits");
        if (len < 0)
            throw new IllegalArgumentException("len = " + len);

        // Write bits
        final long[] array = bits.toLongArray();
        int arrayIndex = 0;
        while (len > 0) {
            final int numBits = Math.min(64, len);
            final long value = arrayIndex < array.length ? array[arrayIndex++] : 0;
            this.writeBits(value, numBits);
            len -= numBits;
        }
    }

    /**
     * Write bits in a {@code long} value.
     *
     * <p>
     * The first bit in {@code bits} written is at index zero, etc.
     *
     * @param bits value containing the bits to write (low-order bit first)
     * @param len the number of bits in {@code bits} to write
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if {@code len} is negative or greater than 64
     */
    public void writeBits(long bits, int len) throws IOException {

        // Sanity check
        if (len < 0 || len > 64)
            throw new IllegalArgumentException("len = " + len);

        // Mask off unused bits
        if (len < 64)
            bits &= ~(~0L << len);

        // Write bits
        while (len > 0) {

            // Calculate how many bits we can copy without overflowing
            final int numCopy = Math.min(len, 64 - this.bufLen);

            // Copy over bits
            switch (numCopy) {
            case 0:
                break;
            case 64:
                assert this.bufLen == 0;
                this.bufBits = bits;
                this.bufLen = 64;
                len = 0;
                break;
            default:
                this.bufBits |= bits << this.bufLen;
                this.bufLen += numCopy;
                bits >>>= numCopy;
                len -= numCopy;
                break;
            }

            // Write out any now-completed byte(s)
            while (this.bufLen >= 8)
                this.outputByte();
        }
    }

    /**
     * Write a single bit.
     *
     * @param bit the bit to write
     * @throws IOException if an I/O error occurs
     */
    public void writeBit(boolean bit) throws IOException {
        this.writeBits(bit ? 1 : 0, 1);
    }

    /**
     * Get the current bit offset.
     *
     * @return current bit offset (from zero to seven)
     */
    public int bitOffset() {
        return this.bufLen & 0x07;
    }

    /**
     * Write zero bits to this output stream up to the next byte boundary.
     *
     * <p>
     * If the number of bits written so far is a multiple of eight, this method does nothing.
     * Otherwise it writes zero bits until the next write operation will be byte-aligned.
     *
     * @return the number of zero bits written (from zero to seven)
     * @throws IOException if an I/O error occurs
     */
    public int padToByteBoundary() throws IOException {
        final int pad = (8 - this.bufLen) & 0x07;
        while (this.bufLen > 0)
            this.outputByte();
        return pad;
    }

// Internal methods

    private void outputByte() throws IOException {
        assert this.bufLen > 0;
        super.write((int)this.bufBits);
        this.bufBits >>>= 8;
        this.bufLen = Math.max(0, this.bufLen - 8);
    }
}
