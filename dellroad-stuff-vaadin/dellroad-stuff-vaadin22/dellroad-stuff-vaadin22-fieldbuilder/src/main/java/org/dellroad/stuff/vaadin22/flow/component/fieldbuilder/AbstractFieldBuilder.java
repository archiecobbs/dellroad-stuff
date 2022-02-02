
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component.fieldbuilder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BindingValidationStatusHandler;
import com.vaadin.flow.data.binder.ErrorMessageProvider;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.converter.Converter;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.ReflectUtil;

import org.dellroad.stuff.vaadin22.flow.component.AutoBuildAware;

/**
 * Provides the machinery for auto-generated {@link FieldBuilder}-like classes.
 *
 * @see FieldBuilder
 */
public abstract class AbstractFieldBuilder<S extends AbstractFieldBuilder<S, T>, T> implements Serializable {

    public static final String DEFAULT_ANNOTATION_DEFAULTS_METHOD_NAME = "annotationDefaultsMethod";

    private static final String STRING_DEFAULT = "<FieldBuilderStringDefault>";

    private static final long serialVersionUID = -3091638771700394722L;

    private final Class<T> type;
    private final LinkedHashMap<String, Binder.BindingBuilder<? extends T, ?>> bindings = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param type backing object type
     * @throws IllegalStateException if {@code type} is null
     */
    protected AbstractFieldBuilder(Class<T> type) {
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
     * Get the names of all properties discovered and bound by the most recent invocation of {@link #bindFields bindFields()}.
     *
     * <p>
     * This will be the subset of all of the {@link Binder}'s properties containing those for which the getter method
     * had defined widget annotations.
     *
     * <p>
     * The returned {@link Set} will iterate fields in order by {@link Binding#order &#64;Binding.order()}
     * values, then by name.
     *
     * @return field names
     * @throws IllegalStateException if {@link #bindFields bindFields()} has not yet been invoked
     */
    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(this.bindings.keySet());
    }

    /**
     * Assign this instance's fields as editor bindings for the corresponding columns in the given {@link Grid}.
     *
     * <p>
     * This method invokes {@link Grid.Column#setEditorComponent(Component)} for each binding for which the {@link Grid}
     * has a column whose column key matches the binding property name.
     *
     * <p>
     * This method does nothing if {@link #bindFields bindFields()} has not been invoked yet.
     *
     * @param grid {@link Grid} for editor bindings
     * @throws IllegalArgumentException if {@code grid} is null
     * @see Grid.Column#setEditorComponent(Component)
     */
    public void setEditorComponents(Grid<T> grid) {
        if (grid == null)
            throw new IllegalArgumentException("null grid");
        this.bindings.forEach((propertyName, builder) -> {
            final Grid.Column<T> gridColumn = grid.getColumnByKey(propertyName);
            if (gridColumn == null)
                return;
            final HasValue<?, ?> field = builder.getField();
            if (!(field instanceof Component))
                return;
            gridColumn.setEditorComponent((Component)field);
        });
    }

    /**
     * Introspect the configured class for defined {@link FieldBuilder &#64;FieldBuilder.Foo} and
     * {@link ProvidesField &#64;ProvidesField} method annotations, create and configure corresponding fields,
     * configure bindings for those fields using any {@link Binding &#64;Binding} annotations, and bind those
     * fields into the given {@link Binder}.
     *
     * <p>
     * If the {@code binder} does not have a bean currently bound to it, then {@link ProvidesField &#64;ProvidesField}
     * annotations on instance methods will be ignored.
     *
     * @throws IllegalArgumentException if an invalid use of a widget annotation is encountered
     * @throws IllegalArgumentException if {@code binder} is null
     */
    public void bindFields(Binder<? extends T> binder) {

        // Sanity check
        if (binder == null)
            throw new IllegalArgumentException("null binder");

        // Start with an empty list of bindings
        final LinkedHashMap<String, Binder.BindingBuilder<? extends T, ?>> newBindings = new LinkedHashMap<>();
        final HashMap<String, MethodAnnotationScanner<T, ?>.MethodInfo> methodInfoMap = new HashMap<>(); // for building exceptions

        // Look for all bean property getter methods
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(this.type);
        } catch (IntrospectionException e) {
            throw new RuntimeException("unexpected exception", e);
        }
        final HashMap<String, String> getter2propertyMap = new HashMap<>();       // mapping from getter name -> property name
        for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
            final Method method = this.workAroundIntrospectorBug(propertyDescriptor.getReadMethod());

            // Add getter, if appropriate
            if (method != null && method.getReturnType() != void.class && method.getParameterTypes().length == 0)
                getter2propertyMap.put(method.getName(), propertyDescriptor.getName());
        }

        // Scan getters for widget annotations and create corresponding fields, also checking for conflicts
        this.getWidgetAnnotationTypes().forEach(annotationType -> {
            final Set<? extends MethodAnnotationScanner<T, ?>.MethodInfo> methodInfos = this.findAnnotatedMethods(annotationType);
            for (MethodAnnotationScanner<T, ?>.MethodInfo methodInfo : methodInfos) {
                final Method method = methodInfo.getMethod();

                // Identify the bean property
                final String propertyName = getter2propertyMap.get(method.getName());
                if (propertyName == null) {
                    throw new IllegalArgumentException("invalid @" + annotationType.getSimpleName()
                      + " annotation on non-getter method " + method.getName());
                }

                // Check for conflicting annotations
                final MethodAnnotationScanner<T, ?>.MethodInfo previousInfo = methodInfoMap.putIfAbsent(propertyName, methodInfo);
                if (previousInfo != null) {
                    throw new IllegalArgumentException(String.format(
                      "method %s.%s() has conflicting %s.* annotations: @%s on %s and @%s on %s",
                      this.type.getName(), method.getName(), this.getClass().getSimpleName(), annotationType.getSimpleName(),
                      method, previousInfo.getAnnotation().annotationType().getSimpleName(), previousInfo.getMethod()));
                }

                // Create field
                final HasValue<?, ?> field = this.buildField(methodInfo);

                // Create binding
                newBindings.put(propertyName, binder.forField(field));
            }
        });

        // Scan all methods for @ProvidesField annotations
        final HashMap<String, Method> providerMap = new HashMap<>();            // contains @ProvidesField methods
        this.buildProviderMap(providerMap);

        // Check for conflicts between @ProvidesField and other annotations and add fields to map
        for (Map.Entry<String, Method> entry : providerMap.entrySet()) {
            final String propertyName = entry.getKey();
            final Method method = entry.getValue();
            final T bean = binder.getBean();

            // Skip instance methods if there is no bean
            if ((method.getModifiers() & Modifier.STATIC) == 0 && bean == null)
                continue;

            // Verify field is not already defined
            if (newBindings.containsKey(propertyName)) {
                throw new IllegalArgumentException("conflicting annotations exist for property `" + propertyName
                  + "': annotation @" + ProvidesField.class.getSimpleName() + " on method " + method
                  + " cannot be combined with other @" + this.getClass().getSimpleName() + ".* annotation types");
            }

            // Invoke method to create field, if possible
            try {
                method.setAccessible(true);
            } catch (Exception e) {
                // ignore
            }
            final HasValue<?, ?> field;
            try {
                field = (HasValue<?, ?>)method.invoke(bean);
            } catch (Exception e) {
                throw new RuntimeException("error invoking @" + ProvidesField.class.getName()
                  + " annotation on method " + method, e);
            }

            // Create binding
            newBindings.put(propertyName, binder.forField(field));
        }

        // Scan getters for @Binding annotations
        final HashMap<String, Binding> bindingAnnotationMap = new HashMap<>();                  // contains @Binding's
        final MethodAnnotationScanner<T, Binding> bindingAnnotationScanner
          = new MethodAnnotationScanner<>(this.type, Binding.class);
        for (MethodAnnotationScanner<T, Binding>.MethodInfo methodInfo : bindingAnnotationScanner.findAnnotatedMethods()) {
            final String propertyName = getter2propertyMap.get(methodInfo.getMethod().getName());
            bindingAnnotationMap.put(propertyName, methodInfo.getAnnotation());
        }

        // Gather bindings in a list so we can order them via @Binding.order()
        final List<Map.Entry<String, Binder.BindingBuilder<? extends T, ?>>> builderList = new ArrayList<>(newBindings.entrySet());
        final HashMap<String, Double> orderMap = new HashMap<>(builderList.size());

        // Apply @Binding annotations (if any) and bind fields, and extract the order() values for sorting
        for (Map.Entry<String, Binder.BindingBuilder<? extends T, ?>> entry : builderList) {
            final String propertyName = entry.getKey();
            Binder.BindingBuilder<? extends T, ?> bindingBuilder = entry.getValue();

            // Apply @Binding annotation, if any
            final Binding bindingAnnotation = bindingAnnotationMap.get(propertyName);
            if (bindingAnnotation != null) {
                bindingBuilder = this.applyBindingAnnotation(bindingBuilder, bindingAnnotation);
                entry.setValue(bindingBuilder);
                orderMap.put(propertyName, bindingAnnotation.order());
            }

            // Complete the binding
            bindingBuilder.bind(propertyName);
        }

        // Sort builders
        builderList.sort(Comparator
          .<Map.Entry<String, Binder.BindingBuilder<? extends T, ?>>>comparingDouble(e -> orderMap.getOrDefault(e.getKey(), 0.0))
          .thenComparing(Map.Entry::getKey));

        // Build binding map, preserving binding order
        this.bindings.clear();
        for (Map.Entry<String, Binder.BindingBuilder<? extends T, ?>> entry : builderList)
            this.bindings.put(entry.getKey(), entry.getValue());
    }

    private <A extends Annotation> Set<MethodAnnotationScanner<T, A>.MethodInfo> findAnnotatedMethods(Class<A> annotationType) {
        return new MethodAnnotationScanner<T, A>(this.type, annotationType).findAnnotatedMethods();
    }

    private void buildProviderMap(Map<String, Method> providerMap) {
        final MethodAnnotationScanner<T, ProvidesField> scanner = new MethodAnnotationScanner<>(this.type, ProvidesField.class);
        for (MethodAnnotationScanner<T, ProvidesField>.MethodInfo methodInfo : scanner.findAnnotatedMethods())
            this.buildProviderMap(providerMap, methodInfo.getMethod().getDeclaringClass(), methodInfo.getMethod().getName());
    }

    private Method workAroundIntrospectorBug(Method method) {
        if (method == null)
            return method;
        for (Class<?> c = this.type; c != null && c != method.getClass(); c = c.getSuperclass()) {
            try {
                method = c.getDeclaredMethod(method.getName(), method.getParameterTypes());
            } catch (Exception e) {
                continue;
            }
            break;
        }
        return method;
    }

    /**
     * Apply a {@link Binding &#64;Binding} annotation to the given {@link Binder.BindingBuilder}.
     *
     * @param bindingBuilder binding under construction
     * @param annotation annotation to apply
     * @return a {@link Binder.BindingBuilder} configured from {@code annotation}
     * @throws IllegalArgumentException if either parameter is null
     */
    @SuppressWarnings("unchecked")
    protected Binder.BindingBuilder<? extends T, ?> applyBindingAnnotation(Binder.BindingBuilder<? extends T, ?> bindingBuilder,
      Binding annotation) {
        if (bindingBuilder == null)
            throw new IllegalArgumentException("null bindingBuilder");
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        if (annotation.requiredValidator() != Validator.class)
            bindingBuilder = bindingBuilder.asRequired(ReflectUtil.instantiate(annotation.requiredValidator()));
        else if (annotation.requiredProvider() != ErrorMessageProvider.class)
            bindingBuilder = bindingBuilder.asRequired(ReflectUtil.instantiate(annotation.requiredProvider()));
        else if (annotation.required().length() > 0)
            bindingBuilder = bindingBuilder.asRequired(annotation.required());
        if (annotation.converter() != Converter.class)
            bindingBuilder = bindingBuilder.withConverter(ReflectUtil.instantiate(annotation.converter()));
        if (annotation.validationStatusHandler() != BindingValidationStatusHandler.class) {
            bindingBuilder = bindingBuilder.withValidationStatusHandler(
              ReflectUtil.instantiate(annotation.validationStatusHandler()));
        }
        if (annotation.validator() != Validator.class)
            bindingBuilder = bindingBuilder.withValidator(ReflectUtil.instantiate(annotation.validator()));
        if (!annotation.nullRepresentation().equals(STRING_DEFAULT)) {
            try {
                bindingBuilder = ((Binder.BindingBuilder<T, String>)bindingBuilder).withNullRepresentation(
                  annotation.nullRepresentation());
            } catch (ClassCastException e) {
                // ignore
            }
        }
        return bindingBuilder;
    }

    // Used by buildBeanPropertyFields() to validate @ProvidesField annotations
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
     * Instantiate and configure a {@link HasValue} according to the given scanned annotation.
     *
     * @param methodInfo annotated method info
     * @param <A> annotation type
     * @return new field
     */
    protected <A extends Annotation> HasValue<?, ?> buildField(MethodAnnotationScanner<T, A>.MethodInfo methodInfo) {

        // Create annotation value applier
        final Method method = methodInfo.getMethod();
        final A annotation = methodInfo.getAnnotation();
        final A defaults = this.getDefaults(annotation);
        final AnnotationApplier<A> applier = new AnnotationApplier<>(method, annotation, defaults);

        // Instantiate field
        final HasValue<?, ?> field = applier.createField(this);

        // Configure field
        applier.configureField(field);

        // Done
        return field;
    }

    /**
     * Get an instance of the given widget annotation that has all default values.
     *
     * @param annotation the annotation to get defautls for
     * @param <A> annotation type
     * @return default annotation, or null if not a widget annotation
     * @throws IllegalArgumentException if {@code annotation} is not a widget annotation
     * @throws IllegalArgumentException if {@code annotation} is null
     */
    @SuppressWarnings("unchecked")
    protected <A extends Annotation> A getDefaults(A annotation) {
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        return (A)this.getDefaults(annotation.annotationType());
    }

    /**
     * Get an instance of the given widget annotation type with all default values.
     *
     * @param annotationType the annotation type to get defautls for
     * @param <A> annotation type
     * @return default annotation, or null if not a widget annotation
     * @throws IllegalArgumentException if {@code annotationType} is not a widget annotation type
     * @throws IllegalArgumentException if {@code annotationType} is null
     */
    protected <A extends Annotation> A getDefaults(Class<A> annotationType) {

        // Sanity check
        if (annotationType == null)
            throw new IllegalArgumentException("null annotationType");

        // Find corresponding annotation on our defaultsMethod()
        return AnnotationUtil.getAnnotations(this.getClass(), this.getAnnotationDefaultsMethodName())
          .stream()
          .filter(candidate -> candidate.annotationType().equals(annotationType))
          .map(annotationType::cast)
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(annotationType + " is not a defined widget annotation type"));
    }

    /**
     * Get all of the widget annotation types defined for this class.
     *
     * <p>
     * The implementation in {@link AbstractFieldBuilder} returns the types of all annotations found
     * on the {@linkplain #getAnnotationDefaultsMethodName annotation defaults method}.
     *
     * @return widget annotation types
     */
    protected Stream<Class<? extends Annotation>> getWidgetAnnotationTypes() {
        return AnnotationUtil.getAnnotations(this.getClass(), this.getAnnotationDefaultsMethodName())
          .stream()
          .map(Annotation::annotationType);
    }

    /**
     * Get the name of the method in this class that has all of the widget annotations (with default values) applied to it.
     *
     * <p>
     * The implementation in {@link AbstractFieldBuilder} returns {@value #DEFAULT_ANNOTATION_DEFAULTS_METHOD_NAME}.
     *
     * @return defaults method name, never null
     */
    protected String getAnnotationDefaultsMethodName() {
        return DEFAULT_ANNOTATION_DEFAULTS_METHOD_NAME;
    }

// AnnotationApplier

    /**
     * Class that knows how to apply annotation properties to a corresponding component.
     */
    static class AnnotationApplier<A extends Annotation> {

        protected final Method method;
        protected final A annotation;
        protected final A defaults;

        AnnotationApplier(Method method, A annotation, A defaults) {
            if (method == null)
                throw new IllegalArgumentException("null method");
            if (annotation == null)
                throw new IllegalArgumentException("null annotation");
            if (defaults == null)
                throw new IllegalArgumentException("null defaults");
            this.method = method;
            this.annotation = annotation;
            this.defaults = defaults;
        }

        public final Method getMethod() {
            return this.method;
        }

        public final A getAnnotation() {
            return this.annotation;
        }

        @SuppressWarnings("unchecked")
        public Class<? extends HasValue<?, ?>> getFieldType() {
            try {
                final Class<?> type = (Class<?>)this.annotation.getClass().getMethod("implementation").invoke(this.annotation);
                return (Class<? extends HasValue<?, ?>>)type.asSubclass(HasValue.class);
            } catch (Exception e) {
                throw new RuntimeException("unexpected exception", e);
            }
        }

        public HasValue<?, ?> createField(AbstractFieldBuilder<?, ?> fieldBuilder) {
            final HasValue<?, ?> field = ReflectUtil.instantiate(this.getFieldType());
            if (field instanceof AutoBuildAware)
                ((AutoBuildAware)field).onAutoBuild(fieldBuilder, this.method, this.annotation);
            return field;
        }

        @SuppressWarnings("unchecked")
        public void configureField(HasValue<?, ?> field) {

            // Apply non-default annotation values
            AnnotationUtil.applyAnnotationValues(field, "set", this.annotation, this.defaults,
              (fieldSetter, propertyName) -> propertyName);
            AnnotationUtil.applyAnnotationValues(field, "add", this.annotation, this.defaults,
              (fieldSetter, propertyName) -> fieldSetter.getName());
        }
    }

// Annotations

    /**
     * Specifies that the annotated method will return an {@link HasValue} suitable for editing the specified property.
     *
     * <p>
     * Annotated methods must take zero arguments and return a type compatible with {@link HasValue}.
     *
     * @see AbstractFieldBuilder
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
     * Properties correspond to methods in {@link Binder.BindingBuilder}.
     *
     * @see AbstractFieldBuilder
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Binding {

        /**
         * Get the ordering value for this field.
         *
         * @return field order value
         * @see AbstractFieldBuilder#getPropertyNames
         */
        double order() default 0;

        /**
         * Get "as required" error message.
         *
         * <p>
         * Only one of this property, {@link #requiredProvider}, or {@link #requiredValidator} should be set.
         *
         * @return "as required" error message
         * @see Binder.BindingBuilder#asRequired(String)
         */
        String required() default "";

        /**
         * Get "as required" error message provider class.
         *
         * <p>
         * Only one of this property, {@link #required}, or {@link #requiredValidator} should be set.
         *
         * @return "as required" error message provider class
         * @see Binder.BindingBuilder#asRequired(ErrorMessageProvider)
         */
        Class<? extends ErrorMessageProvider> requiredProvider() default ErrorMessageProvider.class;

        /**
         * Get "as required" validator class.
         *
         * <p>
         * Only one of this property, {@link #required}, or {@link #requiredProvider} should be set.
         *
         * @return "as required" error message provider class
         * @see Binder.BindingBuilder#asRequired(Validator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends Validator> requiredValidator() default Validator.class;

        /**
         * Get the converter class.
         *
         * @return converter class
         * @see Binder.BindingBuilder#withConverter(Converter)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends Converter> converter() default Converter.class;

        /**
         * Get the null representation.
         *
         * <p>
         * This property only works for fields that present a {@link String} value, such as
         * {@link com.vaadin.flow.component.textfield.TextField}.
         *
         * <p>
         * Note: the default value is just a placeholder, indicating that no null representation should be configured.
         *
         * @return null representation
         * @see Binder.BindingBuilder#withNullRepresentation
         */
        String nullRepresentation() default STRING_DEFAULT;

        /**
         * Get the validation status handler class.
         *
         * @return validation status handler class
         * @see Binder.BindingBuilder#withValidationStatusHandler
         */
        Class<? extends BindingValidationStatusHandler> validationStatusHandler() default BindingValidationStatusHandler.class;

        /**
         * Get the validator class.
         *
         * @return validator class
         * @see Binder.BindingBuilder#withValidator(Validator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends Validator> validator() default Validator.class;
    }
}
