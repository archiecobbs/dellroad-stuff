
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.util;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;

/**
 * A set of non-zero {@code long} values.
 *
 * <p>
 * This implementation uses {@code long[]} arrays and open addressing to minimize memory overhead.
 * Equivalent {@link java.util.Set} methods taking {@code long} instead of {@code Long} are also provided.
 *
 * <p>
 * Instances do not accept zero values and are not thread safe.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Open_addressing">Open addressing</a>
 */
public class LongSet extends AbstractSet<Long> implements Cloneable, Serializable {

    private static final long serialVersionUID = -8245070561628904936L;

    private /*final*/ LongMap<?> map;

// Constructors

    /**
     * Constructs an empty instance.
     */
    public LongSet() {
        this(0);
    }

    /**
     * Constructs an instance with the given initial capacity.
     *
     * @param capacity initial capacity
     * @throws IllegalArgumentException if {@code capacity} is negative
     */
    public LongSet(int capacity) {
        this(new LongMap<Void>(capacity, false));
    }

    /**
     * Constructs an instance initialized with the given values.
     *
     * @param values initial contents for this instance
     * @throws IllegalArgumentException if {@code values} is null
     * @throws IllegalArgumentException if any value in {@code values} is null
     * @throws IllegalArgumentException if any value in {@code values} is zero
     */
    public LongSet(Iterable<? extends Number> values) {
        this(0);
        if (values == null)
            throw new IllegalArgumentException("null values");
        for (Number value : values) {
            if (value == null)
                throw new IllegalArgumentException("null value");
            this.add(value.longValue());
        }
    }

    /**
     * Constructs an instance initialized with the values in the given array.
     *
     * @param values initial contents for this instance
     * @throws IllegalArgumentException if {@code values} is null
     * @throws NullPointerException if any value in {@code values} is zero
     */
    public LongSet(long[] values) {
        this(0);
        if (values == null)
            throw new IllegalArgumentException("null values");
        for (long value : values)
            this.add(value);
    }

    // Internal constructor
    LongSet(LongMap<?> map) {
        this.map = map;
    }

    /**
     * Remove a single, arbitrary {@link long} value from this instance and return it.
     *
     * @return the removed value, or zero if this instance is empty
     */
    public long removeOne() {
        final Map.Entry<Long, ?> entry = this.map.removeOne();
        return entry != null ? entry.getKey() : 0;
    }

// Methods

    @Override
    public Iterator<Long> iterator() {
        return new Iterator<Long>() {

            private final LongMap<?>.EntrySetIterator entryIterator = LongSet.this.map.new EntrySetIterator();

            @Override
            public boolean hasNext() {
                return this.entryIterator.hasNext();
            }

            @Override
            public Long next() {
                return this.entryIterator.next().getKey();
            }

            @Override
            public void remove() {
                this.entryIterator.remove();
            }
        };
    }

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public boolean contains(Object obj) {
        return this.map.containsKey(obj);
    }

    public boolean contains(long value) {
        return this.map.containsKey(value);
    }

    @Override
    public boolean add(Long value) {
        if (value == null)
            throw new IllegalArgumentException("null value");
        return this.add(value.longValue());
    }

    public boolean add(long value) {
        if (this.map.containsKey(value))
            return false;
        this.map.put(value, null);
        return true;
    }

    @Override
    public boolean remove(Object obj) {
        return obj instanceof Long && this.remove(((Long)obj).longValue());
    }

    public boolean remove(long value) {
        if (!this.map.containsKey(value))
            return false;
        this.map.remove(value);
        return true;
    }

    @Override
    public void clear() {
        this.map.clear();
    }

    @Override
    public Long[] toArray() {
        return this.map.toKeysArray();
    }

    public long[] toLongArray() {
        return this.map.toKeysLongArray();
    }

    /**
     * Produce a debug dump of this instance.
     */
    String debugDump() {
        return this.map.debugDump();
    }

// Object

    @Override
    public int hashCode() {
        final long[] keyArray = this.map.getKeys();
        int hash = 0;
        for (int i = 0; i < keyArray.length; i++) {
            final long key = keyArray[i];
            if (key != 0)
                hash += (int)(key >>> 32) ^ (int)key;
        }
        return hash;
    }

// Cloneable

    @Override
    public LongSet clone() {
        final LongSet clone;
        try {
            clone = (LongSet)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        clone.map = clone.map.clone();
        return clone;
    }
}

