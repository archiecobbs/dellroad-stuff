
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.fieldbuilder;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Validator;

/**
 * Implemented by fields that provide their own {@link Validator} to use whenever they are bound into a {@link Binder}.
 *
 * <p>
 * {@link FieldBuilder} recognizes fields that implement this interface and automatically registers the {@link Validator}
 * returned by {@link #getInternalValidator} when binding the field.
 *
 * <p>
 * This interface would typically be implemented by complex fields composed from sub-fields for which some inter-field
 * validation needs to be performed. See {@link GeneratedCustomField} for an example.
 *
 * @param <E> value change event type
 * @param <V> internal binder bean type
 * @see FieldBuilder
 * @see GeneratedCustomField
 */
@SuppressWarnings("serial")
public interface HasInternalValidator<E extends HasValue.ValueChangeEvent<V>, V> extends HasValue<E, V> {

    /**
     * Provide {@link Validator}'s to be used when this field is bound into a {@link Binder}.
     *
     * @return zero or more {@link Validator}'s
     */
    Validator<? super V> getInternalValidator();
}
