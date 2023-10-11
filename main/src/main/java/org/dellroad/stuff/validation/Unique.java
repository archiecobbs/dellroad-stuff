
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation constraint that checks for the uniqueness of the constrained property's value over some uniqueness domain.
 * Uniqueness domains are identified by an arbitrary name; each domain is independent of the others.
 *
 * <p>
 * By supplying a {@link #uniquifier} you can change the unique value associated with the constrained property;
 * by default, it is just the value of the property.
 *
 * <p>
 * For this constraint to be effective, validation must be performed via
 * {@link ValidationContext#validate ValidationContext.validate()}.
 *
 * <p>
 * This constraint will work on {@link java.util.Collection Collection} or {@link java.util.Map Map} properties as well.
 *
 * <p>
 * Note: {@code null} values are not considered, i.e., they are not required to be unique.
 */
@Documented
@Constraint(validatedBy = UniqueValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Unique {

    String message() default "Value is not unique";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Name of the uniqueness domain. Domains are identified by name. Different domains do not interact.
     *
     * @return identifier for the uniqueness domain
     */
    String domain();

    /**
     * Class that converts values into unique objects (in the sense of {@link #equals equals()} and {@link #hashCode hashCode()}).
     * Leave unset if the values themselves are sufficient for uniqueness (e.g., the objects themselves are
     * unique under {@link #equals equals()} comparison).
     *
     * @return unique object generator
     * @see DefaultUniquifier
     */
    Class<? extends Uniquifier<?>> uniquifier() default DefaultUniquifier.class;
}

