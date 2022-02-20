
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.fieldbuilder;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.converter.Converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dellroad.stuff.vaadin22.component.ValidatingField;

/**
 * Automatically configures and bind fields using declarative method annotations.
 *
 * <p>
 * {@link FieldBuilder} annotations allow for the automatic construction and configuration of fields for editing a bean.
 * Annotations on "getter" methods specify how the fields that edit the corresponding bean property should be constructed,
 * configured, and bound to a {@link Binder}. This allows all information about how to edit a Java type
 * to be specified declaratively. Annotations are also provided to configure how to add the geenrated fields to a
 * {@link FormLayout}.
 *
 * <p><b>{@code @FieldBuilder.Foo} vs. {@code @ProvidesField}</b>
 *
 * <p>
 * {@link FieldBuilder} supports two types of annotations from which it infers how to construct and configure a field
 * for editing an associated bean property.
 *
 * <p>
 * The various {@code @FieldBuilder.Foo} annotations are the purely declarative way to specify how to construct a field.
 * Each annotation corresponds to a specific field class (e.g., {@link TextField &#64;FieldBuilder.TextField}
 * configures a {@link com.vaadin.flow.component.textfield.TextField}). The annotation's properties parallel the
 * properties of the field and specify how to construct, configure, and bind an instance of the field.
 * The annotation annotates the bean property's "getter" method.
 *
 * <p>
 * {@link AbstractFieldBuilder.ProvidesField &#64;ProvidesField} provides a more general approach, but it requires writing code.
 * Use {@link AbstractFieldBuilder.ProvidesField &#64;ProvidesField} on a method that itself knows how to build a field suitable
 * for editing the named property. The method should return a component for editing the property. Both instance and static
 * methods are supported; instance methods require that the {@link Binder} being used has a bound bean.
 *
 * <p>
 * In all cases, an annotation on a subclass method will override the same annotation on the corresponding superclass method.
 *
 * <p>
 * Fields defined from these annotations are created, configured, and bound via {@link #bindFields bindFields()}.
 *
 * <p><b>Configuring the Binding</b>
 *
 * <p>
 * In addition to constructing and configuring the fields associated with each bean property into the {@link Binder},
 * you may also want to configure the bindings themselves, for example, to specify a {@link Converter} or {@link Validator}.
 * The {@link AbstractFieldBuilder.Binding &#64;FieldBuilder.Binding} annotation allows you to configure the
 * binding using properties corresponding to methods in {@link Binder.BindingBuilder}.
 *
 * <p><b>Adding Fields to a {@link FormLayout}</b>
 *
 * <p>
 * The {@link FieldBuilder.FormLayout &#64;FieldBuilder.FormLayout} annotation allows you to configure field labels,
 * column span, and ordering of fields in a {@link FormLayout}.
 *
 * <p>
 * Fields are added to a {@link FormLayout} via {@link #addBoundFields addBoundFields()}.
 *
 * <p><b>Example</b>
 *
 * <p>
 * A simple example shows how these annotations are used:
 * <blockquote><pre>
 * <b>&#64;FieldBuilder.TextField(placeholder = "Enter your name...", maxLength = 64)</b>
 * <b>&#64;FieldBuilder.Binding(required = "Name is mandatory", validators = MyValidator.class)</b>
 * <b>&#64;FieldBuilder.FormLayout(label = "Name:", colspan = 2, order = 1)</b>
 * &#64;NotNull
 * public String getName() { ... }
 *
 * <b>&#64;FieldBuilder.EnumComboBox</b>
 * <b>&#64;FieldBuilder.Binding(required = "Status is mandatory")</b>
 * <b>&#64;FieldBuilder.FormLayout(label = "Status:", order = 2)</b>
 * &#64;NotNull
 * public Status getStatus() { ... }
 *
 * // A property that can't be edited with existing fields
 * public Foobar getFoobar() { ... }
 *
 * // Instead, use my own custom field to edit "foobar"
 * <b>&#64;FieldBuilder.ProvidesField("foobar")</b>
 * <b>&#64;FieldBuilder.FormLayout(label = "Your Foobar:", order = 3)</b>
 * private static CustomField&lt;Foobar&gt; createFoobarField() { ... }
 * </pre></blockquote>
 *
 * <p>
 * All of the declarative {@code @FieldBuilder.Foo} annotations have an {@code implementation()} property that allows you
 * to specify a custom implementation. So a slightly different way to specify a custom field to edit the {@code "foobar"}
 * property would be:
 *
 * <blockquote><pre>
 * // Use my own custom FoobarField to edit "foobar"
 * <b>&#64;FieldBuilder.CustomField(label = "Your Foobar:", implementation = FoobarField.class)</b>
 * public Foobar getFoobar() { ... }
 * </pre></blockquote>
 *
 * <p><b>Building the Form</b>
 *
 * <p>
 * First, use {@link #bindFields bindFields()} to create and configure a new set of fields, and bind them into a {@link Binder}:
 *
 * <blockquote><pre>
 * // Create a FieldBuilder
 * <b>FieldBuilder&lt;Person&gt; fieldBuilder = new FieldBuilder&lt;&gt;(Person.class);</b>
 *
 * // Create a Binder and bind fields
 * Binder&lt;Person&gt; binder = new Binder&lt;&gt;(Person.class);
 * <b>fieldBuilder.bindFields(binder)</b>;
 * </pre></blockquote>
 *
 * <p>
 * Then (optionally) use {@link #addBoundFields addBoundFields()} to add and configure those fields into a {@link FormLayout}:
 *
 * <blockquote><pre>
 * // Create form and add fields to it
 * FormLayout form = new FormLayout();
 * <b>fieldBuilder.addBoundFields(form);</b>
 * </pre></blockquote>
 *
 * <p>
 * You can also access the fields directly via {@link #getBoundFields getBoundFields()}.
 *
 * <p>
 * A {@link FieldBuilder} can be used multiple times. Each time {@link #bindFields bindFields()} is invoked a new set
 * of fields is created.
 *
 * <p><b>Alternate Defaults</b>
 *
 * <p>
 * You can override the default value for specific field properties on a per-property-name basis by annotating static methods
 * in the edited model class with {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault}.
 *
 * <p>
 * For example:
 *
 * <blockquote><pre>
 * public class Person {
 *     public String getFirstName() { ... }
 *     public String getLastName() { ... }
 *
 *     <b>@FieldBuilder.FieldDefault("itemLabelGenerator")</b>
 *     private static ItemLabelGenerator&lt;Person&gt; buildPersonILG() {
 *         return person -&gt; person.getLastName() + ", " + person.getFirstName();
 *     }
 * }
 * </pre></blockquote>
 *
 * <p>
 * These defaults can be accessed via {@link #getScannedFieldDefaults getScannedFieldDefaults()}.
 *
 * <p>
 * See {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault} for details.
 *
 * <p><b>Recursive Validation</b>
 *
 * <p>
 * To facilitate nesting/recursion of fields, fields that implement {@link ValidatingField} will be automatically registered
 * as a field {@link Validator} by {@link #bindFields bindFields()} when the field is bound. This allows for more modularity
 * with respect to validation when nested types having sub-fields are in use. See also {@link FieldBuilderCustomField}, which
 * relies on this mechanism.
 *
 * <p><b>Homebrew Your Own</b>
 * <p>
 * You can create your own version of this class containing auto-generated annotations for whatever classes you want
 * simply by subclassing {@link AbstractFieldBuilder} and applying a Maven plugin. See source code for details.
 *
 * @param <T> backing object type
 * @see Checkbox
 * @see CheckboxGroup
 * @see ComboBox
 * @see DatePicker
 * @see DateTimePicker
 * @see Input
 * @see GridMultiSelect
 * @see GridSingleSelect
 * @see ListBox
 * @see MultiSelectListBox
 * @see RadioButtonGroup
 * @see Select
 * @see BigDecimalField
 * @see EmailField
 * @see IntegerField
 * @see NumberField
 * @see PasswordField
 * @see TextArea
 * @see TextField
 * @see TimePicker
 * @see EnumComboBox
 */
public class FieldBuilder<T> extends AbstractGridFieldBuilder<FieldBuilder<T>, T> {

    private static final long serialVersionUID = -4876472481099484174L;

    /**
     * Constructor.
     *
     * @param type backing object type
     */
    public FieldBuilder(Class<T> type) {
        super(type);
    }

    /**
     * Static information copy constructor.
     *
     * <p>
     * Only the static information gathered by this instance by scanning for annotations is copied.
     * Any previously bound fields are not copied.
     *
     * @param original original instance
     * @throws IllegalArgumentException if {@code original} is null
     */
    public FieldBuilder(FieldBuilder<T> original) {
        super(original);
    }

// EVERYTHING BELOW THIS LINE IS GENERATED

    @Checkbox
    @CheckboxGroup
    @ComboBox
    @CustomField(implementation = com.vaadin.flow.component.customfield.CustomField.class)
    @DatePicker
    @DateTimePicker
    @Input
    @ListBox
    @MultiSelectListBox
    @RadioButtonGroup
    @Select
    @BigDecimalField
    @EmailField
    @IntegerField
    @NumberField
    @PasswordField
    @TextArea
    @TextField
    @TimePicker
    @EnumComboBox
    private static java.util.List<Class<? extends java.lang.annotation.Annotation>> annotationDefaultsMethod() {
        return java.util.Arrays.asList(
            Checkbox.class,
            CheckboxGroup.class,
            ComboBox.class,
            CustomField.class,
            DatePicker.class,
            DateTimePicker.class,
            Input.class,
            ListBox.class,
            MultiSelectListBox.class,
            RadioButtonGroup.class,
            Select.class,
            BigDecimalField.class,
            EmailField.class,
            IntegerField.class,
            NumberField.class,
            PasswordField.class,
            TextArea.class,
            TextField.class,
            TimePicker.class,
            EnumComboBox.class
        );
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.checkbox.Checkbox}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.checkbox.Checkbox
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Checkbox {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.checkbox.Checkbox} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.checkbox.Checkbox> implementation() default com.vaadin.flow.component.checkbox.Checkbox.class;

        /**
         * Get the value desired for the {@code ariaLabel} property.
         *
         * @return desired {@code ariaLabel} property value
         * @see com.vaadin.flow.component.checkbox.Checkbox#setAriaLabel(String)
         */
        String ariaLabel() default "";

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.checkbox.Checkbox#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code indeterminate} property.
         *
         * @return desired {@code indeterminate} property value
         * @see com.vaadin.flow.component.checkbox.Checkbox#setIndeterminate(boolean)
         */
        boolean indeterminate() default false;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.checkbox.Checkbox#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code labelAsHtml} property.
         *
         * @return desired {@code labelAsHtml} property value
         * @see com.vaadin.flow.component.checkbox.Checkbox#setLabelAsHtml(String)
         */
        String labelAsHtml() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.checkbox.CheckboxGroup}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.checkbox.CheckboxGroup
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface CheckboxGroup {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.checkbox.CheckboxGroup} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.checkbox.CheckboxGroup> implementation() default com.vaadin.flow.component.checkbox.CheckboxGroup.class;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemEnabledProvider} property.
         *
         * @return desired {@code itemEnabledProvider} property value type
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setItemEnabledProvider(com.vaadin.flow.function.SerializablePredicate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.function.SerializablePredicate> itemEnabledProvider() default com.vaadin.flow.function.SerializablePredicate.class;

        /**
         * Get the class to instantiate for the {@code itemLabelGenerator} property.
         *
         * @return desired {@code itemLabelGenerator} property value type
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setItemLabelGenerator(com.vaadin.flow.component.ItemLabelGenerator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.ItemLabelGenerator> itemLabelGenerator() default com.vaadin.flow.component.ItemLabelGenerator.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.checkbox.CheckboxGroup#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.checkbox.GeneratedVaadinCheckboxGroup#addThemeVariants(com.vaadin.flow.component.checkbox.CheckboxGroupVariant[])
         */
        com.vaadin.flow.component.checkbox.CheckboxGroupVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.combobox.ComboBox}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.combobox.ComboBox
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface ComboBox {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.combobox.ComboBox} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.combobox.ComboBox> implementation() default com.vaadin.flow.component.combobox.ComboBox.class;

        /**
         * Get the value desired for the {@code allowCustomValue} property.
         *
         * @return desired {@code allowCustomValue} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setAllowCustomValue(boolean)
         */
        boolean allowCustomValue() default false;

        /**
         * Get the value desired for the {@code autoOpen} property.
         *
         * @return desired {@code autoOpen} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setAutoOpen(boolean)
         */
        boolean autoOpen() default true;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemLabelGenerator} property.
         *
         * @return desired {@code itemLabelGenerator} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setItemLabelGenerator(com.vaadin.flow.component.ItemLabelGenerator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.ItemLabelGenerator> itemLabelGenerator() default com.vaadin.flow.component.ItemLabelGenerator.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code opened} property.
         *
         * @return desired {@code opened} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setOpened(boolean)
         */
        boolean opened() default false;

        /**
         * Get the value desired for the {@code pageSize} property.
         *
         * @return desired {@code pageSize} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPageSize(int)
         */
        int pageSize() default 50;

        /**
         * Get the value desired for the {@code pattern} property.
         *
         * @return desired {@code pattern} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPattern(String)
         */
        String pattern() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the class to instantiate for the {@code renderer} property.
         *
         * @return desired {@code renderer} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setRenderer(com.vaadin.flow.data.renderer.Renderer)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.renderer.Renderer> renderer() default com.vaadin.flow.data.renderer.Renderer.class;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.combobox.ComboBox#addThemeVariants(com.vaadin.flow.component.combobox.ComboBoxVariant[])
         */
        com.vaadin.flow.component.combobox.ComboBoxVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.customfield.CustomField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.customfield.CustomField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface CustomField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.customfield.CustomField} that will edit the property.
         *
         * <p>
         * This property is required because {@link com.vaadin.flow.component.customfield.CustomField} is an abstract class.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.customfield.CustomField> implementation();

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.customfield.CustomField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.customfield.CustomField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.datepicker.DatePicker}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.datepicker.DatePicker
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface DatePicker {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.datepicker.DatePicker} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.datepicker.DatePicker> implementation() default com.vaadin.flow.component.datepicker.DatePicker.class;

        /**
         * Get the value desired for the {@code autoOpen} property.
         *
         * @return desired {@code autoOpen} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setAutoOpen(boolean)
         */
        boolean autoOpen() default true;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the class to instantiate for the {@code i18n} property.
         *
         * @return desired {@code i18n} property value type
         * @see com.vaadin.flow.component.datepicker.DatePicker#setI18n(com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n> i18n() default com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n.class;

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code initialPosition} property.
         *
         * @return desired {@code initialPosition} property value type
         * @see com.vaadin.flow.component.datepicker.DatePicker#setInitialPosition(java.time.LocalDate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalDate> initialPosition() default java.time.LocalDate.class;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setLabel(String)
         */
        String label() default "";

        /**
         * Get the class to instantiate for the {@code locale} property.
         *
         * @return desired {@code locale} property value type
         * @see com.vaadin.flow.component.datepicker.DatePicker#setLocale(java.util.Locale)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.util.Locale> locale() default java.util.Locale.class;

        /**
         * Get the class to instantiate for the {@code max} property.
         *
         * @return desired {@code max} property value type
         * @see com.vaadin.flow.component.datepicker.DatePicker#setMax(java.time.LocalDate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalDate> max() default java.time.LocalDate.class;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the class to instantiate for the {@code min} property.
         *
         * @return desired {@code min} property value type
         * @see com.vaadin.flow.component.datepicker.DatePicker#setMin(java.time.LocalDate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalDate> min() default java.time.LocalDate.class;

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code name} property.
         *
         * @return desired {@code name} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setName(String)
         */
        String name() default "";

        /**
         * Get the value desired for the {@code opened} property.
         *
         * @return desired {@code opened} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setOpened(boolean)
         */
        boolean opened() default false;

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.datepicker.DatePicker#addThemeVariants(com.vaadin.flow.component.datepicker.DatePickerVariant[])
         */
        com.vaadin.flow.component.datepicker.DatePickerVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code weekNumbersVisible} property.
         *
         * @return desired {@code weekNumbersVisible} property value
         * @see com.vaadin.flow.component.datepicker.DatePicker#setWeekNumbersVisible(boolean)
         */
        boolean weekNumbersVisible() default false;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.datetimepicker.DateTimePicker}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.datetimepicker.DateTimePicker
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface DateTimePicker {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.datetimepicker.DateTimePicker} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.datetimepicker.DateTimePicker> implementation() default com.vaadin.flow.component.datetimepicker.DateTimePicker.class;

        /**
         * Get the value desired for the {@code autoOpen} property.
         *
         * @return desired {@code autoOpen} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setAutoOpen(boolean)
         */
        boolean autoOpen() default true;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the class to instantiate for the {@code datePickerI18n} property.
         *
         * @return desired {@code datePickerI18n} property value type
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setDatePickerI18n(com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n> datePickerI18n() default com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n.class;

        /**
         * Get the value desired for the {@code datePlaceholder} property.
         *
         * @return desired {@code datePlaceholder} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setDatePlaceholder(String)
         */
        String datePlaceholder() default "";

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setLabel(String)
         */
        String label() default "";

        /**
         * Get the class to instantiate for the {@code locale} property.
         *
         * @return desired {@code locale} property value type
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setLocale(java.util.Locale)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.util.Locale> locale() default java.util.Locale.class;

        /**
         * Get the class to instantiate for the {@code max} property.
         *
         * @return desired {@code max} property value type
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setMax(java.time.LocalDateTime)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalDateTime> max() default java.time.LocalDateTime.class;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the class to instantiate for the {@code min} property.
         *
         * @return desired {@code min} property value type
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setMin(java.time.LocalDateTime)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalDateTime> min() default java.time.LocalDateTime.class;

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the class to instantiate for the {@code step} property.
         *
         * @return desired {@code step} property value type
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setStep(java.time.Duration)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.Duration> step() default java.time.Duration.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Get the value desired for the {@code timePlaceholder} property.
         *
         * @return desired {@code timePlaceholder} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setTimePlaceholder(String)
         */
        String timePlaceholder() default "";

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code weekNumbersVisible} property.
         *
         * @return desired {@code weekNumbersVisible} property value
         * @see com.vaadin.flow.component.datetimepicker.DateTimePicker#setWeekNumbersVisible(boolean)
         */
        boolean weekNumbersVisible() default false;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.html.Input}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.html.Input
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Input {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.html.Input} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.html.Input> implementation() default com.vaadin.flow.component.html.Input.class;

        /**
         * Get the value desired for the {@code ariaLabel} property.
         *
         * @return desired {@code ariaLabel} property value
         * @see com.vaadin.flow.component.HasAriaLabel#setAriaLabel(String)
         */
        String ariaLabel() default "";

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.html.Input#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the value desired for the {@code type} property.
         *
         * @return desired {@code type} property value
         * @see com.vaadin.flow.component.html.Input#setType(String)
         */
        String type() default "text";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.html.Input#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.html.Input#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.listbox.ListBox}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.listbox.ListBox
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface ListBox {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.listbox.ListBox} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.listbox.ListBox> implementation() default com.vaadin.flow.component.listbox.ListBox.class;

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemEnabledProvider} property.
         *
         * @return desired {@code itemEnabledProvider} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setItemEnabledProvider(com.vaadin.flow.function.SerializablePredicate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.function.SerializablePredicate> itemEnabledProvider() default com.vaadin.flow.function.SerializablePredicate.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the class to instantiate for the {@code renderer} property.
         *
         * @return desired {@code renderer} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setRenderer(com.vaadin.flow.data.renderer.ComponentRenderer)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.renderer.ComponentRenderer> renderer() default com.vaadin.flow.data.renderer.ComponentRenderer.class;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.listbox.MultiSelectListBox}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.listbox.MultiSelectListBox
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface MultiSelectListBox {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.listbox.MultiSelectListBox} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.listbox.MultiSelectListBox> implementation() default com.vaadin.flow.component.listbox.MultiSelectListBox.class;

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemEnabledProvider} property.
         *
         * @return desired {@code itemEnabledProvider} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setItemEnabledProvider(com.vaadin.flow.function.SerializablePredicate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.function.SerializablePredicate> itemEnabledProvider() default com.vaadin.flow.function.SerializablePredicate.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the class to instantiate for the {@code renderer} property.
         *
         * @return desired {@code renderer} property value type
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setRenderer(com.vaadin.flow.data.renderer.ComponentRenderer)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.renderer.ComponentRenderer> renderer() default com.vaadin.flow.data.renderer.ComponentRenderer.class;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.listbox.ListBoxBase#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.radiobutton.RadioButtonGroup}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface RadioButtonGroup {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.radiobutton.RadioButtonGroup} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.radiobutton.RadioButtonGroup> implementation() default com.vaadin.flow.component.radiobutton.RadioButtonGroup.class;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemEnabledProvider} property.
         *
         * @return desired {@code itemEnabledProvider} property value type
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setItemEnabledProvider(com.vaadin.flow.function.SerializablePredicate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.function.SerializablePredicate> itemEnabledProvider() default com.vaadin.flow.function.SerializablePredicate.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the class to instantiate for the {@code renderer} property.
         *
         * @return desired {@code renderer} property value type
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setRenderer(com.vaadin.flow.data.renderer.ComponentRenderer)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.renderer.ComponentRenderer> renderer() default com.vaadin.flow.data.renderer.ComponentRenderer.class;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.radiobutton.RadioButtonGroup#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.radiobutton.GeneratedVaadinRadioGroup#addThemeVariants(com.vaadin.flow.component.radiobutton.RadioGroupVariant[])
         */
        com.vaadin.flow.component.radiobutton.RadioGroupVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.select.Select}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.select.Select
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Select {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.select.Select} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.select.Select> implementation() default com.vaadin.flow.component.select.Select.class;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.select.Select#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.select.Select#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code emptySelectionAllowed} property.
         *
         * @return desired {@code emptySelectionAllowed} property value
         * @see com.vaadin.flow.component.select.Select#setEmptySelectionAllowed(boolean)
         */
        boolean emptySelectionAllowed() default false;

        /**
         * Get the value desired for the {@code emptySelectionCaption} property.
         *
         * @return desired {@code emptySelectionCaption} property value
         * @see com.vaadin.flow.component.select.Select#setEmptySelectionCaption(String)
         */
        String emptySelectionCaption() default "";

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.select.Select#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemEnabledProvider} property.
         *
         * @return desired {@code itemEnabledProvider} property value type
         * @see com.vaadin.flow.component.select.Select#setItemEnabledProvider(com.vaadin.flow.function.SerializablePredicate)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.function.SerializablePredicate> itemEnabledProvider() default com.vaadin.flow.function.SerializablePredicate.class;

        /**
         * Get the class to instantiate for the {@code itemLabelGenerator} property.
         *
         * @return desired {@code itemLabelGenerator} property value type
         * @see com.vaadin.flow.component.select.Select#setItemLabelGenerator(com.vaadin.flow.component.ItemLabelGenerator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.ItemLabelGenerator> itemLabelGenerator() default com.vaadin.flow.component.ItemLabelGenerator.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.select.Select#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.select.Select#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.select.Select#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the class to instantiate for the {@code renderer} property.
         *
         * @return desired {@code renderer} property value type
         * @see com.vaadin.flow.component.select.Select#setRenderer(com.vaadin.flow.data.renderer.ComponentRenderer)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.renderer.ComponentRenderer> renderer() default com.vaadin.flow.data.renderer.ComponentRenderer.class;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.select.Select#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the class to instantiate for the {@code textRenderer} property.
         *
         * @return desired {@code textRenderer} property value type
         * @see com.vaadin.flow.component.select.Select#setTextRenderer(com.vaadin.flow.component.ItemLabelGenerator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.ItemLabelGenerator> textRenderer() default com.vaadin.flow.component.ItemLabelGenerator.class;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.BigDecimalField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.BigDecimalField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface BigDecimalField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.BigDecimalField} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.BigDecimalField> implementation() default com.vaadin.flow.component.textfield.BigDecimalField.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the class to instantiate for the {@code locale} property.
         *
         * @return desired {@code locale} property value type
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setLocale(java.util.Locale)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.util.Locale> locale() default java.util.Locale.class;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextField#addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant[])
         */
        com.vaadin.flow.component.textfield.TextFieldVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code title} property.
         *
         * @return desired {@code title} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setTitle(String)
         */
        String title() default "";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.BigDecimalField#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.EmailField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.EmailField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface EmailField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.EmailField} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.EmailField> implementation() default com.vaadin.flow.component.textfield.EmailField.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxLength} property.
         *
         * @return desired {@code maxLength} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setMaxLength(int)
         */
        int maxLength() default 0;

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minLength} property.
         *
         * @return desired {@code minLength} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setMinLength(int)
         */
        int minLength() default 0;

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code pattern} property.
         *
         * @return desired {@code pattern} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setPattern(String)
         */
        String pattern() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextField#addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant[])
         */
        com.vaadin.flow.component.textfield.TextFieldVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code title} property.
         *
         * @return desired {@code title} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setTitle(String)
         */
        String title() default "";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.EmailField#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.IntegerField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.IntegerField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface IntegerField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.IntegerField} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.IntegerField> implementation() default com.vaadin.flow.component.textfield.IntegerField.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code hasControls} property.
         *
         * @return desired {@code hasControls} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setHasControls(boolean)
         */
        boolean hasControls() default false;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code max} property.
         *
         * @return desired {@code max} property value
         * @see com.vaadin.flow.component.textfield.IntegerField#setMax(int)
         */
        int max() default 2147483647;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code min} property.
         *
         * @return desired {@code min} property value
         * @see com.vaadin.flow.component.textfield.IntegerField#setMin(int)
         */
        int min() default -2147483648;

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code step} property.
         *
         * @return desired {@code step} property value
         * @see com.vaadin.flow.component.textfield.IntegerField#setStep(int)
         */
        int step() default 1;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextField#addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant[])
         */
        com.vaadin.flow.component.textfield.TextFieldVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code title} property.
         *
         * @return desired {@code title} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setTitle(String)
         */
        String title() default "";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.NumberField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.NumberField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface NumberField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.NumberField} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.NumberField> implementation() default com.vaadin.flow.component.textfield.NumberField.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code hasControls} property.
         *
         * @return desired {@code hasControls} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setHasControls(boolean)
         */
        boolean hasControls() default false;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code max} property.
         *
         * @return desired {@code max} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setMax(double)
         */
        double max() default Double.POSITIVE_INFINITY;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxLength} property.
         *
         * @return desired {@code maxLength} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setMaxLength(int)
         */
        int maxLength() default 0;

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code min} property.
         *
         * @return desired {@code min} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setMin(double)
         */
        double min() default Double.NEGATIVE_INFINITY;

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minLength} property.
         *
         * @return desired {@code minLength} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setMinLength(int)
         */
        int minLength() default 0;

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code pattern} property.
         *
         * @return desired {@code pattern} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setPattern(String)
         */
        String pattern() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code step} property.
         *
         * @return desired {@code step} property value
         * @see com.vaadin.flow.component.textfield.NumberField#setStep(double)
         */
        double step() default 1.0;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextField#addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant[])
         */
        com.vaadin.flow.component.textfield.TextFieldVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code title} property.
         *
         * @return desired {@code title} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setTitle(String)
         */
        String title() default "";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.AbstractNumberField#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.PasswordField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.PasswordField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface PasswordField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.PasswordField} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.PasswordField> implementation() default com.vaadin.flow.component.textfield.PasswordField.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxLength} property.
         *
         * @return desired {@code maxLength} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setMaxLength(int)
         */
        int maxLength() default 0;

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minLength} property.
         *
         * @return desired {@code minLength} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setMinLength(int)
         */
        int minLength() default 0;

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code pattern} property.
         *
         * @return desired {@code pattern} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setPattern(String)
         */
        String pattern() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code revealButtonVisible} property.
         *
         * @return desired {@code revealButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setRevealButtonVisible(boolean)
         */
        boolean revealButtonVisible() default true;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextField#addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant[])
         */
        com.vaadin.flow.component.textfield.TextFieldVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code title} property.
         *
         * @return desired {@code title} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setTitle(String)
         */
        String title() default "";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.PasswordField#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.TextArea}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.TextArea
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface TextArea {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.TextArea} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.TextArea> implementation() default com.vaadin.flow.component.textfield.TextArea.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxLength} property.
         *
         * @return desired {@code maxLength} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setMaxLength(int)
         */
        int maxLength() default 0;

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minLength} property.
         *
         * @return desired {@code minLength} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setMinLength(int)
         */
        int minLength() default 0;

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextArea#addThemeVariants(com.vaadin.flow.component.textfield.TextAreaVariant[])
         */
        com.vaadin.flow.component.textfield.TextAreaVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.TextArea#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.textfield.TextField}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.textfield.TextField
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface TextField {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.textfield.TextField} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.textfield.TextField> implementation() default com.vaadin.flow.component.textfield.TextField.class;

        /**
         * Get the value desired for the {@code autocapitalize} property.
         *
         * @return desired {@code autocapitalize} property value
         * @see com.vaadin.flow.component.textfield.HasAutocapitalize#setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize)
         */
        com.vaadin.flow.component.textfield.Autocapitalize autocapitalize() default com.vaadin.flow.component.textfield.Autocapitalize.NONE;

        /**
         * Get the value desired for the {@code autocomplete} property.
         *
         * @return desired {@code autocomplete} property value
         * @see com.vaadin.flow.component.textfield.HasAutocomplete#setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete)
         */
        com.vaadin.flow.component.textfield.Autocomplete autocomplete() default com.vaadin.flow.component.textfield.Autocomplete.ON;

        /**
         * Get the value desired for the {@code autocorrect} property.
         *
         * @return desired {@code autocorrect} property value
         * @see com.vaadin.flow.component.textfield.HasAutocorrect#setAutocorrect(boolean)
         */
        boolean autocorrect() default false;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.textfield.TextField#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Get the value desired for the {@code autoselect} property.
         *
         * @return desired {@code autoselect} property value
         * @see com.vaadin.flow.component.textfield.TextField#setAutoselect(boolean)
         */
        boolean autoselect() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.textfield.TextField#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.textfield.TextField#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.textfield.TextField#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxLength} property.
         *
         * @return desired {@code maxLength} property value
         * @see com.vaadin.flow.component.textfield.TextField#setMaxLength(int)
         */
        int maxLength() default 0;

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minLength} property.
         *
         * @return desired {@code minLength} property value
         * @see com.vaadin.flow.component.textfield.TextField#setMinLength(int)
         */
        int minLength() default 0;

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code pattern} property.
         *
         * @return desired {@code pattern} property value
         * @see com.vaadin.flow.component.textfield.TextField#setPattern(String)
         */
        String pattern() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.textfield.TextField#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the class to instantiate for the {@code prefixComponent} property.
         *
         * @return desired {@code prefixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setPrefixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> prefixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.textfield.TextField#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.textfield.TextField#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.textfield.TextField#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the class to instantiate for the {@code suffixComponent} property.
         *
         * @return desired {@code suffixComponent} property value type
         * @see com.vaadin.flow.component.textfield.HasPrefixAndSuffix#setSuffixComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> suffixComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.textfield.GeneratedVaadinTextField#addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant[])
         */
        com.vaadin.flow.component.textfield.TextFieldVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code title} property.
         *
         * @return desired {@code title} property value
         * @see com.vaadin.flow.component.textfield.TextField#setTitle(String)
         */
        String title() default "";

        /**
         * Get the value desired for the {@code valueChangeMode} property.
         *
         * @return desired {@code valueChangeMode} property value
         * @see com.vaadin.flow.component.textfield.TextField#setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode)
         */
        com.vaadin.flow.data.value.ValueChangeMode valueChangeMode() default com.vaadin.flow.data.value.ValueChangeMode.ON_CHANGE;

        /**
         * Get the value desired for the {@code valueChangeTimeout} property.
         *
         * @return desired {@code valueChangeTimeout} property value
         * @see com.vaadin.flow.component.textfield.TextField#setValueChangeTimeout(int)
         */
        int valueChangeTimeout() default 400;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link com.vaadin.flow.component.timepicker.TimePicker}.
     *
     * @see FieldBuilder
     * @see com.vaadin.flow.component.timepicker.TimePicker
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface TimePicker {

        /**
         * Get the sub-type of {@link com.vaadin.flow.component.timepicker.TimePicker} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.timepicker.TimePicker> implementation() default com.vaadin.flow.component.timepicker.TimePicker.class;

        /**
         * Get the value desired for the {@code autoOpen} property.
         *
         * @return desired {@code autoOpen} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setAutoOpen(boolean)
         */
        boolean autoOpen() default true;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setLabel(String)
         */
        String label() default "";

        /**
         * Get the class to instantiate for the {@code locale} property.
         *
         * @return desired {@code locale} property value type
         * @see com.vaadin.flow.component.timepicker.TimePicker#setLocale(java.util.Locale)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.util.Locale> locale() default java.util.Locale.class;

        /**
         * Get the class to instantiate for the {@code max} property.
         *
         * @return desired {@code max} property value type
         * @see com.vaadin.flow.component.timepicker.TimePicker#setMax(java.time.LocalTime)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalTime> max() default java.time.LocalTime.class;

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the class to instantiate for the {@code maxTime} property.
         *
         * @return desired {@code maxTime} property value type
         * @see com.vaadin.flow.component.timepicker.TimePicker#setMaxTime(java.time.LocalTime)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalTime> maxTime() default java.time.LocalTime.class;

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the class to instantiate for the {@code min} property.
         *
         * @return desired {@code min} property value type
         * @see com.vaadin.flow.component.timepicker.TimePicker#setMin(java.time.LocalTime)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalTime> min() default java.time.LocalTime.class;

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the class to instantiate for the {@code minTime} property.
         *
         * @return desired {@code minTime} property value type
         * @see com.vaadin.flow.component.timepicker.TimePicker#setMinTime(java.time.LocalTime)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.LocalTime> minTime() default java.time.LocalTime.class;

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.timepicker.TimePicker#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the class to instantiate for the {@code step} property.
         *
         * @return desired {@code step} property value type
         * @see com.vaadin.flow.component.timepicker.TimePicker#setStep(java.time.Duration)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends java.time.Duration> step() default java.time.Duration.class;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link org.dellroad.stuff.vaadin22.component.EnumComboBox}.
     *
     * @see FieldBuilder
     * @see org.dellroad.stuff.vaadin22.component.EnumComboBox
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface EnumComboBox {

        /**
         * Get the sub-type of {@link org.dellroad.stuff.vaadin22.component.EnumComboBox} that will edit the property.
         *
         * <p>
         * This property allows custom widget subclasses to be used.
         *
         * <p>
         * The specified type must have a public no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends org.dellroad.stuff.vaadin22.component.EnumComboBox> implementation() default org.dellroad.stuff.vaadin22.component.EnumComboBox.class;

        /**
         * Get the value desired for the {@code allowCustomValue} property.
         *
         * @return desired {@code allowCustomValue} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setAllowCustomValue(boolean)
         */
        boolean allowCustomValue() default false;

        /**
         * Get the value desired for the {@code autoOpen} property.
         *
         * @return desired {@code autoOpen} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setAutoOpen(boolean)
         */
        boolean autoOpen() default true;

        /**
         * Get the value desired for the {@code autofocus} property.
         *
         * @return desired {@code autofocus} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setAutofocus(boolean)
         */
        boolean autofocus() default false;

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see com.vaadin.flow.component.HasStyle#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Get the value desired for the {@code clearButtonVisible} property.
         *
         * @return desired {@code clearButtonVisible} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setClearButtonVisible(boolean)
         */
        boolean clearButtonVisible() default false;

        /**
         * Get the class to instantiate for the {@code dataProvider} property.
         *
         * @return desired {@code dataProvider} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setDataProvider(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> dataProvider() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see com.vaadin.flow.component.HasEnabled#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code errorMessage} property.
         *
         * @return desired {@code errorMessage} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setErrorMessage(String)
         */
        String errorMessage() default "";

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see com.vaadin.flow.component.HasSize#setHeight(String)
         */
        String height() default "";

        /**
         * Get the class to instantiate for the {@code helperComponent} property.
         *
         * @return desired {@code helperComponent} property value type
         * @see com.vaadin.flow.component.HasHelper#setHelperComponent(com.vaadin.flow.component.Component)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.Component> helperComponent() default com.vaadin.flow.component.Component.class;

        /**
         * Get the value desired for the {@code helperText} property.
         *
         * @return desired {@code helperText} property value
         * @see com.vaadin.flow.component.HasHelper#setHelperText(String)
         */
        String helperText() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see com.vaadin.flow.component.Component#setId(String)
         */
        String id() default "";

        /**
         * Get the class to instantiate for the {@code itemLabelGenerator} property.
         *
         * @return desired {@code itemLabelGenerator} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setItemLabelGenerator(com.vaadin.flow.component.ItemLabelGenerator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.component.ItemLabelGenerator> itemLabelGenerator() default com.vaadin.flow.component.ItemLabelGenerator.class;

        /**
         * Get the class to instantiate for the {@code items} property.
         *
         * @return desired {@code items} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setItems(com.vaadin.flow.data.provider.DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.provider.DataProvider> items() default com.vaadin.flow.data.provider.DataProvider.class;

        /**
         * Get the value desired for the {@code label} property.
         *
         * @return desired {@code label} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setLabel(String)
         */
        String label() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see com.vaadin.flow.component.HasSize#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see com.vaadin.flow.component.HasSize#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code opened} property.
         *
         * @return desired {@code opened} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setOpened(boolean)
         */
        boolean opened() default false;

        /**
         * Get the value desired for the {@code pageSize} property.
         *
         * @return desired {@code pageSize} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPageSize(int)
         */
        int pageSize() default 50;

        /**
         * Get the value desired for the {@code pattern} property.
         *
         * @return desired {@code pattern} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPattern(String)
         */
        String pattern() default "";

        /**
         * Get the value desired for the {@code placeholder} property.
         *
         * @return desired {@code placeholder} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPlaceholder(String)
         */
        String placeholder() default "";

        /**
         * Get the value desired for the {@code preventInvalidInput} property.
         *
         * @return desired {@code preventInvalidInput} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setPreventInvalidInput(boolean)
         */
        boolean preventInvalidInput() default false;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see com.vaadin.flow.component.HasValueAndElement#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the class to instantiate for the {@code renderer} property.
         *
         * @return desired {@code renderer} property value type
         * @see com.vaadin.flow.component.combobox.ComboBox#setRenderer(com.vaadin.flow.data.renderer.Renderer)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.flow.data.renderer.Renderer> renderer() default com.vaadin.flow.data.renderer.Renderer.class;

        /**
         * Get the value desired for the {@code required} property.
         *
         * @return desired {@code required} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setRequired(boolean)
         */
        boolean required() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see com.vaadin.flow.component.combobox.ComboBox#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see com.vaadin.flow.component.Focusable#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Add the specified theme names.
         *
         * @return zero or more theme names to add
         * @see com.vaadin.flow.component.HasTheme#addThemeNames(String[])
         */
        String[] addThemeNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see com.vaadin.flow.component.combobox.ComboBox#addThemeVariants(com.vaadin.flow.component.combobox.ComboBoxVariant[])
         */
        com.vaadin.flow.component.combobox.ComboBoxVariant[] addThemeVariants() default {};

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see com.vaadin.flow.component.Component#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see com.vaadin.flow.component.HasSize#setWidth(String)
         */
        String width() default "";
    }
}