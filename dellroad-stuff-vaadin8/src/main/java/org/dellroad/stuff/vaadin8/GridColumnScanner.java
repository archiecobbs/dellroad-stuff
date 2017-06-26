
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.ui.Grid;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.dellroad.stuff.java.MethodAnnotationScanner;

/**
 * Scans a Java class hierarchy for {@link GridColumn &#64;GridColumn} annotated getter methods, and auto-generates and
 * configures corresponding {@link Grid.Column}s on a {@link Grid} instance.
 *
 * <p>
 * See {@link GridColumn} for an example of usage.
 *
 * @param <T> Java class to be introspected
 * @see GridColumn &#64;GridColumn
 */
public class GridColumnScanner<T> {

    private final Class<T> type;
    private final HashMap<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> columnMap = new HashMap<>();

    /**
     * Constructor.
     *
     * @param type Java class to be introspected
     * @throws IllegalArgumentException if {@code type} is null
     * @throws IllegalArgumentException if an annotated method with no {@linkplain GridColumn#value property name specified}
     *  has a name which cannot be interpreted as a bean property "getter" method
     * @throws IllegalArgumentException if {@code type} has two {@link GridColumn &#64;GridColumn}-annotated
     *  fields or methods with the same {@linkplain GridColumn#value property name}
     */
    public GridColumnScanner(Class<T> type) {

        // Sanity check
        if (type == null)
            throw new IllegalArgumentException("null type");
        this.type = type;

        // Scan for @GridColumn annotations
        final Set<MethodAnnotationScanner<T, GridColumn>.MethodInfo> gridColumnMethods
          = new MethodAnnotationScanner<T, GridColumn>(this.type, GridColumn.class).findAnnotatedMethods();

        // Check for duplicate @GridColumn names
        final Comparator<Method> methodComparator = Comparator.comparing(Method::getDeclaringClass,
          AnnotationUtil.getClassComparator(false));
        for (MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo : gridColumnMethods) {
            final String propertyName = this.getPropertyName(methodInfo);

            // Check for name conflict
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo previousInfo = this.columnMap.get(propertyName);
            if (previousInfo == null) {
                this.columnMap.put(propertyName, methodInfo);
                continue;
            }

            // If there is a name conflict, the sub-type method declaration wins
            switch (methodComparator.compare(previousInfo.getMethod(), methodInfo.getMethod())) {
            case 0:
                throw new IllegalArgumentException("duplicate @" + GridColumn.class.getSimpleName()
                  + " declaration for property `" + propertyName + "' on method " + previousInfo.getMethod()
                  + " and " + methodInfo.getMethod() + " declared in the same class");
            case 1:
                this.columnMap.put(propertyName, methodInfo);
                break;
            default:
                break;
            }
        }
    }

    /**
     * Build a {@link Grid} with columns auto-generated from introspected {@link GridColumn &#64;GridColumn} annotations.
     *
     * @return new {@link Grid}
     */
    public Grid<T> buildGrid() {

        // Build property set
        final SimplePropertySet<T> propertySet = new SimplePropertySet<>();
        for (Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> e : this.columnMap.entrySet()) {
            final String propertyName = e.getKey();
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo = e.getValue();
            final Method getter = methodInfo.getMethod();
            final Method setter = methodInfo.getSetter();
            propertySet.add(getter.getReturnType(), propertyName, methodInfo.getAnnotation().caption(), getter, setter);
        }

        // Create grid
        final Grid<T> grid = Grid.withPropertySet(propertySet);

        // Get default annotation
        final GridColumn defaults = GridColumnScanner.getDefaults();

        // Modify columns
        for (Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> e : this.columnMap.entrySet()) {
            final String propertyName = e.getKey();
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo = e.getValue();

            // Get column
            final Grid.Column<T, ?> column = grid.getColumn(propertyName);

            // Apply annotation values
            AnnotationUtil.apply(column, methodInfo.getAnnotation(), defaults, (method, name) -> true);
        }

        // Done
        return grid;
    }

    @GridColumn
    private static GridColumn getDefaults() {
        return AnnotationUtil.getAnnotation(GridColumn.class, GridColumnScanner.class, "getDefaults");
    }

    /**
     * Get the property name from the annotation.
     *
     * @param methodInfo method info
     * @return property name
     */
    protected String getPropertyName(MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo) {
        return methodInfo.getAnnotation().value().length() > 0 ?
          methodInfo.getAnnotation().value() : methodInfo.getMethodPropertyName();
    }

    /**
     * Get the type associated with this instance.
     *
     * @return backing object type
     */
    public Class<T> getType() {
        return this.type;
    }

    /**
     * Get the annotations found through introspection keyed by property name.
     *
     * @return columns keyed by property name
     */
    public Map<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> getColumnMap() {
        return Collections.unmodifiableMap(this.columnMap);
    }
}

