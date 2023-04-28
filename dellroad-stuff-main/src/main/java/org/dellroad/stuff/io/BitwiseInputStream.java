
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
 *
 * @see BitwiseOutputStream
 */
public class BitwiseInputStream extends FilterInputStream {

    private long bufBits;       // input buffer
    private int bufLen;         // the number of bits in "value"

    private long markBits;
    private int markLen;

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
        this.bufBits = 0;
        this.bufLen = 0;
        super.close();
    }

    @Override
    public void mark(int readlimit) {
        super.mark(readlimit);
        this.markBits = this.bufBits;
        this.markLen = this.bufLen;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.bufBits = this.markBits;
        this.bufLen = this.markLen;
    }

    @Override
    public int read() throws IOException {

        // Optimize the byte-aligned case
        if (this.bufLen == 0)
            return in.read();

        // Fill buffer if needed
        if (this.bufLen < 8 && !this.fillBuffer())
            return -1;
        assert this.bufLen >= 8;

        // Return the next byte
        final int r = (int)this.bufBits & 0xff;
        this.bufBits >>>= 8;
        this.bufLen -= 8;
        return r;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {

        // Sanity check
        if (off < 0 || len < 0 || (long)off + (long)len > buf.length)
            throw new IndexOutOfBoundsException();

        // First, read out any whole bytes already in the buffer
        int total = 0;
        while (len > 0 && this.bufLen >= 8) {
            buf[off] = (byte)this.bufBits;
            this.bufBits >>>= 8;
            this.bufLen -= 8;
            off++;
            len--;
            total++;
        }

        // Now do a bulk read; note we may still have up to 7 buffered bits
        if (len > 0) {
            final int r = in.read(buf, off, len);
            if (r == -1)
                return total == 0 ? -1 : total;
            total += r;
            len = r;
        }

        // Stick any buffered bits onto the front of the buffer, and save what pushes out the other end
        if (this.bufLen > 0 && len > 0)
            this.bufBits = BitwiseInputStream.shiftInBits(this.bufBits, this.bufLen, buf, off, len);

        // Done
        return total;
    }

    @Override
    public long skip(long remain) throws IOException {

        // Initialize return value
        long skipped = 0;

        // Skip any buffered bytes first
        while (remain > 0 && this.bufLen >= 8) {
            this.bufBits >>>= 8;
            this.bufLen -= 8;
            skipped++;
            remain--;
        }

        // Optimize byte-aligned case
        if (this.bufLen == 0)
            return skipped + super.skip(remain);

        // Do a "dumb" skip using bulk reads
        final byte[] buf = new byte[(int)Math.min(1024, remain)];
        while (remain > 0) {
            final int r = this.read(buf, 0, (int)Math.min(remain, buf.length));
            if (r == -1)
                break;
            skipped += r;
        }

        // Done
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

        // Sanity check
        if (len < 0)
            throw new IllegalArgumentException("len = " + len);

        // We want to only read whole bytes, but we allocate room for an extra partial byte
        final int readLen = len >> 3;
        final int arrayLen = (len + 7) >> 3;
        final byte[] buf = new byte[arrayLen];

        // Bulk read as many complete bytes as possible
        int r;
        for (int off = 0; off < readLen; off += r) {
            if ((r = this.read(buf, off, readLen - off)) == -1)
                throw new EOFException();
        }

        // Read the remaining fractional byte
        if (readLen < arrayLen) {
            assert readLen + 1 == arrayLen;
            final AtomicLong word = new AtomicLong();
            final int remainBits = len % 8;
            for (int bitsRead = 0; bitsRead < remainBits; bitsRead += r) {
                if ((r = this.readBits(word, remainBits - bitsRead)) == -1)
                    throw new EOFException();
                buf[readLen] |= (byte)((int)word.get() << bitsRead);
            }
        }

        // Done
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

        // Sanity check
        if (len < 0 || len > 64)
            throw new IllegalArgumentException("len = " + len);

        // Read bits
        int count = 0;
        long value = 0;
        while (len > 0) {

            // Fill buffer
            if (this.bufLen == 0 && !this.fillBuffer()) {
                if (count == 0)
                    return -1;
                break;
            }

            // Calculate how many bits we can copy without underflowing
            final int numCopy = Math.min(len, this.bufLen);
            assert numCopy > 0;

            // Copy over bits
            if (numCopy == 64) {
                assert count == 0;
                value = this.bufBits;
                this.bufBits = 0;
                this.bufLen = 0;
            } else {
                value |= this.bufBits << count;
                this.bufBits >>>= numCopy;
                this.bufLen -= numCopy;
            }
            count += numCopy;
            len -= numCopy;
        }

        // Mask off unused bits
        if (count < 64)
            value &= ~(~0L << count);

        // Done
        result.set(value);
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
        final int skip = this.bufLen & 0x07;
        this.bufBits >>>= skip;
        this.bufLen -= skip;
        return skip;
    }

// Internal methods

    // Try to fill buffer; return true if at least one byte was read
    private boolean fillBuffer() throws IOException {

        // Calculate how many bytes we can read without overflowing
        final int numRead = (64 - this.bufLen) / 8;
        if (numRead == 0)
            return false;

        // Try to read that many bytes
        final byte[] buf = new byte[numRead];
        final int r = in.read(buf, 0, numRead);
        if (r <= 0)
            return false;

        // Shift bytes into buffer
        for (int off = 0; off < r; off++) {
            this.bufBits |= (long)(buf[off] & 0xff) << this.bufLen;
            this.bufLen += 8;
        }

        // Done
        return true;
    }

    // Given a byte[] buffer, shift the given bits into the front of it
    // and return the corresponding bits that were pushed out the back.
    static long shiftInBits(long longBits, int nbits, byte[] buf, int off, int len) {
        assert (longBits & (~0 << nbits)) == 0;
        assert nbits > 0 && nbits < 8;
        int bits = (int)longBits;
        while (len-- > 0) {
            final int value16 = ((buf[off] & 0xff) << nbits) | bits;
            buf[off] = (byte)value16;
            bits = value16 >>> 8;
            off++;
        }
        return bits;
    }

    String describe() {
        return String.format("bits=0x%016x,len=%d", this.bufBits, this.bufLen);
    }
}
