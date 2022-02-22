
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.grid;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.HasMenuItems;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 */
public class GridColumnScanner<T> {

    private final Class<T> type;
    private final LinkedHashMap<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> columnMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, Grid.Column<?>> visbilityMenuColumns = new LinkedHashMap<>();

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
          ReflectUtil.getClassComparator());
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
                  + " and on method " + methodInfo.getMethod());
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
     * Create a new {@link Grid} with columns generated from {@link GridColumn &#64;GridColumn} annotations.
     *
     * <p>
     * No columns are included for bean properties that are not annotated.
     *
     * @return newly built {@link Grid} with auto-generated columns
     */
    public Grid<T> buildGrid() {
        final Grid<T> grid = new Grid<>(this.type, false);
        this.addColumnsTo(grid);
        return grid;
    }

    /**
     * Augment the given {@link Grid} with columns auto-generated from {@link GridColumn &#64;GridColumn} annotations.
     *
     * <p>
     * This method applies to target {@link Grid}s with any model type and gracefully handles mismatches: annotated
     * methods declared in Java types that are not supertype of {@code grid}'s model type always "return" null.
     *
     * <p>
     * This allows populating a {@link Grid} with columns even when only some subtypes of the model type can actually
     * provide values for those columns.
     *
     * <p>
     * Any existing columns with conflicting column keys will be replaced.
     *
     * @param grid target grid
     * @throws IllegalArgumentException if {@code grid} is null
     */
    public void addColumnsTo(Grid<?> grid) {
        this.addColumnsTo2(grid);
    }

    // This method is split from addColumnsTo() simply in order to bind type variable "S"
    private <S> void addColumnsTo2(Grid<S> grid) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");

        // Inventory any existing columns
        final List<Grid.Column<S>> existingColumnList = new ArrayList<>(grid.getColumns());

        // Get default annotation
        final GridColumn defaults = GridColumnScanner.getDefaults();

        // Gather columns in ordered lists, grouped by group name, and track visbilityMenu()
        final LinkedHashMap<String, List<Grid.Column<S>>> columnGroups = new LinkedHashMap<>();
        this.visbilityMenuColumns.clear();
        this.columnMap.forEach((columnKey, methodInfo) -> {

            // Remove any existing column with the same key
            existingColumnList.removeIf(column -> columnKey.equals(column.getKey()));

            // Get method and annotation
            final Method method = methodInfo.getMethod();
            final GridColumn annotation = methodInfo.getAnnotation();

            // Build a bean -> ReflectUtil.invoke(method, bean) that gracefully handles type mismatches
            final Class<?> requiredBeanType = method.getDeclaringClass();
            final ValueProvider<S, ?> valueProvider = bean -> Optional.ofNullable(bean)
                                                                .filter(requiredBeanType::isInstance)
                                                                .map(obj -> ReflectUtil.invoke(method, obj))
                                                                .orElse(null);

            // Add new column
            final boolean selfRendering = Component.class.isAssignableFrom(method.getReturnType());
            final Grid.Column<S> column = GridColumnScanner.addColumn(grid, columnKey,
              annotation, "method " + method, valueProvider, selfRendering, defaults);

            // Update column groups
            columnGroups.computeIfAbsent(annotation.columnGroup(), columnGroup -> new ArrayList<>()).add(column);

            // Add column to visibility menu, if desired
            if (annotation.visbilityMenu()) {
                String menuLabel = annotation.header();
                if (menuLabel.isEmpty())
                    menuLabel = annotation.key();
                visbilityMenuColumns.put(menuLabel, column);
            }
        });

        // Add a column group header row and group columns, if needed
        if (columnGroups.size() > 1 || (columnGroups.size() == 1 && !columnGroups.keySet().iterator().next().isEmpty())) {
            final HeaderRow headerRow = grid.prependHeaderRow();
            columnGroups.forEach((name, columns) -> headerRow.join(columns.toArray(new Grid.Column<?>[0])).setText(name));
        }

        // Set column order, respecting any column groupings
        grid.setColumnOrder(Stream.concat(existingColumnList.stream(), columnGroups.values().stream().flatMap(List::stream))
          .collect(Collectors.toList()));
    }

    /**
     * Add menu items that enable/disable the visibility of individual columns to the given menu.
     *
     * <p>
     * This method adds menu items for all columns for which {@link GridColumn#visbilityMenu visbilityMenu()} was true
     * in the most recent call to {@link #buildGrid} or {@link #addColumnsTo addColumnsTo()}.
     * The menu item labels are taken from {@link GridColumn#header header()}, if any, otherwise {@link GridColumn#key key()}.
     *
     * <p>
     * To use the menu, the caller will need to assign it to some target, e.g., via {@link ContextMenu#setTarget}.
     *
     * @param menu the menu to add visibility items to
     * @return visibility context menu, or null if there were zero such columns
     * @throws IllegalArgumentException if {@code menu} is null
     */
    public void addVisbilityMenuItems(HasMenuItems menu) {

        // Sanity check
        if (menu == null)
            throw new IllegalArgumentException("null menu");

        // Add menu items
        this.visbilityMenuColumns.forEach((label, column) -> {
            final MenuItem menuItem = menu.addItem(label, e -> column.setVisible(e.getSource().isChecked()));
            menuItem.setCheckable(true);
            menuItem.setChecked(column.isVisible());
        });
    }

    /**
     * Add and configure a single column to the given {@link Grid}.
     *
     * @param grid target {@link Grid}
     * @param key the column's unique column key
     * @param annotation {@link GridColumn &#64;GridColumn} annotation
     * @param description description of what we're configuring (for debug purposes)
     * @param valueProvider value provider for the column
     * @param selfRendering true if {@code valueProvider} returns a {@link Component}
     * @param <T> underlying bean type
     * @return newly added column
     * @throws IllegalArgumentException if any parameter is null
     */
    public static <T> Grid.Column<T> addColumn(Grid<T> grid, String key, GridColumn annotation,
      String description, ValueProvider<T, ?> valueProvider, boolean selfRendering) {
        return GridColumnScanner.addColumn(grid, key, annotation,
          description, valueProvider, selfRendering, GridColumnScanner.getDefaults());
    }

    @SuppressWarnings("unchecked")
    private static <T> Grid.Column<T> addColumn(Grid<T> grid, String key, GridColumn annotation,
      String description, ValueProvider<T, ?> valueProvider, boolean selfRendering, GridColumn defaults) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");
        if (key == null)
            throw new IllegalArgumentException("null key");
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        if (valueProvider == null)
            throw new IllegalArgumentException("null valueProvider");
        if (defaults == null)
            throw new IllegalArgumentException("null defaults");

        // Create custom Renderer, if any
        Renderer<T> renderer = null;
        if (!annotation.renderer().equals(defaults.renderer())) {
            final Constructor<? extends Renderer<?>> constructor;
            try {
                constructor = (Constructor<? extends Renderer<?>>)annotation.renderer().getConstructor(ValueProvider.class);
            } catch (Exception e) {
                throw new RuntimeException("cannot instantiate " + annotation.renderer()
                  + " because no constructor taking ValueProvider is found", e);
            }
            renderer = (Renderer<T>)ReflectUtil.instantiate(constructor, valueProvider);
        } else if (selfRendering)
            renderer = new SelfRenderer<T>((ValueProvider<T, ? extends Component>)valueProvider);

        // Create the column, using custom Renderer or else just ValueProvider
        final Grid.Column<T> column;
        if (grid instanceof TreeGrid && annotation.hierarchyColumn()) {
            final TreeGrid<T> treeGrid = (TreeGrid<T>)grid;
            if (renderer != null) {
                if (!(renderer instanceof ComponentRenderer)) {
                    throw new RuntimeException("non-default renderer type " + renderer.getClass().getName()
                      + " specified for " + description + " does not subclass " + ComponentRenderer.class.getName()
                      + ", which is required when configuring a TreeGrid with hierarchyColumn() = true");
                }
                column = treeGrid.addComponentHierarchyColumn(((ComponentRenderer)renderer)::createComponent);
            } else
                column = treeGrid.addHierarchyColumn(valueProvider);
        } else
            column = renderer != null ? grid.addColumn(renderer) : grid.addColumn(valueProvider);

        // Handle annotation properties that we can process automatically
        final HashSet<String> autoProperties = new HashSet<>(Arrays.asList(new String[] {
          "autoWidth", "classNameGenerator", "comparator", "flexGrow", "footer", "frozen", "header",
          "id", "resizable", "sortOrderProvider", "sortable", "textAlign", "visible", "width" }));
        AnnotationUtil.applyAnnotationValues(column, "set", annotation, defaults,
          (method, name) -> autoProperties.contains(name) ? name : null, ReflectUtil::instantiate);

        // Handle other annotation properties manually
        column.setKey(key);
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
