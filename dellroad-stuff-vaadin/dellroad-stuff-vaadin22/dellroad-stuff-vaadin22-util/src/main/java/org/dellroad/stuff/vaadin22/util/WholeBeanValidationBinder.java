
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.util;

import com.vaadin.flow.data.binder.BeanValidationBinder;

/**
 * A {@link BeanValidationBinder} which also validates any bean validation constraints imposed on the bean itself
 * (not to an individual property).
 *
 * <p>
 * This is done simply by adding a {@link WholeBeanValidator}.
 *
 * <p>
 * For this binder to validate, there must be an actual bean bound (via {@link #setBean setBean()}), otherwise you'll
 * get an {@link IllegalStateException} with <i>bean level validators have been configured but no bean is currently set</i>.
 *
 * @param <T> bean type
 *
 * @see WholeBeanValidator
 */
@SuppressWarnings("serial")
public class WholeBeanValidationBinder<T> extends BeanValidationBinder<T> {

// Constructors

    /**
     * Constructor.
     *
     * @param beanType bean type
     * @throws NullPointerException if {@code beanType} is null
     */
    public WholeBeanValidationBinder(Class<T> beanType) {
        super(beanType);
        this.withValidator(new WholeBeanValidator(beanType));
    }

    /**
     * Constructor.
     *
     * @param beanType bean type
     * @param scanNestedDefinitions true to scan for nested property definitions
     * @throws NullPointerException if {@code beanType} is null
     */
    public WholeBeanValidationBinder(Class<T> beanType, boolean scanNestedDefinitions) {
        super(beanType, scanNestedDefinitions);
        this.withValidator(new WholeBeanValidator(beanType));
    }
}
