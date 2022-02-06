
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * A TCP connection to be used with a {@link ChannelNetwork}.
 *
 * <b>Locking</b>
 *
 * <p>
 * All access to this class must be with the associated {@link TCPNetwork} instance locked.
 */
public class TCPConnection extends ChannelConnection {

// Constructors

    protected TCPConnection(TCPNetwork network, String peer, SocketChannel channel) throws IOException {
        super(network, peer, channel);
    }

    /**
     * Get the associated {@link SocketChannel}.
     *
     * @return the socket channel for this connection
     */
    public SocketChannel getSocketChannel() {
        return (SocketChannel)this.getInputChannel();
    }

// ChannelConnection

    @Override
    protected void updateSelection() {
        if (this.getSocketChannel().isConnectionPending()) {
            this.network.selectFor(this.inputSelectionKey, SelectionKey.OP_CONNECT, true);
            this.network.selectFor(this.inputSelectionKey, SelectionKey.OP_READ, true);
            this.network.selectFor(this.outputSelectionKey, SelectionKey.OP_WRITE, false);
            return;
        }
        this.network.selectFor(this.inputSelectionKey, SelectionKey.OP_CONNECT, false);
        super.updateSelection();
    }

// IOHandler

    @Override
    public void serviceIO(SelectionKey key) throws IOException {
        assert this.network.isServiceThread();
        assert Thread.holdsLock(this.network);
        if (key.isConnectable())
            this.handleConnectable();
        super.serviceIO(key);
    }

// I/O Ready Conditions

    // Handle connection succeeded
    private void handleConnectable() throws IOException {

        // Leave connecting state
        this.network.selectFor(this.inputSelectionKey, SelectionKey.OP_CONNECT, false);
        if (!this.getSocketChannel().finishConnect())                    // this should never occur
            throw new IOException("connection failed");
        if (this.log.isDebugEnabled())
            this.log.debug(this + ": connection succeeded");

        // Notify us when readable/writeable
        this.updateSelection();

        // Restart idle timer
        this.restartIdleTimer();

        // Notify client we are open for business
        this.handleOutputQueueEmpty();
    }

// Housekeeping

    @Override
    protected void performHousekeeping() throws IOException {
        assert Thread.holdsLock(this.network);
        assert this.network.isServiceThread();
        if (this.getSocketChannel().isConnectionPending()) {
            if (this.getIdleTime() >= ((TCPNetwork)this.network).getConnectTimeout())
                throw new IOException("connection unsuccessful after " + this.getIdleTime() + "ms");
            return;
        }
        super.performHousekeeping();
    }
}

