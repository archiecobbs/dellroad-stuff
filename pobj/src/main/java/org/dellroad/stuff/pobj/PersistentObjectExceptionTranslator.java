
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * {@link PersistentObject} implementation of Spring's {@link PersistenceExceptionTranslator} interface.
 */
public class PersistentObjectExceptionTranslator implements PersistenceExceptionTranslator {

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException e) {
        if (e instanceof PersistentObjectVersionException)
            return new OptimisticLockingFailureException("optimistic locking failure: " + e.getMessage(), e);
        if (e instanceof PersistentObjectValidationException)
            return new DataIntegrityViolationException("validation failure: " + e.getMessage(), e);
        return null;
    }
}

