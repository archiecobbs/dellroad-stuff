
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import org.dellroad.stuff.spring.AbstractBean;

import org.springframework.beans.factory.BeanNameAware;

/**
 * Support superclass for Spring beans that are configured by a {@link PersistentObject} root object, or some portion thereof.
 *
 * <p>
 * This superclass handles registering as a listener on the {@link PersistentObject}, and invokes the
 * {@link #start start()}, {@link #stop stop()}, and {@link #reconfigure reconfigure()} lifecycle methods as needed.
 * The subclass method {@link #getBeanConfig} determines the portion of the configuration object of interest to the subclass.
 * Subclasses may also override {@link #requiresReconfigure} to determine whether a configuration change requires a
 * {@link #reconfigure reconfigure()} operation.
 *
 * <p>
 * Empty starts and empty stops are supported; these are treated as an unconfigured state and the bean will be (or remain)
 * stopped in those states.
 *
 * <p>
 * At any point in time, {@link #getBeanConfig() getBeanConfig()} (and it's alternate form {@link #getRequiredConfig})
 * access this bean's current configuration (if any).
 *
 * @param <ROOT> type of the configuration object root
 * @param <T> type of the sub-graph of ROOT that this bean is configured by
 */
public abstract class AbstractConfiguredBean<ROOT, T> extends AbstractBean
  implements BeanNameAware, PersistentObjectListener<ROOT> {

    private PersistentObject<ROOT> persistentObject;
    private String beanName;
    private boolean running;
    private boolean configured;

    /**
     * Default constructor.
     */
    protected AbstractConfiguredBean() {
    }

    /**
     * Constructor.
     *
     * @param persistentObject keeper of the current configuration
     */
    protected AbstractConfiguredBean(PersistentObject<ROOT> persistentObject) {
        this.setPersistentObject(persistentObject);
    }

    /**
     * Configure the {@link PersistentObject} that this instance will monitor.
     *
     * @param persistentObject keeper of the this bean's configuration
     */
    protected synchronized void setPersistentObject(PersistentObject<ROOT> persistentObject) {
        this.persistentObject = persistentObject;
    }

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    protected String getBeanName() {
        return this.beanName != null ? this.beanName : "" + this;
    }

    /**
     * Start and configure this instance if the current configuration is valid;
     * if not (i.e., "empty start"), then do nothing and wait until it becomes valid.
     */
    @Override
    public synchronized void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        // Check config
        if (this.persistentObject == null)
            throw new IllegalArgumentException("no PersistentObject configured");

        // Listen for updates
        this.persistentObject.addListener(this);
        this.running = true;

        // Start bean if configured
        T beanConfig = this.getBeanConfig();
        if (beanConfig == null)
            return;
        try {
            this.start(beanConfig);
            this.configured = true;
        } catch (Exception e) {
            this.log.error("error starting bean " + this.getBeanName() + ": " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Stop this instance. Does nothing if already stopped.
     */
    @Override
    public synchronized void destroy() throws Exception {
        this.running = false;
        this.configured = false;
        this.persistentObject.removeListener(this);
        this.stop();
        super.destroy();
    }

    /**
     * Is this bean configured? "Configured" means that either {@link #start start()} or {@link #reconfigure reconfigure()} has
     * been invoked successfully since the most recent {@link #stop stop()}.
     *
     * @return true if this instance is configured
     */
    public synchronized boolean isConfigured() {
        return this.configured;
    }

    @Override
    public final synchronized void handleEvent(PersistentObjectEvent<ROOT> event) {

        // Handle race condition vs. destroy()
        if (!this.running)
            return;
        final T oldConfig = event.getOldRoot() != null ? this.getBeanConfig(event.getOldRoot()) : null;
        final T newConfig = event.getNewRoot() != null ? this.getBeanConfig(event.getNewRoot()) : null;

        // Reconfigure bean as required
        boolean configuredBefore = this.configured;
        boolean configuredAfter = newConfig != null;
        if (!configuredBefore && configuredAfter) {
            try {
                this.start(newConfig);
                this.configured = true;
            } catch (Exception e) {
                this.log.error("error starting bean " + this.getBeanName() + ": " + e.getMessage(), e);
            }
        } else if (configuredBefore && !configuredAfter) {
            this.configured = false;
            try {
                this.stop();
            } catch (Exception e) {
                this.log.error("error stopping bean " + this.getBeanName() + ": " + e.getMessage(), e);
            }
        } else if (configuredBefore && configuredAfter) {

            // Determine if bean requires reconfiguration
            if (!this.requiresReconfigure(oldConfig, newConfig))
                return;

            // Reconfigure bean
            try {
                this.configured = false;
                this.reconfigure(oldConfig, newConfig);
                this.configured = true;
            } catch (Exception e) {
                this.log.error("error reconfiguring bean " + this.getBeanName() + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Start this bean. This instance's monitor will be locked when this method is invoked.
     *
     * <p>
     * The implementation in {@link AbstractConfiguredBean} does nothing.
     *
     * @param beanConfig configuration sub-tree object, never null
     * @throws Exception upon failure; in this case the bean will be considered not configured
     */
    protected void start(T beanConfig) throws Exception {
    }

    /**
     * Stop this bean. This instance's monitor will be locked when this method is invoked.
     *
     * <p>
     * The implementation in {@link AbstractConfiguredBean} does nothing.
     */
    protected void stop() {
    }

    /**
     * Reconfigure this bean. This instance's monitor will be locked when this method is invoked.
     *
     * <p>
     * The implementation in {@link AbstractConfiguredBean} invokes {@link #stop} followed by {@link #start}.
     *
     * @param oldConfig old configuration sub-tree object, never null
     * @param newConfig new configuration sub-tree object, never null
     * @throws Exception upon failure; in this case the bean will be considered <b>not</b> configured
     */
    protected void reconfigure(T oldConfig, T newConfig) throws Exception {
        this.stop();
        this.start(newConfig);
    }

    /**
     * Determine if a change from {@code oldConfig} to {@code newConfig} requires a {@link #reconfigure reconfigure()} operation.
     *
     * <p>
     * The implementation in {@link AbstractConfiguredBean} invokes {@code newConfig.equals(oldConfig)} and returns
     * true if they are not equal.
     *
     * @param oldConfig old configuration sub-tree object, never null
     * @param newConfig new configuration sub-tree object, never null
     * @return true if {@link #reconfigure reconfigure()} needs to be invoked
     */
    protected boolean requiresReconfigure(T oldConfig, T newConfig) {
        return !newConfig.equals(oldConfig);
    }

    /**
     * Extract the configuration sub-tree object that this node uses for its configuration given the root configuration object.
     *
     * @param rootConfig root config object, never null
     * @return this bean's config object
     */
    protected abstract T getBeanConfig(ROOT rootConfig);

    /**
     * Get the current configuration (sub-tree) object appropriate for this instance, or null if not configured.
     *
     * @return current bean configuration, or null if not {@linkplain #isConfigured configured}
     *  or the {@link PersistentObject} has been stopped
     */
    protected synchronized T getBeanConfig() {
        ROOT rootConfig;
        synchronized (this.persistentObject) {
            if (!this.persistentObject.isStarted())
                return null;
            rootConfig = this.persistentObject.getSharedRoot();
        }
        if (rootConfig == null)
            return null;
        return this.getBeanConfig(rootConfig);
    }

    /**
     * Get the current configuration (sub-tree) object appropriate for this instance, and require that
     * this instance also be {@linkplain #isConfigured configured} at the time this method is invoked.
     * This method is like {@link #getBeanConfig getBeanConfig()} but it throws an exception instead of returning null.
     *
     * @return current bean configuration, never null
     * @throws NotConfiguredException if this instance is not {@linkplain #isConfigured configured}
     * @throws NotConfiguredException if the {@link PersistentObject} has been stopped
     */
    protected T getRequiredConfig() {
        T beanConfig = this.getBeanConfig();
        if (beanConfig == null)
            throw new NotConfiguredException();
        return beanConfig;
    }
}
