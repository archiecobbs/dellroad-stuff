
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

/**
 * Default uniquifier for {@link Unique @Unique} constraints.
 *
 * <p>
 * This class assumes all non-null values are already uniquified and so just returns its argument from {@link #getUniqued}.
 */
public class DefaultUniquifier implements Uniquifier<Object> {

    /**
     * Uniquify value.
     *
     * <p>
     * The implementation in {@link DefaultUniquifier} just returns {@code value}.
     */
    @Override
    public Object getUniqued(Object value) {
        return value;
    }
}

