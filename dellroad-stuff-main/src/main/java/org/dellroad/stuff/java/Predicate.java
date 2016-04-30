
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

/**
 * A boolean predicate.
 */
public interface Predicate {

    /**
     * Determine if the predicate is true.
     *
     * @return true if predicate is true, otherwise false
     */
    boolean test();
}

