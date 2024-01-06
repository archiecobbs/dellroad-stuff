
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * {@link Network} implementation based on TCP connections.
 *
 * <p>
 * Remote peers have the {@link String} form <code>IP-Address[:port]</code>. If the port is omitted,
 * the default port provided to the constructor is assumed.
 *
 * <p>
 * Individual TCP connections are created and maintained automatically as needed.
 *
 * <p>
 * Note: if each peer's IP address is unique, consider overriding {@link #identifyPeer identifyPeer()}
 * to remove the port. This will prevent the creation of duplicate connections between peers.
 */
public class TCPNetwork extends ChannelNetwork {

    /**
     * Default connect timeout for outgoing connections ({@value #DEFAULT_CONNECT_TIMEOUT} milliseconds).
     *
     * @see #getConnectTimeout
     */
    public static final long DEFAULT_CONNECT_TIMEOUT = 20 * 1000L;              // 20 sec

    private final int defaultPort;

    private InetSocketAddress listenAddress;
    private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    private ServerSocketChannel serverSocketChannel;
    private SelectionKey selectionKey;

// Constructors

    /**
     * Constructor.
     *
     * @param defaultPort default TCP port when no port is explicitly proviced
     * @throws IllegalArgumentException if {@code port} is invalid
     */
    public TCPNetwork(int defaultPort) {
        if (defaultPort <= 0 || defaultPort > 65535)
            throw new IllegalArgumentException("invalid default port " + defaultPort);
        this.defaultPort = defaultPort;
        this.listenAddress = new InetSocketAddress(this.defaultPort);
    }

// Public API

    /**
     * Get the {@link InetSocketAddress} to which this instance is bound or will bind.
     *
     * @return listen address, possibly null for default behavior
     */
    public synchronized InetSocketAddress getListenAddress() {
        return this.listenAddress;
    }

    /**
     * Configure the {@link InetSocketAddress} to which this instance should bind.
     *
     * <p>
     * If this instance is already started, invoking this method will have no effect until it is
     * {@linkplain #stop stopped} and restarted.
     *
     * <p>
     * By default, instances listen on all interfaces on the defaul port configured in the constructor.
     *
     * @param address listen address, or null to listen on all interfaces on the default port provided to the constructor
     * @throws IllegalArgumentException if {@code address} is null
     */
    public synchronized void setListenAddress(InetSocketAddress address) {
        if (address == null)
            throw new IllegalArgumentException("null address");
        this.listenAddress = address;
    }

    /**
     * Get the outgoing connection timeout. Default is {@value #DEFAULT_CONNECT_TIMEOUT}ms.
     *
     * @return outgoing connection timeout in milliseconds
     */
    public synchronized long getConnectTimeout() {
        return this.connectTimeout;
    }
    public synchronized void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

// Lifecycle

    @Override
    public synchronized void start(Handler handler) throws IOException {
        super.start(handler);
        boolean successful = false;
        try {
            if (this.serverSocketChannel != null)
                return;
            this.serverSocketChannel = ServerSocketChannel.open();
            this.configureServerSocketChannel(this.serverSocketChannel);
            this.serverSocketChannel.bind(this.listenAddress);
            this.selectionKey = this.createSelectionKey(this.serverSocketChannel, new IOHandler() {
                @Override
                public void serviceIO(SelectionKey key) throws IOException {
                    if (key.isAcceptable())
                        TCPNetwork.this.handleAccept();
                }
                @Override
                public void close(Throwable cause) {
                    TCPNetwork.this.log.error("stopping " + this + " due to exception", cause);
                    TCPNetwork.this.stop();
                }
            });
            this.selectForAccept(true);
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
            if (this.serverSocketChannel != null) {
                try {
                    this.serverSocketChannel.close();
                } catch (Exception e) {
                    // ignore
                }
                this.serverSocketChannel = null;
            }
            this.selectionKey = null;
        }
    }

// Utility methods

    /**
     * Parse out the address part of an address that has an optional colon plus TCP port number suffix.
     *
     * @param address address of the form {@code ipaddr} or {@code ipaddr:port}
     * @return the IP address part
     */
    public static String parseAddressPart(String address) {
        return (String)TCPNetwork.parseAddress(address, 0)[0];
    }

    /**
     * Parse out the port part of an address that has an optional colon plus TCP port number suffix.
     *
     * @param address address of the form {@code ipaddr} or {@code ipaddr:port}
     * @param defaultPort default port if none specified in {@code address}
     * @return the port part, or {@code defaultPort} if there is no explicit port
     */
    public static int parsePortPart(String address, int defaultPort) {
        return (Integer)TCPNetwork.parseAddress(address, defaultPort)[1];
    }

    private static Object[] parseAddress(String string, int defaultPort) {
        final int colon = string.lastIndexOf(':');
        if (colon == -1)
            return new Object[] { string, defaultPort };
        try {
            final int port = Integer.parseInt(string.substring(colon + 1), 10);
            if (port < 1 || port > 65535)
                return new Object[] { string, defaultPort };
            return new Object[] { string.substring(0, colon), port };
        } catch (Exception e) {
            return new Object[] { string, defaultPort };
        }
    }

// Subclass methods

    /**
     * Configure the {@link ServerSocketChannel} to be used by this instance. This method is invoked by {@link #start}.
     *
     * <p>
     * The implementation in {@link TCPNetwork} does nothing. Subclasses may override to configure socket options, etc.
     *
     * @param serverSocketChannel channel to configure
     */
    protected void configureServerSocketChannel(ServerSocketChannel serverSocketChannel) {
    }

    /**
     * Configure a new {@link SocketChannel} to be used by this instance. This method is invoked when new connections
     * are created.
     *
     * <p>
     * The implementation in {@link TCPNetwork} does nothing. Subclasses may override to configure socket options, etc.
     *
     * @param socketChannel channel to configure
     */
    protected void configureSocketChannel(SocketChannel socketChannel) {
    }

    /**
     * Identify the peer behind an incoming connection.
     *
     * <p>
     * The implementation in {@link TCPNetwork} returns the {@linkplain InetSocketAddress#getHostString the remote IP address},
     * followed by a colon and {@linkplain InetSocketAddress#getPort the remote port} if that port differs from the default.
     *
     * <p>
     * Note that the default behavior does not identify a peer simply by its IP address. Since TCP connections typically
     * originate from randomly chosen ports, every connection from the same host will appear as a new peer. As a result,
     * two hosts configured to talk to each other will end up creating two TCP connections, one in each direction.
     *
     * <p>
     * To resolve that problem, and identify a peer by its IP address alone, override this method and just return
     * {@code remote.getHostString()}.
     *
     * @param remote remote address of incoming connection
     * @return associated peer name, or null if peer is not recognized (socket will be closed)
     */
    protected String identifyPeer(InetSocketAddress remote) {
        return remote.getHostString() + (remote.getPort() != this.defaultPort ? ":" + remote.getPort() : "");
    }

// Object

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[port=" + this.listenAddress.getPort() + "]";
    }

// I/O Ready Conditions

    // Invoked when we have a new incoming connection
    private void handleAccept() throws IOException {

        // Sanity check
        assert this.isServiceThread();

        // Check connection size limit
        if (this.connectionMap.size() >= this.getMaxConnections()) {
            this.log.warn("too many network connections (" + this.connectionMap.size() + " >= "
              + this.getMaxConnections() + "), not accepting any more (for now)");
            this.selectForAccept(false);
            return;
        }

        // Accept connection
        SocketChannel socketChannel;
        synchronized (this) {
            if (this.serverSocketChannel == null || (socketChannel = this.serverSocketChannel.accept()) == null)
                return;
        }

        // Identify peer
        final InetSocketAddress remote = (InetSocketAddress)socketChannel.socket().getRemoteSocketAddress();
        final String peer = this.identifyPeer(remote);
        if (peer == null) {
            this.log.info("rejecting unrecognized peer connection from {}", socketChannel.getRemoteAddress());
            socketChannel.close();
            return;
        }

        // Configure socket
        this.log.info("accepted incoming connection from " + socketChannel.getRemoteAddress());
        socketChannel
          .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
          .setOption(StandardSocketOptions.TCP_NODELAY, true);
        this.configureSocketChannel(socketChannel);

        // Are we already connected to this peer? If so (deterministically) choose which connection wins
        final TCPConnection oldConnection = (TCPConnection)this.connectionMap.get(peer);
        if (oldConnection != null) {

            // Compare the socket addresses of the initiator side of each connection
            final SocketAddress oldAddr = oldConnection.getSocketChannel().socket().getLocalSocketAddress();
            final SocketAddress newAddr = socketChannel.getRemoteAddress();
            final String oldDesc = oldAddr.toString().replaceAll("^[^/]*/", "");            // strip off hostname part, if any
            final String newDesc = newAddr.toString().replaceAll("^[^/]*/", "");            // strip off hostname part, if any
            final int diff = newDesc.compareTo(oldDesc);
            this.log.info("connection mid-air collision: old: {} new: {} winner: {}",
              oldDesc, newDesc, diff < 0 ? "new" : diff > 0 ? "old" : "neither (???)");

            // If new incoming connection loses, close it and bail out
            if (diff >= 0) {
                this.log.info("rejecting duplicate incoming connection from {} (peer \"{}\")", newAddr, peer);
                socketChannel.close();
                return;
            }

            // New incoming connection wins, so close the old connection
            this.log.info("closing existing duplicate connection to {} (peer \"{}\")", oldAddr, peer);
            this.connectionMap.remove(peer);
            oldConnection.close(new IOException("duplicate connection"));
        }

        // Create a new connection from the new incoming connection socket
        final TCPConnection newConnection = new TCPConnection(this, peer, socketChannel);
        this.connectionMap.put(peer, newConnection);
        newConnection.handleOutputQueueEmpty();
    }

    // Enable/disable incoming connections
    private void selectForAccept(boolean enabled) throws IOException {
        if (this.selectionKey == null)
            return;
        if (enabled && (this.selectionKey.interestOps() & SelectionKey.OP_ACCEPT) == 0) {
            this.selectFor(this.selectionKey, SelectionKey.OP_ACCEPT, true);
            if (this.log.isDebugEnabled())
                this.log.debug(this + " started listening for incoming connections");
        } else if (!enabled && (this.selectionKey.interestOps() & SelectionKey.OP_ACCEPT) != 0) {
            this.selectFor(this.selectionKey, SelectionKey.OP_ACCEPT, false);
            if (this.log.isDebugEnabled())
                this.log.debug(this + " stopped listening for incoming connections");
        }
    }

// ChannelNetwork

    /**
     * Normalize the given peer.
     *
     * <p>
     * The implementation in {@link TCPNetwork} converts {@code peer} to lower case and appends the
     * (default) port if not specified in the string.
     *
     * @throws IllegalArgumentException {@code inheritDoc}
     */
    @Override
    protected String normalizePeerName(String peer) {
        if (peer == null)
            throw new IllegalArgumentException("null peer");
        final int colon = peer.lastIndexOf(':');
        if (colon == -1)
            peer += ":" + this.defaultPort;
        return peer;
    }

    @Override
    protected synchronized TCPConnection createConnection(String peer) throws IOException {

        // Create new socket
        final SocketChannel socketChannel = SocketChannel.open()
          .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
          .setOption(StandardSocketOptions.TCP_NODELAY, true);
        this.configureSocketChannel(socketChannel);
        socketChannel.configureBlocking(false);

        // Resolve peer name into a socket address
        if (this.log.isDebugEnabled())
            this.log.debug(this + " looking up peer address `" + peer + "'");
        InetSocketAddress socketAddress = null;
        try {
            socketAddress = new InetSocketAddress(
              TCPNetwork.parseAddressPart(peer), TCPNetwork.parsePortPart(peer, this.defaultPort));
        } catch (IllegalArgumentException e) {
            if (this.log.isTraceEnabled())
                this.log.trace(this + " peer address `" + peer + "' is invalid", e);
        }
        if (socketAddress == null || socketAddress.isUnresolved())
            throw new IOException("invalid or unresolvable peer address `" + peer + "'");

        // Initiate connection to peer
        if (this.log.isDebugEnabled()) {
            this.log.debug(this + ": resolved peer address `" + peer + "' to " + socketAddress.getAddress()
              + "; now initiating connection");
        }
        socketChannel.connect(socketAddress);

        // Create new connection
        return new TCPConnection(this, peer, socketChannel);
    }

// SelectorSupport

    @Override
    protected void serviceHousekeeping() {
        super.serviceHousekeeping();
        try {
            this.selectForAccept(this.connectionMap.size() < this.getMaxConnections());
        } catch (IOException e) {
            throw new RuntimeException("unexpected exception", e);
        }
    }
}
