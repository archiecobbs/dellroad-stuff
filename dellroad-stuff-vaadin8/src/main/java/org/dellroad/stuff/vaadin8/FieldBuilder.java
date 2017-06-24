
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.Binder;
import com.vaadin.data.BindingValidationStatusHandler;
import com.vaadin.data.Converter;
import com.vaadin.data.ErrorMessageProvider;
import com.vaadin.data.HasValue;
import com.vaadin.data.Validator;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.shared.ui.datefield.DateResolution;
import com.vaadin.shared.ui.datefield.DateTimeResolution;
import com.vaadin.shared.ui.slider.SliderOrientation;
import com.vaadin.ui.Component;
import com.vaadin.ui.IconGenerator;
import com.vaadin.ui.ItemCaptionGenerator;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.dellroad.stuff.java.MethodAnnotationScanner;

/**
 * Builds a {@link Binder} and binds auto-generated fields based on {@link FieldBuilder} annotations on property getter methods.
 *
 * <p>
 * The various nested {@link FieldBuilder} annotation types annotate Java bean property "getter" methods and specify
 * how the the bean properties of that class should be edited. This allows all information about how to edit a Java model
 * class to stay contained within that class.
 *
 * <p>
 * This class supports two types of annotations: first, the {@link ProvidesField &#64;ProvidesField} annotation annotates
 * a method that knows how to build an {@link HasValue} suitable for editing the bean property specified by
 * its {@link ProvidesField#value value()}. So {@link ProvidesField &#64;ProvidesField} is analgous to
 * {@link ProvidesProperty &#64;ProvidesProperty}, except that it defines an editing field rather than a readable property.
 *
 * <p>
 * The {@link FieldBuilder.AbstractComponent &#64;FieldBuilder.AbstractComponent} hierarchy annotations are the other type
 * of annotation.
 * These annotations annotate a Java bean property getter method and specify how to configure a {@link HasValue}
 * instance to edit the bean property corresponding to the getter method. The
 * {@link FieldBuilder.AbstractComponent &#64;FieldBuilder.AbstractComponent},
 * {@link FieldBuilder.AbstractField &#64;FieldBuilder.AbstractField}, etc.
 * correspond to the Vaadin widget class hierarchy.
 * More specific annotations correspond to the various widget subclasses,
 * for example {@link ComboBox &#64;FieldBuilder.ComboBox} corresponds to {@link ComboBox}.
 * When using more specific annotations, the "superclass" annotations configure the corresponding superclass' properties.
 *
 * <p>
 * Finally, the {@link FieldBuilder.Binding &#64;FieldBuilder.Binding} annotation provides for configuration of the field
 * binding itself.
 *
 * <p>
 * A simple example shows how these annotations are used:
 * <blockquote><pre>
 * // Use a 10 row TextArea to edit the "description" property
 * <b>&#64;FieldBuilder.AbstractComponent(caption = "Description:")</b>
 * <b>&#64;FieldBuilder.TextArea(rows = 10)</b>
 * <b>&#64;FieldBuilder.Binding(required = "Description is mandatory", validator = MyValidator.class)</b>
 * public String getDescription() {
 *     return this.description;
 * }
 *
 * // Use an enum combo box to edit the gender property
 * <b>&#64;FieldBuilder.EnumComboBox(caption = "Gender:")</b>
 * <b>&#64;FieldBuilder.Binding(required = "Description is mandatory")</b>
 * public Gender getGender() {
 *     return this.gender;
 * }
 *
 * // Use my own custom field to edit the "foobar" property
 * <b>&#64;FieldBuilder.ProvidesField("foobar")</b>
 * private static MyCustomField createFoobarField() {
 *     ...
 * }
 * </pre></blockquote>
 *
 * <p>
 * A {@link FieldBuilder} instance will read these annotations and build the fields automatically. For example:
 * <blockquote><pre>
 * // Create fields based on FieldBuilder.* annotations
 * Person person = new Person("Joe Smith", 100);
 * Binder&lt;Person&gt; binder = <b>FieldBuilder.buildAndBind(person).getBinder()</b>;
 *
 * // Layout the fields in a form
 * FormLayout layout = new FormLayout();
 * layout.add((Component)binder.getBinding("description").get().getField());
 * layout.add((Component)binder.getBinding("gender").get().getField());
 * layout.add((Component)binder.getBinding("foobar").get().getField());
 * </pre></blockquote>
 *
 * <p>
 * For all annotations in the {@link FieldBuilder.AbstractComponent &#64;FieldBuilder.AbstractComponent} hierarchy,
 * leaving properties set to their default values results in the default behavior.
 *
 * @see AbstractTextField
 * @see CheckBox
 * @see ComboBox
 * @see AbstractDateField
 * @see DateField
 * @see DateTimeField
 * @see EnumComboBox
 * @see ListSelect
 * @see PasswordField
 * @see RichTextArea
 * @see Slider
 * @see TextArea
 * @see TextField
 */
public class FieldBuilder<T> {

    private final Class<T> type;
    private final HashSet<String> propertyNames = new HashSet<>();

    private Binder<T> binder;

    /**
     * Constructor.
     */
    public FieldBuilder(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        this.type = type;
    }

    /**
     * Get the type associated with this instance.
     *
     * @return configured type
     */
    public Class<T> getType() {
        return this.type;
    }

    /**
     * Get the binder associated with this instance.
     *
     * @return binder, or null if {@link #buildAndBind buildAndBind()} has not yet been invoked
     */
    public Binder<T> getBinder() {
        return this.binder;
    }

    /**
     * Get the names of all properties bound by the most recent invocation of {@link #buildAndBind buildAndBind()}.
     *
     * @return field names
     */
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(this.propertyNames);
    }

    /**
     * Introspect for {@link FieldBuilder} annotations on getter methods of the configured class,
     * and create and bind the corresponding fields.
     *
     * <p>
     * Note that non-static {@link ProvidesField &#64;ProvidesField} annotations on instance methods are ignored, because
     * there is no bean instance provided; use {@link #buildAndBind(Object)} instead of this method to include them.
     *
     * @return this instance
     */
    public FieldBuilder<T> buildAndBind() {
        this.doBuildAndBind(null);
        return this;
    }

    /**
     * Introspect for {@link FieldBuilder} annotations on getter methods of the configured class,
     * create and bind the corresponding fields, and set the given bean in the {@link Binder}.
     *
     * @return this instance
     * @throws IllegalArgumentException if {@code bean} is null
     */
    public FieldBuilder<T> buildAndBind(T bean) {
        if (bean == null)
            throw new IllegalArgumentException("null bean");
        this.doBuildAndBind(bean);
        binder.setBean(bean);
        return this;
    }

    protected void doBuildAndBind(T bean) {

        // Reset
        this.binder = new Binder<>(this.type);
        this.propertyNames.clear();

        // Look for all bean property getter methods
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(this.type);
        } catch (IntrospectionException e) {
            throw new RuntimeException("unexpected exception", e);
        }
        final HashMap<String, Method> getterMap = new HashMap<>();              // contains all getter methods
        final HashMap<String, String> inverseGetterMap = new HashMap<>();
        for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
            Method method = propertyDescriptor.getReadMethod();

            // Work around Introspector stupidity where it returns overridden superclass' getter method
            if (method != null && method.getClass() != this.type) {
                for (Class<?> c = this.type; c != null && c != method.getClass(); c = c.getSuperclass()) {
                    try {
                        method = c.getDeclaredMethod(method.getName(), method.getParameterTypes());
                    } catch (Exception e) {
                        continue;
                    }
                    break;
                }
            }

            // Add getter, if appropriate
            if (method != null && method.getReturnType() != void.class && method.getParameterTypes().length == 0) {
                getterMap.put(propertyDescriptor.getName(), method);
                inverseGetterMap.put(method.getName(), propertyDescriptor.getName());
            }
        }

        // Scan getters for FieldBuilder.* annotations other than FieldBuidler.ProvidesField and FieldBuilder.Binding
        final HashMap<String, Binder.BindingBuilder<T, ?>> fieldBuilderMap = new HashMap<>();   // contains @FieldBuilder.* fields
        for (Map.Entry<String, Method> entry : getterMap.entrySet()) {
            final String propertyName = entry.getKey();
            final Method method = entry.getValue();

            // Get annotations, if any
            final List<AnnotationApplier<?>> applierList = this.buildApplierList(method);
            if (applierList.isEmpty())
                continue;

            // Build field
            final HasValue<?> field = this.buildField(applierList, "method " + method);

            // Add binding builder
            fieldBuilderMap.put(propertyName, this.binder.forField(field));
        }

        // Scan all methods for @FieldBuilder.ProvidesField annotations
        final HashMap<String, Method> providerMap = new HashMap<>();            // contains @FieldBuilder.ProvidesField methods
        this.buildProviderMap(providerMap);

        // Check for conflicts between @FieldBuidler.ProvidesField and other annotations and add fields to map
        for (Map.Entry<String, Method> entry : providerMap.entrySet()) {
            final String propertyName = entry.getKey();
            final Method method = entry.getValue();

            // Verify field is not already defined
            if (fieldBuilderMap.containsKey(propertyName)) {
                throw new IllegalArgumentException("conflicting annotations exist for property `" + propertyName + "': annotation @"
                  + ProvidesField.class.getName() + " on method " + method
                  + " cannot be combined with other @FieldBuilder.* annotation types");
            }

            // Invoke method to create field, if possible
            if ((method.getModifiers() & Modifier.STATIC) != 0 && bean == null)
                continue;
            HasValue<?> field;
            try {
                method.setAccessible(true);
            } catch (Exception e) {
                // ignore
            }
            try {
                field = (HasValue<?>)method.invoke(bean);
            } catch (Exception e) {
                throw new RuntimeException("error invoking @" + ProvidesField.class.getName()
                  + " annotation on method " + method, e);
            }

            // Save field
            fieldBuilderMap.put(propertyName, this.binder.forField(field));
        }

        // Scan getters for FieldBuilder.Binding annotations
        final HashMap<String, Binding> bindingAnnotationMap = new HashMap<>();                  // contains @FieldBuilder.Binding's
        final MethodAnnotationScanner<T, Binding> bindingAnnotationScanner
          = new MethodAnnotationScanner<T, Binding>(this.type, Binding.class);
        for (MethodAnnotationScanner<T, Binding>.MethodInfo methodInfo : bindingAnnotationScanner.findAnnotatedMethods()) {
            final String propertyName = inverseGetterMap.get(methodInfo.getMethod().getName());
            bindingAnnotationMap.put(propertyName, methodInfo.getAnnotation());
        }

        // Apply @FieldBuilder.Binding annotations (if any) and bind fields
        for (Map.Entry<String, Binder.BindingBuilder<T, ?>> entry : fieldBuilderMap.entrySet()) {
            final String propertyName = entry.getKey();
            final Binder.BindingBuilder<T, ?> bindingBuilder = entry.getValue();

            // Apply @FieldBuilder.Binding annotation, if any
            final Binding bindingAnnotation = bindingAnnotationMap.get(propertyName);
            if (bindingAnnotation != null)
                this.applyBindingAnnotation(bindingBuilder, bindingAnnotation);

            // Bind field
            this.propertyNames.add(propertyName);
            bindingBuilder.bind(propertyName);
        }
    }

    // This method exists solely to bind the generic type
    private void buildProviderMap(Map<String, Method> providerMap) {
        final MethodAnnotationScanner<T, ProvidesField> scanner
          = new MethodAnnotationScanner<T, ProvidesField>(this.type, ProvidesField.class);
        for (MethodAnnotationScanner<T, ProvidesField>.MethodInfo methodInfo : scanner.findAnnotatedMethods())
            this.buildProviderMap(providerMap, methodInfo.getMethod().getDeclaringClass(), methodInfo.getMethod().getName());
    }

    @SuppressWarnings("unchecked")
    private void applyBindingAnnotation(Binder.BindingBuilder<T, ?> bindingBuilder, Binding bindingAnnotation) {
        if (bindingAnnotation.required().length() > 0)
            bindingBuilder.asRequired(bindingAnnotation.required());
        if (bindingAnnotation.requiredProvider() != ErrorMessageProvider.class)
            bindingBuilder.asRequired(FieldBuilder.instantiate(bindingAnnotation.requiredProvider()));
        if (bindingAnnotation.converter() != Converter.class)
            bindingBuilder.withConverter(FieldBuilder.instantiate(bindingAnnotation.converter()));
        if (bindingAnnotation.validationStatusHandler() != BindingValidationStatusHandler.class) {
            bindingBuilder.withValidationStatusHandler(
              FieldBuilder.instantiate(bindingAnnotation.validationStatusHandler()));
        }
        if (bindingAnnotation.validator() != Validator.class)
            bindingBuilder.withValidator(FieldBuilder.instantiate(bindingAnnotation.validator()));
    }

    // Used by buildBeanPropertyFields() to validate @FieldBuilder.ProvidesField annotations
    private void buildProviderMap(Map<String, Method> providerMap, Class<?> type, String methodName) {

        // Terminate recursion
        if (type == null)
            return;

        // Check the method in this class
        do {

            // Get method
            Method method;
            try {
                method = type.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                break;
            }

            // Get annotation
            final ProvidesField providesField = method.getAnnotation(ProvidesField.class);
            if (providesField == null)
                break;

            // Validate method return type is compatible with Field
            if (!HasValue.class.isAssignableFrom(method.getReturnType())) {
                throw new IllegalArgumentException("invalid @" + ProvidesField.class.getName() + " annotation on method " + method
                  + ": return type " + method.getReturnType().getName() + " is not a subtype of " + HasValue.class.getName());
            }

            // Check for two methods declaring fields for the same property
            final String propertyName = providesField.value();
            final Method otherMethod = providerMap.get(propertyName);
            if (otherMethod != null && !otherMethod.getName().equals(methodName)) {
                throw new IllegalArgumentException("conflicting @" + ProvidesField.class.getName()
                  + " annotations exist for property `" + propertyName + "': both method "
                  + otherMethod + " and method " + method + " are specified");
            }

            // Save method
            if (otherMethod == null)
                providerMap.put(propertyName, method);
        } while (false);

        // Recurse on interfaces
        for (Class<?> iface : type.getInterfaces())
            this.buildProviderMap(providerMap, iface, methodName);

        // Recurse on superclass
        this.buildProviderMap(providerMap, type.getSuperclass(), methodName);
    }

    /**
     * Instantiate and configure an {@link HasValue} according to the given scanned annotations.
     *
     * @param appliers annotation appliers
     * @param description description of the field (used for exception messages)
     * @return new field
     */
    protected HasValue<?> buildField(Collection<AnnotationApplier<?>> appliers, String description) {

        // Get comparator that sorts by class hierarchy, narrower types first; note Collections.sort() is stable,
        // so for any specific annotation type, that annotation on subtype appears before that annotation on supertype.
        final Comparator<AnnotationApplier<?>> comparator = new Comparator<AnnotationApplier<?>>() {
            @Override
            public int compare(AnnotationApplier<?> a1, AnnotationApplier<?> a2) {
                final Class<?> type1 = FieldBuilder.getVaadinComponentType(a1.getAnnotation());
                final Class<?> type2 = FieldBuilder.getVaadinComponentType(a2.getAnnotation());
                if (type1 == type2)
                    return 0;
                if (type1.isAssignableFrom(type2))
                    return 1;
                if (type2.isAssignableFrom(type1))
                    return -1;
                return 0;
            }
        };

        // Sanity check for duplicates and conflicts
        final ArrayList<AnnotationApplier<?>> applierList = new ArrayList<>(appliers);
        Collections.sort(applierList, comparator);
        for (int i = 0; i < applierList.size() - 1; ) {
            final AnnotationApplier<?> a1 = applierList.get(i);
            final AnnotationApplier<?> a2 = applierList.get(i + 1);

            // Let annotations on subclass override annotations on superclass
            if (a1.getAnnotation().annotationType() == a2.getAnnotation().annotationType()) {
                applierList.remove(i + 1);
                continue;
            }

            // Check for conflicting annotation types (e.g., both FieldBuilder.TextField and FieldBuilder.DateField)
            if (comparator.compare(a1, a2) == 0) {
                throw new IllegalArgumentException("conflicting annotations of type "
                  + a1.getAnnotation().annotationType().getName() + " and " + a2.getAnnotation().annotationType().getName()
                  + " for " + description);
            }
            i++;
        }

        // Determine field type
        Class<? extends HasValue<?>> fieldType = null;
        AnnotationApplier<?> typeApplier = null;
        for (AnnotationApplier<?> applier : applierList) {

            // Pick up type() if specified
            if (applier.getActualFieldType() == null)
                continue;
            if (fieldType == null) {
                fieldType = applier.getActualFieldType();
                typeApplier = applier;
                continue;
            }

            // Verify the field type specified by a narrower annotation has compatible narrower field type
            if (!applier.getActualFieldType().isAssignableFrom(fieldType) && typeApplier != null) {
                throw new IllegalArgumentException("conflicting field types specified by annotations of type "
                  + typeApplier.getAnnotation().annotationType().getName() + " (type() = " + fieldType.getName() + ") and "
                  + applier.getAnnotation().annotationType().getName() + " (type() = " + applier.getActualFieldType().getName()
                  + ") for " + description);
            }
        }
        if (fieldType == null)
            throw new IllegalArgumentException("cannot determine field type; no type() specified for " + description);

        // Instantiate field
        final HasValue<?> field = FieldBuilder.instantiate(fieldType);

        // Configure the field
        for (AnnotationApplier<?> applier : applierList)
            applier.applyTo(field);

        // Done
        return field;
    }

    private static <T> T instantiate(Class<T> type) {
        Constructor<T> constructor;
        try {
            constructor = type.getDeclaredConstructor();
        } catch (Exception e) {
            throw new RuntimeException("cannot instantiate " + type + " because no zero-arg constructor could be found", e);
        }
        try {
            constructor.setAccessible(true);
        } catch (Exception e) {
            // ignore
        }
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("cannot instantiate " + type + " using its zero-arg constructor", e);
        }
    }

    /**
     * Find all relevant annotations on the given method as well as on any supertype methods it overrides.
     * The method must be a getter method taking no arguments. Annotations are ordered so that annotations
     * on a method in type X appear before annotations on an overridden method in type Y, a supertype of X.
     *
     * @param method annotated getter method
     * @return appliers for annotations found
     * @throws IllegalArgumentException if {@code method} is null
     * @throws IllegalArgumentException if {@code method} has parameters
     */
    protected List<AnnotationApplier<?>> buildApplierList(Method method) {

        // Sanity check
        if (method == null)
            throw new IllegalArgumentException("null method");
        if (method.getParameterTypes().length > 0)
            throw new IllegalArgumentException("method takes parameters");

        // Recurse
        final ArrayList<AnnotationApplier<?>> list = new ArrayList<>();
        this.buildApplierList(method.getDeclaringClass(), method.getName(), list);
        return list;
    }

    private void buildApplierList(Class<?> type, String methodName, List<AnnotationApplier<?>> list) {

        // Terminate recursion
        if (type == null)
            return;

        // Check class
        Method method;
        try {
            method = type.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            method = null;
        }
        if (method != null)
            list.addAll(this.buildDirectApplierList(method));

        // Recurse on interfaces
        for (Class<?> iface : type.getInterfaces())
            this.buildApplierList(iface, methodName, list);

        // Recurse on superclass
        this.buildApplierList(type.getSuperclass(), methodName, list);
    }

    /**
     * Find all relevant annotations declared directly on the given {@link Method}.
     *
     * @param method method to inspect
     * @return annotations found
     * @throws IllegalArgumentException if {@code method} is null
     */
    protected List<AnnotationApplier<?>> buildDirectApplierList(Method method) {

        // Sanity check
        if (method == null)
            throw new IllegalArgumentException("null method");

        // Build list
        final ArrayList<AnnotationApplier<?>> list = new ArrayList<>();
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            final AnnotationApplier<? extends Annotation> applier = this.getAnnotationApplier(method, annotation);
            if (applier != null)
                list.add(applier);
        }
        return list;
    }

    /**
     * Get the {@link AnnotationApplier} that applies the given annotation.
     * Subclasses can add support for additional annotation types by overriding this method.
     *
     * @param method method to inspect
     * @param annotation method annotation to inspect
     * @return corresponding {@link AnnotationApplier}, or null if annotation is unknown
     */
    protected <A extends Annotation> AnnotationApplier<A> getAnnotationApplier(Method method, A annotation) {
        return this.isFieldBuilderFieldAnnotation(annotation) ? new AnnotationApplier<>(method, annotation) : null;
    }

// AnnotationApplier

    /**
     * Class that knows how to apply annotation properties to a corresponding component.
     */
    private static class AnnotationApplier<A extends Annotation> {

        protected final Method method;
        protected final A annotation;

        AnnotationApplier(Method method, A annotation) {
            if (method == null)
                throw new IllegalArgumentException("null method");
            if (annotation == null)
                throw new IllegalArgumentException("null annotation");
            this.method = method;
            this.annotation = annotation;
        }

        public final Method getMethod() {
            return this.method;
        }

        public final A getAnnotation() {
            return this.annotation;
        }

        @SuppressWarnings("unchecked")
        public Class<? extends HasValue<?>> getActualFieldType() {
            try {
                final Class<?> defaultType = FieldBuilder.getVaadinComponentType(this.annotation);
                final Class<?> actualType = (Class<?>)this.annotation.getClass().getMethod("type").invoke(this.annotation);
                return (actualType.getModifiers() & Modifier.ABSTRACT) != 0 && actualType == defaultType ?
                  null : (Class<? extends HasValue<?>>)actualType.asSubclass(HasValue.class);
            } catch (Exception e) {
                throw new RuntimeException("unexpected exception", e);
            }
        }

        @SuppressWarnings("unchecked")
        public void applyTo(HasValue<?> field) {

            // Get annotation defaults so we can see what properties are changed
            final A defaults = FieldBuilder.getDefaults(this.annotation);

            // Copy annotation property values and apply to the corresponding properties of the field (if changed from default)
            for (Method fieldSetter : field.getClass().getMethods()) {

                // Set if method is a setter method
                if (!fieldSetter.getName().startsWith("set") || fieldSetter.getName().length() < 4)
                    continue;
                final Class<?>[] parameterTypes = fieldSetter.getParameterTypes();
                if (parameterTypes.length != 1)
                    continue;
                final String propertyName = Introspector.decapitalize(fieldSetter.getName().substring(3));
                try {

                    // Special handling for EnumComboBox
                    if (field instanceof org.dellroad.stuff.vaadin8.EnumComboBox
                      && propertyName.equals("enumClass")
                      && Enum.class.isAssignableFrom(this.method.getReturnType())) {
                        ((org.dellroad.stuff.vaadin8.EnumComboBox)field).setEnumClass(
                          this.method.getReturnType().asSubclass(Enum.class));
                        continue;
                    }

                    // Find corresponding annotation property, if any
                    final Method annotationGetter;
                    try {
                        annotationGetter = this.annotation.getClass().getMethod(propertyName);
                    } catch (NoSuchMethodException e) {
                        continue;
                    }

                    // Get value from annotation, and annotation's default value
                    Object value = annotationGetter.invoke(this.annotation);
                    final Object defaultValue = annotationGetter.invoke(defaults);

                    // If annotation value is same as default, don't do anything
                    if (Objects.equals(value, defaultValue))
                        continue;

                    // Special case for Class<?> values: instantiate the class
                    if (value instanceof Class)
                        value = FieldBuilder.instantiate((Class<?>)value);

                    // Copy over the value
                    fieldSetter.invoke(field, value);
                } catch (Exception e) {
                    throw new RuntimeException("unexpected exception", e);
                }
            }

            // Special handling for "styleNames" property
            final Method styleNamesGetter;
            try {
                styleNamesGetter = this.annotation.getClass().getMethod("styleNames");
            } catch (NoSuchMethodException e) {
                return;
            }
            try {
                final Method addStyleNameMethod = field.getClass().getMethod("addStyleName", String.class);
                for (String styleName : (String[])styleNamesGetter.invoke(this.annotation))
                    addStyleNameMethod.invoke(field, styleName);
            } catch (Exception e) {
                throw new RuntimeException("unexpected exception", e);
            }
        }
    }

    @AbstractComponent
    @AbstractDateField
    @AbstractField
    @AbstractListing
    @AbstractMultiSelect
    @AbstractSingleSelect
    @AbstractTextField
    @CheckBox
    @ComboBox
    @DateField
    @DateTimeField
    @EnumComboBox
    @ListSelect
    @PasswordField
    @RichTextArea
    @Slider
    @TextArea
    @TextField
    private static <A extends Annotation> A getDefaults(A annotation) {
        final A defaultAnnotation;
        try {
            defaultAnnotation = FieldBuilder.class.getDeclaredMethod("getDefaults", Annotation.class)
              .getDeclaredAnnotation(FieldBuilder.getFieldBuilderAnnotationType(annotation));
        } catch (Exception e) {
            throw new RuntimeException("unexpected exception", e);
        }
        if (defaultAnnotation == null)
            throw new RuntimeException("internal error: didn't find " + annotation);
        return defaultAnnotation;
    }

    private static <A extends Annotation> Class<? extends com.vaadin.ui.Component> getVaadinComponentType(A annotation) {
        try {
            final Method typeMethod = FieldBuilder.getFieldBuilderAnnotationType(annotation).getMethod("type");
            final Class<?> defaultFieldType = (Class<?>)typeMethod.invoke(FieldBuilder.getDefaults(annotation));
            return defaultFieldType.asSubclass(Component.class);
        } catch (Exception e) {
            throw new RuntimeException("unexpected exception", e);
        }
    }

    private boolean isFieldBuilderFieldAnnotation(Annotation annotation) {
        return FieldBuilder.getFieldBuilderAnnotationType(annotation) != null;
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> Class<A> getFieldBuilderAnnotationType(A annotation) {
        for (Class<?> iface : annotation.getClass().getInterfaces()) {
            if (iface.getDeclaringClass() != FieldBuilder.class)
                continue;
            if (ProvidesField.class.isAssignableFrom(iface) || Binding.class.isAssignableFrom(iface))
                continue;
            return (Class<A>)iface;
        }
        return null;
    }

// Annotations

    /**
     * Specifies that the annotated method will return a {@link HasValue} suitable for editing the specified property.
     *
     * <p>
     * Annotated methods must take zero arguments and return a type compatible with {@link HasValue}.
     * To be included when used with {@link #buildAndBind()} (taking no bean parameter), the annotated method must be
     * {@code static}.
     *
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface ProvidesField {

        /**
         * The name of the property that the annotated method's return value edits.
         *
         * @return property name
         */
        String value();
    }

    /**
     * Specifies properties of the {@link Binder} binding itself.
     *
     * <p>
     * Properties correspond to methods in {@link Binder.BindingBuider}.
     *
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Binding {

        /**
         * Get "as required" error message.
         *
         * <p>
         * Either this or {@link #requiredProvider} should be set, but not both.
         *
         * @return "as required" error message
         * @see Binder.BindingBuider#asRequired(String)
         */
        String required() default "";

        /**
         * Get "as required" error message provider class.
         *
         * <p>
         * Either this or {@link #required} should be set, but not both.
         *
         * @return "as required" error message provider class
         * @see Binder.BindingBuider#asRequired(ErrorMessageProvider)
         */
        Class<? extends ErrorMessageProvider> requiredProvider() default ErrorMessageProvider.class;

        /**
         * Get the converter class.
         *
         * @return converter class
         * @see Binder.BindingBuider#withConverter(Converter)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends Converter> converter() default Converter.class;

        /**
         * Get the validation status handler class.
         *
         * @return validation status handler class
         * @see Binder.BindingBuider#withValidationStatusHandler
         */
        Class<? extends BindingValidationStatusHandler> validationStatusHandler() default BindingValidationStatusHandler.class;

        /**
         * Get the validator class.
         *
         * @return validator class
         * @see Binder.BindingBuider#withValidator
         */
        @SuppressWarnings("rawtypes")
        Class<? extends Validator> validator() default Validator.class;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.AbstractComponent}.
     *
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractComponent {

        /**
         * Get the {@link com.vaadin.ui.AbstractComponent} type that will edit the property.
         *
         * <p>
         * Although this property has a default value, it must be overridden either in this annotation, or
         * by also including a more specific annotation such as {@link TextField}.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.AbstractComponent> type() default com.vaadin.ui.AbstractComponent.class;

        /**
         * Get style names.
         *
         * @return style names
         * @see com.vaadin.ui.AbstractComponent#addStyleName
         */
        String[] styleNames() default {};

        /**
         * Get primary style name.
         *
         * @return primary style name
         * @see com.vaadin.ui.AbstractComponent#setPrimaryStyleName
         */
        String primaryStyleName() default "";

        /**
         * Get width.
         *
         * @return field width
         * @see com.vaadin.ui.AbstractComponent#setWidth(String)
         */
        String width() default "";

        /**
         * Get height.
         *
         * @return field height
         * @see com.vaadin.ui.AbstractComponent#setHeight(String)
         */
        String height() default "";

        /**
         * Get the caption associated with this field.
         *
         * @return field caption
         * @see com.vaadin.ui.AbstractComponent#setCaption
         */
        String caption() default "";

        /**
         * Get whether caption is HTML.
         *
         * @return true for HTML caption
         * @see com.vaadin.ui.AbstractComponent#setCaptionAsHtml
         */
        boolean captionAsHtml() default false;

        /**
         * Get the description associated with this field.
         *
         * @return field description
         * @see com.vaadin.ui.AbstractComponent#setDescription
         */
        String description() default "";

        /**
         * Get the unique ID for this field used for testing purposes.
         *
         * @return field id
         * @see com.vaadin.ui.AbstractComponent#setId
         */
        String id() default "";

        /**
         * Get whether this field is enabled.
         *
         * @return true to enable field
         * @see com.vaadin.ui.AbstractComponent#setEnabled
         */
        boolean enabled() default true;

        /**
         * Get whether field is responsive.
         *
         * @return true if value is responsive
         * @see com.vaadin.ui.AbstractComponent#setResponsive
         */
        boolean responsive() default false;

        /**
         * Get whether field is visible.
         *
         * @return true if value is visible
         * @see com.vaadin.ui.AbstractComponent#setVisible
         */
        boolean visible() default true;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.AbstractField}.
     *
     * @see FieldBuilder.AbstractComponent
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractField {

        /**
         * Get the {@link com.vaadin.ui.AbstractField} type that will edit the property.
         *
         * <p>
         * Although this property has a default value, it must be overridden either in this annotation, or
         * by also including a more specific annotation such as {@link ComboBox}.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.AbstractField> type() default com.vaadin.ui.AbstractField.class;

        /**
         * Get whether this field is read-only.
         *
         * @return true for read-only
         * @see com.vaadin.ui.AbstractField#setReadOnly
         */
        boolean readOnly() default false;

        /**
         * Get whether the required indicator is visible.
         *
         * @return true for visible
         * @see com.vaadin.ui.AbstractField#setRequiredIndicatorVisible
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get this field's tab index.
         *
         * @return tab index
         * @see com.vaadin.ui.AbstractField#setTabIndex
         */
        int tabIndex() default -1;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.AbstractListing}.
     *
     * @see FieldBuilder.AbstractField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractListing {

        /**
         * Get the {@link com.vaadin.ui.AbstractListing} type that will edit the property.
         *
         * <p>
         * Although this property has a default value, it must be overridden either in this annotation, or
         * by also including a more specific annotation such as {@link ComboBox}.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.AbstractListing> type() default com.vaadin.ui.AbstractListing.class;

        /**
         * Get this field's tab index.
         *
         * @return tab index
         * @see com.vaadin.ui.AbstractListing#setTabIndex
         */
        int tabIndex() default -1;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.AbstractMultiSelect}.
     *
     * @see FieldBuilder.AbstractListing
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractMultiSelect {

        /**
         * Get the {@link com.vaadin.ui.AbstractMultiSelect} type that will edit the property.
         *
         * <p>
         * Although this property has a default value, it must be overridden either in this annotation, or
         * by also including a more specific annotation such as {@link ListSelect}.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.AbstractMultiSelect> type() default com.vaadin.ui.AbstractMultiSelect.class;

        /**
         * Get the item caption generator class.
         *
         * @return caption generator
         * @see com.vaadin.ui.AbstractListing#setItemCaptionGenerator
         */
        @SuppressWarnings("rawtypes")
        Class<? extends ItemCaptionGenerator> itemCaptionGenerator() default ItemCaptionGenerator.class;

        /**
         * Get whether this field is read-only.
         *
         * @return true for read-only
         * @see com.vaadin.ui.AbstractMultiSelect#setReadOnly
         */
        boolean readOnly() default false;

        /**
         * Get whether the required indicator is visible.
         *
         * @return true for visible
         * @see com.vaadin.ui.AbstractMultiSelect#setRequiredIndicatorVisible
         */
        boolean requiredIndicatorVisible() default false;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.AbstractSingleSelect}.
     *
     * @see FieldBuilder.AbstractListing
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractSingleSelect {

        /**
         * Get the {@link com.vaadin.ui.AbstractSingleSelect} type that will edit the property.
         *
         * <p>
         * Although this property has a default value, it must be overridden either in this annotation, or
         * by also including a more specific annotation such as {@link ComboBox}.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.AbstractSingleSelect> type() default com.vaadin.ui.AbstractSingleSelect.class;

        /**
         * Get whether this field is read-only.
         *
         * @return true for read-only
         * @see com.vaadin.ui.AbstractSingleSelect#setReadOnly
         */
        boolean readOnly() default false;

        /**
         * Get whether the required indicator is visible.
         *
         * @return true for visible
         * @see com.vaadin.ui.AbstractSingleSelect#setRequiredIndicatorVisible
         */
        boolean requiredIndicatorVisible() default false;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.CheckBox}.
     *
     * @see FieldBuilder.AbstractField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface CheckBox {

        /**
         * Get the {@link com.vaadin.ui.CheckBox} type that will edit the property. Type must have a no-arg constructor.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.CheckBox> type() default com.vaadin.ui.CheckBox.class;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.ComboBox}.
     *
     * @see FieldBuilder.AbstractSingleSelect
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface ComboBox {

        /**
         * Get the {@link com.vaadin.ui.ComboBox} type that will edit the property. Type must have a no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.ComboBox> type() default com.vaadin.ui.ComboBox.class;

        /**
         * Get the item icon generator class.
         *
         * @return icon generator
         * @see com.vaadin.ui.ComboBox#setItemIconGenerator
         */
        @SuppressWarnings("rawtypes")
        Class<? extends IconGenerator> itemIconGenerator() default IconGenerator.class;

        /**
         * Get the item caption generator class.
         *
         * @return caption generator
         * @see com.vaadin.ui.ComboBox#setItemCaptionGenerator
         */
        @SuppressWarnings("rawtypes")
        Class<? extends ItemCaptionGenerator> itemCaptionGenerator() default ItemCaptionGenerator.class;

        /**
         * Get the {@link ListDataProvider} data provider class.
         *
         * @return data provider class
         * @see com.vaadin.ui.ComboBox#setDataProvider
         */
        @SuppressWarnings("rawtypes")
        Class<? extends ListDataProvider> dataProvider() default ListDataProvider.class;

        /**
         * Get the new item handler class.
         *
         * @return new item handler
         * @see com.vaadin.ui.ComboBox#setNewItemHandler
         */
        Class<? extends com.vaadin.ui.ComboBox.NewItemHandler> newItemHandler()
          default com.vaadin.ui.ComboBox.NewItemHandler.class;

        /**
         * Get the {@link ListDataProvider} class used to populate the {@link ComboBox}.
         *
         * @return data provider
         * @see com.vaadin.ui.ComboBox#setDataProvider(ListDataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends ListDataProvider> listDataProvider() default ListDataProvider.class;

        /**
         * Get whether empty selection is allowed.
         *
         * @return true to allow empty selection
         * @see com.vaadin.ui.ComboBox#setEmptySelectionAllowed
         */
        boolean emptySelectionAllowed() default true;

        /**
         * Get the input prompt.
         *
         * @return input prompt
         * @see com.vaadin.ui.ComboBox#setEmptySelectionCaption
         */
        String emptySelectionCaption() default "";

        /**
         * Get the placeholder.
         *
         * @return placeholder
         * @see com.vaadin.ui.ComboBox#setPlaceholder
         */
        String placeholder() default "";

        /**
         * Get the popup width.
         *
         * @return popup width
         * @see com.vaadin.ui.ComboBox#setPopupWidth
         */
        String popupWidth() default "";

        /**
         * Get the page length.
         *
         * @return page length, or -1 for none
         * @see com.vaadin.ui.ComboBox#setPageLength
         */
        int pageLength() default -1;

        /**
         * Get whether to scroll to the selected item.
         *
         * @return true to scroll to the selected item
         * @see com.vaadin.ui.ComboBox#setScrollToSelectedItem
         */
        boolean scrollToSelectedItem() default true;

        /**
         * Get whether text input is allowed.
         *
         * @return true to allow text input
         * @see com.vaadin.ui.ComboBox#setTextInputAllowed
         */
        boolean textInputAllowed() default true;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link org.dellroad.stuff.vaadin8.EnumComboBox}.
     *
     * @see FieldBuilder.ComboBox
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface EnumComboBox {

        /**
         * Get the {@link org.dellroad.stuff.vaadin8.EnumComboBox} type that will edit the property.
         * Type must have a no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends org.dellroad.stuff.vaadin8.EnumComboBox> type() default org.dellroad.stuff.vaadin8.EnumComboBox.class;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.ListSelect}.
     *
     * @see FieldBuilder.AbstractMultiSelect
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface ListSelect {

        /**
         * Get the {@link com.vaadin.ui.ListSelect} type that will edit the property. Type must have a no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.ListSelect> type() default com.vaadin.ui.ListSelect.class;

        /**
         * Get the number of rows in the editor.
         *
         * @return number of rows, or -1 for none
         * @see com.vaadin.ui.ListSelect#setRows
         */
        int rows() default -1;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.AbstractDateField}.
     *
     * @see FieldBuilder.AbstractField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractDateField {

        /**
         * Get the {@link com.vaadin.ui.AbstractDateField} type that will edit the property. Type must have a no-arg constructor.
         *
         * @return field type
         */
        @SuppressWarnings("rawtypes")
        Class<? extends com.vaadin.ui.AbstractDateField> type() default com.vaadin.ui.AbstractDateField.class;

        /**
         * Get the format string.
         *
         * @return format string
         * @see com.vaadin.ui.AbstractDateField#setDateFormat
         */
        String dateFormat() default "";

        /**
         * Get the date out of range error message.
         *
         * @return date out of range error message
         * @see com.vaadin.ui.AbstractDateField#setDateOutOfRangeMessage
         */
        String dateOutOfRangeMessage() default "";

        /**
         * Get lenient mode.
         *
         * @return true for lenient mode
         * @see com.vaadin.ui.AbstractDateField#setLenient
         */
        boolean lenient() default false;

        /**
         * Get the date parse error message.
         *
         * @return date parse error message
         * @see com.vaadin.ui.AbstractDateField#setParseErrorMessage
         */
        String parseErrorMessage() default "";

        /**
         * Get whether to show ISO week numbers.
         *
         * @return whether to show ISO week numbers
         * @see com.vaadin.ui.AbstractDateField#setShowISOWeekNumbers
         */
        boolean showISOWeekNumbers() default false;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.DateField}.
     *
     * @see FieldBuilder.AbstractDateField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface DateField {

        /**
         * Get the {@link com.vaadin.ui.DateField} type that will edit the property. Type must have a no-arg constructor.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.DateField> type() default com.vaadin.ui.DateField.class;

        /**
         * Get the date resolution.
         *
         * @return date resolution
         * @see com.vaadin.ui.DateField#setResolution
         */
        DateResolution resolution() default DateResolution.DAY;

        /**
         * Get whether text field is enabled.
         *
         * @return true for text field enabled
         * @see com.vaadin.ui.DateField#setTextFieldEnabled
         */
        boolean textFieldEnabled() default false;

        /**
         * Get the placeholder.
         *
         * @return placeholder
         * @see com.vaadin.ui.DateField#setPlaceholder
         */
        String placeholder() default "";

        /**
         * Get the assistive text.
         *
         * @return assistive text
         * @see com.vaadin.ui.DateField#setAssistiveText
         */
        String assistiveText() default "";
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using an {@link com.vaadin.ui.DateTimeField}.
     *
     * @see FieldBuilder.AbstractDateField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface DateTimeField {

        /**
         * Get the {@link com.vaadin.ui.DateTimeField} type that will edit the property. Type must have a no-arg constructor.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.DateTimeField> type() default com.vaadin.ui.DateTimeField.class;

        /**
         * Get the time resolution.
         *
         * @return time resolution
         * @see com.vaadin.ui.DateTimeField#setResolution
         */
        DateTimeResolution resolution() default DateTimeResolution.SECOND;

        /**
         * Get whether text field is enabled.
         *
         * @return true for text field enabled
         * @see com.vaadin.ui.DateField#setTextFieldEnabled
         */
        boolean textFieldEnabled() default false;

        /**
         * Get the placeholder.
         *
         * @return placeholder
         * @see com.vaadin.ui.DateField#setPlaceholder
         */
        String placeholder() default "";

        /**
         * Get the assistive text.
         *
         * @return assistive text
         * @see com.vaadin.ui.DateField#setAssistiveText
         */
        String assistiveText() default "";
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using a {@link com.vaadin.ui.AbstractTextField}.
     *
     * @see FieldBuilder.AbstractField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface AbstractTextField {

        /**
         * Get the {@link com.vaadin.ui.AbstractTextField} type that will edit the property.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.AbstractTextField> type() default com.vaadin.ui.AbstractTextField.class;

        /**
         * Get the placeholder.
         *
         * @return placeholder
         * @see com.vaadin.ui.AbstractTextField#setPlaceholder
         */
        String placeholder() default "";

        /**
         * Get text change event mode.
         *
         * @return text change event mode
         * @see com.vaadin.ui.AbstractTextField#setValueChangeMode
         */
        ValueChangeMode valueChangeMode() default ValueChangeMode.LAZY;

        /**
         * Get text change event timeout.
         *
         * @return text change event timeout in seconds, or -1 to not override
         * @see com.vaadin.ui.AbstractTextField#setValueChangeTimeout
         */
        int valueChangeTimeout() default -1;

        /**
         * Get the maximum length.
         *
         * @return maximum length, or -1 to not override
         * @see com.vaadin.ui.AbstractTextField#setMaxLength
         */
        int maxLength() default -1;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using a {@link com.vaadin.ui.TextField}.
     *
     * @see FieldBuilder.AbstractTextField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface TextField {

        /**
         * Get the {@link com.vaadin.ui.TextField} type that will edit the property.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.TextField> type() default com.vaadin.ui.TextField.class;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using a {@link com.vaadin.ui.TextArea}.
     *
     * @see FieldBuilder.AbstractTextField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface TextArea {

        /**
         * Get the {@link com.vaadin.ui.TextArea} type that will edit the property.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.TextArea> type() default com.vaadin.ui.TextArea.class;

        /**
         * Set the number of rows.
         *
         * @return number of rows, or -1 for none
         * @see com.vaadin.ui.TextArea#setRows
         */
        int rows() default -1;

        /**
         * Set wordwrap mode.
         *
         * @return word wrap mode
         * @see com.vaadin.ui.TextArea#setWordWrap
         */
        boolean wordWrap() default true;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using a {@link com.vaadin.ui.PasswordField}.
     *
     * @see FieldBuilder.TextField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface PasswordField {

        /**
         * Get the {@link com.vaadin.ui.PasswordField} type that will edit the property.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.PasswordField> type() default com.vaadin.ui.PasswordField.class;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using a {@link com.vaadin.ui.Slider}.
     *
     * @see FieldBuilder.AbstractField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Slider {

        /**
         * Get the {@link com.vaadin.ui.Slider} type that will edit the property.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.Slider> type() default com.vaadin.ui.Slider.class;

        /**
         * Get the minimum value.
         *
         * @return minimum value
         * @see com.vaadin.ui.Slider#setMin
         */
        double min() default Double.NaN;

        /**
         * Get the maximum value.
         *
         * @return maximum value
         * @see com.vaadin.ui.Slider#setMax
         */
        double max() default Double.NaN;

        /**
         * Get the resolution.
         *
         * @return resolution
         * @see com.vaadin.ui.Slider#setResolution
         */
        int resolution() default -1;

        /**
         * Get the orientation.
         *
         * @return orientation
         * @see com.vaadin.ui.Slider#setOrientation
         */
        SliderOrientation orientation() default SliderOrientation.HORIZONTAL;
    }

    /**
     * Specifies how a Java property should be edited in Vaadin using a {@link com.vaadin.ui.com.vaadin.ui.RichTextArea}.
     *
     * @see FieldBuilder.AbstractField
     * @see FieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface RichTextArea {

        /**
         * Get the {@link com.vaadin.ui.RichTextArea} type that will edit the property.
         *
         * @return field type
         */
        Class<? extends com.vaadin.ui.RichTextArea> type() default com.vaadin.ui.RichTextArea.class;
    }
}

