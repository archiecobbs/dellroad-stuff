
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.Callable;

/**
 * Registry of unique IDs for objects.
 *
 * <p>
 * Instances support creating unique {@code long} ID numbers for objects, as well as setting the unique ID
 * to a specific value for any unregistered object.
 *
 * <p>
 * This class uses object identity, not {@link Object#equals Object.equals()}, to distinguish objects.
 *
 * <p>
 * Weak references are used to ensure that registered objects can be garbage collected normally.
 *
 * <p>
 * New {@code long} ID numbers are issued serially; after 2<sup>64</sup>-1 invocations of {@link #getId getId()},
 * an {@link IllegalStateException} will be thrown.
 *
 * <p>
 * Instances are thread safe.
 *
 * @see org.dellroad.stuff.jibx.IdMapper
 */
public class IdGenerator {

    private static final ThreadLocalHolder<IdGenerator> CURRENT = new ThreadLocalHolder<>();

    private final HashMap<Ref, Long> idMap = new HashMap<>();
    private final HashMap<Long, Ref> refMap = new HashMap<>();
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    private long next = 1;

    /**
     * Get a unique ID for the given object.
     *
     * <p>
     * If this method has been previously invoked on this instance with the same {@code obj} parameter (where "same" means
     * object identity, not {@link Object#equals Object.equals()} identity), then the same ID value will be returned.
     * Otherwise a new ID value will be returned.
     *
     * <p>
     * New IDs are assigned sequentially starting at {@code 1}. No conflict avoidance with IDs assigned
     * via {@link #setId setId()} is performed; if there is a conflict, an exception is thrown.
     *
     * @param obj object to ID; must not be null
     * @throws IllegalArgumentException if {@code obj} is null
     * @throws IllegalStateException if the next sequential ID has already been assigned to a different object
     *  via {@link #setId setId()}
     * @throws IllegalStateException if all 2<sup>64</sup>-1 values have been used up
     * @return a non-zero, unique identifier for {@code obj}
     */
    public synchronized long getId(Object obj) {
        if (obj == null)
            throw new IllegalArgumentException("null obj");
        this.flush();
        Long id = this.idMap.get(new Ref(obj));         // don't register on queue just yet
        if (id == null) {
            if (this.next == 0)
                throw new IllegalStateException("no more identifiers left!");
            Ref ref = new Ref(obj, this.queue);             // now register on queue
            id = this.next++;
            this.idMap.put(ref, id);
            this.refMap.put(id, ref);
        }
        return id;
    }

    /**
     * Get the next ID to be assigned.
     *
     * <p>
     * This method does not actually assign the ID; it only returns the ID that would be assigned by the next
     * invocation of {@link #getId}.
     *
     * @return next available ID
     */
    public synchronized long nextId() {
        return this.next;
    }

    /**
     * Test whether the given object is already registered with this instance.
     *
     * @param obj object to test; must not be null
     * @throws IllegalArgumentException if {@code obj} is null
     * @return a non-zero, unique identifier for {@code obj} if already registered, otherwise zero
     */
    public synchronized long checkId(Object obj) {
        if (obj == null)
            throw new IllegalArgumentException("null obj");
        this.flush();
        Long id = this.idMap.get(new Ref(obj));
        return id != null ? id : 0;
    }

    /**
     * Assign a unique ID to the given object. Does nothing if the object and ID number are already associated.
     *
     * @param obj object to assign
     * @param id unique ID number to assign
     * @throws IllegalArgumentException if {@code obj} is null
     * @throws IllegalArgumentException if {@code id} has already been assigned to some other object
     */
    public synchronized void setId(Object obj, long id) {
        if (obj == null)
            throw new IllegalArgumentException("null obj");
        this.flush();
        Ref ref = this.refMap.get(id);
        if (ref != null) {
            if (ref.get() != obj)
                throw new IllegalArgumentException("id " + id + " is already assigned to another object");
            return;
        }
        ref = new Ref(obj, this.queue);
        this.idMap.put(ref, id);
        this.refMap.put(id, ref);
    }

    /**
     * Get the object assigned to the given ID.
     *
     * @param id unique ID
     * @return object associated with that ID, or null if no object is assigned to {@code id}
     */
    public synchronized Object getObject(long id) {
        this.flush();
        Ref ref = this.refMap.get(id);
        return ref != null ? ref.get() : null;
    }

    /**
     * Flush any cleared weak references.
     *
     * <p>
     * This operation is invoked by {@link #getId getId()} and {@link #setId setId()}, so it's usually not necessary
     * to explicitly invoke it. However, if a lot of previously ID'd objects have been garbage collected since the
     * last call to {@link #getId getId()}, then invoking this method may free up some additional memory.
     */
    public synchronized void flush() {
        Reference<? extends Object> entry;
        while ((entry = this.queue.poll()) != null) {
            Ref ref = (Ref)entry;
            Long id = this.idMap.get(ref);
            this.idMap.remove(ref);
            this.refMap.remove(id);
        }
    }

    /**
     * Create a new {@link IdGenerator} and make it available via {@link #get()} for the duration of the given operation.
     *
     * <p>
     * This method is re-entrant: nested invocations of this method in the same thread re-use the same {@link IdGenerator}
     * instance.
     *
     * @param action action to perform, and which may successfully invoke {@link #get}
     * @throws NullPointerException if {@code action} is null
     */
    public static void run(final Runnable action) {
        IdGenerator current = IdGenerator.CURRENT.get();
        if (current == null)
            current = new IdGenerator();
        IdGenerator.CURRENT.invoke(current, action);
    }

    /**
     * Create a new {@link IdGenerator} and make it available via {@link #get()} for the duration of the given operation.
     *
     * <p>
     * This method is re-entrant: nested invocations of this method in the same thread re-use the same {@link IdGenerator}
     * instance.
     *
     * @param <R> action return type
     * @param action action to perform, and which may successfully invoke {@link #get}
     * @return result of invoking {@code action}
     * @throws Exception if {@code action} throws an exception
     * @throws NullPointerException if {@code action} is null
     */
    public static <R> R run(final Callable<R> action) throws Exception {
        IdGenerator current = IdGenerator.CURRENT.get();
        if (current == null)
            current = new IdGenerator();
        return IdGenerator.CURRENT.invoke(current, action);
    }

    /**
     * Get the {@link IdGenerator} associated with the current thread.
     * This method only works when the current thread is running within an invocation of {@link #run run()};
     * otherwise, an {@link IllegalStateException} is thrown.
     *
     * @return the {@link IdGenerator} created in the most recent, still running invocation of {@link #run} in this thread
     * @throws IllegalStateException if there is not such instance
     */
    public static IdGenerator get() {
        return IdGenerator.CURRENT.require();
    }

// Reference to a registered object that weakly references the actual object

    private static final class Ref extends WeakReference<Object> {

        private final int hashCode;

        Ref(Object obj, ReferenceQueue<Object> queue) {
            super(obj, queue);
            if (obj == null)
                throw new IllegalArgumentException("null obj");
            this.hashCode = System.identityHashCode(obj);
        }

        Ref(Object obj) {
            this(obj, null);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            Ref that = (Ref)obj;
            obj = this.get();
            return obj != null ? obj == that.get() : false;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}

