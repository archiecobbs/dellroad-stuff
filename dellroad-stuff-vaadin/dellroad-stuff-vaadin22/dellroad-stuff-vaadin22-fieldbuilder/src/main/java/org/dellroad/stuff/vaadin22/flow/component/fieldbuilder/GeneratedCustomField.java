
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component.fieldbuilder;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.binder.Binder;

/**
 * Support superclass that mostly automates the creation of {@link CustomField}s for editing any model type
 * having {@link FieldBuilder} annotations, using automatically generated sub-fields.
 *
 * <p>
 * This field uses a {@link FieldBuilder} to generate sub-fields for the annotated properties of a user-supplied model class.
 * As with any other {@link CustomField}, this allows editing instances of the model class as a single property within
 * some containing class. The containing class could then specify this class as the editor for the corresponding property
 * using a {@link FieldBuilder.CustomField &#64;FieldBuilder.CustomField} annotation.
 *
 * <p>
 * Internally each {@link GeneratedCustomField} contains a separate {@link Binder} for its sub-fields. This "inner" {@link Binder}
 * is linked for validation to the "outer" {@link Binder} for the containing class by way of this class implementing
 * {@link HasBinder} (see {@link FieldBuilder} for details).
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
 *             ValidationResult.ok() : ValidationResult.error("Start date must be before end date"));
 *     }
 *
 *     // Customize how we want to layout the subfields
 *     &#64;Override
 *     protected void layoutComponents() {
 *         this.add(this.fieldBuilder.getBoundFields().get("startDate").getComponent());
 *         this.add(new Text(" - "));
 *         this.add(this.fieldBuilder.getBoundFields().get("endDate").getComponent());
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
public class GeneratedCustomField<T> extends CustomField<T> implements HasBinder<T> {

    /**
     * The field value type.
     */
    protected final Class<T> modelType;

    /**
     * The field builder that builds this instance's sub-fields.
     */
    protected final AbstractFieldBuilder<?, T> fieldBuilder;

    /**
     * The binder for this instance's sub-fields.
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
     * Subclasses can override this method to add additional validators, etc.
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

// HasBinder

    @Override
    public Binder<T> getBinder() {
        return this.binder;
    }

// CustomField

    @Override
    protected T generateModelValue() {

        // Create a new bean instance
        final T bean;
        try {
            bean = modelType.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("unexpected error invoking " + this.modelType + " constructor", e);
        }

        // Write fields into bean where possible
        this.binder.writeBeanAsDraft(bean);

        // Done
        return bean;
    }

    @Override
    protected void setPresentationValue(T value) {
        this.binder.setBean(value);
    }
}
