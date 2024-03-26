
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;

import org.dellroad.stuff.vaadin24.util.WholeBeanValidator;

/**
 * Implemented by {@link FieldBuilder} target bean classes that want to apply some bean-level validation logic themselves.
 *
 * <p>
 * If a {@link FieldBuilder}'s target bean type implements this interface, then the {@link FieldBuilder} will automatically
 * add a bean-level validation check by way of {@link #validateBean validateBean()}.
 *
 * <p>
 * This class is a simpler, non-JSR 303 alternative to {@link WholeBeanValidator}.
 *
 * <p>
 * This is a bean-level validator, so any {@link Binder} using this validator will need access to an actual bean in order
 * to validate (e.g., via {@link Binder#setBean Binder.setBean()}, {@link Binder#writeBean Binder.writeBean()},
 * {@link Binder#writeBeanIfValid Binder.writeBeanIfValid()}, etc.), otherwise you'll get an {@link IllegalStateException}
 * with <i>bean level validators have been configured but no bean is currently set</i>.
 *
 * @see ValidatingField
 * @see WholeBeanValidator
 * @see Binder#withValidator(Validator) Binder.withValidator()
 */
public interface ValidatingBean {

    /**
     * Apply bean-level validation to this bean instance.
     *
     * @param context the value context for validation
     * @return the validation result
     */
    ValidationResult validateBean(ValueContext context);
}
