
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.schema;

import java.sql.SQLTransientException;

/**
 * Exception thrown by an {@link AbstractUpdatingDataSource} operating in asynchronous mode when
 * {@link AbstractUpdatingDataSource#getConnection getConnection()} is invoked while an update is still in progress.
 */
@SuppressWarnings("serial")
public class UpdateInProgressException extends SQLTransientException {

    public UpdateInProgressException() {
    }

    public UpdateInProgressException(String message) {
        super(message);
    }
}

