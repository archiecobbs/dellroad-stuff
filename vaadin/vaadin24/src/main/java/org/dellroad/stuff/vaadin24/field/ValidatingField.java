
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.binder.ValueContext;

import java.time.LocalDate;

/**
 * Implemented by fields that are themselves capable of validating field values.
 *
 * <p>
 * The main purpose of doing things this way is modularity: it allows a field to define its own validation logic,
 * rather than relying on some external component to do so. Of course, this only works for validation checks that
 * can be done by the field itself and are therefore independent of the containing context (e.g., other fields).
 *
 * <p>
 * This interface would typically be implemented by complex fields composed from sub-fields for which some overall validation
 * is required. For example, imagine a date range field composed of two {@link LocalDate} fields representing the start
 * and end date: an overall validation would require that the start date be prior to the end date.
 *
 * <p>
 * However, for any {@link ValidatingField} validation to take effect, some mechanism must register the validation with
 * an appropriate {@link Binder}. This happens automatically for fields created by a {@link FieldBuilder}, but can also
 * be done manually via {@link #addValidationTo addValidationTo()}.
 *
 * @param <E> value change event type
 * @param <V> internal binder bean type
 * @see FieldBuilder
 * @see FieldBuilderCustomField
 * @see org.dellroad.stuff.vaadin24.util.WholeBeanValidator
 */
@SuppressWarnings("serial")
public interface ValidatingField<E extends HasValue.ValueChangeEvent<V>, V> extends HasValue<E, V> {

    /**
     * Validate this instance.
     *
     * @param value field value to validate
     * @param context the value context for validation
     * @return the validation result
     */
    ValidationResult validate(V value, ValueContext context);

    /**
     * Add this instance as a {@link Validator} to the given binding.
     *
     * @param builder binding builder
     * @param <BEAN> binder bean type
     * @param <TARGET> binding target type
     * @return updated binding builder
     * @throws IllegalArgumentException if {@code builder} is null
     */
    default <BEAN, TARGET extends V>
      Binder.BindingBuilder<BEAN, TARGET> addValidationTo(Binder.BindingBuilder<BEAN, TARGET> builder) {
        if (builder == null)
            throw new IllegalArgumentException("null builder");
        return builder.withValidator(this::validate);
    }
}
