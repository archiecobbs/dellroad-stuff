
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.util;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * A map with non-zero {@code long} keys.
 *
 * <p>
 * This implementation uses {@code long[]} arrays and open addressing to minimize memory overhead.
 * Equivalent {@link Map} methods taking {@code long} instead of {@code Long} are also provided.
 *
 * <p>
 * Instances do not accept zero values and are not thread safe.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Open_addressing">Open addressing</a>
 */
public class LongMap<V> extends AbstractMap<Long, V> implements Cloneable, Serializable {

    // Algorithm described here: http://en.wikipedia.org/wiki/Open_addressing

    private static final long serialVersionUID = -4931628136892145403L;

    private static final float EXPAND_THRESHOLD = 0.70f;    // expand array when > 70% full
    private static final float SHRINK_THRESHOLD = 0.25f;    // shrink array when < 25% full

    private static final int MIN_LOG2_LENGTH = 4;           // minimum array length = 16 slots
    private static final int MAX_LOG2_LENGTH = 30;          // maximum array length = 1 billion slots

    private long[] keys;                                    // has length always a power of 2
    private V[] values;                                     // will be null if we are being used to implement LongSet
    private int size;                                       // the number of entries in the map
    private int log2len;                                    // log2 of keys.length and values.length (if not null)
    private int upperSizeLimit;                             // size threshold when to grow array
    private int lowerSizeLimit;                             // size threshold when to shrink array
    private int numHashShifts;                              // used by hash() function

    private /*final*/ AtomicInteger modcount = new AtomicInteger();

// Constructors

    /**
     * Constructs an empty instance.
     */
    public LongMap() {
        this(0, true);
    }

    /**
     * Constructs an instance with the given initial capacity.
     *
     * @param capacity initial capacity
     * @throws IllegalArgumentException if {@code capacity} is negative
     */
    public LongMap(int capacity) {
        this(capacity, true);
    }

    /**
     * Constructs an instance initialized from the given map.
     *
     * @param map initial contents for this instance
     * @throws NullPointerException if {@code map} is null
     * @throws IllegalArgumentException if {@code map} contains a null key
     */
    public LongMap(Map<? extends Number, ? extends V> map) {
        this(map.size(), true);
        for (Map.Entry<? extends Number, ? extends V> entry : map.entrySet()) {
            final Number key = entry.getKey();
            if (key == null)
                throw new IllegalArgumentException("map contains null key");
            this.put(key.longValue(), entry.getValue());
        }
    }

    // Internal constructor
    LongMap(int capacity, boolean withValues) {
        if (capacity < 0)
            throw new IllegalArgumentException("capacity < 0");
        capacity &= 0x3fffffff;                                                 // avoid integer overflow from large values
        capacity = (int)(capacity / EXPAND_THRESHOLD);                          // increase to account for overhead
        capacity = Math.max(1, capacity);                                       // avoid zero, on which the next line fails
        this.log2len = 32 - Integer.numberOfLeadingZeros(capacity - 1);         // round up to next power of 2
        this.log2len = Math.max(MIN_LOG2_LENGTH, this.log2len);                 // clip to bounds
        this.log2len = Math.min(MAX_LOG2_LENGTH, this.log2len);
        this.createArrays(withValues);
    }

// Methods

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public boolean containsKey(Object obj) {
        if (!(obj instanceof Long))
            return false;
        return this.containsKey(((Long)obj).longValue());
    }

    public boolean containsKey(long key) {
        if (key == 0)
            return false;
        final int slot = this.findSlot(key);
        if (this.keys[slot] == key)
            return true;
        assert this.keys[slot] == 0;
        return false;
    }

    @Override
    public V get(Object obj) {
        if (!(obj instanceof Long))
            return null;
        return this.get(((Long)obj).longValue());
    }

    public V get(long key) {
        if (key == 0)
            return null;
        final int slot = this.findSlot(key);
        if (this.keys[slot] == key)
            return this.values != null ? this.values[slot] : null;
        assert this.keys[slot] == 0;
        return null;
    }

    @Override
    public V put(Long key, V value) {
        if (key == null)
            throw new IllegalArgumentException("null key");
        return this.put(key.longValue(), value);
    }

    public V put(long key, V value) {
        if (key == 0)
            throw new IllegalArgumentException("key is zero");
        return this.insert(key, value);
    }

    @Override
    public V remove(Object obj) {
        if (!(obj instanceof Long))
            return null;
        return this.remove(((Long)obj).longValue());
    }

    public V remove(long key) {
        if (key == 0)
            return null;
        return this.exsert(key);
    }

    @Override
    public void clear() {
        this.log2len = MIN_LOG2_LENGTH;
        this.createArrays(this.values != null);
        this.size = 0;
        this.modcount.incrementAndGet();
    }

    @Override
    public LongSet keySet() {
        return new LongSet(this);
    }

    @Override
    public Set<Map.Entry<Long, V>> entrySet() {
        return new EntrySet();
    }

    /**
     * Remove a single, arbitrary entry from this instance and return it.
     *
     * @return the removed entry, or null if this instance is empty
     */
    public Map.Entry<Long, V> removeOne() {
        return this.removeOne(this.modcount.get() * 11171);
    }

    private Map.Entry<Long, V> removeOne(final int offset) {
        if (this.size == 0)
            return null;
        final int mask = (1 << this.log2len) - 1;
        for (int i = 0; i < this.keys.length; i++) {
            final int slot = (offset + i) & mask;
            if (LongMap.this.keys[slot] != 0) {
                final Entry entry = new Entry(slot);
                this.exsert(slot);
                return entry;
            }
        }
        return null;
    }

    /**
     * Produce a debug dump of this instance's keys.
     */
    String debugDump() {
        final StringBuilder buf = new StringBuilder();
        buf.append("LONGMAP: size=" + this.size + " len=" + this.keys.length + " modcount=" + this.modcount.get());
        for (int i = 0; i < this.keys.length; i++)
            buf.append('\n').append(String.format(" [%2d] %016x (hash %d)", i, this.keys[i], this.hash(this.keys[i])));
        return buf.toString();
    }

// Object

    @Override
    public int hashCode() {
        return this.entrySet().hashCode();              // this is more efficient than what superclass does
    }

// Cloneable

    @Override
    @SuppressWarnings("unchecked")
    public LongMap<V> clone() {
        final LongMap<V> clone;
        try {
            clone = (LongMap<V>)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        clone.keys = clone.keys.clone();
        clone.modcount = new AtomicInteger(clone.modcount.get());
        if (clone.values != null)
            clone.values = clone.values.clone();
        return clone;
    }

    /**
     * Perform a "deep clone" operation, which is a normal {@link #clone} followed by a cloning of each
     * value using the provided clone function.
     *
     * @param valueCloner used to clone the individual values in this map
     * @return a deeply cloned copy of this instance
     * @throws IllegalArgumentException if {@code valueCloner} is null
     */
    public LongMap<V> deepClone(UnaryOperator<V> valueCloner) {
        if (valueCloner == null)
            throw new IllegalArgumentException("null valueCloner");
        final LongMap<V> clone = this.clone();
        if (clone.values != null) {
            for (int i = 0; i < clone.values.length; i++) {
                final V value = clone.values[i];
                if (value != null)
                    clone.values[i] = valueCloner.apply(value);
            }
        }
        return clone;
    }

// Package methods

    long[] getKeys() {
        return this.keys;
    }

    V getValue(int slot) {
        return this.values[slot];
    }

    void setValue(int slot, V value) {
        this.values[slot] = value;
    }

    Long[] toKeysArray() {
        final Long[] array = new Long[this.size];
        int index = 0;
        for (int slot = 0; slot < this.keys.length; slot++) {
            final long value = this.keys[slot];
            if (value != 0)
                array[index++] = value;
        }
        return array;
    }

    long[] toKeysLongArray() {
        final long[] array = new long[this.size];
        int index = 0;
        for (int slot = 0; slot < this.keys.length; slot++) {
            final long value = this.keys[slot];
            if (value != 0)
                array[index++] = value;
        }
        return array;
    }

// Internal methods

    private V insert(long key, V value) {

        // Find slot for key
        assert key != 0;
        assert this.values != null || value == null;
        final int slot = this.findSlot(key);

        // Key already exists? Just replace value
        if (this.keys[slot] == key) {
            if (this.values == null)
                return null;
            final V prev = this.values[slot];
            this.values[slot] = value;
            return prev;
        }

        // Insert new key/value pair
        assert this.keys[slot] == 0;
        this.keys[slot] = key;
        if (this.values != null) {
            assert this.values[slot] == null;
            this.values[slot] = value;
        }

        // Expand if necessary
        if (++this.size > this.upperSizeLimit && this.log2len < MAX_LOG2_LENGTH) {
            this.log2len++;
            this.resize();
        }
        this.modcount.incrementAndGet();
        return null;
    }

    private V exsert(long key) {

        // Find slot for key
        final int slot = this.findSlot(key);
        if (this.keys[slot] == 0) {
            assert this.values == null || this.values[slot] == null;
            return null;
        }
        assert this.keys[slot] == key;

        // Remove key
        return this.exsert(slot);
    }

    private V exsert(final int slot) {

        // Sanity check
        assert this.keys[slot] != 0;
        final V ovalue = this.values != null ? this.values[slot] : null;

        // Remove key/value pair and fixup subsequent entries
        int i = slot;                                                   // i points to the new empty slot
        int j = slot;                                                   // j points to the next slot to fixup
loop:   while (true) {
            this.keys[i] = 0;
            if (this.values != null)
                this.values[i] = null;
            long jkey;
            V jvalue;
            while (true) {
                j = (j + 1) & (this.keys.length - 1);
                jkey = this.keys[j];
                if (jkey == 0)                                          // end of hash chain, no more fixups required
                    break loop;
                jvalue = this.values != null ? this.values[j] : null;   // get corresponding value
                final int k = this.hash(jkey);                          // find where jkey's hash chain started
                if (i <= j ? (i < k && k <= j) : (i < k || k <= j))     // jkey is between i and j, so it's not cut off
                    continue;
                break;                                                  // jkey is cut off from its hash chain, need to fix
            }
            this.keys[i] = jkey;                                        // move jkey back into its hash chain
            if (this.values != null)
                this.values[i] = jvalue;
            i = j;                                                      // restart fixups at jkey's old location
        }

        // Shrink if necessary
        if (--this.size < this.lowerSizeLimit && this.log2len > MIN_LOG2_LENGTH) {
            this.log2len--;
            this.resize();
        }
        this.modcount.incrementAndGet();
        return ovalue;
    }

    private int findSlot(long value) {
        assert value != 0;
        int slot = this.hash(value);
        while (true) {
            final long existing = this.keys[slot];
            if (existing == 0 || existing == value)
                return slot;
            slot = (slot + 1) & (this.keys.length - 1);
        }
    }

    private int hash(long value) {
        final int shift = this.log2len;
        int hash = (int)value;
        for (int i = 0; i < this.numHashShifts; i++) {
            value >>>= shift;
            hash ^= (int)value;
        }
        return hash & (this.keys.length - 1);
    }

    private void resize() {

        // Grab a copy of old arrays and create new ones
        final long[] oldKeys = this.keys;
        final V[] oldValues = this.values;
        assert oldValues == null || oldValues.length == oldKeys.length;
        this.createArrays(oldValues != null);

        // Rehash key/value pairs from old array into new array
        for (int oldSlot = 0; oldSlot < oldKeys.length; oldSlot++) {
            final long key = oldKeys[oldSlot];
            if (key == 0) {
                assert oldValues == null || oldValues[oldSlot] == null;
                continue;
            }
            final int newSlot = this.findSlot(key);
            assert this.keys[newSlot] == 0;
            this.keys[newSlot] = key;
            if (this.values != null) {
                assert this.values[newSlot] == null;
                this.values[newSlot] = oldValues[oldSlot];
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void createArrays(boolean withValues) {
        assert this.log2len >= MIN_LOG2_LENGTH;
        assert this.log2len <= MAX_LOG2_LENGTH;
        final int arrayLength = 1 << this.log2len;
        this.lowerSizeLimit = this.log2len > MIN_LOG2_LENGTH ? (int)(SHRINK_THRESHOLD * arrayLength) : 0;
        this.upperSizeLimit = this.log2len < MAX_LOG2_LENGTH ? (int)(EXPAND_THRESHOLD * arrayLength) : arrayLength;
        this.numHashShifts = (64 + (this.log2len - 1)) / this.log2len;
        this.numHashShifts = Math.min(12, this.numHashShifts);
        this.keys = new long[arrayLength];
        if (withValues)
            this.values = (V[])new Object[arrayLength];
    }

// EntrySet

    class EntrySet extends AbstractSet<Map.Entry<Long, V>> {

        @Override
        public Iterator<Map.Entry<Long, V>> iterator() {
            return new EntrySetIterator();
        }

        @Override
        public int size() {
            return LongMap.this.size;
        }

        @Override
        public boolean contains(Object obj) {
            if (!(obj instanceof Map.Entry))
                return false;
            final Map.Entry<?, ?> entry = (Map.Entry<?, ?>)obj;
            final Object key = entry.getKey();
            final V actualValue = LongMap.this.get(key);
            if (actualValue == null && !LongMap.this.containsKey(key))
                return false;
            return entry.equals(new AbstractMap.SimpleEntry<Long, V>((Long)key, actualValue));
        }

        @Override
        public boolean remove(Object obj) {
            if (!(obj instanceof Map.Entry))
                return false;
            final Map.Entry<?, ?> entry = (Map.Entry<?, ?>)obj;
            final Object key = entry.getKey();
            final V actualValue = LongMap.this.get(key);
            if (actualValue == null && !LongMap.this.containsKey(key))
                return false;
            if (actualValue != null ? actualValue.equals(entry.getValue()) : entry.getValue() == null) {
                LongMap.this.remove(key);
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            LongMap.this.clear();
        }

        // This works because Long.hashCode() == Long.asLong().hashCode()
        @Override
        public int hashCode() {
            final long[] keyArray = LongMap.this.keys;
            final V[] valueArray = LongMap.this.values;
            int hash = 0;
            for (int i = 0; i < keyArray.length; i++) {
                final long key = keyArray[i];
                if (key != 0) {
                    int entryHash = (int)(key >>> 32) ^ (int)key;
                    if (valueArray != null) {
                        final V value = valueArray[i];
                        if (value != null)
                            entryHash ^= value.hashCode();
                    }
                    hash += entryHash;
                }
            }
            return hash;
        }
    }

// EntrySetIterator

    class EntrySetIterator implements Iterator<Map.Entry<Long, V>> {

        private int modcount = LongMap.this.modcount.get();
        private int removeSlot = -1;
        private int nextSlot;

        @Override
        public boolean hasNext() {
            return this.findNext(false) != -1;
        }

        @Override
        public Entry next() {
            final int slot = this.findNext(true);
            if (slot == -1)
                throw new NoSuchElementException();
            final long key = LongMap.this.keys[slot];
            assert key != 0;
            this.removeSlot = slot;
            return new Entry(slot);
        }

        @Override
        public void remove() {
            if (this.removeSlot == -1)
                throw new IllegalStateException();
            if (this.modcount != LongMap.this.modcount.get())
                throw new ConcurrentModificationException();
            LongMap.this.exsert(this.removeSlot);
            this.removeSlot = -1;
            this.modcount++;                            // keep synchronized with LongMap.this.modcount
        }

        private int findNext(boolean advance) {
            if (this.modcount != LongMap.this.modcount.get())
                throw new ConcurrentModificationException();
            for (int slot = this.nextSlot; slot < LongMap.this.keys.length; slot++) {
                if (LongMap.this.keys[slot] == 0)
                    continue;
                this.nextSlot = advance ? slot + 1 : slot;
                return slot;
            }
            return -1;
        }
    }

// Entry

    @SuppressWarnings("serial")
    class Entry implements Map.Entry<Long, V> {

        private final int modcount = LongMap.this.modcount.get();
        private final int slot;

        Entry(int slot) {
            this.slot = slot;
        }

        @Override
        public Long getKey() {
            if (this.modcount != LongMap.this.modcount.get())
                throw new ConcurrentModificationException();
            return LongMap.this.keys[this.slot];
        }

        @Override
        public V getValue() {
            if (this.modcount != LongMap.this.modcount.get())
                throw new ConcurrentModificationException();
            return LongMap.this.values[this.slot];
        }

        @Override
        public V setValue(V value) {
            if (this.modcount != LongMap.this.modcount.get())
                throw new ConcurrentModificationException();
            final V oldValue = LongMap.this.values[this.slot];
            LongMap.this.values[this.slot] = value;
            return oldValue;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof Map.Entry))
                return false;
            final Map.Entry<?, ?> that = (Map.Entry<?, ?>)obj;
            return Objects.equals(this.getKey(), that.getKey()) && Objects.equals(this.getValue(), that.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.getKey()) ^ Objects.hashCode(this.getValue());
        }
    }
}

