
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A bit-oriented {@link InputStream}.
 *
 * <p>
 * Instances support reading arbitrary numbers of individual bits. Bits are read from the underlying
 * {@link InputStream} in groups of eight (i.e., whole bytes, obviously), where the bits in each
 * byte are assumed to be ordered from least significant to most significant bit.
 *
 * <p>
 * Of course, instances also support reading traditional byte-oriented data: any bytes read are handled
 * as if each of the eight bits were read individually, in order from least significant to most significant.
 *
 * <p>
 * Because the underlying input is byte-oriented, the total number of bits read will always be a multiple of eight.
 * Attempting to read one or more whole bytes when less than eight bits remain will result in EOF being returnd.
 *
 * @see BitwiseOutputStream
 */
public class BitwiseInputStream extends FilterInputStream {

    private byte bufBits;       // partial byte input buffer
    private byte bufLen;        // the number of bits in "bufBits", in the range 0...7

    private byte markBits;
    private byte markLen;

    /**
     * Constructor.
     *
     * @param in underlying input
     */
    public BitwiseInputStream(InputStream in) {
        super(in);
    }

// InputStream

    @Override
    public void close() throws IOException {
        assert invariants();
        this.bufBits = 0;
        this.bufLen = 0;
        super.close();
    }

    @Override
    public void mark(int readlimit) {
        assert invariants();
        super.mark(readlimit);
        this.markBits = this.bufBits;
        this.markLen = this.bufLen;
    }

    @Override
    public void reset() throws IOException {
        assert invariants();
        super.reset();
        this.bufBits = this.markBits;
        this.bufLen = this.markLen;
    }

    @Override
    public int read() throws IOException {
        assert invariants();

        // Optimize the byte-aligned cases
        if (this.bufLen == 0)
            return this.in.read();

        // Handle the mis-aligned case
        final int next = this.in.read();
        if (next == -1)
            return next;
        final int r = (this.bufBits | (next << this.bufLen)) & 0xff;
        this.bufBits = (byte)(next >>> (8 - this.bufLen));
        assert invariants();
        return r;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        assert invariants();

        // Optimize the byte-aligned cases
        if (this.bufLen == 0 || len == 0)
            return this.in.read(buf, off, len);

        // Sanity check
        if (off < 0 || len < 0 || (long)off + (long)len > buf.length)
            throw new IndexOutOfBoundsException();

        // Do a bulk read
        final int r = this.in.read(buf, off, len);
        if (r == -1)
            return -1;

        // Push buffered bits onto the front of the buffer, and grab what pushes out the other end
        this.bufBits = BitwiseInputStream.shiftInBits(this.bufBits, this.bufLen, buf, off, r);

        // Done
        assert invariants();
        return r;
    }

    @Override
    public long skip(long remain) throws IOException {
        assert invariants();

        // Optimize byte-aligned case
        if (this.bufLen == 0)
            return super.skip(remain);

        // We have to do a "dumb" skip using bulk reads
        long skipped = 0;
        final byte[] buf = new byte[(int)Math.min(1024, remain)];
        while (remain > 0) {
            final int r = this.read(buf, 0, (int)Math.min(remain, buf.length));
            if (r == -1)
                break;
            skipped += r;
            remain -= r;
        }

        // Done
        assert invariants();
        return skipped;
    }

// Public methods

    /**
     * Read some number of bits and return them in a {@link BitSet}.
     *
     * <p>
     * If EOF is encountered before reading {@link len} bits, an {@link EOFException} is thrown.
     *
     * @param len the number of bits to read
     * @throws IOException if an I/O error occurs
     * @throws EOFException if EOF is encountered before {@code len} bits can be read
     * @throws IllegalArgumentException if {@code len} is negative
     */
    public BitSet readBits(final int len) throws IOException {
        assert invariants();

        // Sanity check
        if (len < 0)
            throw new IllegalArgumentException("len = " + len);

        // Determine how many whole bytes and extra bits we should read
        final int numBytes = len / 8;
        final int numBits = len % 8;
        final byte[] buf = new byte[numBytes + (numBits > 0 ? 1 : 0)];

        // Read whole bytes
        int r;
        for (int off = 0; off < numBytes; off += r) {
            if ((r = this.read(buf, off, numBytes - off)) == -1)
                throw new EOFException();
        }

        // Read extra bits
        if (numBits > 0)
            buf[numBytes] = (byte)this.bits(numBits);

        // Done
        assert invariants();
        return BitSet.valueOf(buf);
    }

    /**
     * Read up to 64 bits.
     *
     * <p>
     * Up to {@code len} bits will be read and stored in {@code result}; the first bit read will be at index zero, etc.
     * The actual number of bits read is returned, and all higher bits in {@code result} will be set to zero.
     *
     * <p>
     * If EOF is encountered before reading any bits, -1 is returned.
     *
     * @param result value in which to set the bits read (low-order bit first)
     * @param len the desired number of bits to read
     * @return the number of bits actually read
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if {@code len} is negative or greater than 64
     */
    public int readBits(AtomicLong result, int len) throws IOException {
        assert invariants();

        // Sanity check
        if (len < 0 || len > 64)
            throw new IllegalArgumentException("len = " + len);

        // Read bits
        int count = 0;
        long value = 0;
        while (len > 0) {

            // Refill buffer if needed
            if (this.bufLen == 0) {
                final int r = this.in.read();
                if (r == -1) {
                    if (count == 0)
                        return -1;
                    break;
                }
                this.bufBits = (byte)r;
                this.bufLen = 8;
            }

            // Copy bits from buffer
            final int numCopy = Math.min(len, this.bufLen);
            assert numCopy > 0;
            final long mask = (1L << numCopy) - 1;
            value |= (this.bufBits & mask) << count;
            this.bufBits = (byte)((this.bufBits & 0xff) >>> numCopy);
            this.bufLen -= numCopy;
            count += numCopy;
            len -= numCopy;
        }

        // Mask off unused bits
        if (count < 64)
            value &= ~(~0L << count);

        // Done
        result.set(value);
        assert invariants();
        return count;
    }

    /**
     * Read up to 64 bits that are expected to be there.
     *
     * <p>
     * This will read {@code len} bits and return them in a {@code long} value.
     * The first bit read will be at index zero, etc. All higher bits will be zero.
     *
     * @param len the number of bits to read
     * @return the {@code len} bits that were read, starting at bit index zero
     * @throws EOFException if EOF is encountered
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if {@code len} is negative or greater than 64
     */
    public long bits(int len) throws IOException {
        final AtomicLong word = new AtomicLong();
        final int r = this.readBits(word, len);
        if (r < len)
            throw new EOFException();
        return word.get();
    }

    /**
     * Read a single bit that is expected to be there.
     *
     * @return the bit read
     * @throws EOFException if EOF is encountered
     * @throws IOException if an I/O error occurs
     */
    public boolean bit() throws IOException {
        switch (this.readBit()) {
        case 0:
            return false;
        case 1:
            return true;
        default:
            throw new EOFException();
        }
    }

    /**
     * Read a single bit, or detect EOF.
     *
     * @return a bit value of 0 or 1, or -1 on EOF
     * @throws IOException if an I/O error occurs
     */
    public int readBit() throws IOException {
        final AtomicLong word = new AtomicLong();
        final int r = this.readBits(word, 1);
        return r == -1 ? -1 : ((int)word.get() & 1);
    }

    /**
     * Get the current bit offset.
     *
     * @return current bit offset (from zero to seven)
     */
    public int bitOffset() {
        assert invariants();
        return this.bufLen & 0x07;
    }

    /**
     * Discard bits from this input stream up to the next byte boundary.
     *
     * <p>
     * If the number of bits read so far is a multiple of eight, this method does nothing.
     * Otherwise it skips up to seven bits so that the next read operation will be byte-aligned.
     *
     * @return the number of bits skipped (from zero to seven)
     * @throws IOException if an I/O error occurs
     */
    public int skipToByteBoundary() throws IOException {
        assert invariants();
        final int skipped = this.bufLen;
        this.bufBits = 0;
        this.bufLen = 0;
        return skipped;
    }

// Internal methods

    // Given a byte[] buffer, shift the given bits into the front of it
    // and return the corresponding bits that were pushed out the back.
    static byte shiftInBits(byte byteBits, int nbits, byte[] buf, int off, int len) {
        int bits = byteBits & 0xff;
        assert (bits & (~0 << nbits)) == 0;
        assert nbits > 0 && nbits < 8;
        while (len-- > 0) {
            final int value16 = ((buf[off] & 0xff) << nbits) | bits;
            buf[off] = (byte)value16;
            bits = value16 >>> 8;
            off++;
        }
        return (byte)bits;
    }

    String describe() {
        return String.format("bits=0x%02x,len=%d", this.bufBits & 0xff, this.bufLen);
    }

    boolean invariants() {
        assert this.bufLen >= 0 && this.bufLen < 8 : describe();
        assert ((this.bufBits & 0xff) & (~0 << this.bufLen)) == 0 : describe();
        return true;
    }
}
