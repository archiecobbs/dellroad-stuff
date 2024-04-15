
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Optional;

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
 * <p>
 * If you don't need to do any customization, you can use {@link FieldBuilderCustomField} directly; the field's
 * value type will be inferred by the {@link #FieldBuilderCustomField(FieldBuilderContext)} constructor:
 *
 * <pre><code class="language-java">
 * public class Contract {
 *
 *     &#64;FieldBuilder.CheckBox(label = "Approved?")
 *     public boolean isApproved() { ... }
 *     public void setApproved(boolean approved) { ... }
 *
 *     // "DateInterval" value type is inferred from the method's return type
 *     &#64;FieldBuilder.CustomField(label = "Term", <b>implementation = FieldBuilderCustomField.class</b>)
 *     public DateInterval getTerm() { ... }
 *     public void setTerm(DateInterval term) { ... }
 * }
 * </code></pre>
 *
 * <p><b>Edit in Dialog Window</b>
 *
 * <p>
 * For more complex value types, this class support an alternative display model in which the value's sub-fields
 * are edited in a {@link Dialog} window instead of inline in the form. In lieu of the inline sub-fields, the form
 * displays an "Edit" button which, when clicked, opens a new {@link Dialog} window into which the sub-fields
 * are laid out, plus "OK" and "Cancel" buttons.
 *
 * <p>
 * To configure an edit dialog, add a {@link FieldBuilderCustomField.DialogForm &#64;FieldBuilderCustomField.DialogForm}
 * annotation to the method:
 *
 * <pre><code class="language-java">
 * public class Contract {
 *
 *     &#64;FieldBuilder.CheckBox(label = "Approved?")
 *     public boolean isApproved() { ... }
 *     public void setApproved(boolean approved) { ... }
 *
 *     &#64;FieldBuilder.CustomField(label = "Term", implementation = FieldBuilderCustomField.class)
 *     <b>&#64;FieldBuilderCustomField.DialogForm(windowTitle = "Contract Term")</b>
 *     public DateInterval getTerm() { ... }
 *     public void setTerm(DateInterval term) { ... }
 * }
 * </code></pre>
 *
 * <p><b>Nullable Values</b>
 *
 * <p>
 * When the field's value can be null, consider adding a
 * {@link AbstractFieldBuilder.NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox} annotation.
 *
 * @param <T> field value type
 */
@SuppressWarnings("serial")
public class FieldBuilderCustomField<T> extends BinderCustomField<T> {

    protected final DialogForm dialogForm;

    /**
     * The field builder that builds this instance's sub-fields.
     */
    protected AbstractFieldBuilder<?, T> fieldBuilder;

    /**
     * Auto-configure Constructor.
     *
     * <p>
     * This constructor will infer the target type and find any {@link DialogForm &#64;FieldBuilderCustomField.DialogForm}
     * annotation by inspecting the annotated method.
     *
     * @param ctx field builder context
     * @throws NullPointerException if {@code ctx} is null
     */
    @SuppressWarnings("unchecked")
    public FieldBuilderCustomField(FieldBuilderContext ctx) {
        this((Class<T>)ctx.inferDataModelType(), ctx.getMethod().getAnnotation(DialogForm.class));
    }

    /**
     * Constructor.
     *
     * @param modelType model type to introspect for {@link AbstractFieldBuilder} annotations
     * @throws IllegalArgumentException if {@code modelType} is null
     */
    public FieldBuilderCustomField(Class<T> modelType) {
        this(modelType, null);
    }

    /**
     * Constructor.
     *
     * @param modelType model type to introspect for {@link AbstractFieldBuilder} annotations
     * @param dialogForm configuration for using a {@link Dialog} window to edit the field value, or null for inline editing
     * @throws IllegalArgumentException if {@code modelType} is null
     */
    public FieldBuilderCustomField(Class<T> modelType, DialogForm dialogForm) {
        super(modelType);
        this.dialogForm = dialogForm;
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
     * Layout the components required for this field.
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField} delegates to {@link #layoutInlineComponents()}
     * if {@code #dialogForm} is null, otherwise {@link #layoutNonInlineComponents()}.
     */
    protected void layoutComponents() {
        if (this.dialogForm == null)
            this.layoutInlineComponents();
        else
            this.layoutNonInlineComponents();
    }

    /**
     * Layout components required for this field when a separate edit dialog window is not being used.
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField} iterates the bound fields in {@link #fieldBuilder}
     * into a new {@link HorizontalLayout} which is then added to this instance.
     */
    protected void layoutInlineComponents() {
        final HorizontalLayout layout = new HorizontalLayout();
        this.add(layout);
        this.fieldBuilder.getFieldComponents().values().stream()
          .map(FieldComponent::getComponent)
          .forEach(layout::add);
    }

    /**
     * Layout components required for this field when a separate edit dialog window is being used.
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField}
     */
    protected void layoutNonInlineComponents() {
        final HorizontalLayout layout = new HorizontalLayout();
        this.add(layout);
        final Button editButton = new Button(this.dialogForm.editButtonLabel(), e -> this.editButtonPressed());
        layout.add(editButton);
    }

    protected void editButtonPressed() {

        // Get current value, which must not be null, else resort to a new bean
        final T bean = Optional.ofNullable(this.getValue())
          .orElseGet(this::createNewBean);

        // Open dialog to edit it
        this.openEditDialog(bean);
    }

    /**
     * Open a dialog window containing a form for editing the given value.
     *
     * @param bean field value to edit
     * @throws IllegalArgumentException if {@code bean} is null
     * @throws IllegalStateException if {@code this.dialogForm} is null
     */
    protected void openEditDialog(T bean) {

        // Sanity check
        Preconditions.checkArgument(bean != null, "null bean");
        Preconditions.checkState(this.dialogForm != null, "no @DialogForm");

        // Create dialog window
        final Dialog dialog = new Dialog(this.dialogForm.windowTitle());
        dialog.setModal(false);             // workaround for https://github.com/vaadin/flow-components/issues/6052
        dialog.setHeaderTitle(this.dialogForm.windowTitle());

        // Create form
        final FormLayout formLayout = new FormLayout();

        // Add fields to form
        this.layoutEditDialogFields(formLayout);

        // Add buttons
        final Button submitButton = new Button(this.dialogForm.submitButtonLabel(), e -> {
            if (this.submitEditDialog(bean))
                dialog.close();
        });
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.addClickShortcut(Key.ENTER);
        final Button cancelButton = new Button(this.dialogForm.cancelButtonLabel(), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelButton.addClickShortcut(Key.ESCAPE);
        formLayout.addFormItem(new HorizontalLayout(submitButton, cancelButton), "");

        // Add form and open dialog window
        dialog.add(formLayout);
        dialog.open();
    }

    protected boolean submitEditDialog(T bean) {
        Preconditions.checkArgument(bean != null, "null bean");
        return this.binder.writeBeanIfValid(bean);
    }

    /**
     * Layout components required for this field in a separate edit dialog window.
     *
     * <p>
     * The implementation in {@link FieldBuilderCustomField} delegtes to {@link FieldBuilder#addFieldComponents}.
     *
     * @param formLayout form
     */
    protected void layoutEditDialogFields(FormLayout formLayout) {
        this.fieldBuilder.addFieldComponents(formLayout);
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
            throw new IllegalArgumentException(String.format("not an AbstractField: %s", name));
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
            throw new IllegalArgumentException(String.format("no such field: %s", name));
        return fieldComponent;
    }

// DialogForm

    public @interface DialogForm {

        /**
         * Title for the dialog window.
         */
        String windowTitle() default "";

        /**
         * Edit button label.
         */
        String editButtonLabel() default "Edit";

        /**
         * Submit button label.
         */
        String submitButtonLabel() default "OK";

        /**
         * Cancel button label.
         */
        String cancelButtonLabel() default "Cancel";
    }
}
