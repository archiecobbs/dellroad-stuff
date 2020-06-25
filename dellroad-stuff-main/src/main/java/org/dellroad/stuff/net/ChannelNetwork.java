
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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
     * Default minimum buffer size to use a direct buffer.
     *
     * @see #getMinDirectBufferSize
     */
    public static final int DEFAULT_MIN_DIRECT_BUFFER_SIZE = 64 * 1024;          // 64 K

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final HashMap<String, ChannelConnection> connectionMap = new HashMap<>();
    protected Network.Handler handler;
    protected ExecutorService executor;

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private long maxIdleTime = DEFAULT_MAX_IDLE_TIME;
    private int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    private long maxOutputQueueSize = DEFAULT_MAX_OUTPUT_QUEUE_SIZE;
    private int minDirectBufferSize = DEFAULT_MIN_DIRECT_BUFFER_SIZE;
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
            if (this.handler != null)
                return;
            if (this.log.isDebugEnabled())
                this.log.debug("starting " + this);
            this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final Thread thread = new Thread(r);
                    synchronized (ChannelNetwork.this) {
                        if (ChannelNetwork.this.serviceThreadName != null)
                            thread.setName(ChannelNetwork.this.serviceThreadName);
                    }
                    return thread;
                }
            });
            this.handler = handler;
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
            if (this.handler == null)
                return;
            if (this.log.isDebugEnabled())
                this.log.debug("stopping " + this);
            if (this.executor != null) {
                this.executor.shutdownNow();
                try {
                    this.executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                this.executor = null;
            }
            this.handler = null;
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

    // Invoked when a message arrives on a connection
    void handleMessage(final ChannelConnection connection, final ByteBuffer msg) {
        assert Thread.holdsLock(this);
        assert this.isServiceThread();
        this.executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelNetwork.this.handler.handle(connection.getPeer(), msg);
                } catch (Throwable t) {
                    ChannelNetwork.this.log.error("exception in callback", t);
                }
            }
        });
    }

    // Invoked a connection's output queue goes empty
    void handleOutputQueueEmpty(final ChannelConnection connection) {
        assert Thread.holdsLock(this);
        assert this.isServiceThread();
        this.executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelNetwork.this.handler.outputQueueEmpty(connection.getPeer());
                } catch (Throwable t) {
                    ChannelNetwork.this.log.error("exception in callback", t);
                }
            }
        });
    }

    // Invoked when a connection closes
    void handleConnectionClosed(ChannelConnection connection) {
        assert Thread.holdsLock(this);
        assert this.isServiceThread();
        if (this.log.isDebugEnabled())
            this.log.debug(this + " handling closed connection " + connection);
        final String normalizedPeer = this.normalizePeerName(connection.getPeer());
        this.connectionMap.remove(normalizedPeer);
        this.handleOutputQueueEmpty(connection);
        this.wakeup();
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
        for (ChannelConnection connection : new ArrayList<ChannelConnection>(this.connectionMap.values())) {
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
        for (ChannelConnection connection : new ArrayList<ChannelConnection>(this.connectionMap.values()))
            connection.close(null);
    }
}

