
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

/**
 * A hack to workaround a stupid JLS restriction which requires {@code super()} be the first statement in a constructor.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * The JLS should permit constructors to perform arbitrary non-{@code this} and/or assignment-to-final-field
 * operations before invoking {@code super()} (which is what the JVM allows). Because this is currently disallowed,
 * a class that invokes a superclass constructor that invokes an overridden method can't guarantee that final fields
 * will be actually be initialized before use.
 *
 * <p>
 * For example, this class will throw a {@link NullPointerException} if a non-empty collection is provided
 * to the constructor, because the superclass constructor invokes {@code this.add()} before {@code this.filter}
 * has been initialized:
 *
 * <pre><code class="language-java">
 * import java.util.*;
 * import java.util.concurrent.*;
 * import java.util.function.*;
 *
 * public class FilteredDelayQueue&lt;E extends Delayed&gt; extends DelayQueue&lt;E&gt; {
 *
 *     private final Predicate&lt;? super E&gt; filter;
 *
 *     public FilteredDelayQueue(Predicate&lt;? super E&gt; filter) {
 *         this.filter = filter;
 *     }
 *
 *     // This constructor will throw a NullPointerException if "elems" is non-empty
 *     public FilteredDelayQueue(Predicate&lt;? super E&gt; filter, Collection&lt;? extends E&gt; elems) {
 *         super(elems);
 *         this.filter = filter;
 *     }
 *
 *     &#64;Override
 *     public boolean add(E elem) {
 *         if (!this.filter.test(elem))      // NullPointerException can happen here
 *             throw new IllegalArgumentException("disallowed element");
 *         return super.add(elem);
 *     }
 * }
 * </code></pre>
 *
 * <p>
 * To work around the problem, you can use {@link SuperFinal} to "stash" {@code this.filter} to make it accessible during
 * the superclass constructor.
 *
 * <pre><code class="language-java">
 * import java.util.*;
 * import java.util.concurrent.*;
 * import java.util.function.*;
 *
 * public class FilteredDelayQueue&lt;E extends Delayed&gt; extends DelayQueue&lt;E&gt; {
 *
 *     private final Predicate&lt;? super E&gt; filter;
 *
 *     public FilteredDelayQueue(Predicate&lt;? super E&gt; filter) {
 *         this.filter = filter;
 *     }
 *
 *     public FilteredDelayQueue(Predicate&lt;? super E&gt; filter, Collection&lt;? extends E&gt; elems) {
 *         super(SuperFinal.stash(filter, elems));
 *         SuperFinal.clear();
 *         this.filter = filter;
 *     }
 *
 *     &#64;Override
 *     public boolean add(E elem) {
 *         if (!SuperFinal.retrieve(this.filter)).test(elem))
 *             throw new IllegalArgumentException("disallowed element");
 *         return super.add(elem);
 *     }
 * }
 * </code></pre>
 *
 * Limitations:
 *  <ul>
 *      <li>Only works for non-null fields
 *      <li>Can't be used by more than one class at a time
 *  </ul>
 *
 * @see <a href="https://mail.openjdk.org/pipermail/compiler-dev/2014-March/008563.html">"JLS Tweaks"
 *  rant on the compiler-dev mailing list</a>
 */
public final class SuperFinal {

    private static final ThreadLocal<Object> VALUE = new ThreadLocal<>();

    private SuperFinal() {
    }

    /**
     * Stash the given value for later retrieval by {@link #retrieve retrieve()} in the current thread.
     *
     * <p>
     * This method is intended to be used as a parameter within a {@code this()} or {@code super()} invocation;
     * it returns {@code param}.
     *
     * <p>
     * Caller should ensure {@link #clear clear()} is invoked to clear the stashed value when done.
     *
     * @param value the value to stash, must not be null
     * @param param arbitrary return value
     * @return {@code param}
     * @throws IllegalArgumentException if {@code value} is null
     * @throws IllegalStateException if there is already a value stashed
     */
    public static <P> P stash(Object value, P param) {
        if (value == null)
            throw new IllegalArgumentException("null value");
        if (VALUE.get() != null)
            throw new IllegalStateException("value already stashed");
        VALUE.set(value);
        return param;
    }

    /**
     * Retrieve the value previously stashed by {@link #stash stash}.
     *
     * @return the value previously stashed by {@link #stash stash()}, never null
     * @throws IllegalStateException if there is no value currently stashed by the current thread
     */
    @SuppressWarnings("unchecked")
    public static <V> V retrieve() {
        final V value = (V)VALUE.get();
        if (value == null)
            throw new IllegalStateException("no stashed value");
        return value;
    }

    /**
     * Retrieve the value previously stashed by {@link #stash stash}, but only if necessary.
     *
     * <p>
     * If {@code value} is not null, then this method returns {@code value}; otherwise,
     * it returns the stashed value.
     *
     * @param value the desired value, or null if not yet initialized
     * @return {@code value} if non-null, otherwise the non-null value previously stashed by {@link #stash stash()}
     * @throws IllegalStateException if {@code value} is null and there is no value currently stashed by the current thread
     */
    @SuppressWarnings("unchecked")
    public static <V> V retrieve(V value) {
        return value != null ? value : SuperFinal.retrieve();
    }

    /**
     * Discard any value previously saved by {@link #stash stash} in the current thread.
     */
    public static void clear() {
        VALUE.remove();
    }
}
