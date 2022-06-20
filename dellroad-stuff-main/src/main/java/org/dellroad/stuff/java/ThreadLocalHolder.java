
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.util.function.Supplier;

/**
 * Manages a thread local whose lifetime matches the duration of some method call.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * This class is useful for this common pattern:
 * <ul>
 *  <li>A thread local variable is instantiated by some initial method call and has an intended
 *      lifetime matching the duration of that method call;</li>
 *  <li>The thread local variable is accessible from some other nested method calls in the same thread,
 *      as long as the initial method call is still executing;</li>
 *  <li>The thread local variable is removed (and optionally cleaned up) when the initial method call exits,
 *      whether successfully or not.</li>
 * </ul>
 *
 * <p>
 * Example:
 * <pre><code class="language-java">
 * public class FooContext {
 *
 *     private static final ThreadLocalHolder&lt;FooContext&gt; CURRENT = new ThreadLocalHolder&lt;&gt;();
 *
 *     /**
 *      * Make this instance the current instance while performing the given activity.
 *      *
 *      * &#64;throws IllegalArgumentException if {&#64;code action} is null
 *      * &#64;throws IllegalStateException if there is already another, different current instance
 *      *&#47;
 *     public void doWhileCurrent(Runnable action) {
 *         CURRENT_ACTIVITY.invoke(this, action);
 *     }
 *
 *     /**
 *      * Get the current {&#64;link FooContext}.
 *      *
 *      * &#64;throws IllegalStateException if the current thread is not running within an invocation of {&#64;link #doWhileCurrent}
 *      *&#47;
 *     public static FooContext getCurrent() {
 *         return CURRENT.require();
 *     }
 * }
 * </code></pre>
 *
 * @param <T> the type of the thread local variable
 */
public class ThreadLocalHolder<T> {

    private final ThreadLocal<T> threadLocal;

    /**
     * Conveninece constructor.
     *
     * <p>
     * Equivalent to:
     *  <blockquote><code>new ThreadLocalHolder&lt;T&gt;(new ThreadLocal&lt;T&gt;())</code></blockquote>
     */
    public ThreadLocalHolder() {
        this(new ThreadLocal<T>());
    }

    /**
     * Primary constructor.
     *
     * @param threadLocal the thread local to use
     * @throws IllegalArgumentException if {@code threadLocal} is null
     */
    public ThreadLocalHolder(ThreadLocal<T> threadLocal) {
        if (threadLocal == null)
            throw new IllegalArgumentException("null threadLocal");
        this.threadLocal = threadLocal;
    }

    /**
     * Invoke the given action while making the given thread local variable available via {@link #get} and {@link #require}.
     *
     * <p>
     * If there is already a thread local variable set for the current thread (i.e., we are already executing within
     * an invocation of <code>ThreadLocalHolder.invoke()</code>), then if {@code value} is the exact same Java object
     * (using object equality, not <code>equals()</code>), execution proceeds normally, otherwise an exception is thrown.
     *
     * @param value value for the thread local variable
     * @param action action to invoke
     * @throws IllegalArgumentException if either {@code action} or {@code value} is null
     * @throws IllegalStateException if there is already a thread local variable <code>previous</code>
     *  associated with the current thread and <code>value != previous</code>
     */
    public void invoke(final T value, Runnable action) {
        if (action == null)
            throw new IllegalArgumentException("null action");
        this.<Void>invoke(value, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Invoke the given action while making the given thread local variable available via {@link #get} and {@link #require}.
     *
     * <p>
     * If there is already a thread local variable set for the current thread (i.e., we are already executing within
     * an invocation of <code>ThreadLocalHolder.invoke()</code>), then if {@code value} is the exact same Java object
     * (using object equality, not <code>equals()</code>), execution proceeds normally, otherwise an exception is thrown.
     *
     * @param <R> action return type
     * @param value value for the thread local variable
     * @param action action to invoke
     * @return result of invoking {@code action}
     * @throws IllegalArgumentException if either {@code action} or {@code value} is null
     * @throws IllegalStateException if there is already a thread local variable <code>previous</code>
     *  associated with the current thread and <code>value != previous</code>
     */
    public <R> R invoke(final T value, Supplier<R> action) {
        if (action == null)
            throw new IllegalArgumentException("null action");
        if (value == null)
            throw new IllegalArgumentException("null value");
        final T previousValue = this.threadLocal.get();
        final boolean topLevel = previousValue == null;
        if (!topLevel) {
            if (value != previousValue) {
                throw new IllegalStateException("already executing within an invocation of ThreadLocalHolder.invoke()"
                  + " but with a different value");
            }
        } else
            this.threadLocal.set(value);
        try {
            return action.get();
        } finally {
            if (topLevel) {
                this.threadLocal.remove();
                this.destroy(value);
            }
        }
    }

    /**
     * Get the thread local value associated with the current thread, if any.
     *
     * @return the current thread local variable value, or null if not executing
     *  within an invocation of <code>ThreadLocalHolder.invoke()</code>
     */
    public T get() {
        return this.threadLocal.get();
    }

    /**
     * Get the thread local value associated with the current thread; there must be one.
     *
     * @return the current thread local variable value, never null
     * @throws IllegalStateException if the current thread is not running
     *  within an invocation of <code>ThreadLocalHolder.invoke()</code>
     */
    public T require() {
        T value = this.threadLocal.get();
        if (value == null) {
            throw new IllegalStateException("no value associated with the current thread;"
              + " are we executing within an invocation of ThreadLocalHolder.invoke()?");
        }
        return value;
    }

    /**
     * Clean up the thread local value when no longer needed.
     *
     * <p>
     * The implementation in {@link ThreadLocalHolder} does nothing. Subclasses may override if necessary.
     *
     * @param value previously used thread-local value
     */
    protected void destroy(T value) {
    }
}
