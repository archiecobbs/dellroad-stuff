
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection utility methods.
 */
public final class ReflectUtil {

    private ReflectUtil() {
    }

    /**
     * Find all setter methods taking the given type as parameter and whose name starts with the given prefix.
     *
     * <p>
     * Methods from narrower types take precedence.
     *
     * @param cl target class
     * @param methodPrefix setter method name prefix (typically {@code "set"})
     * @return mapping from property name to setter method
     * @throws IllegalArgumentException if either parameter is null
     */
    public static Map<String, Method> findPropertySetters(Class<?> type, String methodPrefix) {

        // Sanity check
        if (type == null)
            throw new IllegalArgumentException("null type");
        if (methodPrefix == null)
            throw new IllegalArgumentException("null methodPrefix");

        // Get all methods
        final List<Method> methods = Arrays.asList(type.getMethods());

        // Sort the methods from narrower types (i.e., overriding methods) last so they override supertypes' methods
        Collections.sort(methods, Comparator.comparing(Method::getDeclaringClass, ReflectUtil.getClassComparator().reversed()));

        // Identify setters
        final HashMap<String, Method> setterMap = new HashMap<>();
        for (Method method : methods) {

            // Set if method is a setter method
            if (!method.getName().startsWith(methodPrefix) || method.getName().length() <= methodPrefix.length())
                continue;
            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1)
                continue;
            final String propertyName = Introspector.decapitalize(method.getName().substring(methodPrefix.length()));

            // Found one (if key already exists, this is an overriding method in a sub-type)
            setterMap.put(propertyName, method);
        }

        // Done
        return setterMap;
    }

    /**
     * Instantiate the given class using its zero-arg constructor.
     *
     * @param type type to instantiate
     * @throws RuntimeException if construction fails
     * @throws IllegalArgumentException if {@code type} is null
     */
    public static <T> T instantiate(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        Constructor<T> constructor;
        try {
            constructor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("cannot instantiate " + type + " because no zero-arg constructor could be found", e);
        }
        return ReflectUtil.instantiate(constructor);
    }

    /**
     * Instantiate the given class using the given constructor.
     *
     * @param type constructor to invoke
     * @param params constructor parameters
     * @throws RuntimeException if construction fails
     * @throws IllegalArgumentException if either parameter is null
     */
    public static <T> T instantiate(Constructor<T> constructor, Object... params) {
        if (constructor == null)
            throw new IllegalArgumentException("null constructor");
        if (params == null)
            throw new IllegalArgumentException("null params");
        try {
            constructor.setAccessible(true);
        } catch (Exception e) {
            // ignore
        }
        try {
            return constructor.newInstance(params);
        } catch (Exception e) {
            throw new RuntimeException("cannot instantiate "
              + constructor.getDeclaringClass() + " using constructor " +  constructor, e);
        }
    }

    /**
     * Invoke the given method, rethrowing any checked exceptions.
     *
     * @param method method to invoke
     * @param target target object, or null if method is static
     * @param params constructor parameters
     * @throws RuntimeException if invocation fails
     * @throws IllegalArgumentException if {@code method} or {@code params} is null
     */
    public static Object invoke(Method method, Object target, Object... params) {
        if (method == null)
            throw new IllegalArgumentException("null method");
        if (params == null)
            throw new IllegalArgumentException("null params");
        try {
            return method.invoke(target, params);
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

    /**
     * Get a comparator that partially orders types where narrower types sort first.
     *
     * <p>
     * Non-comparable types compare as equal, so they will not reorder under a stable sort.
     */
    public static Comparator<Class<?>> getClassComparator() {
        return ReflectUtil.getClassComparator(true);
    }

    /**
     * Get a comparator that partially orders types where narrower types sort first.
     *
     * <p>
     * Non-comparable types either compare as equal (so they will not reorder under a stable sort)
     * if {@code incomparableEqual} is true, or else generate an {@link IllegalArgumentException}.
     *
     * @param incomparableEqual true to return zero for incomparable classes, false to throw {@link IllegalArgumentException}
     */
    public static Comparator<Class<?>> getClassComparator(boolean incomparableEqual) {
        return (type1, type2) -> {
            if (type1 == type2)
                return 0;
            if (type1.isAssignableFrom(type2))
                return 1;
            if (type2.isAssignableFrom(type1))
                return -1;
            if (incomparableEqual)
                return 0;
            throw new IllegalArgumentException(type1 + " and " + type2 + " are incomparable");
        };
    }
}
