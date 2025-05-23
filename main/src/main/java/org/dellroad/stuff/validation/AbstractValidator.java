
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import com.google.common.base.Preconditions;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * Support superclass for validators.
 */
public abstract class AbstractValidator<C extends Annotation, T> implements ConstraintValidator<C, T> {

    /**
     * The constraint being checked by this instance.
     */
    protected C annotation;

    @Override
    public void initialize(C annotation) {
        this.annotation = annotation;
    }

    /**
     * Convenience method to add a constraint violation described by {@code message} and disable the default violation.
     *
     * @param context validation context
     * @param message violation message (<i>not</i> a message template)
     * @throws IllegalArgumentException if either parameter is null
     */
    protected void setViolation(ConstraintValidatorContext context, String message) {
        Preconditions.checkArgument(context != null, "null context");
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(ValidationUtil.escapeForTemplate(message)).addConstraintViolation();
    }

    /**
     * Apply this constraint to all values in a collection. This is a convenience method for validators
     * that want to work with both simple properties and collection properties.
     *
     * @param collection collection of objects to validate
     * @param context validation context
     * @return true if all objects are valid
     */
    protected boolean isCollectionValid(Collection<? extends T> collection, ConstraintValidatorContext context) {
        boolean result = true;
        for (T value : collection) {
            if (!this.isValid(value, context))
                result = false;
        }
        return result;
    }
}
