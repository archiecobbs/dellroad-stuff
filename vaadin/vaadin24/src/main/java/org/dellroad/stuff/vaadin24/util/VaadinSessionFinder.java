
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

import com.google.common.base.Preconditions;
import com.vaadin.flow.server.VaadinSession;

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
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null)
            return Optional.empty();
        final HttpServletRequest request = (HttpServletRequest)requestAttributes
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
     * @return true if successfully dispatched, false if {@code session} was not found
     * @throws IllegalArgumentException if {@code action} is null
     */
    public static boolean access(Runnable action) {
        Preconditions.checkArgument(action != null, "null action");
        final Optional<VaadinSession> session = VaadinSessionFinder.find();
        if (!session.isPresent())
            return false;
        session.get().access(action::run);
        return true;
    }
}
