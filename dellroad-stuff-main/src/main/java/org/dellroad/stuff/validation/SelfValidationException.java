
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
 * @see SelfValidating
 */
@SuppressWarnings("serial")
public class SelfValidationException extends Exception {

    public SelfValidationException() {
    }

    public SelfValidationException(String message) {
        super(message);
    }

    public SelfValidationException(Throwable cause) {
        super(cause);
    }

    public SelfValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

