
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support superclass for {@link Network} implementations based on {@link java.nio.channels.SelectableChannel}s.
 * Uses Java NIO.
 */
public abstract class ChannelNetwork extends SelectorSupport implements Network {

    /**
     * Default maximum number of simultaneous connections ({@value #DEFAULT_MAX_CONNECTIONS}).
     *
     * @see #getMaxConnections
     */
    public static final int DEFAULT_MAX_CONNECTIONS = 1000;

    /**
     * Default idle connection timeout ({@value #DEFAULT_MAX_IDLE_TIME} milliseconds).
     *
     * @see #getMaxIdleTime
     */
    public static final long DEFAULT_MAX_IDLE_TIME = 30 * 1000L;                // 30 sec

    /**
     * Default maximum allowed size of an incoming message ({@value #DEFAULT_MAX_MESSAGE_SIZE} bytes).
     *
     * @see #getMaxMessageSize
     */
    public static final int DEFAULT_MAX_MESSAGE_SIZE = 32 * 1024 * 1024;        // 32 MB

    /**
     * Default maximum allowed size of a connection's outgoing queue before we start dropping messages
     * ({@value #DEFAULT_MAX_OUTPUT_QUEUE_SIZE} bytes).
     *
     * @see #getMaxOutputQueueSize
     */
    public static final long DEFAULT_MAX_OUTPUT_QUEUE_SIZE = 64 * 1024 * 1024;   // 64 MB

    /**
     * Default maximum allowed size of a connection's incoming queue before we start dropping messages
     * ({@value #DEFAULT_MAX_INPUT_QUEUE_SIZE} bytes).
     *
     * @see #getMaxInputQueueSize
     */
    public static final long DEFAULT_MAX_INPUT_QUEUE_SIZE = 128 * 1024 * 1024;   // 128 MB

    /**
     * Default minimum buffer size to use a direct buffer.
     *
     * @see #getMinDirectBufferSize
     */
    public static final int DEFAULT_MIN_DIRECT_BUFFER_SIZE = 64 * 1024;          // 64 K

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final HashMap<String, ChannelConnection> connectionMap = new HashMap<>();     // keys are NORMALIZED peer names

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private long maxIdleTime = DEFAULT_MAX_IDLE_TIME;
    private int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    private long maxOutputQueueSize = DEFAULT_MAX_OUTPUT_QUEUE_SIZE;
    private long maxInputQueueSize = DEFAULT_MAX_INPUT_QUEUE_SIZE;
    private int minDirectBufferSize = DEFAULT_MIN_DIRECT_BUFFER_SIZE;

    private HandlerThread handlerThread;
    private String serviceThreadName;

// Public API

    /**
     * Get the maximum number of allowed connections. Default is {@value #DEFAULT_MAX_CONNECTIONS}.
     *
     * @return max allowed connections
     */
    public synchronized int getMaxConnections() {
        return this.maxConnections;
    }
    public synchronized void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Get the maximum idle time for connections before automatically closing them down.
     * Default is {@value #DEFAULT_MAX_IDLE_TIME}ms.
     *
     * @return max connection idle time in milliseconds
     */
    public synchronized long getMaxIdleTime() {
        return this.maxIdleTime;
    }
    public synchronized void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    /**
     * Get the maximum allowed length for incoming messages. Default is {@value #DEFAULT_MAX_MESSAGE_SIZE} bytes.
     *
     * @return max allowed incoming message length in bytes
     */
    public synchronized int getMaxMessageSize() {
        return this.maxMessageSize;
    }
    public synchronized void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    /**
     * Get the maximum allowed size of the queue for outgoing messages.
     * Default is {@value #DEFAULT_MAX_OUTPUT_QUEUE_SIZE} bytes.
     *
     * @return max allowed outgoing message queue length in bytes
     */
    public synchronized long getMaxOutputQueueSize() {
        return this.maxOutputQueueSize;
    }
    public synchronized void setMaxOutputQueueSize(long maxOutputQueueSize) {
        this.maxOutputQueueSize = maxOutputQueueSize;
        for (ChannelConnection connection : this.connectionMap.values())        // in case output queue empty status changes
            connection.updateSelection();
    }

    /**
     * Get the maximum allowed size of the queue for incoming messages.
     * Default is {@value #DEFAULT_MAX_INPUT_QUEUE_SIZE} bytes.
     *
     * <p>
     * Messages are considered in the "input queue" if they have been received, but not
     * yet handled by the {@link Handler}, because the {@link Handler} has not finished
     * handling one or more earlier messages.
     *
     * @return max allowed incomign message queue length in bytes
     */
    public synchronized long getMaxInputQueueSize() {
        return this.maxInputQueueSize;
    }
    public synchronized void setMaxInputQueueSize(long maxInputQueueSize) {
        this.maxInputQueueSize = maxInputQueueSize;
        for (ChannelConnection connection : this.connectionMap.values())        // in case input queue full status changes
            connection.updateSelection();
    }

    /**
     * Get the name of the service thread.
     *
     * <p>
     * By default this is null, meaning no special name is used. In that case the name will look something
     * like {@code pool-2-thread-1}.
     *
     * @return custom service thread name, or null for none
     */
    public synchronized String getServiceThreadName() {
        return this.serviceThreadName;
    }
    public synchronized void setServiceThreadName(String serviceThreadName) {
        this.serviceThreadName = serviceThreadName;
    }

    /**
     * Get the minimum incoming message size for which we should allocate a <b>direct</b> {@link ByteBuffer}.
     *
     * @return direct {@link ByteBuffer} minimum size
     * @see ByteBuffer
     */
    public synchronized int getMinDirectBufferSize() {
        return this.minDirectBufferSize;
    }

    /**
     * Set the minimum incoming message size for which we should allocate a <b>direct</b> {@link ByteBuffer}.
     *
     * @param minDirectBufferSize direct buffer size lower limit
     * @see ByteBuffer
     */
    public synchronized void setMinDirectBufferSize(final int minDirectBufferSize) {
        this.minDirectBufferSize = minDirectBufferSize;
    }

// Lifecycle

    @Override
    public synchronized void start(Handler handler) throws IOException {
        super.start();
        boolean successful = false;
        try {
            if (this.handlerThread != null)
                return;
            this.handlerThread = new HandlerThread(handler);
            if (this.log.isDebugEnabled())
                this.log.debug("starting " + this);
            if (this.serviceThreadName != null)
                this.handlerThread.setName(this.serviceThreadName);
            this.handlerThread.start();
            successful = true;
        } finally {
            if (!successful)
                this.stop();
        }
    }

    @Override
    public void stop() {
        super.stop();
        synchronized (this) {
            if (this.handlerThread == null)
                return;
            if (this.log.isDebugEnabled())
                this.log.debug("stopping " + this);
            this.handlerThread = null;
            this.notifyAll();           // wakeup HandlerThread so he can notice that we are stopped
        }
    }

// Network

    @Override
    public synchronized boolean send(String peer, ByteBuffer msg) {

        // Sanity check
        if (peer == null)
            throw new IllegalArgumentException("null peer");
        final String normalizedPeer = this.normalizePeerName(peer);

        // Get/create connection
        ChannelConnection connection = this.connectionMap.get(normalizedPeer);
        if (connection == null) {

            // Create connection
            try {
                connection = this.createConnection(peer);
            } catch (IOException e) {
                this.log.info(this + " unable to send message to `" + peer + "': " + e.getMessage());
                return false;
            }

            // Record connection
            this.connectionMap.put(normalizedPeer, connection);
        }

        // Send message
        return connection.output(msg);
    }

// Connection API

    // Invoked when a connection closes
    void handleConnectionClosed(ChannelConnection connection) {
        assert Thread.holdsLock(this);
        assert this.isServiceThread();
        if (this.log.isDebugEnabled())
            this.log.debug(this + " handling closed connection " + connection);
        final String normalizedPeer = this.normalizePeerName(connection.getPeer());
        this.connectionMap.remove(normalizedPeer);
        this.wakeup();
    }

// HandlerThread

    /**
     * Thread that delivers notifications to the {@link Network.Handler}.
     */
    private class HandlerThread extends Thread {

        private final Logger log = ChannelNetwork.this.log;
        private final Network.Handler handler;

        HandlerThread(Network.Handler handler) {
            Preconditions.checkArgument(handler != null, "null handler");
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                int counter = 0;                            // use this to ensure channels are handled fairly (round-robin)
                while (true) {

                    // Work we will do
                    ByteBuffer buf = null;
                    String peer = null;
                    boolean outputQueueEmpty = false;

                    // Find next input for handler
                    synchronized (ChannelNetwork.this) {
                    workLoop:
                        while (true) {

                            // Check for shutdown
                            if (ChannelNetwork.this.handlerThread != this)
                                return;

                            // Snapshot current channels
                            final ChannelConnection[] connections = ChannelNetwork.this.connectionMap.values()
                              .toArray(new ChannelConnection[0]);

                            // Find the first connection with pending notification(s) of some kind
                            for (int i = 0; i < connections.length; i++) {

                                // Get the next connection (using counter for round-robin)
                                final ChannelConnection connection = connections[counter++ % connections.length];
                                counter &= 0x7fffffff;                              // ensure counter stays positive

                                // Get pending output queue empty notification, if any
                                outputQueueEmpty = connection.pollForOutputQueueEmpty();

                                // Get next input message, if any
                                buf = connection.pollForInputQueueNotEmpty();

                                // Any notification(s) needed?
                                if (outputQueueEmpty || buf != null) {
                                    peer = connection.getPeer();
                                    break workLoop;
                                }
                            }

                            // None of our connections have any pending notifications - sleep until we have more work
                            ChannelNetwork.this.wait();
                        }
                    }

                    // Notify if output queue empty
                    if (outputQueueEmpty) {
                        try {
                            this.handler.outputQueueEmpty(peer);
                        } catch (Throwable t) {
                            this.log.error("exception in callback", t);
                        }
                    }

                    // Notify of new input
                    if (buf != null) {
                        try {
                            this.handler.handle(peer, buf);
                        } catch (Throwable t) {
                            this.log.error("exception in callback", t);
                        }
                    }
                }
            } catch (Error | RuntimeException t) {
                this.log.error("unexpected exception in HandlerThread", t);
                throw t;
            } catch (Throwable t) {
                this.log.error("unexpected exception in HandlerThread", t);
                throw new RuntimeException(t);
            }
        }
    }

// Subclass API

    /**
     * Normalize the given peer.
     *
     * <p>
     * The implementation in {@link ChannelNetwork} just returns {@code peer}.
     * Subclasses should override this method as needed to ensure that two different strings that refer
     * to the same destination have the same normalized form.
     *
     * @param peer remote peer
     * @return normalized form for {@code peer}
     * @throws IllegalArgumentException if {@code peer} is an invalid peer name
     * @throws IllegalArgumentException if {@code peer} is null
     */
    protected String normalizePeerName(String peer) {
        return peer;
    }

    /**
     * Create a new connection to the specified peer.
     *
     * @param peer remote peer
     * @return new connection to {@code peer}
     * @throws IOException if an I/O error occurs
     */
    protected abstract ChannelConnection createConnection(String peer) throws IOException;

// SelectorSupport

    @Override
    protected void serviceHousekeeping() {

        // Perform connection housekeeping
        for (ChannelConnection connection : new ArrayList<>(this.connectionMap.values())) {
            try {
                connection.performHousekeeping();
            } catch (IOException e) {
                if (this.log.isDebugEnabled())
                    this.log.debug("I/O error from " + connection, e);
                connection.close(e);
            } catch (Throwable t) {
                this.log.error("error performing housekeeping for " + connection, t);
                connection.close(t);
            }
        }
    }

    @Override
    protected void serviceCleanup() {
        for (ChannelConnection connection : new ArrayList<>(this.connectionMap.values()))
            connection.close(null);
    }
}
