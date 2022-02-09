
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.component;

import com.vaadin.flow.data.provider.ListDataProvider;

import java.util.Arrays;

/**
 * A simple data provider containing {@link Enum} values.
 */
@SuppressWarnings("serial")
public class EnumDataProvider<T extends Enum<T>> extends ListDataProvider<T> {

    /**
     * Constructor.
     *
     * @param type enum type
     * @throws IllegalArgumentException if {@code type} is null
     * @throws IllegalArgumentException if {@code type} is not an {@link Enum} type
     */
    public EnumDataProvider(final Class<T> type) {
        super(Arrays.asList(EnumDataProvider.getEnumConstants(type)));
    }

    private static <T> T[] getEnumConstants(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        final T[] values = type.getEnumConstants();
        if (values == null)
            throw new IllegalArgumentException("non-Enum type");
        return values;
    }
}
