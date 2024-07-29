
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.dao;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

/**
 * Helper class for building and executing JPA typed queries.
 *
 * @param <T> query result type, i.e., what the {@link TypedQuery} returns zero or more of
 * @param <R> query Java return type, usually either {@code R} or {@code List<R>}
 */
public abstract class TypedQueryCallback<T, R> extends QueryCallback<R> {

    /**
     * Execute the query.
     *
     * <p>
     * The implementation in {@link TypedQueryCallback} delegates to {@link #executeQuery(TypedQuery)}.
     */
    @SuppressWarnings("unchecked")
    protected final R executeQuery(Query query) {
        return this.executeQuery((TypedQuery<T>)query);
    }

    /**
     * Build the query as a {@link TypedQuery}.
     */
    @Override
    protected abstract TypedQuery<T> buildQuery(EntityManager entityManager);

    /**
     * Execute the query. Typically this is implemented in one of these two ways:
     * <blockquote><code>
     *   return query.getSingleResult();
     *   return query.getResultList();
     * </code></blockquote>
     *
     * @param query query to execute
     * @return result of query
     */
    protected abstract R executeQuery(TypedQuery<T> query);
}

