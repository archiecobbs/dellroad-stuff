
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Straightforward {@link PropertySet} implementation using {@link SimplePropertyDefinition}s.
 */
public class SimplePropertySet<T> implements PropertySet<T> {

    private static final long serialVersionUID = 4983663265225248973L;

    private final Class<T> type;
    private final HashMap<String, PropertyDefinition<T, ?>> defs = new HashMap<>();

    /**
     * Constructor.
     *
     * @param type type containing the properties
     */
    public SimplePropertySet(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        this.type = type;
    }

    /**
     * Get the associated type.
     *
     * @return type containing the properties
     */
    public Class<T> getType() {
        return this.type;
    }

    /**
     * Add a new property.
     *
     * @param type type of property
     * @param name name of property
     * @param caption property caption
     * @param getter property getter
     * @param setter property setter, or null for none
     * @param <V> property type
     * @return newly created property definition
     * @throws IllegalArgumentException if any parameter other than {@code setter} is null
     * @throws IllegalArgumentException if a property named {@code name} already exists
     */
    public <V> SimplePropertyDefinition<T, V> add(Class<V> type, String name, String caption, Method getter, Method setter) {
        final SimplePropertyDefinition<T, V> def = new SimplePropertyDefinition<>(this, type, name, caption, getter, setter);
        if (this.defs.containsKey(name))
            throw new IllegalArgumentException("property `" + name + "' already exists");
        this.defs.put(name, def);
        return def;
    }

    /**
     * Get all property names.
     *
     * @return all property names
     */
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(this.defs.keySet());
    }

    @Override
    public Stream<PropertyDefinition<T, ?>> getProperties() {
        return this.defs.values().stream();
    }

    @Override
    public Optional<PropertyDefinition<T, ?>> getProperty(String name) {
        final PropertyDefinition<T, ?> def = this.defs.get(name);
        return def != null ? Optional.of(def) : Optional.empty();
    }

// Object

    @Override
    public String toString() {
        return this.defs.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final SimplePropertySet<?> that = (SimplePropertySet<?>)obj;
        return this.defs.equals(that.defs);
    }

    @Override
    public int hashCode() {
        return this.defs.hashCode();
    }
}

