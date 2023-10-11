
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.spring;

import java.sql.Connection;
import java.sql.SQLException;

import org.dellroad.stuff.schema.SQLCommandList;
import org.testng.Assert;

public class TestDatabaseAction extends SQLCommandList {

    public static final SQLException TEST_EXCEPTION = new SQLException("test exception");

    private final boolean fail;
    private int count;

    public TestDatabaseAction() {
        this(false);
    }

    public TestDatabaseAction(boolean fail) {
        super("test sql");
        this.fail = fail;
    }

    @Override
    public final void apply(Connection c) throws SQLException {
        if (this.fail)
            throw TEST_EXCEPTION;
        this.count++;
    }

    public int getCount() {
        return this.count;
    }

    public void checkCount(int count) {
        Assert.assertEquals(count, this.count);
    }
}

