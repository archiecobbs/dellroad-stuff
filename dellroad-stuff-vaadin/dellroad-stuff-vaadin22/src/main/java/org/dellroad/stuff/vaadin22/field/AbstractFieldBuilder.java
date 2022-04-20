
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.field;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BindingValidationStatusHandler;
import com.vaadin.flow.data.binder.ErrorMessageProvider;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.util.SharedUtil;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.ObjectInputStream;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.Primitive;
import org.dellroad.stuff.java.ReflectUtil;

/**
 * Provides the machinery for auto-generated {@link FieldBuilder}-like classes.
 *
 * <p>
 * The annotation classes defined in this class, which are common to all {@link FieldBuilder}-like classes, may
 * be referenced using the concrete subclass name for consistency. For example, code can reference
 * {@link Binding &#64;FieldBuilder.Binding} instead of {@link Binding &#64;AbstractFieldBuilder.Binding}.
 *
 * <p>
 * See {@link FieldBuilder} for details and the standard implementation.
 *
 * @param <S> subclass type
 * @param <T> edited model type
 * @see FieldBuilder
 */
public abstract class AbstractFieldBuilder<S extends AbstractFieldBuilder<S, T>, T> implements Serializable {

    public static final String DEFAULT_IMPLEMENTATION_PROPERTY_NAME = "implementation";
    public static final String DEFAULT_ANNOTATION_DEFAULTS_METHOD_NAME = "annotationDefaultsMethod";

    private static final String STRING_DEFAULT = "<FieldBuilderStringDefault>";

    private static final long serialVersionUID = -3091638771700394722L;

    // Static info
    private final Class<T> type;
    private transient LinkedHashMap<String, BindingInfo> bindingInfoMap;            // info from scanned annotations
    private transient HashMap<Class<?>, Map<String, DefaultInfo>> defaultInfoMap;   // info from scanned @FieldDefault's

    // Mutable info
    private LinkedHashMap<String, FieldComponent<?>> fieldComponentMap;             // fields most recently built by bindFields()

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
     * Static information copy constructor.
     *
     * <p>
     * Using this constructor is more efficient than repeatedly scanning the same classes for the same annotations.
     *
     * <p>
     * Only the static information gathered by this instance by scanning for annotations is copied.
     * Any previously built fields are not copied.
     *
     * @param original original instance
     * @throws IllegalArgumentException if {@code original} is null
     */
    protected AbstractFieldBuilder(AbstractFieldBuilder<S, T> original) {
        if (original == null)
            throw new IllegalArgumentException("null original");
        this.type = original.type;
        this.bindingInfoMap = new LinkedHashMap<>(original.bindingInfoMap);
        this.defaultInfoMap = new HashMap<>(original.defaultInfoMap);
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
    public Map<String, BindingInfo> getScannedProperties() {
        return Collections.unmodifiableMap(this.bindingInfoMap);
    }

    /**
     * Get the default values discovered by this instance (if any) from scanned
     * {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault} annotations.
     *
     * <p>
     * The returned map is keyed by the return types of methods found with
     * {@link FieldBuilder &#64;FieldBuilder.Foo} declarative annotations.
     *
     * <p>
     * This represents static information gathered by this instance by scanning the class hierarchy during construction.
     *
     * @return unmodifiable mapping from model class to field defaults keyed by field property name
     */
    public Map<Class<?>, Map<String, DefaultInfo>> getScannedFieldDefaults() {
        return Collections.unmodifiableMap(this.defaultInfoMap);
    }

    /**
     * Create, configure, and bind fields into the given {@link Binder}.
     *
     * <p>
     * If the {@code binder} does not have a bean currently bound to it, then any
     * {@link ProvidesField &#64;FieldBuilder.ProvidesField} annotations on instance methods will generate an error.
     *
     * <p>
     * After this method completes, the associated components can be obtained via {@link #getFieldComponents getFieldComponents()}
     * or added to a {@link com.vaadin.flow.component.formlayout.FormLayout} via {@link #addFieldComponents addFieldComponents()}.
     *
     * @param binder target binder
     * @throws IllegalArgumentException if invalid annotation use is encountered
     * @throws IllegalArgumentException if {@code binder} is null
     */
    public void bindFields(Binder<? extends T> binder) {

        // Sanity check
        if (binder == null)
            throw new IllegalArgumentException("null binder");

        // Create and bind new fields and save associated components
        this.fieldComponentMap = new LinkedHashMap<>();
        this.bindingInfoMap.forEach((propertyName, info) -> {
            final FieldComponent<?> fieldComponent = info.createFieldComponent(binder.getBean());
            info.bind(binder, fieldComponent.getField());
            this.fieldComponentMap.put(propertyName, fieldComponent);
        });

        // Wire up and initialize @EnabledBy dependencies
        this.bindingInfoMap.forEach((propertyName, info) ->
          Optional.ofNullable(info.getEnabledBy())
            .map(EnabledBy::value)
            .flatMap(binder::getBinding)
            .map(Binder.Binding::getField)
            .ifPresent(field ->
              this.setEnablement(field, this.fieldComponentMap.get(propertyName), info.getEnabledBy().resetOnDisable())));
    }

    private <V> void setEnablement(HasValue<?, V> field, FieldComponent<?> fieldComponent, boolean resetOnDisable) {
        final Component component = fieldComponent.getComponent();
        final HasEnabled hasEnabled = component instanceof HasEnabled ? (HasEnabled)component : null;
        if (hasEnabled == null && !resetOnDisable)                          // nothing to do
            return;
        final Consumer<V> newValueHandler = value -> {
            final boolean enabled = !Objects.equals(value, field.getEmptyValue());
            if (!enabled && resetOnDisable)
                this.resetField(fieldComponent.getField());
            if (hasEnabled != null)
                hasEnabled.setEnabled(enabled);
        };
        field.addValueChangeListener(e -> newValueHandler.accept(e.getValue()));
        newValueHandler.accept(field.getValue());
    }

    private <V> void resetField(HasValue<?, V> field) {
        field.setValue(field.getEmptyValue());
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
    public Map<String, FieldComponent<?>> getFieldComponents() {

        // Sanity check
        if (this.fieldComponentMap == null)
            throw new IllegalStateException("bindFields() must be invoked first");

        // Return mapping
        return Collections.unmodifiableMap(this.fieldComponentMap);
    }

    /**
     * Add the fields' components created by this instance in the previous call to {@link #bindFields bindFields()}
     * to the given {@link com.vaadin.flow.component.formlayout.FormLayout}.
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
    public void addFieldComponents(com.vaadin.flow.component.formlayout.FormLayout formLayout) {

        // Sanity check
        if (formLayout == null)
            throw new IllegalArgumentException("null formLayout");
        if (this.fieldComponentMap == null)
            throw new IllegalStateException("bindFields() must be invoked first");

        // Extract included fields from the binder and add them to the layout
        this.bindingInfoMap.forEach(
          (propertyName, info) -> info.addField(formLayout, this.fieldComponentMap.get(propertyName).getComponent()));
    }

    /**
     * Introspect the configured class for defined {@link FieldBuilder &#64;FieldBuilder.Foo},
     * {@link ProvidesField &#64;FieldBuilder.ProvidesField}, {@link Binding &#64;FieldBuilder.Binding},
     * and {@link FormLayout &#64;FieldBuilder.FormLayout} annotations.
     *
     * <p>
     * Note: this method is invoked from the {@link AbstractFieldBuilder} constructor.
     *
     * @throws IllegalArgumentException if an invalid use of an annotation is encountered
     */
    protected void scanForAnnotations() {

        // Reset state
        this.bindingInfoMap = new LinkedHashMap<>();
        this.defaultInfoMap = new HashMap<>();

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

                // Get @NullifyCheckbox, if any
                final NullifyCheckbox nullifyCheckbox = method.getAnnotation(NullifyCheckbox.class);

                // Get @EnabledBy, if any
                final EnabledBy enabledBy = method.getAnnotation(EnabledBy.class);

                // Create new binding info and check for conflict
                final BindingInfo bindingInfo = this.createBindingInfo(method, propertyName, methodInfo.getAnnotation(),
                  bindingAnnotationMap.get(propertyName), formLayoutAnnotationMap.get(propertyName), nullifyCheckbox, enabledBy,
                  (info, bean) -> this.buildDeclarativeField(info));
                final BindingInfo previousInfo = this.bindingInfoMap.putIfAbsent(propertyName, bindingInfo);
                if (previousInfo != null) {
                    throw new IllegalArgumentException(String.format("conflicting annotations for property \"%s\": %s and %s",
                      propertyName, previousInfo.getOrigin(), bindingInfo.getOrigin()));
                }
            }
        });

        // Inspect model types and scan them for static methods with @FieldDefault annotations
        this.bindingInfoMap.values().stream()
          .map(BindingInfo::getMethod)
          .map(Method::getReturnType)
          .distinct()                                                               // avoid redundant scans
          .forEach(modelType -> {
            final Map<String, DefaultInfo> defaultInfo = this.scanForFieldDefaultAnnotations(modelType);
            if (!defaultInfo.isEmpty())
                this.defaultInfoMap.put(modelType, defaultInfo);
          });

        // Scan all zero-arg, non-void methods for @ProvidesField annotations
        new MethodAnnotationScanner<T, ProvidesField>(this.type, ProvidesField.class) {

            @Override
            protected boolean includeMethod(Method method, ProvidesField annotation) {
                final Class<?> returnType = method.getReturnType();
                if (!(HasValue.class.isAssignableFrom(returnType) && Component.class.isAssignableFrom(returnType))
                  && !FieldComponent.class.isAssignableFrom(returnType)) {
                    throw new IllegalArgumentException("incompatible return type for @ProvidesField annotation on method "
                      + method);
                }
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (!(parameterTypes.length == 0
                  || (parameterTypes.length == 1 && FieldBuilderContext.class.isAssignableFrom(parameterTypes[0])))) {
                    throw new IllegalArgumentException("incompatible parameter type(s) for @ProvidesField annotation on method "
                      + method);
                }
                return true;
            }
        }.findAnnotatedMethods().forEach(methodInfo -> {

            // Get method and annotation
            final Method method = methodInfo.getMethod();
            final ProvidesField providesField = methodInfo.getAnnotation();
            final String propertyName = providesField.value();

            // Validate method return type is compatible with FieldComponent OR (HasValue AND Component)
            if (!FieldComponent.class.isAssignableFrom(method.getReturnType())) {
                Stream.of(HasValue.class, Component.class).forEach(requiredType -> {
                    if (!requiredType.isAssignableFrom(method.getReturnType())) {
                        throw new IllegalArgumentException(String.format(
                          "invalid @%s annotation on method %s: return type %s is not a sub-type of %s",
                          ProvidesField.class.getName(), method, method.getReturnType().getName(), requiredType.getName()));
                    }
                });
            }

            // Get @NullifyCheckbox, if any
            final NullifyCheckbox nullifyCheckbox = method.getAnnotation(NullifyCheckbox.class);

            // Get @EnabledBy, if any
            final EnabledBy enabledBy = method.getAnnotation(EnabledBy.class);

            // Create new binding info and check for conflict
            final BindingInfo bindingInfo = this.createBindingInfo(method, propertyName, providesField,
              bindingAnnotationMap.get(propertyName), formLayoutAnnotationMap.get(propertyName), nullifyCheckbox,
              enabledBy, (info, bean) -> this.buildProvidedField(info, methodInfo, bean));
            final BindingInfo previousInfo = this.bindingInfoMap.putIfAbsent(propertyName, bindingInfo);
            if (previousInfo != null) {
                throw new IllegalArgumentException(String.format("conflicting annotations for property \"%s\": %s and %s",
                  propertyName, previousInfo.getOrigin(), bindingInfo.getOrigin()));
            }
        });

        // Sort infos by FormLayout.order() then property name
        final Comparator<Map.Entry<String, BindingInfo>> comparator
          = Comparator.<Map.Entry<String, BindingInfo>>comparingDouble(entry -> entry.getValue().getSortOrder())
            .thenComparing(Map.Entry::getKey);
        final ArrayList<Map.Entry<String, BindingInfo>> infoList = new ArrayList<>(this.bindingInfoMap.entrySet());
        infoList.sort(comparator);
        this.bindingInfoMap.clear();
        infoList.forEach(entry -> this.bindingInfoMap.put(entry.getKey(), entry.getValue()));
    }

    /**
     * Scan the given model type for {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault} annotations.
     *
     * @param modelType model type for some edited property
     * @param <M> model type
     * @return mapping from field property name to default info for that property (possibly empty)
     * @throws IllegalArgumentException if {@code modelType} is null
     */
    protected <M> Map<String, DefaultInfo> scanForFieldDefaultAnnotations(Class<M> modelType) {

        // Sanity check
        if (modelType == null)
            throw new IllegalArgumentException("null modelType");

        // Scan type for @FieldDefault annotations
        final HashMap<String, DefaultInfo> defaultMap = new HashMap<>();
        new MethodAnnotationScanner<M, FieldDefault>(modelType, FieldDefault.class) {

            // We only want non-void, zero-arg static methods
            @Override
            protected boolean includeMethod(Method method, FieldDefault annotation) {
                return super.includeMethod(method, annotation) && (method.getModifiers() & Modifier.STATIC) != 0;
            }
        }.findAnnotatedMethods().forEach(methodInfo -> {

            // Get method and annotation
            final Method method = methodInfo.getMethod();
            final FieldDefault defaultAnnotation = methodInfo.getAnnotation();
            final String propertyName = defaultAnnotation.value();

            // Create new default info and check for conflict
            defaultMap.merge(propertyName, new DefaultInfo(method, propertyName), (info1, info2) -> {

                // Choose the method declared in the narrower type, if possible
                final int diff = ReflectUtil.getClassComparator().compare(
                  info1.getMethod().getDeclaringClass(), info2.getMethod().getDeclaringClass());
                if (diff < 0)
                    return info1;
                if (diff > 0)
                    return info2;

                // Declaring types are not comparable, so we don't know which one to pick
                throw new IllegalArgumentException(String.format(
                  "conflicting @%s annotations for field property \"%s\" on methods %s and %s",
                  FieldDefault.class.getName(), propertyName, info1.getMethod(), info2.getMethod()));
            });
        });

        // Done
        return Collections.unmodifiableMap(defaultMap);
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
     * @param bindingInfo field binding context
     * @return new field
     */
    protected FieldComponent<?> buildDeclarativeField(BindingInfo bindingInfo) {

        // Sanity check
        if (bindingInfo == null)
            throw new IllegalArgumentException("null bindingInfo");

        // Create annotation value applier
        final Annotation annotation = bindingInfo.getAnnotation();
        final AnnotationApplier<?> applier = this.new AnnotationApplier<>(bindingInfo, this.getDefaultsFor(annotation));

        // Instantiate field
        final HasValue<?, ?> field = applier.createField();
        if (!(field instanceof Component)) {
            throw new RuntimeException("internal error: field type generated from @" + annotation.annotationType().getName()
              + " annotation on method " + bindingInfo.getMethod() + " is not a sub-type of " + Component.class);
        }

        // Apply any applicable @FieldDefault annotations found in the data model class
        this.applyFieldDefaultAnnotations(field, this.newFieldBuilderContext(bindingInfo).inferDataModelType());

        // Configure field from annotation (this will only apply non-default values)
        applier.configureField(field);

        // Done
        return new FieldComponent<>(field, (Component)field);
    }

    /**
     * Apply defaults derived from {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault} annotations
     * to the given field.
     *
     * @param field the field being configured
     * @param modelType the type of the property edited by {@code field}
     * @throws IllegalArgumentException if either parameter is null
     */
    protected void applyFieldDefaultAnnotations(HasValue<?, ?> field, Class<?> modelType) {

        // Sanity check
        if (field == null)
            throw new IllegalArgumentException("null field");
        if (modelType == null)
            throw new IllegalArgumentException("null modelType");

        // Find the narrowest type compatible with modelType that has default info, and apply all those defaults
        this.defaultInfoMap.entrySet().stream()
          .filter(entry -> entry.getKey().isAssignableFrom(modelType))
          .reduce(BinaryOperator.minBy(Map.Entry.comparingByKey(ReflectUtil.getClassComparator())))
          .map(Map.Entry::getValue)
          .map(Map::values)
          .map(Collection::stream)
          .ifPresent(stream -> stream.forEach(info -> info.applyTo(field)));
    }

    /**
     * Construct a field and corresponding component using a {@link ProvidesField &#64;FieldBuilder.ProvidesField} annotated method.
     *
     * @param bindingInfo field binding context
     * @param methodInfo scanned annotation context
     * @param bean binder bean, or null if none
     * @return new field
     */
    protected FieldComponent<?> buildProvidedField(BindingInfo bindingInfo,
      MethodAnnotationScanner<T, ProvidesField>.MethodInfo methodInfo, Object bean) {

        // Sanity check
        if (bindingInfo == null)
            throw new IllegalArgumentException("null bindingInfo");
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
            final Object field = method.getParameterTypes().length > 0 ?
              method.invoke(bean, this.newFieldBuilderContext(bindingInfo)) : method.invoke(bean);
            if (field == null)
                throw new IllegalArgumentException("null value returned");
            if (field instanceof FieldComponent)
                return (FieldComponent<?>)field;
            return new FieldComponent<>((HasValue<?, ?>)field, (Component)field);
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
    protected <A extends Annotation> A getDefaultsFor(A annotation) {
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        return (A)this.getDefaultsFor(annotation.annotationType());
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
    protected <A extends Annotation> A getDefaultsFor(Class<A> annotationType) {

        // Sanity check
        if (annotationType == null)
            throw new IllegalArgumentException("null annotationType");

        // Find corresponding annotation on our defaultsMethod()
        return Optional.ofNullable(this.getAnnotationDefaultsMethod().getAnnotation(annotationType))
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
    @SuppressWarnings("unchecked")
    protected Stream<Class<? extends Annotation>> getDeclarativeAnnotationTypes() {
        return ((List<Class<? extends Annotation>>)ReflectUtil.invoke(this.getAnnotationDefaultsMethod(), null)).stream();
    }

    /**
     * Create a {@link BindingInfo}.
     *
     * @param method annotated method
     * @param propertyName property name
     * @param annotation annotation found on {@code method}
     * @param binding associated {@link Binding &#64;FieldBuilder.Binding} annotation, if any
     * @param formLayout associated {@link FormLayout &#64;FieldBuilder.FormLayout} annotation, if any
     * @param nullifyCheckbox associated from {@link NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox}, if any
     * @param enabledBy associated from {@link EnabledBy &#64;FieldBuilder.EnabledBy}, if any
     * @param fieldBuilder builds the field
     * @return new {@link BindingInfo}
     * @throws IllegalArgumentException if {@code method}, {@code propertyName}, {@code annotation}, or {@code fieldBuilder} is null
     */
    protected BindingInfo createBindingInfo(Method method, String propertyName, Annotation annotation,
      Binding binding, FormLayout formLayout, NullifyCheckbox nullifyCheckbox, EnabledBy enabledBy,
      BiFunction<BindingInfo, ? super T, FieldComponent<?>> fieldBuilder) {
        return new BindingInfo(method, propertyName, annotation, binding, formLayout, nullifyCheckbox, enabledBy, fieldBuilder);
    }

    /**
     * Get the method in this class that has all of the widget annotations (with default values) applied to it.
     *
     * @return defaults method name, never null
     */
    protected Method getAnnotationDefaultsMethod() {
        Method method = null;
        for (Class<?> stype = this.getClass(); stype != null; stype = stype.getSuperclass()) {
            try  {
                method = stype.getDeclaredMethod(this.getAnnotationDefaultsMethodName());
            } catch (NoSuchMethodException e) {
                continue;
            }
            break;
        }
        if (method == null)
            throw new RuntimeException("internal error: method " + this.getAnnotationDefaultsMethodName() + "() not found");
        method.setAccessible(true);
        return method;
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

    /**
     * Get the name of the annotation property that specifies the implementation class.
     *
     * <p>
     * The implementation in {@link AbstractFieldBuilder} returns {@value #DEFAULT_IMPLEMENTATION_PROPERTY_NAME}.
     *
     * @return implementation property name, never null
     */
    protected String getImplementationPropertyName() {
        return DEFAULT_IMPLEMENTATION_PROPERTY_NAME;
    }

    /**
     * Instantiate a field (or whatever) from the given {@link Class}-valued annotation property.
     *
     * <p>
     * We use the {@link FieldBuilderContext} constructor if available, otherwise default constructor.
     *
     * @param expectedType expected type of thing we're instantiating
     * @param bindingInfo binding context
     * @param annotation annotation with {@link Class}-valued property
     * @param propertyName annotation property name having type {@link Class}
     * @param <F> field type
     * @throws IllegalArgumentException if any parameter is null
     */
    protected <F> F instantiate(Class<F> expectedType, BindingInfo bindingInfo, Annotation annotation, String propertyName) {

        // Sanity check
        if (expectedType == null)
            throw new IllegalArgumentException("null expectedType");
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        if (propertyName == null)
            throw new IllegalArgumentException("null propertyName");

        // Determine the class to instantiate
        final Class<? extends F> implType;
        try {
            implType = ((Class<?>)annotation.annotationType().getMethod(propertyName).invoke(annotation)).asSubclass(expectedType);
        } catch (Exception e) {
            throw new RuntimeException("unexpected exception", e);
        }

        // Instantiate
        return this.instantiate(implType, bindingInfo);
    }

    /**
     * Instantiate a field (or whatever) from the given {@link Class}.
     *
     * <p>
     * We use the {@link FieldBuilderContext} constructor if available, otherwise default constructor.
     *
     * @param expectedType expected type of thing we're instantiating
     * @param bindingInfo binding context
     * @throws IllegalArgumentException if any parameter is null
     */
    protected <T> T instantiate(Class<T> type, BindingInfo bindingInfo) {

        // Sanity check
        if (type == null)
            throw new IllegalArgumentException("null type");
        if (bindingInfo == null)
            throw new IllegalArgumentException("null bindingInfo");

        // Create FieldBuilderContext "context" so we know its type
        final FieldBuilderContext context = this.newFieldBuilderContext(bindingInfo);

        // Provide helpful exception messages
        final Function<Supplier<?>, Object> errorWrapper = invoker -> {
            try {
                return invoker.get();
            } catch (RuntimeException e) {
                throw new RuntimeException("error instantiating " + type.getName() + " for " + bindingInfo.getOrigin(), e);
            }
        };

        // Find the best (narrowest) matching constructor for "context", if any, else fall back to default constructor
        return type.cast(Stream.of(type.getConstructors())          // returns Constructor<?>[] instead of Constructor<T>[]
          .filter(c -> c.getParameterCount() == 1)
          .filter(c -> c.getParameterTypes()[0].isInstance(context))
          .min(Comparator.comparing(c -> c.getParameterTypes()[0], ReflectUtil.getClassComparator()))
          .map(constructor -> errorWrapper.apply(() -> ReflectUtil.instantiate(constructor, context)))
          .orElseGet(() -> errorWrapper.apply(() -> ReflectUtil.instantiate(type))));
    }

    /**
     * Create an {@link FieldBuilderContext} from the given method and annotation.
     *
     * <p>
     * The implementation in {@link AbstractFieldBuilder} creates a {@link FieldBuilderContextImpl}.
     *
     * @param bindingInfo binding context
     * @throws IllegalArgumentException if {@code bindingInfo} is null
     */
    protected FieldBuilderContext newFieldBuilderContext(BindingInfo bindingInfo) {
        return this.new FieldBuilderContextImpl(bindingInfo);
    }

// BindingInfo

    /**
     * {@link FieldBuilder} static configuration information for one bean property.
     *
     * <p>
     * This class captures the information gathered from these annotations for some bean property:
     * <ul>
     *  <li>{@link FieldBuilder &#64;FieldBuilder.Foo} (for some widget class {@code Foo})
     *  <li>{@link ProvidesField &#64;FieldBuilder.ProvidesField}
     *  <li>{@link Binding &#64;FieldBuilder.Binding}
     *  <li>{@link FormLayout &#64;FieldBuilder.FormLayout}
     *  <li>{@link NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox}
     *  <li>{@link EnabledBy &#64;FieldBuilder.EnabledBy}
     * </ul>
     *
     * <p>
     * Instances are immutable.
     */
    public class BindingInfo {

        private final Method method;
        private final String propertyName;
        private final Annotation annotation;
        private final Binding binding;
        private final FormLayout formLayout;
        private final NullifyCheckbox nullifyCheckbox;
        private final EnabledBy enabledBy;
        private final BiFunction<BindingInfo, ? super T, FieldComponent<?>> fieldBuilder;

        /**
         * Constructor.
         *
         * @param method annotated method
         * @param propertyName property name
         * @param annotation annotation found on {@code method}
         * @param binding associated {@link Binding &#64;FieldBuilder.Binding} annotation, if any
         * @param formLayout associated {@link FormLayout &#64;FieldBuilder.FormLayout} annotation, if any
         * @param nullifyCheckbox associated {@link NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox}, if any
         * @param enabledBy associated from {@link EnabledBy &#64;FieldBuilder.EnabledBy}, if any
         * @param fieldBuilder builds the field
         * @throws IllegalArgumentException if {@code method}, {@code propertyName}, {@code annotation},
         *  or {@code fieldBuilder} is null
         */
        public BindingInfo(Method method, String propertyName, Annotation annotation, Binding binding,
          FormLayout formLayout, NullifyCheckbox nullifyCheckbox, EnabledBy enabledBy,
          BiFunction<BindingInfo, ? super T, FieldComponent<?>> fieldBuilder) {
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
            this.nullifyCheckbox = nullifyCheckbox;
            this.enabledBy = enabledBy;
            this.fieldBuilder = fieldBuilder;
        }

        /**
         * Return the {@link AbstractFieldBuilder} that created this instance.
         *
         * @return associated field builder
         */
        public AbstractFieldBuilder<S, T> getFieldBuilder() {
            return AbstractFieldBuilder.this;
        }

        /**
         * Get the annotated method associated with this instance.
         *
         * <p>
         * This is the method that is annotated with the {@link FieldBuilder &#64;FieldBuilder.Foo} annotation.
         * Note that the other annotations (e.g., {@link Binding &#64;FieldBuilder.Binding}) could possibly
         * have come from different methods (they would necessarily be overriding or overridden versions of
         * this method).
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
         * Get the ({@link FieldBuilder &#64;FieldBuilder.Foo} or {@link ProvidesField &#64;FieldBuilder.ProvidesField})
         * annotation that annotates {@linkplain #getMethod the method from which this instance originated}.
         *
         * @return associated annotation type
         */
        public Annotation getAnnotation() {
            return this.annotation;
        }

        /**
         * Get the associated {@link Binding &#64;FieldBuilder.Binding} annotation, if any.
         *
         * @return associated {@link Binding &#64;FieldBuilder.Binding} annotation, or null if none
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
         * Get the associated {@link NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox} annotation, if any.
         *
         * @return associated {@link NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox} annotation, or null if none
         */
        public NullifyCheckbox getNullifyCheckbox() {
            return this.nullifyCheckbox;
        }

        /**
         * Get the associated {@link EnabledBy &#64;FieldBuilder.EnabledBy} annotation, if any.
         *
         * @return associated {@link EnabledBy &#64;FieldBuilder.EnabledBy} annotation, or null if none
         */
        public EnabledBy getEnabledBy() {
            return this.enabledBy;
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
         * Get a description of the origin of this instance (method and annotation).
         *
         * @return origin description
         */
        public String getOrigin() {
            return String.format("@%s on method %s", this.annotation.annotationType().getName(), this.method);
        }

        /**
         * Instantiate a field (or whatever) from the given {@link Class}.
         *
         * <p>
         * We use the {@link FieldBuilderContext} constructor if available, otherwise default constructor.
         *
         * @param type expected type of thing we're instantiating
         */
        protected <T> T instantiate(Class<T> type) {
            return AbstractFieldBuilder.this.instantiate(type, this);
        }

        /**
         * Instantiate a new field and associated component according to this instance.
         *
         * @param bean instance bean, or null if none available
         * @return new field and associated component
         * @throws IllegalArgumentException if a bean instance is required but {@code bean} is null
         */
        public FieldComponent<?> createFieldComponent(T bean) {
            FieldComponent<?> fieldComponent = this.fieldBuilder.apply(this, bean);
            if (this.nullifyCheckbox != null)
                fieldComponent = this.addNullifyCheckbox(fieldComponent);
            return fieldComponent;
        }

        protected <T> FieldComponent<T> addNullifyCheckbox(FieldComponent<T> fieldComponent) {
            if (fieldComponent == null)
                throw new IllegalArgumentException("null fieldComponent");
            if (this.nullifyCheckbox == null)
                throw new IllegalStateException("no nullifyCheckbox");
            final NullableField<T> field = new NullableField<>(fieldComponent.getField(),
              fieldComponent.getComponent(), new Checkbox(this.nullifyCheckbox.value()));
            field.setResetOnDisable(this.nullifyCheckbox.resetOnDisable());
            return new FieldComponent<>(field);
        }

        /**
         * Bind the given field to the given {@link Binder} according to this instance.
         *
         * @param binder binder to bind new field to
         * @param field field to bind
         * @param <V> field value type
         * @return the new binding
         * @throws IllegalArgumentException if either parameter is null
         */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <V> Binder.Binding<? extends T, ?> bind(Binder<? extends T> binder, HasValue<?, V> field) {

            // Sanity check
            if (binder == null)
                throw new IllegalArgumentException("null binder");
            if (field == null)
                throw new IllegalArgumentException("null field");

            // Create and configure BindingBuilder using @Binding annotation, if any
            Binder.BindingBuilder<? extends T, V> bindingBuilder = binder.forField(field);
            if (this.binding != null) {
                if (this.binding.requiredValidator() != Validator.class)
                    bindingBuilder = bindingBuilder.asRequired(this.instantiate(this.binding.requiredValidator()));
                else if (this.binding.requiredProvider() != ErrorMessageProvider.class)
                    bindingBuilder = bindingBuilder.asRequired(this.instantiate(this.binding.requiredProvider()));
                else if (this.binding.required().length() > 0)
                    bindingBuilder = bindingBuilder.asRequired(this.binding.required());
                if (this.binding.converter() != Converter.class)
                    bindingBuilder = bindingBuilder.withConverter(this.instantiate(this.binding.converter()));
                if (this.binding.validationStatusHandler() != BindingValidationStatusHandler.class) {
                    bindingBuilder = bindingBuilder.withValidationStatusHandler(
                      this.instantiate(this.binding.validationStatusHandler()));
                }
                for (Class<? extends Validator> validatorClass : this.binding.validators())
                    bindingBuilder = bindingBuilder.withValidator(this.instantiate(validatorClass));
                if (!this.binding.nullRepresentation().equals(STRING_DEFAULT)) {
                    try {
                        bindingBuilder = bindingBuilder.withNullRepresentation((V)this.binding.nullRepresentation());
                    } catch (ClassCastException e) {
                        // ignore
                    }
                }
            }

            // Add recursive validation if field implements ValidatingField
            if (field instanceof ValidatingField) {
                final ValidatingField<?, V> validatingField = (ValidatingField<?, V>)field;
                bindingBuilder = validatingField.addValidationTo(bindingBuilder);
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
         * @param obj target object
         * @return the returned label, or null on failure
         */
        protected String getLabel(Object obj) {
            try {
                return (String)obj.getClass().getMethod("getLabel").invoke(obj);
            } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return String.format("%s[property=\"%s\",method=%s,annotation=@%s]",
              this.getClass().getSimpleName(), this.getPropertyName(), this.getMethod(),
              this.getAnnotation().annotationType().getSimpleName());
        }
    }

// DefaultInfo

    /**
     * Holds static information gathered from scanning {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault}
     * annotations.
     *
     * <p>
     * Instances are immutable.
     */
    public static class DefaultInfo {

        private Method method;
        private String propertyName;

        /**
         * Constructor.
         *
         * @param method the annotated method that provides the alternate default value
         * @param propertyName the name of the field property provided by {@code method}
         * @throws IllegalArgumentException if either parameter is null
         */
        public DefaultInfo(Method method, String propertyName) {
            if (method == null)
                throw new IllegalArgumentException("null method");
            if (propertyName == null)
                throw new IllegalArgumentException("null propertyName");
            this.method = method;
            this.propertyName = propertyName;
        }

        /**
         * Get the annotated method that provides the alternate default value.
         *
         * @return default providing method
         */
        public Method getMethod() {
            return this.method;
        }

        /**
         * Get the name of the field property provided.
         *
         * @return provided property name
         */
        public String getPropertyName() {
            return this.propertyName;
        }

        /**
         * Apply the alternate default value to the configured property in the given field, if possible.
         *
         * @param field field to update
         * @return true if successful, false if no such setter method exists, value is incompatible, etc.
         * @throws IllegalArgumentException if {@code field} is null
         */
        public boolean applyTo(HasValue<?, ?> field) {

            // Sanity check
            if (field == null)
                throw new IllegalArgumentException("null field");
            if (this.propertyName.isEmpty())
                return false;

            // Invoke annotated method
            final Object defaultValue;
            try {
                method.setAccessible(true);
                defaultValue = method.invoke(null);
            } catch (ReflectiveOperationException e) {
                return false;
            }
            if (defaultValue == null)
                return false;

            // Find best candidate setter method in field
            final String methodName = "set" + this.propertyName.substring(0, 1).toUpperCase() + this.propertyName.substring(1);
            final Method setter = Stream.of(field.getClass().getMethods())
              .filter(method -> method.getName().equals(methodName))                    // name must match
              .filter(method -> (method.getModifiers() & Modifier.STATIC) == 0)         // must not be static
              .filter(method -> method.getParameterTypes().length == 1)                 // must take exactly one parameter
              .filter(method -> Primitive.wrap(method.getParameterTypes()[0]).isInstance(defaultValue))    // param is compatible
              .reduce(BinaryOperator.minBy(Comparator.comparing(
                method -> Primitive.unwrap(method.getParameterTypes()[0]),
                ReflectUtil.getClassComparator())))                                     // prefer method w/ narrowest parameter type
              .orElse(null);
            if (setter == null)
                return false;

            // Invoke field setter method to set default value
            try {
                setter.invoke(field, defaultValue);
            } catch (ReflectiveOperationException e) {
                return false;
            }

            // Done
            return true;
        }

        @Override
        public String toString() {
            return String.format("%s[property=\"%s\",method=%s]",
              this.getClass().getSimpleName(), this.getPropertyName(), this.getMethod());
        }
    }

// AnnotationApplier

    /**
     * Class that knows how to apply annotation properties to a newly constructed field.
     */
    private class AnnotationApplier<A extends Annotation> {

        protected final BindingInfo bindingInfo;
        protected final A defaults;
        protected final Method stylePropertiesMethod;

        AnnotationApplier(BindingInfo bindingInfo, A defaults) {
            if (bindingInfo == null)
                throw new IllegalArgumentException("null bindingInfo");
            if (defaults == null)
                throw new IllegalArgumentException("null defaults");
            this.bindingInfo = bindingInfo;
            this.defaults = defaults;

            // Identify styleProperties() annotation property method, if any
            Method method = null;
            try {
                method = this.bindingInfo.getAnnotation().getClass().getMethod("styleProperties");
            } catch (NoSuchMethodException e) {
                // ignore
            }
            this.stylePropertiesMethod = method;
        }

        public HasValue<?, ?> createField() {
            return AbstractFieldBuilder.this.instantiate(HasValue.class,
              this.bindingInfo, this.bindingInfo.getAnnotation(), AbstractFieldBuilder.this.getImplementationPropertyName());
        }

        @SuppressWarnings("unchecked")
        public void configureField(HasValue<?, ?> field) {

            // Apply non-default annotation values
            AnnotationUtil.applyAnnotationValues(field, "set", this.bindingInfo.getAnnotation(), this.defaults,
              (methodList, propertyName) -> propertyName, this::instantiate);
            AnnotationUtil.applyAnnotationValues(field, "add", this.bindingInfo.getAnnotation(), this.defaults,
              (methodList, propertyName) -> methodList.get(0).getName(), this::instantiate);

            // Apply any custom logic
            this.configureFieldCustom(field);
        }

        protected void configureFieldCustom(HasValue<?, ?> field) {

            // Handle styleProperties()
            if (this.stylePropertiesMethod != null && field instanceof HasStyle) {

                // Get name, value pairs
                String[] styleProperties = null;
                try {
                    styleProperties = (String[])this.stylePropertiesMethod.invoke(this.bindingInfo.getAnnotation());
                } catch (ReflectiveOperationException e) {
                    // ignore
                }

                // Apply them if found
                if (styleProperties != null) {
                    final Style style = ((HasStyle)field).getStyle();
                    int i = 0;
                    while (i < styleProperties.length - 1)
                        style.set(styleProperties[i++], styleProperties[i++]);
                }
            }
        }

        public <T> T instantiate(Class<T> type) {
            return AbstractFieldBuilder.this.instantiate(type, this.bindingInfo);
        }
    }

// FieldBuilderContextImpl

    /**
     * Straightforward implementation of the {@link FieldBuilderContext} interface.
     */
    protected class FieldBuilderContextImpl implements FieldBuilderContext {

        private static final long serialVersionUID = -4636811655407064538L;

        protected final BindingInfo bindingInfo;

        public FieldBuilderContextImpl(BindingInfo bindingInfo) {
            if (bindingInfo == null)
                throw new IllegalArgumentException("null bindingInfo");
            this.bindingInfo = bindingInfo;
        }

        @Override
        public BindingInfo getBindingInfo() {
            return this.bindingInfo;
        }

        @Override
        public Class<?> getBeanType() {
            return AbstractFieldBuilder.this.type;
        }

        @Override
        public String toString() {
            return String.format("%s[info=%s,beanType=%s]",
              this.getClass().getSimpleName(), this.getBindingInfo(), this.getBeanType());
        }
    }

// Serialization

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        this.scanForAnnotations();
    }

// Annotations

    /**
     * Specifies that the annotated method will create a field suitable for editing the specified property.
     *
     * <p>
     * Annotated methods must either take no parameters, or one {@link FieldBuilderContext} parameter,
     * and have a return type that is (a) a sub-type of both {@link HasValue} and {@link Component},
     * or (b) a sub-type of {@link FieldComponent}.
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
     * Annotates methods returning alternate default values for some property of the various fields constructed
     * by a {@link FieldBuilder} from {@link FieldBuilder &#64;FieldBuilder.Foo} declarative annotations.
     *
     * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
     * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
     * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
     *
     * <p>
     * A {@link AbstractFieldBuilder.FieldDefault &#64;FieldBuilder.FieldDefault} annotation annotates a static method in an
     * edited model class that returns an alternate default value for some field configuration property (for example,
     * {@code "itemLabelGenerator"}. The property is specified by name and works for all field types having a corresponding
     * setter method with parameter type compatible with the annotated method's return value.
     *
     * <p>
     * For example:
     *
     * <pre><code class="language-java">
     * // This is one of our data model classes
     * public class <b>Person</b> {
     *     public String getFirstName() { ... }
     *     public String getLastName() { ... }
     *
     *     <b>&#64;FieldBuilder.FieldDefault("itemLabelGenerator")</b>
     *     private static ItemLabelGenerator&lt;Person&gt; myCustomLabel() {
     *         return person -&gt; person.getLastName() + ", " + person.getFirstName();
     *     }
     * }
     *
     * // This is a class we want to edit using {@link FieldBuilder}-generated fields
     * public class Vehicle {
     *
     *     &#64;FieldBuilder.ComboBox          // Does not use any special ItemLabelGenerator
     *     public Model getModel() { ... }
     *
     *     &#64;FieldBuilder.ComboBox(         // Uses an instance of AnotherClass as ItemLabelGenerator
     *       itemLabelGenerator = AnotherClass.class)
     *     public Person getOwner() { ... }
     *
     *     <b>&#64;FieldBuilder.ComboBox</b>          // Uses ItemLabelGenerator from Person.myCustomLabel()
     *     public <b>Person</b> getOwner() { ... }
     *
     *     <b>&#64;FieldBuilder.CheckboxGroup</b>     // Uses ItemLabelGenerator from Person.myCustomLabel()
     *     public <b>Person</b> getPassengers() { ... }
     * }
     * </code></pre>
     *
     * <p><b>Details</b>
     *
     * <p>
     * The annotated method must be static, take zero parameters, and have a return type compatible with the named field property,
     * or else the annotation is ignored. Public, package-private, protected, and private methods are supported.
     *
     * <p>
     * These method may be declared in the edited model class, or any superclass thereof. If the same property is named
     * by multiple methods, the method declared in the narrower declaring class wins. If neither declaring class is narrower,
     * an exception is thrown.
     *
     * <p>
     * These annotations only affect fields created from {@link FieldBuilder &#64;FieldBuilder.Foo} declarative annotations;
     * fields returned by {@link ProvidesField &#64;FieldBuilder.ProvidesField} methods are not affected.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface FieldDefault {

        /**
         * The name of the field property that the annotated method will provide.
         *
         * @return field property name
         */
        String value();
    }

    /**
     * Configures how {@link AbstractFieldBuilder FieldBuilder.addFieldComponents()}
     * adds a generated field to a {@link com.vaadin.flow.component.formlayout.FormLayout}.
     *
     * @see AbstractFieldBuilder#addFieldComponents FieldBuilder.addFieldComponents()
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

    /**
     * Causes the field that would otherwise be used for a property to be wrapped in a {@link NullableField},
     * which adds a {@link Checkbox} that controls whether the value is null or not.
     *
     * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
     * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
     * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
     *
     * <p>
     * For example:
     *
     * <pre><code class="language-java">
     * // This is one of our data model classes
     * public class FoodItem {
     *
     *     // This field is mandatory
     *     &#64;FieldBuilder.TextField
     *     &#64;NotNull
     *     public String getName() { ... }
     *
     *     // This field is optional
     *     <b>&#64;FieldBuilder.NullifyCheckbox("This item expires on:")</b>
     *     &#64;FieldBuilder.DatePicker
     *     public LocalDate getExpirationDate() { ... }
     * }
     * </code></pre>
     *
     * @see NullableField
     * @see EnabledBy &#64;FieldBuilder.EnabledBy
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface NullifyCheckbox {

        /**
         * The label for the auto-generated {@link Checkbox}.
         *
         * @return checkbox label
         */
        String value();

        /**
         * Whether the value of the inner field should be automatically reset to its {@linkplain HasValue#getEmptyValue empty value}
         * when the {@link Checkbox} is unchecked.
         *
         * @return whether to reset when disabled
         * @see NullableField#isResetOnDisable
         */
        boolean resetOnDisable() default true;
    }

    /**
     * Causes the generated field to be automatically enabled or disabled based on the value of some other field.
     *
     * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
     * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
     * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
     *
     * <p>
     * Typically the other field is a {@link Checkbox}, but any type of field is supported.
     * The target field will be enabled whenever the other field's value is not equal to its
     * {@linkplain HasValue#getEmptyValue empty value}.
     *
     * <p>
     * When the generated field is disabled, by default it is automatically reset to its
     * {@linkplain HasValue#getEmptyValue empty value}. To have it retain its previous value instead,
     * so the value reappears if the checkbox is unchecked, set {@link #resetOnDisable} to false.
     *
     * <p>
     * If the named property does not exist in the {@link Binder}, or the target field's {@link Component}
     * doesn't implement {@link HasEnabled}, then this annotation has no effect.
     *
     * <p>
     * Example:
     *
     * <pre><code class="language-java">
     * public class GroceryItem {
     *
     *     // Is this item perishable?
     *     &#64;FieldBuilder.Checkbox(label = "Perishable food item")
     *     public boolean isPerishable() { ... }
     *
     *     // This field is only used for perishable items
     *     &#64;FieldBuilder.DatePicker
     *     <b>&#64;FieldBuilder.EnabledBy("perishable")</b>
     *     &#64;FieldBuilder.FormLayout("Expiration Date:")
     *     public LocalDate getExpirationDate() { ... }
     * }
     * </code></pre>
     *
     * @see NullifyCheckbox &#64;FieldBuilder.NullifyCheckbox
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface EnabledBy {

        /**
         * The name of another property in the form that should control enablement.
         *
         * @return controlling property name
         */
        String value();

        /**
         * Whether the value of this field should be automatically reset to its {@linkplain HasValue#getEmptyValue empty value}
         * when this field is disabled by its controlling field.
         *
         * @return whether to reset when disabled
         */
        boolean resetOnDisable() default true;
    }
}
