
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.field;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.binder.ValueContext;

/**
 * Implemented by fields that are also capable of validating field values.
 *
 * <p>
 * The main purpose of doing things this way is modularity: it allows a field to define its own validation logic,
 * rather than relying on some external component to do so.
 *
 * <p>
 * This interface would typically be implemented by complex fields composed from sub-fields, for which some overall validation
 * needs to be performed. See {@link FieldBuilderCustomField} for a motivating example.
 *
 * <p>
 * {@link FieldBuilder} recognizes fields implementing this interface and automatically registers them
 * as an additional {@link Validator} when binding the field.
 *
 * @param <E> value change event type
 * @param <V> internal binder bean type
 * @see FieldBuilder
 * @see FieldBuilderCustomField
 * @see org.dellroad.stuff.vaadin22.util.WholeBeanValidator
 */
public interface ValidatingField<E extends HasValue.ValueChangeEvent<V>, V> extends HasValue<E, V> {

    /**
     * Validate this instance.
     *
     * @param value field value to validate
     * @param context the value context for validation
     * @return the validation result
     */
    ValidationResult validate(V value, ValueContext context);
}
