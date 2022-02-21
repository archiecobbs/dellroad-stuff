
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.util;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;

/**
 * Miscellaneous utility methods.
 */
public final class VaadinUtil {

    private VaadinUtil() {
    }

    /**
     * Verify that there is a {@link VaadinSession} associated with the current thread.
     *
     * <p>
     * This method is equivalent to {@link #getCurrentSession} but doesn't actually return the session.
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public static void assertCurrentSession() {
        VaadinUtil.getCurrentSession();
    }

    /**
     * Verify that we are running in the context of the given session and holding the session's lock.
     *
     * <p>
     * This method can be used by any code that manipulates Vaadin state to assert that the proper Vaadin
     * locking has been performed on the expected session.
     *
     * @param session session we are supposed to be running with
     * @throws IllegalArgumentException if {@code session} is null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not {@code session}
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not locked
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is locked by another thread
     */
    public static void assertCurrentSession(VaadinSession session) {
        if (session == null)
            throw new IllegalArgumentException("null session");
        final VaadinSession currentSession = VaadinUtil.getCurrentSession();
        if (currentSession != session) {
            throw new IllegalStateException("the VaadinSession associated with the current thread " + currentSession
              + " is not the same session as the given one " + session);
        }
        if (!session.hasLock()) {
            throw new IllegalStateException("the VaadinSession associated with the current thread " + currentSession
              + " is not locked by this thread");
        }
    }

    /**
     * Get the {@link VaadinSession} associated with the current thread and require that it be found.
     *
     * <p>
     * This is just a wrapper around {@link VaadinSession#getCurrent} that throws an exception instead
     * of returning null when there is no session associated with the current thread.
     *
     * @return current {@link VaadinSession}, never null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public static VaadinSession getCurrentSession() {
        final VaadinSession session = VaadinSession.getCurrent();
        if (session == null)
            throw new IllegalStateException("there is no VaadinSession associated with the current thread");
        return session;
    }

    /**
     * Invoke {@link #accessSession accessSession()} using the {@link VaadinSession} associated with the current thread,
     * unless the session is no longer open.
     *
     * @param action action to perform
     * @return true if successfully invoked, false if {@code session} is not in state {@link VaadinSessionState#OPEN}
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public static boolean accessSession(Command action) {
        return VaadinUtil.accessSession(VaadinUtil.getCurrentSession(), action);
    }

    /**
     * Invoke {@link VaadinSession#access VaadinSession.access()}, unless the session is no longer open.
     *
     * <p>
     * Does nothing (and returns false) if {@code session} is null or not in state {@link VaadinSessionState#OPEN}.
     *
     * @param session the session to access
     * @param action action to perform
     * @return true if successfully invoked, false if {@code session} is not in state {@link VaadinSessionState#OPEN}
     * @throws IllegalArgumentException if {@code session} or {@code action} is null
     */
    public static boolean accessSession(VaadinSession session, Command action) {
        if (session == null)
            throw new IllegalArgumentException("null session");
        if (action == null)
            throw new IllegalArgumentException("null action");
        if (!VaadinSessionState.OPEN.equals(session.getState()))
            return false;
        session.access(action);
        return true;
    }

    /**
     * Get the {@link VaadinRequest} associated with the current thread and require that it be found.
     *
     * <p>
     * This is just a wrapper around {@link VaadinRequest#getCurrent} that throws an exception instead
     * of returning null when there is no request associated with the current thread.
     *
     * @return current {@link VaadinRequest}, never null
     *
     * @throws IllegalStateException if there is no {@link VaadinRequest} associated with the current thread
     */
    public static VaadinRequest getCurrentRequest() {
        final VaadinRequest request = VaadinRequest.getCurrent();
        if (request == null)
            throw new IllegalStateException("there is no VaadinRequest associated with the current thread");
        return request;
    }

    /**
     * Get the {@link UI} associated with the current thread and require that it be found.
     *
     * <p>
     * This is just a wrapper around {@link UI#getCurrent} that throws an exception instead
     * of returning null when there is no request associated with the current thread.
     *
     * @return current {@link UI}, never null
     *
     * @throws IllegalStateException if there is no {@link UI} associated with the current thread
     */
    public static UI getCurrentUI() {
        final UI ui = UI.getCurrent();
        if (ui == null)
            throw new IllegalStateException("there is no UI associated with the current thread");
        return ui;
    }

    /**
     * Invoke {@link #accessUI accessUI()} using the {@link UI} associated with the current thread,
     * but catch and discard any thrown {@link UIDetachedException}'s.
     *
     * <p>
     * Does nothing (and returns false) if the current {@link UI} is not attached.
     *
     * @param action action to perform
     * @return true if successfully invoked, false if the current {@link UI} is detached
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if there is no {@link UI} associated with the current thread
     */
    public static boolean accessUI(Command action) {
        final UI ui = UI.getCurrent();
        if (ui == null)
            throw new IllegalStateException("there is no UI associated with the current thread");
        return VaadinUtil.accessUI(ui, action);
    }

    /**
     * Invoke {@link UI#access UI.access()} but catch and discard any thrown {@link UIDetachedException}'s.
     *
     * <p>
     * Does nothing (and returns false) if {@code ui} is null or not attached.
     *
     * @param ui the {@link UI} to access, or null
     * @param action action to perform
     * @return true if successfully invoked, false if {@code ui} is null or detached
     * @throws IllegalArgumentException if {@code action} is null
     */
    public static boolean accessUI(UI ui, Command action) {
        if (action == null)
            throw new IllegalArgumentException("null action");
        if (ui == null)
            return false;
        try {
            ui.access(action);
        } catch (UIDetachedException e) {
            return false;
        }
        return true;
    }

    /**
     * Invoke {@link UI#accessSynchronously UI.accessSynchronously()} but catch and discard any thrown
     * {@link UIDetachedException}'s.
     *
     * <p>
     * Does nothing (and returns false) if {@code ui} is null or not attached to a {@link VaadinSession}.
     *
     * @param ui the {@code UI} to access, or null
     * @param action action to perform
     * @return true if successfully invoked, otherwise false
     * @throws IllegalArgumentException if {@code action} is null
     */
    public static boolean accessUISynchronously(UI ui, Command action) {
        if (action == null)
            throw new IllegalArgumentException("null action");
        if (ui == null)
            return false;
        try {
            ui.accessSynchronously(action);
        } catch (UIDetachedException e) {
            return false;
        }
        return true;
    }

    /**
     * Get the {@link Page} associated with the current {@link UI}.
     *
     * @return current {@link Page}, never null
     * @throws IllegalStateException if there is no {@link UI} associated with the current thread
     */
    public static Page getCurrentPage() {
        final Page page = VaadinUtil.getCurrentUI().getPage();
        if (page == null)
            throw new IllegalStateException("there is no Page associated with the current UI");
        return page;
    }
}
