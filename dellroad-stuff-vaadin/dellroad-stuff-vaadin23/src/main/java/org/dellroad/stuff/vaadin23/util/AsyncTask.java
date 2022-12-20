
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.util;

import com.vaadin.flow.server.VaadinSession;

/**
 * Performs some task executing asynchronously and managed by an {@link AsyncTaskManager}.
 *
 * @param <R> task result type
 */
@FunctionalInterface
public interface AsyncTask<R> {

    /**
     * Perform the task.
     *
     * <p>
     * This method runs in a background thread; in particular, no {@link VaadinSession} will be locked.
     *
     * <p>
     * This method should be prepared to handle an {@linkplain Thread#interrupt interrupt} if/when
     * {@link AsyncTaskManager#cancelTask cancelTask()} is invoked; in that case this method may throw
     * {@link InterruptedException}.
     *
     * <p>
     * This task may also cancel itself by throwing an unprompted {@link InterruptedException}.
     *
     * @param id the unique ID for this task run
     * @return task result
     * @throws InterruptedException if the current thread is interrupted
     * @throws RuntimeException if an error occurs during task execution
     */
    R perform(long id) throws InterruptedException;
}
