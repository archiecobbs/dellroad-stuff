
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapMaker;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks and limits the open {@link VaadinSession}'s in a Vaadin application and provides
 * a thread-safe way to poll them for information.
 *
 * <p>
 * The application is responsible for registering new sessions via {@link #registerCurrentSession registerCurrentSession()}.
 * This should happen as soon as possible, e.g., on a login screen. If that method returns false, then the maximum
 * number of allowed sessions has already been reached, and so the application should take appropriate action, e.g.,
 * display an error message and invalidate the new session.
 *
 * <p>
 * Sessions are automatically unregistered when they close.
 *
 * <p>
 * All of the currently registered sessions can be surveyed via {@link #surveySessions surveySessions()}.
 *
 * <p>
 * This class does not care if the registered sessions come from multiple Vaadin servlets; if there are multiple servlets,
 * all of the registered sessions will end up being tracked and counted together in one common pool.
 */
public class VaadinSessionTracker {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final Map<VaadinSession, Boolean> sessionMap = new MapMaker().weakKeys().makeMap();

    private Registration sessionDestroyRegistration;
    private int maxSessions;

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * This instance will be configured with no limit on the number of sessions.
     */
    public VaadinSessionTracker() {
        this(0);
    }

    /**
     * Constructor.
     *
     * @param maxSessions maximum number of allowed sessions, or zero for infinity
     * @throws IllegalArgumentException if {@code maxSessions} is negative
     */
    public VaadinSessionTracker(int maxSessions) {
        this.setMaxSessions(maxSessions);
    }

// Methods

    /**
     * Configure the maximum number of allowed sessions.
     *
     * <p>
     * Default is zero, which means no limit.
     *
     * @param maxSessions maximum number of allowed sessions, or zero for infinity
     * @throws IllegalArgumentException if {@code maxSessions} is negative
     */
    public synchronized void setMaxSessions(int maxSessions) {
        Preconditions.checkArgument(maxSessions >= 0, "maxSessions < 0");
        this.maxSessions = maxSessions;
    }

    /**
     * Get the number of currently registered sessions.
     *
     * @return number of registered sessions
     */
    public synchronized int getNumSessions() {
        return this.sessionMap.size();
    }

    /**
     * Register the current Vaadin session.
     *
     * <p>
     * The current thread must have an associated {@link VaadinSession} and be holding its lock.
     *
     * <p>
     * If the session is already registered, or in the {@link VaadinSessionState#CLOSING} state, nothing happens
     * and this method returns true. Otherwise the session is added if there is room, otherwise false is returned.
     *
     * @return true if the new total number of sessions does not exceed the maximum number allowed, otherwise false
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the current {@link VaadinSession} is not locked
     * @throws IllegalArgumentException the {@link VaadinSession} is in state {@link VaadinSessionState#CLOSED}
     */
    public synchronized boolean registerCurrentSession() {

        // Sanity check
        final VaadinSession session = VaadinUtil.getCurrentSession();
        switch (session.getState()) {
        case OPEN:
            break;
        case CLOSING:                           // ignore, on the way out
            return true;
        case CLOSED:
            throw new IllegalArgumentException("session is closed");
        default:
            throw new RuntimeException("internal error");
        }

        // Do we need to register our session shutdown listener? This is a one-time thing.
        if (this.sessionDestroyRegistration == null) {
            this.sessionDestroyRegistration = session.getService()
              .addSessionDestroyListener(e -> this.unregisterSession(e.getSession()));
        }

        // Already registered?
        if (this.sessionMap.containsKey(session))
            return true;

        // Add session if there's room
        final int numSessions = this.sessionMap.size();
        if (this.maxSessions != 0 && numSessions >= this.maxSessions) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("{}: can't register new session {} (already have {} ≥ {})",
                  this.getClass().getSimpleName(), session, numSessions);
            }
            return false;
        }
        this.sessionMap.put(session, true);

        // Debug
        if (this.log.isDebugEnabled()) {
            this.log.debug("{}: registered new session {} ({} total)",
              this.getClass().getSimpleName(), session, this.sessionMap.size());
        }
        return true;
    }

    /**
     * Manually unregister a session.
     *
     * <p>
     * It is not necessary to explicitly call this method; sessions are unregistered automatically on close.
     *
     * @param session
     * @return true if session was unregistered, false if session was already not registered
     * @throws IllegalArgumentException if {@code session} is null
     */
    public synchronized boolean unregisterSession(VaadinSession session) {
        Preconditions.checkArgument(session != null, "null session");
        final boolean removed = this.sessionMap.remove(session) != null;
        if (this.log.isDebugEnabled()) {
            this.log.debug("{}: unregistered session {} ({} remain)",
              this.getClass().getSimpleName(), session, this.sessionMap.size());
        }
        return removed;
    }

    /**
     * Survey all registered {@link VaadinSession}s and extract some info from each one.
     *
     * <p>
     * This method guarantees that each session is locked while {@code extractor} is extracting from it.
     *
     * <p>
     * This method must <b>not</b> be invoked with a current and locked {@link VaadinSession}.
     *
     * @param extractor returns info about a session (may be null)
     * @return mapping from {@link VaadinSession} to the corresponding extracted information
     * @throws IllegalArgumentException if {@code extractor} is null
     * @throws IllegalStateException if there is a locked {@link VaadinSession} associated with the current thread
     * @throws InterruptedException if interrupted while waiting to lock a session
     */
    public <T> Map<VaadinSession, T> surveySessions(Function<? super VaadinSession, ? extends T> extractor)
      throws InterruptedException {

        // Sanity check
        VaadinUtil.assertNoSession();
        Preconditions.checkArgument(extractor != null, "null extractor");

        // Snapshot known sessions
        final List<VaadinSession> sessionList;
        synchronized (this) {
            sessionList = new ArrayList<>(this.sessionMap.keySet());
        }

        // Extract session info from each session, locking the session while we do it
        final HashMap<VaadinSession, T> extractionMap = new HashMap<>(sessionList.size());
        for (VaadinSession session : sessionList) {
            if (Thread.interrupted())                       // we got canceled
                throw new InterruptedException();
            session.accessSynchronously(() -> {
                if (VaadinSessionState.OPEN.equals(session.getState()))
                    extractionMap.put(session, extractor.apply(session));
            });
            if (!extractionMap.containsKey(session))        // session got closed while we were surveying
                this.unregisterSession(session);            // so might as well go ahead and get rid of it
        }

        // Done
        return extractionMap;
    }
}
