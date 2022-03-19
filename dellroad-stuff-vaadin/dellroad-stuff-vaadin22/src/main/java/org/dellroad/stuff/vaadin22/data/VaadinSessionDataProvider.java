
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.data;

import com.vaadin.flow.data.provider.ListDataProvider;
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

import org.dellroad.stuff.vaadin22.servlet.SimpleSpringServlet;
import org.dellroad.stuff.vaadin22.util.VaadinUtil;

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
public class VaadinSessionDataProvider<T extends SessionInfo> extends ListDataProvider<T> {

    protected final VaadinSession session;
    protected final SimpleSpringServlet servlet;
    protected final Function<? super VaadinSession, T> infoCreator;

    private AtomicReference<Future<?>> currentReloadToken;

    /**
     * Create an instance from the current {@link VaadinSession}.
     *
     * <p>
     * The {@link infoCreator} gathers whatever information is needed by this instance from the given session,
     * and may assume that the given session is locked.
     *
     * <p>
     * If {@link infoCreator} returns null, the session is omitted.
     *
     * @param infoCreator extracts data provider information from each session
     * @throws IllegalStateException if there is no current {@link VaadinSession} associated with the current thread
     * @throws IllegalArgumentException if the associated {@link VaadinService} is not a {@link VaadinServletService}.
     * @throws IllegalArgumentException if the associated {@link VaadinServlet} is not a {@link SimpleSpringServlet}.
     * @throws IllegalArgumentException if {@code infoCreator} is null
     */
    public VaadinSessionDataProvider(Function<? super VaadinSession, T> infoCreator) {
        this(VaadinUtil.getCurrentSession(), infoCreator);
    }

    /**
     * Create an instance from the provided {@link VaadinSession}.
     *
     * <p>
     * The {@link infoCreator} gathers whatever information is needed by this instance from the given session,
     * and may assume that the given session is locked.
     *
     * <p>
     * If {@link infoCreator} returns null, the session is omitted.
     *
     * @param session Vaadin session
     * @param infoCreator extracts data provider information from each session
     * @throws IllegalArgumentException if the associated {@link VaadinService} is not a {@link VaadinServletService}.
     * @throws IllegalArgumentException if the associated {@link VaadinServlet} is not a {@link SimpleSpringServlet}.
     * @throws IllegalArgumentException if either parameter is null
     */
    public VaadinSessionDataProvider(VaadinSession session, Function<? super VaadinSession, T> infoCreator) {
        super(new ArrayList<>());
        if (session == null)
            throw new IllegalArgumentException("null session");
        if (infoCreator == null)
            throw new IllegalArgumentException("null infoCreator");
        this.session = session;
        this.servlet = SimpleSpringServlet.forSession(this.session);
        this.infoCreator = infoCreator;
    }

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
     * Reload this data provider using the given asynchronous executor.
     *
     * <p>
     * If a previous reload operation is still in progress, an {@link IllegalStateException} exception is thrown;
     * {@link #isReloading} can be checked to avoid that from happening.
     *
     * @param executor asynchronous executor
     * @throws IllegalArgumentException if {@code executor} is null
     * @throws IllegalArgumentException if {@code executor} returns a null {@link Future}
     * @throws IllegalStateException if this instance is already in the process of reloading
     * @throws IllegalStateException if this instance's {@link VaadinSession} is not locked
     */
    public void reload(Function<? super Runnable, ? extends Future<?>> executor) {

        // Sanity check
        if (executor == null)
            throw new IllegalArgumentException("null executor");
        VaadinUtil.assertCurrentSession(this.session);
        if (this.currentReloadToken != null)
            throw new IllegalArgumentException("already reloading");

        // Initiate reload
        final AtomicReference<Future<?>> reloadToken = new AtomicReference<>();
        final Future<?> future = executor.apply((Runnable)() -> this.reload(reloadToken));
        if (future == null)
            throw new IllegalArgumentException("internal error: executor returned a null Future");
        reloadToken.set(future);
        this.currentReloadToken = reloadToken;
    }

    /**
     * Cancel any outstanding reload operation.
     *
     * @return true if an outstanding reload was canceled, false if nothing happened
     * @throws IllegalStateException if this instance's {@link VaadinSession} is not locked
     */
    public boolean cancel() {
        VaadinUtil.assertCurrentSession(this.session);
        if (this.currentReloadToken != null) {
            this.currentReloadToken.get().cancel(true);
            this.currentReloadToken = null;
            return true;
        }
        return false;
    }

    /**
     * Determine if there is any outstanding asynchronous reload operation in progress.
     *
     * @return true if an outstanding reload is happening, otherwise false
     * @throws IllegalStateException if this instance's {@link VaadinSession} is not locked
     */
    public boolean isReloading() {
        VaadinUtil.assertCurrentSession(this.session);
        return this.currentReloadToken != null;
    }

    /**
     * Reload this data provider.
     *
     * <p>
     * This method must <b>not</b> be invoked while holding a {@link VaadinSession} lock. Instead, use {@link #reload(Function)}
     * to trigger an asynchronous reload.
     *
     * @param reloadToken token representing a particular reload operation
     * @throws IllegalStateException if there is any {@link VaadinSession} associated with the current thread
     */
    protected void reload(final AtomicReference<Future<?>> reloadToken) {

        // Sanity check
        if (VaadinSession.getCurrent() != null)
            throw new IllegalArgumentException("do not invoke me within the context of any VaadinSession");

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
                return;
            otherSession.accessSynchronously(() -> sessionInfoRef.set(infoCreator.apply(otherSession)));
            final T sessionInfo = sessionInfoRef.get();
            if (sessionInfo != null)
                sessionInfoList.add(sessionInfo);
        }

        // Update this instance
        VaadinUtil.accessSession(this.session, () -> {

            // Handle cancel() race condition
            if (this.currentReloadToken != reloadToken)
                return;

            // Mark this reload complete
            this.currentReloadToken = null;

            // Apply updates
            this.applyUpdates(sessionInfoList);
        });
    }

    /**
     * Reload this data provider's items after a successful reload operation.
     *
     * <p>
     * The implementation in {@link VaadinSessionDataProvider} reloads this instance's {@linkplain items #getItems}
     * and then invokes {@link #refreshAll}.
     *
     * <p>
     * This instance's session lock will be held.
     *
     * @param sessionInfoList updated sessions info
     */
    protected void applyUpdates(List<T> sessionInfoList) {
        if (sessionInfoList == null)
            throw new IllegalArgumentException("null list");
        this.getItems().clear();
        this.getItems().addAll(sessionInfoList);
        this.refreshAll();
    }
}
