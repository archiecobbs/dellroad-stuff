
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Support superclass that mostly automates the creation of {@link CustomField}s for editing any model type
 * using sub-fields automatically generated from {@link FieldBuilder} annotations to arbitrary recursion depth.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * This class is just a {@link BinderCustomField} that uses a {@link FieldBuilder} to generate the sub-fields.
 *
 * <p><b>Example</b>
 *
 * <p>
 * Suppose model class {@code Contract} has a "term" property of type {@code DateInterval} which has a start and end date.
 * We want to use {@link FieldBuilder} annotations to define the editor fields for the properties of {@code Contract},
 * including {@code "term"}, but for that to work we will need to specify a custom field to handle the {@code "term"} property.
 * We also want this custom field to contain the logic for laying out the two date picker components as well as for
 * validating proper ordering.
 *
 * <p>
 * For example:
 *
 * <pre><code class="language-java">
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
 * // This is the corresponding custom field
 * public class DateIntervalField extends FieldBuilderCustomField&lt;DateInterval&gt; {
 *
 *     public DateIntervalField() {
 *         super(DateInterval.class);
 *     }
 *
 *     // Customize how we want to layout the subfields
 *     &#64;Override
 *     protected void layoutComponents() {
 *        this.add(new HorizontalLayout(
 *          new Text("From"), this.getField("startDate")), new Text("to"), this.getField("endDate"));
 *     }
 *
 *     // Bean level validation: ensure end date is after start date
 *     &#64;Override
 *     public ValidationResult validate(DateInterval dates, ValueContext ctx) {
 *         if (dates.getStartDate().isAfter(dates.getEndDate())
 *             return ValidationResult.error("Dates out-of-order"));
 *         return super.validate(dates, ctx);           // always ensure superclass contraints apply also
 *     }
 * }
 * </code></pre>
 *
 * <p>
 * Once that's done, using {@link FieldBuilder} works recursively and automatically for this multi-level class:
 *
 * <pre><code class="language-java">
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
 * </code></pre>
 *
 * @param <T> field value type
 */
@SuppressWarnings("serial")
public class FieldBuilderCustomField<T> extends BinderCustomField<T> {

    /**
     * The field builder that builds this instance's sub-fields.
     */
    protected AbstractFieldBuilder<?, T> fieldBuilder;

    /**
     * Constructor.
     *
     * @param modelType model type to introspect for {@link AbstractFieldBuilder} annotations
     * @throws IllegalArgumentException if {@code modelType} is null
     */
    public FieldBuilderCustomField(Class<T> modelType) {
        super(modelType);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField} just invokes
     * {@link FieldBuilder#bindFields FieldBuilder.bindFields}{@code (this.binder)}.
     */
    @Override
    protected void createAndBindFields() {
        this.fieldBuilder = this.createFieldBuilder();
        this.fieldBuilder.bindFields(this.binder);
    }

    /**
     * Create a new {@link AbstractFieldBuilder} for the given type.
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField} returns a new {@link FieldBuilder} each time.
     *
     * @return field builder
     */
    protected AbstractFieldBuilder<?, T> createFieldBuilder() {
        return new FieldBuilder<>(this.modelType);
    }

    /**
     * Layout components required for this field.
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField} iterates the bound fields in {@link #fieldBuilder}
     * into a new {@link HorizontalLayout} which is then added to this instance.
     */
    protected void layoutComponents() {
        final HorizontalLayout layout = new HorizontalLayout();
        this.add(layout);
        this.fieldBuilder.getFieldComponents().values().stream()
          .map(FieldComponent::getComponent)
          .forEach(layout::add);
    }

    /**
     * Get the {@link FieldComponent} sub-field corresponding to the given field name.
     *
     * @param name field name
     * @return corresponding {@link FieldComponent}
     * @throws IllegalArgumentException if {@code name} is not found
     * @throws IllegalArgumentException if {@code name} is null
     */
    protected AbstractField<?, ?> getField(String name) {
        final FieldComponent<?> fieldComponent = this.getFieldComponent(name);
        try {
            return (AbstractField<?, ?>)fieldComponent.getField();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("not an AbstractField: " + name);
        }
    }

    /**
     * Get the {@link AbstractField} sub-field corresponding to the given field name.
     *
     * @param name field name
     * @return corresponding {@link AbstractField}
     * @throws IllegalArgumentException if {@code name} is not found
     * @throws IllegalArgumentException if {@code name}'s {@linkplain FieldComponent#getField field} is not an {@link AbstractField}
     * @throws IllegalArgumentException if {@code name} is null
     */
    protected FieldComponent<?> getFieldComponent(String name) {
        if (name == null)
            throw new IllegalArgumentException("null name");
        final FieldComponent<?> fieldComponent = this.fieldBuilder.getFieldComponents().get(name);
        if (fieldComponent == null)
            throw new IllegalArgumentException("no such field: " + name);
        return fieldComponent;
    }
}
