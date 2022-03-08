
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.dellroad.stuff.java.ThrowableUtil;

/**
 * Presents an {@link OutputStream} interface given a {@link ReadCallback} that can read from an {@link InputStream}.
 *
 * <p>
 * A background process, initated by a provided {@link Executor}, invokes the {@link ReadCallback} to read
 * from an {@link InputStream} which receives whatever is written to this {@link OutputStream}.
 *
 * <p><b>Exceptions</b>
 *
 * <p>
 * Regarding this {@link OutputStream}:
 *
 * <ul>
 *  <li>If the {@link ReadCallback} throws an {@link IOException}, any subsequent write to this {@link OutputStream}
 *      will throw an {@link IOException}, with the original {@linkplain ThrowableUtil#appendStackFrames appended to it}.
 *  <li>If the {@link ReadCallback} {@link InputStream#close close()}'es the {@link InputStream}, any subsequent write to this
 *      {@link OutputStream} will generate an {@link IOException}.
 * </ul>
 *
 * <p>
 * The {@link InputStream} provided to the {@link ReadCallback} should never throw any {@link IOException}, as long as
 * it is still open.
 *
 * <p>
 * Note: This class uses {@link PipedInputStream} and {@link PipedOutputStream} under the covers, so instances should not
 * be shared by multiple threads or they might be considered "broken".
 *
 * @since 1.0.82
 */
public class NullModemOutputStream extends FilterOutputStream {

    private final AtomicReference<Throwable> error = new AtomicReference<>();

// Constructors

    /**
     * Constructor that uses a background thread for reading.
     *
     * <p>
     * Delegates to {@link #NullModemOutputStream(ReadCallback, Executor)},
     * passing an executor that creates a dedicated daemon {@link Thread} with the given name.
     *
     * @param reader callback that reads the data written
     * @param threadName name for the background thread to be created
     */
    public NullModemOutputStream(ReadCallback reader, String threadName) {
        this(reader, NullUtil.newThreadExecutor(threadName));
    }

    /**
     * Constructor.
     *
     * <p>
     * The {@code reader}'s {@link ReadCallback#readFrom readFrom()} method will be invoked (once)
     * in an asynchronous thread created by {@code executor}. The {@link InputStream}
     * provided to it will produce whatever data is written to this instance.
     *
     * @param reader callback that reads the data written
     * @param executor executes reading process in the background
     * @throws IllegalArgumentException if any parameter is null
     */
    public NullModemOutputStream(ReadCallback reader, Executor executor) {
        super(new PipedOutputStream());

        // Sanity check
        if (reader == null)
            throw new IllegalArgumentException("null reader");
        if (executor == null)
            throw new IllegalArgumentException("null executor");

        // Create other end of pipe
        final PipedInputStream input;
        try {
            input = new PipedInputStream(this.getPipedOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("unexpected exception", e);
        }

        // Launch reader task
        executor.execute(() -> {
            try {
                reader.readFrom(input);
            } catch (Throwable t) {
                this.error.compareAndSet(null, t);
                throw ThrowableUtil.<RuntimeException>maskException(t);
            } finally {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }

// Wrapper Methods

    @Override
    public void write(int b) throws IOException {
        NullUtil.checkError(this.error);
        NullUtil.wrap(this.error, () -> super.write(b));
    }

    @Override
    public void write(byte[] b) throws IOException {
        NullUtil.checkError(this.error);
        NullUtil.wrap(this.error, () -> super.write(b));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        NullUtil.checkError(this.error);
        NullUtil.wrap(this.error, () -> super.write(b, off, len));
    }

    @Override
    public void flush() throws IOException {
        NullUtil.checkError(this.error);
        NullUtil.wrap(this.error, super::flush);
    }

    @Override
    public void close() throws IOException {
        this.error.set(null);                       // avoid redundant exception being thrown by flush()
        super.close();
    }

// Subclass Methods

    /**
     * Get the wrapped stream cast as a {@link PipedOutputStream}.
     *
     * @return the underlying {@link PipedOutputStream}
     */
    protected PipedOutputStream getPipedOutputStream() {
        return (PipedOutputStream)this.out;
    }

    /**
     * Ensure output stream is closed when this instance is no longer referenced.
     *
     * <p>
     * This ensures the reader thread wakes up (and exits, avoiding a memory leak) when an instance of this class
     * is created but never read from.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            try {
                this.getPipedOutputStream().close();
            } catch (IOException e) {
                // ignore
            }
        } finally {
            super.finalize();
        }
    }
}
