
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.component;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;

import java.util.Objects;

import org.dellroad.stuff.vaadin22.util.WholeBeanValidator;

/**
 * Support superclass for {@link CustomField}s containing sub-fields that are managed by an internal {@link Binder}.
 *
 * <p>
 * As with many {@link CustomField}'s, this class this allows editing instances of a complex value type having multiple
 * distinct properties as a single bound property within some larger containing class. The main purpose of this class
 * is to add an internal {@link Binder} so both the individual sub-fields and the overall value can be properly validated.
 *
 * <p><b>Binding</b>
 *
 * <p>
 * Although a {@link BinderCustomField} may be bound to a property in some larger containing class' {@link Binder},
 * each {@link BinderCustomField} also contains its own internal {@link Binder} to which its private sub-fields are bound.
 * Subclasses can customize this {@link Binder} by overriding {@link #createBinder}.
 *
 * <p>
 * The value of a {@link BinderCustomField} is created by applying the values of these sub-fields to a
 * {@linkplain #createNewBean a newly created bean}. However, the value of a {@link BinderCustomField} remains
 * {@linkplain #getEmptyValue the empty value} as long as the internal {@link Binder} remains invalid.
 *
 * <p>
 * To create new instances, the value type must have a public zero-arg constructor; otherwise, override {@link #createNewBean}.
 *
 * <p><b>Validation</b>
 *
 * <p>
 * Individual sub-fields will be validated normally using the internal {@link Binder}; however, this class also implements
 * {@link ValidatingField} to support recursive validation of the overall field value by the field itself.
 *
 * <p>
 * So subclasses can override {@link #validate validate()} to apply validation contraints that span multiple sub-fields
 * (see also {@link WholeBeanValidator} for an alternate way to do this via JSR 303 "whole bean" validation constraints).
 * Note that this "whole bean" validation affects in the outer (containing) {@link Binder}, not the internal {@link Binder}
 * mentioned above.
 *
 * <p>
 * However, for this to work some code must register the field as a validator in the outer binding. Note, this happens
 * automatically when the field is created by a {@link org.dellroad.stuff.vaadin22.fieldbuilder.FieldBuilder}.
 *
 * <p>
 * <video style="border: 1px solid #000;" loop="true" autoplay="true" muted="true" controls="true">
 *  <source src="doc-files/CustomFieldAnimation.webm" type="video/webm">
 *  <span>[Video not supported]</span>
 * </video>
 *
 * <p><b>Layout</b>
 *
 * <p>
 * For layout, {@link BinderCustomField} simply concatenates the sub-fields into a {@link HorizontalLayout}.
 * Subclasses can cusomtize this behavior by overriding {@link #layoutComponents}.
 *
 * <p><b>Example</b>
 *
 * <p>
 * See {@link org.dellroad.stuff.vaadin22.fieldbuilder.FieldBuilderCustomField} for an example.
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
        this.createAndBindFields();
        this.layoutComponents();

        // When any bound sub-field changes, recalculate this field's value
        this.binder.addValueChangeListener(e -> this.updateValue());
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
     *
     * <p>
     * Note: this method is invoked from the constructor.
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
     *
     * <p>
     * Note: this method is invoked from the constructor.
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
            throw new RuntimeException("unexpected error invoking " + this.modelType + " constructor", e);
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

// CustomField

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
        final T target = !Objects.equals(value, this.getEmptyValue()) ? value : this.createNewBean();
        this.binder.readBean(target);
    }
}
