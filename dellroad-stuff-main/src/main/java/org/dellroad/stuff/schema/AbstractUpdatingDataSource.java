
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.schema;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A {@link DataSource} that wraps an inner {@link DataSource} and automatically performs some update
 * operation on the inner {@link DataSource} on first access.
 *
 * <p>
 * The update operation is triggered by an invocation of {@link #triggerUpdate} or any {@link DataSource} method.
 *
 * <p>
 * Instances can operate in synchronous or asychronous mode.
 *
 * <p>
 * In synchronous mode, the update is performed by the thread that triggers the update, and simultaneous invocations of any
 * {@link DataSource} method will block until the update is complete.
 *
 * <p>
 * In asynchronous mode, the update is performed in a separate thread, and invocations of any {@link DataSource} method
 * before the update is complete will throw an {@link UpdateInProgressException}.
 *
 * <p>
 * The {@link #getUpdateCompleteFuture} returns a {@link Future} that waits for completion of the initial update;
 * {@link #isUpdated} checks whether the update is complete.
 *
 * <p>
 * The {@link #setDataSource dataSource} property is required.
 */
public abstract class AbstractUpdatingDataSource implements DataSource {

    private static final int INITIAL = 0;
    private static final int UPDATING = 1;
    private static final int UPDATED = 2;
    private static final int FAILED = 3;

    private DataSource dataSource;
    private boolean asynchronous;
    private int state = INITIAL;
    private SQLException error;
    private CompletableFuture<DataSource> future = new CompletableFuture<DataSource>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("cancel() not supported");
        }
    };

    /**
     * Configure the underlying {@link DataSource}. Required property.
     *
     * @param dataSource underlying {@link DataSource} to update
     * @throws IllegalStateException if an update has already been triggered
     */
    public synchronized void setDataSource(DataSource dataSource) {
        if (this.state != INITIAL)
            throw new IllegalStateException("update already triggered");
        this.dataSource = dataSource;
    }

    /**
     * Determine whether this instance is in asynchronous mode.
     *
     * @return true if this instance is in asynchronous mode, otherwise false
     */
    public synchronized boolean isAsynchronous() {
        return this.asynchronous;
    }

    /**
     * Configure mode.
     *
     * @param asynchronous true for asynchronous mode, false for synchronous mode
     * @throws IllegalStateException if an update has already been triggered
     */
    public synchronized void setAsynchronous(boolean asynchronous) {
        if (this.state != INITIAL)
            throw new IllegalStateException("update already triggered");
        this.asynchronous = asynchronous;
    }

    /**
     * Update the inner {@link DataSource}.
     *
     * <p>
     * This method will be invoked at most once.
     *
     * @param dataSource the {@link DataSource} to update
     * @throws SQLException if the update attempt fails
     */
    protected abstract void updateDataSource(DataSource dataSource) throws SQLException;

    /**
     * Get the underlying {@link DataSource}.
     *
     * @return the underlying {@link DataSource}
     */
    protected synchronized DataSource getInnerDataSource() {
        return this.dataSource;
    }

    /**
     * Determine if the underlying {@link DataSource} has been updated.
     *
     * @return true if the underlying {@link DataSource} has already been updated
     */
    public synchronized boolean isUpdated() {
        return this.state == UPDATED;
    }

    /**
     * Trigger the update of the underlying {@link DataSource} if it has not already been triggered.
     *
     * <p>
     * In synchronous mode, this will perform the update in the current thread if necesssary.
     * In asynchronous mode, this will spawn a new anonymous thread to do the update; use {@link #triggerUpdate(Executor)}
     * if more control is needed.
     *
     * @return true if update was triggered by this invocation, false if update had already been triggered or is completed
     * @throws IllegalStateException if no {@link DataSource} has been configured
     * @throws SQLException if the update fails or has already failed
     */
    public boolean triggerUpdate() throws SQLException {

        // Read async flag
        final boolean async;
        synchronized (this) {
            async = this.asynchronous;
        }

        // Trigger update
        return this.triggerUpdate(async ? runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setName("SQL-Updater");
            thread.start();
        } : null);
    }

    /**
     * Trigger the update of the underlying {@link DataSource} if it has not already been triggered.
     *
     * @param executor in asynchronous mode, the executor that will actually perform the update, otherwise ignored
     * @return true if update was triggered by this invocation, false if update had already been triggered or is completed
     * @throws IllegalStateException if no {@link DataSource} has been configured
     * @throws IllegalArgumentException if in asynchronous mode and {@code executor} is null
     * @throws SQLException if the update fails or has already failed
     */
    public boolean triggerUpdate(Executor executor) throws SQLException {

        // Check state
        synchronized (this) {
            switch (this.state) {
            case INITIAL:
                if (this.dataSource == null)
                    throw new IllegalStateException("no DataSource configured");
                this.state = UPDATING;
                if (!this.asynchronous)
                    executor = r -> r.run();
                else if (executor == null)
                    throw new IllegalArgumentException("null executor");
                break;
            case UPDATING:
            case UPDATED:
                return false;
            case FAILED:
                throw new SQLException("update failed", this.error);
            default:
                throw new RuntimeException("internal error: " + this.state);
            }
        }

        // We actually triggered the update so we need to do it
        executor.execute(this::doUpdate);

        // Done
        return true;
    }

    /**
     * Get a {@link Future} that waits for the update of the underlying {@link DataSource} to be completed.
     *
     * <p>
     * The returned {@link Future} does not support cancellation; invoking {@link Future#cancel} will generate
     * an {@link UnsupportedOperationException}.
     *
     * <p>
     * Note that this method does not actually trigger the update; use {@link #triggerUpdate} for that.
     *
     * @return update completed future
     */
    public synchronized Future<DataSource> getUpdateCompleteFuture() {
        return this.future;
    }

// DataSource methods

    @Override
    public Connection getConnection() throws SQLException {
        return this.getUpdatedDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return this.getUpdatedDataSource().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return this.getUpdatedDataSource().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter pw) throws SQLException {
        this.getUpdatedDataSource().setLogWriter(pw);
    }

    @Override
    public void setLoginTimeout(int timeout) throws SQLException {
        this.getUpdatedDataSource().setLoginTimeout(timeout);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return this.getUpdatedDataSource().getLoginTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> cl) throws SQLException {
        return cl.cast(this.getUpdatedDataSource());
    }

    @Override
    public boolean isWrapperFor(Class<?> cl) throws SQLException {
        return cl.isInstance(this.getUpdatedDataSource());
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        try {
            return this.getUpdatedDataSource().getParentLogger();
        } catch (SQLFeatureNotSupportedException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLFeatureNotSupportedException(e);
        }
    }

// Internal methods

    /**
     * Get the wrapped {@link DataSource}, triggering and waiting for it to be updated as necessary.
     *
     * @return updated {@link DataSource}
     * @throws SQLException if an error occurs
     * @throws UpdateInProgressException if an update is in progress and this instance is in asynchronous mode
     */
    protected DataSource getUpdatedDataSource() throws SQLException {

        // Trigger update
        this.triggerUpdate();

        // Throw exception in async mode if we would have to wait
        synchronized (this) {
            if (this.asynchronous && this.state == UPDATING)
                throw new UpdateInProgressException("update still in progress");
        }

        // Wait for update to complete
        try {
            return this.getUpdateCompleteFuture().get();
        } catch (ExecutionException e) {
            throw new SQLException("DataSource update failed", e.getCause());
        } catch (InterruptedException e) {
            throw new SQLException("interrupted while waiting for update to complete", e);
        }
    }

    // Do the update
    private void doUpdate() {

        // Get inner DataSource
        final DataSource innerDataSource;
        synchronized (this) {
            assert this.state == UPDATING;
            innerDataSource = this.dataSource;
        }

        // Perform update
        try {
            this.updateDataSource(innerDataSource);
        } catch (Throwable t) {
            synchronized (this) {
                assert this.state == UPDATING;
                this.state = FAILED;
                this.future.completeExceptionally(t);
                this.error = t instanceof SQLException ? (SQLException)t : new SQLException(t);
                this.notifyAll();
            }
        }

        // Success
        synchronized (this) {
            assert this.state == UPDATING;
            this.state = UPDATED;
            this.future.complete(innerDataSource);
            this.notifyAll();
        }
    }
}
