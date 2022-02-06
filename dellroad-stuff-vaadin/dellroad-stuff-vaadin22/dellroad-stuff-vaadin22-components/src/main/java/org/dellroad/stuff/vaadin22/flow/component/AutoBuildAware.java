
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.flow.component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Implemented by widget classes that want to be notified if/when they have been constructed automatically
 * from an annotation on a method.
 */
public interface AutoBuildAware {

    /**
     * Notify that this instance was instantiated automatically based on an annotated method.
     *
     * @param builder notifier (likely {@link org.dellroad.stuff.vaadin22.flow.component.fieldbuilder.FieldBuilder})
     * @param method the method that was annotated
     * @param annotation the annotation that was found on the method
     */
    void onAutoBuild(Object builder, Method method, Annotation annotation);
}
