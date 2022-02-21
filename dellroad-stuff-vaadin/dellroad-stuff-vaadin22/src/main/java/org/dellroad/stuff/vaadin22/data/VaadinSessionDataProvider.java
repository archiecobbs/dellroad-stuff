
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.data;

import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dellroad.stuff.vaadin22.servlet.SimpleSpringServlet;

/**
 * A {@link DataProvider} that contains all live {@link VaadinSession}s.
 *
 * <p>
 * The sessions are retrieved from the {@link SimpleSpringServlet}, which must be configured
 * with session tracking enabled.
 *
 * @see SimpleSpringServlet
 */
@SuppressWarnings("serial")
public class VaadinSessionDataProvider extends CallbackDataProvider<VaadinSession, Predicate<? super VaadinSession>> {

    /**
     * Create an instance from the current {@link VaadinSession}.
     *
     * @throws IllegalStateException if there is no current {@link VaadinSession} associated with the current thread
     * @throws IllegalArgumentException if the associated {@link VaadinService} is not a {@link VaadinServletService}.
     * @throws IllegalArgumentException if the associated {@link VaadinServlet} is not a {@link SimpleSpringServlet}.
     */
    public VaadinSessionDataProvider() {
        this(SimpleSpringServlet.forCurrentSession());
    }

    /**
     * Create an instance from the provided {@link VaadinSession}.
     *
     * @param session Vaadin session
     * @throws IllegalArgumentException if the associated {@link VaadinService} is not a {@link VaadinServletService}.
     * @throws IllegalArgumentException if the associated {@link VaadinServlet} is not a {@link SimpleSpringServlet}.
     * @throws IllegalArgumentException if {@code session} is null
     */
    public VaadinSessionDataProvider(VaadinSession session) {
        this(SimpleSpringServlet.forSession(session));
    }

    /**
     * Create an instance from the provided {@link SimpleSpringServlet}.
     *
     * @param servlet servlet to extract sessions from
     * @throws IllegalArgumentException if {@code servlet} is null
     */
    public VaadinSessionDataProvider(final SimpleSpringServlet servlet) {
        super(new Fetcher(servlet), new Counter(servlet));
    }

// Fetcher

    @SuppressWarnings("serial")
    private static class Fetcher implements CallbackDataProvider.FetchCallback<VaadinSession, Predicate<? super VaadinSession>> {

        private final SimpleSpringServlet servlet;

        Fetcher(SimpleSpringServlet servlet) {
            if (servlet == null)
                throw new IllegalArgumentException("null servlet");
            this.servlet = servlet;
        }

        @Override
        public Stream<VaadinSession> fetch(Query<VaadinSession, Predicate<? super VaadinSession>> query) {

            // Sanity check
            if (query == null)
                throw new IllegalArgumentException("null query");

            // Get sessions
            List<VaadinSession> sessionList = this.servlet.getSessions();

            // Apply filtering
            if (query.getFilter().isPresent()) {
                sessionList = sessionList.stream()
                  .filter(query.getFilter().get())
                  .collect(Collectors.toList());
            }

            // Apply sorting
            if (query.getInMemorySorting() != null)
                sessionList.sort(query.getInMemorySorting());

            // Apply offset & limit
            final int minIndex = Math.max(0, Math.min(sessionList.size(), query.getOffset()));
            final int maxIndex = Math.max(0, Math.min(sessionList.size(), query.getRequestedRangeEnd()));

            // Done
            return sessionList.subList(minIndex, maxIndex).stream();
        }
    }

// Counter

    @SuppressWarnings("serial")
    private static class Counter implements CallbackDataProvider.CountCallback<VaadinSession, Predicate<? super VaadinSession>> {

        private final SimpleSpringServlet servlet;

        Counter(SimpleSpringServlet servlet) {
            if (servlet == null)
                throw new IllegalArgumentException("null servlet");
            this.servlet = servlet;
        }

        @Override
        public int count(Query<VaadinSession, Predicate<? super VaadinSession>> query) {

            // Sanity check
            if (query == null)
                throw new IllegalArgumentException("null query");

            // Get sessions
            Stream<VaadinSession> sessionStream = this.servlet.getSessions().stream();

            // Apply filtering, if any
            if (query.getFilter().isPresent())
                sessionStream = sessionStream.filter(query.getFilter().get());

            // Done
            return (int)sessionStream.count();
        }
    }
}
