
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.dellroad.stuff.java.CheckedExceptionWrapper;
import org.dellroad.stuff.java.Predicate;
import org.dellroad.stuff.java.TimedWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OutputStream} that performs writes using a background thread, so that
 * write, flush, and close operations never block.
 *
 * <p>
 * If the underlying output stream throws an {@link IOException} during any operation,
 * this instance will re-throw the exception for all subsequent operations.
 *
 * <p>
 * Instances use an internal buffer whose size is configured at construction time;
 * if the buffer overflows, a {@link BufferOverflowException} is thrown. Alternately,
 * if a buffer size of zero is configured, the internal buffer will expand automatically as needed
 * (up to 2<sup>31</sup> bytes).
 * However, this creates a memory leak if the underlying {@link OutputStream} blocks indefinitely.
 *
 * <p>
 * Instances of this class are thread safe, and moreover writes are atomic: if multiple threads are writing
 * at the same time the bytes written in any single method invocation are written contiguously to the
 * underlying output.
 */
public class AsyncOutputStream extends FilterOutputStream {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final int MIN_BUFFER_SIZE = 20;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 32;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String threadName;
    private byte[] buf;                 // output buffer
    private int count;                  // number of bytes in output buffer ready to be written
    private int flushMark = -1;         // buffer byte at which a flush is requested, or -1 if none
    private Thread thread;              // async writer thread
    private IOException exception;      // exception caught by async thread
    private boolean closed;             // this instance has been close()'d
    private boolean expand;             // whether to auto-expand buffer as needed

    /**
     * Convenience constructor for when an auto-expanding buffer is desired and a default thread name is to be used.
     *
     * @param out     underlying output stream
     * @throws IllegalArgumentException if {@code out} is null
     */
    public AsyncOutputStream(OutputStream out) {
        this(out, 0, AsyncOutputStream.class.getSimpleName() + "-" + AsyncOutputStream.COUNTER.incrementAndGet());
    }

    /**
     * Convenience constructor for when an auto-expanding buffer is desired.
     *
     * @param out     underlying output stream
     * @param name    name for this instance; used to create the name of the background thread
     * @throws IllegalArgumentException if {@code out} or {@code name} is null
     */
    public AsyncOutputStream(OutputStream out, String name) {
        this(out, 0, name);
    }

    /**
     * Constructor.
     *
     * @param out     underlying output stream
     * @param bufsize maximum number of bytes we can buffer, or zero for an auto-expanding buffer that has no fixed limit
     * @param name    name for this instance; used to create the name of the background thread
     * @throws IllegalArgumentException if {@code out} or {@code name} is null
     * @throws IllegalArgumentException if {@code bufsize} is negative
     */
    public AsyncOutputStream(OutputStream out, int bufsize, String name) {
        super(out);
        if (out == null)
            throw new IllegalArgumentException("null output");
        if (name == null)
            throw new IllegalArgumentException("null name");
        if (bufsize < 0)
            throw new IllegalArgumentException("invalid bufsize " + bufsize);
        this.threadName = name;
        this.expand = bufsize == 0;
        this.buf = new byte[Math.min(Math.max(bufsize, MIN_BUFFER_SIZE), MAX_BUFFER_SIZE)];
    }

    /**
     * Write data.
     *
     * <p>
     * This method will never block. To effect a normal blocking write, use {@link #waitForSpace} first.
     *
     * @param b byte to write (lower 8 bits)
     * @throws IOException             if an exception has been thrown by the underlying stream
     * @throws IOException             if this instance has been closed
     * @throws BufferOverflowException if the buffer does not have room for the new byte
     */
    @Override
    public void write(int b) throws IOException {
        this.write(new byte[] { (byte)b }, 0, 1);
    }

    /**
     * Write data.
     *
     * <p>
     * This method will never block. To effect a normal blocking write, invoke {@link #waitForSpace} first.
     *
     * @param data bytes to write
     * @param off  starting offset in buffer
     * @param len  number of bytes to write
     * @throws IOException              if an exception has been thrown by the underlying stream
     * @throws IOException              if this instance has been closed
     * @throws BufferOverflowException  if the buffer does not have room for the new data
     * @throws IllegalArgumentException if {@code len} is negative
     */
    @Override
    public synchronized void write(byte[] data, int off, int len) throws IOException {

        // Check exception conditions
        this.checkExceptions();
        if (len < 0)
            throw new IllegalArgumentException("len = " + len);
        if (len == 0)
            return;
        if (this.count + len > this.buf.length) {
            if (!this.expand) {
                throw new BufferOverflowException(this.count + " + " + len + " = " + (this.count + len)
                  + " byte(s) would exceed the " + this.buf.length + " byte buffer");
            }
            this.resizeBuffer(Math.max(this.count + len, this.buf.length * 2));

            final byte[] newBuf = new byte[Math.max(this.count + len, this.buf.length * 2)];
            System.arraycopy(this.buf, 0, newBuf, 0, this.count);
            this.buf = newBuf;
        }

        // Add data to buffer
        System.arraycopy(data, off, this.buf, this.count, len);
        this.count += len;

        // Wakeup writer thread
        this.startThreadIfNecessary();
        this.notifyAll();
    }

    /**
     * Flush output. This method will cause the underlying stream to be flushed once all of the data written to this
     * instance at the time this method is invoked has been written to it.
     *
     * <p>
     * If additional data is written and then a second flush is requested before the first flush has actually occurred,
     * the first flush will be canceled and only the second flush will be applied. Normally this is not a problem because
     * the act of writing more data and then flushing forces earlier data to be flushed as well.
     *
     * <p>
     * This method will never block. To block until the underlying flush operation completes, invoke {@link #waitForIdle}.
     *
     * @throws IOException if this instance has been closed
     * @throws IOException if an exception has been detected on the underlying stream
     * @throws IOException if the current thread is interrupted; the nested exception will an {@link InterruptedException}
     */
    @Override
    public synchronized void flush() throws IOException {
        this.checkExceptions();
        this.flushMark = this.count;
        this.startThreadIfNecessary();
        this.notifyAll();                               // wake up writer thread
    }

    /**
     * Close this instance. This will (eventually) close the underlying output stream.
     *
     * <p>
     * If this instance has already been closed, nothing happens.
     *
     * <p>
     * This method will never block. To block until the underlying close operation completes, invoke {@link #waitForIdle}.
     *
     * @throws IOException if an exception has been detected on the underlying stream
     */
    @Override
    public synchronized void close() throws IOException {
        if (this.closed)
            return;
        this.closed = true;
        this.startThreadIfNecessary();
        this.notifyAll();                               // wake up writer thread
    }

    /**
     * Get the exception thrown by the underlying output stream, if any.
     *
     * @return thrown exception, or {@code null} if none has been thrown by the underlying stream
     */
    public synchronized IOException getException() {
        return this.exception;
    }

    /**
     * Get the capacity of this instance's output buffer.
     *
     * <p>
     * If a (fixed) non-zero value was given at construction time, this will return that value.
     *
     * @return current output buffer capacity
     */
    public synchronized int getBufferSize() {
        return this.buf.length;
    }

    /**
     * Get the number of free bytes remaining in the output buffer.
     *
     * @return current number of available bytes in the output buffer
     * @throws IOException              if this instance is or has been closed
     * @throws IOException              if an exception has been detected on the underlying stream
     * @see #waitForSpace
     */
    public synchronized int availableBufferSpace() throws IOException {
        this.checkExceptions();
        return this.buf.length - this.count;
    }

    /**
     * Determine if there is outstanding work still to be performed (writes, flushes, and/or close operations)
     * by the background thread.
     *
     * @return true if work remains to be done
     * @throws IOException              if this instance is or has been closed
     * @throws IOException              if an exception has been detected on the underlying stream
     * @see #waitForIdle
     */
    public synchronized boolean isWorkOutstanding() throws IOException {
        this.checkExceptions();
        return this.threadHasWork();
    }

    /**
     * Wait for buffer space availability.
     *
     * <p>
     * If a zero buffer size was configured at construction time, indicating an auto-expanding buffer,
     * this will return immediately.
     *
     * @param numBytes amount of buffer space required
     * @param timeout  maximum time to wait in milliseconds, or zero for infinite
     * @return true if space was found, false if time expired
     * @throws IOException              if this instance is or has been closed
     * @throws IOException              if an exception has been detected on the underlying stream
     * @throws IllegalArgumentException if {@code numBytes} is greater than the configured buffer size
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws InterruptedException     if the current thread is interrupted
     * @see #availableBufferSpace
     */
    public synchronized boolean waitForSpace(final int numBytes, long timeout) throws IOException, InterruptedException {
        if (this.expand)
            return true;
        if (numBytes > this.buf.length)
            throw new IllegalArgumentException("numBytes (" + numBytes + ") > buffer size (" + this.buf.length + ")");
        return this.waitForPredicate(timeout, new Predicate() {
            @Override
            public boolean test() {
                return AsyncOutputStream.this.buf.length - AsyncOutputStream.this.count >= numBytes;
            }
        });
    }

    /**
     * Wait for all outstanding work to complete.
     *
     * @param timeout maximum time to wait in milliseconds, or zero for infinite
     * @return true for success, false if time expired
     * @throws IOException              if this instance is or has been closed
     * @throws IOException              if an exception has been detected on the underlying stream
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws InterruptedException     if the current thread is interrupted
     * @see #isWorkOutstanding
     */
    public synchronized boolean waitForIdle(long timeout) throws IOException, InterruptedException {
        return this.waitForPredicate(timeout, new Predicate() {
            @Override
            public boolean test() {
                return !AsyncOutputStream.this.threadHasWork();
            }
        });
    }

    /**
     * Check for exceptions.
     *
     * @throws IOException if this instance has been closed
     * @throws IOException if an exception has been detected on the underlying stream
     */
    private void checkExceptions() throws IOException {
        if (this.closed)
            throw new IOException("instance has been closed");
        if (this.exception != null)
            throw new IOException("exception from underlying output stream", this.exception);
    }

    /**
     * Determine if there is outstanding work still to be performed (writes, flushes, and/or close operations)
     * by the background thread.
     */
    private boolean threadHasWork() {
        return this.count > 0 || this.flushMark != -1 || this.closed;
    }

    /**
     * Start the background thread if necessary.
     */
    private void startThreadIfNecessary() {
        assert Thread.holdsLock(this);
        if (this.thread == null && this.threadHasWork()) {
            this.thread = new Thread(this.threadName) {
                @Override
                public void run() {
                    AsyncOutputStream.this.threadMain();
                }
            };
            this.thread.setDaemon(true);
            this.thread.start();
        }
    }

    /**
     * Resize internal buffer.
     */
    private /*synchronized*/ void resizeBuffer(int size) {
        assert Thread.holdsLock(this);
        assert this.count <= size;
        size = Math.min(size, MAX_BUFFER_SIZE);
        size = Math.max(size, MIN_BUFFER_SIZE);
        final byte[] newBuf = new byte[size];
        System.arraycopy(this.buf, 0, newBuf, 0, this.count);
        this.buf = newBuf;
    }

    /**
     * Writer thread main entry point.
     */
    private void threadMain() {
        try {
            this.runLoop();
        } catch (Throwable t) {
            synchronized (this) {
                this.exception = t instanceof IOException ? (IOException)t : new IOException("caught unexpected exception", t);
                this.notifyAll();                       // wake up sleepers in waitForSpace() and waitForIdle()
            }
        } finally {
            synchronized (this) {
                this.thread = null;
                this.notifyAll();                       // wake up sleepers in waitForIdle()
            }
        }
    }

    /**
     * Async writer thread main loop.
     */
    private void runLoop() throws IOException, InterruptedException {
        while (true) {

            // Wait for something to do
            synchronized (this) {
                while (!this.threadHasWork())
                    this.wait();                        // will be woken up by write(), flush(), or close()
            }

            // Determine what needs to be done
            byte[] currbuf;
            int wlen;
            boolean flush;
            boolean close;
            synchronized (this) {
                currbuf = this.buf;
                wlen = this.count;
                flush = this.flushMark == 0;
                close = this.closed;
            }

            // First priority: any data to write?
            if (wlen > 0) {

                // Write data
                this.out.write(currbuf, 0, wlen);

                // Shift data in buffer
                synchronized (this) {
                    System.arraycopy(this.buf, wlen, this.buf, 0, this.count - wlen);
                    this.count -= wlen;
                    if (this.flushMark != -1)
                        this.flushMark = Math.max(0, this.flushMark - wlen);
                    if (this.count <= this.buf.length >> 7)
                        this.resizeBuffer(this.count);
                    this.notifyAll();                   // wake up sleepers in waitForSpace() and waitForIdle()
                }
                continue;
            }

            // Second priority: is a flush required?
            if (flush) {

                // Flush output
                this.out.flush();

                // Update flush mark
                synchronized (this) {
                    if (this.flushMark == 0) {
                        this.flushMark = -1;
                        this.notifyAll();               // wake up sleepers in waitForIdle()
                    }
                }
                continue;
            }

            // Third priority:  is a close required?
            if (close) {
                this.out.close();
                break;
            }
        }
    }

    /**
     * Wait for some condition to become true. Of course somebody has to wake us up when it becomes true.
     */
    private synchronized boolean waitForPredicate(long timeout, final Predicate predicate)
      throws IOException, InterruptedException {
        try {
            return TimedWait.wait(this, timeout, () -> {
                try {
                    this.checkExceptions();
                } catch (IOException e) {
                    throw new CheckedExceptionWrapper(e);
                }
                return predicate.test();
            });
        } catch (CheckedExceptionWrapper e) {
            throw (IOException)e.getException();
        }
    }
}

