
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.schema;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * A {@link DataSource} that wraps an inner {@link DataSource} and automatically applies a configured
 * {@link SQLCommandList} on first access.
 *
 * @see SQLCommandList
 */
public class UpdatingDataSource extends AbstractUpdatingDataSource {

    private SQLCommandList action;
    private boolean transactional = true;

    /**
     * Configure the {@link SQLCommandList} to be applied to the database on first access. Required property.
     *
     * @param action SQL command(s) to apply exactly once to the underlying {@link DataSource}
     */
    public void setSQLCommandList(SQLCommandList action) {
        this.action = action;
    }

    /**
     * Configure whether the {@link SQLCommandList} is applied transactionally or not.
     * Default is {@code true}.
     *
     * @param transactional true for transactional application of SQL command(s)
     */
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    @Override
    protected void updateDataSource(DataSource dataSource) throws SQLException {

        // Sanity check
        if (this.action == null)
            throw new IllegalArgumentException("no SQLCommandList configured");

        // Get connection
        try (Connection c = dataSource.getConnection()) {
            boolean tx = this.transactional;
            try {

                // Open transaction if so configured
                if (tx) {
                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                    c.setAutoCommit(false);
                }

                // Apply SQL command(s)
                this.action.apply(c);

                // Commit transaction
                if (tx)
                    c.commit();
                tx = false;
            } finally {
                if (tx)
                    c.rollback();
            }
        }
    }
}
