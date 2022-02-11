
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.fieldbuilder;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;

import java.util.Optional;

/**
 * Support superclass that mostly automates the creation of {@link CustomField}s for editing any model type
 * having {@link FieldBuilder} annotations, using automatically generated sub-fields.
 *
 * <p>
 * This field uses a {@link FieldBuilder} to generate sub-fields for the annotated properties of a user-supplied model class.
 * As with any other {@link CustomField}, this allows editing instances of the model class as a single property within
 * some containing class. The containing class could then specify this class as the editor for that property with a
 * {@link FieldBuilder.CustomField &#64;FieldBuilder.CustomField} annotation.
 *
 * <p>
 * Each {@link GeneratedCustomField} contains an internal {@link Binder} to which its sub-fields are bound. Subclasses can
 * customize the {@link Binder}, e.g., to add additional cross-field validation, etc. Subclasses can also customize
 * the layout of the sub-fields via {@link #layoutComponents}.
 *
 * <p>
 * This class also implements {@link HasInternalValidator}, which facilitates for recursive validation when this field
 * is used within an outer {@link FieldBuilder}; see {@link FieldBuilder} for details.
 *
 * <p>
 * Put another way, this class provides the glue that allows {@link FieldBuilder} to work recursively, while also
 * promoting modularity.
 *
 * <p><b>Example</b>
 *
 * <p>
 * Suppose model class {@code Contract} has a "term" property of type {@code DateInterval} which has a start and end date.
 * We want to use {@link FieldBuilder} annotations to define the editor fields for the properties of {@code Contract},
 * including "term", but for that to work we will need to specify a custom field to handle the "term" property.
 *
 * <p>
 * Ideally, we want this custom field not only to contain the logic for laying out the start and end date components,
 * but also to contain the logic for validating their proper ordering, as that is a concern for {@code DateInterval} and
 * not {@code Contract}. This class provides a way to do that.
 *
 * <p>
 * For example:
 *
 * <blockquote><pre>
 * // This is complex type that we wish to edit with a single CustomField
 * public class DateInterval {
 *
 *     &#64;FieldBuilder.DatePicker(label = "Start date")
 *     public LocalDate getStartDate() { ... }
 *     public void setStartDate(LocalDate date) { ... }
 *
 *     &#64;FieldBuilder.DatePicker(label = "End date")
 *     public LocalDate getEndDate() { ... }
 *     public void setEndDate(LocalDate date) { ... }
 * }
 *
 * // This is the corresponding custom field for editing a DateInterval
 * public class DateIntervalField extends GeneratedCustomField&lt;DateInterval&gt; {
 *
 *     public DateIntervalField() {
 *         super(DateInterval.class);
 *     }
 *
 *     // Ensure end date is after start date
 *     &#64;Override
 *     protected Binder&lt;DateInterval&gt; createBinder() {
 *         return super.createBinder().withValidator(i -&gt; i.getStartDate().isBefore(i.getEndDate()) ?
 *             ValidationResult.ok() : ValidationResult.error("Dates out-of-order"));
 *     }
 *
 *     // Customize how we want to layout the subfields
 *     &#64;Override
 *     protected void layoutComponents() {
 *
 *         // Get sub-fields
 *         final FieldBuilder.BoundField startDateField = this.fieldBuilder.getBoundFields().get("startDate");
 *         final FieldBuilder.BoundField endDateField = this.fieldBuilder.getBoundFields().get("endDate");
 *
 *         // Layout sub-fields
 *         final HorizontalLayout layout = new HorizontalLayout();
 *         layout.add(startDateField.getComponent());
 *         layout.add(new Text("-"));
 *         layout.add(endDateField.getComponent());
 *         this.add(layout);
 *     }
 * }
 *
 * // Now FieldBuilder works recursively for this multi-level class
 * public class Contract {
 *
 *     &#64;FieldBuilder.CheckBox(label = "Approved?")
 *     public boolean isApproved() { ... }
 *     public void setApproved(boolean approved) { ... }
 *
 *     <b>&#64;FieldBuilder.CustomField(label = "Term", implementation = DateIntervalField.class)</b>
 *     public DateInterval getTerm() { ... }
 *     public void setTerm(DateInterval term) { ... }
 * }
 * </pre></blockquote>
 *
 * <p><b>Details</b>
 *
 * <p>
 * Subclasses are responsible for laying out the sub-fields and any additional {@link Binder} configuration.
 *
 * <p>
 * The field's value type must have a public zero-arg constructor.
 *
 * @param <T> field value type
 */
@SuppressWarnings("serial")
public class GeneratedCustomField<T> extends CustomField<T>
  implements HasInternalValidator<AbstractField.ComponentValueChangeEvent<CustomField<T>, T>, T> {

    /**
     * The field value type.
     */
    protected final Class<T> modelType;

    /**
     * The field builder that builds this instance's sub-fields.
     */
    protected final AbstractFieldBuilder<?, T> fieldBuilder;

    /**
     * The binder that is bound to this instance's sub-fields.
     */
    protected final Binder<T> binder;

    /**
     * Constructor.
     *
     * @param modelType model type to introspect for {@link AbstractFieldBuilder} annotations
     * @throws IllegalArgumentException if {@code modelType} is null
     */
    public GeneratedCustomField(Class<T> modelType) {

        // Sanity check
        if (modelType == null)
            throw new IllegalArgumentException("null modelType");
        try {
            modelType.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(modelType + " has no public zero-arg constructor");
        }

        // Initialize
        this.modelType = modelType;
        this.fieldBuilder = this.createFieldBuilder();
        this.binder = this.createBinder();
        this.fieldBuilder.bindFields(this.binder);
        this.layoutComponents();

        // When any bound sub-field changes, recalculate this field's value
        this.binder.addValueChangeListener(e -> this.updateValue());
    }

    /**
     * Create a new {@link AbstractFieldBuilder} for the given type.
     *
     * <p>
     * The implementation in {@link GeneratedCustomField} returns a new {@link FieldBuilder} each time.
     *
     * @return field builder
     */
    protected AbstractFieldBuilder<?, T> createFieldBuilder() {
        return new FieldBuilder<>(this.modelType);
    }

    /**
     * Create a new {@link Binder} for the given type.
     *
     * <p>
     * The implementation in {@link GeneratedCustomField} delegates to {@link Binder#Binder(Class)}.
     * Subclasses can override this method to substitute {@link BeanValidationBinder}, add additional validators, etc.
     *
     * @return field builder
     */
    protected Binder<T> createBinder() {
        return new Binder<>(this.modelType);
    }

    /**
     * Layout components required for this field.
     *
     * <p>
     * The implementation in {@link GeneratedCustomField} iterates the bound fields in {@link #fieldBuilder}
     * into a new {@link HorizontalLayout} which is then added to this instance. Subclasses will typically
     * override this method to add decoration and/or layout differently.
     */
    protected void layoutComponents() {
        final HorizontalLayout layout = new HorizontalLayout();
        this.add(layout);
        this.fieldBuilder.getBoundFields().values().stream()
          .map(FieldBuilder.BoundField::getComponent)
          .forEach(layout::add);
    }

    /**
     * Create a new/empty instance of the bean model class.
     *
     * <p>
     * This value is used to initialize fields to their default values.
     *
     * <p>
     * The implementation in {@link GeneratedCustomField} attempts to invoke a default constructor for the bean class.
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

// HasInternalValidator

    @Override
    public Validator<T> getInternalValidator() {

        // Create an entirely separate binder and set of fields just for validation
        final AbstractFieldBuilder<?, T> validationFieldBuilder = this.createFieldBuilder();
        final Binder<T> validationBinder = this.createBinder();
        validationFieldBuilder.bindFields(validationBinder);

        // Return Validator that uses them
        return (bean, ctx) -> {
            if (bean == null)
                return ValidationResult.ok();               // if null is an invalid value, presumably that's handled elsewhere
            validationBinder.setBean(bean);
            try {
                return this.buildValidationResult(validationBinder.validate());
            } finally {
                validationBinder.removeBean();
            }
        };
    }

    /**
     * Build a {@link ValidationResult} from the given {@link BinderValidationStatus}s.
     *
     * <p>
     * The implementation in {@code GeneratedCustomField} simply returns an "Invalid" result if the given
     * status contains any errors, otherwise {@link ValidationResult#ok}.
     *
     * @param status validation status from internal binder
     * @return {@link ValidationResult} encapsulating {@code status} to be forwarded to the outer binder
     * @throws IllegalArgumentException if {@code status} is null
     */
    protected ValidationResult buildValidationResult(BinderValidationStatus<T> status) {
        Preconditions.checkArgument(status != null, "null status");
        return status.isOk() ? ValidationResult.ok() : ValidationResult.error("Invalid");
    }

// CustomField

    @Override
    protected T generateModelValue() {

        // Create a new bean instance
        final T bean = this.createNewBean();

        // Write fields into bean where possible
        this.binder.writeBeanAsDraft(bean);

        // Done
        return bean;
    }

    @Override
    protected void setPresentationValue(T value) {
        final T target = Optional.ofNullable(value).orElseGet(this::createNewBean);
        this.binder.readBean(target);
    }
}
