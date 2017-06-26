
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;
import com.vaadin.data.ValueProvider;
import com.vaadin.server.Setter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.Primitive;

/**
 * Scans a Java class hierarchy for {@link ProvidesProperty &#64;ProvidesProperty} annotated getter methods and creates
 * a corresponding {@link PropertySet} containing read-only {@link PropertyDefinition}s that extract property values from
 * instances of the given class via the annotated methods.
 *
 * @param <T> Java class to be introspected
 * @see ProvidesProperty &#64;ProvidesProperty
 */
public class ProvidesPropertyScanner<T> {

    private final AnnotationPropertySet<T> propertySet = new AnnotationPropertySet<T>();

    /**
     * Constructor.
     *
     * @param type Java class to be introspected
     * @throws IllegalArgumentException if {@code type} is null
     * @throws IllegalArgumentException if an annotated method with no {@linkplain ProvidesProperty#value property name specified}
     *  has a name which cannot be interpreted as a bean property "getter" method
     * @throws IllegalArgumentException if {@code type} has two {@link ProvidesProperty &#64;ProvidesProperty}-annotated
     *  fields or methods with the same {@linkplain ProvidesProperty#value property name}
     */
    public ProvidesPropertyScanner(Class<T> type) {

        // Sanity check
        if (type == null)
            throw new IllegalArgumentException("null type");

        // Scan for @ProvidesProperty annotations
        final Set<MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo> providesPropertyMethods
          = new MethodAnnotationScanner<T, ProvidesProperty>(type, ProvidesProperty.class).findAnnotatedMethods();

        // Check for duplicate @ProvidesProperty names
        final HashMap<String, MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo> providesPropertyNameMap = new HashMap<>();
        for (MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo : providesPropertyMethods) {
            final String propertyName = this.getPropertyName(methodInfo);

            // Check for name conflict
            final MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo previousInfo = providesPropertyNameMap.get(propertyName);
            if (previousInfo == null) {
                providesPropertyNameMap.put(propertyName, methodInfo);
                continue;
            }

            // If there is a name conflict, the sub-type method declaration wins
            switch (this.compareDeclaringClass(previousInfo.getMethod(), methodInfo.getMethod())) {
            case 0:
                throw new IllegalArgumentException("duplicate @" + ProvidesProperty.class.getSimpleName()
                  + " declaration for property `" + propertyName + "' on method " + previousInfo.getMethod()
                  + " and " + methodInfo.getMethod() + " declared in the same class");
            case 1:
                providesPropertyNameMap.put(propertyName, methodInfo);
                break;
            default:
                break;
            }
        }

        // Build AnnotationPropertyDef list
        for (Map.Entry<String, MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo> e : providesPropertyNameMap.entrySet()) {
            final String name = e.getKey();
            final MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo = e.getValue();

            // Get property caption
            final String caption = this.getPropertyCaption(methodInfo);

            // Get property type
            Class<?> propertyType = methodInfo.getMethod().getReturnType();

            // Wrap primitive types
            if (propertyType.isPrimitive())
                propertyType = Primitive.get(propertyType).getWrapperType();

            // Add property definition
            this.propertySet.add(this.createAnnotationPropertyDef(this.propertySet, name, caption, propertyType, methodInfo));
        }
    }

    /**
     * Get the {@link PropertySet} generated from the annotated methods.
     *
     * @return propertie set
     */
    public PropertySet<T> getPropertySet() {
        return this.propertySet;
    }

    /**
     * Get the property name.
     *
     * @param methodInfo method info
     */
    protected String getPropertyName(MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo) {
        return methodInfo.getAnnotation().value().length() > 0 ?
          methodInfo.getAnnotation().value() : methodInfo.getMethodPropertyName();
    }

    /**
     * Get the property caption.
     *
     * @param methodInfo method info
     */
    protected String getPropertyCaption(MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo) {
        return methodInfo.getAnnotation().caption().length() > 0 ?
          methodInfo.getAnnotation().caption() : this.getPropertyName(methodInfo);
    }

    // This method exists solely to bind the generic type
    private <V> AnnotationPropertyDef<V> createAnnotationPropertyDef(AnnotationPropertySet<T> propertySet, String name,
      String caption, Class<V> type, MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo) {
        return new AnnotationPropertyDef<V>(propertySet, name, caption, type, methodInfo);
    }

    // Compare two methods to determine which one has the declaring class that is a sub-type of the other's
    private int compareDeclaringClass(Method method1, Method method2) {
        final Class<?> class1 = method1.getDeclaringClass();
        final Class<?> class2 = method2.getDeclaringClass();
        if (class1 == class2)
            return 0;
        if (class1.isAssignableFrom(class2))
            return 1;
        if (class2.isAssignableFrom(class1))
            return -1;
        throw new RuntimeException("internal error: incomparable classes " + class1.getName() + " and " + class2.getName());
    }

// AnnotationPropertySet

    private static class AnnotationPropertySet<T> implements PropertySet<T> {

        private static final long serialVersionUID = 4983663265225248973L;

        private final HashMap<String, PropertyDefinition<T, ?>> defs = new HashMap<>();

        boolean add(PropertyDefinition<T, ?> def) {
            return this.defs.put(def.getName(), def) == null;
        }

        @Override
        public Stream<PropertyDefinition<T, ?>> getProperties() {
            return this.defs.values().stream();
        }

        @Override
        public Optional<PropertyDefinition<T, ?>> getProperty(String name) {
            final PropertyDefinition<T, ?> def = this.defs.get(name);
            return def != null ? Optional.of(def) : Optional.empty();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final AnnotationPropertySet<?> that = (AnnotationPropertySet<?>)obj;
            return this.defs.equals(that.defs);
        }

        @Override
        public int hashCode() {
            return this.defs.hashCode();
        }
    }

// AnnotationPropertyDef

    private class AnnotationPropertyDef<V> implements PropertyDefinition<T, V> {

        private static final long serialVersionUID = 4983663265225248972L;

        private final AnnotationPropertySet<T> propertySet;
        private final String name;
        private final String caption;
        private final Class<V> type;
        private final MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo;
        private final Method setter;

        AnnotationPropertyDef(AnnotationPropertySet<T> propertySet, String name, String caption, Class<V> type,
          MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo) {
            this.propertySet = propertySet;
            this.caption = caption;
            this.name = name;
            this.type = type;
            this.methodInfo = methodInfo;

            // Find corresponding setter, if any
            final Method getter = methodInfo.getMethod();
            final String getterName = getter.getName();
            Method setter0 = null;
            if (getterName.matches("^(is|get).+$")) {
                final String setterName = "set" + getterName.substring(getterName.startsWith("is") ? 2 : 3);
                try {
                    setter0 = getter.getDeclaringClass().getMethod(setterName, getter.getReturnType());
                } catch (Exception e) {
                    // ignore
                }
            }
            this.setter = setter0;
        }

        @Override
        public String getCaption() {
            return this.caption;
        }

        @Override
        public ValueProvider<T, V> getGetter() {
            return obj -> this.type.cast(this.methodInfo.invoke(obj));
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public PropertySet<T> getPropertySet() {
            return this.propertySet;
        }

        @Override
        public Optional<Setter<T, V>> getSetter() {
            return this.setter != null ?
              Optional.of((obj, value) -> AnnotationUtil.invoke(this.setter, obj, value)) : Optional.empty();
        }

        @Override
        public Class<V> getType() {
            return this.type;
        }

        public MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo getMethodInfo() {
            return this.methodInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final ProvidesPropertyScanner<?>.AnnotationPropertyDef<?> that
              = (ProvidesPropertyScanner<?>.AnnotationPropertyDef<?>)obj;
            return Objects.equals(this.name, that.name)
              && Objects.equals(this.caption, that.caption)
              && Objects.equals(this.type, that.type)
              && Objects.equals(this.methodInfo, that.methodInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.name)
              ^ Objects.hashCode(this.caption)
              ^ Objects.hashCode(this.type)
              ^ Objects.hashCode(this.methodInfo);
        }
    }
}

