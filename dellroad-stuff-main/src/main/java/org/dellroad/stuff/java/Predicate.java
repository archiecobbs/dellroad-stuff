
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.util.function.BooleanSupplier;

/**
 * A boolean predicate.
 */
@FunctionalInterface
public interface Predicate extends BooleanSupplier {

    /**
     * Determine if the predicate is true.
     *
     * @return true if predicate is true, otherwise false
     */
    boolean test();

    /**
     * Returns {@code this.}{@link #test}.
     *
     * @return result of test
     */
    @Override
    default boolean getAsBoolean() {
        return this.test();
    }
}

