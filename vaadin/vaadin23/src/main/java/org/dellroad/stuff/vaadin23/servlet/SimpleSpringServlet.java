
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.servlet;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.SpringVaadinServletService;
import com.vaadin.flow.spring.VaadinScopesConfig;
import com.vaadin.flow.spring.annotation.EnableVaadin;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.dellroad.stuff.spring.Springleton;
import org.dellroad.stuff.vaadin23.data.VaadinSessionDataProvider;
import org.dellroad.stuff.vaadin23.util.VaadinUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.context.ContextLoaderListener;

/**
 * A variant of {@link com.vaadin.flow.spring.SpringServlet} that doesn't require Spring Boot, supports traditional
 * XML configuration for Spring and servlets via {@code web.xml}, and adds support for tracking and limiting sessions.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * This class needs a way to find the {@link ApplicationContext}; this is done by {@link #findApplicationContext}.
 * The implementation in this class obtains the {@link ApplicationContext} from the
 * a {@link Springleton} bean which must be declared within it.
 *
 * <p>
 * In addition, your {@link ApplicationContext} may need to have a bean with an {@link EnableVaadin &#64;EnableVaadin}
 * annotation, in order to bring in other beans required by Vaadin Spring, such as {@link VaadinScopesConfig}.
 *
 * <p>
 * You can accomplish both of these tasks by including a class like this in your application context:
 *
 * <pre><code class="language-java">
 * import com.vaadin.flow.spring.annotation.EnableVaadin;
 * import org.dellroad.stuff.spring.Springleton;
 *
 * &#64;EnableVaadin
 * public class MySpringleton extends Springleton {
 *
 *     public static MySpringleton getInstance() {
 *         return (MySpringleton)Springleton.getInstance();
 *     }
 * }
 * </code></pre>
 *
 * <p>
 * You may also need to include an {@link AppShellConfigurator} bean with the appropriate application-wide annotations.
 *
 * <p>
 * The {@link ApplicationContext} can be created however you want; traditionally, this is done via {@link ContextLoaderListener}.
 *
 * <p>
 * Note: Unlike {@link com.vaadin.flow.spring.SpringServlet}, this servlet does not pull servlet parameters like {@code FooBar}
 * from corresponding {@code vaadin.foo_bar} variables found in the {@link ApplicationContext}'s {@link Environment}.
 *
 * <p>
 * The servlet associated with the current thread can be found via {@link #forCurrentSession}.
 *
 * <p>
 * Additional supported servlet parameters:
 * <div style="margin-left: 20px;">
 * <table border="1" cellpadding="3" cellspacing="0" summary="Servlet URL Parameters">
 * <tr style="bgcolor:#ccffcc">
 *  <th align="left">Parameter Name</th>
 *  <th align="left">Required?</th>
 *  <th align="left">Description</th>
 * </tr>
 * <tr>
 * <td>{@code sessionTracking}</td>
 * <td align="center">No</td>
 * <td>
 *  Boolean value that configures whether to track Vaadin sessions; default {@code false}.
 *  If set to {@code true}, then {@link #getSessions} can be used to access all active sessions.
 *  Session tracking should not be used unless sessions are normally kept in memory; e.g., don't use session tracking
 *  when sessions are being serialized and persisted, or when this servlet may be serialized.
 *  See also {@link VaadinSessionDataProvider}.
 * </td>
 * </tr>
 * <tr>
 * <td>{@code maxSessions}</td>
 * <td align="center">No</td>
 * <td>
 *  Configures a limit on the number of simultaneous Vaadin sessions that may exist at one time. Going over this
 *  limit will result in a {@link ServiceException} being thrown. A zero or negative number
 *  means there is no limit (this is the default).
 * </td>
 * </tr>
 * </table>
 * </div>
 *
 * @see VaadinSessionDataProvider
 * @see <a href="https://github.com/vaadin/spring/issues/560">vaadin-spring issue #560</a>
 */
public class SimpleSpringServlet extends VaadinServlet {

    /**
     * Servlet initialization parameter (<code>{@value #SESSION_TRACKING_PARAMETER}</code>) that enables
     * tracking of all Vaadin session.
     *
     * <p>
     * This parameter is optional, and defaults to <code>false</code>.
     */
    public static final String SESSION_TRACKING_PARAMETER = "sessionTracking";

    /**
     * Servlet initialization parameter (<code>{@value #MAX_SESSIONS_PARAMETER}</code>) that configures the
     * maximum number of simultaneous Vaadin sessions. Requires {@link #SESSION_TRACKING_PARAMETER} to be set to {@code true}.
     * This parameter is optional, and defaults to zero, which means no limit.
     */
    public static final String MAX_SESSIONS_PARAMETER = "maxSessions";

    private static final long serialVersionUID = 2837283478619038364L;

    private transient AtomicInteger sessionCounter = new AtomicInteger();

    // We use weak references here to avoid possible Vaadin bugs
    private transient WeakHashMap<VaadinSession, Void> liveSessions = new WeakHashMap<>();

// Methods

    /**
     * Get the current count of active {@link VaadinSession}s associated with this instance.
     *
     * @return current number of sessions
     */
    public int getSessionCount() {
        return this.sessionCounter.get();
    }

    /**
     * Get all live {@link VaadinSession}s associated with this servlet.
     *
     * @return live tracked sessions, or an empty collection if session tracking is not enabled
     * @see VaadinSessionDataProvider
     * @throws IllegalStateException if this servlet instance has been serialized
     */
    public List<VaadinSession> getSessions() {
        if (this.liveSessions == null)
            throw new IllegalStateException("servlet was serialized");
        synchronized (this.liveSessions) {
            return this.liveSessions.keySet().stream()
              .filter(Objects::nonNull)                     // probably not needed, but just in case
              .collect(Collectors.toList());
        }
    }

    /**
     * Find the root {@link ApplicationContext} to be used by this servlet.
     *
     * <p>
     * The implementation in {@link SimpleSpringServlet} delegates to {@link Springleton#getApplicationContext}.
     *
     * @return the {@link ApplicationContext} for this servlet to use
     * @throws IllegalStateException if no application context can be found or has been established yet
     */
    protected ApplicationContext findApplicationContext() {
        return Springleton.getInstance().getApplicationContext();
    }

// VaadinServlet

    @Override
    protected VaadinServletService createServletService(DeploymentConfiguration deploymentConfiguration) throws ServiceException {

        // Get session tracking parameters
        final Properties params = deploymentConfiguration.getInitParameters();
        final boolean sessionTracking = SimpleSpringServlet.isSessionTracking(params);
        final int maxSessions = SimpleSpringServlet.getMaxSessions(params);

        // Get application context
        final ApplicationContext context = this.findApplicationContext();

        // Return a VaadinServletService that tracks sessions (if enabled)
        @SuppressWarnings("serial")
        final VaadinServletService service = new SpringVaadinServletService(this, deploymentConfiguration, context) {

            @Override
            protected VaadinSession createVaadinSession(VaadinRequest request) {

                // Increment counter
                final int counterCount = SimpleSpringServlet.this.sessionCounter.incrementAndGet();

                // Check max number of sessions
                if (maxSessions > 0 && counterCount > maxSessions) {
                    SimpleSpringServlet.this.sessionCounter.decrementAndGet();
                    throw new RuntimeException("The maximum number of active sessions (" + maxSessions + ") has been reached");
                }

                // Create new session
                final VaadinSession session = super.createVaadinSession(request);

                // Track session, if enabled
                if (sessionTracking && SimpleSpringServlet.this.liveSessions != null) {
                    synchronized (SimpleSpringServlet.this.liveSessions) {
                        SimpleSpringServlet.this.liveSessions.put(session, null);
                    }
                }

                // Done
                return session;
            }

            @Override
            public void fireSessionDestroy(VaadinSession session) {

                // Decrement counter
                SimpleSpringServlet.this.sessionCounter.decrementAndGet();

                // Untrack ssession, if enabled
                if (sessionTracking && SimpleSpringServlet.this.liveSessions != null) {
                    synchronized (SimpleSpringServlet.this.liveSessions) {
                        SimpleSpringServlet.this.liveSessions.remove(session);
                    }
                }

                // Proceed
                super.fireSessionDestroy(session);
            }
        };
        service.init();
        return service;
    }

    /**
     * Get the {@link SimpleSpringServlet} that is associated with the given {@link VaadinSession}.
     *
     * @param session Vaadin session
     * @return the assocated {@link SimpleSpringServlet}
     * @throws IllegalStateException if the {@link VaadinServlet} associated with {@code session} is not a
     *  {@link SimpleSpringServlet}
     * @throws IllegalArgumentException if {@code session} is null
     */
    public static SimpleSpringServlet forSession(VaadinSession session) {
        if (session == null)
            throw new IllegalArgumentException("null session");
        if (!(session.getService() instanceof VaadinServletService))
            throw new IllegalStateException("the VaadinService associated with the session is not a VaadinServletService instance");
        final VaadinServletService service = (VaadinServletService)session.getService();
        if (!(service.getServlet() instanceof SimpleSpringServlet))
            throw new IllegalStateException("the VaadinServlet associated with the session is not a SimpleSpringServlet instance");
        return (SimpleSpringServlet)service.getServlet();
    }

    /**
     * Get the {@link SimpleSpringServlet} that is associated with the current {@link VaadinSession}.
     *
     * @return the {@link SimpleSpringServlet} associated with the current thread
     * @throws IllegalStateException if there is no {@link VaadinSession} associated with the current thread
     * @throws IllegalStateException if the current {@link VaadinServlet} is not a {@link SimpleSpringServlet}
     */
    public static SimpleSpringServlet forCurrentSession() {
        return SimpleSpringServlet.forSession(VaadinUtil.getCurrentSession());
    }

// Utility methods

    private static boolean isSessionTracking(Properties params) {
        return Boolean.valueOf(params.getProperty(SESSION_TRACKING_PARAMETER));
    }

    private static int getMaxSessions(Properties params) {
        try {
            return Integer.parseInt(params.getProperty(MAX_SESSIONS_PARAMETER));
        } catch (Exception e) {
            return 0;
        }
    }

// Serialization

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    // If we are (de)serialized, we just forget all the sessions
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        this.sessionCounter = new AtomicInteger();
        this.liveSessions = new WeakHashMap<>();
    }
}
