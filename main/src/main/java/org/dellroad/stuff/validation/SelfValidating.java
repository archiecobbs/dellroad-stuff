
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import jakarta.validation.ConstraintValidatorContext;

/**
 * Implemented by classes that can validate themselves. Such classes should be annotated with
 * the {@link SelfValidates @SelfValidates} constraint.
 *
 * @see SelfValidates
 */
public interface SelfValidating {

    /**
     * Validate this instance.
     *
     * @param context validation context
     * @throws SelfValidationException to indicate this instance is invalid
     */
    void checkValid(ConstraintValidatorContext context) throws SelfValidationException;
}
