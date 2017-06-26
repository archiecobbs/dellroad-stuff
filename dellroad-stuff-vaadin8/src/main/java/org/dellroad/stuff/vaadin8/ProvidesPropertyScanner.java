
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;
import com.vaadin.data.ValueProvider;
import com.vaadin.server.Setter;

import java.lang.reflect.Method;
import java.util.Comparator;
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

    private final SimplePropertySet<T> propertySet = new SimplePropertySet<T>();

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
        final Comparator<Method> methodComparator = Comparator.comparing(Method::getDeclaringClass,
          AnnotationUtil.getClassComparator(false));
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
            switch (methodComparator.compare(previousInfo.getMethod(), methodInfo.getMethod())) {
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

        // Build PropertyDefinition list
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
            this.addPropertyDefinition(propertyType, name, caption, methodInfo);
        }
    }

    /**
     * Get the {@link PropertySet} generated from the annotated methods.
     *
     * @return property set
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
    private <V> void addPropertyDefinition(Class<V> type, String name, String caption,
      MethodAnnotationScanner<T, ProvidesProperty>.MethodInfo methodInfo) {
        this.propertySet.add(type, name, caption, methodInfo.getMethod(), methodInfo.getSetter());
    }
}