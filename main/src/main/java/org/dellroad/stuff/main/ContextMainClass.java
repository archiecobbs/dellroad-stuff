
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.main;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Support superclass for {@link MainClass} implementations that wish to execute
 * with an associated Spring application context.
 */
public abstract class ContextMainClass extends MainClass {

    protected ClassPathXmlApplicationContext context;

    private void openContext() {
        String path = getContextLocation();
        this.log.info("opening application context " + path);
        this.context = new ClassPathXmlApplicationContext(path, getClass());
    }

    private void closeContext() {
        this.log.info("closing application context");
        this.context.close();
        this.context = null;
    }

    /**
     * Get the classpath location of this instance's associated XML application context.
     * The returned string will resolved on the classpath relative to this instance's class.
     *
     * <p>
     * The implementation in {@link ContextMainClass} returns {@code getClass().getSimpleName() + ".xml"},
     * which will locate an XML file in the same package and with the same name.
     *
     * @return application context XML file location
     */
    protected String getContextLocation() {
        return getClass().getSimpleName() + ".xml";
    }

    /**
     * Autowire this instance using its associated application context.
     * This may be invoked by {@link #runInContext} to autowire this bean using its associated context.
     *
     * <p>
     * For this to work, the application context must have autowiring enabled, e.g., via
     * {@code <context:annotation-config/>}.
     */
    protected void autowire() {
        this.log.info("autowiring instance of " + this.getClass() + " using " + this.context.getAutowireCapableBeanFactory());
        this.context.getAutowireCapableBeanFactory().autowireBean(this);
    }

    @Override
    public int run(final String[] args) throws Exception {

        // Open context
        openContext();

        // Invoke subclass
        try {
            return this.runInContext(args);
        } catch (Exception e) {
            this.log.error("caught exception during execution", e);
            throw e;
        } finally {
            closeContext();
        }
    }

    /**
     * Execute the main method. The application context will be open when this method is invoked.
     *
     * @param args command line arguments
     * @return process exit value
     * @throws Exception if an error occurs; will result in the process exiting with an exit value of one
     */
    protected abstract int runInContext(String[] args) throws Exception;
}
