
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin7;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.Primitive;

/**
 * Scans a Java class hierarchy for {@link ProvidesProperty &#64;ProvidesProperty} and
 * {@link ProvidesPropertySort &#64;ProvidesPropertySort} annotated getter methods and creates
 * a corresponding set of {@link PropertyDef} property definitions and a {@link SortingPropertyExtractor}
 * that will extract the properties from instances of the given class and sort them accordingly.
 *
 * @param <T> Java class to be introspected
 * @see ProvidesProperty &#64;ProvidesProperty
 * @see ProvidesPropertySort &#64;ProvidesPropertySort
 */
public class ProvidesPropertyScanner<T> {

    private final ArrayList<PropertyDef<?>> propertyList = new ArrayList<>();
    private final SortingPropertyExtractor<T> propertyExtractor;

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

        // Scan for @ProvidesProperty and @ProvidesPropertySort annotations
        final Set<MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo> providesPropertyMethods
          = new MethodAnnotationScanner<T, ProvidesProperty>(type, ProvidesProperty.class).findAnnotatedMethods();
        final Set<MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo> providesPropertySortMethods
          = new MethodAnnotationScanner<T, ProvidesPropertySort>(type, ProvidesPropertySort.class).findAnnotatedMethods();

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

        // Check for duplicate @ProvidesPropertySort names, etc.
        final HashMap<String, MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo> providesPropertySortNameMap
          = new HashMap<>();
        for (MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo methodInfo : providesPropertySortMethods) {
            final String propertyName = this.getSortPropertyName(methodInfo);

            // Verify the method's return type (or wrapper type if primitive) implements Comparable or Comparator
            Class<?> methodType = methodInfo.getMethod().getReturnType();
            if (methodType.isPrimitive())
                methodType = Primitive.get(methodType).getWrapperType();
            if (!Comparable.class.isAssignableFrom(methodType) && !Comparator.class.isAssignableFrom(methodType)) {
                throw new IllegalArgumentException("invalid @" + ProvidesPropertySort.class.getSimpleName()
                  + " declaration for property `" + propertyName + "': method " + methodInfo.getMethod()
                  + " return type " + methodType.getName() + " implements neither " + Comparable.class.getName()
                  + " nor " + Comparator.class.getName());
            }

            // Check for name conflict
            final MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo previousInfo
              = providesPropertySortNameMap.get(propertyName);
            if (previousInfo == null) {
                providesPropertySortNameMap.put(propertyName, methodInfo);
                continue;
            }

            // If there is a name conflict, the sub-type method declaration wins
            switch (this.compareDeclaringClass(previousInfo.getMethod(), methodInfo.getMethod())) {
            case 0:
                throw new IllegalArgumentException("duplicate @" + ProvidesPropertySort.class.getSimpleName()
                  + " declaration for property `" + propertyName + "' on method " + previousInfo.getMethod()
                  + " and " + methodInfo.getMethod() + " declared in the same class");
            case 1:
                providesPropertySortNameMap.put(propertyName, methodInfo);
                break;
            default:
                break;
            }
        }

        // Build PropertyDef list
        for (Map.Entry<String, MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo> e : providesPropertyNameMap.entrySet()) {
            final String propertyName = e.getKey();
            final MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo = e.getValue();

            // Get property type
            Class<?> propertyType = methodInfo.getMethod().getReturnType();

            // Get property default value
            Object defaultValue = null;
            if (propertyType.isPrimitive()) {
                final Primitive<?> primitiveType = Primitive.get(propertyType);
                defaultValue = primitiveType.getDefaultValue();
                propertyType = primitiveType.getWrapperType();
            }

            // Get sort property (if any)
            final MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo sortMethodInfo
              = providesPropertySortNameMap.get(propertyName);

            // Create property definition
            this.propertyList.add(this.createAnnotationPropertyDef(
              propertyName, propertyType, defaultValue, methodInfo, sortMethodInfo));
        }

        // Build PropertyExtractor
        this.propertyExtractor = new SortingPropertyExtractor<T>() {

            @Override
            @SuppressWarnings("unchecked")
            public <V> V getPropertyValue(T obj, PropertyDef<V> propertyDef) {
                if (!(propertyDef instanceof AnnotationPropertyDef))
                    throw new IllegalArgumentException("unknown property " + propertyDef);
                final AnnotationPropertyDef<V> annotationPropertyDef = (AnnotationPropertyDef<V>)propertyDef;
                return propertyDef.getType().cast(annotationPropertyDef.getMethodInfo().invoke(obj));
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean canSort(PropertyDef<?> propertyDef) {
                if (!(propertyDef instanceof AnnotationPropertyDef))
                    return false;
                final AnnotationPropertyDef<?> annotationPropertyDef = (AnnotationPropertyDef)propertyDef;
                return annotationPropertyDef.getSortMethodInfo() != null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public int sort(PropertyDef<?> propertyDef, T obj1, T obj2) {
                if (!(propertyDef instanceof AnnotationPropertyDef))
                    throw new UnsupportedOperationException("unknown property " + propertyDef);
                final AnnotationPropertyDef<?> annotationPropertyDef = (AnnotationPropertyDef<?>)propertyDef;
                return annotationPropertyDef.getComparator().compare(obj1, obj2);
            }
        };
    }

    /**
     * Get the list of {@link PropertyDef}s generated from the annotated methods.
     *
     * <p>
     * All of the properties in the returned list can be extracted from instances of this reader's configured
     * class by the {@link PropertyExtractor} returned by {@link #getPropertyExtractor}.
     *
     * @return unmodifiable list of properties
     * @see #getPropertyExtractor
     */
    public List<PropertyDef<?>> getPropertyDefs() {
        return Collections.unmodifiableList(this.propertyList);
    }

    /**
     * Get the {@link PropertyExtractor} that extracts Vaadin {@link com.vaadin.data.Property} values
     * from instances of the annotated class when given one of the {@link PropertyDef}s returned by
     * {@link #getPropertyDefs}.
     *
     * @see #getPropertyDefs
     */
    public SortingPropertyExtractor<T> getPropertyExtractor() {
        return this.propertyExtractor;
    }

    private String getPropertyName(MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo) {
        return methodInfo.getAnnotation().value().length() > 0 ?
          methodInfo.getAnnotation().value() : methodInfo.getMethodPropertyName();
    }

    private String getSortPropertyName(MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo methodInfo) {
        return methodInfo.getAnnotation().value().length() > 0 ?
          methodInfo.getAnnotation().value() : methodInfo.getMethodPropertyName();
    }

    // This method exists solely to bind the generic type
    private <V> AnnotationPropertyDef<V> createAnnotationPropertyDef(String propertyName, Class<V> propertyType,
      Object defaultValue, MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo,
      MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo sortMethodInfo) {
        return new AnnotationPropertyDef<V>(propertyName, propertyType,
          propertyType.cast(defaultValue), methodInfo, sortMethodInfo);
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

    private class AnnotationPropertyDef<V> extends PropertyDef<V> {

        private static final long serialVersionUID = 4983663265225248971L;

        private final MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo;
        private final MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo sortMethodInfo;

        private Comparator<T> comparator;

        AnnotationPropertyDef(String name, Class<V> type, V defaultValue,
          MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo,
          MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo sortMethodInfo) {
            super(name, type, defaultValue);
            this.methodInfo = methodInfo;
            this.sortMethodInfo = sortMethodInfo;
        }

        public MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo getMethodInfo() {
            return this.methodInfo;
        }

        public MethodAnnotationScanner<T, ProvidesPropertySort>.MethodInfo getSortMethodInfo() {
            return this.sortMethodInfo;
        }

        public Comparator<T> getComparator() {

            // Sanity check
            if (this.sortMethodInfo == null)
                throw new UnsupportedOperationException("can't sort property " + this);

            // Already created?
            if (this.comparator != null)
                return this.comparator;

            // Handle Comparator case
            if (Comparator.class.isAssignableFrom(this.sortMethodInfo.getMethod().getReturnType())) {
                this.comparator = new Comparator<T>() {

                    private Comparator<T> comparator;

                    @Override
                    @SuppressWarnings("unchecked")
                    public int compare(T obj1, T obj2) {
                        if (this.comparator == null)
                            this.comparator = (Comparator<T>)AnnotationPropertyDef.this.sortMethodInfo.invoke(obj1);
                        return this.comparator.compare(obj1, obj2);
                    }
                };
                return this.comparator;
            }

            // Handle Comparable case
            this.comparator = new Comparator<T>() {
                @Override
                @SuppressWarnings("unchecked")
                public int compare(T obj1, T obj2) {
                    final Comparable<Object> value1 = (Comparable<Object>)AnnotationPropertyDef.this.sortMethodInfo.invoke(obj1);
                    final Comparable<Object> value2 = (Comparable<Object>)AnnotationPropertyDef.this.sortMethodInfo.invoke(obj2);
                    if (value1 == null && value2 != null)
                        return -1;
                    if (value1 != null && value2 == null)
                        return 1;
                    if (value1 == null && value2 == null)
                        return 0;
                    return value1.compareTo(value2);
                }
            };
            return this.comparator;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final ProvidesPropertyScanner<?>.AnnotationPropertyDef<?> that
              = (ProvidesPropertyScanner<?>.AnnotationPropertyDef<?>)obj;
            return Objects.equals(this.methodInfo, that.methodInfo)
              && Objects.equals(this.sortMethodInfo, that.sortMethodInfo);
        }

        @Override
        public int hashCode() {
            return super.hashCode()
              ^ (this.methodInfo != null ? this.methodInfo.hashCode() : 0)
              ^ (this.sortMethodInfo != null ? this.sortMethodInfo.hashCode() : 0);
        }
    }
}

