
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/**
 * A better piped input/output stream pair.
 *
 * <p>
 * The two ends of the pipe are returned by {@link #getInputStream()} and {@link #getOutputStream()}.
 * Both streams are fully thread safe. The aliveness of any particular thread that happens to be the
 * last writer to the output stream is irrelevant.
 *
 * @see <a href="https://bugs.openjdk.java.net/browse/JDK-4028322">JDK-4028322</a>
 */
public class PipedStreams {

    /**
     * Default internal buffer size ({@value #DEFAULT_BUFFER_SIZE}).
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192 - 32;        // two 4K pages with room for array object header

    private final Input input = this.new Input();
    private final Output output = this.new Output();
    private final byte[] buf;

    private int off;
    private int len;
    private boolean inputClosed;
    private boolean outputClosed;

    /**
     * Constructor.
     */
    public PipedStreams() {
        this(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructor.
     *
     * @param bufsiz internal buffer size
     * @throws IllegalArgumentException if {@code bufsiz} is zero or negative
     */
    public PipedStreams(int bufsiz) {
        if (bufsiz <= 0)
            throw new IllegalArgumentException("bufsiz <= 0");
        this.buf = new byte[bufsiz];
    }

    /**
     * Get the input side of the pipe.
     *
     * <p>
     * Regarding the returned {@link InputStream}:
     * <ul>
     *  <li>When the internal buffer is empty, reads block until more data is written to the output side,
     *      or the output side is closed.
     *  <li>If the current thread is interrupted while blocked, an {@link InterruptedIOException} is thrown.
     * </ul>
     *
     * @return input side of the piped stream
     */
    public Input getInputStream() {
        return this.input;
    }

    /**
     * Get the output side of the pipe.
     *
     * <p>
     * Regarding the returned {@link OutputStream}:
     * <ul>
     *  <li>When the internal buffer is full, writes block until more data is read from the input side.
     *  <li>If the current thread is interrupted while blocked, an {@link InterruptedIOException} is thrown.
     *  <li>After the input side side is closed, trying to write more data will generate an {@link IOException}.
     * </ul>
     *
     * @return output side of the piped stream
     */
    public Output getOutputStream() {
        return this.output;
    }

// Input methods

    private synchronized int read() throws IOException {
        assert this.check();

        // Wait for data
        if (!this.waitForInput())
            return -1;
        assert this.len > 0;

        // Grab the first byte
        final int b = this.buf[off] & 0xff;
        this.off = (this.off + 1) % this.buf.length;
        this.len--;

        // Wakeup waiting writers if we were previously full
        if (this.len + 1 == this.buf.length)
            this.notifyAll();

        // Done
        return b;
    }

    private synchronized int read(byte[] b, int off, int len) throws IOException {

        // Check stuff
        assert this.check();
        if (off < 0 || len < 0 || off + len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (this.inputClosed)
            throw new IOException("input closed");
        if (len == 0)
            return 0;

        // Wait for data
        if (!this.waitForInput())
            return -1;
        assert this.len > 0;

        // Possibly copy two chunks: from buf[off] to end of buf, then from buf[0] to end of available data
        int total = 0;
        for (int i = 0; i < 2; i++) {

            // Copy a contiguous range of bytes
            final int num = Math.min(len, Math.min(this.len, this.buf.length - this.off));
            if (num == 0)
                break;
            System.arraycopy(this.buf, this.off, b, off, num);
            off += num;
            len -= num;
            this.off = (this.off + num) % this.buf.length;
            this.len -= num;
            total += num;
        }

        // Wakeup waiting writers if we were previously full
        if (this.len + total == this.buf.length)
            this.notifyAll();

        // Done
        return total;
    }

    private synchronized long skip(long n) throws IOException {

        // Check stuff
        assert this.check();
        if (this.inputClosed)
            throw new IOException("input closed");
        if (n <= 0)
            return 0;

        // Discard data
        final int skip = (int)Math.min(this.len, n);
        this.off = (this.off + skip) % this.buf.length;
        this.len -= skip;

        // Wakeup waiting writers if we were previously full
        if (this.len + skip == this.buf.length)
            this.notifyAll();

        // Done
        return skip;
    }

    private synchronized int available() throws IOException {
        assert this.check();
        if (this.inputClosed)
            throw new IOException("input closed");
        return this.len;
    }

    private synchronized void closeInput() {
        assert this.check();
        if (!this.inputClosed) {
            this.inputClosed = true;
            this.notifyAll();
        }
    }

// Output methods

    private synchronized void write(int b) throws IOException {
        assert this.check();

        // Wait for room
        this.waitForOutput();
        assert this.len < this.buf.length;

        // Add byte
        this.buf[(this.off + this.len) % this.buf.length] = (byte)b;
        this.len++;

        // Wakeup waiting readers if we were previously empty
        if (this.len == 1)
            this.notifyAll();
    }

    private synchronized void write(byte[] b, int off, int len) throws IOException {

        // Check stuff
        assert this.check();
        if (off < 0 || len < 0 || off + len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (this.outputClosed)
            throw new IOException("output closed");

        // Possibly copy two chunks: from buf[off + len] to end of buf, then from buf[0] to buf[off]
        while (len > 0) {

            // Wait for room
            this.waitForOutput();
            assert this.len < this.buf.length;

            // Copy a contiguous range of bytes
            final int soff = (this.off + this.len) % this.buf.length;                   // starting offset for new data
            final int num = Math.min(len, (soff < this.off ? this.off : this.buf.length) - soff);
            System.arraycopy(b, off, this.buf, soff, num);
            off += num;
            len -= num;
            this.len += num;

            // Wakeup waiting readers if we were previously empty
            if (this.len == num)
                this.notifyAll();
        }
    }

    private synchronized void closeOutput() {
        assert this.check();
        if (!this.outputClosed) {
            this.outputClosed = true;
            this.notifyAll();
        }
    }

// Internal methods

    private boolean waitForInput() throws IOException {
        assert Thread.holdsLock(this);
        while (true) {
            assert this.check();
            if (this.inputClosed)
                throw new IOException("input closed");
            if (this.len > 0)
                return true;
            if (this.outputClosed)
                return false;
            try {
                this.wait();
            } catch (InterruptedException e) {
                throw (InterruptedIOException)new InterruptedIOException().initCause(e);
            }
        }
    }

    private void waitForOutput() throws IOException {
        assert Thread.holdsLock(this);
        while (true) {
            assert this.check();
            if (this.outputClosed)
                throw new IOException("output closed");
            if (this.inputClosed)
                throw new IOException("input closed");
            if (this.len < this.buf.length)
                return;
            try {
                this.wait();
            } catch (InterruptedException e) {
                throw (InterruptedIOException)new InterruptedIOException().initCause(e);
            }
        }
    }

    private boolean check() {
        assert this.buf.length > 0;
        assert this.off >= 0;
        assert this.off < this.buf.length;
        assert this.len >= 0;
        assert this.len <= this.buf.length;
        return true;
    }

// Input class

    /**
     * The {@link InputStream} associated with a {@link PipedStreams} instance.
     *
     * @see PipedStreams#getInputStream
     */
    public final class Input extends InputStream implements HasPipedStreams {

        private Input() {
        }

    // HasPipedStreams

        @Override
        public PipedStreams getPipedStreams() {
            return PipedStreams.this;
        }

    // InputStream

        @Override
        public int read() throws IOException {
            return PipedStreams.this.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return PipedStreams.this.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return PipedStreams.this.skip(n);
        }

        @Override
        public int available() throws IOException {
            return PipedStreams.this.available();
        }

        @Override
        public void close() {
            PipedStreams.this.closeInput();
        }
    }

// Output class

    /**
     * The {@link OutputStream} associated with a {@link PipedStreams} instance.
     *
     * @see PipedStreams#getOutputStream
     */
    public final class Output extends OutputStream implements HasPipedStreams {

        private Output() {
        }

    // HasPipedStreams

        @Override
        public PipedStreams getPipedStreams() {
            return PipedStreams.this;
        }

    // OutputStream

        @Override
        public void write(int b) throws IOException {
            PipedStreams.this.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            PipedStreams.this.write(b, off, len);
        }

        @Override
        public void close() {
            PipedStreams.this.closeOutput();
        }
    }

// HasPipedStreams

    /**
     * Interface implemented by the {@link InputStream} and {@link OutputStream}
     * associated with a {@link PipedStreams} instance.
     */
    public interface HasPipedStreams {

        /**
         * Get this stream's originating {@link PipedStreams} instance.
         */
        PipedStreams getPipedStreams();
    }
}
