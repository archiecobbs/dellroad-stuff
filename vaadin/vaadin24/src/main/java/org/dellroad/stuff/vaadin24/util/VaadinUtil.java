
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;

/**
 * Miscellaneous utility methods.
 */
public final class VaadinUtil {

    private VaadinUtil() {
    }

    /**
     * Verify that there is a current and locked {@link VaadinSession} associated with the current thread.
     *
     * <p>
     * This method is equivalent to {@link #getCurrentSession} but doesn't actually return the session.
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not locked
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
     */
    public static void assertCurrentSession(VaadinSession session) {
        Preconditions.checkArgument(session != null, "null session");
        final VaadinSession currentSession = VaadinUtil.getCurrentSession();
        if (currentSession != session) {
            throw new IllegalStateException("the VaadinSession associated with the current thread " + currentSession
              + " is not the same session as the given one " + session);
        }
    }

    /**
     * Get the {@link VaadinSession} associated with the current thread and require that it be found and locked.
     *
     * <p>
     * This is a wrapper around {@link VaadinSession#getCurrent} that (a) throws an exception instead of returning null
     * when there is no session associated with the current thread and (b) verifies the session is locked.
     *
     * @return current {@link VaadinSession}, never null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not locked
     */
    public static VaadinSession getCurrentSession() {
        final VaadinSession session = VaadinSession.getCurrent();
        Preconditions.checkState(session != null, "there is no VaadinSession associated with the current thread");
        Preconditions.checkState(session.hasLock(), "the VaadinSession associated with the current thread is not locked");
        return session;
    }

    /**
     * Invoke {@link #accessSession accessSession()} using the {@link VaadinSession} associated with the current thread.
     *
     * @param action action to perform
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not locked
     */
    public static void accessCurrentSession(Runnable action) {
        VaadinUtil.accessSession(VaadinUtil.getCurrentSession(), action);
    }

    /**
     * Invoke {@link VaadinSession#access VaadinSession.access()}.
     *
     * @param session the session to access
     * @param action action to perform
     * @throws IllegalArgumentException if {@code session} or {@code action} is null
     */
    public static void accessSession(VaadinSession session, Runnable action) {
        Preconditions.checkArgument(session != null, "null session");
        Preconditions.checkArgument(action != null, "null action");
        session.access(action::run);
    }

    /**
     * Invoke {@link VaadinSession#access VaadinSession.accessSynchronously()}.
     *
     * @param session the session to access, or null
     * @param action action to perform
     * @throws IllegalArgumentException if {@code session} is null
     * @throws IllegalArgumentException if {@code action} is null
     */
    public static void accessSessionSynchronously(VaadinSession session, Runnable action) {
        Preconditions.checkArgument(session != null, "null session");
        Preconditions.checkArgument(action != null, "null action");
        session.accessSynchronously(action::run);
    }

    /**
     * Verify that there is no current <b>and locked</b> {@link VaadinSession} associated with the current thread.
     *
     * @throws IllegalStateException if there is a current and locked Vaadin session associated with the current thread
     */
    public static void assertNoSession() {
        VaadinUtil.assertNotSession(null);
    }

    /**
     * Verify that a {@link VaadinSession} is not associated with the current thread <b>and locked</b>.
     *
     * <p>
     * To verify this only for one specific {@link VaadinSession}, pass that {@link VaadinSession} as the parameter.
     *
     * <p>
     * To verify this for any {@link VaadinSession} whatsoever, pass null as the parameter.
     *
     * @param session some {@link VaadinSession}, or null for any {@link VaadinSession}
     * @throws IllegalStateException if a/the {@link VaadinSession} associated with the current thread and locked by it
     */
    public static void assertNotSession(VaadinSession session) {
        final VaadinSession currentSession = VaadinSession.getCurrent();
        Preconditions.checkState(currentSession == null
            || !currentSession.hasLock()
            || (session != null && currentSession != session),
          "VaadinSession is locked");
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
        Preconditions.checkState(request != null, "there is no VaadinRequest associated with the current thread");
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
        Preconditions.checkState(ui != null, "there is no UI associated with the current thread");
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
    public static boolean accessUI(Runnable action) {
        final UI ui = UI.getCurrent();
        Preconditions.checkState(ui != null, "there is no UI associated with the current thread");
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
    public static boolean accessUI(UI ui, Runnable action) {
        Preconditions.checkArgument(action != null, "null action");
        if (ui == null)
            return false;
        try {
            ui.access(action::run);
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
    public static boolean accessUISynchronously(UI ui, Runnable action) {
        Preconditions.checkArgument(action != null, "null action");
        if (ui == null)
            return false;
        try {
            ui.accessSynchronously(action::run);
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
     * @throws IllegalStateException if there is no {@link Page} associated with the {@link UI} associated with the current thread
     */
    public static Page getCurrentPage() {
        final Page page = VaadinUtil.getCurrentUI().getPage();
        Preconditions.checkState(page != null, "there is no Page associated with the current UI");
        return page;
    }
}
