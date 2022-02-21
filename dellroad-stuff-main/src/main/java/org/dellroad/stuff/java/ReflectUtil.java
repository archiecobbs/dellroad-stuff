
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reflection utility methods.
 */
public final class ReflectUtil {

    private ReflectUtil() {
    }

    /**
     * Find all setter methods in the given class whose name starts with the given prefix.
     *
     * <p>
     * A setter method is any public method taking exactly one parameter. Multiple setter methods
     * could have the same name, if they have a different parameter type and/or return type, though
     * method will filter out {@linkplain Method#isBridge bridge methods}.
     *
     * @param type type to introspect
     * @param methodPrefix setter method name prefix (typically {@code "set"})
     * @return mapping from property name to setter methods, with those having narrower parameter types first
     * @throws IllegalArgumentException if either parameter is null
     */
    public static Map<String, List<Method>> findPropertySetters(Class<?> type, String methodPrefix) {

        // Sanity check
        if (type == null)
            throw new IllegalArgumentException("null type");
        if (methodPrefix == null)
            throw new IllegalArgumentException("null methodPrefix");

        // Get all methods
        final List<Method> methods = Arrays.asList(type.getMethods());

        // Identify setters
        final HashMap<String, List<Method>> setterMap = new HashMap<>();
        for (Method method : methods) {

            // Set if method is a (non-bridge) setter method with matching name prefix
//            if (method.isBridge())
//                continue;
            if (!method.getName().startsWith(methodPrefix) || method.getName().length() <= methodPrefix.length())
                continue;
            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1)
                continue;
            final String propertyName = Introspector.decapitalize(method.getName().substring(methodPrefix.length()));

            // Found one
            setterMap.computeIfAbsent(propertyName, n -> new ArrayList<>(3)).add(method);
        }

        // Sort method lists so narrower parameter types are first
        setterMap.values().forEach(list -> ReflectUtil.sortByType(list, method -> method.getParameterTypes()[0]));

        // Done
        return setterMap;
    }

    /**
     * Instantiate the given class using its zero-arg constructor.
     *
     * @param type type to instantiate
     * @param <T> new object type
     * @return new instance
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
     * @param constructor constructor to invoke
     * @param params constructor parameters
     * @param <T> new object type
     * @return method's return value
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
     * @return method's return value
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
     * Sort the given list so narrower types always appear before wider types.
     *
     * <p>
     * This is a stable sort. Algorithm is O(n<super>2</super>) however.
     *
     * @param list list of types
     */
    public static void sortByType(List<? extends Class<?>> list) {
        ReflectUtil.sortByType(list, Function.identity());
    }

    /**
     * Sort the given list so items with narrower types always appear before items with wider types.
     *
     * <p>
     * This is a stable sort. Algorithm is O(n<super>2</super>) however.
     *
     * @param list list of items
     * @param typer associates a type with each item
     */
    public static <T> void sortByType(List<T> list, Function<? super T, ? extends Class<?>> typer) {

        // Sanity check
        if (list == null)
            throw new IllegalArgumentException("null list");
        if (typer == null)
            throw new IllegalArgumentException("null typer");

        // Get comparator
        final Comparator<T> comparator = Comparator.comparing(typer, ReflectUtil.getClassComparator());

        // Do swap sort
        for (int targetIndex = 0; targetIndex < list.size() - 1; targetIndex++) {
            final T targetItem = list.get(targetIndex);
            int bestIndex = targetIndex;
            T bestItem = targetItem;
            for (int nextIndex = targetIndex + 1; nextIndex < list.size(); nextIndex++) {
                final T nextItem = list.get(nextIndex);
                if (comparator.compare(bestItem, nextItem) > 0) {
                    bestItem = nextItem;
                    bestIndex = nextIndex;
                }
            }
            list.set(targetIndex, bestItem);
            list.set(bestIndex, targetItem);
        }
    }

    /**
     * Get a comparator that partially orders types where narrower types sort first.
     *
     * <p>
     * Non-comparable types compare as equal, so they will not reorder under a stable sort.
     *
     * @return stable ordering of types narrowest first
     */
    public static Comparator<Class<?>> getClassComparator() {
        return ReflectUtil.getClassComparator(true);
    }

    /**
     * Get a comparator that calculates which type is narrower, if possible.
     *
     * <p>
     * Non-comparable types will compare as equal if {@code incomparableEqual} is true,
     * or else generate an {@link IllegalArgumentException}.
     *
     * <p>
     * <b>Warning:</b> use this comparator for simple comparisons only; it will not work
     * for sorting because it is not transitive for "equality"; use {@link #sortByType(List) sortByType()} instead.
     *
     * @param incomparableEqual true to return zero for incomparable classes, false to throw {@link IllegalArgumentException}
     * @return ordering of types narrowest first
     */
    public static Comparator<Class<?>> getClassComparator(boolean incomparableEqual) {
        return (type1, type2) -> {
            if (type1.isAssignableFrom(type2) && !type2.isAssignableFrom(type1))
                return 1;
            if (!type1.isAssignableFrom(type2) && type2.isAssignableFrom(type1))
                return -1;
            if (incomparableEqual)
                return 0;
            throw new IllegalArgumentException(type1 + " and " + type2 + " are incomparable");
        };
    }
}
