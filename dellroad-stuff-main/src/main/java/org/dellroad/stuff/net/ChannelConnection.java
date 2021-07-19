
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support superclass for {@link ChannelNetwork} connections.
 *
 * <p>
 * <b>Locking</b>
 *
 * <p>
 * All access to this class must be with the associated {@link ChannelNetwork} instance locked.
 */
public abstract class ChannelConnection implements SelectorSupport.IOHandler {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final ChannelNetwork network;
    protected final String peer;
    protected final SelectableChannel inputChannel;
    protected final SelectableChannel outputChannel;
    protected final SelectionKey inputSelectionKey;
    protected final SelectionKey outputSelectionKey;        // same as inputSelectionKey if inputChannel == outputChannel

    // Partially constructed incoming message
    private ByteBuffer inbuf;
    private boolean readingLength;                          // indicates 'inbuf' is reading the message length (4 bytes)

    // Input queue
    private final ArrayDeque<ByteBuffer> input = new ArrayDeque<>();
    private long inputQueueSize;                            // invariant: always equals the total number of bytes in 'input'

    // Output queue
    private final ArrayDeque<ByteBuffer> output = new ArrayDeque<>();
    private long outputQueueSize;                           // invariant: always equals the total number of bytes in 'output'
    private boolean outputQueueEmpty;                       // there is a pending notification that the output queue is empty

    // Misc state
    private volatile long lastActiveTime;
    private boolean closed;

// Constructors

    protected ChannelConnection(ChannelNetwork network, String peer, SelectableChannel channel) throws IOException {
        this(network, peer, channel, channel);
    }

    protected ChannelConnection(ChannelNetwork network, String peer,
      SelectableChannel inputChannel, SelectableChannel outputChannel) throws IOException {

        // Sanity check
        if (network == null)
            throw new IllegalArgumentException("null network");
        if (peer == null)
            throw new IllegalArgumentException("null peer");
        if (!(inputChannel instanceof ReadableByteChannel))
            throw new IllegalArgumentException("inputChannel must be a ReadableByteChannel");
        if (!(outputChannel instanceof GatheringByteChannel))
            throw new IllegalArgumentException("inputChannel must be a GatheringByteChannel");

        // Initialize
        this.network = network;
        this.peer = peer;
        this.inputChannel = inputChannel;
        this.outputChannel = outputChannel;
        this.restartIdleTimer();

        // Set up selection
        this.inputSelectionKey = this.network.createSelectionKey(this.inputChannel, this);
        this.outputSelectionKey = this.outputChannel != this.inputChannel ?
          this.network.createSelectionKey(this.outputChannel, this) : this.inputSelectionKey;
        this.updateSelection();

        // Initialize input state
        this.inbuf = ByteBuffer.allocate(4);
        this.readingLength = true;
    }

    /**
     * Get remote peer's identity.
     *
     * @return remote peer for this connection
     */
    public String getPeer() {
        return this.peer;
    }

    /**
     * Get the associated input channel.
     *
     * @return input channel for this connection; could be same as the {@linkplain #getOutputChannel output channel}
     */
    public SelectableChannel getInputChannel() {
        return this.inputChannel;
    }

    /**
     * Get the associated output channel.
     *
     * @return input channel for this connection; could be same as the {@linkplain #getInputChannel input channel}
     */
    public SelectableChannel getOutputChannel() {
        return this.outputChannel;
    }

    /**
     * Get time in milliseconds since last activity.
     *
     * @return idle time in milliseconds
     */
    public long getIdleTime() {
        return (System.nanoTime() - this.lastActiveTime) / 1000000L;
    }

    /**
     * Enqueue an outgoing message on this connection.
     *
     * @param buf outgoing data
     * @return true if message was enqueued, false if output buffer was full or connection closed
     */
    protected boolean output(ByteBuffer buf) {

        // Sanity check
        assert Thread.holdsLock(this.network);
        if (buf == null)
            throw new IllegalArgumentException("null buf");
        if (this.closed)
            return false;

        // Avoid anyone else mucking with my buffer position, etc.
        buf = buf.asReadOnlyBuffer();

        // Check output queue capacity
        final int length = buf.remaining();
        final int increment = length + 4;
        if (this.outputQueueSize + increment > this.network.getMaxOutputQueueSize())
            return false;

        // Add to queue
        this.output.add((ByteBuffer)ByteBuffer.allocate(4).putInt(length).flip());
        this.output.add(buf);
        this.outputQueueSize += increment;

        // Ensure we are notified when output is writable
        this.updateSelection();

        // Done
        this.restartIdleTimer();
        return true;
    }

// Object

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[peer=" + this.peer + ",closed=" + this.closed + "]";
    }

// IOHandler

    @Override
    public void serviceIO(SelectionKey key) throws IOException {
        assert this.network.isServiceThread();
        assert Thread.holdsLock(this.network);
        if (key.isReadable())
            this.handleReadable();
        if (key.isWritable())
            this.handleWritable();
    }

    @Override
    public void close(Throwable cause) {
        assert Thread.holdsLock(this.network);
        if (this.closed)
            return;
        this.closed = true;
        if (this.log.isDebugEnabled())
            this.log.debug("closing " + this + (cause != null ? " due to " + cause : ""));
        try {
            this.inputChannel.close();
        } catch (IOException e) {
            // ignore
        }
        try {
            this.outputChannel.close();
        } catch (IOException e) {
            // ignore
        }
        this.inbuf = null;
        this.readingLength = false;
        this.input.clear();
        this.inputQueueSize = 0;
        this.output.clear();
        this.outputQueueSize = 0;
        this.outputQueueEmpty = false;
        this.network.handleConnectionClosed(this);
    }

// Subclass Methods

    /**
     * Update selected keys.
     *
     * <p>
     * The implementation in {@link ChannelConnection} selects for read if the input queue is not full,
     * and for write if the output queue is non-empty.
     */
    protected void updateSelection() {
        this.network.selectFor(this.inputSelectionKey, SelectionKey.OP_READ, !this.inputQueueFull());
        this.network.selectFor(this.outputSelectionKey, SelectionKey.OP_WRITE, !this.output.isEmpty());
    }

    /**
     * Restart the idle timer.
     */
    protected void restartIdleTimer() {
        this.lastActiveTime = System.nanoTime();
    }

// Input Handling

    // Add buffer to input queue
    private void receiveBuffer(ByteBuffer buf) {
        assert Thread.holdsLock(this.network);

        // Add buffer to queue
        final boolean queueWasFull = this.inputQueueFull();
        final boolean queueWasEmpty = this.input.isEmpty();
        this.input.add(buf);
        this.inputQueueSize += buf.remaining();

        // If input queue became full, stop reading to create back-pressure on the network
        if (!queueWasFull && this.inputQueueFull())
            this.updateSelection();

        // If input queue became non-empty, wakeup handler thread so input can be delivered
        if (queueWasEmpty)
            this.network.notify();
    }

    /**
     * Grab the next available input buffer, if any.
     *
     * <p>
     * This method is invoked by {@link ChannelNetwork.HandlerThread}.
     *
     * @return next buffer if any, otherwise null
     */
    ByteBuffer pollForInputQueueNotEmpty() {
        assert Thread.holdsLock(this.network);

        // Anything there?
        final ByteBuffer buf = this.input.pollFirst();
        if (buf == null)
            return null;

        // Update total queue length in bytes
        final boolean queueWasFull = this.inputQueueFull();
        this.inputQueueSize -= buf.remaining();

        // If the input queue just became no longer full, enable reading again
        if (queueWasFull && !this.inputQueueFull())
            this.updateSelection();

        // Done
        return buf;
    }

    /**
     * Determine whether there is a pending notification that the output queue is empty.
     *
     * <p>
     * If this returns true, the pending notification is cleared.
     *
     * <p>
     * This method is invoked by {@link ChannelNetwork.HandlerThread}.
     */
    boolean pollForOutputQueueEmpty() {
        assert Thread.holdsLock(this.network);
        if (this.outputQueueEmpty) {
            this.outputQueueEmpty = false;
            return true;
        }
        return false;
    }

    private boolean inputQueueFull() {
        assert Thread.holdsLock(this.network);
        return this.inputQueueSize >= this.network.getMaxInputQueueSize();
    }

// I/O Ready Conditions

    private void handleReadable() throws IOException {
        assert Thread.holdsLock(this.network);
        assert this.network.isServiceThread();

        // Channels are non-blocking, so we keep reading until no more data is available or the input queue gets full
        while (true) {

            // If the input queue is full, don't read anything - hopefully the sender will get pushback and stop sending
            if (this.inputQueueFull()) {
                this.updateSelection();             // this shouldn't be necessary - but just in case we missed it before somehow
                break;
            }

            // Update timestamp
            this.restartIdleTimer();

            // Read bytes into input buffer
            final long len = ((ReadableByteChannel)this.inputChannel).read(this.inbuf);
            if (len == -1)
                throw new EOFException("connection closed");

            // Is the message (or length header) still incomplete? Then there's no more available data for now
            if (this.inbuf.hasRemaining())
                break;

            // Set up for reading
            this.inbuf.flip();

            // Completed length header?
            if (this.readingLength) {

                // Get and validate length
                assert this.inbuf.remaining() == 4;
                final int length = this.inbuf.getInt();
                if (length < 0 || length > this.network.getMaxMessageSize())
                    throw new IOException("rec'd message with bogus length " + length);

                // Set up for reading the actual message
                this.inbuf = length >= this.network.getMinDirectBufferSize() ?
                  ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
                this.readingLength = false;
                continue;
            }

            // Add the completed message to our input queue
            this.receiveBuffer(this.inbuf);

            // Set up for reading next length header
            this.inbuf = ByteBuffer.allocate(4);
            this.readingLength = true;
        }

        // Done
        this.restartIdleTimer();
    }

    private void handleWritable() throws IOException {
        assert Thread.holdsLock(this.network);
        assert this.network.isServiceThread();

        // Write more data, if present
        boolean queueBecameEmpty = false;
        if (!this.output.isEmpty()) {

            // Write data
            final long written = ((GatheringByteChannel)this.outputChannel).write(
              this.output.toArray(new ByteBuffer[this.output.size()]));
            this.outputQueueSize -= written;

            // Clear away empty buffers
            while (!this.output.isEmpty() && !this.output.peekFirst().hasRemaining())
                this.output.removeFirst();

            // Set flag if queue became empty
            queueBecameEmpty = this.output.isEmpty();
        }

        // If queue became empty, ensure we are no longer notified when output is writable
        if (queueBecameEmpty)
            this.updateSelection();

        // Update timestamp
        this.restartIdleTimer();

        // Notify client if queue became empty
        if (queueBecameEmpty)
            this.handleOutputQueueEmpty();
    }

    // Notify handler output queue is empty
    void handleOutputQueueEmpty() {
        if (!this.outputQueueEmpty) {
            this.outputQueueEmpty = true;
            this.network.notify();
        }
    }

// Housekeeping

    /**
     * Perform housekeeping.
     *
     * The implementation in {@link ChannelConnection} checks the max idle time.
     *
     * @throws IOException if an I/O error occurs (this connection will be closed)
     */
    protected void performHousekeeping() throws IOException {
        assert Thread.holdsLock(this.network);
        assert this.network.isServiceThread();
        if (this.getIdleTime() >= this.network.getMaxIdleTime())
            throw new IOException("connection idle timeout after " + this.getIdleTime() + "ms");
    }
}

