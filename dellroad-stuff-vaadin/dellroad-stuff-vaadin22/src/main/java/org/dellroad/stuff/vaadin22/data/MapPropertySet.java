
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.data;

import com.vaadin.flow.data.binder.PropertyDefinition;
import com.vaadin.flow.data.binder.PropertySet;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.function.ValueProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Straightforward implementation of {@link PropertySet} supporting property values stored in a {@link Map}.
 *
 * <p>
 * Does not support sub-properties.
 */
@SuppressWarnings("serial")
public class MapPropertySet implements PropertySet<Map<String, Object>> {

    private final Map<String, Definition<?>> propertyMap = new LinkedHashMap<>();

    @Override
    public Stream<PropertyDefinition<Map<String, Object>, ?>> getProperties() {
        return this.propertyMap.values().stream().map(x -> x);
    }

    @Override
    public Optional<PropertyDefinition<Map<String, Object>, ?>> getProperty(String name) {
        return Optional.ofNullable(this.propertyMap.get(name));
    }

    /**
     * Add a new property to this instance.
     *
     * @param name property name
     * @param type property type
     * @param caption property caption
     * @return newly created property definition
     * @throws IllegalArgumentException if any parameter is null
     * @throws IllegalArgumentException if {@code name} has already been added
     */
    public <V> Definition<V> addPropertyDefinition(String name, Class<V> type, String caption) {
        final Definition<V> newDefinition = new Definition<V>(name, type, caption);
        final Definition<?> oldDefinition = this.propertyMap.putIfAbsent(name, newDefinition);
        if (oldDefinition != null)
            throw new IllegalArgumentException("duplicate name");
        return newDefinition;
    }

// Definition

    /**
     * A {@link PropertyDefinition} within a {@link MapPropertySet}.
     *
     * @param <V> property value type
     */
    @SuppressWarnings("serial")
    public class Definition<V> implements PropertyDefinition<Map<String, Object>, V> {

        private final String name;
        private final Class<V> type;
        private final String caption;

        public Definition(String name, Class<V> type, String caption) {
            if (name == null)
                throw new IllegalArgumentException("null name");
            if (type == null)
                throw new IllegalArgumentException("null type");
            if (caption == null)
                throw new IllegalArgumentException("null caption");
            this.name = name;
            this.type = type;
            this.caption = caption;
        }

        @Override
        public String getCaption() {
            return this.caption;
        }

        @Override
        public ValueProvider<Map<String, Object>, V> getGetter() {
            return this::get;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public PropertyDefinition<Map<String, Object>, ?> getParent() {
            return null;
        }

        @Override
        public Class<?> getPropertyHolderType() {
            return Map.class;
        }

        @Override
        public PropertySet<Map<String, Object>> getPropertySet() {
            return MapPropertySet.this;
        }

        @Override
        public Optional<Setter<Map<String, Object>, V>> getSetter() {
            return Optional.of(this::set);
        }

        @Override
        public Class<V> getType() {
            return this.type;
        }

        private V get(Map<String, Object> map) {
            if (map == null)
                throw new IllegalArgumentException("null map");
            try {
                return this.type.cast(map.get(this.name));
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("wrong type for \"" + this.name + "\" in map", e);
            }
        }

        private void set(Map<String, Object> map, V value) {
            if (map == null)
                throw new IllegalArgumentException("null map");
            try {
                map.put(this.name, this.type.cast(value));
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("wrong type for \"" + this.name + "\" in map", e);
            }
        }
    }
}
