
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * <p><b>Synchronous Close</b>
 *
 * <p>
 * Normally, an {@link OutputStream} will not return from {@link #close} until all data has been written out
 * (e.g., to a destination file). However, due to thread concurrency, the reader thread associated with this
 * {@link NullModemOutputStream} could still be reading when {@link #close} returns. If this is a problem,
 * you can use {@link #setSynchronousClose setSynchronousClose()} to force {@link #close} to block until the
 * reader has returned from {@link ReadCallback#readFrom readFrom()}.
 *
 * @since 1.0.82
 */
public class NullModemOutputStream extends FilterOutputStream {

    private final AtomicReference<Throwable> error = new AtomicReference<>();

    private boolean synchronousClose;
    private boolean readerFinished;

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
        super(new PipedStreams().getOutputStream());

        // Sanity check
        if (reader == null)
            throw new IllegalArgumentException("null reader");
        if (executor == null)
            throw new IllegalArgumentException("null executor");

        // Launch reader task
        final InputStream input = this.getPipedStreams().getInputStream();
        executor.execute(() -> {
            try {
                reader.readFrom(input);
            } catch (Throwable t) {
                this.error.compareAndSet(null, t);
                throw ThrowableUtil.<RuntimeException>maskException(t);
            } finally {
                synchronized (this) {
                    this.readerFinished = true;
                    this.notifyAll();
                }
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }

// Properties

    /**
     * Get whether {@link #close} should block until the reader has finished reading (or thrown an exception).
     *
     * <p>
     * Default is false.
     *
     * @return true if {@link #close} should block until the reader has finished, otherwise false
     */
    public boolean isSynchronousClose() {
        return this.synchronousClose;
    }

    /**
     * Set whether {@link #close} should block until the reader has finished reading (or thrown an exception).
     *
     * @param synchronousClose true to make {@link #close} block until the reader finishes, false to make it return immediately
     */
    public void setSynchronousClose(final boolean synchronousClose) {
        this.synchronousClose = synchronousClose;
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
        NullUtil.wrap(this.error, () -> this.out.write(b, off, len));
    }

    @Override
    public void flush() throws IOException {
        NullUtil.checkError(this.error);
        NullUtil.wrap(this.error, super::flush);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If {@linkplain #setSynchronousClose synchronous close} is enabled, this method will block until the reader
     * thread has finished reading (or thrown an exception). Otherwise, this method returns immediately.
     *
     * <p>
     * If the current thread is interrupted during a synchronous close, an {@link IOException} is thrown.
     *
     * @see #isSynchronousClose
     */
    @Override
    public void close() throws IOException {
        this.error.set(null);                       // avoid redundant exception being thrown by flush()
        super.close();
        if (this.synchronousClose) {
            try {
                this.waitForReader();
            } catch (InterruptedException e) {
                throw new IOException("interrupted while waiting for reader to finish", e);
            }
        }
    }

    protected synchronized void waitForReader() throws InterruptedException {
        while (!this.readerFinished)
            this.wait();
    }

// Subclass Methods

    /**
     * Get the {@link PipedStreams} associated with this instance.
     *
     * @return the underlying {@link PipedStreams}
     */
    protected PipedStreams getPipedStreams() {
        return ((PipedStreams.Output)this.out).getPipedStreams();
    }
}
