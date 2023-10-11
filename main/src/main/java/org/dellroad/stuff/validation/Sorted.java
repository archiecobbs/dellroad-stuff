
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
import java.util.Comparator;

/**
 * Validation constraint that checks elements are sorted.
 * Applies to non-primitive arrays, collections and maps; for maps, the keys are examined.
 * If any element is null, it is skipped.
 */
@Documented
@Constraint(validatedBy = SortedValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Sorted {

    String message() default "Collection is not sorted";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Specifies a {@link java.util.Comparator} to use. If none is specified, the natural sort ordering is used.
     * The class must have a default constructor.
     *
     * @return comparator class
     */
    @SuppressWarnings("rawtypes")
    Class<? extends Comparator> comparator() default Comparator.class;

    /**
     * Configures whether the sorting should be strict, i.e., whether adjacent equal elements should be disallowed.
     *
     * @return true to disallow equal elements
     */
    boolean strict() default true;
}

