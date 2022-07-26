
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.data;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.dellroad.stuff.vaadin23.servlet.SimpleSpringServlet;

/**
 * A {@link DataProvider} that provides an object corresponding to each live {@link VaadinSession}.
 *
 * <p>
 * The sessions are retrieved from the {@link SimpleSpringServlet}, which must be configured with session tracking enabled.
 *
 * <p>
 * Sessions are represented by {@link SessionInfo}, which may be subclassed to add application-specific information
 * (e.g., currently logged-in user).
 *
 * <p>
 * Sessions are orderded by reverse creation time (newest first).
 *
 * @see SimpleSpringServlet
 */
@SuppressWarnings("serial")
public class VaadinSessionDataProvider<T extends SessionInfo> extends AsyncDataProvider<T> {

    protected final SimpleSpringServlet servlet;
    protected final Function<? super VaadinSession, T> infoCreator;

    /**
     * Constructor.
     *
     * <p>
     * The {@link infoCreator} gathers whatever information is needed by this instance from the given session,
     * and may assume that the given session is locked.
     *
     * <p>
     * If {@link infoCreator} returns null, that session is omitted.
     *
     * <p>
     * Caller still must configure an async executor via {@link #setAsyncExecutor setAsyncExecutor()}.
     *
     * @param infoCreator extracts data provider information from each session
     * @throws IllegalStateException if there is no current {@link VaadinSession} associated with the current thread
     * @throws IllegalArgumentException if the associated {@link VaadinService} is not a {@link VaadinServletService}
     * @throws IllegalArgumentException if the associated {@link VaadinServlet} is not a {@link SimpleSpringServlet}
     * @throws IllegalArgumentException if {@code infoCreator} is null
     */
    public VaadinSessionDataProvider(Function<? super VaadinSession, T> infoCreator) {
        this(null, infoCreator);
    }

    /**
     * Constructor.
     *
     * <p>
     * The {@link infoCreator} gathers whatever information is needed by this instance from the given session,
     * and may assume that the given session is locked.
     *
     * <p>
     * If {@link infoCreator} returns null, that session is omitted.
     *
     * @param executor the executor used to execute async load tasks
     * @param infoCreator extracts data provider information from each session
     * @throws IllegalStateException if there is no current {@link VaadinSession} associated with the current thread
     * @throws IllegalArgumentException if the associated {@link VaadinService} is not a {@link VaadinServletService}
     * @throws IllegalArgumentException if the associated {@link VaadinServlet} is not a {@link SimpleSpringServlet}
     * @throws IllegalArgumentException if {@code infoCreator} is null
     */
    public VaadinSessionDataProvider(Function<? super Runnable, ? extends Future<?>> executor,
      Function<? super VaadinSession, T> infoCreator) {
        super(executor);
        if (infoCreator == null)
            throw new IllegalArgumentException("null infoCreator");
        this.servlet = SimpleSpringServlet.forSession(this.session);
        this.infoCreator = infoCreator;
    }

    /**
     * Load or reload this data provider.
     *
     * <p>
     * If there is already an async load in progress, this method will {@link #cancel cancel()} it first.
     * You can check this ahead of time via {@link #isBusy}.
     *
     * @param executor asynchronous executor
     * @return unique ID for this load attempt
     * @throws IllegalArgumentException if {@code executor} is null
     * @throws IllegalArgumentException if {@code executor} returns a null {@link Future}
     * @throws IllegalStateException if this instance is already in the process of reloading
     * @throws IllegalStateException if this instance's {@link VaadinSession} is not locked
     */
    public void reload() {
        this.load(id -> this.buildSessionList().stream());
    }

// ListDataProvider

    /**
     * Get the {@link SessionInfo}'s currently in this container.
     */
    @Override
    public List<T> getItems() {
        return (List<T>)super.getItems();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@code VaadinSessionDataProvider} returns {@code item.getVaadinSession().getSession().getId()}.
     */
    @Override
    public String getId(T item) {
        return item.getVaadinSession().getSession().getId();
    }

    /**
     * Build the list of sessions.
     *
     * <p>
     * @throws IllegalStateException if any session lock is held
     */
    protected List<T> buildSessionList() throws InterruptedException {

        // Sanity check
        if (VaadinSession.getCurrent() != null)
            throw new IllegalStateException("there is a current VaadinSession");

        // Grab list of live sessions from servlet
        final List<VaadinSession> sessionList = this.servlet.getSessions();

        // Sort by reverse creation time
        sessionList.sort(Comparator.<VaadinSession>
           comparingLong(otherSession -> otherSession.getSession().getCreationTime()).reversed()
          .thenComparing(otherSession -> otherSession.getSession().getId()));

        // Extract session info, locking each session while we do it
        final ArrayList<T> sessionInfoList = new ArrayList<>(sessionList.size());
        final AtomicReference<T> sessionInfoRef = new AtomicReference<>();
        for (VaadinSession otherSession : sessionList) {
            if (Thread.interrupted())                       // we got canceled
                throw new InterruptedException();
            otherSession.accessSynchronously(() -> sessionInfoRef.set(infoCreator.apply(otherSession)));
            final T sessionInfo = sessionInfoRef.get();
            if (sessionInfo != null)
                sessionInfoList.add(sessionInfo);
        }

        // Done
        return sessionInfoList;
    }
}
