
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.component;

import com.google.common.reflect.TypeToken;
import com.vaadin.flow.data.provider.DataProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Provides context when fields are instantiated automatically based on method annotations.
 *
 * <p>
 * Field classes can declare constructors taking one of these in order to be given more context
 * during construction.
 *
 * <p>
 * @see org.dellroad.stuff.vaadin22.fieldbuilder.FieldBuilder
 */
public interface AutoBuildContext {

    /**
     * Get the thing that is instantiating the field (most likely a
     * {@link org.dellroad.stuff.vaadin22.fieldbuilder.FieldBuilder}).
     *
     * @return builder
     */
    Object getBuilder();

    /**
     * Get the bean type for which the field is being built.
     *
     * <p>
     * This will either be the declaring class of {@link #getMethod}, or some
     * supertype if the method is inherited.
     *
     * @return the method having the annotation on which the new field is based
     */
    Class<?> getBeanType();

    /**
     * Get the method that was annotated.
     *
     * @return the method having the annotation on which the new field is based
     */
    Method getMethod();

    /**
     * Get the annotation that was used to create the field.
     *
     * @return field annotation
     */
    Annotation getAnnotation();

    /**
     * Determine the {@link DataProvider} data model type from the given method's return type.
     *
     * <p>
     * This information is needed in order to create certain fields that use {@link DataProvider}'s.
     *
     * <p>
     * The default implementation in {@link AutoBuildContext} assumes the data model type is the method's
     * return type unless the return type is a sub-type of {@link Collection}, in which case the data model
     * type is the collection's element type.
     *
     * <p>
     * Note: this method is currently broken for {@link Collection} return types with generic type
     * parameter element types. Fix by using Guava's TypeToken.
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
