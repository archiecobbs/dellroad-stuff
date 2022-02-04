
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component.grid;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;

import java.util.Optional;

/**
 * A "do nothing" {@link ComponentRenderer} used to render property values that are already {@link Component}'s.
 *
 * <p>
 * This becomes the default {@link GridColumn#renderer renderer()} for {@link GridColumn &#64;GridColumn} annotations
 * on methods that return sub-types of {@link Component}:
 * <blockquote><pre>
 * public class User {
 *
 *     // Render username normally - no special Renderer is used
 *     <b>&#64;GridColumn(header = "Username", width = "50px")</b>
 *     public String getUsername() { ... }
 *
 *     // Render foobar with a custom renderer
 *     <b>&#64;GridColumn(header = "Foobar", width = "64px", renderer = FoobarRenderer.class)</b>
 *     public Foobar getFoobar() { ... }
 *
 *     // Render status with an Image - as if we had said "renderer = SelfRenderer.class"
 *     <b>&#64;GridColumn(header = "Status", width = "64px")</b>
 *     public Image getStatus() { ... }
 * }
 * </pre></blockquote>
 *
 * <p>
 * This class adds graceful handling of null values; null values are converted into empty {@link Text} components.
 *
 * @param <T> the type of the input model object
 * @see GridColumn
 * @see GridColumnScanner
 */
@SuppressWarnings("serial")
public class SelfRenderer<T> extends ComponentRenderer<Component, T> {

    /**
     * Constructor.
     *
     * @param valueProvider creates a component from a model instance
     * @throws IllegalArgumentException if {@code valueProvider} is null
     */
    public SelfRenderer(final ValueProvider<? super T, ? extends Component> valueProvider) {
        super(new SerializableFunction<T, Component>() {
            @Override
            public Component apply(T obj) {
                return Optional.ofNullable(obj)
                  .<Component>map(valueProvider::apply)
                  .orElseGet(() -> new Text(""));
            }
        });
        if (valueProvider == null)
            throw new IllegalArgumentException("null valueProvider");
    }
}
