
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.server.SerializableComparator;
import com.vaadin.ui.StyleGenerator;
import com.vaadin.ui.components.grid.DescriptionGenerator;
import com.vaadin.ui.components.grid.SortOrderProvider;
import com.vaadin.ui.renderers.Renderer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides the information necessary to auto-generate a {@link com.vaadin.ui.Grid.Column} based on the annotated getter method.
 *
 * <p>
 * Example:
 * <blockquote><pre>
 * // Container backing object class
 * public class User {
 *
 *     private String username;
 *     private LocalDate dateOfBirth;
 *
 *     <b>&#64;GridColumn(caption = "User name", expandRatio = 3)</b>
 *     public String getUsername() {
 *         return this.username;
 *     }
 *     public void setUsername(String username) {
 *         this.username = username;
 *     }
 *
 *     <b>&#64;GridColumn(caption = "Birthday", sortable = true)</b>
 *     public LocalDate getDateOfBirth() {
 *         return this.dateOfBirth;
 *     }
 *     public void setDateOfBirth(LocalDate dateOfBirth) {
 *         this.dateOfBirth = dateOfBirth;
 *     }
 * }
 *
 * // Build Grid with auto-generated columns
 * Grid&lt;User&gt; grid = new GridColumnScanner(User.class).buildGrid();
 * ...
 * </pre></blockquote>
 *
 * <p>
 * Some details regarding {@link GridColumn &#64;GridColumn} annotations:
 *  <ul>
 *  <li>Only non-void methods taking zero parameters are supported; {@link GridColumn &#64;GridColumn}
 *      annotations on other methods are ignored</li>
 *  <li>Protected, package private, and private methods are supported.</li>
 *  <li>{@link GridColumn &#64;GridColumn} annotations declared in super-types (including interfaces) are supported</li>
 *  <li>If a method and the superclass or superinterface method it overrides are both annotated with
 *      {@link GridColumn &#64;GridColumn}, then the overridding method's annotation takes precedence.
 *  <li>If two methods with different names are annotated with {@link GridColumn &#64;GridColumn} for the same
 *      {@linkplain #value property name}, then the declaration in the class which is a sub-type of the other
 *      wins (if the two classes are equal or not comparable, an exception is thrown). This allows subclasses
 *      to "override" which method supplies a given property.</li>
 *  <li>Columns will be ordered first by {@link #order}, then by property name</li>
 *  </ul>
 *
 * @see GridColumnScanner
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
@Documented
public @interface GridColumn {

    /**
     * Get the column ID.
     *
     * <p>
     * If this is left unset (empty string), then the bean property name of the annotated bean property "getter" method is used.
     *
     * @return property name
     */
    String value() default "";

    /**
     * Get the caption for the Grid column.
     *
     * @return column caption
     * @see com.vaadin.ui.Grid.Column#setCaption
     */
    String caption() default "";

    /**
     * Get the in-memory comparator for the Grid column.
     *
     * @return column comparator
     * @see com.vaadin.ui.Grid.Column#setComparator
     */
    @SuppressWarnings("rawtypes")
    Class<? extends SerializableComparator> comparator() default SerializableComparator.class;

    /**
     * Get the description generator for the Grid column.
     *
     * @return column description generator
     * @see com.vaadin.ui.Grid.Column#setDescriptionGenerator
     */
    @SuppressWarnings("rawtypes")
    Class<? extends DescriptionGenerator> descriptionGenerator() default DescriptionGenerator.class;

    /**
     * Get whether the Grid column is editable.
     *
     * @return whether editable
     * @see com.vaadin.ui.Grid.Column#setEditable
     */
    boolean editable() default false;

    /**
     * Get the expand ratio for the Grid column.
     *
     * @return column expand ratio
     * @see com.vaadin.ui.Grid.Column#setExpandRatio
     */
    int expandRatio() default -1;

    /**
     * Get whether the Grid column is hidable.
     *
     * @return whether hidable
     * @see com.vaadin.ui.Grid.Column#setHidable
     */
    boolean hidable() default false;

    /**
     * Get whether the Grid column is hidden.
     *
     * @return whether hidden
     * @see com.vaadin.ui.Grid.Column#setHidden
     */
    boolean hidden() default false;

    /**
     * Get the hiding toggle caption for the Grid column.
     *
     * @return column hiding toggle caption
     * @see com.vaadin.ui.Grid.Column#setHidingToggleCaption
     */
    String hidingToggleCaption() default "";

    /**
     * Get the debug ID for the Grid column.
     *
     * @return column debug ID
     * @see com.vaadin.ui.Grid.Column#setId
     */
    String id() default "";

    /**
     * Get the maximum width for the Grid column.
     *
     * @return column maximum width
     * @see com.vaadin.ui.Grid.Column#setMaximumWidth
     */
    double maximumWidth() default Float.NaN;

    /**
     * Get the minimum width for the Grid column.
     *
     * @return column minimum width
     * @see com.vaadin.ui.Grid.Column#setMinimumWidth
     */
    double minimumWidth() default Float.NaN;

    /**
     * Get the renderer for the Grid column.
     *
     * @return column renderer
     * @see com.vaadin.ui.Grid.Column#setRenderer
     */
    @SuppressWarnings("rawtypes")
    Class<? extends Renderer> renderer() default Renderer.class;

    /**
     * Get whether the Grid column is resizable.
     *
     * @return whether resizable
     * @see com.vaadin.ui.Grid.Column#setResizable
     */
    boolean resizable() default false;

    /**
     * Get whether the Grid column is sortable.
     *
     * @return whether sortable
     * @see com.vaadin.ui.Grid.Column#setSortable
     */
    boolean sortable() default false;

    /**
     * Get the sort order provider for the Grid column.
     *
     * @return column sort order provider
     * @see com.vaadin.ui.Grid.Column#setSortOrderProvider
     */
    Class<? extends SortOrderProvider> sortOrderProvider() default SortOrderProvider.class;

    /**
     * Get the back-end sort properties for the Grid column.
     *
     * @return column back-end sort properties
     * @see com.vaadin.ui.Grid.Column#setSortOrderProvider
     */
    String[] sortProperty() default {};

    /**
     * Get the style generator for the Grid column.
     *
     * @return column style generator
     * @see com.vaadin.ui.Grid.Column#setStyleGenerator
     */
    @SuppressWarnings("rawtypes")
    Class<? extends StyleGenerator> styleGenerator() default StyleGenerator.class;

    /**
     * Get the width for the Grid column.
     *
     * @return column width
     * @see com.vaadin.ui.Grid.Column#setWidth
     */
    double width() default Float.NaN;

    /**
     * Get the ordering value for this column.
     *
     * @return column order value
     */
    double order() default 0;
}
