
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.dellroad.stuff.java.TimedWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support for managing activity based on {@link SelectableChannel} asynchronous I/O notifications.
 *
 * <p>
 * This class helps simplify the management of asynchronous I/O: all operations involving configuring and notifying
 * I/O listeners are performed while this instance is locked, and all callbacks are performed in a separate service thread
 * with this instance locked. As a result, all I/O operations are effectively atomic.
 *
 * <p>
 * Instances must be {@link #start}ed before use. While running, an internal service thread continuously monitors for
 * ready I/O operations, notifying the corresponding {@link IOHandler} when the I/O is ready (or channel closed).
 * The service thread also performs {@linkplain #serviceHousekeeping periodic housekeeping}.
 *
 * <p>
 * Subclasses invoke {@link #createSelectionKey createSelectionKey()} to setup monitoring for a {@link SelectableChannel},
 * and then {@link #selectFor selectFor()} as needed to configure the I/O conditions being monitored.
 * All channels are automatically {@linkplain SelectableChannel#configureBlocking configured} for non-blocking mode.
 *
 * <p><b>Housekeeping</b>
 *
 * <p>
 * Whether or not there is I/O activity, instances perform regular periodic housekeeping via
 * {@link #serviceHousekeeping serviceHousekeeping()}. The maximum time between housekeeping checks is configurable
 * via {@link #setHousekeepingInterval setHousekeepingInterval()}; periodic checks can also be disabled by setting
 * an interval of zero.
 *
 * <p>
 * Whether or not periodic housekeeping checks are enabled, an immediate housekeeping check can be forced at any
 * time by invoking {@link #wakeup}.
 *
 * <p><b>Shutdown Cleanup</b>
 *
 * <p>
 * On shutdown, subclasses are given an opportunity to perform final cleanup via {@link #serviceCleanup serviceCleanup()}.
 *
 * <p><b>Concurrency</b>
 *
 * <p>
 * This class guarantees that the current instance will be locked and the current thread will be the service thread when:
 * <ul>
 *  <li>{@link IOHandler} notifications are delivered</li>
 *  <li>{@link #serviceHousekeeping serviceHousekeeping()} is invoked</li>
 *  <li>{@link #serviceCleanup serviceCleanup()} is invoked</li>
 * </ul>
 *
 * <p>
 * Because these callbacks are only ever invoked from the service thread, subclasses can be written without concern for
 * re-entrancy issues, and subclass methods can use instance synchronization on other methods to avoid any race conditions
 * from asynchronous I/O events.
 */
public class SelectorSupport {

    /**
     * Default housekeeping interval ({@value #DEFAULT_HOUSEKEEPING_INTERVAL}ms).
     *
     * @see #setHousekeepingInterval setHousekeepingInterval()
     */
    public static final int DEFAULT_HOUSEKEEPING_INTERVAL = 1000;

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final SelectorProvider provider;
    protected final HashSet<SelectionKey> closureTrackables = new HashSet<>();

    private volatile Selector selector;
    private ServiceThread serviceThread;
    private volatile int housekeepingInterval = DEFAULT_HOUSEKEEPING_INTERVAL;

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * This instance will use the default {@link SelectorProvider}.
     */
    public SelectorSupport() {
        this(SelectorProvider.provider());
    }

    /**
     * Primary constructor.
     *
     * @param provider the {@link SelectorProvider} that this instance will use
     * @throws IllegalArgumentException if {@code provider} is null
     */
    public SelectorSupport(SelectorProvider provider) {
        Preconditions.checkArgument(provider != null, "null provider");
        this.provider = provider;
    }

// Configuration

    /**
     * Set the housekeeping interval.
     *
     * @param housekeepingInterval housekeeping interval in milliseconds, or zero for none
     * @throws IllegalArgumentException if {@code housekeepingInterval} is negative
     */
    public void setHousekeepingInterval(int housekeepingInterval) {
        Preconditions.checkArgument(housekeepingInterval >= 0, "housekeepingInterval < 0");
        this.housekeepingInterval = housekeepingInterval;
    }

// Lifecycle

    /**
     * Start this instance.
     *
     * <p>
     * Does nothing if already started.
     *
     * @throws IOException if a {@link Selector} cannot be created
     */
    public synchronized void start() throws IOException {
        if (this.selector != null)
            return;
        boolean successful = false;
        try {
            this.selector = this.provider.openSelector();
            this.serviceThread = new ServiceThread();
            this.serviceThread.start();
            successful = true;
        } finally {
            if (!successful)
                this.stop();
        }
    }

    /**
     * Stop this instance.
     *
     * <p>
     * Does nothing if already stopped.
     */
    public synchronized void stop() {

        // Already stopped?
        if (this.selector == null)
            return;
        assert this.serviceThread != null;

        // Stop service thread
        if (this.log.isDebugEnabled())
            this.log.debug("stopping {}", this);
        try {
            this.selector.close();
        } catch (Exception e) {
            // ignore
        }
        this.selector = null;               // this signals the service thread to shut down
        this.serviceThread.interrupt();

        // Wait for service thread to exit
        if (this.isServiceThread())
            return;
        final Thread currentServiceThread = this.serviceThread;
        String failure = null;
        try {
            if (!TimedWait.wait(this, 1000L, () -> this.serviceThread != currentServiceThread))
                failure = "timed out";
        } catch (InterruptedException e) {
            failure = "interrupted";
            Thread.currentThread().interrupt();
        }
        if (failure != null) {
            this.log.warn(String.format(
              "%s waiting for service thread %s while stopping %s, giving up", failure, currentServiceThread, this));
        }
    }

    /**
     * Determine whether this instance has been {@link #start start()}'ed (and not yet {@link #stop stop()}'d).
     */
    public synchronized boolean isRunning() {
        return this.selector != null;
    }

// Subclass methods

    /**
     * Create a new {@link SelectionKey} by registering the given channel on this instance's {@link Selector}
     * and associating the specified {@link IOHandler} to handle I/O ready conditions.
     *
     * <p>
     * This method is equivalent to:
     *  {@link #createSelectionKey(SelectableChannel, IOHandler, boolean) createSelectionKey}{@code (channel, handler, false)}.
     *
     * @param channel I/O channel
     * @param handler I/O handler
     * @return new selection key
     * @throws IllegalArgumentException if either parameter is null
     * @throws java.nio.channels.ClosedChannelException if {@code channel} is closed
     * @throws IOException if {@code channel} cannot be configured for non-blocking  mode
     * @throws IllegalStateException if this instance is not {@link #start}ed or is shutting down
     */
    public SelectionKey createSelectionKey(SelectableChannel channel, IOHandler handler) throws IOException {
        return this.createSelectionKey(channel, handler, false);
    }

    /**
     * Create a new {@link SelectionKey} by registering the given channel on this instance's {@link Selector}
     * and associating the specified {@link IOHandler} to handle I/O ready conditions and/or channel closure.
     *
     * <p>
     * <b>Note:</b> the {@code channel} will be {@linkplain SelectableChannel#configureBlocking configured} for non-blocking mode.
     *
     * <p>
     * Initially, no I/O operations will be selected. Use {@link #selectFor selectFor()} to add/remove them.
     *
     * <p>
     * If {@code notifyOnClose} is true, the {@code handler} is also automatically invoked one last time after
     * {@code channel} is closed (or the returned {@link SelectionKey} is {@link SelectionKey#cancel cancel()}'ed).
     * However, this notification doesn't occur immediately; instead, it is only guaranteed to occur no later than
     * the next housekeeping operation.
     *
     * <p>
     * There is no way to explicitly unregister {@code handler} from {@code channel}, although it can be
     * {@linkplain #selectFor selected} for zero I/O operations. Handlers are implicitly unregistered either when
     * {@code channel} is closed, the returned {@link SelectionKey} is {@link SelectionKey#cancel cancel()}'ed,
     * or this instance is {@link #stop}'ed.
     *
     * @param channel I/O channel
     * @param handler I/O handler
     * @param notifyOnClose whether to also detect closure of {@code channel} and invoke {@code handler} one last time
     * @return new selection key
     * @throws IllegalArgumentException if either parameter is null
     * @throws java.nio.channels.ClosedChannelException if {@code channel} is closed
     * @throws IOException if {@code channel} cannot be configured for non-blocking  mode
     * @throws IllegalStateException if this instance is not {@link #start}ed or is shutting down
     */
    public synchronized SelectionKey createSelectionKey(SelectableChannel channel, IOHandler handler, boolean notifyOnClose)
      throws IOException {

        // Sanity check
        Preconditions.checkArgument(channel != null, "null channel");
        Preconditions.checkArgument(handler != null, "null handler");
        Preconditions.checkState(this.selector != null, "not started");

        // Wakeup service thread: we need it to release the selector's lock (held by select()) so we don't block in register()
        this.wakeup();

        // Configure channel for non-blocking mode
        channel.configureBlocking(false);

        // Register channel with our selector
        final SelectionKey selectionKey = channel.register(this.selector, 0, handler);

        // Remember if we are also tracking this channel's closure
        if (notifyOnClose)
            this.closureTrackables.add(selectionKey);

        // Done
        return selectionKey;
    }

    /**
     * Enable or disable listening for the specified I/O operation(s).
     *
     * <p>
     * The given {@code selectionKey} must have been acquired from {@link #createSelectionKey createSelectionKey()}.
     *
     * <p>
     * The change will take effect immediately.
     *
     * @param selectionKey selection key
     * @param ops I/O operations to enable or disable
     * @param enabled true to enable, false to disable
     * @throws IllegalArgumentException if {@code selectionKey} was not created by {@link #createSelectionKey createSelectionKey()}
     * @throws IllegalArgumentException if {@code selectionKey} is null
     * @throws IllegalArgumentException if {@code ops} contains an invalid operation
     */
    public synchronized void selectFor(SelectionKey selectionKey, int ops, boolean enabled) {

        // Sanity check
        Preconditions.checkArgument(selectionKey != null, "null selectionKey");
        Preconditions.checkArgument(selectionKey.attachment() instanceof IOHandler, "unrecognized selectionKey");

        // Wakeup service thread: we will need it to restart its select() operation with updated slection keys
        this.wakeup();

        // Add/remove ops
        final int currentOps = selectionKey.interestOps();
        selectionKey.interestOps(enabled ? currentOps | ops : currentOps & ~ops);
    }

    /**
     * Wakeup the service thread.
     *
     * <p>
     * This results in an immediate invocation of {@link #serviceHousekeeping serviceHousekeeping()} (from the service thread).
     *
     * <p>
     * Does nothing if this instance is not {@link #start}ed.
     *
     * <p>
     * This method does not acquire the lock on this instance, so it can be invoked at any time from any context.
     *
     * @return true if service thread woken up, false if this instance is not started
     */
    public boolean wakeup() {
        final Selector currentSelector = this.selector;     // unsynchronized, volatile read
        if (currentSelector == null)
            return false;
        if (this.log.isTraceEnabled())
            this.log.trace("wakeup service thread");
        currentSelector.wakeup();
        return true;
    }

    /**
     * Perform housekeeping.
     *
     * <p>
     * This method is invoked from the internal service thread; this instance will be locked at that time.
     *
     * <p>
     * This method is invoked after every I/O service (while still holding this instance's lock), and periodically
     * at least every {@linkplain #setHousekeepingInterval housekeeping interval}, if enabled.
     * If this method is invoked, it is guaranteed that this instance is not being {@link #stop}'ed.
     *
     * <p>
     * Any unchecked exceptions thrown by this method are logged but otherwise ignored. If a fatal error occurs,
     * {@link #stop} may be invoked to initiate a graceful shutdown.
     *
     * <p>
     * Use {@link #wakeup} to trigger an immediate invocation of this method.
     *
     * <p>
     * The implementation in {@link SelectorSupport} does nothing.
     */
    protected void serviceHousekeeping() {
    }

    /**
     * Perform shutdown cleanups.
     *
     * <p>
     * This method is invoked at shutdown from the internal service thread; this instance will be locked at that time.
     *
     * <p>
     * Note: ay invocation from this method to {@link #createSelectionKey createSelectionKey()} will result in
     * an {@link IllegalStateException}.
     *
     * <p>
     * The implementation in {@link SelectorSupport} does nothing.
     */
    protected void serviceCleanup() {
    }

// Debug

    /**
     * Get a debug description of the given keys.
     *
     * @param keys selection keys
     * @return debug description
     */
    public static String dbg(Iterable<? extends SelectionKey> keys) {
        final ArrayList<String> strings = new ArrayList<>();
        for (SelectionKey key : keys)
            strings.add(SelectorSupport.dbg(key));
        return strings.toString();
    }

    /**
     * Get a debug description of the given key.
     *
     * @param key selection key
     * @return debug description
     */
    public static String dbg(SelectionKey key) {
        try {
            return "Key[interest=" + SelectorSupport.dbgOps(key.interestOps()) + ",ready="
              + SelectorSupport.dbgOps(key.readyOps()) + ",obj=" + key.attachment() + "]";
        } catch (java.nio.channels.CancelledKeyException e) {
            return "Key[canceled]";
        }
    }

    /**
     * Get a debug description of the given I/O ops.
     *
     * @param ops I/O operation bits
     * @return debug description
     */
    public static String dbgOps(int ops) {
        final StringBuilder buf = new StringBuilder(4);
        if ((ops & SelectionKey.OP_ACCEPT) != 0)
            buf.append("A");
        if ((ops & SelectionKey.OP_CONNECT) != 0)
            buf.append("C");
        if ((ops & SelectionKey.OP_READ) != 0)
            buf.append("R");
        if ((ops & SelectionKey.OP_WRITE) != 0)
            buf.append("W");
        return buf.toString();
    }

// Service loop

    private void service() throws IOException {
        assert this.isServiceThread();
    serviceLoop:
        while (true) {

            // Check if we're still open
            final Selector currentSelector = this.selector;
            if (currentSelector == null)
                break;

            // Wait for I/O readiness, timeout, or shutdown
            final boolean ready;
            try {
                if (this.log.isTraceEnabled())
                    this.log.trace("[SVC]: sleeping: keys={}", SelectorSupport.dbg(currentSelector.keys()));
                ready = currentSelector.select(this.housekeepingInterval) > 0;
            } catch (ClosedSelectorException e) {               // close() was invoked
                break;
            }
            if (Thread.interrupted() || this.selector == null)
                break;

            // Figure out what has happened
            synchronized (this) {

                // Check if we're still open
                if (this.selector == null)
                    break;

                // Handle any ready I/O
                if (this.log.isTraceEnabled()) {
                    this.log.trace("[SVC]: {}: selectedKeys={}",
                      ready ? "ready" : "awoke", SelectorSupport.dbg(currentSelector.selectedKeys()));
                }

                // Identify keys we are tracking for closure that have disappeared (because their channel was closed)
                final Set<SelectionKey> closureKeys = new HashSet<>(this.closureTrackables);
                closureKeys.removeAll(currentSelector.keys());

                // Remove those from our trackable set; this will be their last notification
                this.closureTrackables.removeAll(closureKeys);

                // Combine the closure keys with the keys reported as ready to get our notification set
                final Set<SelectionKey> notifyKeys = closureKeys;
                notifyKeys.addAll(this.selector.selectedKeys());

                // Notify I/O handlers
                for (SelectionKey key : notifyKeys) {
                    this.selector.selectedKeys().remove(key);
                    final IOHandler handler = (IOHandler)key.attachment();
                    if (this.log.isTraceEnabled())
                        this.log.trace("[SVC]: ready key={} handler={}", SelectorSupport.dbg(key), handler);
                    try {
                        handler.serviceIO(key);
                    } catch (IOException e) {
                        if (this.log.isDebugEnabled())
                            this.log.debug("I/O error from {}", handler, e);
                        handler.close(e);
                    } catch (Throwable t) {
                        this.log.error("service error from {}", handler, t);
                        handler.close(t);
                    }
                    if (this.selector == null)                              // stop() must have been invoked from handler
                        break serviceLoop;
                }

                // Perform housekeeping
                try {
                    this.serviceHousekeeping();
                } catch (Throwable t) {
                    this.log.error("exception during housekeeping", t);
                }
            }
        }

        // Done
        synchronized (this) {
            try {
                this.serviceCleanup();
            } catch (Throwable t) {
                this.log.error("exception during cleanup", t);
            }
            if (this.serviceThread == Thread.currentThread())
                this.serviceThread = null;              // this signals to stop() that we have shut down
            this.notifyAll();
        }
    }

// Service thread

    private class ServiceThread extends Thread {

        ServiceThread() {
            super("Service Thread for " + SelectorSupport.this);
        }

        @Override
        public void run() {
            try {
                SelectorSupport.this.service();
            } catch (ThreadDeath t) {
                throw t;
            } catch (Throwable t) {
                SelectorSupport.this.log.error("unexpected error in service thread", t);
            }
            if (SelectorSupport.this.log.isDebugEnabled())
                SelectorSupport.this.log.debug("{} exiting", this);
        }
    }

    /**
     * Determine whether the current thread is this instance's service thread.
     *
     * @return true if the current thread is this instance's service thread
     */
    public boolean isServiceThread() {
        return Thread.currentThread().equals(this.serviceThread);
    }

// IOHandler

    /**
     * Callback interface used by {@link SelectorSupport}.
     */
    public interface IOHandler {

        /**
         * Handle ready I/O.
         *
         * @param key selection key
         * @throws IOException if an I/O error occurs; this will result in {@link #close close()} being invoked
         */
        void serviceIO(SelectionKey key) throws IOException;

        /**
         * Invoked when an exception is thrown by {@link #serviceIO serviceIO()}.
         *
         * <p>
         * This usually indicates the channel has been (or should be) closed. In any case, it allows for this
         * handler to perform any required cleanup.
         *
         * <p>
         * Typically this method will close the associated channel (if not already closed), which implicitly unregisters
         * the associated {@link SelectionKey}s and causes the {@link #serviceIO serviceIO()} methods of other handlers
         * waiting on the same channel to be invoked, where they will then likely throw
         * {@link java.nio.channels.ClosedChannelException}, which in turn causes a subsequent invocation of this method.
         * Therefore, if this instance is shared by multiple selection keys selecting on the same channel, it should be
         * idempotent.
         *
         * @param cause the error that occurred
         */
        void close(Throwable cause);
    }
}
