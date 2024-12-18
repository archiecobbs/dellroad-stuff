
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

/**
 * Exception that can be thrown by {@link SelfValidating#checkValid}.
 *
 * <p>
 * Instances will be automatically caught and converted into a constraint violation using the exception's message
 * if any, otherwise the default message.
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
     * @param message validation error message
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
}
