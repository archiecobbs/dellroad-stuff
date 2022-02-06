
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component;

import com.vaadin.flow.component.combobox.ComboBox;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.EnumSet;

/**
 * {@link ComboBox} that chooses an {@link Enum} value.
 *
 * <p>
 * When instances are created by {@link org.dellroad.stuff.vaadin22.flow.component.fieldbuilder.FieldBuilder},
 * the {@link #setEnumClass} method will be configured automatically using the return type of the annotated
 * getter method (this happens via {@link #onAutoBuild}).
 *
 * @param <T> enum type
 */
@SuppressWarnings("serial")
public class EnumComboBox<T extends Enum<T>> extends ComboBox<T> implements AutoBuildAware {

    private final boolean allowEmpty;

    private Class<T> enumClass;

    /**
     * Default constructor.
     *
     * <p>
     * Caller must separately invoke {@link #setEnumClass}.
     */
    public EnumComboBox() {
        this(null);
    }

    /**
     * Convenience constructor.
     *
     * <p>
     * Equivalent to:
     *  <blockquote><code>
     *  EnumComboBox(enumClass, true);
     *  </code></blockquote>
     *
     * @param enumClass enum type, or null to leave unset
     */
    public EnumComboBox(Class<T> enumClass) {
        this(enumClass, true);
    }

    /**
     * Primary constructor.
     *
     * @param enumClass enum type, or null to leave unset
     * @param allowEmpty true to allow an empty selection, false otherwise
     */
    public EnumComboBox(Class<T> enumClass, boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
        if (enumClass != null)
            this.setEnumClass(enumClass);
    }

    /**
     * Set the enum class.
     *
     * @param enumClass enum type
     * @throws IllegalArgumentException if {@code enumClass} is null
     */
    public void setEnumClass(Class<T> enumClass) {
        if (enumClass == null)
            throw new IllegalArgumentException("null enumClass");
        this.enumClass = enumClass;
        this.setItems(EnumSet.allOf(enumClass));
    }

    /**
     * Get the enum class.
     *
     * @return enum type
     */
    public Class<T> getEnumClass() {
        return this.enumClass;
    }

// AutoBuildAware

    @Override
    @SuppressWarnings("unchecked")
    public void onAutoBuild(Object fieldBuilder, Method method, Annotation annotation) {
        if (this.enumClass != null)
            return;
        try {
            this.setEnumClass((Class<T>)method.getReturnType().asSubclass(Enum.class));
        } catch (ClassCastException e) {
            // ignore
        }
    }
}
