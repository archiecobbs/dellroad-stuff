
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

import com.google.common.base.Preconditions;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Utility class for finding the {@link VaadinSession} associated with the current HTTP request
 * when {@link VaadinSession#getCurrent} isn't an option.
 */
public final class VaadinSessionFinder {

    private VaadinSessionFinder() {
    }

    /**
     * Find the {@link VaadinSession} associated with the current HTTP request.
     *
     * <p>
     * The session is found by directly inspecting the current HTTP session, so this will work
     * even if the current thread is not executing within the Vaadin servlet.
     *
     * <p>
     * This method relies on Spring's {@link RequestContextHolder} to locate the current HTTP request.
     *
     * @return the {@link VaadinSession} associated with the current HTTP request, if any
     */
    public static Optional<VaadinSession> find() {

        // Get the current HTTP request
        final HttpServletRequest request = (HttpServletRequest)RequestContextHolder.currentRequestAttributes()
          .resolveReference(RequestAttributes.REFERENCE_REQUEST);

        // Find the VaadinSession in the HTTP session (this logic follows VaadinService.java)
        final String servletName = request.getHttpServletMapping().getServletName();
        final String attributeName = String.format("%s.%s", VaadinSession.class.getName(), servletName);
        return Optional.ofNullable(request.getSession(false))
          .map(session -> session.getAttribute(attributeName))
          .map(VaadinSession.class::cast);
    }

    /**
     * Invoke the given action in the context of the {@link VaadinSession} associated with the current HTTP request.
     *
     * @param action the action to perform
     * @return true if successfully dispatched, false if {@code session} is not in state {@link VaadinSessionState#OPEN}
     * @throws IllegalStateException if there is no current HTTP request or {@link VaadinSession} associated with it
     * @throws IllegalArgumentException if {@code action} is null
     */
    public static boolean access(Runnable action) {
        Preconditions.checkArgument(action != null, "null action");
        final VaadinSession session = VaadinSessionFinder.find()
          .orElseThrow(() -> new IllegalStateException("no VaadinSession found"));
        if (!VaadinSessionState.OPEN.equals(session.getState()))
            return false;
        session.access(action::run);
        return true;
    }
}
