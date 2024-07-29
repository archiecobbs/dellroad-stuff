
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.server.SessionDestroyEvent;
import com.vaadin.server.SessionDestroyListener;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.Registration;

/**
 * Miscellaneous utility methods.
 */
public final class VaadinUtil {

    private VaadinUtil() {
    }

    /**
     * Verify that we are running in the context of the given session and holding the session's lock.
     * This method can be used by any code that manipulates Vaadin state to assert that the proper Vaadin
     * locking has been performed.
     *
     * @param session session we are supposed to be running with
     * @throws IllegalArgumentException if {@code session} is null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not {@code session}
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is not locked
     * @throws IllegalStateException if the {@link VaadinSession} associated with the current thread is locked by another thread
     */
    public static void assertSession(VaadinSession session) {
        if (session == null)
            throw new IllegalArgumentException("null session");
        final VaadinSession currentSession = VaadinSession.getCurrent();
        if (currentSession == null)
            throw new IllegalStateException("there is no VaadinSession associated with the current thread");
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
     * Get the {@link VaadinSession} associated with the current thread.
     * This is just a wrapper around {@link VaadinSession#getCurrent} that throws an exception instead
     * of returning null when there is no session associated with the current thread.
     *
     * @return current {@link VaadinSession}, never null
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    public static VaadinSession getCurrentSession() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            throw new IllegalStateException("there is no VaadinSession associated with the current thread;"
              + " are we executing within a Vaadin HTTP request or VaadinUtil.invoke()?");
        }
        return session;
    }

    /**
     * Get the {@link VaadinRequest} associated with the current thread.
     * This is just a wrapper around {@link VaadinService#getCurrentRequest} that throws an exception instead
     * of returning null when there is no request associated with the current thread.
     *
     * @return current {@link VaadinRequest}, never null
     *
     * @throws IllegalStateException if there is no {@link VaadinRequest} associated with the current thread
     */
    public static VaadinRequest getCurrentRequest() {
        VaadinRequest request = VaadinService.getCurrentRequest();
        if (request == null) {
            throw new IllegalStateException("there is no VaadinRequest associated with the current thread;"
              + " are we executing within a Vaadin HTTP request?");
        }
        return request;
    }

    /**
     * Register for a notification when the {@link VaadinSession} is closed, without creating a memory leak.
     * This method is intended to be used by listeners that are themselves part of a Vaadin application.
     *
     * <p>
     * Explanation: the {@link VaadinSession} class does not provide a listener API directly; instead, you must
     * use the {@link com.vaadin.server.VaadinService} class. However, registering as a listener on the
     * {@link com.vaadin.server.VaadinService} when you are part of a Vaadin application sets you up for a memory leak
     * if you forget to unregister yourself when the notification arrives, because the {@link com.vaadin.server.VaadinService}
     * lifetime is longer than the {@link VaadinSession} lifetime. This method handles that de-registration for
     * you automatically.
     *
     * @param session Vaadin session
     * @param listener listener for notifications
     * @return listener registration
     * @throws IllegalArgumentException if either parameter is null
     */
    public static Registration addSessionDestroyListener(VaadinSession session, SessionDestroyListener listener) {
        final LeakAvoidingDestroyListener wrappedListener = new LeakAvoidingDestroyListener(session, listener);
        final Registration registration = session.getService().addSessionDestroyListener(wrappedListener);
        wrappedListener.setRegistration(registration);
        return () -> registration.remove();
    }

// LeakAvoidingDestroyListener

    @SuppressWarnings("serial")
    private static class LeakAvoidingDestroyListener implements SessionDestroyListener {

        private final VaadinSession session;
        private final SessionDestroyListener listener;

        private Registration registration;

        LeakAvoidingDestroyListener(VaadinSession session, SessionDestroyListener listener) {
            if (session == null)
                throw new IllegalArgumentException("null session");
            if (listener == null)
                throw new IllegalArgumentException("null listener");
            this.session = session;
            this.listener = listener;
        }

        void setRegistration(Registration registration) {
            this.registration = registration;
        }

        @Override
        public void sessionDestroy(SessionDestroyEvent event) {
            final VaadinSession closedSession = event.getSession();
            if (closedSession == this.session) {
                this.registration.remove();                                         // remove myself as listener to avoid mem leak
                this.listener.sessionDestroy(event);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final LeakAvoidingDestroyListener that = (LeakAvoidingDestroyListener)obj;
            return this.session == that.session && this.listener.equals(that.listener);
        }

        @Override
        public int hashCode() {
            return this.session.hashCode() ^ this.listener.hashCode();
        }
    }
}

