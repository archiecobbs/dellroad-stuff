
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin7;

import com.vaadin.server.SessionDestroyEvent;
import com.vaadin.server.SessionDestroyListener;
import com.vaadin.server.VaadinSession;

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
 *  <li>Events are delivered {@linkplain VaadinUtil#invoke in the proper Vaadin application context}; and</li>
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
 * @see VaadinUtil#invoke
 * @see VaadinApplicationScope
 * @see VaadinApplicationListener
 * @see SpringVaadinSessionListener
 */
public abstract class VaadinExternalListener<S> {

    private final S eventSource;
    private final VaadinSession session;
    private final CloseListener closeListener = new CloseListener();

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
     * Convenience constructor. Equivalent to:
     * <blockquote>
     *  {@link #VaadinExternalListener(Object, VaadinSession) VaadinExternalListener(eventSource, application.getSession())}
     * </blockquote>
     *
     * @param eventSource the event source on which this listener will register
     * @param application the {@link VaadinApplication} that this listener is part of
     * @throws IllegalArgumentException if {@code eventSource} is null
     * @throws NullPointerException if {@code application} is null
     */
    protected VaadinExternalListener(S eventSource, VaadinApplication application) {
        this(eventSource, application.getSession());
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
     * Set whether to notify asynchronously. If set, {@link VaadinUtil#invokeLater VaadinUtil.invokeLater()} will
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
     * This also registers a {@link SessionDestroyListener} on the {@linkplain #getSession configured Vaadin application},
     * so that when the application closes we can unregister this instance from the event source to avoid a memory leak.
     */
    public void register() {
        VaadinUtil.addSessionDestroyListener(this.session, this.closeListener);
        this.register(this.eventSource);
    }

    /**
     * Un-register as a listener on configured event source.
     *
     * <p>
     * This also unregisters the {@link SessionDestroyListener} registered by {@link #register}.
     */
    public void unregister() {
        VaadinUtil.removeSessionDestroyListener(this.session, this.closeListener);
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
     * This method delegates to {@link VaadinUtil#invoke VaadinUtil.invoke()}, or
     * {@link VaadinUtil#invokeLater VaadinUtil.invokeLater()} if this instance is configured to be
     * {@linkplain #setAsynchronous asynchronous}.
     *
     * @param action action to perform
     * @see VaadinUtil#invoke
     */
    protected void handleEvent(final Runnable action) {
        if (this.asynchronous) {
            VaadinUtil.invokeLater(this.getSession(), new Runnable() {
                @Override
                public void run() {
                    try {
                        action.run();
                    } catch (RuntimeException e) {
                        LoggerFactory.getLogger(VaadinExternalListener.this.getClass()).error(
                          "exception in asynchrnous listener", e);
                        throw e;
                    }
                }
            });
        } else
            VaadinUtil.invoke(this.getSession(), action);
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

// Application close listener

    @SuppressWarnings("serial")
    private final class CloseListener implements SessionDestroyListener {

        @Override
        public void sessionDestroy(SessionDestroyEvent event) {
            VaadinExternalListener.this.unregister();
        }
    }
}

