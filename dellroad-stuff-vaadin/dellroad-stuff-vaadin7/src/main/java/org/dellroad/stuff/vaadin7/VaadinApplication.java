
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin7;

import com.vaadin.server.SessionDestroyListener;
import com.vaadin.server.VaadinSession;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A globally accessible "Vaadin application" singleton.
 *
 * <p>
 * At most one instance is associated with each {@link VaadinSession} (attempting to create more than one will result in
 * an exception). This singleton instance is then always accessible from any Vaadin thread via {@link #get()}.
 *
 * <p>
 * Although this class does not require Spring, the singleton instance can be declared as a bean in the Spring XML
 * application context created by a {@link SpringVaadinSessionListener}, which allows other beans and widgets
 * in the Vaadin application context to autowire it and have access to the methods provided here:
 * <blockquote><pre>
 *  &lt;bean class="org.dellroad.stuff.vaadin7.VaadinApplication"/&gt;
 * </pre></blockquote>
 * If your {@link VaadinApplication} instance allocates any resources on startup, be sure to configure it so
 * that it cleans them up on application context shutdown, for example, by specifying {@code destroy-method="..."}.
 *
 * <p>
 * If you are in a clustered environment and are subclassing this class, {@code scope="session"} is recommended;
 * without it, there will be one instance per server+session rather than one unique instance per session. Of course,
 * whether that matters depends on the semantics of the subclass fields. In any case, be aware that no instance will
 * exist in session until the application context is refreshed.
 *
 * <p>
 * Note: using {@code scope="session"} requires adding a {@code <listener>} clause registering Spring's
 * {@link org.springframework.web.context.request.RequestContextListener} in your {@code web.xml}.
 *
 * <p>
 * If this class is subclassed, additional application-specific fields and methods can be supplied to the entire
 * application via the same mechanism. Subclasses can override {@link #init} for any application-specific initialization.
 *
 * <p>
 * Note: the singleton instance of this class is stored in the Vaadin session and is {@link Serializable}. Therefore,
 * in a clustered environment, subclasses must be prepared to handle (de)serialization. Also, avoid declaring fields
 * that reference this class in non-session-scope beans; instead, use {@link #get VaadinApplication.get()}.
 *
 * @see SpringVaadinServlet
 * @see SpringVaadinSessionListener
 * @see com.vaadin.server.VaadinService
 */
public class VaadinApplication implements Serializable {

    private static final long serialVersionUID = -4202507689739224846L;
    private static final Class<VaadinApplication> ATTRIBUTE_KEY = VaadinApplication.class;

    protected transient Logger log = LoggerFactory.getLogger(this.getClass());

    private transient VaadinSession session;

    /**
     * Convenience constructor. Equivalent to:
     * <blockquote><code>
     *  {@link #VaadinApplication(VaadinSession) VaadinApplication}({@link VaadinUtil#getCurrentSession})
     * </code></blockquote>
     *
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if there is already a {@link VaadinApplication} instance associated with the current session
     */
    public VaadinApplication() {
        this(VaadinUtil.getCurrentSession());
    }

    /**
     * Primary Constructor.
     *
     * @param session the session with which this instance should be associated
     * @throws IllegalArgumentException if {@code session} is null
     * @throws IllegalStateException if there is already a {@link VaadinApplication} instance associated with {@code session}
     */
    public VaadinApplication(VaadinSession session) {

        // Get session
        if (session == null)
            throw new IllegalArgumentException("null session");
        this.session = session;

        // Check for already-existing instance
        VaadinApplication vaadinApplication = this.session.getAttribute(ATTRIBUTE_KEY);
        if (vaadinApplication != null) {
            throw new IllegalStateException("there is already a VaadinApplication associated with VaadinSession "
              + this.session + ": " + vaadinApplication + "; did you accidentally declare more than one instance of"
              + " VaadinApplication in the Vaadin Spring XML application context?");
        }

        // Set session attribute
        VaadinApplication.setAttribute(this.session, ATTRIBUTE_KEY, this);

        // Delegate to subclass for further initialization
        this.init();
    }

    /**
     * Perform any further initialization at construction time.
     *
     * <p>
     * The implementation in {@link VaadinApplication} does nothing. Subclasses may override as desired.
     */
    protected void init() {
    }

    /**
     * Get the {@link VaadinSession} associated with this instance.
     *
     * @return associated VaadinSession, never null
     */
    public VaadinSession getSession() {
        return this.session;
    }

    /**
     * Close the {@link VaadinSession} associated with this instance.
     * After invoking this method, the caller would normally ensure that no further references to this
     * instance remain so that it and the associated {@link VaadinSession} can be freed.
     *
     * <p>
     * The implementation in {@link VaadinApplication} just delegates to {@link com.vaadin.server.VaadinSession#close}.
     */
    public void close() {
        this.session.close();
    }

    /**
     * Get the singleton {@link VaadinApplication} instance associated with the current {@link VaadinSession}.
     *
     * <p>
     * This is a convenience method, equivalent to:
     * <blockquote><code>
     *  {@link #get(Class) VaadinApplication.get}({@link VaadinApplication VaadinApplication.class})
     * </code></blockquote>
     *
     * @return singleton instance for the current Vaadin application, never null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if there is no {@link VaadinApplication} instance associated with the current session
     */
    public static VaadinApplication get() {
        return VaadinApplication.get(VaadinApplication.class);
    }

    /**
     * Get the singleton instance of the specified class associated with the current {@link VaadinSession}.
     *
     * <p>
     * This is a convenience method, equivalent to:
     * <blockquote><code>
     *  {@link #get(Class) VaadinApplication.get}({@link VaadinUtil#getCurrentSession}, clazz)
     * </code></blockquote>
     *
     * <p>
     * Useful for subclasses of {@link VaadinApplication} that want to provide their own zero-parameter {@code get()} methods.
     *
     * @param clazz singleton instance type
     * @param <T> application type
     * @return singleton instance of {@code clazz} in the session, never null
     * @throws IllegalArgumentException if {@code clazz} is null
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if there is no singleton of type {@code clazz} associated with the current session
     */
    public static <T extends VaadinApplication> T get(Class<T> clazz) {
        return VaadinApplication.get(VaadinUtil.getCurrentSession(), clazz);
    }

    /**
     * Get the singleton instance of the specified class associated with the given session.
     *
     * @param session Vaadin session
     * @param clazz singleton instance type
     * @param <T> application type
     * @return singleton instance of {@code clazz} in the session, never null
     * @throws IllegalArgumentException if {@code session} is null
     * @throws IllegalArgumentException if {@code clazz} is null
     * @throws IllegalStateException if there is no singleton of type {@code clazz} associated with the {@code session}
     */
    public static <T extends VaadinApplication> T get(VaadinSession session, Class<T> clazz) {

        // Sanity check
        if (session == null)
            throw new IllegalArgumentException("null session");
        if (clazz == null)
            throw new IllegalArgumentException("null clazz");

        // Get the application
        VaadinApplication vaadinApplication = session.getAttribute(ATTRIBUTE_KEY);
        if (vaadinApplication == null) {
            throw new IllegalStateException("there is no VaadinApplication associated with the current VaadinSession"
              + "; did you declare an instance of VaadinApplication in the Vaadin Spring XML application context?"
              + " And if declared scope=\"session\", did you register Spring's RequestContextListener in web.xml?");
        }

        // Check type
        if (!clazz.isInstance(vaadinApplication)) {
            throw new IllegalStateException("there is a VaadinApplication associated with the current VaadinSession"
              + " but it is not an instance of " + clazz + "; instead it has type " + vaadinApplication.getClass().getName());
        }

        // Done
        return clazz.cast(vaadinApplication);
    }

    /**
     * Peform some action while holding the lock of the {@link VaadinSession} associated with this instance.
     *
     * <p>
     * This is a convenience method that in turn invokes {@link VaadinUtil#invoke VaadinUtil.invoke()} using the
     * {@link VaadinSession} associated with this instance.
     *
     * @param action action to perform
     * @throws IllegalArgumentException if {@code action} is null
     * @see VaadinUtil#invoke
     */
    public void invoke(Runnable action) {
        VaadinUtil.invoke(this.session, action);
    }

    /**
     * Peform some action asynchronously while holding the lock of the {@link VaadinSession} associated with this instance.
     *
     * <p>
     * This is a convenience method that in turn invokes {@link VaadinUtil#invokeLater VaadinUtil.invokeLater()} using the
     * {@link VaadinSession} associated with this instance.
     *
     * @param action action to perform
     * @return future for the action
     * @throws IllegalArgumentException if {@code action} is null
     * @see VaadinUtil#invokeLater
     */
    public Future<Void> invokeLater(Runnable action) {
        return VaadinUtil.invokeLater(this.session, action);
    }

    /**
     * Register for a notification when the {@link VaadinSession} is closed, without creating a memory leak.
     *
     * <p>
     * This is a convenience method that in turn invokes
     * {@link VaadinUtil#addSessionDestroyListener VaadinUtil.addSessionDestroyListener()} using the
     * {@link VaadinSession} associated with this instance.
     *
     * @param listener listener for notifications
     * @throws IllegalArgumentException if {@code listener} is null
     * @see VaadinUtil#addSessionDestroyListener
     */
    public void addSessionDestroyListener(SessionDestroyListener listener) {
        VaadinUtil.addSessionDestroyListener(this.session, listener);
    }

    /**
     * Remove a listener added via {@link #addSessionDestroyListener addSessionDestroyListener()}.
     *
     * <p>
     * This is a convenience method that in turn invokes
     * {@link VaadinUtil#removeSessionDestroyListener VaadinUtil.removeSessionDestroyListener()} using the
     * {@link VaadinSession} associated with this instance.
     *
     * @param listener listener for notifications
     * @throws IllegalArgumentException if {@code listener} is null
     * @see VaadinUtil#removeSessionDestroyListener
     */
    public void removeSessionDestroyListener(SessionDestroyListener listener) {
        VaadinUtil.removeSessionDestroyListener(this.session, listener);
    }

    // This method exists solely to bind the generic type
    private static <T> void setAttribute(VaadinSession session, Class<T> clazz, Object value) {
        session.setAttribute(clazz, clazz.cast(value));
    }

// Serialization

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        this.session = VaadinUtil.getCurrentSession();
        this.log = LoggerFactory.getLogger(this.getClass());
    }
}

