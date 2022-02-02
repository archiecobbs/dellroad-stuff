
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Annotation utility methods.
 */
public final class AnnotationUtil {

    private AnnotationUtil() {
    }

    /**
     * Introspect the specified method and read all annotations.
     *
     * @param methodClass method's declaring class
     * @param methodName method name
     * @param methodParameterTypes method parameter types
     * @return list of annotations found
     * @throws IllegalArgumentException if method is not found
     * @throws RuntimeException if method cannot be instrospected
     * @throws IllegalArgumentException if any parameter is null
     */
    public static List<Annotation> getAnnotations(Class<?> methodClass, String methodName, Class<?>... methodParameterTypes) {
        if (methodClass == null)
            throw new IllegalArgumentException("null methodClass");
        if (methodName == null)
            throw new IllegalArgumentException("null methodName");
        if (methodParameterTypes == null)
            throw new IllegalArgumentException("null methodParameterTypes");
        final Method method;
        try {
            method = methodClass.getDeclaredMethod(methodName, methodParameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("method " + methodClass.getName() + "." + methodName + "() not found");
        }
        try {
            return Arrays.asList(method.getDeclaredAnnotations());
        } catch (Exception e) {
            throw new RuntimeException("unexpected exception", e);
        }
    }

    /**
     * Introspect the specified method and read the specified annotation, which must exist.
     *
     * @param annotationType annotation to look for
     * @param methodClass method's declaring class
     * @param methodName method name
     * @param methodParameterTypes method parameter types
     * @param <A> annotation type
     * @return the annotation found
     * @throws IllegalArgumentException if method is not found
     * @throws RuntimeException if method cannot be instrospected
     * @throws IllegalArgumentException if any parameter is null
     */
    public static <A extends Annotation> A getAnnotation(Class<A> annotationType,
      Class<?> methodClass, String methodName, Class<?>... methodParameterTypes) {
        if (annotationType == null)
            throw new IllegalArgumentException("null annotationType");
        return AnnotationUtil.getAnnotations(methodClass, methodName, methodParameterTypes).stream()
          .filter(annotationType::isInstance)
          .map(annotationType::cast)
          .findFirst()
          .orElseThrow(() -> new RuntimeException("didn't find " + annotationType + " on " + methodName + "()"));
    }

    /**
     * Read annotation values and apply non-default values to the corresponding properties of the given bean.
     *
     * <p>
     * For example, if the annotation has a {@code foo()} property of type {@code int}, and bean has
     * a method like {@code setFoo(int)}, then the annotation value will be used to set the property
     * using the method.
     *
     * <p>
     * Properties that are {@link Class} instances result in instantiating the given class, unless the property
     * actually has type {@link Class}.
     *
     * <p>
     * Non-primitive array properties are converted into {@link List}s if necessary.
     *
     * @param bean target bean
     * @param methodPrefix setter method name prefix (typically {@code "set"})
     * @param annotation annotation with properties
     * @param defaults annotation with default values; if non-null, values equal to their defaults are skipped
     * @param propertyFinder returns the annotation property name for the setter method, or null if none
     * @param <A> annotation type
     * @throws IllegalArgumentException if a required parameter is null
     * @throws IllegalArgumentException if a bean property is being set more than once
     * @throws RuntimeException if an unexpected error occurs
     */
    public static <A extends Annotation> void applyAnnotationValues(Object bean, String methodPrefix,
      A annotation, A defaults, BiFunction<Method, String, String> propertyFinder) {

        // Sanity check
        if (bean == null)
            throw new IllegalArgumentException("null bean");
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        if (propertyFinder == null)
            throw new IllegalArgumentException("null propertyFinder");

        // Iterate over property setter methods
        ReflectUtil.findPropertySetters(bean.getClass(), methodPrefix).forEach((propertyName, beanSetter) -> {

            // Get annotation property name
            final String annotationPropertyName = propertyFinder.apply(beanSetter, propertyName);
            if (annotationPropertyName == null)
                return;

            // Find corresponding annotation property, if any
            final Method annotationGetter;
            try {
                annotationGetter = annotation.getClass().getMethod(annotationPropertyName);
            } catch (NoSuchMethodException e) {
                return;
            }

            // Apply value if appropriate
            try {

                // Get value from annotation
                Object value = annotationGetter.invoke(annotation);

                // If annotation value is same as default, don't do anything
                if (defaults != null) {
                    final Object defaultValue = annotationGetter.invoke(defaults);
                    if (Objects.deepEquals(value, defaultValue))
                        return;
                }

                // Special case for Class<?> values: instantiate the class
                if (value instanceof Class)
                    value = ReflectUtil.instantiate((Class<?>)value);

                // Get parameter type (wrapper type if primitive)
                Class<?> parameterType = beanSetter.getParameterTypes()[0];
                parameterType = Optional.ofNullable(Primitive.get(parameterType))
                  .<Class<?>>map(Primitive::getWrapperType)
                  .orElse(parameterType);

                // Special case for converting array values into lists (if needed)
                if (value instanceof Object[] && List.class.isAssignableFrom(parameterType))
                    value = Arrays.asList((Object[])value);

                // If value is not compatible with method parameter, then this must be the wrong (overloaded) method
                if (!parameterType.isInstance(value))
                    return;

                // Workaround JDK bug (JDK-8280013)
                if ((beanSetter.getModifiers() & Modifier.PUBLIC) != 0)
                    beanSetter.setAccessible(true);

                // Apply the value
                beanSetter.invoke(bean, value);
            } catch (Exception e) {
                throw new RuntimeException("error applying annotation property \""
                  + annotationPropertyName + "\" to " + beanSetter, e);
            }
        });
    }
}