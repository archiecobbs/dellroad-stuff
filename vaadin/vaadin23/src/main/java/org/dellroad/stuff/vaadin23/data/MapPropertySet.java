
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.data;

import com.vaadin.flow.data.binder.PropertySet;
import com.vaadin.flow.shared.util.SharedUtil;

import java.util.Map;

/**
 * Straightforward implementation of {@link PropertySet} with property values stored in a {@link Map}.
 *
 * <p>
 * This class is useful for building arbitrary property sets.
 *
 * <p>
 * Does not support sub-properties.
 */
@SuppressWarnings("serial")
public class MapPropertySet extends SimplePropertySet<Map<String, Object>> {

    @SuppressWarnings("unchecked")
    public MapPropertySet() {
        super((Class<Map<String, Object>>)(Object)Map.class);
    }

// Methods

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
        return this.addPropertyDefinition(name, type, caption,
          map -> this.get(map, type, name), (map, value) -> this.set(map, type, name, value));
    }

    /**
     * Add a new property to this instance, deriving the caption automatically from the property name.
     *
     * <p>
     * Equivalent to: {@link #addPropertyDefinition(String, Class, String) addPropertyDefinition}{@code (name, type,
     *  }{@link SharedUtil#camelCaseToHumanFriendly SharedUtil.camelCaseToHumanFriendly}{@code (name))}.
     *
     * @param name property name
     * @param type property type
     * @return newly created property definition
     * @throws IllegalArgumentException if either parameter is null
     * @throws IllegalArgumentException if {@code name} has already been added
     */
    public <V> Definition<V> addPropertyDefinition(String name, Class<V> type) {
        return this.addPropertyDefinition(name, type, SharedUtil.camelCaseToHumanFriendly(name));
    }

    private <V> V get(Map<String, Object> map, Class<V> type, String name) {
        if (map == null)
            throw new IllegalArgumentException("null map");
        try {
            return type.cast(map.get(name));
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("wrong type for \"" + name + "\" in map", e);
        }
    }

    private <V> void set(Map<String, Object> map, Class<V> type, String name, V value) {
        if (map == null)
            throw new IllegalArgumentException("null map");
        try {
            map.put(name, type.cast(value));
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("wrong type for \"" + name + "\" in map", e);
        }
    }
}
