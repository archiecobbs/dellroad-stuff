
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.ui.ComboBox;

import java.util.EnumSet;

/**
 * {@link ComboBox} that chooses an {@link Enum} value.
 *
 * @param <T> enum type
 */
@SuppressWarnings("serial")
public class EnumComboBox<T extends Enum<T>> extends ComboBox<T> {

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
        this.setItemCaptionGenerator(T::toString);
        this.setEmptySelectionAllowed(allowEmpty);
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
        final EnumSet<T> enums = EnumSet.allOf(enumClass);
        this.setItems(enums);
        if (!this.isEmptySelectionAllowed() && !enums.isEmpty())
            this.setSelectedItem(enums.iterator().next());
        this.enumClass = enumClass;
    }

    /**
     * Get the enum class.
     *
     * @return enum type
     */
    public Class<T> getEnumClass() {
        return this.enumClass;
    }
}

