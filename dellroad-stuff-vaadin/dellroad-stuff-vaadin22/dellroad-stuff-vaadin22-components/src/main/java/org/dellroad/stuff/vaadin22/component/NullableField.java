
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.component;

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
import com.vaadin.flow.data.binder.ValueContext;

/**
 * A {@link CustomField} that wraps an inner field and prepends an checkbox in front of it which
 * controls whether the value is null or not.
 *
 * <p>
 * When the checkbox is checked, the inner field functions normally and provides the value of this field.
 * When the checkbox is unchecked, the inner field is completely disabled and the value of this field is null.
 *
 * <p>
 * Implements {@link ValidatingField}, delegating to the inner field (for non-null values only) if it also
 * implements {@link ValidatingField}.
 *
 * @param <T> field value type
 */
@SuppressWarnings("serial")
public class NullableField<T> extends CustomField<T>
  implements ValidatingField<AbstractField.ComponentValueChangeEvent<CustomField<T>, T>, T> {

    public static final String DEFAULT_ENABLED_BUT_NULL_ERROR = "Required value";

    protected final HasValue<?, T> innerField;
    protected final HasValue<?, Boolean> enabledField;
    protected final Component component;

// Constructors

    /**
     * Create an instance using the given {@link AbstractField} and a new {@link Checkbox} with the given label.
     *
     * @param innerField the inner field
     * @param checkboxLabel label for the checkbox, or null for none
     */
    public NullableField(AbstractField<?, T> innerField, String checkboxLabel) {
        this(innerField, new Checkbox(checkboxLabel));
    }

    /**
     * Create an instance using the given {@link AbstractField} and the given {@link Checkbox} or other enablement field.
     *
     * @param innerField the inner field
     * @param enabledField field that controls nullability
     * @throws IllegalArgumentException if {@code innerField} is null
     * @throws IllegalArgumentException if {@code enabledField} is null
     */
    public NullableField(AbstractField<?, T> innerField, HasValue<?, Boolean> enabledField) {
        this(innerField, innerField, enabledField);
    }

    /**
     * Create an instance using the given field, component, and {@link Checkbox} or other enablement field.
     *
     * @param innerField the inner field
     * @param component the component corresponding to {@link innerField} (typically this is same object)
     * @param enabledField field that controls nullability
     * @throws IllegalArgumentException if any parameter is null
     */
    @SuppressWarnings("unchecked")
    public NullableField(HasValue<?, T> innerField, Component component, HasValue<?, Boolean> enabledField) {

        // Sanity check
        Preconditions.checkArgument(innerField != null, "null innerField");
        Preconditions.checkArgument(component != null, "null component");
        Preconditions.checkArgument(enabledField != null, "null enabledField");

        // Initialize
        this.innerField = innerField;
        this.enabledField = enabledField;
        this.component = component;

        // Build layout
        this.buildLayout();

        // Update value when checkbox or inner field changes
        this.enabledField.addValueChangeListener(e -> this.updateValue());
        this.innerField.addValueChangeListener(e -> this.updateValue());

        // Start tracking checkbox state
        this.setComponentEnabled(this.enabledField.getValue());
        this.enabledField.addValueChangeListener(event -> {
            this.setComponentEnabled(event.getValue());
            if (!event.getValue())
                this.innerField.setValue(this.innerField.getEmptyValue());
        });
    }

// Public methods

    public HasValue<?, T> getInnerField() {
        return this.innerField;
    }

    public HasValue<?, Boolean> getEnabledField() {
        return this.enabledField;
    }

    public Component getComponent() {
        return this.component;
    }

// ValidatingField

    @Override
    public ValidationResult validate(T value, ValueContext ctx) {

        // Kludge alert: we are assuming the current checkbox value was the same as it was when "value" was read
        final boolean enabled = this.enabledField.getValue();

        // Handle various combinations
        if (value == null) {
            if (enabled)
                return this.enabledButNullValidationResult();
        } else {
            if (this.innerField instanceof ValidatingField)
                return ((ValidatingField<?, T>)this.innerField).validate(value, ctx);
        }
        return ValidationResult.ok();
    }

    /**
     * Build a {@link ValidationResult} to be returned by {@link #validate validate()} in the situation
     * where the checkbox is checked, but the inner field is returning a value of null.
     *
     * <p>
     * This would typically indicate that the inner field is incompletely filled-in or otherwise invalid.
     *
     * <p>
     * In this situation, {@link #validate validate()} should return a validation error, because the user
     * has explicitly said they want a non-null value by checking the checkbox.
     *
     * <p>
     * The implementation in {@link NullableField} returns a {@value #DEFAULT_ENABLED_BUT_NULL_ERROR} error.
     *
     * @return validation error
     */
    protected ValidationResult enabledButNullValidationResult() {
        return ValidationResult.error("Required value");
    }

// Subclass methods

    protected void buildLayout() {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        if (this.enabledField instanceof Component)
            layout.add((Component)this.enabledField);
        layout.add(this.component);
        this.add(layout);
    }

    protected void setComponentEnabled(boolean enabled) {
        if (this.component instanceof HasEnabled)
            ((HasEnabled)this.component).setEnabled(enabled);
    }

// CustomField

    @Override
    protected T generateModelValue() {
        return this.enabledField.getValue() ? this.innerField.getValue() : null;
    }

// AbstractField

    @Override
    protected void setPresentationValue(T value) {
        this.setComponentEnabled(value != null);
        this.innerField.setValue(value != null ? value : this.innerField.getEmptyValue());
    }
  }
