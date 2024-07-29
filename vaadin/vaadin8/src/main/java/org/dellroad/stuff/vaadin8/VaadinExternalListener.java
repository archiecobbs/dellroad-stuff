
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.server.VaadinSession;
import com.vaadin.shared.Registration;

import org.slf4j.LoggerFactory;

/**
 * Support superclass customized for use by listeners that are part of a Vaadin application when listening
 * to non-Vaadin ("external") event sources.
 *
 * <p>
 * Listeners that are part of a Vaadin application should use this superclass if they are going to be registered
 * with non-Vaadin event sources, and use the methods {@link #register()} and {@link #unregister()} to control listener
 * registration. Subclasses implement {@link #register(Object) register(S)} and {@link #unregister(Object) register(S)}
 * to perform the actual listener registration/unregister operations.
 *
 * <p>
 * Use of this class will ensure two things:
 * <ul>
 *  <li>Events are delivered {@linkplain VaadinSession#access in the proper Vaadin application context}; and</li>
 *  <li>The listener is automatically unregistered from the external event source when the Vaadin application is closed,
 *      avoiding a memory leak</li>
 * </ul>
 *
 * <p>
 * <b>Important:</b> subclass listener methods must use {@link #handleEvent handleEvent()} when handling events.
 * This will ensure proper locking to avoid race conditions.
 *
 * <p>
 * Note: when listening to event sources that are scoped to specific Vaadin application instances and already originate events
 * within the proper Vaadin application context (i.e., event sources that are not external to the Vaadin application),
 * then the use of this superclass is not necessary (however, it also doesn't hurt to use it anyway).
 *
 * @param <S> The type of the event source
 */
public abstract class VaadinExternalListener<S> {

    private final S eventSource;
    private final VaadinSession session;

    private Registration sessionDestroyRegistration;

    private volatile boolean asynchronous;

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
     * Determine whether this instance is configured for asynchronous notification. Default false.
     *
     * @return true if this instance will notify asynchronously
     */
    public boolean isAsynchronous() {
        return this.asynchronous;
    }

    /**
     * Set whether to notify asynchronously. If set, {@link VaadinSession#access VaadinSession.access()} will
     * be used for notifications, so that these occur on a different thread from the original notifying thread.
     *
     * @param asynchronous true to notify asynchronously, false for synchronous notifications
     * @see #handleEvent(Runnable) handleEvent()
     */
    public void setAsynchronous(boolean asynchronous) {
        this.asynchronous = asynchronous;
    }

    /**
     * Register as a listener on configured event source.
     *
     * <p>
     * This also listens for shutdown of the {@linkplain #getSession configured Vaadin application},
     * so that when the application closes we can unregister this instance from the event source to avoid a memory leak.
     *
     * @throws IllegalStateException if this instance is already registered
     */
    public void register() {
        if (this.sessionDestroyRegistration != null)
            throw new IllegalStateException("already registered");
        this.sessionDestroyRegistration = VaadinUtil.addSessionDestroyListener(this.session, e -> this.unregister());
        this.register(this.eventSource);
    }

    /**
     * Un-register as a listener on configured event source.
     *
     * <p>
     * This also removes the listener registered by {@link #register}.
     */
    public void unregister() {
        if (this.sessionDestroyRegistration != null) {
            this.sessionDestroyRegistration.remove();
            this.sessionDestroyRegistration = null;
        }
        this.unregister(this.eventSource);
    }

    /**
     * Get the {@link VaadinSession} (aka Vaadin application) with which this instance is associated.
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
     * Execute the given listener action using the {@link VaadinSession} with which this instance is associated.
     *
     * <p>
     * Subclass listener methods should handle events by invoking this method to ensure proper locking to avoid race conditions.
     *
     * <p>
     * This method delegates to {@link VaadinSession#accessSynchronously VaadinSession.accessSynchronously()}, or
     * {@link VaadinSession#access VaadinSession.access()} if this instance is configured to be
     * {@linkplain #setAsynchronous asynchronous}.
     *
     * @param action action to perform
     */
    protected void handleEvent(final Runnable action) {
        if (this.asynchronous) {
            this.getSession().access(() -> {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    LoggerFactory.getLogger(this.getClass()).error("exception in asynchrnous listener", e);
                    throw e;
                }
            });
        } else
            this.getSession().accessSynchronously(action);
    }

    /**
     * Register as a listener on the given event source.
     *
     * <p>
     * Subclass must implement this to perform the actual listener registration.
     *
     * @param eventSource event source, never null; will be same as provided to the constructor
     */
    protected abstract void register(S eventSource);

    /**
     * Register as a listener from the given event source.
     *
     * <p>
     * Subclass must implement this to perform the actual listener registration.
     *
     * @param eventSource event source, never null; will be same as provided to the constructor
     */
    protected abstract void unregister(S eventSource);
}

