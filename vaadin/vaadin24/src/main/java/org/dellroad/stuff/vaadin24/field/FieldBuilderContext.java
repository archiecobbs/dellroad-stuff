
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.google.common.reflect.TypeToken;
import com.vaadin.flow.data.provider.DataProvider;

import java.io.Serializable;
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
public interface FieldBuilderContext extends Serializable {

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
    default Class<?> inferDataModelType() {

        // Get method return type
        Class<?> returnType = this.getMethod().getReturnType();
        Type modelType = this.getMethod().getGenericReturnType();

        // If it's a collection type, drill down to get the element type
        if (Collection.class.isAssignableFrom(returnType)) {
            try {
                modelType = ((ParameterizedType)modelType).getActualTypeArguments()[0];
            } catch (ClassCastException | ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException(String.format(
                  "can't determine collection element type from return type of method %s in the context of %s",
                  this.getMethod(), this.getBeanType()), e);
            }
        }

//        // Debug
//        org.slf4j.LoggerFactory.getLogger(this.getClass()).info("inferDataModelType():"
//          + "\n  method={}"
//          + "\n  returnType={}"
//          + "\n  beanType={}"
//          + "\n  modelType1={}"
//          + "\n  modelType2={}"
//          + "\n  resolvedType={}"
//          + "\n  result={}",
//          this.getMethod(),
//          returnType,
//          this.getBeanType(),
//          this.getMethod().getGenericReturnType(),
//          modelType,
//          TypeToken.of(this.getBeanType()).resolveType(modelType),
//          TypeToken.of(this.getBeanType()).resolveType(modelType).getRawType());

        // Resolve any type variables in the context of the bean's type, then return the raw type equivalent
        return TypeToken.of(this.getBeanType()).resolveType(modelType).getRawType();
    }
}
