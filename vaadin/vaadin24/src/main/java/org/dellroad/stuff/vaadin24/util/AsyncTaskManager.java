
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.LoggerFactory;

/**
 * Allows applications to safely manage asynchronous tasks that execute in the background (without holding
 * the {@link VaadinSession} lock) entirely within the context of a locked {@link VaadinSession}.
 *
 * <p>
 * Instances of this class manage some background activity or task that is initiated from within a {@link VaadinSession}.
 * The task runs asynchronously in the background without holding the {@link VaadinSession} lock. Once completed, the
 * result is reported back to the locked {@link VaadinSession} and the configured
 * {@linkplain #setResultConsumer result consumer}, if any.
 *
 * <p>
 * Only one such task is allowed to be executing at any given time: if a second task is started while an existing task is
 * still in progress, the first task is automatically canceled. Tasks are initiated via {@link #startTask startTask()}
 * and may be canceled at any time via {@link #cancelTask cancelTask()}.
 *
 * <p>
 * Results returned from successful task executions are delivered to the configured
 * {@linkplain #setResultConsumer result consumer}, if any.
 *
 * <p><b>Safety Guarantees</b>
 *
 * <p>
 * This class handles all required synchronization and locking. It guarantees that at most one background task
 * can be executing at a time, that all operations are atomic, that listener notifications are delivered in proper order,
 * and that no race conditions can occur. For example, if a background task tries to report back at the same time a Vaadin
 * thread invokes {@link #cancelTask}, then the task will always appear to have either completed successfully or been canceled.
 *
 * <p>
 * Instances bind to the current {@link VaadinSession} at construction time and may only be used with that session.
 * If any method is invoked with the wrong {@link VaadinSession} locking state, an immediate exception is thrown.
 * Therefore, thread safety is not only provided but enforced.
 *
 * <p>
 * If a {@linkplain UI#getCurrent current UI} exists when {@link #startTask} is invoked, it will also be restored
 * (along with the current {@link VaadinSession}) when any corresponding callbacks are invoked, unless it has since
 * been detached.
 *
 * <p><b>Event Notifications</b>
 *
 * <p>
 * Instances support event notification via {@link #addAsyncTaskStatusChangeListener addAsyncTaskStatusChangeListener()}.
 * All notifications are delivered within the context of the locked {@link VaadinSession}.
 *
 * <p>
 * On task start, a {@link AsyncTaskStatusChangeEvent#STARTED STARTED} notification is generated. When the task finishes,
 * the outcome - one of: {@link AsyncTaskStatusChangeEvent#COMPLETED COMPLETED},
 * {@link AsyncTaskStatusChangeEvent#CANCELED CANCELED}, or {@link AsyncTaskStatusChangeEvent#FAILED FAILED} - is reported.
 *
 * <p>
 * Proper ordering of event notifications is guaranteed:
 * <ul>
 *  <li>Exactly one {@link AsyncTaskStatusChangeEvent#STARTED STARTED} notification and exactly one
 *      {@link AsyncTaskStatusChangeEvent#COMPLETED COMPLETED}, {@link AsyncTaskStatusChangeEvent#CANCELED CANCELED},
 *      or {@link AsyncTaskStatusChangeEvent#FAILED FAILED} notification will be delivered
 *      for each task initiated by {@link AsyncTaskManager#startTask startTask()}.
 *  <li>{@link AsyncTaskStatusChangeEvent#STARTED STARTED} notifications are always delivered before the corresponding
 *      {@link AsyncTaskStatusChangeEvent#COMPLETED COMPLETED}, {@link AsyncTaskStatusChangeEvent#CANCELED CANCELED},
 *      or {@link AsyncTaskStatusChangeEvent#FAILED FAILED} notification for the same task.
 *  <li>The {@link AsyncTaskStatusChangeEvent#COMPLETED COMPLETED}, {@link AsyncTaskStatusChangeEvent#CANCELED CANCELED}, or
 *      {@link AsyncTaskStatusChangeEvent#FAILED FAILED} notification for a task is always delivered before the
 *      {@link AsyncTaskStatusChangeEvent#STARTED STARTED} notification for any subsequent task.
 *  <li>Tasks are executed, and corresponding notifications are delivered, in the same order that they are started.
 * </ul>
 *
 * @param <R> asynchronous task result type
 * @see SimpleTaskManager
 */
@SuppressWarnings("serial")
public class AsyncTaskManager<R> {

    /**
     * The {@link VaadinSession} with which this instance is associated.
     */
    protected final VaadinSession session = VaadinUtil.getCurrentSession();

    private final HashSet<AsyncTaskStatusChangeListener<R>> listeners = new HashSet<>();
    private final AtomicLong lastTaskId = new AtomicLong();

    private Function<? super Runnable, ? extends Future<?>> executor;
    private BiConsumer<? super Long, ? super R> resultConsumer;

    private Future<?> currentFuture;                                    // non-null iff some task is running AND not canceled
    private long currentId;                                             // non-zero iff some task is running AND not canceled
    private UI currentUI;                                               // non-null iff some task is running AND not canceled

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * Caller must configure an async executor via {@link #setAsyncExecutor setAsyncExecutor()}.
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public AsyncTaskManager() {
    }

    /**
     * Constructor.
     *
     * @param executor the executor used to execute async tasks, or null for none
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public AsyncTaskManager(Function<? super Runnable, ? extends Future<?>> executor) {
        this.setAsyncExecutor(executor);
    }

// Public Methods

    /**
     * Get the {@link VaadinSession} to which this instance is associated.
     *
     * @return this instance's {@link VaadinSession}, never null
     */
    public VaadinSession getVaadinSession() {
        return this.session;
    }

    /**
     * Configure the executor used for async tasks.
     *
     * <p>
     * The executor must execute tasks with {@linkplain #session this instance's VaadinSession} unlocked.
     *
     * <p>
     * Note: when an in-progress task is canceled via {@link #cancelTask}, then {@link Future#cancel Future.cancel()}
     * will be invoked on the {@link Future} returned by the executor.
     *
     * @param executor the thing that launches background tasks, or null for none
     */
    public void setAsyncExecutor(final Function<? super Runnable, ? extends Future<?>> executor) {
        this.executor = executor;
    }

    /**
     * Configure where successful results are delivered.
     *
     * <p>
     * The given consumer will always be invoked with {@linkplain #session this instance's VaadinSession} locked.
     *
     * @param resultConsumer recipient for successful task results, taking task ID and result, or null to discard task results
     */
    public void setResultConsumer(BiConsumer<? super Long, ? super R> resultConsumer) {
        this.resultConsumer = resultConsumer;
    }

    /**
     * Trigger execution of a new asynchronous task.
     *
     * <p>
     * If there is already an asynchronous task in progress, this method will {@linkplain #cancelTask cancel} it first.
     * You can safely check this ahead of time via {@link #isBusy}; this is race-free as long as the session lock is held
     * across both method invocations.
     *
     * @param task performs the desired task and returns some result
     * @return unique ID for this task execution
     * @throws IllegalStateException if {@linkplain #session this instance's VaadinSession} is not locked by the current thread
     * @throws IllegalStateException if there is no executor configured
     * @throws IllegalArgumentException if {@code task} is null
     */
    public long startTask(AsyncTask<? extends R> task) {

        // Sanity check
        Preconditions.checkArgument(task != null, "null task");
        VaadinUtil.assertCurrentSession(this.session);
        Preconditions.checkState(this.executor != null, "no executor");

        // Cancel existing task, if any
        this.cancelTask();

        // Get the next unique task ID
        final long id = this.nextTaskId();
        Preconditions.checkArgument(id != 0, "invalid task ID");

        // Emit STARTED notification
        this.notifyListeners(UI.getCurrent(),
          new AsyncTaskStatusChangeEvent<>(this, id, AsyncTaskStatusChangeEvent.STARTED, null, null));

        // Start task and update state
        this.currentFuture = this.executor.apply((Runnable)() -> this.invokeTask(id, task));
        this.currentId = id;
        this.currentUI = UI.getCurrent();

        // Done
        return id;
    }

    /**
     * Determine whether there is an outstanding asynchronous task in progress.
     *
     * <p>
     * Equivalent to: {@code getCurrentTaskId() != 0}.
     *
     * @return true if an asynchronous task is currently executing, otherwise false
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public boolean isBusy() {
        VaadinUtil.assertCurrentSession(this.session);
        return this.currentId != 0;
    }

    /**
     * Get the ID of the currently outstanding asynchronous task, if any.
     *
     * @return the unique ID of the current asynchronous task, if any, otherwise zero
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public long getCurrentTaskId() {
        VaadinUtil.assertCurrentSession(this.session);
        return this.currentId;
    }

    /**
     * Cancels the current outstanding asynchronous task, if any, and returns its unique ID.
     *
     * <p>
     * Any currently executing asynchronous task canceled and {@link Future#cancel Future.cancel()}
     * is invoked on it's {@link Future}, which may result in the background thread being
     * {@linkplain Thread#interrupt interrupted}.
     *
     * <p>
     * This method guarantees that the corresponding task, if any, will have a
     * {@link AsyncTaskStatusChangeEvent#CANCELED CANCELED} outcome.
     *
     * @return the unique ID of the canceled task, if any, or zero if there is no task outstanding
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public long cancelTask() {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);

        // Any task outstanding?
        final long id = this.currentId;
        if (id == 0)
            return 0;

        // Enqueue CANCEL notification
        this.notifyListeners(this.currentUI,
          new AsyncTaskStatusChangeEvent<>(this, id, AsyncTaskStatusChangeEvent.CANCELED, null, null));

        // Cancel task
        this.currentFuture.cancel(true);
        this.currentFuture = null;
        this.currentUI = null;
        this.currentId = 0;

        // Done
        return id;
    }

    /**
     * Add a {@link AsyncTaskStatusChangeListener} to this instance.
     *
     * @param listener listener for notifications
     * @return listener registration
     * @throws IllegalArgumentException if {@code listener} is null
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    public Registration addAsyncTaskStatusChangeListener(AsyncTaskStatusChangeListener<R> listener) {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);
        Preconditions.checkArgument(listener != null, "null listener");

        // Add listener
        return Registration.addAndRemove(this.listeners, listener);
    }

// Internal Methods

    /**
     * Get the next unique task ID.
     *
     * <p>
     * Each invocation of this method returns a new value.
     *
     * @return unique task ID, never zero
     */
    protected long nextTaskId() {
        while (true) {                                              // just in case of in the unlikely event of a roll-over
            final long nextTaskId = this.lastTaskId.incrementAndGet();
            if (nextTaskId != 0)
                return nextTaskId;
        }
    }

    /**
     * Perform the asynchronous task.
     *
     * <p>
     * This method is invoked in the background, with {@linkplain #session this instance's session} <i>not</i> locked.
     *
     * <p>
     * When finished (regardless of the outcome) this method invokes {@link #reportTask reportTask()}
     * with {@linkplain #session this instance's session} locked.
     *
     * @param id task ID
     * @param task task to execute
     * @throws IllegalStateException if the current thread has {@linkplain #session this instance's session} locked
     * @throws IllegalArgumentException if {@code id} is zero
     * @throws IllegalArgumentException if {@code task} is null
     */
    protected void invokeTask(final long id, final AsyncTask<? extends R> task) {

        // Sanity check
        Preconditions.checkArgument(id != 0, "zero id");
        Preconditions.checkArgument(task != null, "null task");
        VaadinUtil.assertNotSession(this.session);

        // Do the task and gather results
        R result = null;
        Throwable exception = null;
        try {
            result = task.perform(id);
        } catch (InterruptedException e) {
            exception = e;
        } catch (Throwable t) {
            this.handleTaskException(id, t);
            exception = t;
        }

        // Apply results
        final R result2 = result;
        final Throwable exception2 = exception;
        VaadinUtil.accessSession(this.session, () -> this.reportTask(id, result2, exception2));
    }

    /**
     * Report the outcome of an asynchronous task (whether successful or otherwise) back to the {@link VaadinSession}.
     *
     * <p>
     * This is invoked (indirectly) by {@link #invokeTask invokeTask()} with {@linkplain #session this instance's session} locked.
     *
     * @param id task ID
     * @param result task result; must be null if there was an exception
     * @param exception thrown exception ({@link InterruptedException} if interrupted), or null if there was no exception
     * @return true if {@code id} matched the current task ID, otherwise false
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalArgumentException if {@code id} is zero
     * @throws IllegalArgumentException if {@code result} and {@code exception} are both not null
     */
    protected boolean reportTask(final long id, final R result, final Throwable exception) {

        // Sanity check
        VaadinUtil.assertCurrentSession(this.session);
        Preconditions.checkArgument(id != 0, "zero id");
        Preconditions.checkArgument(exception == null || result == null, "result and exception both given");

        // If we were canceled, silently go away
        if (id != this.currentId)
            return false;

        // Reset state
        final UI ui = this.currentUI;
        this.currentFuture = null;
        this.currentUI = null;
        this.currentId = 0;

        // Enqueue the appropriate notification
        final int status = exception instanceof InterruptedException ? AsyncTaskStatusChangeEvent.CANCELED :
          exception != null ? AsyncTaskStatusChangeEvent.FAILED : AsyncTaskStatusChangeEvent.COMPLETED;
        final AsyncTaskStatusChangeEvent<R> event = new AsyncTaskStatusChangeEvent<>(this, id, status, result, exception);
        this.notifyListeners(ui, event);

        // Report back any successful result
        if (exception == null)
            this.withUI(ui, () -> this.handleTaskResult(id, result));

        // Done
        return true;
    }

    /**
     * Notify listeners.
     *
     * <p>
     * This is invoked with {@linkplain #session this instance's session} locked.
     *
     * <p>
     * The implementation in {@link AsyncTaskManager} actually delivers the notifications later,
     * in the manner of {@link VaadinSession#access VaadinSession.access()}.
     *
     * @param ui {@link UI} to make current, or null for none
     * @param event status change event
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     * @throws IllegalArgumentException if {@code event} is zero
     */
    protected void notifyListeners(UI ui, AsyncTaskStatusChangeEvent<R> event) {

        // Sanity check
        Preconditions.checkArgument(event != null, "null event");
        VaadinUtil.assertCurrentSession(this.session);

        // Notify listeners (later)
        final ArrayList<AsyncTaskStatusChangeListener<R>> recipients = new ArrayList<>(this.listeners);
        VaadinUtil.accessSession(this.session,
          () -> recipients.stream().forEach(listener -> this.withUI(ui, () -> listener.onTaskStatusChange(event))));
    }

    /**
     * Process the result from a successfully completed asynchronous task.
     *
     * <p>
     * This is invoked with {@linkplain #session this instance's session} locked.
     *
     * <p>
     * The implementation in {@link AsyncTaskManager} passes the result to the configured result consumer, if any.
     *
     * @param id task ID
     * @param result task result
     * @throws IllegalArgumentException if {@code result} is null
     * @throws IllegalStateException if the current thread is not associated with {@linkplain #session this instance's session}
     */
    protected void handleTaskResult(long id, R result) {
        VaadinUtil.assertCurrentSession(this.session);
        if (this.resultConsumer != null)
            this.resultConsumer.accept(id, result);
    }

    /**
     * Invoked when an exception other than {@link InterruptedException} is thrown by the {@link AsyncTask}.
     *
     * <p>
     * Note: this method runs in the background thread and the {@link VaadinSession} will not be locked.
     *
     * <p>
     * The implementation in {@link AsyncTaskManager} just logs an error.
     *
     * @param id the unique ID of the task that failed
     * @param t the exception that was caught
     */
    protected void handleTaskException(long id, Throwable t) {
        LoggerFactory.getLogger(this.getClass()).error("exception from async task #" + id, t);
    }

    /**
     * Perform the given action with the given {@link UI} as current, if not null and still associated
     * with the current {@link VaadinSession}.
     *
     * @param ui {@link UI} to make current, or null for none
     * @param action action to perform
     */
    protected void withUI(UI ui, Runnable action) {
        VaadinUtil.assertCurrentSession(this.session);
        if (ui != null && ui.getSession() == this.session)
            ui.accessSynchronously(action::run);
        else
            action.run();
    }
}
