
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.data;

import com.vaadin.flow.server.VaadinSession;

/**
 * Holds information gathered from one {@link VaadinSession}.
 *
 * <p>
 * This class (or a subclass) is the model type for the {@link VaadinSessionDataProvider}.
 */
public class SessionInfo {

    protected final VaadinSession session;

    /**
     * Constructor.
     *
     * @throws IllegalArgumentException if {@code session} is null
     */
    public SessionInfo(VaadinSession session) {
        if (session == null)
            throw new IllegalArgumentException("null session");
        this.session = session;
    }

    /**
     * Get the {@link VaadinSession} associated with this instance.
     *
     * <p>
     * Note: if you have to ask, you probably aren't holding the lock for the returned session. Therefore,
     * any access to it should be done through {@link VaadinSession#access VaadinSession.access()}, etc.
     */
    public VaadinSession getVaadinSession() {
        return this.session;
    }
}
