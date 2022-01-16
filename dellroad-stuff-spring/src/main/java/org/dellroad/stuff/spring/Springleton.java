
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ApplicationObjectSupport;

/**
 * Global singleton that provides access to the application context in which it is defined from anywhere.
 * Reminiscent of Spring's <a href="https://www.google.com/search?q=spring+evil+singleton">"evil singleton"</a>
 * from days of yore.
 *
 * <p>
 * This bean allows at most one instantiated instance per class loader, and that instance may be defined
 * in at most one {@link ApplicationContext}. In particular, this means web containers hosting multiple
 * web applications must create separate class loaders per web application.
 *
 * <p>
 * This class may be subclassed to add autowired properties, etc. If so, access the singleton in the same
 * way (i.e., via {@link #getInstance Springleton.getInstance()}) and then downcast as needed.
 */
public class Springleton extends ApplicationObjectSupport {

    private static Springleton instance;

// Constructor

    /**
     * Constructor.
     *
     * @throws IllegalStateException if another instance has already been created in the same class loader
     */
    public Springleton() {
        synchronized (Springleton.class) {
            if (Springleton.instance != null)
                throw new IllegalStateException("singleton already instantiated");
            Springleton.instance = this;
        }
    }

// Accessor

    /**
     * Get the singleton instance.
     *
     * @return {@link Springleton} singleton
     * @throws IllegalStateException if no instance has been created yet
     */
    public static Springleton getInstance() {
        synchronized (Springleton.class) {
            if (Springleton.instance == null)
                throw new IllegalStateException("no instance has been created yet");
            return Springleton.instance;
        }
    }

// Methods

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@link Springleton} always returns true.
     */
    @Override
    public boolean isContextRequired() {
        return true;
    }
}
