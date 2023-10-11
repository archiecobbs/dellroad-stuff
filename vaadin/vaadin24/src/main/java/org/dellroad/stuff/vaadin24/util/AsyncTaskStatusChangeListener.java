
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

/**
 * Listener interface for {@link AsyncTaskManager} event notifications.
 *
 * @param <R> task result type
 */
@FunctionalInterface
public interface AsyncTaskStatusChangeListener<R> {

    /**
     * Handle notification that an {@link AsyncTask} has changed status.
     *
     * @param event task status change event
     */
    void onTaskStatusChange(AsyncTaskStatusChangeEvent<R> event);
}
