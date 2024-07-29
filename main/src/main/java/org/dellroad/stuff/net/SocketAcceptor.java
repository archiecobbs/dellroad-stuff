
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring bean that listens for connections on a TCP socket and spawns a child thread to handle each new connection.
 * Subclasses must implement {@link #getSocketHandler}. Only the {@link #getPort port} property is required to be set.
 */
public abstract class SocketAcceptor {

    /**
     * Default maximum incoming connection queue length ({@value}).
     *
     * @see #setBacklog
     */
    public static final int DEFAULT_BACKLOG = 50;

    private static final long NOTIFICATION_INTERVAL = 5 * 1000 * 1000 * 1000L;      // 5 seconds

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final HashSet<SocketInfo> connections = new HashSet<>();
    private InetAddress address;
    private int backlog = DEFAULT_BACKLOG;
    private int port;
    private int maxConnections;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean started;

    /**
     * Get address to listen on.
     *
     * @return address to listen on, or null for any
     */
    public synchronized InetAddress getInetAddress() {
        return this.address;
    }
    public synchronized void setInetAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * Get maximum connect backlog.
     *
     * @return maximum backlogged connections
     */
    public synchronized int getBacklog() {
        return this.backlog;
    }
    public synchronized void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    /**
     * Get maximum number of concurrent connections.
     *
     * @return max conncurrent connections, or zero for unlimited
     */
    public synchronized int getMaxConnections() {
        return this.maxConnections;
    }
    public synchronized void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Get TCP port to listen on.
     *
     * @return TCP listening port
     */
    public synchronized int getPort() {
        return this.port;
    }
    public synchronized void setPort(int port) {
        this.port = port;
    }

    /**
     * Verifies configuration and starts accepting incoming connections. Does nothing if already started.
     *
     * @throws RuntimeException if an I/O error occurs setting up the server socket
     * @throws IllegalArgumentException if configuration is invalid
     */
    @PostConstruct
    public synchronized void start() {

        // Sanity check
        if (this.port == 0)
            throw new IllegalArgumentException("TCP port not set");
        if (this.port < 1 || this.port > 65535)
            throw new IllegalArgumentException("invalid TCP port " + this.port);
        if (this.maxConnections < 0)
            throw new IllegalArgumentException("invalid maxConnections " + this.maxConnections);

        // Already started?
        if (this.started)
            return;

        // Create server thread
        String addr = this.address != null ? "" + this.address : "*";
        String threadName = this.getClass().getSimpleName() + "[" + addr + ":" + this.port + "]";
        this.serverThread = new Thread(threadName) {
            @Override
            public void run() {
                SocketAcceptor.this.run();
            }
        };

        // Create socket
        try {
            this.serverSocket = this.createServerSocket();
        } catch (IOException e) {
            throw new RuntimeException("error creating server socket", e);
        }
        if (this.serverSocket == null)
            throw new RuntimeException("createServerSocket() returned a null socket");

        // Start server thread
        this.serverThread.start();
        this.started = true;
    }

    private void run() {
        try {
            while (true) {

                // Block while we've reached our connection limit
                synchronized (this) {
                    boolean logged = false;
                    boolean interrupted = false;
                    while (this.serverSocket != null && this.maxConnections > 0 && this.connections.size() >= this.maxConnections) {
                        if (!logged) {
                            this.log.warn(Thread.currentThread().getName() + " has reached connection limit of "
                              + this.maxConnections + ", temporarily refusing new connections");
                            logged = true;
                        }
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    if (logged)
                        this.log.info(Thread.currentThread().getName() + " is accepting new connections again");
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }

                // Have we been stopped?
                ServerSocket serverSocketCopy;
                synchronized (this) {
                    serverSocketCopy = this.serverSocket;
                }
                if (serverSocketCopy == null)
                    break;

                // Accept a new connection
                final Socket socket = serverSocketCopy.accept();
                final String socketDesc = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                this.log.info("accepted new TCP connection from " + socketDesc);

                // Get a handler for it
                final SocketHandler handler = this.getSocketHandler(socket);
                if (handler == null) {
                    this.log.info("null handler returned by getSocketHandler, closing connection from " + socketDesc);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                    continue;
                }
                final SocketInfo socketInfo = new SocketInfo(socket, handler);

                // Create a thread that will handle the connection.
                Thread handlerThread = new Thread() {
                    public void run() {
                        try {
                            handler.handleConnection(socket);
                        } catch (Throwable t) {
                            SocketAcceptor.this.log.error("error handling connection in " + Thread.currentThread().getName(), t);
                        } finally {
                            synchronized (SocketAcceptor.this) {
                                SocketAcceptor.this.connections.remove(socketInfo);
                                SocketAcceptor.this.notifyAll();
                            }
                            try {
                                socket.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                };
                handlerThread.setName(handler.getClass().getSimpleName() + "[" + socketDesc + "]");
                socketInfo.setThread(handlerThread);

                // Start handler thread and update active connections
                synchronized (SocketAcceptor.this) {

                    // Need to check again for stopped-ness because we released the lock
                    if (this.serverSocket == null) {
                        this.log.info("discarding connection from " + socketDesc + " due to shutdown");
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // ignore
                        }
                        break;
                    }

                    // Start handler
                    handlerThread.start();
                    this.connections.add(socketInfo);
                }
            }
        } catch (IOException e) {
            boolean expected;
            synchronized (this) {
                expected = this.serverSocket == null;
            }
            if (!expected)
                this.log.error("exception in acceptor thread " + Thread.currentThread().getName(), e);
        } catch (Throwable t) {
            this.log.error("exception in acceptor thread " + Thread.currentThread().getName(), t);
        } finally {
            this.log.info("acceptor thread " + Thread.currentThread().getName() + " exiting");
            this.closeServerSocket();
            synchronized (this) {
                this.serverThread = null;
                this.notifyAll();
            }
        }
    }

    /**
     * Stop accepting connections. Does nothing if already stopped.
     *
     * <p>
     * Any currently active connections are stopped via {@link SocketHandler#stop SocketHandler.stop()},
     * and this method waits until all such connections have returned from {@link SocketHandler#handleConnection
     * SocketHandler.handleConnection()} before returning.
     */
    @PreDestroy
    public synchronized void stop() {

        // Already shut down?
        if (!this.started)
            return;

        // Stop acceptor thread
        if (this.serverThread != null)
            this.log.info("stopping acceptor thread");
        this.closeServerSocket();
        boolean interrupted = false;
        while (this.serverThread != null) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();

        // Notify all active connections
        for (SocketInfo socketInfo : this.connections) {

            // Notify handler
            try {
                socketInfo.getHandler().stop(socketInfo.getThread(), socketInfo.getSocket());
            } catch (Throwable t) {
                this.log.error("error stopping " + socketInfo.getHandler(), t);
            }

            // Close socket
            try {
                socketInfo.getSocket().close();
            } catch (IOException e) {
                // ignore
            }
        }

        // Wait for all active connections to complete
        long lastLogTime = 0;
        interrupted = false;
        while (!this.connections.isEmpty()) {
            final long currentTime = System.nanoTime();
            if (currentTime - lastLogTime > NOTIFICATION_INTERVAL) {
                this.log.info("waiting for " + this.connections.size() + " active connection(s) to stop...");
                lastLogTime = currentTime;
            }
            try {
                this.wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (lastLogTime != 0)
            this.log.info("all active connection(s) have stopped");
        if (interrupted)
            Thread.currentThread().interrupt();

        // Done
        this.started = false;
    }

    /**
     * Shut down socket.
     *
     * @return true if socket was shut down by this invocation, false if it was already shut down
     */
    private synchronized void closeServerSocket() {
        if (this.serverSocket == null)
            return;
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            // ignore
        } finally {
            this.serverSocket = null;
        }
    }

    /**
     * Create the server's socket via which connections will be accepted.
     *
     * <p>
     * The implementation in {@link SocketAcceptor} creates the socket and sets the "reuse address" flag.
     * Subclasses may override.
     *
     * @return server socket
     * @throws IOException if an I/O error occurs
     */
    protected synchronized ServerSocket createServerSocket() throws IOException {
        ServerSocket socket = new ServerSocket(this.port, this.backlog, this.address);
        socket.setReuseAddress(true);
        return socket;
    }

    /**
     * Get the {@link SocketHandler} that will handle a new connection using the given socket.
     *
     * @param socket connection socket
     * @return new handler, or <code>null</code> to disconnect the socket immediately
     * @throws IOException if an I/O error occurs
     */
    protected abstract SocketHandler getSocketHandler(Socket socket) throws IOException;

    // Information about one active connection
    private static class SocketInfo {

        private final Socket socket;
        private final SocketHandler handler;
        private Thread thread;

        SocketInfo(Socket socket, SocketHandler handler) {
            this.socket = socket;
            this.handler = handler;
        }

        public Socket getSocket() {
            return this.socket;
        }

        public SocketHandler getHandler() {
            return this.handler;
        }

        public Thread getThread() {
            return this.thread;
        }
        public void setThread(Thread thread) {
            this.thread = thread;
        }
    }
}

