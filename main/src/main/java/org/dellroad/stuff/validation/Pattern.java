
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Works like the standard {@link jakarta.validation.constraints.Pattern @Pattern} but applies to any
 * type of object, converting to {@link String} as necessary via {@link Object#toString}, and recursing
 * on collection types.
 */
@Documented
@Constraint(validatedBy = PatternValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
public @interface Pattern {

    String message() default "Does not match the pattern \"{regexp}\"";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Regular expression that must be matched.
     *
     * @return regular expression
     */
    String regexp();

    /**
     * Regular expression flags.
     *
     * @return regular expression flags
     */
    jakarta.validation.constraints.Pattern.Flag[] flags() default {};
}
