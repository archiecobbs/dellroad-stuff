
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.data;

import com.google.common.base.Preconditions;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.server.VaadinSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import org.dellroad.stuff.vaadin23.util.AsyncTaskManager;

/**
 * A {@link ListDataProvider} that supports asynchronous loading using a {@link AsyncTaskManager}.
 *
 * <p>
 * Instances are just a {@link ListDataProvider} plus support for race-free (re)loading of the underlying data
 * in a background thread. As a result, the UI never "locks up" while a backend data query executes.
 *
 * <p>
 * The associated {@link AsyncTaskManager} provides status updates when a load operation starts, completes, fails,
 * or is canceled. These updates can be used to drive GUI loading spinners, etc.
 *
 * <p>
 * Loads are triggered via {@link #load load()} and may be canceled in progress via {@link #cancel}.
 *
 * <p>
 * This class handles all required synchronization and locking. See {@link AsyncTaskManager} for details.
 *
 * @param <T> data provider model type
 */
@SuppressWarnings("serial")
public class AsyncDataProvider<T> extends ListDataProvider<T> {

    private final AsyncTaskManager<Stream<? extends T>> taskManager;

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * Caller still must configure an async executor via
     * {@link #getAsyncTaskManager getAsyncTaskManager}{@code ().}{@link AsyncTaskManager#setAsyncExecutor setAsyncExecutor()}.
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public AsyncDataProvider() {
        this(new AsyncTaskManager<>());
    }

    /**
     * Constructor.
     *
     * @param taskManager loading task manager
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public AsyncDataProvider(AsyncTaskManager<Stream<? extends T>> taskManager) {
        super(new ArrayList<>());
        Preconditions.checkArgument(taskManager != null, "null taskManager");
        this.taskManager = taskManager;
        this.taskManager.setResultConsumer(this::updateFromLoad);
    }

// Public Methods

    /**
     * Get the {@link AsyncTaskManager} that manages background load operations.
     *
     * @return this instance's {@link AsyncTaskManager}, never null
     */
    public AsyncTaskManager<Stream<? extends T>> getAsyncTaskManager() {
        return this.taskManager;
    }

    /**
     * Trigger a new asynchronous load of this instance.
     *
     * <p>
     * If/when the given load task completes successfully, its results will completely replace the contents
     * of this data provider.
     *
     * <p>
     * <b>Note</b>: The {@code loader} returns a {@link Stream}, but the {@link Stream} is not actually consumed
     * in the background thread; that operation occurs later, in a different thread with the {@link VaadinSession} locked.
     * In cases where this matters (e.g., the data is gathered in a per-thread transactions), the {@code loader}
     * may need to proactively pull the data into memory ahead of time.
     *
     * <p>
     * The {@code loader} must not return null; if it does, the load fails with an {@link IllegalArgumentException}.
     *
     * @param loader performs the load operation
     * @return unique ID for this load attempt
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalStateException if there is no executor configured
     * @throws IllegalArgumentException if {@code loader} is null
     * @see AsyncTaskManager#startTask
     */
    public long load(Loader<? extends T> loader) {
        Preconditions.checkArgument(loader != null, "null loader");
        return this.taskManager.startTask(id -> {
            final Stream<? extends T> result = loader.load(id);
            if (result == null)
                throw new IllegalArgumentException("loader returned null Stream");
            return result;
        });
    }

    /**
     * Cancel any outstanding asynchronous load of this instance.
     *
     * @return the unique ID of the canceled task, if any, or zero if there is no task outstanding
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @see AsyncTaskManager#cancelTask
     */
    public long cancel() {
        return this.taskManager.cancelTask();
    }

// Internal Methods

    /**
     * Update this data provider's internal list of items using the new data from a successful load operation
     * and fire a refresh notification.
     *
     * <p>
     * This is invoked with {@linkplain #session this instance's session} locked.
     *
     * @param id task ID
     * @param stream load results
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalArgumentException if {@code stream} is null
     */
    protected void updateFromLoad(long id, final Stream<? extends T> stream) {
        final Collection<T> items = this.getItems();
        items.clear();
        stream.forEach(items::add);
        this.refreshAll();
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
         * This method returns a {@link Stream} but the {@link Stream} is not actually consumed in the background thread;
         * that operation occurs later, in a different thread with the {@link VaadinSession} locked.
         * In cases where this matters (e.g., the data is gathered in a per-thread transactions), this method
         * may need to proactively pull the data into memory ahead of time.
         *
         * <p>
         * This method may cancel itself by throwing {@link InterruptedException} unprompted;
         * a {@link AsyncTaskManager.TaskStatusChangeEvent#CANCELED} event will be reported.
         *
         * <p>
         * If this method returns null, the load fails with an {@link IllegalArgumentException} and
         * {@link LoadListener#FAILED} is reported.
         *
         * @param id unique ID for this load attempt
         * @return stream of data items
         * @throws InterruptedException if the current thread is interrupted
         * @throws RuntimeException if an error occurs during loading
         */
        Stream<T> load(long id) throws InterruptedException;
    }
}
