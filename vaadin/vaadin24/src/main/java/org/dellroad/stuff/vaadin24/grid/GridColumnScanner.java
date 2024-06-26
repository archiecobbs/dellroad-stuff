
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.grid;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.HasMenuItems;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.shared.util.SharedUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.java.ReflectUtil;
import org.dellroad.stuff.java.ThreadLocalHolder;
import org.dellroad.stuff.vaadin24.util.SelfRenderer;

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

    private static final ThreadLocalHolder<Grid<?>> CURRENT_GRID = new ThreadLocalHolder<>();

    private final Class<T> type;
    private final LinkedHashMap<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> columnMap = new LinkedHashMap<>();

// Constructors

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
          = new MethodAnnotationScanner<T, GridColumn>(this.type, GridColumn.class) {
            @Override
            protected boolean includeMethod(Method method, GridColumn annotation) {
                return method.getParameterTypes().length == 0;
            }
        }.findAnnotatedMethods();

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
                throw new IllegalArgumentException(String.format(
                  "duplicate @%s declaration for column key \"%s\" on method %s and on method %s",
                  GridColumn.class.getSimpleName(), columnKey, previousInfo.getMethod(), methodInfo.getMethod()));
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
     * Static information copy constructor.
     *
     * <p>
     * Using this constructor is more efficient than repeatedly scanning the same classes for the same annotations.
     *
     * <p>
     * Any modifications made to the {@link Map} returned from {@link getColumnMap getColumnMap()} are included.
     *
     * @param original original instance
     * @throws IllegalArgumentException if {@code original} is null
     */
    public GridColumnScanner(GridColumnScanner<T> original) {
        if (original == null)
            throw new IllegalArgumentException("null original");
        this.type = original.type;
        this.columnMap.putAll(original.columnMap);
    }

// Public Methods

    /**
     * Get the type associated with this instance.
     *
     * @return backing object type
     */
    public Class<T> getType() {
        return this.type;
    }

    /**
     * Get the annotations found through introspection indexed by {@linkplain GridColumn#key column key}.
     *
     * <p>
     * This represents static information gathered by this instance by scanning the class hierarchy during construction.
     *
     * <p>
     * The returned map is mutable, e.g., if you delete unwanted entries then {@link #buildGrid} will skip them.
     *
     * @return annotations keyed by {@linkplain GridColumn#key column key}, and sorted based on {@link GridColumn#order}, then key
     */
    public Map<String, MethodAnnotationScanner<T, GridColumn>.MethodInfo> getColumnMap() {
        return this.columnMap;
    }

    /**
     * Create a new {@link Grid} with columns generated from {@link GridColumn &#64;GridColumn} annotations.
     *
     * <p>
     * No columns are included for bean properties that are not annotated.
     *
     * <p>
     * During the execution of this method, in particular when any custom {@link Renderer}, {@link ValueProvider}, etc.,
     * classes are being instantiated, the {@link Grid} being configured is available via {@link #currentGrid}.
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
     * <p>
     * During the execution of this method, in particular when any custom {@link Renderer}, {@link ValueProvider}, etc.,
     * classes are being instantiated, the supplied {@code grid} is available via {@link #currentGrid}.
     *
     * @param grid target grid
     * @throws IllegalArgumentException if {@code grid} is null
     */
    public void addColumnsTo(Grid<?> grid) {
        CURRENT_GRID.invoke(grid, () -> this.addColumnsTo2(grid));
    }

    // This method is split from addColumnsTo() simply in order to bind type variable "S"
    private <S> void addColumnsTo2(Grid<S> grid) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");

        // Inventory any columns by key, but while also preserving their current order
        final LinkedHashMap<String, Grid.Column<S>> existingColumnMap = grid.getColumns().stream()
          .collect(Collectors.toMap(Grid.Column::getKey, c -> c,
            (c1, c2) -> {
              throw new RuntimeException("internal error");
            }, LinkedHashMap::new));

        // Get default annotation
        final GridColumn defaults = GridColumnScanner.getDefaults();

        // Gather columns in ordered lists, grouped by group name, and track visibilityMenu()
        final LinkedHashMap<String, List<Grid.Column<S>>> columnGroups = new LinkedHashMap<>();
        this.columnMap.forEach((columnKey, methodInfo) -> {

            // Remove any existing column with the same key - the new column is going to override it
            if (existingColumnMap.remove(columnKey) != null)
                grid.removeColumnByKey(columnKey);

            // Get method and annotation
            final Method method = methodInfo.getMethod();
            final GridColumn annotation = methodInfo.getAnnotation();
            final Class<?> returnType = method.getReturnType();
            final boolean voidMethod = returnType == void.class || returnType == Void.class;

            // Build a bean -> ReflectUtil.invoke(method, bean) that gracefully handles type mismatches
            final Class<?> requiredBeanType = method.getDeclaringClass();
            final ValueProvider<S, ?> valueProvider = voidMethod ? null : bean -> Optional.ofNullable(bean)
                                                                            .filter(requiredBeanType::isInstance)
                                                                            .map(obj -> ReflectUtil.invoke(method, obj))
                                                                            .orElse(null);

            // Add new column
            final boolean selfRendering = Component.class.isAssignableFrom(method.getReturnType());
            final Grid.Column<S> column = GridColumnScanner.addColumn(grid, columnKey,
              annotation, "method " + method, valueProvider, selfRendering, defaults);

            // Update column groups
            columnGroups.computeIfAbsent(annotation.columnGroup(), columnGroup -> new ArrayList<>()).add(column);
        });

        // Add a column group header row and group columns, if needed
        if (columnGroups.size() > 1 || (columnGroups.size() == 1 && !columnGroups.keySet().iterator().next().isEmpty())) {
            final HeaderRow headerRow = grid.prependHeaderRow();
            columnGroups.forEach((name, columns) -> headerRow.join(columns.toArray(new Grid.Column<?>[0])).setText(name));
        }

        // Set column order, respecting any column groupings
        final List<Grid.Column<S>> columnList = Stream.concat(
            existingColumnMap.values().stream(),
            columnGroups.values().stream().flatMap(List::stream))
          .collect(Collectors.toList());
        grid.setColumnOrder(columnList);
    }

    /**
     * Add menu items that enable/disable the visibility of individual columns for for which
     * {@link GridColumn#visibilityMenu visibilityMenu()} was true to the given menu.
     *
     * <p>
     * This method adds menu items for all columns in the given grid for which {@link GridColumn#visibilityMenu visibilityMenu()}
     * was true, according to the most recent call to {@link #buildGrid} or {@link #addColumnsTo addColumnsTo()}.
     *
     * <p>
     * The menu item labels come from {@link GridColumn#header header()}, if any, otherwise {@link GridColumn#key key()}
     * via {@link SharedUtil#camelCaseToHumanFriendly SharedUtil.camelCaseToHumanFriendly()}.
     *
     * <p>
     * To use the menu, the caller will need to assign it to some target, e.g., via {@link ContextMenu#setTarget}.
     *
     * @param grid grid containing previously scanned columns
     * @param menu the menu to add visibility items to
     * @return true if any menu items were added, otherwise false
     * @throws IllegalArgumentException if either parameter is null
     */
    public boolean addVisbilityMenuItems(Grid<?> grid, HasMenuItems menu) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");
        if (menu == null)
            throw new IllegalArgumentException("null menu");

        // Add menu items
        final AtomicBoolean menuItemsAdded = new AtomicBoolean();
        this.columnMap.forEach((columnKey, methodInfo) -> {

            // In visibility menu?
            final GridColumn annotation = methodInfo.getAnnotation();
            if (!annotation.visibilityMenu())
                return;

            // Find corresponding column
            final Grid.Column<?> column = grid.getColumnByKey(columnKey);
            if (column == null)
                return;

            // Get menu label
            final String menuLabel = !annotation.header().isEmpty() ?
              annotation.header() : SharedUtil.camelCaseToHumanFriendly(annotation.key());

            // Add menu item
            final MenuItem menuItem = menu.addItem(menuLabel, e -> column.setVisible(e.getSource().isChecked()));
            menuItem.setCheckable(true);
            menuItem.setChecked(column.isVisible());
            menuItemsAdded.set(true);
        });

        // Done
        return menuItemsAdded.get();
    }

    /**
     * Add and configure a single column to the given {@link Grid}.
     *
     * <p>
     * During the execution of this method, in particular when any custom {@link Renderer}, {@link ValueProvider}, etc.,
     * classes are being instantiated, the supplied {@code grid} is available via {@link #currentGrid}.
     *
     * @param grid target {@link Grid}
     * @param key the column's unique column key
     * @param annotation {@link GridColumn &#64;GridColumn} annotation
     * @param description description of what we're configuring (for debug purposes)
     * @param valueProvider {@link ValueProvider} providing the return value from the annotated method,
     *  or null if the annotated method returns {@code void} or {@link Void}
     * @param selfRendering true if the annotated method (and therefore {@code valueProvider}) returns a {@link Component}
     * @param <T> underlying bean type
     * @return newly added column
     * @throws IllegalArgumentException if any parameter is null
     */
    public static <T> Grid.Column<T> addColumn(Grid<T> grid, String key, GridColumn annotation,
      String description, ValueProvider<T, ?> valueProvider, boolean selfRendering) {
        return CURRENT_GRID.invoke(grid, () -> GridColumnScanner.addColumn(grid, key, annotation,
          description, valueProvider, selfRendering, GridColumnScanner.getDefaults()));
    }

    /**
     * Obtain the {@link Grid} being configured in the current thread.
     *
     * <p>
     * This method only works when the current thread is executing in {@link #buildGrid buildGrid()},
     * {@link #addColumn addColumn()}, or {@link #addColumnsTo addColumnsTo()}.
     *
     * @return current {@link Grid} being configured
     * @throws IllegalStateException if no {@link Grid} is currently being configured by this class
     */
    public static Grid<?> currentGrid() {
        return GridColumnScanner.CURRENT_GRID.require();
    }

// Internal Methods

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> Grid.Column<T> addColumn(Grid<T> grid, String key, GridColumn annotation,
      String description, ValueProvider<T, ?> valueProvider0, boolean selfRendering, GridColumn defaults) {

        // Sanity check
        if (grid == null)
            throw new IllegalArgumentException("null grid");
        if (key == null)
            throw new IllegalArgumentException("null key");
        if (annotation == null)
            throw new IllegalArgumentException("null annotation");
        if (defaults == null)
            throw new IllegalArgumentException("null defaults");

        // Don't let valueProvider be null
        final ValueProvider<T, ?> valueProvider = valueProvider0 != null ? valueProvider0 : bean -> null;

        // Provide helpful exception messages
        class ErrorWrapper<T> implements Supplier<T> {

            private final Supplier<T> supplier;

            ErrorWrapper(Supplier<T> supplier) {
                this.supplier = supplier;
            }

            @Override
            public T get() {
                try {
                    return this.supplier.get();
                } catch (RuntimeException e) {
                    throw new RuntimeException(String.format(
                      "error in @%s annotation for %s", GridColumn.class.getSimpleName(), description), e);
                }
            }
        }

        // Create custom Renderer, if any
        Renderer<T> renderer = null;
        if (!annotation.renderer().equals(defaults.renderer())) {
            renderer = (Renderer<T>)new ErrorWrapper<>(() -> {

                // Try constructor #1
                Constructor<? extends Renderer<?>> constructor = null;
                Object[] constructorParams = null;
                Exception firstException = null;
                if (valueProvider != null) {
                    try {
                        constructor = (Constructor<? extends Renderer<?>>)annotation.renderer().getConstructor(ValueProvider.class);
                        constructorParams = new Object[] { valueProvider };
                    } catch (Exception e) {
                        firstException = e;
                    }
                }

                // Try constructor #2
                if (constructor == null) {
                    try {
                        constructor = (Constructor<? extends Renderer<?>>)annotation.renderer().getConstructor();
                        constructorParams = new Object[0];
                    } catch (Exception e) {
                        final RuntimeException re = new RuntimeException(String.format(
                          "cannot instantiate %s because no default constructor%s was found",
                          annotation.renderer(), valueProvider != null ? " or a constructor taking ValueProvider" : ""), e);
                        if (firstException != null)
                            re.addSuppressed(firstException);
                        throw re;
                    }
                }

                // Invoke constructor to create Renderer
                return ReflectUtil.instantiate(constructor, constructorParams);
            }).get();
        } else if (selfRendering)
            renderer = new SelfRenderer<T>((ValueProvider<T, ? extends Component>)valueProvider);

        // Create the column, using custom Renderer or else just ValueProvider
        final Grid.Column<T> column;
        if (grid instanceof TreeGrid && annotation.hierarchyColumn()) {
            final TreeGrid<T> treeGrid = (TreeGrid<T>)grid;
            if (renderer != null) {
                if (!(renderer instanceof ComponentRenderer)) {
                    throw new RuntimeException(String.format(
                      "non-default renderer type %s specified for %s does not subclass %s,"
                      + " which is required when configuring a TreeGrid with hierarchyColumn() = true",
                      renderer.getClass().getName(), description, ComponentRenderer.class.getName()));
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
          (method, name) -> autoProperties.contains(name) ? name : null,
          type -> new ErrorWrapper<>(() -> ReflectUtil.instantiate(type)).get());

        // Handle other annotation properties manually
        column.setKey(key);
        if (!annotation.valueProviderComparator().equals(defaults.valueProviderComparator())) {
            final ValueProvider keyExtractor = new ErrorWrapper<>(
              () -> ReflectUtil.instantiate(annotation.valueProviderComparator())).get();
            column.setComparator(keyExtractor);
        }
        if (!annotation.editorComponent().equals(defaults.editorComponent())) {
            final SerializableFunction componentCallback = new ErrorWrapper<>(
              () -> (SerializableFunction)ReflectUtil.instantiate(annotation.editorComponent())).get();
            column.setEditorComponent(componentCallback);
        }
        if (!annotation.tooltipGenerator().equals(defaults.tooltipGenerator())) {
            final SerializableFunction tooltipGenerator = new ErrorWrapper<>(
              () -> (SerializableFunction)ReflectUtil.instantiate(annotation.tooltipGenerator())).get();
            column.setTooltipGenerator(tooltipGenerator);
        }
        if (annotation.sortProperties().length > 0)
            column.setSortProperty(annotation.sortProperties());
        final String[] styleProperties = annotation.styleProperties();
        if (styleProperties.length > 0) {
            final Style style = column.getStyle();
            for (int i = 0; i < styleProperties.length - 1; )
                style.set(styleProperties[i++], styleProperties[i++]);
        }

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
}
