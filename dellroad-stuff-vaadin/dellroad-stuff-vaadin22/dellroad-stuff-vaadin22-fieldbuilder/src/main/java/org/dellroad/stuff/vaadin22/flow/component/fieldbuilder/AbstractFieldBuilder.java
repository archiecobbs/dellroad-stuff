
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
import com.vaadin.flow.shared.util.SharedUtil;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.ReflectUtil;

import org.dellroad.stuff.vaadin22.flow.component.AutoBuildAware;

/**
 * Provides the machinery for auto-generated {@link FieldBuilder}-like classes.
 *
 * <p>
 * See {@link FieldBuilder} for details.
 *
 * @see FieldBuilder
 */
public abstract class AbstractFieldBuilder<S extends AbstractFieldBuilder<S, T>, T> implements Serializable {

    public static final String DEFAULT_ANNOTATION_DEFAULTS_METHOD_NAME = "annotationDefaultsMethod";

    private static final String STRING_DEFAULT = "<FieldBuilderStringDefault>";

    private static final long serialVersionUID = -3091638771700394722L;

    private final Class<T> type;
    private final LinkedHashMap<String, BindingInfo<T>> bindingInfoMap = new LinkedHashMap<>(); // info from scanned annotations

    private LinkedHashMap<String, FormField> formFieldMap;                                      // most recently built fields

    /**
     * Constructor.
     *
     * @param type backing object type
     * @throws IllegalArgumentException if {@code type} is null
     */
    protected AbstractFieldBuilder(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        this.type = type;
        this.scanForAnnotations();
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
     * Get all of the properties discovered by this instance from scanned annotations.
     *
     * <p>
     * This represents static information gathered by this instance by scanning the class hierarchy during construction.
     *
     * <p>
     * The returned {@link Map} iterates in order of {@link FormLayout#order &#64;FieldBuilder.FormLayout.order()},
     * then by property name.
     *
     * @return unmodifiable mapping of scanned properties keyed by property name
     */
    public Map<String, BindingInfo<T>> getProperties() {
        return Collections.unmodifiableMap(this.bindingInfoMap);
    }

    /**
     * Create, configure, and bind fields into the given {@link Binder}.
     *
     * <p>
     * If the {@code binder} does not have a bean currently bound to it, then any {@link ProvidesField &#64;ProvidesField}
     * annotations on instance methods will generate an error.
     *
     * <p>
     * After this method completes, the associated components can be obtained via {@link #getFields getFields()} or added to a
     * {@link com.vaadin.flow.component.formlayout.FormLayout} via {@link #addFields addFields()}.
     *
     * @param binder target binder
     * @throws IllegalArgumentException if an invalid use of a widget annotation is encountered
     * @throws IllegalArgumentException if {@code binder} is null
     */
    public void bindFields(Binder<? extends T> binder) {

        // Sanity check
        if (binder == null)
            throw new IllegalArgumentException("null binder");

        // Create and bind new fields and save associated components
        this.formFieldMap = new LinkedHashMap<>();
        this.bindingInfoMap.forEach((propertyName, info) -> {
            final FormField formField = info.createFormField(binder.getBean());
            info.bind(binder, formField.getField());
            this.formFieldMap.put(propertyName, formField);
        });
    }

    /**
     * Obtain the fields created by this instance in the most recent call to {@link #bindFields bindFields()}.
     *
     * <p>
     * The returned {@link Map} iterates in order of {@link FormLayout#order &#64;FieldBuilder.FormLayout.order()},
     * then by property name.
     *
     * @return unmodifiable mapping from property name to field/component
     * @throws IllegalStateException if {@link #bindFields bindFields()} has not yet been invoked
     */
    public Map<String, FormField> getFields() {

        // Sanity check
        if (this.formFieldMap == null)
            throw new IllegalStateException("bindFields() must be invoked first");

        // Return mapping
        return Collections.unmodifiableMap(this.formFieldMap);
    }

    /**
     * Add the fields' components created by this instance in the previous call to {@link #bindFields bindFields()}
     * to the given {@link com.vaadin.flow.component.formlayout.FormLayout}.
     *
     * <p>
     * Fields with {@link FormLayout#included &#64;FieldBuilder.FormLayout.included()} equal to false are omitted.
     *
     * <p>
     * The field components are added in order of {@link FormLayout#order &#64;FieldBuilder.FormLayout.order()},
     * then by property name.
     *
     * @param formLayout target layout
     * @throws IllegalArgumentException if {@code formLayout} is null
     * @throws IllegalStateException if {@link #bindFields bindFields()} has not yet been invoked
     * @throws IllegalStateException if this method is invoked twice in a row without an intervening call to
     *  {@link #bindFields bindFields()}
     */
    public void addFields(com.vaadin.flow.component.formlayout.FormLayout formLayout) {

        // Sanity check
        if (formLayout == null)
            throw new IllegalArgumentException("null formLayout");
        if (this.formFieldMap == null)
            throw new IllegalStateException("bindFields() must be invoked first");

        // Extract included fields from the binder and add them to the layout
        this.bindingInfoMap.forEach((propertyName, info) -> {
            if (info.isIncluded())
                info.addField(formLayout, this.formFieldMap.get(propertyName).getComponent());
        });
    }

    /**
     * Introspect the configured class for defined {@link FieldBuilder &#64;FieldBuilder.Foo},
     * {@link ProvidesField &#64;ProvidesField}, {@link Binding &#64;Binding}, and
     * {@link FormLayout &#64;FieldBuilder.FormLayout} annotations.
     *
     * <p>
     * Note: this method is invoked from the {@link AbstractFieldBuilder} constructor.
     *
     * @throws IllegalArgumentException if an invalid use of an annotation is encountered
     */
    protected void scanForAnnotations() {

        // Reset state
        this.bindingInfoMap.clear();
        this.formFieldMap = null;

        // Identify all bean property getter methods
        final BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(this.type);
        } catch (IntrospectionException e) {
            throw new RuntimeException("unexpected exception", e);
        }
        final HashMap<String, String> getter2propertyMap = new HashMap<>();                 // method name -> property name
        for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
            final Method method = this.workAroundIntrospectorBug(propertyDescriptor.getReadMethod());
            if (method != null && method.getReturnType() != void.class && method.getParameterTypes().length == 0)
                getter2propertyMap.put(method.getName(), propertyDescriptor.getName());
        }

        // Scan getter methods for @Binding annotations
        final HashMap<String, Binding> bindingAnnotationMap = new HashMap<>();
        this.findAnnotatedMethods(Binding.class).forEach(methodInfo -> {
            final String propertyName = getter2propertyMap.get(methodInfo.getMethod().getName());
            if (propertyName != null)
                bindingAnnotationMap.put(propertyName, methodInfo.getAnnotation());
        });

        // Scan getter methods for @FormLayout annotations
        final HashMap<String, FormLayout> formLayoutAnnotationMap = new HashMap<>();
        this.findAnnotatedMethods(FormLayout.class).forEach(methodInfo -> {
            final String propertyName = getter2propertyMap.get(methodInfo.getMethod().getName());
            if (propertyName != null)
                formLayoutAnnotationMap.put(propertyName, methodInfo.getAnnotation());
        });

        // Scan getter methods for @FieldBuilder.Foo annotations for all "Foo"
        this.getDeclarativeAnnotationTypes().forEach(annotationType -> {
            final Set<? extends MethodAnnotationScanner<T, ?>.MethodInfo> methodInfos = this.findAnnotatedMethods(annotationType);
            for (MethodAnnotationScanner<T, ?>.MethodInfo methodInfo : methodInfos) {
                final Method method = methodInfo.getMethod();

                // Identify the bean property
                final String propertyName = getter2propertyMap.get(method.getName());
                if (propertyName == null) {
                    throw new IllegalArgumentException("invalid @" + annotationType.getSimpleName()
                      + " annotation on non-getter method " + method.getName());
                }

                // Create new binding info and check for conflict
                final BindingInfo<T> bindingInfo = this.createBindingInfo(method, propertyName, methodInfo.getAnnotation(),
                  bindingAnnotationMap.get(propertyName), formLayoutAnnotationMap.get(propertyName),
                  bean -> this.buildDeclarativeField(methodInfo));
                final BindingInfo<T> previousInfo = this.bindingInfoMap.putIfAbsent(propertyName, bindingInfo);
                if (previousInfo != null) {
                    throw new IllegalArgumentException(String.format("conflicting annotations for property \"%s\": %s and %s",
                      propertyName, previousInfo.getOrigin(), bindingInfo.getOrigin()));
                }
            }
        });

        // Scan all zero-arg, non-void methods for @ProvidesField annotations
        this.findAnnotatedMethods(ProvidesField.class).forEach(methodInfo -> {

            // Get method and annotation
            final Method method = methodInfo.getMethod();
            final ProvidesField providesField = methodInfo.getAnnotation();
            final String propertyName = providesField.value();

            // Validate method return type is compatible with FormField OR (HasValue AND Component)
            if (!FormField.class.isAssignableFrom(method.getReturnType())) {
                Stream.of(HasValue.class, Component.class).forEach(requiredType -> {
                    if (!requiredType.isAssignableFrom(method.getReturnType())) {
                        throw new IllegalArgumentException(String.format(
                          "invalid @%s annotation on method %s: return type %s is not a sub-type of %s",
                          ProvidesField.class.getName(), method, method.getReturnType().getName(), requiredType.getName()));
                    }
                });
            }

            // Create new binding info and check for conflict
            final BindingInfo<T> bindingInfo = this.createBindingInfo(method, propertyName, providesField,
              bindingAnnotationMap.get(propertyName), formLayoutAnnotationMap.get(propertyName),
              bean -> this.buildProvidedField(methodInfo, bean));
            final BindingInfo<T> previousInfo = this.bindingInfoMap.putIfAbsent(propertyName, bindingInfo);
            if (previousInfo != null) {
                throw new IllegalArgumentException(String.format("conflicting annotations for property \"%s\": %s and %s",
                  propertyName, previousInfo.getOrigin(), bindingInfo.getOrigin()));
            }
        });

        // Sort infos by FormLayout.order() then property name
        final Comparator<Map.Entry<String, BindingInfo<T>>> comparator
          = Comparator.<Map.Entry<String, BindingInfo<T>>>comparingDouble(entry -> entry.getValue().getSortOrder())
            .thenComparing(Map.Entry::getKey);
        final ArrayList<Map.Entry<String, BindingInfo<T>>> infoList = new ArrayList<>(this.bindingInfoMap.entrySet());
        infoList.sort(comparator);
        this.bindingInfoMap.clear();
        infoList.forEach(entry -> this.bindingInfoMap.put(entry.getKey(), entry.getValue()));
    }

    private <A extends Annotation> Set<MethodAnnotationScanner<T, A>.MethodInfo> findAnnotatedMethods(Class<A> annotationType) {
        return new MethodAnnotationScanner<T, A>(this.type, annotationType).findAnnotatedMethods();
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
     * Construct and configure a field based on a {@code @FieldBuilder.Foo} declarative annotation.
     *
     * @param methodInfo annotated method info
     * @param <A> annotation type
     * @return new field
     */
    protected <A extends Annotation> FormField buildDeclarativeField(MethodAnnotationScanner<T, A>.MethodInfo methodInfo) {

        // Sanity check
        if (methodInfo == null)
            throw new IllegalArgumentException("null methodInfo");

        // Create annotation value applier
        final Method method = methodInfo.getMethod();
        final A annotation = methodInfo.getAnnotation();
        final A defaults = this.getDefaults(annotation);
        final AnnotationApplier<A> applier = new AnnotationApplier<>(method, annotation, defaults);

        // Instantiate field
        final HasValue<?, ?> field = applier.createField(this);
        if (!(field instanceof Component)) {
            throw new RuntimeException("internal error: field type generated from @" + annotation.annotationType().getName()
              + " annotation on method " + method + " is not a sub-type of " + Component.class);
        }

        // Configure field
        applier.configureField(field);

        // Done
        return new FormField(field, (Component)field);
    }

    /**
     * Construct a field and corresponding component using a {@link ProvidesField &#64;ProvidesField} annotated method.
     *
     * @param methodInfo annotated method info
     * @param bean binder bean, or null if none
     * @return new field
     */
    protected FormField buildProvidedField(MethodAnnotationScanner<T, ProvidesField>.MethodInfo methodInfo, Object bean) {

        // Sanity check
        if (methodInfo == null)
            throw new IllegalArgumentException("null methodInfo");

        // Sanity check bean vs. method
        final Method method = methodInfo.getMethod();
        if ((method.getModifiers() & Modifier.STATIC) != 0)
            bean = null;
        else if ((method.getModifiers() & Modifier.STATIC) == 0 && bean == null) {
            throw new IllegalArgumentException("@" + ProvidesField.class.getName() + " annotated method " + method
              + " is an instance method but the Binder has no bound bean");
        }

        // Invoke method
        try {
            method.setAccessible(true);
        } catch (Exception e) {
            // ignore
        }
        try {
            final Object field = method.invoke(bean);
            if (field == null)
                throw new IllegalArgumentException("null value returned");
            if (field instanceof FormField)
                return (FormField)field;
            return new FormField((HasValue<?, ?>)field, (Component)field);
        } catch (Exception e) {
            throw new RuntimeException("error invoking @" + ProvidesField.class.getName() + " annotated method " + method, e);
        }
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
    protected Stream<Class<? extends Annotation>> getDeclarativeAnnotationTypes() {
        return AnnotationUtil.getAnnotations(this.getClass(), this.getAnnotationDefaultsMethodName())
          .stream()
          .map(Annotation::annotationType);
    }

    /**
     * Create a {@link BindingInfo}.
     *
     * @param method annotated method
     * @param propertyName property name
     * @param annotation annotation found on {@code method}
     * @param binding associated {@link Binding &#64;Binding} annotation, if any
     * @param formLayout associated {@link FormLayout &#64;FieldBuilder.FormLayout} annotation, if any
     * @param fieldBuilder builds the field
     * @return new {@link BindingInfo}
     * @throws IllegalArgumentException if {@code method}, {@code propertyName}, {@code annotation}, or {@code fieldBuilder} is null
     */
    protected BindingInfo<T> createBindingInfo(Method method, String propertyName, Annotation annotation,
      Binding binding, FormLayout formLayout, Function<? super T, FormField> fieldBuilder) {
        return new BindingInfo<>(method, propertyName, annotation, binding, formLayout, fieldBuilder);
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

// BindingInfo

    /**
     * Holds static information gathered for one bean property based on {@link FieldBuilder &#64;FieldBuilder.Foo},
     * {@link ProvidesField &#64;ProvidesField}, {@link Binding &#64;Binding}, and
     * {@link FormLayout &#64;FieldBuilder.FormLayout} annotations.
     *
     * <p>
     * Instances are immutable.
     */
    public static class BindingInfo<T> {

        private final Method method;
        private final String propertyName;
        private final Annotation annotation;
        private final Binding binding;
        private final FormLayout formLayout;
        private final Function<? super T, FormField> fieldBuilder;

        /**
         * Constructor.
         *
         * @param method annotated method
         * @param propertyName property name
         * @param annotation annotation found on {@code method}
         * @param binding associated {@link Binding &#64;Binding} annotation, if any
         * @param formLayout associated {@link FormLayout &#64;FieldBuilder.FormLayout} annotation, if any
         * @param fieldBuilder builds the field
         * @throws IllegalArgumentException if {@code method}, {@code propertyName}, {@code annotation},
         *  or {@code fieldBuilder} is null
         */
        public BindingInfo(Method method, String propertyName, Annotation annotation, Binding binding,
          FormLayout formLayout, Function<? super T, FormField> fieldBuilder) {
            if (method == null)
                throw new IllegalArgumentException("null method");
            if (propertyName == null)
                throw new IllegalArgumentException("null propertyName");
            if (annotation == null)
                throw new IllegalArgumentException("null annotation");
            if (fieldBuilder == null)
                throw new IllegalArgumentException("null fieldBuilder");
            this.method = method;
            this.propertyName = propertyName;
            this.annotation = annotation;
            this.binding = binding;
            this.formLayout = formLayout;
            this.fieldBuilder = fieldBuilder;
        }

        /**
         * Return the annotated method associated with this instance.
         *
         * @return associated method
         */
        public Method getMethod() {
            return this.method;
        }

        /**
         * Return the property name associated with this instance.
         *
         * @return associated property name
         */
        public String getPropertyName() {
            return this.propertyName;
        }

        /**
         * Get the ({@link FieldBuilder &#64;FieldBuilder.Foo} or {@link ProvidesField &#64;ProvidesField})
         * annotation that annotates the method from which this instance originated.
         *
         * @return associated annotation type
         */
        public Annotation getAnnotation() {
            return this.annotation;
        }

        /**
         * Get the associated {@link Binding &#64;Binding} annotation, if any.
         *
         * @return associated {@link Binding &#64;Binding} annotation, or null if none
         */
        public Binding getBinding() {
            return this.binding;
        }

        /**
         * Get the associated {@link FormLayout &#64;FieldBuilder.FormLayout} annotation, if any.
         *
         * @return associated {@link FormLayout &#64;FieldBuilder.FormLayout} annotation, or null if none
         */
        public FormLayout getFormLayout() {
            return this.formLayout;
        }

        /**
         * Generate a sorting value for this field.
         *
         * @return sort order value
         */
        public double getSortOrder() {
            return Optional.ofNullable(this.formLayout)
              .map(FormLayout::order)
              .orElse(0.0);
        }

        /**
         * Get whether to include this field in a {@link com.vaadin.flow.component.formlayout.FormLayout} by default.
         *
         * @return true to include field, otherwise false
         */
        public boolean isIncluded() {
            return Optional.ofNullable(this.formLayout)
              .map(FormLayout::included)
              .orElse(true);
        }

        /**
         * Get a description of the origin of this instance (method and annotation).
         *
         * @return origin description
         */
        public String getOrigin() {
            return String.format("@%s on method %s", this.annotation.annotationType().getName(), this.method);
        }

        /**
         * Instantiate a new field and associated component according to this instance.
         *
         * @param bean instance bean, or null if none available
         * @return new field and associated component
         * @throws IllegalArgumentException if a bean instance is required but {@code bean} is null
         */
        public FormField createFormField(T bean) {
            return this.fieldBuilder.apply(bean);
        }

        /**
         * Bind the given field to the given {@link Binder} according to this instance.
         *
         * @param binder binder to bind new field to
         * @param field field to bind
         * @return the new binding
         * @throws IllegalArgumentException if either parameter is null
         */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Binder.Binding<? extends T, ?> bind(Binder<? extends T> binder, HasValue<?, ?> field) {

            // Sanity check
            if (binder == null)
                throw new IllegalArgumentException("null binder");
            if (field == null)
                throw new IllegalArgumentException("null field");

            // Create and configure BindingBuilder using @Binding annotation, if any
            Binder.BindingBuilder<? extends T, ?> bindingBuilder = binder.forField(field);
            if (this.binding != null) {
                if (this.binding.requiredValidator() != Validator.class)
                    bindingBuilder = bindingBuilder.asRequired(ReflectUtil.instantiate(this.binding.requiredValidator()));
                else if (this.binding.requiredProvider() != ErrorMessageProvider.class)
                    bindingBuilder = bindingBuilder.asRequired(ReflectUtil.instantiate(this.binding.requiredProvider()));
                else if (this.binding.required().length() > 0)
                    bindingBuilder = bindingBuilder.asRequired(this.binding.required());
                if (this.binding.converter() != Converter.class)
                    bindingBuilder = bindingBuilder.withConverter(ReflectUtil.instantiate(this.binding.converter()));
                if (this.binding.validationStatusHandler() != BindingValidationStatusHandler.class) {
                    bindingBuilder = bindingBuilder.withValidationStatusHandler(
                      ReflectUtil.instantiate(this.binding.validationStatusHandler()));
                }
                for (Class<? extends Validator> validatorClass : this.binding.validators())
                    bindingBuilder = bindingBuilder.withValidator(ReflectUtil.instantiate(validatorClass));
                if (!this.binding.nullRepresentation().equals(STRING_DEFAULT)) {
                    try {
                        bindingBuilder = ((Binder.BindingBuilder<T, String>)bindingBuilder).withNullRepresentation(
                          this.binding.nullRepresentation());
                    } catch (ClassCastException e) {
                        // ignore
                    }
                }
            }

            // Complete the binding
            return bindingBuilder.bind(this.propertyName);
        }

        /**
         * Add the given component to the given {@link com.vaadin.flow.component.formlayout.FormLayout} according to this instance.
         *
         * @param formLayout target layout
         * @param component field component to add
         * @throws IllegalArgumentException if either parameter is null
         */
        public void addField(com.vaadin.flow.component.formlayout.FormLayout formLayout, Component component) {

            // Sanity check
            if (formLayout == null)
                throw new IllegalArgumentException("null formLayout");
            if (component == null)
                throw new IllegalArgumentException("null component");

            // Get label: (a) from @FormLayout.label(); (b) from component.getLabel(); (c) derive from property name
            final String label = Optional.ofNullable(this.formLayout)
              .map(FormLayout::label)
              .filter(value -> !value.isEmpty())
              .orElseGet(() -> Optional.ofNullable(this.getLabel(component))
                                .orElseGet(() -> SharedUtil.camelCaseToHumanFriendly(this.propertyName)));

            // Add component to form
            final com.vaadin.flow.component.formlayout.FormLayout.FormItem formItem = formLayout.addFormItem(component, label);

            // Set colspan, if any
            Optional.ofNullable(this.formLayout)
              .map(FormLayout::colspan)
              .filter(colspan -> colspan > 0)
              .ifPresent(colspan -> formLayout.setColspan(formItem, colspan));
        }

        /**
         * Find a public zero-arg method {@code getLabel} returning {@link String} and invoke it, if it exists.
         *
         * @return the returned label, or null on failure
         */
        protected String getLabel(Object obj) {
            try {
                return (String)obj.getClass().getMethod("getLabel").invoke(obj);
            } catch (ReflectiveOperationException | SecurityException | ClassCastException | IllegalArgumentException e) {
                return null;
            }
        }
    }

// FormField

    /**
     * Holds a bound field and its corresponding component.
     *
     * <p>
     * Usually but not always these are the same object. This class supports both situations.
     *
     * <p>
     * An example of when they are not the same is when a {@link Grid} is used as the visual component for a single select
     * single select field created via {@link Grid#asSingleSelect} or multi-select field created via {@link Grid#asMultiSelect}.
     */
    public static class FormField {

        private final HasValue<?, ?> field;
        private final Component component;

        /**
         * Constructor.
         *
         * @param field field to bind to {@link Binder}
         * @param component component to add to a {@link com.vaadin.flow.component.formlayout.FormLayout}
         *  (typically the same object as {@code field})
         * @throws IllegalArgumentException if either parameter is null
         */
        public FormField(HasValue<?, ?> field, Component component) {
            if (field == null)
                throw new IllegalArgumentException("null field");
            if (component == null)
                throw new IllegalArgumentException("null component");
            this.field = field;
            this.component = component;
        }

        /**
         * Get the field bound into the {@link Binder}.
         *
         * @return bound field
         */
        public HasValue<?, ?> getField() {
            return this.field;
        }

        /**
         * Get the component to be displayed.
         *
         * @return field's component
         */
        public Component getComponent() {
            return this.component;
        }
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
     * Specifies that the annotated method will create a field suitable for editing the specified property.
     *
     * <p>
     * Annotated methods must take zero arguments and have a return type that is (a) a sub-type of both
     * {@link HasValue} and {@link Component}, or (b) a sub-type of {@link FormField}.
     *
     * @see AbstractFieldBuilder#bindFields FieldBuilder.bindFields()
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface ProvidesField {

        /**
         * The bean property that the annotated method's return value edits.
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
     * @see AbstractFieldBuilder#bindFields FieldBuilder.bindFields()
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Binding {

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
         * Get the validator class(es).
         *
         * @return zero or more validator classes
         * @see Binder.BindingBuilder#withValidator(Validator)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends Validator>[] validators() default {};
    }

    /**
     * Configures how {@link AbstractFieldBuilder FieldBuilder.addFields()}
     * adds a generated field to a {@link com.vaadin.flow.component.formlayout.FormLayout}.
     *
     * @see AbstractFieldBuilder#addFields FieldBuilder.addFields()
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface FormLayout {

        /**
         * Specify the column span of the associated field.
         *
         * @return field column span
         * @see com.vaadin.flow.component.formlayout.FormLayout#setColspan
         */
        int colspan() default 0;

        /**
         * Determine whether the associated field should be added to the {@link FormLayout}.
         *
         * <p>
         * This property can be used (for example) to elide a field in a subclass with an annotation on the overridden method.
         *
         * @return whether to include field in {@link com.vaadin.flow.component.formlayout.FormLayout}
         */
        boolean included() default true;

        /**
         * Specify a label for the associated field.
         *
         * <p>
         * By default, we delegate to {@link SharedUtil#camelCaseToHumanFriendly SharedUtil.SharedUtil.camelCaseToHumanFriendly()}
         * using the associated property name.
         *
         * @return field label
         * @see com.vaadin.flow.component.formlayout.FormLayout#addFormItem(Component, String) FormLayout.addFormItem()
         */
        String label() default "";

        /**
         * Get the ordering value for the associated field.
         *
         * <p>
         * This value determines the order in which fields are added to the {@link com.vaadin.flow.component.formlayout.FormLayout}.
         *
         * @return field ordering value
         */
        double order() default 0.0;
    }
}
