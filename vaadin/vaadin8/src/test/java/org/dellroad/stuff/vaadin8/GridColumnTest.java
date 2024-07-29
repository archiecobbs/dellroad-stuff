
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.shared.ui.grid.ColumnState;
import com.vaadin.ui.Grid;
import com.vaadin.ui.renderers.TextRenderer;

import java.lang.reflect.Method;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GridColumnTest extends TestSupport {

    @Test
    @SuppressWarnings("unchecked")
    public void test1() throws Exception {
        final Grid<Foo> grid = new GridColumnScanner<>(Foo.class).buildGrid();

        final Grid.Column<Foo, ?> col = grid.getColumn("field1");

        Assert.assertEquals(col.getCaption(), "caption1");
        Assert.assertEquals(col.getMaximumWidth(), 23.5);
        Assert.assertEquals(col.isHidden(), true);

        final Method m1 = Grid.Column.class.getDeclaredMethod("getState");
        m1.setAccessible(true);
        final ColumnState cs = (ColumnState)m1.invoke(col);
        Assert.assertEquals(cs.renderer.getClass(), MyRenderer.class);
    }

// Classes

    public abstract class Foo {

        @GridColumn(caption = "caption1", maximumWidth = 23.5, hidden = true, renderer = MyRenderer.class)
        public abstract String getField1();
        public abstract void setField1(String field1);
    }

    @SuppressWarnings("serial")
    public static class MyRenderer extends TextRenderer {
    }
}

