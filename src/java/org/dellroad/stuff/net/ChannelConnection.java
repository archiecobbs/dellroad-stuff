
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
 * <b>Locking</b>
 *
 * <p>
 * All access to this class must be with the associated {@link ChannelNetwork} instance locked.
 */
public abstract class ChannelConnection implements SelectorSupport.IOHandler {

    /**
     * Minimum buffer size to use a direct buffer.
     */
    private static final int MIN_DIRECT_BUFFER_SIZE = 128;

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final ChannelNetwork network;
    protected final String peer;
    protected final SelectableChannel inputChannel;
    protected final SelectableChannel outputChannel;
    protected final SelectionKey inputSelectionKey;
    protected final SelectionKey outputSelectionKey;        // same as inputSelectionKey if inputChannel == outputChannel

    private final ArrayDeque<ByteBuffer> output = new ArrayDeque<>();

    private ByteBuffer inbuf;
    private long queueSize;                                 // invariant: always equals the total number of bytes in 'output'
    private long lastActiveTime;
    private boolean readingLength;                          // indicates 'inbuf' is reading the message length (4 bytes)
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
     */
    public String getPeer() {
        return this.peer;
    }

    /**
     * Get the associated input channel.
     */
    public SelectableChannel getInputChannel() {
        return this.inputChannel;
    }

    /**
     * Get the associated output channel.
     */
    public SelectableChannel getOutputChannel() {
        return this.outputChannel;
    }

    /**
     * Get time in milliseconds since last activity.
     */
    public long getIdleTime() {
        return (System.nanoTime() - this.lastActiveTime) / 1000000L;
    }

    /**
     * Enqueue an outgoing message on this connection.
     *
     * @param buf outgoing data
     * @return true if message was enqueued, false if output buffer was full
     */
    public boolean output(ByteBuffer buf) {

        // Sanity check
        assert Thread.holdsLock(this.network);
        if (buf == null)
            throw new IllegalArgumentException("null buf");

        // Avoid anyone else mucking with my buffer position, etc.
        buf = buf.asReadOnlyBuffer();

        // Check output queue capacity
        final int length = buf.remaining();
        final int increment = length + 4;
        if (this.queueSize + increment > this.network.getMaxOutputQueueSize())
            return false;

        // Add to queue
        this.output.add((ByteBuffer)ByteBuffer.allocate(4).putInt(length).flip());
        this.output.add(buf);
        this.queueSize += increment;

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
        this.network.handleConnectionClosed(this);
    }

// Subclass Methods

    /**
     * Update selected keys.
     *
     * <p>
     * The implementation in {@link ChannelConnection} selects for read always and write
     * if the output queue is non-empty.
     */
    protected void updateSelection() {
        this.network.selectFor(this.inputSelectionKey, SelectionKey.OP_READ, true);
        this.network.selectFor(this.outputSelectionKey, SelectionKey.OP_WRITE, !this.output.isEmpty());
    }

    /**
     * Restart the idle timer.
     */
    protected void restartIdleTimer() {
        this.lastActiveTime = System.nanoTime();
    }

// I/O Ready Conditions

    private void handleReadable() throws IOException {
        while (true) {

            // Update timestamp
            this.restartIdleTimer();

            // Read bytes
            final long len = ((ReadableByteChannel)this.inputChannel).read(this.inbuf);
            if (len == -1)
                throw new EOFException("connection closed");

            // Is the message (or length header) still incomplete?
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
                this.inbuf = length >= MIN_DIRECT_BUFFER_SIZE ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
                this.readingLength = false;
                continue;
            }

            // Deliver the completed message
            this.network.handleMessage(this, this.inbuf);

            // Set up for reading next length header
            this.inbuf = ByteBuffer.allocate(4);
            this.readingLength = true;
        }

        // Done
        this.restartIdleTimer();
    }

    private void handleWritable() throws IOException {

        // Write more data, if present
        boolean queueBecameEmpty = false;
        if (!this.output.isEmpty()) {

            // Write data
            final long written = ((GatheringByteChannel)this.outputChannel).write(
              this.output.toArray(new ByteBuffer[this.output.size()]));
            this.queueSize -= written;

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
            this.network.handleOutputQueueEmpty(this);
    }

// Housekeeping

    /**
     * Perform housekeeping.
     *
     * The implementation in {@link ChannelConnection} checks the max idle time.
     */
    protected void performHousekeeping() throws IOException {
        assert Thread.holdsLock(this.network);
        assert this.network.isServiceThread();
        if (this.getIdleTime() >= this.network.getMaxIdleTime())
            throw new IOException("connection idle timeout after " + this.getIdleTime() + "ms");
    }
}

