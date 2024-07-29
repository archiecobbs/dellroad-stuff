
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.dao;

import java.util.List;

import javax.persistence.FlushModeType;

/**
 * Data Access Object (DAO) generic interface.
 */
public interface DAO<T> {

// Meta-data methods

    /**
     * Get the type of persistent object handled by this instance.
     *
     * @return associated object type
     */
    Class<T> getType();

// Access methods

    /**
     * Get an instance by ID. This assumes object IDs are long values.
     *
     * @param id unique ID for instance
     * @return instance with the given {@code id}, or null if not found
     */
    T getById(long id);

    /**
     * Get all instances.
     *
     * @return list of all instances of type {@code T}
     */
    List<T> getAll();

    /**
     * Get a reference to an instance by ID. This assumes object IDs are long values.
     *
     * <p>
     * Note if the instance does not exist, then an exception may be thrown either here or later upon first access.
     *
     * @param id unique ID for instance
     * @return reference to instance with the given {@code id}
     */
    T getReference(long id);

// Lifecycle methods

    /**
     * Save a newly created instance.
     *
     * @param obj object to save
     */
    void save(T obj);

    /**
     * Delete the given instance from the persistent store.
     *
     * @param obj object to delete
     */
    void delete(T obj);

    /**
     * Merge the given object into the current session.
     *
     * @param obj object to merge
     * @return merged persistent instance
     */
    T merge(T obj);

    /**
     * Refresh the given object from the database.
     *
     * @param obj object to refresh
     */
    void refresh(T obj);

    /**
     * Evict an object from the session cache.
     *
     * @param obj object to evict
     */
    void detach(T obj);

// Session methods

    /**
     * Flush outstanding changes to the persistent store.
     */
    void flush();

    /**
     * Set flush mode.
     *
     * @param flushMode desired flush mode
     */
    void setFlushMode(FlushModeType flushMode);

    /**
     * Clear the session cache.
     */
    void clear();

    /**
     * Determine if the current transaction is read-only.
     *
     * @return true if the current transaction is read-only
     * @throws IllegalStateException if no transaction is associated with the current thread
     */
    boolean isReadOnly();

    /**
     * Determine if the current session (i.e., {@code EntityManager}) contains the given instance.
     *
     * @param obj instance to inquire about
     * @return true if the current transaction contains {@code obj}
     */
    boolean contains(T obj);
}

