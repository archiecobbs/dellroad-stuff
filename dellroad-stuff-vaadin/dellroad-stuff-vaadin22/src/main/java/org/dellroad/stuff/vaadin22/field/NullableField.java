
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.field;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;

/**
 * A {@link CustomField} that wraps an inner field and prepends a checkbox (or other boolean field) in front of it
 * that controls whether the value is null or not.
 *
 * <p>
 * When the checkbox is checked, the inner field functions normally and provides this field's value.
 * When the checkbox is unchecked, the inner field is completely disabled and this field's value is null.
 *
 * <p>
 * <video style="border: 1px solid #000;" loop="true" autoplay="true" muted="true" controls="true">
 *  <source src="doc-files/NullableFieldAnimation.webm" type="video/webm">
 *  <span>[Video not supported]</span>
 * </video>
 *
 * <p><b>Resetting On Disable</b>
 *
 * <p>
 * When the checkbox is unchecked, by default the inner (wrapped) field is automatically reset to its
 * {@linkplain HasValue#getEmptyValue empty value}. To have the inner field retain its value instead,
 * so the value reappears if the checkbox is unchecked, use {@link #setResetOnDisable setResetOnDisable(false)}.
 *
 * <p><b>Validation</b>
 *
 * <p>
 * This class implements {@link ValidatingField}, delegating to the inner field (for non-null values only) if it also
 * implements {@link ValidatingField}.
 *
 * @param <T> field value type
 */
@SuppressWarnings("serial")
public class NullableField<T> extends CustomField<T>
  implements ValidatingField<AbstractField.ComponentValueChangeEvent<CustomField<T>, T>, T> {

    public static final String DEFAULT_ENABLED_BUT_NULL_ERROR = "Required value";

    /**
     * The inner field.
     */
    protected final HasValue<?, T> innerField;

    /**
     * The checkbox (or other boolean field).
     */
    protected final HasValue<?, Boolean> enabledField;

    /**
     * The inner field component.
     */
    protected final Component component;

    private boolean resetOnDisable = true;

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
     * Create an instance using the given {@link AbstractField} and the given enablement field.
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
     * Create an instance using the given field, component, and enablement field.
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

        // Start tracking checkbox and inner field state
        this.setComponentEnabled(this.enabledField.getValue());
        this.enabledField.addValueChangeListener(e -> {
            if (this.resetOnDisable && !e.getValue())
                this.innerField.setValue(this.innerField.getEmptyValue());
            this.setComponentEnabled(e.getValue());
            if (!e.getValue() && this.innerField instanceof HasValidation)
                ((HasValidation)this.innerField).setInvalid(false);
            this.updateValue();
        });
        this.innerField.addValueChangeListener(e -> this.updateValue());
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

    /**
     * Determine whether the inner field forgets its value when the checkbox is unchecked.
     *
     * @return true if the inner field is reset when the checkbox is unchecked
     */
    public boolean isResetOnDisable() {
        return this.resetOnDisable;
    }
    public void setResetOnDisable(final boolean resetOnDisable) {
        if (this.resetOnDisable == resetOnDisable)
            return;
        this.resetOnDisable = resetOnDisable;
        if (this.resetOnDisable && !this.enabledField.getValue())
            this.innerField.setValue(this.innerField.getEmptyValue());
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
        final boolean enabled = value != null;
        this.enabledField.setValue(enabled);
        this.setComponentEnabled(enabled);
        this.innerField.setValue(enabled ? value : this.innerField.getEmptyValue());
    }
}
