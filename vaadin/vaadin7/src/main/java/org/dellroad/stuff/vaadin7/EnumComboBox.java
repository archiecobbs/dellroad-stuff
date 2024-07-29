
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin7;

import com.vaadin.shared.ui.combobox.FilteringMode;
import com.vaadin.ui.AbstractSelect.ItemCaptionMode;
import com.vaadin.ui.ComboBox;

/**
 * {@link ComboBox} that chooses an {@link Enum} value.
 *
 * @see EnumContainer
 */
@SuppressWarnings("serial")
public class EnumComboBox extends ComboBox {

    /**
     * Default constructor.
     *
     * <p>
     * Caller must separately invoke {@link #setEnumDataSource}.
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
     *  EnumComboBox(enumClass, EnumContainer.TO_STRING_PROPERTY.getName(), true);
     *  </code></blockquote>
     *
     * @param enumClass enum type, or null to leave data source unset
     * @param <T> enum type
     */
    public <T extends Enum<T>> EnumComboBox(Class<T> enumClass) {
        this(enumClass, true);
    }

    /**
     * Convenience constructor.
     *
     * <p>
     * Equivalent to:
     *  <blockquote><code>
     *  EnumComboBox(enumClass, EnumContainer.TO_STRING_PROPERTY, allowNull);
     *  </code></blockquote>
     *
     * @param enumClass enum type, or null to leave data source unset
     * @param allowNull true to allow a null selection, false otherwise
     * @param <T> enum type
     */
    public <T extends Enum<T>> EnumComboBox(Class<T> enumClass, boolean allowNull) {
        this(enumClass, EnumContainer.TO_STRING_PROPERTY, allowNull);
    }

    /**
     * Convenience constructor.
     *
     * <p>
     * Equivalent to:
     *  <blockquote><code>
     *  EnumComboBox(enumClass, displayPropertyName, false);
     *  </code></blockquote>
     *
     * @param enumClass enum type
     * @param displayPropertyName container property to display in the combo box
     * @param <T> enum type
     */
    public <T extends Enum<T>> EnumComboBox(Class<T> enumClass, String displayPropertyName) {
        this(enumClass, displayPropertyName, false);
    }

    /**
     * Convenience constructor.
     *
     * <p>
     * Equivalent to:
     *  <blockquote><code>
     *  EnumComboBox(enumClass != null ? new EnumContainer&lt;T&gt;(enumClass) : null, displayPropertyName, allowNull);
     *  </code></blockquote>
     *
     * @param enumClass enum type, or null to leave data source unset
     * @param displayPropertyName container property to display in the combo box
     * @param allowNull true to allow a null selection, false otherwise
     * @param <T> enum type
     */
    public <T extends Enum<T>> EnumComboBox(Class<T> enumClass, String displayPropertyName, boolean allowNull) {
        this(enumClass != null ? new EnumContainer<>(enumClass) : null, displayPropertyName, allowNull);
    }

    /**
     * Primary constructor.
     *
     * <p>
     * This instance is configured for item caption {@link ItemCaptionMode#PROPERTY} mode, with
     * {@code displayPropertyName} as the {@linkplain #setItemCaptionPropertyId item caption property}.
     *
     * @param container container data source, or null to leave data source unset
     * @param displayPropertyName container property to display in the combo box
     * @param allowNull true to allow a null selection, false otherwise
     */
    public EnumComboBox(EnumContainer<?> container, String displayPropertyName, boolean allowNull) {
        if (container != null)
            this.setContainerDataSource(container);
        this.setNewItemsAllowed(false);
        this.setFilteringMode(FilteringMode.CONTAINS);
        this.setItemCaptionMode(ItemCaptionMode.PROPERTY);
        this.setItemCaptionPropertyId(displayPropertyName);

        // Set up whether null selection is allowed
        this.setNullSelectionAllowed(allowNull);
        if (!allowNull && !this.getContainerDataSource().getItemIds().isEmpty())
            this.setValue(this.getContainerDataSource().getItemIds().iterator().next());
    }

    /**
     * Set the {@link Enum} type whose instances serve as this instance's data source.
     *
     * @param enumClass enum class instance
     * @param <T> enum type
     * @throws IllegalArgumentException if {@code enumClass} is null
     */
    public <T extends Enum<T>> void setEnumDataSource(Class<T> enumClass) {
        this.setContainerDataSource(new EnumContainer<>(enumClass));
        if (!this.isNullSelectionAllowed() && !this.getContainerDataSource().getItemIds().isEmpty())
            this.setValue(this.getContainerDataSource().getItemIds().iterator().next());
    }
}
