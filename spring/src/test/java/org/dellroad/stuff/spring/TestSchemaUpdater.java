
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.spring;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.dellroad.stuff.schema.SQLCommandList;

public class TestSchemaUpdater extends SpringSQLSchemaUpdater {

    public static final String PLACEHOLDER_SQL = "sql-statement-placeholder";

    private boolean databaseNeedsInitialization;

    private final TestDatabaseAction databaseInitialization = new TestDatabaseAction();
    private final TestDatabaseAction updateTableInitialization = new TestDatabaseAction();
    private final ArrayList<String> updatesRecorded = new ArrayList<>();

    private Set<String> previousUpdates;

    @Override
    public SQLCommandList getDatabaseInitialization() {
        return this.databaseInitialization;
    }

    @Override
    public SQLCommandList getUpdateTableInitialization() {
        return this.updateTableInitialization;
    }

    public void checkInitialization() {
        int expectedCount = this.databaseNeedsInitialization ? 1 : 0;
        this.databaseInitialization.checkCount(expectedCount);
        this.updateTableInitialization.checkCount(expectedCount);
    }

    @Override
    public boolean databaseNeedsInitialization(Connection c) {
        return this.databaseNeedsInitialization;
    }
    public void setDatabaseNeedsInitialization(boolean databaseNeedsInitialization) {
        this.databaseNeedsInitialization = databaseNeedsInitialization;
    }

    @Override
    protected void recordUpdateApplied(Connection c, String name) throws SQLException {
        this.updatesRecorded.add(name);
    }

    @Override
    protected Set<String> getAppliedUpdateNames(Connection c) throws SQLException {
        return this.previousUpdates;
    }

    public List<String> getUpdatesRecorded() {
        return this.updatesRecorded;
    }

    public void setPreviousUpdates(Set<String> previousUpdates) {
        this.previousUpdates = previousUpdates;
    }
}
