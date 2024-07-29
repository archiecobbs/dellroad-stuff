
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin7;

import com.vaadin.server.SessionDestroyEvent;
import com.vaadin.server.SessionDestroyListener;
import com.vaadin.server.VaadinSession;

import java.util.HashMap;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * A Spring custom {@link Scope} for Vaadin applications.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-xml-doc.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * This scopes beans to the lifetime of the {@link VaadinSession} (formerly known as "Vaadin application").
 * Spring {@linkplain org.springframework.beans.factory.DisposableBean#destroy destroy-methods}
 * will be invoked when the {@link VaadinSession} is closed.
 *
 * <p>
 * To enable this scope, simply add this bean to your application context as a singleton (it will register itself):
 * <pre><code class="language-xml">
 *  &lt;!-- Enable the "vaadinApplication" custom scope --&gt;
 *  &lt;bean class="org.dellroad.stuff.vaadin7.VaadinApplicationScope"/&gt;
 * </code></pre>
 * Then declare scoped beans normally using the scope name {@code "vaadinApplication"}.
 */
@SuppressWarnings("serial")
public class VaadinApplicationScope implements Scope, BeanFactoryPostProcessor, SessionDestroyListener {

    /**
     * Key to the current {@link VaadinSession} instance. For use by {@link #resolveContextualObject}.
     */
    public static final String VAADIN_SERVICE_SESSION_KEY = "vaadinServiceSession";

    /**
     * The name of this scope (i.e., <code>{@value}</code>).
     */
    public static final String SCOPE_NAME = "vaadinApplication";

    private final HashMap<VaadinSession, SessionBeanHolder> beanHolders = new HashMap<>();

// BeanFactoryPostProcessor methods

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        beanFactory.registerScope(VaadinApplicationScope.SCOPE_NAME, this);
    }

// SessionDestroyListener

    @Override
    public void sessionDestroy(SessionDestroyEvent event) {
        final VaadinSession session = event.getSession();
        SessionBeanHolder beanHolder;
        synchronized (this) {
            beanHolder = this.beanHolders.remove(session);
        }
        if (beanHolder != null)
            beanHolder.close();
    }

// Scope methods

    @Override
    public synchronized Object get(String name, ObjectFactory<?> objectFactory) {
        return this.getSessionBeanHolder(true).getBean(name, objectFactory);
    }

    @Override
    public synchronized Object remove(String name) {
        SessionBeanHolder beanHolder = this.getSessionBeanHolder(false);
        return beanHolder != null ? beanHolder.remove(name) : null;
    }

    @Override
    public synchronized void registerDestructionCallback(String name, Runnable callback) {
        this.getSessionBeanHolder(true).registerDestructionCallback(name, callback);
    }

    @Override
    public String getConversationId() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null)
            return null;
        return session.getClass().getName() + "@" + System.identityHashCode(session);
    }

    @Override
    public Object resolveContextualObject(String key) {
        if (VAADIN_SERVICE_SESSION_KEY.equals(key))
            return VaadinSession.getCurrent();
        return null;
    }

// Internal methods

    private synchronized SessionBeanHolder getSessionBeanHolder(boolean create) {
        VaadinSession session = VaadinUtil.getCurrentSession();
        VaadinUtil.addSessionDestroyListener(session, this);
        SessionBeanHolder beanHolder = this.beanHolders.get(session);
        if (beanHolder == null && create) {
            beanHolder = new SessionBeanHolder();
            this.beanHolders.put(session, beanHolder);
        }
        return beanHolder;
    }

// Bean holder class corresponding to a single Application instance

    private static class SessionBeanHolder {

        private final HashMap<String, Object> beans = new HashMap<>();
        private final HashMap<String, Runnable> destructionCallbacks = new HashMap<>();

        public Object getBean(String name, ObjectFactory<?> objectFactory) {
            Object bean = this.beans.get(name);
            if (bean == null) {
                bean = objectFactory.getObject();
                this.beans.put(name, bean);
            }
            return bean;
        }

        public Object remove(String name) {
            this.destructionCallbacks.remove(name);
            return this.beans.remove(name);
        }

        public void registerDestructionCallback(String name, Runnable callback) {
            this.destructionCallbacks.put(name, callback);
        }

        public void close() {
            for (Runnable callback : this.destructionCallbacks.values())
                callback.run();
            this.beans.clear();
            this.destructionCallbacks.clear();
        }
    }
}

