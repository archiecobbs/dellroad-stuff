
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.grid;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.grid.SortOrderProvider;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;

import org.dellroad.stuff.vaadin22.util.SelfRenderer;

/**
 * Provides the information necessary to auto-generate a {@link Grid.Column} based on annotated getter methods.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * Example:
 * <pre><code class="language-java">
 * // Container backing object class
 * public class User {
 *
 *     // Default rendering using String value
 *     <b>&#64;GridColumn(header = "Username", width = "50px")</b>
 *     public String getUsername() { ... }
 *
 *     // Custom rendering using the specified Renderer
 *     <b>&#64;GridColumn(header = "DOB", sortable = true, renderer = MyRenderer.class)</b>
 *     public LocalDate getDateOfBirth() { ... }
 *
 *     // Render directly using the returned Component
 *     <b>&#64;GridColumn(header = "Status")</b>
 *     public Image getStatus() { ... }
 * }
 *
 * // Build Grid with auto-generated columns
 * final Grid&lt;User&gt; grid = new GridColumnScanner&lt;&gt;(User.class).buildGrid();
 * ...
 *
 * </code></pre>
 *
 * <p>
 * Some details regarding {@link GridColumn &#64;GridColumn} annotations:
 * <ul>
 *  <li>Only non-void instance methods taking zero parameters are supported; {@link GridColumn &#64;GridColumn}
 *      annotations on other methods are ignored</li>
 *  <li>Protected, package private, and private methods are supported.</li>
 *  <li>{@link GridColumn &#64;GridColumn} annotations declared in super-types (including interfaces) are supported</li>
 *  <li>If a method and the superclass or superinterface method it overrides are both annotated with
 *      {@link GridColumn &#64;GridColumn}, then the overridding method's annotation takes precedence
 *      (all properties are overridden).
 *  <li>If two methods with different names are annotated with {@link GridColumn &#64;GridColumn} for the same
 *      {@linkplain #key column key}, then the declaration in the class which is a sub-type of the other
 *      wins (if the two classes are equal or not comparable, an exception is thrown). This allows subclasses
 *      to "override" which method supplies a given property.</li>
 *  <li>If the method returns a sub-type of {@link Component}, then by default a {@link SelfRenderer} is assumed.
 *      This lets you define a method that directly returns the {@link Component} to display.</li>
 *  <li>Columns will be ordered first by {@link #order}, then by property name.</li>
 * </ul>
 *
 * @see GridColumnScanner
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface GridColumn {

    /**
     * Get the custom renderer class to use.
     *
     * <p>
     * If this property is not specified, {@link Grid#addColumn(ValueProvider)} is used to create the column;
     * if this property is specified, {@link Grid#addColumn(Renderer)} is used to create the column and the
     * specified class must have a constructor taking a {@link ValueProvider} parameter.
     *
     * <p>
     * As a special case, when the annotated method returns a sub-type of {@link Component} and this property
     * is not specified otherwise, then a {@link SelfRenderer} is used.
     *
     * @return column renderer class
     * @see Grid#addColumn(Renderer)
     * @see SelfRenderer
     */
    @SuppressWarnings("rawtypes")
    Class<? extends Renderer> renderer() default Renderer.class;

    /**
     * Get the auto width setting.
     *
     * @return auto width
     * @see Grid.Column#setAutoWidth
     */
    boolean autoWidth() default true;

    /**
     * Get the CSS class name generator class.
     *
     * @return column comparator
     * @see Grid.Column#setClassNameGenerator
     */
    @SuppressWarnings("rawtypes")
    Class<? extends SerializableFunction> classNameGenerator() default SerializableFunction.class;

    /**
     * Get the name of this column's column group.
     *
     * <p>
     * Columns with the same (non-empty) group name will be grouped together under a common super-heading for the group.
     *
     * @return column group name, or empty string for none
     * @see HeaderRow#join
     */
    @SuppressWarnings("rawtypes")
    String columnGroup() default "";

    /**
     * Get the {@link Comparator} class to use for in-memory sorting.
     *
     * <p>
     * If this property is set, {@link #sortable} should also be true, and {@link #valueProviderComparator} should not be set.
     *
     * @return column comparator
     * @see Grid.Column#setComparator
     * @see #valueProviderComparator
     */
    @SuppressWarnings("rawtypes")
    Class<? extends Comparator> comparator() default Comparator.class;

    /**
     * Get the {@link ValueProvider} class to use to produce values used for in-memory sorting.
     *
     * <p>
     * If this property is set, {@link #sortable} should also be true, and {@link #comparator} should not be set.
     *
     * @return column comparator
     * @see Grid.Column#setComparator
     * @see #comparator
     */
    @SuppressWarnings("rawtypes")
    Class<? extends ValueProvider> valueProviderComparator() default ValueProvider.class;

    /**
     * Get the editor component generator class.
     *
     * @return editor component class
     * @see Grid.Column#setEditorComponent(SerializableFunction)
     */
    @SuppressWarnings("rawtypes")
    Class<? extends SerializableFunction> editorComponent() default SerializableFunction.class;

    /**
     * Get the flex grow ratio.
     *
     * @return column flex grow ratio.
     * @see Grid.Column#setFlexGrow
     */
    int flexGrow() default 0;

    /**
     * Get the column footer.
     *
     * @return column footer
     * @see Grid.Column#setFooter(String)
     */
    String footer() default "";

    /**
     * Get the frozen setting.
     *
     * @return column frozen setting
     * @see Grid.Column#setFrozen
     */
    boolean frozen() default false;

    /**
     * Get the column header.
     *
     * @return column header
     * @see Grid.Column#setHeader(String)
     */
    String header() default "";

    /**
     * Get whether this column is the hierarchy column for a {@link TreeGrid}.
     *
     * <p>
     * If this property is set, and the {@link Grid} being configured is a {@link TreeGrid}, then the column is created
     * as an expand/collapse "hierarchy column".
     *
     * <p>
     * How the column is created depends on {@link #renderer}. If a {@link #renderer} class is specified, then it
     * <i>must</i> subclass {@link ComponentRenderer}, and
     * {@link TreeGrid#addComponentHierarchyColumn TreeGrid.addComponentHierarchyColumn()} will be used to create the column
     * using the rendered components. Otherwise, {@link TreeGrid#addHierarchyColumn TreeGrid.addHierarchyColumn()} is used to
     * create the column, using {@link String#valueOf String.valueOf()} applied to the annotated method's return value.
     *
     * <p>
     * If the {@link Grid} being configured is not a {@link TreeGrid}, this column is ignored.
     *
     * @return column debug ID
     * @see TreeGrid#addHierarchyColumn TreeGrid.addHierarchyColumn()
     * @see TreeGrid#addComponentHierarchyColumn TreeGrid.addComponentHierarchyColumn()
     */
    boolean hierarchyColumn() default false;

    /**
     * Get the column debug ID.
     *
     * @return column debug ID
     * @see Grid.Column#setId
     */
    String id() default "";

    /**
     * Get the column key.
     *
     * <p>
     * If this is left unset, the annotated method's bean property name method is used.
     *
     * @return column key
     * @see Grid.Column#setKey
     */
    String key() default "";

    /**
     * Get the user-resizable setting.
     *
     * @return column resizable setting
     * @see Grid.Column#setResizable
     */
    boolean resizable() default false;

    /**
     * Get the sortable setting.
     *
     * @return column sortable setting
     * @see Grid.Column#setSortable
     */
    boolean sortable() default false;

    /**
     * Get the {@link SortOrderProvider} class.
     *
     * <p>
     * If this property is set, {@link #sortable} should also be true.
     *
     * @return column {@link SortOrderProvider}
     * @see Grid.Column#setSortOrderProvider
     */
    @SuppressWarnings("rawtypes")
    Class<? extends SortOrderProvider> sortOrderProvider() default SortOrderProvider.class;

    /**
     * Get the back-end sort properties.
     *
     * <p>
     * If this property is set, {@link #sortable} should also be true.
     *
     * @return sort properties
     * @see Grid.Column#setSortProperty
     */
    String[] sortProperties() default {};

    /**
     * Get the text alignment setting.
     *
     * @return column text alignment setting
     * @see Grid.Column#setTextAlign
     */
    ColumnTextAlign textAlign() default ColumnTextAlign.START;

    /**
     * Get initial visibility setting.
     *
     * @return whether column is visible
     * @see #visibilityMenu
     * @see Grid.Column#setVisible
     */
    boolean visible() default true;

    /**
     * Get whether to include this column as one of the columns with configurable visibility by way
     * of {@link GridColumnScanner#addVisbilityMenuItems}.
     *
     * <p>
     * The menu item labels are taken from the {@link #header}, if any, otherwise {@link #key}.
     *
     * @return whether column has configurable visibility
     * @see #visible
     * @see GridColumnScanner#addVisbilityMenuItems
     */
    boolean visibilityMenu() default false;

    /**
     * Get the width.
     *
     * @return column width
     * @see Grid.Column#setWidth
     */
    String width() default "";

    /**
     * Get the ordering value for this column.
     *
     * @return column order value
     */
    double order() default 0;
}
