
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.PropertySet;
import com.vaadin.data.ValueProvider;
import com.vaadin.ui.Grid;
import com.vaadin.ui.renderers.Renderer;
import com.vaadin.util.ReflectTools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.ReflectUtil;

/**
 * Scans a Java class hierarchy for {@link GridColumn &#64;GridColumn} annotated getter methods, and auto-generates and
 * configures corresponding {@link Grid.Column}s on a {@link Grid} instance.
 *
 * <p>
 * This class will also introspect for {@link FieldBuilder} annotations and
 * {@linkplain Grid.Column#setEditorBinding(Binder.Binding) configure editor bindings} accordingly via
 * {@link FieldBuilder#setEditorBindings}.
 *
 * <p>
 * See {@link GridColumn} for an example of usage.
 *
 * @param <T> Java class to be introspected
 * @see GridColumn &#64;GridColumn
 * @see FieldBuilder#setEditorBindings
 */
public class GridColumnScanner<T> {

    private final Class<T> type;
    private final LinkedHashMap<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> columnMap = new LinkedHashMap<>();

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
          ReflectUtil.getClassComparator(false));
        final HashMap<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> unorderedColumnMap = new HashMap<>();
        for (MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo : gridColumnMethods) {
            final String propertyName = this.getPropertyName(methodInfo);

            // Check for name conflict
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo previousInfo = unorderedColumnMap.get(propertyName);
            if (previousInfo == null) {
                unorderedColumnMap.put(propertyName, methodInfo);
                continue;
            }

            // If there is a name conflict, the sub-type method declaration wins
            switch (methodComparator.compare(previousInfo.getMethod(), methodInfo.getMethod())) {
            case 0:
                throw new IllegalArgumentException("duplicate @" + GridColumn.class.getSimpleName()
                  + " declaration for property `" + propertyName + "' on method " + previousInfo.getMethod()
                  + " and " + methodInfo.getMethod() + " declared in the same class");
            case 1:
                unorderedColumnMap.put(propertyName, methodInfo);
                break;
            default:
                break;
            }
        }

        // Order columns
        final ArrayList<Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo>> columnList
          = new ArrayList<>(unorderedColumnMap.entrySet());
        Collections.sort(columnList,
          Comparator.<Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo>>comparingDouble(
           entry -> entry.getValue().getAnnotation().order())
          .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> entry : columnList)
            this.columnMap.put(entry.getKey(), entry.getValue());
    }

    /**
     * Build a {@link Grid} with columns auto-generated from introspected {@link GridColumn &#64;GridColumn} annotations.
     *
     * <p>
     * The implementation in {@link GridColumnScanner} simply invokes {@link #buildGrid() this.buildGrid(Grid::withPropertySet)}.
     *
     * @return new {@link Grid}
     */
    public Grid<T> buildGrid() {
        return this.buildGrid(Grid::withPropertySet);
    }

    /**
     * Build a {@link Grid} with columns auto-generated from introspected {@link GridColumn &#64;GridColumn} annotations,
     * using the given function to instantiate the {@link Grid}.
     *
     * @param creator function that creates a new {@link Grid} instance given a {@link PropertySet}
     * @param <G> {@link Grid} type
     * @return new {@link Grid}
     * @throws IllegalArgumentException if {@code creator} is null
     */
    public <G extends Grid<T>> G buildGrid(Function<? super PropertySet<T>, G> creator) {

        // Sanity check
        if (creator == null)
            throw new IllegalArgumentException("null creator");

        // Build property set
        final SimplePropertySet<T> propertySet = new SimplePropertySet<>(this.type);
        for (Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> e : this.columnMap.entrySet()) {
            final String propertyName = e.getKey();
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo = e.getValue();
            final Method getter = methodInfo.getMethod();
            final Method setter = methodInfo.getSetter();
            propertySet.add(ReflectTools.convertPrimitiveType(getter.getReturnType()),
              propertyName, methodInfo.getAnnotation().caption(), getter, setter);
        }

        // Create grid
        final G grid = creator.apply(propertySet);

        // Set field editors
        new FieldBuilder<>(this.type).buildAndBind().setEditorBindings(grid);

        // Get default annotation
        final GridColumn defaults = GridColumnScanner.getDefaults();

        // Modify columns
        for (Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> e : this.columnMap.entrySet()) {
            final String propertyName = e.getKey();
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo = e.getValue();
            final GridColumn annotation = methodInfo.getAnnotation();

            // Get column
            final Grid.Column<T, ?> column = grid.getColumn(propertyName);

            // Apply annotation values
            AnnotationUtil.applyAnnotationValues(column, "set", annotation, defaults, (method, name) -> name);

            // Special handling for setRenderer() method with two parameters
            if (annotation.renderer() != defaults.renderer() && annotation.valueProvider() != defaults.valueProvider())
                this.setRenderer(column, annotation.renderer(), annotation.valueProvider());
        }

        // Order columns
        grid.setColumns(this.columnMap.keySet().toArray(new String[this.columnMap.size()]));

        // Done
        return grid;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <V, P, R extends Renderer, VP extends ValueProvider> void setRenderer(
      Grid.Column<T, V> column, Class<R> rendererType, Class<VP> valueProviderType) {
        final Renderer<P> renderer = (Renderer<P>)ReflectUtil.instantiate(rendererType);
        final ValueProvider<V, P> valueProvider = (ValueProvider<V, P>)ReflectUtil.instantiate(valueProviderType);
        column.setRenderer(valueProvider, renderer);
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
     * @return columns keyed by property name and sorted based on {@link GridColumn#order}, then property name
     */
    public Map<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> getColumnMap() {
        return Collections.unmodifiableMap(this.columnMap);
    }
}

