
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.field;

import com.google.common.base.Preconditions;

/**
 * Straightforward implementation of the {@link FieldBuilderContext} interface.
 */
@SuppressWarnings("serial")
public class FieldBuilderContextImpl implements FieldBuilderContext {

    private static final long serialVersionUID = -4636811655407064538L;

    protected final AbstractFieldBuilder<?, ?>.BindingInfo bindingInfo;

    /**
     * Constructor.
     *
     * @param bindingInfo static information for the bean property
     * @throws IllegalArgumentException if {@code bindingInfo} is null
     */
    public FieldBuilderContextImpl(AbstractFieldBuilder<?, ?>.BindingInfo bindingInfo) {
        Preconditions.checkArgument(bindingInfo != null, "null bindingInfo");
        this.bindingInfo = bindingInfo;
    }

    @Override
    public AbstractFieldBuilder<?, ?>.BindingInfo getBindingInfo() {
        return this.bindingInfo;
    }

    @Override
    public Class<?> getBeanType() {
        return this.bindingInfo.getFieldBuilder().getType();
    }

    @Override
    public String toString() {
        return String.format("%s[info=%s,beanType=%s]", this.getClass().getSimpleName(), this.getBindingInfo(), this.getBeanType());
    }
}
