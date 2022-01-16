
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component.grid;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.ReflectUtil;

/**
 * Scans a Java class hierarchy for {@link GridColumn &#64;GridColumn} annotated getter methods,
 * allowing for declarative auto-generation of {@link Grid.Column}s.
 *
 * <p>
 * See {@link GridColumn &#64;GridColumn} for an example of usage.
 *
 * @param <T> Java class to be introspected
 * @see GridColumn &#64;GridColumn
 * @see org.dellroad.stuff.vaadin22.flow.component.fieldbuilder.FieldBuilder#setEditorComponents
 */
public class GridColumnScanner<T> {

    private final Class<T> type;
    private final LinkedHashMap<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> columnMap = new LinkedHashMap<>();

    /**
     * Scan the given type and all super-types for {@link GridColumn &#64;GridColumn} annotations.
     *
     * @param type Java type to be introspected for annotations
     * @throws IllegalArgumentException if {@code type} is null
     * @throws IllegalArgumentException if a bean property name cannot be inferred from the name of a method
     *  that is annotated with no {@linkplain GridColumn#key explicit column key}
     * @throws IllegalArgumentException if {@code type} has multiple {@link GridColumn &#64;GridColumn}-annotated
     *  methods specifying the same {@linkplain GridColumn#key column key}
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

            // Get column key
            final String columnKey = this.determineColumnKey(methodInfo);

            // Check for name conflict
            final MethodAnnotationScanner<T, GridColumn>.MethodInfo previousInfo
              = unorderedColumnMap.putIfAbsent(columnKey, methodInfo);
            if (previousInfo == null)
                continue;

            // If there is a name conflict, the sub-type method declaration wins
            final int diff = methodComparator.compare(previousInfo.getMethod(), methodInfo.getMethod());
            if (diff > 0)
                unorderedColumnMap.put(columnKey, methodInfo);
            else if (diff == 0) {
                throw new IllegalArgumentException("duplicate @" + GridColumn.class.getSimpleName()
                  + " declaration for column key `" + columnKey + "' on method " + previousInfo.getMethod()
                  + " and " + methodInfo.getMethod() + " declared in the same class");
            }
            // else previous wins
        }

        // Order columns and add to map
        unorderedColumnMap.entrySet().stream()
          .sorted(Comparator.<Map.Entry<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo>>comparingDouble(
            entry -> entry.getValue().getAnnotation().order()).thenComparing(Map.Entry::getKey))
          .forEach(entry -> this.columnMap.put(entry.getKey(), entry.getValue()));
    }

    /**
     * Create a new {@link Grid} and add all columns auto-generated from {@link GridColumn &#64;GridColumn} annotations.
     *
     * @return newly built {@link Grid} with auto-generated columns
     */
    public Grid<T> buildGrid() {
        final Grid<T> grid = new Grid<>(this.type);
        this.addColumnsTo(grid);
        return grid;
    }

    /**
     * Augment the given {@link Grid} with columns auto-generated from {@link GridColumn &#64;GridColumn} annotations.
     *
     * @param grid target grid
     * @throws IllegalArgumentException if {@code grid} is null
     */
    public void addColumnsTo(Grid<T> grid) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");

        // Get default annotation
        final GridColumn defaults = GridColumnScanner.getDefaults();

        // Get current list of columns to capture current order
        final List<Grid.Column<T>> columnList = new ArrayList<>(grid.getColumns());

        // Add new columns to the end of the list
        this.columnMap.forEach((columnKey, methodInfo) ->
          columnList.add(GridColumnScanner.addColumn(grid, columnKey, methodInfo, defaults)));

        // Update column order (is this really necessary?)
        grid.setColumnOrder(columnList);
    }

    /**
     * Add and configure a single column to the given {@link Grid}.
     *
     * @param key the column's unique column key
     * @param grid target {@link Grid}
     * @param methodInfo method's {@link GridColumn &#64;GridColumn} annotation info
     * @param <T> underlying bean type
     * @return newly added column
     * @throws IllegalArgumentException if any parameter is null
     */
    public static <T> Grid.Column<T> addColumn(Grid<T> grid, String key,
      MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo) {
        return GridColumnScanner.addColumn(grid, key, methodInfo, GridColumnScanner.getDefaults());
    }

    @SuppressWarnings("unchecked")
    private static <T> Grid.Column<T> addColumn(Grid<T> grid, String key,
      MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo, GridColumn defaults) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");
        if (key == null)
            throw new IllegalArgumentException("null key");
        if (methodInfo == null)
            throw new IllegalArgumentException("null methodInfo");
        if (defaults == null)
            throw new IllegalArgumentException("null defaults");

        // Create ValueProvider for this property
        final ValueProvider<T, ?> valueProvider = bean -> ReflectUtil.invoke(methodInfo.getMethod(), bean);

        // Create custom Renderer, if any
        Renderer<T> renderer = null;
        final GridColumn annotation = methodInfo.getAnnotation();
        if (!annotation.renderer().equals(defaults.renderer())) {
            final Constructor<? extends Renderer<?>> constructor;
            try {
                constructor = (Constructor<? extends Renderer<?>>)annotation.renderer().getConstructor(ValueProvider.class);
            } catch (Exception e) {
                throw new RuntimeException("cannot instantiate " + annotation.renderer()
                  + " because no constructor taking ValueProvider is found", e);
            }
            renderer = (Renderer<T>)ReflectUtil.instantiate(constructor, valueProvider);
        }

        // Create the column, using custom Renderer or else just ValueProvider
        final Grid.Column<T> column = renderer != null ? grid.addColumn(renderer) : grid.addColumn(valueProvider);

        // Handle annotation properties that we can process automatically
        final HashSet<String> autoProperties = new HashSet<>(Arrays.asList(new String[] {
          "autoWidth", "classNameGenerator", "comparator", "flexGrow", "footer", "frozen", "header",
          "id", "key", "resizable", "sortOrderProvider", "sortable", "textAlign", "visible", "width" }));
        AnnotationUtil.applyAnnotationValues(column, "set", methodInfo.getAnnotation(), defaults,
          (method, name) -> autoProperties.contains(name) ? name : null);

        // Handle other annotation properties manually
        if (!annotation.valueProviderComparator().equals(defaults.valueProviderComparator()))
            column.setComparator(ReflectUtil.instantiate(annotation.valueProviderComparator()));
        if (!annotation.editorComponent().equals(defaults.editorComponent())) {
            column.setEditorComponent(
              (SerializableFunction<T, ? extends Component>)ReflectUtil.instantiate(annotation.editorComponent()));
        }
        if (annotation.sortProperties().length > 0)
            column.setSortProperty(annotation.sortProperties());

        // Done
        return column;
    }

    @GridColumn
    private static GridColumn getDefaults() {
        return AnnotationUtil.getAnnotation(GridColumn.class, GridColumnScanner.class, "getDefaults");
    }

    /**
     * Determine the column key from the annotation.
     *
     * @param methodInfo method info
     * @return property name
     */
    protected String determineColumnKey(MethodAnnotationScanner<T, GridColumn>.MethodInfo methodInfo) {
        if (!methodInfo.getAnnotation().key().isEmpty())
            return methodInfo.getAnnotation().key();
        return Optional.ofNullable(methodInfo.getMethodPropertyName())
          .orElseThrow(
            () -> new IllegalArgumentException("can't infer column key name from non-bean method " + methodInfo.getMethod()));
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
