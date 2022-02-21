
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.field;

import com.google.common.reflect.TypeToken;
import com.vaadin.flow.data.provider.DataProvider;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Provides context when fields are instantiated automatically based on method annotations
 * by an {@link AbstractFieldBuilder}.
 *
 * <p>
 * Fields and other related classes (data providers, renderers, etc.) can declare constructors
 * taking one of these in order to be provided with more context during construction.
 *
 * <p>
 * @see FieldBuilder
 */
public interface FieldBuilderContext {

    /**
     * Get the static information (annotations, annotated method, etc.) associated with this context.
     *
     * @return builder
     */
    AbstractFieldBuilder<?, ?>.BindingInfo getBindingInfo();

    /**
     * Get the method that was annotated.
     *
     * <p>
     * The implementation in {@link FieldBuilderContext} just returns
     * {@link #getBindingInfo #getBindingInfo()}{@code .}{@link AbstractFieldBuilder.BindingInfo#getMethod getMethod()}.
     *
     * @return the method having the annotation on which the new field is based
     */
    default Method getMethod() {
        return this.getBindingInfo().getMethod();
    }

    /**
     * Get the bean type for which the field is being built.
     *
     * <p>
     * This will either be the declaring class (or interface) of
     * {@linkplain AbstractFieldBuilder.BindingInfo#getMethod the annotated method},
     * or possibly a subclass of that class if the annotated method is declared in
     * a supertype.
     *
     * @return the method having the annotation on which the new field is based
     */
    Class<?> getBeanType();

    /**
     * Determine the {@link DataProvider} data model type from the annotated method's return type.
     *
     * <p>
     * This information is needed in order to automatically create certain {@link DataProvider}'s
     * that need to know the data model type.
     *
     * <p>
     * The default implementation in {@link FieldBuilderContext} assumes the data model type is the method's
     * return type, unless the return type is a sub-type of {@link Collection}, in which case the data model
     * type is the collection's element type.
     */
    default Class<?> inferDataType() {
        Class<?> modelType = this.getMethod().getReturnType();
        if (Collection.class.isAssignableFrom(modelType)) {
            final Type elementType;
            try {
                final ParameterizedType returnType = (ParameterizedType)this.getMethod().getGenericReturnType();
                elementType = returnType.getActualTypeArguments()[0];
            } catch (ClassCastException | ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("can't determine data model type from non-generic return type of method "
                  + this.getMethod() + " in the context of " + this.getBeanType());
            }
            modelType = TypeToken.of(this.getBeanType()).resolveType(elementType).getRawType();
        }
        return modelType;
    }
}
