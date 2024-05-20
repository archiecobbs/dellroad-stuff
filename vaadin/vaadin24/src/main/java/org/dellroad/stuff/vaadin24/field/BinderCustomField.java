
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;

import java.time.LocalDate;
import java.util.Objects;

import org.dellroad.stuff.vaadin24.util.VaadinUtil;
import org.dellroad.stuff.vaadin24.util.WholeBeanValidator;

/**
 * Support superclass for {@link CustomField}s containing sub-fields that are managed by an internal {@link Binder}.
 *
 * <p>
 * As with many {@link CustomField}'s, this class this allows editing a complex value type as a single bound
 * property using multiple sub-fields. A common example is a date range value, which is a composite of two {@link LocalDate}
 * values representing start and end date.
 *
 * <p>
 * The purpose of this class is to add an internal {@link Binder} so that the individual sub-fields can be properly
 * (and separately) validated. This class also includes support for validating the overall value, for example,
 * requiring the date range start date to be prior to the end date.
 *
 * <p>
 * <video style="border: 1px solid #000;" loop="true" autoplay="true" muted="true" controls="true">
 *  <source src="doc-files/CustomFieldAnimation.webm" type="video/webm">
 *  <span>[Video not supported]</span>
 * </video>
 *
 * <p><b>Binding</b>
 *
 * <p>
 * Although a {@link BinderCustomField} may be bound as a field to a property in some larger containing class' {@link Binder},
 * each {@link BinderCustomField} also contains its own internal {@link Binder} to which its private sub-fields are bound.
 *
 * <p>
 * Subclasses can customize this internal {@link Binder} by overriding {@link #createBinder}.
 *
 * <p><b>Field Value</b>
 *
 * <p>
 * The value of a {@link BinderCustomField} is generated by {@linkplain #createNewBean creating a new bean instance} and then
 * applying the values of the internal sub-fields to it.
 *
 * <p>
 * In order for this class to create new instances, its value type must have a public zero-arg constructor; otherwise,
 * the subclass must override {@link #createNewBean}.
 *
 * <p>
 * In any case, the value of a {@link BinderCustomField} remains {@linkplain #getEmptyValue its empty value}
 * as long as any of its sub-fields remain in an invalid state according to the internal {@link Binder}.
 *
 * <p><b>Binders and Validation</b>
 *
 * <p>
 * Individual sub-fields are validated normally using the internal {@link Binder}; however, this guarantees only that
 * the sub-fields are valid. It does not validate this {@link BinderCustomField}'s value, which is derived from a combination
 * of the sub-fields; normally, that's the responsibilty of some outer {@link Binder}, not the internal {@link Binder}.
 *
 * <p>
 * Subclasses can override {@link #validate validate()} to implement "whole bean" validation contraints on
 * this {@link BinderCustomField}'s value. However, for whole bean validation to have any effect, some code must register
 * the validation provided by {@link #validate validate()} to the outer binding. This happens automatically when this
 * field is created by a {@link FieldBuilder}, or by any other mechanism which recognizes the {@link ValidatingField} interface
 * (which this class implements). In particular, it's possible to recursively nest {@link BinderCustomField}'s using
 * {@link FieldBuilder} annotations and have proper validation at each level.
 *
 * <p>
 * See also {@link WholeBeanValidator} for another way to do "whole bean" validation using JSR 303 validation constraints.
 *
 * <p><b>Layout</b>
 *
 * <p>
 * For layout, {@link BinderCustomField} simply concatenates the sub-fields into a {@link HorizontalLayout}.
 * Subclasses can customize this behavior by overriding {@link #layoutComponents}.
 *
 * <p><b>Example</b>
 *
 * <p>
 * See {@link FieldBuilderCustomField} for an example.
 *
 * @param <T> field value type
 */
@SuppressWarnings("serial")
public abstract class BinderCustomField<T> extends CustomField<T>
  implements ValidatingField<AbstractField.ComponentValueChangeEvent<CustomField<T>, T>, T> {

    /**
     * The field value type.
     */
    protected final Class<T> modelType;

    /**
     * The binder that is bound to this instance's sub-fields.
     */
    protected final Binder<T> binder;

    /**
     * Whether any sub-fields currently have a validation error.
     */
    protected boolean subfieldValidationErrors;

    // Handle setPresentationValue() being invoked prior to initialize() if initialization is delayed
    private InitState initState = InitState.INITIAL;
    private T initialValue;

// Constructors

    /**
     * Constructor.
     *
     * @param modelType field value type
     * @throws IllegalArgumentException if {@code modelType} is null
     */
    public BinderCustomField(Class<T> modelType) {

        // Sanity check
        if (modelType == null)
            throw new IllegalArgumentException("null modelType");

        // Initialize
        this.modelType = modelType;
        this.binder = this.createBinder();
        this.initialize();

        // When any bound sub-field changes, recalculate this field's value
        this.binder.addValueChangeListener(e -> this.updateValue());

        // Whenever any sub-field becomes invalid, remove our error message (if any) to avoid clutter
        this.binder.addStatusChangeListener(e -> {
            this.subfieldValidationErrors = e.hasValidationErrors();
            if (this.subfieldValidationErrors)
                this.setErrorMessage(null);
        });
    }

    /**
     * Get the model type.
     */
    public Class<T> getModelType() {
        return this.modelType;
    }

// Subclass Methods

    /**
     * Initialize this instance.
     *
     * <p>
     * The implementation in {@link BinderCustomField} invokes {@link #createAndBindFields} and then {@link #layoutComponents}.
     *
     * <p>
     * Note: this method is invoked from the constructor, so any subclass constructor initialization will not have been done yet.
     * Subclasses can fix this by overriding this method and invoking
     * {@link VaadinUtil#accessCurrentSession VaadinUtil.accessCurrentSession}{@code (() -> super.initialize())}.
     */
    protected void initialize() {
        if (this.initState.equals(InitState.INITIALIZED))
            throw new IllegalStateException("duplicate initialization");
        this.createAndBindFields();
        this.layoutComponents();
        final boolean hasInitialValue = this.initState.equals(InitState.INITIAL_VALUE);
        this.initState = InitState.INITIALIZED;
        if (hasInitialValue) {
            this.setPresentationValue(this.initialValue);
            this.initialValue = null;
        }
    }

    /**
     * Create a new {@link Binder} for the given type.
     *
     * <p>
     * The implementation in {@link BinderCustomField} delegates to {@link Binder#Binder(Class)}.
     * Subclasses can override this method to substitute {@link BeanValidationBinder}, configure additional validators, etc.
     *
     * <p>
     * Note: this method is invoked from the constructor.
     *
     * @return field builder
     */
    protected Binder<T> createBinder() {
        return new Binder<>(this.modelType);
    }

    /**
     * Create this field's sub-fields and bind them to {@link #binder}.
     */
    protected abstract void createAndBindFields();

    /**
     * Layout components required for this field.
     *
     * <p>
     * The implementation in {@link BinderCustomField} iterates the bound fields in {@link #binder}
     * into a new {@link HorizontalLayout} which is then added to this instance, assuming all fields
     * are also actually {@link Component}'s.
     *
     * <p>
     * Subclasses can override this method to add decoration and/or layout differently.
     */
    protected void layoutComponents() {
        final HorizontalLayout layout = new HorizontalLayout();
        this.add(layout);
        this.binder.getFields()
          .map(Component.class::cast)
          .forEach(layout::add);
    }

    /**
     * Create a new instance of the bean model class.
     *
     * <p>
     * This value is used to create beans to be populated with sub-field values and returned from {@link #generateModelValue}.
     *
     * <p>
     * The implementation in {@link BinderCustomField} attempts to invoke a default constructor for the bean class.
     *
     * @return new empty bean
     */
    protected T createNewBean() {
        try {
            return this.modelType.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(String.format("unexpected error invoking %s constructor", this.modelType), e);
        }
    }

// ValidatingField

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@code BinderCustomField} always returns {@link ValidationResult#ok}.
     */
    @Override
    public ValidationResult validate(T value, ValueContext ctx) {
        return ValidationResult.ok();
    }

// HasEnabled

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@code BinderCustomField} delegates to the superclass and then removes
     * any error message from sub-fields that implement {@link HasValidation}.
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled)
            this.updateValue();                     // trigger validation to (re)populate error messages
        else {
            this.binder.getFields()
              .filter(HasValidation.class::isInstance)
              .map(HasValidation.class::cast)
              .forEach(field -> {
                field.setInvalid(false);
                field.setErrorMessage(null);
              });
        }
    }

// CustomField

    /**
     * Sets an error message to the component.
     *
     * <p>
     * The implementation in {@link BinderCustomField} delegates to the overridden superclass method
     * except when {@code errorMessage} is not null and any sub-field currently has a validation error.
     * This is to avoid clutter in the display.
     */
    @Override
    public void setErrorMessage(String errorMessage) {
        if (errorMessage == null || !this.subfieldValidationErrors)
            super.setErrorMessage(errorMessage);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@link BinderCustomField} returns {@linkplain #getEmptyValue the empty value} if
     * the internal binder is invalid, otherwise {@linkplain #createNewBean a newly created bean instance} populated
     * with the current sub-fields' values.
     */
    @Override
    protected T generateModelValue() {
        final T bean = this.createNewBean();
        try {
            this.binder.writeBean(bean);
        } catch (ValidationException e) {
            return this.getEmptyValue();
        }
        return bean;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@link BinderCustomField} updates the sub-fields from {@code value} unless it is equal to
     * {@linkplain #getEmptyValue the empty value}, in which case from {@linkplain #createNewBean a newly created bean instance}.
     */
    @Override
    protected void setPresentationValue(T value) {
        switch (this.initState) {
        case INITIAL:
            this.initialValue = value;
            this.initState = InitState.INITIAL_VALUE;
            return;
        case INITIAL_VALUE:
            this.initialValue = value;
            return;
        default:
            break;
        }
        final T target = !Objects.equals(value, this.getEmptyValue()) ? value : this.createNewBean();
        this.binder.readBean(target);
    }

// InitState

    private enum InitState {
        INITIAL,                                // initial state
        INITIAL_VALUE,                          // setPresentationValue() has been invoked, but not initialize() yet
        INITIALIZED;                            // initialize() has been invoked
    }
}
