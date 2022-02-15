
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.fieldbuilder;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.selection.MultiSelect;
import com.vaadin.flow.data.selection.SingleSelect;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.dellroad.stuff.java.AnnotationUtil;
import org.dellroad.stuff.java.MethodAnnotationScanner;
import org.dellroad.stuff.vaadin22.grid.GridColumn;
import org.dellroad.stuff.vaadin22.grid.GridColumnScanner;

/**
 * Extension of {@link AbstractFieldBuilder} that adds the {@link GridSingleSelect &#64;FieldBuilder.GridSingleSelect}
 * and {@link GridSingleSelect &#64;FieldBuilder.GridMultiSelect} annotations.
 *
 * <p>
 * These annotations require special handling because they are not normal fields.
 *
 * @see EnumComboBox
 */
public class AbstractGridFieldBuilder<S extends AbstractGridFieldBuilder<S, T>, T> extends AbstractFieldBuilder<S, T> {

    private static final long serialVersionUID = -4639701993755627170L;

    /**
     * Constructor.
     *
     * @param type backing object type
     */
    protected AbstractGridFieldBuilder(Class<T> type) {
        super(type);
    }

    /**
     * Static information copy constructor.
     *
     * <p>
     * Only the static information gathered by this instance by scanning for annotations is copied.
     * Any previously bound fields are not copied.
     *
     * @param original original instance
     * @throws IllegalArgumentException if {@code original} is null
     */
    protected AbstractGridFieldBuilder(AbstractGridFieldBuilder<?, T> original) {
        super(original);
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link Grid}
     * converted to a single-select field via {@link Grid#asSingleSelect}.
     *
     * @see FieldBuilder
     * @see Grid#asSingleSelect
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface GridSingleSelect {

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see Grid#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see Grid#addThemeVariants(GridVariant[])
         */
        GridVariant[] addThemeVariants() default {};

        /**
         * Get the configuration for the {@link Grid} (single) column that is displayed.
         *
         * @return grid column configuration
         * @see GridColumn &#64;GridColumn
         */
        GridColumn column();

        /**
         * Get the class to instantiate as the data provider for the grid.
         *
         * @return desired data provider property value type
         * @see Grid#setItems(DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends DataProvider> items() default DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see Grid#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see Grid#setHeight(String)
         */
        String height() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see Grid#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see Grid#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see Grid#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see Grid#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see Grid#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code pageSize} property.
         *
         * @return desired {@code pageSize} property value
         * @see Grid#setPageSize(int)
         */
        int pageSize() default 50;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see SingleSelect#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see SingleSelect#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see Grid#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see Grid#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see Grid#setWidth(String)
         */
        String width() default "";
    }

    /**
     * Specifies how a Java bean property should be edited using a {@link Grid}
     * converted to a multi-select field via {@link Grid#asMultiSelect}.
     *
     * @see FieldBuilder
     * @see Grid#asMultiSelect
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface GridMultiSelect {

        /**
         * Add the specified class names.
         *
         * @return zero or more class names to add
         * @see Grid#addClassNames(String[])
         */
        String[] addClassNames() default {};

        /**
         * Add the specified theme variants.
         *
         * @return zero or more theme variants to add
         * @see Grid#addThemeVariants(GridVariant[])
         */
        GridVariant[] addThemeVariants() default {};

        /**
         * Get the configuration for the {@link Grid} (single) column that is displayed.
         *
         * @return grid column configuration
         * @see GridColumn &#64;GridColumn
         */
        GridColumn column();

        /**
         * Get the class to instantiate as the data provider for the grid.
         *
         * @return desired data provider property value type
         * @see Grid#setItems(DataProvider)
         */
        @SuppressWarnings("rawtypes")
        Class<? extends DataProvider> items() default DataProvider.class;

        /**
         * Get the value desired for the {@code enabled} property.
         *
         * @return desired {@code enabled} property value
         * @see Grid#setEnabled(boolean)
         */
        boolean enabled() default true;

        /**
         * Get the value desired for the {@code height} property.
         *
         * @return desired {@code height} property value
         * @see Grid#setHeight(String)
         */
        String height() default "";

        /**
         * Get the value desired for the {@code id} property.
         *
         * @return desired {@code id} property value
         * @see Grid#setId(String)
         */
        String id() default "";

        /**
         * Get the value desired for the {@code maxHeight} property.
         *
         * @return desired {@code maxHeight} property value
         * @see Grid#setMaxHeight(String)
         */
        String maxHeight() default "";

        /**
         * Get the value desired for the {@code maxWidth} property.
         *
         * @return desired {@code maxWidth} property value
         * @see Grid#setMaxWidth(String)
         */
        String maxWidth() default "";

        /**
         * Get the value desired for the {@code minHeight} property.
         *
         * @return desired {@code minHeight} property value
         * @see Grid#setMinHeight(String)
         */
        String minHeight() default "";

        /**
         * Get the value desired for the {@code minWidth} property.
         *
         * @return desired {@code minWidth} property value
         * @see Grid#setMinWidth(String)
         */
        String minWidth() default "";

        /**
         * Get the value desired for the {@code pageSize} property.
         *
         * @return desired {@code pageSize} property value
         * @see Grid#setPageSize(int)
         */
        int pageSize() default 50;

        /**
         * Get the value desired for the {@code readOnly} property.
         *
         * @return desired {@code readOnly} property value
         * @see MultiSelect#setReadOnly(boolean)
         */
        boolean readOnly() default false;

        /**
         * Get the value desired for the {@code requiredIndicatorVisible} property.
         *
         * @return desired {@code requiredIndicatorVisible} property value
         * @see MultiSelect#setRequiredIndicatorVisible(boolean)
         */
        boolean requiredIndicatorVisible() default false;

        /**
         * Get the value desired for the {@code tabIndex} property.
         *
         * @return desired {@code tabIndex} property value
         * @see Grid#setTabIndex(int)
         */
        int tabIndex() default 0;

        /**
         * Get the value desired for the {@code visible} property.
         *
         * @return desired {@code visible} property value
         * @see Grid#setVisible(boolean)
         */
        boolean visible() default true;

        /**
         * Get the value desired for the {@code width} property.
         *
         * @return desired {@code width} property value
         * @see Grid#setWidth(String)
         */
        String width() default "";
    }

    // Add special handling for @GridSingleSelect and @GridMultiSelect
    @Override
    protected <A extends Annotation> BoundField buildDeclarativeField(MethodAnnotationScanner<T, A>.MethodInfo methodInfo) {

        // Gather info
        final Method method = methodInfo.getMethod();
        final A annotation = methodInfo.getAnnotation();
        final Class<? extends Annotation> annotationType = annotation.annotationType();

        // Check for our two custom types @GridSingleSelect and @GridMultiSelect
        if (annotationType.equals(GridSingleSelect.class)) {
            final Grid<?> grid = this.buildGrid(method, annotation, ((GridMultiSelect)annotation).column());
            grid.setSelectionMode(Grid.SelectionMode.SINGLE);
            return new BoundField(grid.asSingleSelect(), grid);
        }
        if (annotationType.equals(GridMultiSelect.class)) {
            final Grid<?> grid = this.buildGrid(method, annotation, ((GridMultiSelect)annotation).column());
            grid.setSelectionMode(Grid.SelectionMode.MULTI);
            return new BoundField(grid.asMultiSelect(), grid);
        }

        // Do the normal thing
        return super.buildDeclarativeField(methodInfo);
    }

    protected Grid<?> buildGrid(Method method, Annotation annotation, GridColumn column) {

        // Create grid
        final Grid<?> grid = new Grid<>(method.getReturnType());

        // Apply annotation values
        AnnotationUtil.applyAnnotationValues(grid, "set", annotation, null, (methodList, propertyName) -> propertyName);
        AnnotationUtil.applyAnnotationValues(grid, "add", annotation, null,
          (methodList, propertyName) -> methodList.get(0).getName());

        // Add a single column configured via @GridColumn
        final String description = "@" + annotation.annotationType().getSimpleName() + ".column() on method " + method;
        GridColumnScanner.addColumn(grid, "key", column, description, v -> v, false);

        // Done
        return grid;
    }
}
