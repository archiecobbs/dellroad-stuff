
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.fieldbuilder;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;

/**
 * A {@link CustomField} that wraps an inner field and sticks an enablement checkbox in front of it which explicitly
 * controls whether the value is null or not.
 *
 * <p>
 * When the checkbox is checked, the inner field functions normally. When the checkbox is unchecked, the inner field
 * is completely disabled and the value of this field is null.
 *
 * <p>
 * Implements {@link HasInternalValidator}, delegating to the inner field (for non-null values only) if it also
 * implements {@link HasInternalValidator}.
 */
@SuppressWarnings("serial")
public class NullableCustomField<T> extends CustomField<T>
  implements HasInternalValidator<AbstractField.ComponentValueChangeEvent<CustomField<T>, T>, T> {

    protected final HasValue<?, T> innerField;
    protected final Component component;
    protected final Checkbox checkbox;

// Constructors

    /**
     * Create an instance using the given {@link AbstractField} and a new checkbox with given label.
     *
     * @param innerField the inner field
     * @param checkboxLabel label for the checkbox, or null for none
     */
    public NullableCustomField(AbstractField<?, T> innerField, String checkboxLabel) {
        this(innerField, new Checkbox(checkboxLabel));
    }

    /**
     * Create an instance using the given {@link AbstractField} and the given checkbox.
     *
     * @param innerField the inner field
     * @param checkbox checkbox that controls nullability
     * @throws IllegalArgumentException if {@code innerField} is null
     * @throws IllegalArgumentException if {@code checkbox} is null
     */
    public NullableCustomField(AbstractField<?, T> innerField, Checkbox checkbox) {
        this(innerField, innerField, checkbox);
    }

    /**
     * Create an instance using the given field, component, and checkbox.
     *
     * @param innerField the inner field
     * @param component the component corresponding to {@link innerField} (typically this is same object)
     * @param checkbox checkbox that controls nullability
     * @throws IllegalArgumentException if {@code innerField} is null
     * @throws IllegalArgumentException if {@code component} is null
     * @throws IllegalArgumentException if {@code checkbox} is null
     */
    public NullableCustomField(HasValue<?, T> innerField, Component component, Checkbox checkbox) {

        // Sanity check
        Preconditions.checkArgument(innerField != null, "null innerField");
        Preconditions.checkArgument(component != null, "null component");
        Preconditions.checkArgument(checkbox != null, "null checkbox");

        // Initialize
        this.innerField = innerField;
        this.component = component;
        this.checkbox = checkbox;

        // Build layout
        this.buildLayout();

        // Initialize and start tracking nullified state
        this.setNullified(!this.checkbox.getValue());
        this.checkbox.addValueChangeListener(event -> this.setNullified(!event.getValue()));
    }

// Public methods

    public HasValue<?, T> getInnerField() {
        return this.innerField;
    }

    public Component getComponent() {
        return this.component;
    }

    public Checkbox getCheckbox() {
        return this.checkbox;
    }

// HasInternalValidator

    @Override
    public Validator<T> getInternalValidator() {
        if (!(this.innerField instanceof HasInternalValidator))
            return (bean, ctx) -> ValidationResult.ok();
        final Validator<? super T> validator = ((HasInternalValidator<?, T>)this.innerField).getInternalValidator();
        return (bean, ctx) -> bean != null ? validator.apply(bean, ctx) : ValidationResult.ok();
    }

// Subclass methods

    /**
     * Build the layout for this field.
     *
     * <p>
     * The implementation in {@link NullableCustomField} adds a {@link HorizontalLayout} containing
     * the {@code checkbox} and the inner field.
     */
    protected void buildLayout() {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        layout.add(this.checkbox);
        layout.add(this.component);
        this.add(layout);
    }

    /**
     * Set whether the field is nullified or not.
     *
     * <p>
     * The implementation in {@link NullableCustomField} updates the checkbox and enables/disabled the component
     * accordingly (assuming it implements {@link HasEnabled}).
     *
     * @param nullified if field should be null and the nested components disabled
     */
    protected void setNullified(boolean nullified) {
        this.checkbox.setValue(!nullified);
        if (this.component instanceof HasEnabled)
            ((HasEnabled)this.component).setEnabled(!nullified);
    }

// CustomField

    @Override
    protected T generateModelValue() {
        return this.checkbox.getValue() ? this.innerField.getValue() : null;
    }

// AbstractField

    @Override
    protected void setPresentationValue(T value) {
        this.setNullified(value == null);
        this.innerField.setValue(value);
    }
}
