
/*
 * Copyright (C) 2012 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.stuff.pobj;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that annotated methods should execute within {@link PersistentObjectTransactionManager} transactions.
 * During the execution, the method has private, atomic access to the associated database.
 *
 * <p>
 * When the associated AOP aspect is applied, annotated methods are executed within a {@link PersistentObjectTransactionManager}
 * transaction. In normal mode, the transaction simply executes in the current thread. In "Disruptor" mode, the
 * annotated method executes on the dedicated transaction thread, and the thread that invoked the method will block
 * until the transaction completes (unless specified as {@link #async}); return values and thrown exceptions are
 * delivered back to the calling thread as they normally would be.
 * </p>
 *
 * <p>
 * The associated AOP aspect must be configured with the {@link PersistentObjectTransactionManager}(s) to be
 * used for these transactional methods. The easiest way to do this is by declaring the aspect in your Spring
 * bean factory, for example:
 * <pre>
 *  &lt;bean class="org.dellroad.stuff.pobj.PersistentObjectTransactionalAspect" factory-method="aspectOf"
 *      p:persistentObjectTransactionManager-ref="myManagerBean"/&gt;
 * </pre>
 * </p>
 *
 * <p>
 * More than one {@link PersistentObjectTransactionManager}s may be configured. For example:
 * <pre>
 *  &lt;bean class="org.dellroad.stuff.pobj.PersistentObjectTransactionalAspect" factory-method="aspectOf"&gt;
 *      &lt;property name="persistentObjectTransactionManagers"&gt;
 *          &lt;util:list&gt;
 *              &lt;bean ref="fooManager"/&gt;
 *              &lt;bean ref="barManager"/&gt;
 *          &lt;/util:list&gt;
 *      &lt;/property&gt;
 *  &lt;/bean&gt;
 * </pre>
 * In the case of multiple {@link PersistentObjectTransactionManager}s, which {@link PersistentObjectTransactionManager}
 * the aspect uses is determined by the {@link #managerName} annotation property, which must equal the
 * {@linkplain PersistentObjectTransactionManager#getName name} of the desired {@link PersistentObjectTransactionManager}, e.g.:
 * <pre>
 *
 *  &#64;PersistentObjectTransactional(managerName = "fooManager")
 *  public void doSomething() {
 *      final Foo database = PersistentObjectTransactionManager.&lt;Foo&gt;getCurrent().getRoot();
 *      ...
 *  }
 * </pre>
 * </p>
 *
 * <p>
 * To apply the AOP aspect associated with this annotation, the annoated method's class must be woven (either at build
 * time or runtime) using the <a href="http://www.eclipse.org/aspectj/doc/released/faq.php#compiler">AspectJ compiler</a>
 * with the {@code PersistentObjectTransactionalAspect} aspect (included in the <code>dellroad-stuff</code> JAR file).
 * <p>
 *
 * @see PersistentObjectTransactionManager
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PersistentObjectTransactional {

    /**
     * The {@linkplain PersistentObjectTransactionManager#getName name} of the desired
     * {@link PersistentObjectTransactionManager}. This property only need be set when more than one
     * {@link PersistentObjectTransactionManager} is configured with the aspect.
     */
    String managerName() default "";

    /**
     * Whether a read-only transaction should be performed; otherwise, the transaction is read-write.
     */
    boolean readOnly() default false;

    /**
     * Whether a read-only transaction should access the {@linkplain PersistentObject#getSharedRoot shared root}.
     * In shared mode, copies are avoided entirely but the {@link PersistentObject} root object graph must not be
     * modified, either by the annotated method, or by any caller to whom access is granted after the transaction
     * ends by virtue of a returned reference.
     *
     * <p>
     * If {@link #readOnly} is false, this property is ignored.
     * </p>
     */
    boolean shared() default false;

    /**
     * Whether the transaction should execute asynchronously when in "Disruptor" mode.
     *
     * <p>
     * Although all transactions are executed by the dedicated {@link PersistentObjectTransactionManager} transaction thread,
     * by default {@link PersistentObjectTransactional @PersistentObjectTransactional}-annotated methods will block until the
     * transaction completes, allowing the return value from the transaction to be passed back to the caller.
     * </p>
     *
     * <p>
     * Setting {@link #async} to true forces the method to return immediately, so that the transaction runs asynchronously.
     * Such methods must return {@code void}. If an exception is thrown by an asynchronous transaction, it will be logged
     * as an error and then discarded.
     * </p>
     *
     * <p>
     * Setting this property to true is only valid when the associated {@link PersistentObjectTransactionManager}
     * is operating in "Disruptor" mode. Otherwise, an {@link IllegalStateException} is thrown by the annotated method.
     * </p>
     */
    boolean async() default false;
}
