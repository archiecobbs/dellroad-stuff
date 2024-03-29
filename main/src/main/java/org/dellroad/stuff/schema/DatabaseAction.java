
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.schema;

/**
 * Database action interface.
 *
 * @param <T> database transaction type
 */
@FunctionalInterface
public interface DatabaseAction<T> {

    /**
     * Apply this action to the database using the provided open transaction.
     *
     * @param transaction open transaction
     * @throws Exception if the action fails
     */
    void apply(T transaction) throws Exception;
}
