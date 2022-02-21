
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.component;

import com.vaadin.flow.data.provider.ListDataProvider;

import java.util.EnumSet;

/**
 * A simple data provider containing {@link Enum} values.
 *
 * <p>
 * When created by a {@link org.dellroad.stuff.vaadin22.fieldbuilder.FieldBuilder}, instances will
 * automatically infer the {@link Enum} type via {@link AutoBuildContext#inferDataType} and then
 * populate themselves with the corresponding enum values.
 */
@SuppressWarnings("serial")
public class EnumDataProvider<T extends Enum<T>> extends ListDataProvider<T> {

    private final Class<T> type;

    /**
     * Auto-build Constructor.
     *
     * <p>
     * This constructor will infer the {@link Enum} type from the annotated method's return value.
     *
     * @param type enum type
     * @throws IllegalArgumentException if {@code type} is null
     * @throws ClassCastException if {@code type} is not an {@link Enum} type
     */
    @SuppressWarnings("unchecked")
    public EnumDataProvider(AutoBuildContext ctx) {
        this((Class<T>)ctx.inferDataType());
    }

    /**
     * Constructor.
     *
     * @param type enum type
     * @throws IllegalArgumentException if {@code type} is null
     * @throws ClassCastException if {@code type} is not an {@link Enum} type
     */
    public EnumDataProvider(Class<T> type) {
        super(EnumSet.allOf(type));
        this.type = type;
    }

    /**
     * Get the enum type.
     *
     * @return enum type
     */
    public Class<T> getEnumType() {
        return this.type;
    }
}
