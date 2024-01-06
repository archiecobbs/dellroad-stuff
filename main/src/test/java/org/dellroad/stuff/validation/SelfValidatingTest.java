
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import jakarta.validation.ConstraintValidatorContext;

import org.dellroad.stuff.test.TestSupport;
import org.testng.annotations.Test;

@SelfValidates(message = SelfValidatingTest.MESSAGE)
public class SelfValidatingTest extends TestSupport implements SelfValidating {

    public static final String MESSAGE = "validation failed";

    private boolean valid;

    @Test
    public void testValidation() {
        this.valid = true;
        checkValid(this, true);
        this.valid = false;
        checkValid(this, false);
    }

    @Override
    public void checkValid(ConstraintValidatorContext context) throws SelfValidationException {
        if (!this.valid)
            throw new SelfValidationException(MESSAGE);
    }
}
