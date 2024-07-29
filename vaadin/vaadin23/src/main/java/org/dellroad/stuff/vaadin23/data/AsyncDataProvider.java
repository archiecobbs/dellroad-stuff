
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.data;

import com.google.common.base.Preconditions;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.server.VaadinSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import org.dellroad.stuff.vaadin23.util.AsyncTaskManager;
import org.dellroad.stuff.vaadin23.util.VaadinUtil;

/**
 * A {@link ListDataProvider} whose contents are gathered in an asynchronous query.
 *
 * <p>
 * Instances are just a {@link ListDataProvider} with support for asynchronous (re)loading of the underlying data
 * in a background thread using an {@link AsyncTaskManager}. As a result, the UI never "locks up" while a backend
 * data query executes.
 *
 * <p>
 * The associated {@link AsyncTaskManager} provides status change notifications when a load operation starts,
 * completes, fails, or is canceled. These updates can be used to drive GUI loading spinners, etc.
 *
 * <p>
 * Load operations are initiated via {@link #load load()}, and may be canceled in progress via {@link #cancel}.
 * Initiating a new load operation will cancel and replace any previous load operation still in progress.
 *
 * <p>
 * All operations are atomic and race free. See {@link AsyncTaskManager} for details.
 *
 * <p><b>Queries</b>
 *
 * <p>
 * The {@link DataProvider}'s {@link #fetch fetch()} and {@link #size size()} methods take a {@link Query} parameter,
 * which includes basic filtering, sorting, and offset/limit information for the query. However, because these methods
 * are synchronous, they not compatible with this class' aynchronous loading model. That's why this class extends
 * {@link ListDataProvider}, which applies the {@link Query} to a list of objects already loaded into memory by other means.
 * The purpose of this class is therefore simply to refresh that list, aynchronously, {@linkplain #load when requested}.
 *
 * <p>
 * However, there may be scenarios where the aynchronous load operation can benefit from knowing the {@link Query} currently
 * being used, i.e., most recently passed to {@link #fetch fetch()} or {@link #size size()}. For such scenarios, subclasses
 * could override these methods and trigger a new load operation if the {@link Query} has changed, etc.
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
     * See {@link Loader#load Loader} for important requirements for the behavior of {@code loader}.
     *
     * @param loader performs the load operation
     * @return unique ID for this load attempt
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalStateException if this instance's {@link AsyncTaskManager} has no executor configured
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
        VaadinUtil.assertCurrentSession(this.taskManager.getVaadinSession());
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
         * <b>Note</b>: This method returns a {@link Stream} but the {@link Stream} is not actually consumed in the
         * background thread; that operation occurs later, in a different thread with the {@link VaadinSession} locked.
         * In cases where this matters (e.g., the data is gathered in a per-thread transaction), this method
         * may need to proactively pull the data into memory ahead of time.
         *
         * <p>
         * This method should be prepared to handle an {@linkplain Thread#interrupt interrupt} if/when
         * {@link AsyncDataProvider#cancel} is invoked; in that case this method may throw {@link InterruptedException}.
         * This method may also cancel itself by throwing an unprompted {@link InterruptedException}.
         *
         * <p>
         * If this method returns null, the load fails with an {@link IllegalArgumentException}.
         *
         * @param id unique ID for this load attempt
         * @return stream of data items
         * @throws InterruptedException if the current thread is interrupted
         * @throws RuntimeException if an error occurs during loading
         */
        Stream<? extends T> load(long id) throws InterruptedException;
    }
}
