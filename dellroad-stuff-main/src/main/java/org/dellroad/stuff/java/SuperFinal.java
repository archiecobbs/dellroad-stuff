
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.util.ArrayList;

/**
 * A hack to workaround a stupid JLS restriction which requires {@code super()} be the first statement in a constructor.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * The JLS should permit constructors to perform arbitrary non-{@code this} operations and assignments to final fields
 * before invoking {@code super()}; this is actually what the JVM allows. Because this is currently disallowed,
 * a class that invokes a superclass constructor that invokes an overridden method can't guarantee that its final fields
 * will be actually be initialized before they get used.
 *
 * <p>
 * For example, the following seemingly straightforward class will throw a {@link NullPointerException} if constructed
 * with a non-empty {@code elems}, because the superclass constructor invokes {@code this.add()} before {@code this.filter}
 * is initialized:
 *
 * <pre><code class="language-java">
 * import java.util.*;
 * import java.util.function.*;
 *
 * public class FilteredSet&lt;E&gt; extends HashSet&lt;E&gt; {
 *
 *     private final Predicate&lt;? super E&gt; filter;
 *
 *     public FilteredSet(Predicate&lt;? super E&gt; filter) {
 *         this.filter = filter;
 *     }
 *
 *     // BUG: throws NullPointerException if "elems" is non-empty!
 *     public FilteredSet(Predicate&lt;? super E&gt; filter, Collection&lt;? extends E&gt; elems) {
 *         super(elems);
 *         this.filter = filter;
 *     }
 *
 *     &#64;Override
 *     public boolean add(E elem) {
 *         if (!this.filter.test(elem))                         // NullPointerException thrown here!
 *             throw new IllegalArgumentException("disallowed element");
 *         return super.add(elem);
 *     }
 * }
 * </code></pre>
 *
 * <p>
 * To work around that problem, you could use this class to temporarily "stash" {@code this.filter}'s value
 * so that it's accessible during the superclass constructor's execution (before the field is initialized) if needed:
 *
 * <pre><code class="language-java">
 * import java.util.*;
 * import java.util.function.*;
 *
 * public class FilteredSet&lt;E&gt; extends HashSet&lt;E&gt; {
 *
 *     private final Predicate&lt;? super E&gt; filter;
 *
 *     public FilteredSet(Predicate&lt;? super E&gt; filter) {
 *         this.filter = filter;
 *     }
 *
 *     public FilteredSet(Predicate&lt;? super E&gt; filter, Collection&lt;? extends E&gt; elems) {
 *         super(SuperFinal.push(filter, elems));               // stash "filter" value while super() executes
 *         SuperFinal.pop();                                    // discard stashed value
 *         this.filter = filter;
 *     }
 *
 *     &#64;Override
 *     public boolean add(E elem) {
 *         if (!SuperFinal.get(this.filter)).test(elem))        // use stashed value here if needed
 *             throw new IllegalArgumentException("disallowed element");
 *         return super.add(elem);
 *     }
 * }
 * </code></pre>
 *
 * <p>
 * Stashed values are stored on a stack, so {@link #pop} must be invoked before the constructor returns to keep
 * the stack properly aligned. If multiple superclasses are stash values, things will work as long as only the top-most
 * value is accessed. In more complicated situations, you can specify the desired stack depth explicitly.
 *
 * @see <a href="https://mail.openjdk.org/pipermail/compiler-dev/2014-March/008563.html">"JLS Tweaks"
 *  rant on the compiler-dev mailing list</a>
 */
public final class SuperFinal {

    private static final ThreadLocal<ArrayList<Object>> STACK = new ThreadLocal<>();

    private SuperFinal() {
    }

    /**
     * Stash the given value for later retrieval by {@link #get get()} in the current thread.
     *
     * <p>
     * This method is intended to be used as a parameter within a {@code this()} or {@code super()} invocation;
     * it returns {@code param}.
     *
     * <p>
     * Caller should ensure {@link #pop pop()} is invoked to remove the stashed value
     * after {@code this()} or {@code super()} returns.
     *
     * @param value the value to stash
     * @param param arbitrary return value
     * @return {@code param}
     */
    public static <P> P push(Object value, P param) {
        if (STACK.get() == null)
            STACK.set(new ArrayList<>());
        STACK.get().add(value);
        return param;
    }

    /**
     * Retrieve the value previously stashed by {@link #push push} at position {@code depth} down from the stop of the stack.
     *
     * @param depth stack depth of desired value
     * @return the value previously stashed by {@link #push push()}
     * @throws IllegalStateException if there is no value currently stashed by the current thread at depth {@code depth}
     * @throws IllegalArgumentException if {@code depth} is negative
     */
    @SuppressWarnings("unchecked")
    public static <V> V get(int depth) {
        if (depth < 0)
            throw new IllegalArgumentException("depth < 0");
        final ArrayList<Object> stack = STACK.get();
        if (stack == null)
            throw new IllegalStateException("no stashed value at depth " + depth);
        final int stackSize = stack.size();
        if (depth >= stackSize)
            throw new IllegalStateException("no stashed value at depth " + depth);
        return (V)stack.get(stackSize - depth - 1);
    }

    /**
     * Retrieve the value previously stashed by {@link #push push}, but only if the given field value is null,
     * indicating the field is still uninitialized.
     *
     * <p>
     * If {@code value} is not null, then this method returns {@code value}; otherwise,
     * it returns the stashed value as if by invoking {@link #get(int) get}{@code (0)}. Therefore, it is
     * safe to invoke this method either before or after a final field's initialization, as long as that
     * field's eventual value is not null.
     *
     * <p>
     * This method is equivalent to {@link #get(int, Object) get}{@code (0, value)}.
     *
     * @param value the desired value, or null if not yet initialized
     * @return {@code value} if non-null, otherwise the non-null value previously stashed by {@link #push push()}
     * @throws IllegalStateException if {@code value} is null and there is no value currently stashed by the current thread
     */
    @SuppressWarnings("unchecked")
    public static <V> V get(V value) {
        return SuperFinal.get(0, value);
    }

    /**
     * Retrieve the value previously stashed by {@link #push push} at stack depth {@code depth}, but only if the given
     * field value is null, indicating the field is still uninitialized.
     *
     * <p>
     * If {@code value} is not null, then this method returns {@code value}; otherwise,
     * it returns the stashed value as if by invoking {@link #get(int) get}{@code (depth)}. Therefore, it is
     * safe to invoke this method either before or after a final field's initialization, as long as that
     * field's eventual value is not null.
     *
     * @param depth stack depth of desired value
     * @param value the desired value, or null if not yet initialized
     * @return {@code value} if non-null, otherwise the non-null value previously stashed by {@link #push push()}
     * @throws IllegalArgumentException if {@code depth} is negative
     * @throws IllegalStateException if {@code value} is null and there is no value at depth {@code depth}
     *  currently stashed by the current thread
     */
    @SuppressWarnings("unchecked")
    public static <V> V get(int depth, V value) {
        return value != null ? value : SuperFinal.get(depth);
    }

    /**
     * Discard any value previously saved by {@link #push push} in the current thread.
     *
     * @throws IllegalStateException if there is no value currently stashed by the current thread
     */
    public static void pop() {
        final ArrayList<Object> stack = STACK.get();
        if (stack == null || stack.isEmpty())
            throw new IllegalStateException("stack is empty");
        final int lastElementIndex = stack.size() - 1;
        stack.set(lastElementIndex, null);                      // avoid possible memory leak
        if (lastElementIndex > 0)
            stack.remove(lastElementIndex);
        else
            STACK.remove();
    }
}
