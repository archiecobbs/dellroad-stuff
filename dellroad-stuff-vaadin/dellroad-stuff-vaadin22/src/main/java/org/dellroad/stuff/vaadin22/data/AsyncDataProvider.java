
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.data;

import com.google.common.base.Preconditions;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.dellroad.stuff.vaadin22.util.VaadinUtil;
import org.slf4j.LoggerFactory;

/**
 * A {@link ListDataProvider} that supports asynchronous loading.
 *
 * <p>
 * Instances are just a {@link ListDataProvider} with an added ability to load the underlying data list using
 * the results from an asynchronous background query. These async load attempts are initiated via {@link #load load}
 * and may be canceled in progress via {@link #cancel}.
 *
 * <p>
 * {@link LoadListener}'s receive status updates when a task starts, completes, fails, or is canceled. These updates
 * can be used to drive GUI loading spinners, etc.
 *
 * <p>
 * This class handles all required synchronization and locking. It guarantees that at most one async load task
 * can be happening at a time, that listener notifications are delivered in proper order, and that instance's
 * state can not change unexpectely as long as the current {@link VaadinSession} remains locked.
 *
 * <p>
 * Instances bind to the current {@link VaadinSession} at construction time and may only be used with that session.
 *
 * @param <T> data provider model type
 */
@SuppressWarnings("serial")
public class AsyncDataProvider<T> extends ListDataProvider<T> {

    private static final AtomicLong LAST_LOAD_ID = new AtomicLong(0);

    /**
     * The {@link VaadinSession} with which this instance is associated.
     */
    protected final VaadinSession session = VaadinUtil.getCurrentSession();

    private final HashSet<LoadListener> listeners = new HashSet<>();

    private ExecutorService executorService;

    private Future<?> currentFuture;                                    // non-null iff some task is running AND not canceled
    private long currentId;                                             // non-zero iff some task is running AND not canceled

// Constructors

    /**
     * Constructor.
     *
     * <p>
     * Caller still must configure an {@link ExecutorService} via {@link #setExecutorService setExecutorService()}.
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public AsyncDataProvider() {
        super(new ArrayList<>());
    }

    /**
     * Constructor.
     *
     * @param executorService the {@link ExecutorService} used to execute async load tasks
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public AsyncDataProvider(ExecutorService executorService) {
        this();
        this.setExecutorService(executorService);
    }

// Public Methods

    /**
     * Get the {@link ExecutorService} used for async tasks.
     *
     * @return the {@link ExecutorService} that this instance will use to execute async load tasks
     */
    public ExecutorService getExecutorService() {
        return this.executorService;
    }

    /**
     * Configure the {@link ExecutorService} used for async tasks.
     *
     * @param executorService the {@link ExecutorService} used to execute async load tasks
     */
    public void setExecutorService(final ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Trigger a new asynchronous load of this instance.
     *
     * <p>
     * If there is already an async load in progress, this method will {@link #cancel cancel()} it.
     * You can check this ahead of time via {@link #isBusy}.
     *
     * <p>
     * If/when the given load task completes, its results will completely replace the contents of this data provider.
     *
     * @param loader performs the load operation
     * @return unique ID for this load attempt
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalStateException if there is no {@link ExecutorService} configured
     * @throws IllegalArgumentException if either parameter is null
     */
    public long load(Loader<? extends T> loader) {

        // Sanity check
        Preconditions.checkArgument(loader != null, "null loader");
        VaadinUtil.assertCurrentSession(this.session);
        final ExecutorService executor = this.getExecutorService();
        Preconditions.checkState(executor != null, "no ExecutorService");

        // Cancel existing task, if any
        this.cancel();

        // Get the next unique load ID
        final long id = nextId();

        // Enqueue STARTED notification
        this.notifyListeners(id, LoadListener.STARTED, null);

        // Start task and update state
        this.currentFuture = executor.submit(() -> this.performLoad(id, loader));
        this.currentId = id;

        // Done
        return id;
    }

    /**
     * Determine whether there is an outstanding async load operation.
     *
     * @return true if an async load operation is active, otherwise false
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public boolean isBusy() {
        VaadinUtil.assertCurrentSession(this.session);
        return this.currentId != 0;
    }

    /**
     * Attempt to cancel the current outstanding async load operation, if any.
     *
     * <p>
     * Note: this may result in {@linkplain Thread#interrupt interrupting} the async thread.
     *
     * <p>
     * If this method returns non-zero, it is guaranteed that the results of the corresponding load attempt
     * are not applied.
     *
     * @return the unique ID of the canceled load attempt, if any, otherwise zero
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public long cancel() {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);

        // Any task outstanding?
        final long id = this.currentId;
        if (id == 0)
            return 0;

        // Enqueue CANCEL notification
        this.notifyListeners(id, LoadListener.CANCELED, null);

        // Cancel task
        this.currentFuture.cancel(true);
        this.currentFuture = null;
        this.currentId = 0;

        // Done
        return id;
    }

    /**
     * Add a {@link LoadListener} to this instance.
     *
     * @param listener
     * @return listener registration
     * @throws IllegalArgumentException if {@code listener} is null
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public Registration addLoadListener(LoadListener listener) {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);
        Preconditions.checkArgument(listener != null, "null listener");

        // Add listener
        return Registration.addAndRemove(this.listeners, listener);
    }

// Internal Methods

    /**
     * Get the next unique ID.
     *
     * @return unique ID, never zero
     */
    protected long nextId() {
        while (true) {                                              // just in case of in the unlikely event of a roll-over
            final long nextId = LAST_LOAD_ID.incrementAndGet();
            if (nextId != 0)
                return nextId;
        }
    }

    /**
     * Notify listeners (later).
     *
     * @param id task ID
     * @param status task status
     * @param error task error or null
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    protected void notifyListeners(final long id, final int status, final Throwable error) {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);

        // Notify listeners (later)
        VaadinUtil.accessSession(this.session,
          () -> new ArrayList<>(this.listeners).stream().forEach(
            listener -> listener.handleLoadingStatusChange(id, status, error)));
    }

    /**
     * Perform the load operation.
     *
     * <p>
     * This is invoked in the async background thread.
     *
     * <p>
     * When done, this method should invoke {@link #applyLoad applyLoa()}
     * with {@linkplain #session this instance's session} locked.
     *
     * @param id task ID
     * @param loader load callback
     * @throws IllegalArgumentException if {@code id} is zero
     * @throws IllegalArgumentException if {@code loader} is null
     */
    protected void performLoad(final long id, final Loader<? extends T> loader) {

        // Sanity check
        Preconditions.checkArgument(id != 0, "zero id");
        Preconditions.checkArgument(loader != null, "null loader");

        // Do the load and gather results
        Throwable error = null;
        Stream<? extends T> stream = null;
        try {
            stream = loader.load(id);
            Preconditions.checkArgument(stream != null, "loader returned a null Stream");
        } catch (InterruptedException e) {
            error = e;
        } catch (ThreadDeath t) {
            throw t;
        } catch (Throwable t) {
            this.handleLoaderException(id, t);
            error = t;
        }

        // Apply results
        final Throwable error2 = error;
        final Stream<? extends T> stream2 = stream;
        VaadinUtil.accessSession(this.session, () -> this.applyLoad(id, error2, stream2));
    }

    /**
     * Apply the results of a load operation.
     *
     * <p>
     * This is invoked with {@linkplain #session this instance's session} locked.
     *
     * @param id task ID
     * @param error load error, or null if none
     * @param stream load results
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalArgumentException if {@code id} is zero
     * @throws IllegalArgumentException if {@code stream} is null
     */
    protected void applyLoad(final long id, final Throwable error, final Stream<? extends T> stream) {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);
        Preconditions.checkArgument(id != 0, "zero id");
        Preconditions.checkArgument(stream != null, "null stream");

        // If we were canceled, bail out without making any changes
        if (id != this.currentId)
            return;

        // Reset state
        this.currentFuture = null;
        this.currentId = 0;

        // Enqueue COMPLETED or FAILED notification
        this.notifyListeners(id, error != null ? LoadListener.FAILED : LoadListener.COMPLETED, error);

        // Update data if successful
        if (error == null) {
            this.getItems().clear();
            stream.forEach(this.getItems()::add);
            this.refreshAll();
        }
    }

    /**
     * Invoked when an exception is thrown by the {@link Loader}.
     *
     * <p>
     * This method runs in the background thread and the {@link VaadinSession} will not be locked.
     *
     * <p>
     * The implementation in {@link AsyncDataProvider} just logs an error.
     *
     * @param id the unique ID of the load attempt that failed
     * @param t the exception that was caught
     */
    protected void handleLoaderException(long id, Throwable t) {
        LoggerFactory.getLogger(this.getClass()).error("exception from async loader task #" + id, t);
    }

// Loader

    /**
     * Callback interface for asynchronous loading of data for a {@link AsyncDataProvider}.
     *
     * @param <T> data provider model type
     */
    @FunctionalInterface
    public interface Loader<T> {

        /**
         * Perform a load operation.
         *
         * <p>
         * This method runs in a background thread; in particular, no {@link VaadinSession} will be locked.
         *
         * <p>
         * This method should be prepared to handle an {@linkplain Thread#interrupt interrupt} if/when
         * {@link AsyncDataProvider#cancel} is invoked; in that case this method may throw {@link InterruptedException}.
         *
         * <p>
         * This method returns a {@link Stream} but the {@link Stream} is not actually read in the background thread;
         * that operation occurs later, with the {@link VaadinSesion} locked, and possibly in a different thread.
         * In cases where this matters (e.g., the data is gathered in a per-thread transactions), this method
         * may need to proactively pull the data into memory ahead of time.
         *
         * @param id unique ID for this load attempt
         * @return stream of data items
         * @throws InterruptedException if the current thread is interrupted
         * @throws RuntimeException if an error occurs during loading
         */
        Stream<T> load(long id) throws InterruptedException;
    }

// LoadListener

    /**
     * Listener interface for notifications about {@link AsyncDataProvider} asynchronous load attempts.
     *
     * <p>
     * {@link AsyncDataProvider} guarantees "proper" ordering of notifications:
     * <ul>
     *  <li>Exactly one {@link #STARTED} notification and one {@link #COMPLETED}, {@link #CANCELED},
     *      or {@link #FAILED} notification is delivered for each task.
     *  <li>{@link #STARTED} notifications are always delivered before the corresponding
     *      {@link #COMPLETED}, {@link #CANCELED}, or {@link #FAILED} notification for the same task.
     *  <li>The {@link #COMPLETED}, {@link #CANCELED}, or {@link #FAILED} notification for a task
     *      is always delivered before the {@link #STARTED} notification for the next task.
     *  <li>Notifications are delivered for tasks in the same order that the tasks were started
     *      (by invoking {@link AsyncDataProvider#load AsyncDataProvider#load.load()}).
     * </ul>
     */
    @FunctionalInterface
    public interface LoadListener {

        /**
         * Status value indicating that async loading has started.
         */
        int STARTED = 0;

        /**
         * Status value indicating that async loading successfully completed.
         */
        int COMPLETED = 1;

        /**
         * Status value indicating that async loading was {@link AsyncDataProvider#cancel cancel()}'ed,
         * and that its results (if any) were not applied.
         */
        int CANCELED = 3;

        /**
         * Status value indicating that async loading failed.
         */
        int FAILED = 4;

        /**
         * Receive notification that asynchronous loading has either started, completed, been canceled, or failed.
         *
         * @param id unique ID for this async load attempt
         * @param status async load status
         * @param error exception if {@code status} is {@link #FAILED}, otherwise null
         */
        void handleLoadingStatusChange(long id, int status, Throwable error);
    }
}
