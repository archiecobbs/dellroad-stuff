
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Abstraction layer representing a "network" over which a local "node" can communicate with remote nodes.
 *
 * <p>
 * Communication takes the form of sending and receiving arbitrary packets of data known as "messages".
 * Messages are simply sequences of zero or more bytes and are represented by {@link ByteBuffer}s.
 *
 * <p>
 * In general, messages may be delayed, dropped or delivered out of order; however, some implementations
 * may provide better guarantees.
 *
 * <p>
 * Remote nodes are identified by {@link String}s; the interpretation of these {@link String}s is up to the implementation,
 * but typically it is some form of network address.
 *
 * <p>
 * Incoming messages, along with notifications of output queue status, are delivered to a {@link Handler} specified
 * by the application.
 */
public interface Network {

    /**
     * Start this instance.
     *
     * @param handler handler for notifications
     * @throws IllegalStateException if already started
     * @throws IllegalArgumentException if {@code handler} is null
     * @throws IOException if an error occurs
     */
    void start(Handler handler) throws IOException;

    /**
     * Stop this instance.
     *
     * <p>
     * Does nothing if already stopped.
     */
    void stop();

    /**
     * Send (or enqueue for sending) a message to a remote peer.
     *
     * <p>
     * If this method returns true, then {@link Handler#outputQueueEmpty Handler.outputQueueEmpty()}
     * is guaranteed to be invoked with parameter {@code peer} at some later point.
     *
     * @param peer message destination
     * @param msg message to send
     * @return true if message was succesfully enqueued for output; false if message failed to be delivered due to local reasons,
     *  such as failure to initiate a new connection or output queue overflow
     * @throws IllegalArgumentException if {@code peer} cannot be interpreted
     * @throws IllegalArgumentException if {@code peer} or {@code msg} is null
     */
    boolean send(String peer, ByteBuffer msg);

// Handler

    /**
     * Callback interface used by {@link Network} implementations.
     */
    public interface Handler {

        /**
         * Handle an incoming message from a remote peer.
         *
         * <p>
         * The {@code msg} buffer is read-only; its contents are not guaranteed to be valid after this method returns.
         *
         * <p>
         * Note that due to inherent race conditions with multiple threads, it is possible for this method to be invoked
         * (at most once) after {@link Network#stop} has returned.
         *
         * @param peer message source
         * @param msg message received
         */
        void handle(String peer, ByteBuffer msg);

        /**
         * Handle notification that a remote peer's output queue has transitioned from a non empty state to an empty state.
         *
         * <p>
         * This notification can be used for flow-control, i.e., to prevent the local output queue for {@code peer}
         * from growing without bound if there is network congestion, or the peer is slow to read messages, etc.
         *
         * <p>
         * Note that due to inherent race conditions with multiple threads, it is possible for this method to be invoked
         * (at most once) after {@link Network#stop} has returned.
         *
         * @param peer remote peer
         */
        void outputQueueEmpty(String peer);
    }
}
