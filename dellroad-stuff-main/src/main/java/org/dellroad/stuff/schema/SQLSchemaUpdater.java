
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

/**
 * Concrete extension of {@link AbstractSchemaUpdater} for SQL databases.
 *
 * <p>
 * Required properties are the {@linkplain #setDatabaseInitialization database initialization},
 * {@linkplain #setUpdateTableInitialization update table initialization}, and the {@linkplain #setUpdates updates} themselves.
 *
 * <p>
 * Applied updates are recorded in a special <i>update table</i>, which contains two columns: one for the unique
 * {@linkplain SchemaUpdate#getName update name} and one for a timestamp. The update table and column names
 * are configurable via {@link #setUpdateTableName setUpdateTableName()},
 * {@link #setUpdateTableNameColumn setUpdateTableNameColumn()}, and {@link #setUpdateTableTimeColumn setUpdateTableTimeColumn()}.
 *
 * <p>
 * By default, this class detects a completely uninitialized database by the absence of the update table itself
 * in the schema (see {@link #databaseNeedsInitialization databaseNeedsInitialization()}).
 * When an uninitialized database is encountered, the configured {@linkplain #setDatabaseInitialization database initialization}
 * and {@linkplain #setUpdateTableInitialization update table initialization} actions are applied first to initialize
 * the database schema.
 */
public class SQLSchemaUpdater extends AbstractSchemaUpdater<DataSource, Connection> {

    /**
     * Default name of the table that tracks schema updates, <code>{@value}</code>.
     */
    public static final String DEFAULT_UPDATE_TABLE_NAME = "SchemaUpdate";

    /**
     * Default name of the column in the updates table holding the unique update name, <code>{@value}</code>.
     */
    public static final String DEFAULT_UPDATE_TABLE_NAME_COLUMN = "updateName";

    /**
     * Default name of the column in the updates table holding the update's time applied, <code>{@value}</code>.
     */
    public static final String DEFAULT_UPDATE_TABLE_TIME_COLUMN = "updateTime";

    private int transactionIsolation = Connection.TRANSACTION_SERIALIZABLE;
    private String updateTableName = DEFAULT_UPDATE_TABLE_NAME;
    private String updateTableNameColumn = DEFAULT_UPDATE_TABLE_NAME_COLUMN;
    private String updateTableTimeColumn = DEFAULT_UPDATE_TABLE_TIME_COLUMN;

    private SQLCommandList databaseInitialization;
    private SQLCommandList updateTableInitialization;

    /**
     * Get the name of the table that keeps track of applied updates.
     *
     * @return the name of the update table
     * @see #setUpdateTableName setUpdateTableName()
     */
    public String getUpdateTableName() {
        return this.updateTableName;
    }

    /**
     * Set the name of the table that keeps track of applied updates.
     * Default value is {@link #DEFAULT_UPDATE_TABLE_NAME}.
     *
     * <p>
     * This name must be consistent with the {@linkplain #setUpdateTableInitialization update table initialization}.
     *
     * @param updateTableName the name of the update table
     */
    public void setUpdateTableName(String updateTableName) {
        this.updateTableName = updateTableName;
    }

    /**
     * Get the name of the update name column in the table that keeps track of applied updates.
     *
     * @return the name of the name column in the update table
     * @see #setUpdateTableNameColumn setUpdateTableNameColumn()
     */
    public String getUpdateTableNameColumn() {
        return this.updateTableNameColumn;
    }

    /**
     * Set the name of the update name column in the table that keeps track of applied updates.
     * Default value is {@link #DEFAULT_UPDATE_TABLE_NAME_COLUMN}.
     *
     * <p>
     * This name must be consistent with the {@linkplain #setUpdateTableInitialization update table initialization}.
     *
     * @param updateTableNameColumn the name of the name column in the update table
     */
    public void setUpdateTableNameColumn(String updateTableNameColumn) {
        this.updateTableNameColumn = updateTableNameColumn;
    }

    /**
     * Get the name of the update timestamp column in the table that keeps track of applied updates.
     *
     * @return the name of the timestamp column in the update table
     * @see #setUpdateTableTimeColumn setUpdateTableTimeColumn()
     */
    public String getUpdateTableTimeColumn() {
        return this.updateTableTimeColumn;
    }

    /**
     * Set the name of the update timestamp column in the table that keeps track of applied updates.
     * Default value is {@link #DEFAULT_UPDATE_TABLE_TIME_COLUMN}.
     *
     * <p>
     * This name must be consistent with the {@linkplain #setUpdateTableInitialization update table initialization}.
     *
     * @param updateTableTimeColumn the name of the timestamp column in the update table
     */
    public void setUpdateTableTimeColumn(String updateTableTimeColumn) {
        this.updateTableTimeColumn = updateTableTimeColumn;
    }

    /**
     * Get the update table initialization.
     *
     * @return SQL commands to create the update table
     * @see #setUpdateTableInitialization setUpdateTableInitialization()
     */
    public SQLCommandList getUpdateTableInitialization() {
        return this.updateTableInitialization;
    }

    /**
     * Configure how the update table itself gets initialized. This update is run when no update table found,
     * which (we assume) implies an empty database with no tables or content. This is a required property.
     *
     * <p>
     * This initialization should create the update table where the name column is the primary key.
     * The name column must have a length limit greater than or equal to the longest schema update name.
     *
     * <p>
     * The table and column names must be consistent with the values configured via
     * {@link #setUpdateTableName setUpdateTableName()}, {@link #setUpdateTableNameColumn setUpdateTableNameColumn()},
     * and {@link #setUpdateTableTimeColumn setUpdateTableTimeColumn()}.
     *
     * <p>
     * For convenience, pre-defined initialization scripts using the default table and column names are available
     * at the following resource locations. These can be used to configure a {@link SQLCommandList}:
     * <table border="1" cellspacing="0" cellpadding="4" summary="Pre-Defined Initialization Scripts">
     * <tr>
     * <th>Database</th>
     * <th>Resource</th>
     * </tr>
     * <tr>
     * <td>MySQL (InnoDB)</td>
     * <td><code>classpath:org/dellroad/stuff/schema/updateTable-mysql.sql</code></td>
     * </tr>
     * <tr>
     * <td>Oracle</td>
     * <td><code>classpath:org/dellroad/stuff/schema/updateTable-oracle.sql</code></td>
     * </tr>
     * <tr>
     * <td>Postgres</td>
     * <td><code>classpath:org/dellroad/stuff/schema/updateTable-postgres.sql</code></td>
     * </tr>
     * <tr>
     * <td>HSQLDB</td>
     * <td><code>classpath:org/dellroad/stuff/schema/updateTable-hsqldb.sql</code></td>
     * </tr>
     * </table>
     *
     * @param updateTableInitialization update table schema initialization
     * @see #setUpdateTableName setUpdateTableName()
     * @see #setUpdateTableNameColumn setUpdateTableNameColumn()
     * @see #setUpdateTableTimeColumn setUpdateTableTimeColumn()
     */
    public void setUpdateTableInitialization(SQLCommandList updateTableInitialization) {
        this.updateTableInitialization = updateTableInitialization;
    }

    /**
     * Get the empty database initialization.
     *
     * @return SQL commands to initialize an empty database
     * @see #setDatabaseInitialization setDatabaseInitialization()
     */
    public SQLCommandList getDatabaseInitialization() {
        return this.databaseInitialization;
    }

    /**
     * Configure how an empty database gets initialized. This is a required property.
     *
     * <p>
     * This update is run when no update table found, which (we assume) implies an empty database with no tables or content.
     * Typically this contains the SQL script that gets automatically generated by your favorite schema generation tool.
     *
     * <p>
     * This script is expected to initialize the database schema (i.e., creating all the tables) so that
     * when completed the database is "up to date" with respect to the configured schema updates.
     * That is, when this action completes, we assume all updates have already been (implicitly) applied
     * (and they will be recorded as such).
     *
     * <p>
     * Note this script is <i>not</i> expected to create the update table that tracks schema updates;
     * that function is handled by the {@linkplain #setUpdateTableInitialization update table initialization}.
     *
     * @param databaseInitialization application database schema initialization
     */
    public void setDatabaseInitialization(SQLCommandList databaseInitialization) {
        this.databaseInitialization = databaseInitialization;
    }

    /**
     * Get transaction isolation level for the schema check/migration transaction.
     *
     * <p>
     * Default is {@link Connection#TRANSACTION_SERIALIZABLE}.
     *
     * @return transaction isolation level, or -1 to leave it alone
     */
    public int getTransactionIsolation() {
        return this.transactionIsolation;
    }

    /**
     * Set transaction isolation level for the schema check/migration transaction.
     *
     * <p>
     * Default is {@link Connection#TRANSACTION_SERIALIZABLE}.
     *
     * @param transactionIsolation transaction isolation level, or -1 to leave it alone
     */
    public void setTransactionIsolation(final int transactionIsolation) {
        this.transactionIsolation = transactionIsolation;
    }

    @Override
    protected void apply(Connection c, DatabaseAction<Connection> action) throws SQLException {
        try {
            super.apply(c, action);
        } catch (SQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @throws SQLException if an update fails
     * @throws IllegalStateException if the database needs initialization and either the
     *  {@linkplain #setDatabaseInitialization database initialization} or
     *  the {@linkplain #setUpdateTableInitialization update table initialization} has not been configured
     * @throws IllegalStateException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    public synchronized void initializeAndUpdateDatabase(DataSource dataSource) throws SQLException {
        try {
            super.initializeAndUpdateDatabase(dataSource);
        } catch (SQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Begin a transaction on the given connection.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} creates a serializable-level transaction.
     *
     * @param dataSource the database on which to open the transaction
     * @return new {@link Connection} with an open transaction
     * @throws SQLException if an error occurs while accessing the database
     */
    @Override
    protected Connection openTransaction(DataSource dataSource) throws SQLException {
        Connection c = dataSource.getConnection();
        if (this.transactionIsolation != -1)
            c.setTransactionIsolation(this.transactionIsolation);
        c.setAutoCommit(false);
        return c;
    }

    /**
     * Commit a previously opened transaction.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} just invokes {@link Connection#commit}.
     *
     * @param c the connection on which to commit the transaction
     * @throws SQLException if an error occurs while accessing the database
     */
    @Override
    protected void commitTransaction(Connection c) throws SQLException {
        c.commit();
        c.close();
    }

    /**
     * Roll back a previously opened transaction.
     * This method will also be invoked if {@link #commitTransaction commitTransaction()} throws an exception.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} just invokes {@link Connection#rollback}.
     *
     * @param c the connection on which to roll back the transaction
     * @throws SQLException if an error occurs while accessing the database
     */
    @Override
    protected void rollbackTransaction(Connection c) throws SQLException {
        c.rollback();
        c.close();
    }

    /**
     * Determine if the database needs initialization.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} simply invokes <code>SELECT COUNT(*) FROM <i>UPDATETABLE</i></code>
     * and checks for success or failure. If an exception is thrown, {@link #indicatesUninitializedDatabase} is used
     * to distinguish between an exception caused by an uninitialized database and a truly unexpected one.
     *
     * @param c connection to the database
     * @throws SQLException if an unexpected error occurs while accessing the database
     */
    @Override
    protected boolean databaseNeedsInitialization(Connection c) throws SQLException {
        final boolean[] result = new boolean[1];
        this.apply(c, new SQLCommand("SELECT COUNT(*) FROM " + this.getUpdateTableName()) {
            @Override
            public void apply(Connection c) throws SQLException {
                try (Statement s = c.createStatement()) {
                    ResultSet resultSet;
                    try {
                        resultSet = s.executeQuery(this.getSQL());
                    } catch (SQLException e) {
                        if (SQLSchemaUpdater.this.indicatesUninitializedDatabase(c, e)) {
                            SQLSchemaUpdater.this.log.info("detected an uninitialized database");
                            result[0] = true;
                            return;
                        }
                        throw e;
                    }
                    if (!resultSet.next())
                        throw new IllegalStateException("zero rows returned by `" + this.getSQL() + "'");
                    SQLSchemaUpdater.this.log.info("detected initialized database, with "
                      + resultSet.getLong(1) + " update(s) already applied");
                }
            }
        });
        return result[0];
    }

    /**
     * Determine if an exception thrown during {@link #databaseNeedsInitialization} is consistent with
     * an uninitialized database.
     *
     * <p>
     * This should return true if the exception would be thrown by an SQL query that attempts to access a non-existent table.
     * For exceptions thrown by other causes, this should return false.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} always returns true. Subclasses are encouraged to override
     * with a more precise implementation.
     *
     * @param c connection on which the exception occurred
     * @param e exception thrown during database access in {@link #databaseNeedsInitialization}
     * @return true if {@code e} indicates an uninitialized database
     * @throws SQLException if an error occurs
     * @see #databaseNeedsInitialization
     */
    protected boolean indicatesUninitializedDatabase(Connection c, SQLException e) throws SQLException {
        return true;
    }

    /**
     * Record an update as having been applied.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} does the standard JDBC thing using an INSERT statement
     * into the update table.
     *
     * @param c SQL connection
     * @param updateName update name
     * @throws IllegalStateException if the update has already been recorded in the database
     * @throws SQLException if an error occurs while accessing the database
     */
    @Override
    protected void recordUpdateApplied(Connection c, final String updateName) throws SQLException {
        this.apply(c, new SQLCommand("INSERT INTO " + this.getUpdateTableName()
          + " (" + this.getUpdateTableNameColumn() + ", " + this.getUpdateTableTimeColumn() + ") VALUES (?, ?)") {
            @Override
            public void apply(Connection c) throws SQLException {
                try (PreparedStatement s = c.prepareStatement(this.getSQL())) {
                    s.setString(1, updateName);
                    s.setTimestamp(2, new Timestamp(new Date().getTime()));
                    int rows = s.executeUpdate();
                    if (rows != 1)
                        throw new IllegalStateException("got " + rows + " != 1 rows for `" + this.getSQL() + "'");
                }
            }
        });
    }

    /**
     * Determine which updates have already been applied.
     *
     * <p>
     * The implementation in {@link SQLSchemaUpdater} does the standard JDBC thing using a SELECT statement
     * from the update table.
     *
     * @throws SQLException if an error occurs while accessing the database
     */
    @Override
    protected Set<String> getAppliedUpdateNames(Connection c) throws SQLException {
        final HashSet<String> updateNames = new HashSet<>();
        this.apply(c, new SQLCommand("SELECT " + this.getUpdateTableNameColumn() + " FROM " + this.getUpdateTableName()) {
            @Override
            public void apply(Connection c) throws SQLException {
                try (Statement s = c.createStatement()) {
                    for (ResultSet resultSet = s.executeQuery(this.getSQL()); resultSet.next(); )
                        updateNames.add(resultSet.getString(1));
                }
            }
        });
        return updateNames;
    }

    // Initialize the database
    @Override
    protected void initializeDatabase(Connection c) throws SQLException {

        // Sanity check
        if (this.getDatabaseInitialization() == null)
            throw new IllegalArgumentException("database needs initialization but no database initialization is configured");
        if (this.getUpdateTableInitialization() == null)
            throw new IllegalArgumentException("database needs initialization but no update table initialization is configured");

        // Initialize application schema
        this.log.info("intializing database schema");
        this.apply(c, this.getDatabaseInitialization());

        // Initialize update table
        this.log.info("intializing update table");
        this.apply(c, this.getUpdateTableInitialization());
    }
}

