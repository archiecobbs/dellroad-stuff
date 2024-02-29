
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

import com.google.common.base.Preconditions;
import com.vaadin.flow.server.VaadinSession;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A simplified asynchronous task manager.
 *
 * <p>
 * This class wraps an underlying {@link AsyncTaskManager} and provides a simplified interface,
 * where each task has its own result type, and success and error handlers.
 *
 * <p>
 * All of the safety guarantees provided by {@link AsyncTaskManager} are provided by this class.
 * As with {@link AsyncTaskManager}, only one task can be executing at a time, and starting a new task
 * automatically cancels any previous task that is still outstanding.
 */
public class SimpleTaskManager {

    protected final AsyncTaskManager<Object> taskManager;

    // These are for the currently executing task, if any
    private Consumer<?> onSuccess;
    private Consumer<? super Throwable> onError;

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * Caller must configure an async executor via {@link #setAsyncExecutor setAsyncExecutor()}.
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public SimpleTaskManager() {
        this(new AsyncTaskManager<>());
    }

    /**
     * Constructor.
     *
     * @param executor the executor used to execute async tasks, or null for none
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public SimpleTaskManager(Function<? super Runnable, ? extends Future<?>> executor) {
        this(new AsyncTaskManager<>(executor));
    }

    private SimpleTaskManager(AsyncTaskManager<Object> taskManager) {
        this.taskManager = taskManager;
        this.taskManager.addAsyncTaskStatusChangeListener(this::handleTaskResult);
    }

// Public Methods

    /**
     * Get the {@link VaadinSession} to which this instance is associated.
     *
     * @return this instance's {@link VaadinSession}, never null
     */
    public VaadinSession getVaadinSession() {
        return this.taskManager.getVaadinSession();
    }

    /**
     * Configure the executor used for async tasks.
     *
     * <p>
     * The executor must execute tasks with {@linkplain #getVaadinSession this instance's VaadinSession} unlocked.
     *
     * <p>
     * Note: when an in-progress task is canceled via {@link #cancelTask}, then {@link Future#cancel Future.cancel()}
     * will be invoked on the {@link Future} returned by the executor.
     *
     * @param executor the thing that launches background tasks, or null for none
     */
    public void setAsyncExecutor(final Function<? super Runnable, ? extends Future<?>> executor) {
        this.taskManager.setAsyncExecutor(executor);
    }

// Public Methods

    /**
     * Start a new task that returns some value.
     *
     * <p>
     * Any previous task that is still executing will be cancelled.
     *
     * @param action the task to perform
     * @param onSuccess callback when task is successful
     * @param onError callback when task fails or is interrupted
     * @param <T> task result type
     * @throws IllegalStateException if the current thread is not associated with
     *  {@linkplain #getVaadinSession this instance's session}
     */
    public <T> void startTask(Callable<? extends T> action, Consumer<? super T> onSuccess, Consumer<? super Throwable> onError) {
        Preconditions.checkArgument(action != null, "null action");
        Preconditions.checkArgument(onSuccess != null, "null onSuccess");
        Preconditions.checkArgument(onError != null, "null onError");
        Preconditions.checkState(this.taskManager != null, "not initialized");
        Preconditions.checkState(!this.taskManager.isBusy(), "a task is already executing");
        this.onSuccess = onSuccess;
        this.onError = onError;
        this.taskManager.startTask(id -> action.call());
    }

    /**
     * Start a new task that returns nothing.
     *
     * <p>
     * Any previous task that is still executing will be cancelled.
     *
     * @param action the task to perform
     * @param onSuccess callback when task is successful
     * @param onError callback when task fails or is interrupted
     * @throws IllegalStateException if the current thread is not associated with
     *  {@linkplain #getVaadinSession this instance's session}
     */
    public void startTask(ThrowingRunnable action, Runnable onSuccess, Consumer<? super Throwable> onError) {
        Preconditions.checkArgument(action != null, "null action");
        Preconditions.checkArgument(onSuccess != null, "null onSuccess");
        Preconditions.checkArgument(onError != null, "null onError");
        Preconditions.checkState(this.taskManager != null, "not initialized");
        Preconditions.checkState(!this.taskManager.isBusy(), "a task is already executing");
        this.onSuccess = ignored -> onSuccess.run();
        this.onError = onError;
        this.taskManager.startTask(id -> {
            action.run();
            return null;
        });
    }

    /**
     * Determine whether there is an outstanding asynchronous task in progress.
     *
     * @return true if an asynchronous task is currently executing, otherwise false
     * @throws IllegalStateException if the current thread is not associated with
     *  {@linkplain #getVaadinSession this instance's session}
     */
    public boolean isBusy() {
        return this.taskManager.isBusy();
    }

    /**
     * Cancel current task, if any
     *
     * @return true if task was cancelled, otherwise false
     * @throws IllegalStateException if the current thread is not associated with
     *  {@linkplain #getVaadinSession this instance's session}
     */
    public boolean cancelTask() {
        return this.taskManager.cancelTask() != 0;
    }

// ThrowingRunnable

    @FunctionalInterface
    public interface ThrowingRunnable {

        /**
         * Perform some action.
         *
         * @throws Exception if an error occurs
         */
        void run() throws Exception;
    }

// Private Methods

    private void handleTaskResult(AsyncTaskStatusChangeEvent<Object> event) {

        // Ignore starting event
        if (event.getStatus() == AsyncTaskStatusChangeEvent.STARTED)
            return;

        // Handle success/failure
        switch (event.getStatus()) {
        case AsyncTaskStatusChangeEvent.COMPLETED:
            this.deliverResult(onSuccess, event.getResult());
            break;
        case AsyncTaskStatusChangeEvent.FAILED:
        case AsyncTaskStatusChangeEvent.CANCELED:
            this.onError.accept(event.getException());
            break;
        default:
            throw new RuntimeException("internal error");
        }

        // Clean up
        this.onSuccess = null;
        this.onError = null;
    }

    // Capture the unchecked cast
    @SuppressWarnings("unchecked")
    private <T> void deliverResult(Consumer<T> handler, Object obj) {
        handler.accept((T)obj);
    }
}
