
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

final class AnnotationUtil {

    private AnnotationUtil() {
    }

    /**
     * Introspect the specified method and read the specified annotation, which must exist.
     */
    static <A extends Annotation> A getAnnotation(Class<A> annotationType,
      Class<?> methodClass, String methodName, Class<?>... methodParameterTypes) {
        final A annotation;
        try {
            annotation = methodClass.getDeclaredMethod(methodName, methodParameterTypes).getDeclaredAnnotation(annotationType);
        } catch (Exception e) {
            throw new RuntimeException("unexpected exception", e);
        }
        if (annotation == null)
            throw new RuntimeException("internal error: didn't find " + annotationType + " on " + methodName + "()");
        return annotation;
    }

    /**
     * Determine the original annotation type that the given annotation (which is likely a proxy object) implements.
     *
     * @param annotation the annotation
     * @param filter additional predicat the annotation type must match
     */
    @SuppressWarnings("unchecked")
    static <A extends Annotation> Class<A> getAnnotationType(A annotation, Predicate<? super Class<? extends Annotation>> filter) {
        for (Class<?> iface : annotation.getClass().getInterfaces()) {
            if (iface.isAnnotation() && filter.test((Class<? extends Annotation>)iface))
                return (Class<A>)iface;
        }
        return null;
    }

    /**
     * Read annotation values and apply non-default values to the corresponding properties of the given bean.
     *
     * <p>
     * Properties that are {@link Class} instances result in instatiating the given class.
     *
     * @param bean target bean
     * @param annotation annotation with properties
     * @param defaults annotation with defaults
     * @param filter filter taking method and property name that returns true to proceed, false to skip
     */
    static <A extends Annotation> void apply(Object bean, A annotation, A defaults, BiPredicate<Method, String> filter) {
        for (Method beanSetter : bean.getClass().getMethods()) {

            // Set if method is a setter method
            if (!beanSetter.getName().startsWith("set") || beanSetter.getName().length() < 4)
                continue;
            final Class<?>[] parameterTypes = beanSetter.getParameterTypes();
            if (parameterTypes.length != 1)
                continue;
            final String propertyName = Introspector.decapitalize(beanSetter.getName().substring(3));

            // Check filter
            if (!filter.test(beanSetter, propertyName))
                continue;

            // Find corresponding annotation property, if any
            final Method annotationGetter;
            try {
                annotationGetter = annotation.getClass().getMethod(propertyName);
            } catch (NoSuchMethodException e) {
                continue;
            }

            // Get value from annotation, and annotation's default value
            try {
                Object value = annotationGetter.invoke(annotation);
                final Object defaultValue = annotationGetter.invoke(defaults);

                // If annotation value is same as default, don't do anything
                if (Objects.equals(value, defaultValue))
                    continue;

                // Special case for Class<?> values: instantiate the class
                if (value instanceof Class)
                    value = AnnotationUtil.instantiate((Class<?>)value);

                // Copy over the value
                beanSetter.invoke(bean, value);
            } catch (Exception e) {
                throw new RuntimeException("unexpected exception", e);
            }
        }
    }

    /**
     * Instantiate the given class using its zero-arg constructor.
     */
    static <T> T instantiate(Class<T> type) {
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
     * Invoke the given method, rethrowing any checked exceptions.
     */
    static Object invoke(Method method, Object target, Object... parameters) {
        try {
            return method.invoke(target, parameters);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException)e.getCause();
            if (e.getCause() instanceof Error)
                throw (Error)e.getCause();
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
