
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import com.google.common.base.Preconditions;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.groups.Default;

import java.util.Set;

/**
 * Validation utility methods.
 */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    /**
     * Validate the given object.
     *
     * <p>
     * This method simply creates a {@link ValidationContext} with the given root and invokes {@link ValidationContext#validate()}.
     *
     * @param obj object to validate
     * @param groups group(s) targeted for validation (if empty, defaults to {@link Default})
     * @param <T> root validation object type
     * @return zero or more violations
     * @throws IllegalArgumentException if either paramter is null
     */
    public static <T> Set<ConstraintViolation<T>> validate(T obj, Class<?>... groups) {
        return new ValidationContext<>(obj, groups).validate();
    }

    /**
     * Describe the validation errors in a friendly format.
     *
     * @param violations validation violations
     * @return description of the validation errors
     * @throws IllegalArgumentException if {@code violations} is null
     */
    public static String describe(Set<? extends ConstraintViolation<?>> violations) {
        Preconditions.checkArgument(violations != null, "null violations");
        if (violations.isEmpty())
            return "  (no violations)";
        StringBuilder buf = new StringBuilder(violations.size() * 32);
        for (ConstraintViolation<?> violation : violations) {
            String violationPath = violation.getPropertyPath().toString();
            buf.append("  ");
            if (violationPath.length() > 0)
                buf.append("[").append(violationPath).append("]: ");
            buf.append(violation.getMessage()).append('\n');
        }
        return buf.toString();
    }

    /**
     * Escape any special characters in the given literal string, returning an equivalent JSR 303 message template.
     *
     * @param message plain message string
     * @return JSR 303 message template
     * @throws IllegalArgumentException if {@code message} is null
     */
    public static String escapeForTemplate(String message) {
        Preconditions.checkArgument(message != null, "null message");
        return message.replaceAll("([\\\\{}$])", "\\\\$1");
    }
}
