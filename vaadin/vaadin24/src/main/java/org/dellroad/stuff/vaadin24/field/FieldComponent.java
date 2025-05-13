
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import java.io.Serializable;

/**
 * A combination of a {@link HasValue} field and its corresponding {@link Component}.
 *
 * <p>
 * Usually, but not always, these are the same object - for example, with subclasses of {@link AbstractField}
 * like {@link ComboBox} and {@link TextField}.
 *
 * <p>
 * An example of when they are not the same is when a {@link Grid} is used as the visual component for a single select
 * created via {@link Grid#asSingleSelect} or multi-select field created via {@link Grid#asMultiSelect}.
 *
 * @param <V> field value type
 */
public class FieldComponent<V> implements Serializable {

    private static final long serialVersionUID = -4773664300675624288L;

    private final HasValue<?, V> field;
    private final Component component;

    /**
     * Constructor.
     *
     * @param field field
     * @param component corresponding component
     * @throws IllegalArgumentException if either parameter is null
     */
    public FieldComponent(HasValue<?, V> field, Component component) {
        if (field == null)
            throw new IllegalArgumentException("null field");
        if (component == null)
            throw new IllegalArgumentException("null component");
        this.field = field;
        this.component = component;
    }

    /**
     * Constructor.
     *
     * @param field field
     * @throws IllegalArgumentException if {@code field} is null
     */
    public FieldComponent(AbstractField<?, V> field) {
        this(field, field);
    }

    /**
     * Get the field bound into the {@link Binder}.
     *
     * @return bound field
     */
    public HasValue<?, V> getField() {
        return this.field;
    }

    /**
     * Get the component to be displayed.
     *
     * @return field's component
     */
    public Component getComponent() {
        return this.component;
    }

    /**
     * Get this instance's field as an {@link AbstractField}, assuming that's what it is.
     *
     * @return this instance's field
     * @throws IllegalArgumentException if this instance's field is not an {@link AbstractField}
     */
    @SuppressWarnings("unchecked")
    public AbstractField<?, V> getAbstractField() {
        if (this.field instanceof AbstractField)
            return (AbstractField<?, V>)this.field;
        throw new IllegalArgumentException(String.format("%s is not a subtype of %s", this.field.getClass(), AbstractField.class));
    }

    @Override
    public String toString() {
        return String.format("%s[field=%s%s]", this.getClass().getSimpleName(), this.getField(),
          (this.getComponent() != this.getField() ? ",component=" + this.getComponent() : ""));
    }
}
