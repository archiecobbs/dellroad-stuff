
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation constraint requiring a {@link String} to be a valid DNS hostname (or hostname component).
 */
@Documented
@Constraint(validatedBy = {})
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "^(([\\p{Alnum}]([-\\p{Alnum}]*[\\p{Alnum}])?)\\.)*([\\p{Alpha}]([-\\p{Alnum}]*[\\p{Alnum}])?)$")
@ReportAsSingleViolation
public @interface Hostname {

    String message() default "Invalid hostname or hostname component";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
