
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

/**
 * Exception that can be thrown by {@link SelfValidating#checkValid}.
 *
 * <p>
 * Instances will be automatically caught and converted into a constraint violation using the exception's message
 * if any, otherwise the default message.
 *
 * <p>
 * Note that the {@code message} constructor parameter is a JSR 303 template string; to provide a plain text string,
 * use {@link #escapeTemplate SelfValidationException.escapeTemplate()} or {@link #plain SelfValidationException.plain()}.
 *
 * @see SelfValidating
 */
@SuppressWarnings("serial")
public class SelfValidationException extends Exception {

    /**
     * Constructor.
     */
    public SelfValidationException() {
    }

    /**
     * Constructor.
     *
     * @param message validation error message template
     */
    public SelfValidationException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause underlying cause of this exception
     */
    public SelfValidationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message validation error message template
     * @param cause underlying cause of this exception
     */
    public SelfValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Build a {@link SelfValidationException} with the given plain text message.
     *
     * @param message plain message string, or null for none
     * @throws SelfValidationException always
     * @see #escapeTemplate SelfValidationException.escapeTemplate()
     */
    public static SelfValidationException plain(String message) {
        return new SelfValidationException(message != null ? SelfValidationException.escapeTemplate(message) : null);
    }

    /**
     * Escape any special characters in the given literal string, returning an equivalent JSR 303 message template.
     *
     * @param message plain message string
     * @return JSR 303 message template
     * @throws IllegalArgumentException if {@code message} is null
     */
    public static String escapeTemplate(String message) {
        if (message == null)
            throw new IllegalArgumentException("null message");
        return message.replaceAll("([\\\\{}$])", "\\\\$1");
    }
}
