
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.util;

import com.google.common.base.Preconditions;

import java.util.EventObject;

/**
 * Event type emitted by an {@link AsyncTaskManager} when an {@link AsyncTask} changes status.
 *
 * @param <R> task result type
 */
@SuppressWarnings("serial")
public class AsyncTaskStatusChangeEvent<R> extends EventObject {

    /**
     * Status value indicating that a new asynchronous task has started.
     */
    public static final int STARTED = 0;

    /**
     * Status value indicating that an asynchronous task has completed successfully.
     */
    public static final int COMPLETED = 1;

    /**
     * Status value indicating that an asynchronous task was {@link AsyncTaskManager#cancelTask cancelTask()}'ed
     * (or canceled itself by throwing {@link InterruptedException}).
     */
    public static final int CANCELED = 3;

    /**
     * Status value indicating that an asynchronous task failed (i.e., threw an exception).
     */
    public static final int FAILED = 4;

    private final long id;
    private final int status;
    private final R result;
    private final Throwable exception;

    /**
     * Constructor.
     *
     * @param source source for this event
     * @param id task unique ID
     * @param result task result, or null if not {@link #COMPLETED}
     * @param exception thrown exception, or null if not {@link #CANCELED} or {@link #FAILED}
     * @throws IllegalArgumentException if {@code source} is null
     * @throws IllegalArgumentException if {@code id} is zero
     * @throws IllegalArgumentException if {@code status} is invalid
     * @throws IllegalArgumentException if {@code result} or {@code exception} is inconsistent with {@code status}
     */
    public AsyncTaskStatusChangeEvent(AsyncTaskManager<R> source, long id, int status, R result, Throwable exception) {
        super(source);
        Preconditions.checkArgument(source != null, "null source");
        Preconditions.checkArgument(id != 0, "zero id");
        switch (status) {
        case STARTED:
            Preconditions.checkArgument(result == null, "result must be null");
            Preconditions.checkArgument(exception == null, "exception must be null");
            break;
        case COMPLETED:
            Preconditions.checkArgument(exception == null, "exception must be null");
            break;
        case CANCELED:
        case FAILED:
            Preconditions.checkArgument(result == null, "result must be null");
            Preconditions.checkArgument(exception != null, "exception must not be null");
            break;
        default:
            throw new IllegalArgumentException("invalid status");
        }
        this.id = id;
        this.status = status;
        this.result = result;
        this.exception = exception;
    }

    /**
     * Get the ID of the associated asynchronous task.
     *
     * @return task ID
     */
    public long getId() {
        return this.id;
    }

    /**
     * Get the new status of the associated asynchronous task: one of {@link #STARTED}, {@link #COMPLETED},
     * {@link #CANCELED}, or {@link #FAILED}.
     *
     * @return status
     */
    public int getStatus() {
        return this.status;
    }

    /**
     * Get the {@link AsyncTaskManager} that originated this event.
     *
     * @return event originator
     */
    @Override
    @SuppressWarnings("unchecked")
    public AsyncTaskManager<R> getSource() {
        return (AsyncTaskManager<R>)super.getSource();
    }

    /**
     * Get the result from the successfully completed associated asynchronous task.
     *
     * <p>
     * This method returns null except when the status is {@link #COMPLETED}, in which case it returns
     * the result of the task.
     *
     * @return task result, or null if task has not completed successfully
     */
    public R getResult() {
        return this.result;
    }

    /**
     * Get the exception thrown by the associated asynchronous task that failed or was canceled.
     *
     * <p>
     * This method returns null except when the status is {@link #FAILED} or {@link #CANCELED},
     * in which case it returns the exception thrown. In the case of {@link #CANCELED}, this will
     * always be an {@link InterruptedException}.
     *
     * @return error thrown, or null if task has not yet finished or completed successfully
     */
    public Throwable getException() {
        return this.exception;
    }
}
