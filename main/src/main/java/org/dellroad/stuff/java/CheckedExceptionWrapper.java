
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

/**
 * Wraps checked exceptions so they can be thrown across API methods that don't declare them.
 */
@SuppressWarnings("serial")
public class CheckedExceptionWrapper extends RuntimeException {

    private final Exception exception;

    /**
     * Constructor.
     *
     * @param exception the checked exception to wrap
     * @throws IllegalArgumentException if {@code exception} is {@code null}
     */
    public CheckedExceptionWrapper(Exception exception) {
        if (exception == null)
            throw new IllegalArgumentException("null exception");
        this.exception = exception;
    }

    /**
     * Get the wrapped exception.
     *
     * @return the wrapped checked exception
     */
    public Exception getException() {
        return this.exception;
    }

    /**
     * Throw the wrapped exception.
     *
     * @throws Exception always
     */
    public void throwException() throws Exception {
        throw this.exception;
    }
}
