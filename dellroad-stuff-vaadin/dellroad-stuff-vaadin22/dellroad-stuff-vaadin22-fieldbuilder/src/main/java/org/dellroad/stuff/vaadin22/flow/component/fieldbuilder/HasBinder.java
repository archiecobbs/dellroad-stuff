
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component.fieldbuilder;

import com.vaadin.flow.data.binder.Binder;

/**
 * Implemented by fields that have their own internal {@link Binder}s.
 *
 * @param <T> binder bean type
 */
public interface HasBinder<T> {

    /**
     * Get the {@link Binder} associated with this instance.
     */
    Binder<T> getBinder();
}
