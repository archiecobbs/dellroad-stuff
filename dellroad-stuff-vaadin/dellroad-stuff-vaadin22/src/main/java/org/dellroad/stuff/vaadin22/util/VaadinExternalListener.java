
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.util;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;
import com.vaadin.flow.shared.Registration;

import org.slf4j.LoggerFactory;

/**
 * Support superclass for listeners in a Vaadin session who need to listen to non-Vaadin ("external") event sources.
 *
 * <p>
 * Listeners that are part of a Vaadin application should use this superclass if they are going to be registered
 * with non-Vaadin event sources, where "non-Vaadin" means not operating in the context of a {@link VaadinSession}
 * and not holding corresponding lock.
 *
 * <p>
 * Use the methods {@link #register()} and {@link #unregister()} from within a {@link VaadinSession} to control
 * listener registration.
 *
 * <p>
 * Subclasses must then implement {@link #registerExternal} and {@link #unregisterExternal} to perform the actual external
 * registration/unregister operations, and then when notified by the external source, must use {@link #handleEvent handleEvent()}
 * to relay the notification back to the caller with the {@link VaadinSession} safely locked.
 *
 * <p>
 * Use of this class will prevent these bugs from happening:
 * <ul>
 *  <li>Notifications being delivered without the {@link VaadinSession} being locked;</li>
 *  <li>Race conditions between unregistering a listener and receiving a listener notification; and</li>
 *  <li>Memory leaks that occur if the Vaadin application is closed while listeners are still registered</li>
 * </ul>
 *
 * <p>
 * Note: when listening to event sources that are scoped to specific Vaadin application instances and that already originate events
 * within the proper Vaadin application context (i.e., non-external event sources), then the use of this class is not
 * necessary, but it also won't hurt to use it.
 *
 * @param <S> The type of the event source
 */
public abstract class VaadinExternalListener<S> {

    private final S eventSource;
    private final VaadinSession session;

    private Registration sessionDestroyRegistration;

    /**
     * Convenience constructor. Equivalent to:
     * <blockquote>
     *  {@link #VaadinExternalListener(Object, VaadinSession)
     *      VaadinExternalListener(eventSource, VaadinUtil.getCurrentSession())}
     * </blockquote>
     *
     * @param eventSource the event source on which this listener will register
     * @throws IllegalArgumentException if {@code eventSource} is null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     */
    protected VaadinExternalListener(S eventSource) {
        this(eventSource, VaadinUtil.getCurrentSession());
    }

    /**
     * Primary constructor.
     *
     * @param eventSource the event source on which this listener will register when {@link #register} is invoked
     * @param session the associated Vaadin application's session
     * @throws IllegalArgumentException if either parameter is null
     */
    protected VaadinExternalListener(S eventSource, VaadinSession session) {
        if (eventSource == null)
            throw new IllegalArgumentException("null eventSource");
        if (session == null)
            throw new IllegalArgumentException("null session");
        this.eventSource = eventSource;
        this.session = session;
    }

    /**
     * Register as a listener on configured event source.
     *
     * <p>
     * This method may be invoked from any context.
     *
     * @throws IllegalStateException if this instance is already registered
     */
    public synchronized void register() {
        if (this.sessionDestroyRegistration != null)
            throw new IllegalStateException("already registered");
        this.registerExternal(this.eventSource);
        this.sessionDestroyRegistration = this.session.getService().addSessionDestroyListener(e -> {
            if (this.session.equals(e.getSession()))
                this.unregister();
        });
    }

    /**
     * Un-register as a listener on configured event source.
     *
     * <p>
     * This method may be invoked from any context.
     */
    public synchronized void unregister() {
        if (this.sessionDestroyRegistration != null) {
            this.sessionDestroyRegistration.remove();
            this.sessionDestroyRegistration = null;
        }
        this.unregisterExternal(this.eventSource);
    }

    /**
     * Get the {@link VaadinSession} with which this instance is associated.
     *
     * @return associated session
     */
    public final VaadinSession getSession() {
        return this.session;
    }

    /**
     * Get the event source with which this instance is (or was) registered as a listener.
     *
     * @return associated event source
     */
    public final S getEventSource() {
        return this.eventSource;
    }

    /**
     * Execute the given listener action while the {@link VaadinSession} with which this instance is associated is locked.
     *
     * <p>
     * Subclass listener methods should handle events by invoking this method to ensure proper locking to avoid race conditions.
     *
     * <p>
     * This method delegates to {@link VaadinUtil#accessSession VaadinUtil.accessSession()} to actually handle the event.
     *
     * @param action action to perform
     * @return true if successfully invoked, false if the session is no longer in state {@link VaadinSessionState#OPEN}
     * @throws IllegalArgumentException if {@code action} is null
     */
    protected boolean handleEvent(final Runnable action) {
        if (action == null)
            throw new IllegalArgumentException("null action");
        return VaadinUtil.accessSession(this.session, () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                this.handleException(e);
            }
        });
    }

    /**
     * Handle a {@link RuntimeException} thrown by the {@link #handleEvent handleEvent} callback.
     *
     * <p>
     * The implementation in {@link VaadinExternalListener} logs an error and then re-throws the exception.
     *
     * @param e exception thrown
     */
    protected void handleException(RuntimeException e) {
        LoggerFactory.getLogger(this.getClass()).error("exception in asynchronous listener", e);
        throw e;
    }

    /**
     * Register as a listener on the given external event source.
     *
     * <p>
     * Subclass must implement this to perform the actual listener registration.
     *
     * @param eventSource event source, never null; will be same as provided to the constructor
     */
    protected abstract void registerExternal(S eventSource);

    /**
     * Register as a listener from the given external event source.
     *
     * <p>
     * Subclass must implement this to perform the actual listener registration.
     *
     * @param eventSource event source, never null; will be same as provided to the constructor
     */
    protected abstract void unregisterExternal(S eventSource);
}
