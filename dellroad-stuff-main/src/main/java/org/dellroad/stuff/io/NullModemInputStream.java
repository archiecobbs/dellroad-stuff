
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.dellroad.stuff.java.ThrowableUtil;

/**
 * Presents an {@link InputStream} interface given a {@link WriteCallback} that can write to an {@link OutputStream}.
 *
 * <p>
 * A background process, initated by a provided {@link Executor}, invokes the {@link WriteCallback} to write
 * to an {@link OutputStream} that relays whatever is written to this {@link InputStream}.
 *
 * <p><b>Exceptions</b>
 *
 * <p>
 * Regarding this {@link InputStream}:
 *
 * <ul>
 *  <li>If the {@link WriteCallback} throws an {@link IOException}, any subsequent read from this {@link InputStream}
 *      will throw an {@link IOException}, with the original {@linkplain ThrowableUtil#appendStackFrames appended to it}.
 * </ul>
 *
 * <p>
 * Regarding the {@link OutputStream} provided to the {@link WriteCallback}:
 *
 * <ul>
 *  <li>If this {@link InputStream} is {@link InputStream#close close()}'ed, any subsequent write to the {@link OutputStream}
 *      by the {@link WriteCallback} will generate an {@link IOException}.
 * </ul>
 *
 * <p>
 * Note: This class uses {@link PipedInputStream} and {@link PipedOutputStream} under the covers, so instances should not
 * be shared by multiple threads or they might be considered "broken".
 *
 * @since 1.0.74
 */
public class NullModemInputStream extends FilterInputStream {

    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final PipedOutputStream output;

    /**
     * Constructor that uses a background thread for writing.
     *
     * <p>
     * Delegates to {@link #NullModemInputStream(WriteCallback, Executor)},
     * passing an executor that creates a dedicated daemon {@link Thread} with the given name.
     *
     * @param reader callback that writes the data to be read
     * @param threadName name for the background thread to be created
     */
    public NullModemInputStream(WriteCallback writer, String threadName) {
        this(writer, NullUtil.newThreadExecutor(threadName));
    }

    /**
     * Constructor.
     *
     * <p>
     * The {@code writer}'s {@link WriteCallback#writeTo writeTo()} method will be invoked (once)
     * asynchronously in a dedicated writer thread. This instance will produce whatever data is
     * written by {@code writer} to the provided {@link java.io.OutputStream}.
     *
     * @param writer callback that writes the data to be read
     * @param executor executes writing process in the background
     * @throws IllegalArgumentException if any parameter is null
     */
    public NullModemInputStream(WriteCallback writer, Executor executor) {
        super(new PipedInputStream());

        // Sanity check
        if (writer == null)
            throw new IllegalArgumentException("null writer");
        if (executor == null)
            throw new IllegalArgumentException("null executor");

        // Create other end of pipe
        try {
            this.output = new PipedOutputStream(this.getPipedInputStream());
        } catch (IOException e) {
            throw new RuntimeException("unexpected exception", e);
        }

        // Launch writer task
        executor.execute(() -> {
            try {
                writer.writeTo(output);
                output.flush();
                output.close();
            } catch (Throwable t) {
                this.error.compareAndSet(null, t);
                throw ThrowableUtil.<RuntimeException>maskException(t);
            } finally {
                try {
                    output.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }

// Wrapper Methods

    @Override
    public int read() throws IOException {
        NullUtil.checkError(this.error);
        return NullUtil.wrapInt(this.error, super::read);
    }

    @Override
    public int read(byte[] b) throws IOException {
        NullUtil.checkError(this.error);
        return NullUtil.wrapInt(this.error, () -> super.read(b));
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        NullUtil.checkError(this.error);
        return NullUtil.wrapInt(this.error, () -> super.read(b, off, len));
    }

    @Override
    public long skip(long n) throws IOException {
        NullUtil.checkError(this.error);
        return NullUtil.wrapLong(this.error, () -> super.skip(n));
    }

    @Override
    public int available() throws IOException {
        NullUtil.checkError(this.error);
        return NullUtil.wrapInt(this.error, super::available);
    }

    @Override
    public void reset() throws IOException {
        NullUtil.checkError(this.error);
        NullUtil.wrap(this.error, super::reset);
    }

// Internal Methods

    /**
     * Get the wrapped stream cast as a {@link PipedInputStream}.
     *
     * @return the underlying {@link PipedInputStream}
     */
    protected PipedInputStream getPipedInputStream() {
        return (PipedInputStream)this.in;
    }

    /**
     * Ensure input stream is closed when this instance is no longer referenced.
     *
     * <p>
     * This ensures the writer thread wakes up (and exits, avoiding a memory leak) when an instance of this class
     * is created but never read from.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            try {
                this.getPipedInputStream().close();
            } catch (IOException e) {
                // ignore
            }
        } finally {
            super.finalize();
        }
    }
}
