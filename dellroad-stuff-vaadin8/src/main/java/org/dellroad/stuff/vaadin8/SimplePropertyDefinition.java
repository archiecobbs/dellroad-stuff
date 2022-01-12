
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;
import com.vaadin.data.ValueProvider;
import com.vaadin.server.Setter;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

import org.dellroad.stuff.java.ReflectUtil;
import org.dellroad.stuff.java.SerializableMethod;

/**
 * Straightforward {@link PropertyDefinition} implementation based on getter and setter methods.
 *
 * @param <T> backing object type
 * @param <V> property value type
 */
public class SimplePropertyDefinition<T, V> implements PropertyDefinition<T, V> {

    private static final long serialVersionUID = 4983663265225248972L;

    private final SimplePropertySet<T> propertySet;
    private final Class<V> type;
    private final String name;
    private final String caption;
    private final SerializableMethod getter;
    private final SerializableMethod setter;

    /**
     * Constructor.
     *
     * @param propertySet containing property set
     * @param type type of property
     * @param name name of property
     * @param caption property caption
     * @param getter property getter
     * @param setter property setter, or null for none
     * @throws IllegalArgumentException if any parameter other than {@code setter} is null
     */
    public SimplePropertyDefinition(SimplePropertySet<T> propertySet,
      Class<V> type, String name, String caption, Method getter, Method setter) {
        if (propertySet == null)
            throw new IllegalArgumentException("null propertySet");
        if (type == null)
            throw new IllegalArgumentException("null type");
        if (name == null)
            throw new IllegalArgumentException("null name");
        if (caption == null)
            throw new IllegalArgumentException("null caption");
        if (getter == null)
            throw new IllegalArgumentException("null getter");
        this.propertySet = propertySet;
        this.type = type;
        this.name = name;
        this.caption = caption;
        this.getter = new SerializableMethod(getter);
        this.setter = setter != null ? new SerializableMethod(setter) : null;
    }

    @Override
    public Class<T> getPropertyHolderType() {
        return this.propertySet.getType();
    }

    @Override
    public PropertySet<T> getPropertySet() {
        return this.propertySet;
    }

    @Override
    public Class<V> getType() {
        return this.type;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getCaption() {
        return this.caption;
    }

    @Override
    public ValueProvider<T, V> getGetter() {
        return obj -> this.type.cast(ReflectUtil.invoke(this.getter.getMethod(), obj));
    }

    @Override
    public Optional<Setter<T, V>> getSetter() {
        return this.setter != null ?
          Optional.of((obj, value) -> ReflectUtil.invoke(this.setter.getMethod(), obj, value)) : Optional.empty();
    }

// Object

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
          + "[name=\"" + this.name + "\""
          + ",type=" + this.type.getName()
          + ",caption=\"" + this.caption + "\""
          + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final SimplePropertyDefinition<?, ?> that = (SimplePropertyDefinition<?, ?>)obj;
        return Objects.equals(this.type, that.type)
          && Objects.equals(this.name, that.name)
          && Objects.equals(this.caption, that.caption)
          && Objects.equals(this.getter, that.getter)
          && Objects.equals(this.setter, that.setter);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.type)
          ^ Objects.hashCode(this.name)
          ^ Objects.hashCode(this.caption)
          ^ Objects.hashCode(this.getter)
          ^ Objects.hashCode(this.setter);
    }
}
