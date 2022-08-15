
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.grid;

import com.vaadin.flow.component.grid.ColumnPathRenderer;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.SortOrderProvider;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;

import java.util.Comparator;
import java.util.stream.Stream;

import org.dellroad.stuff.test.TestSupport;
import org.dellroad.stuff.vaadin23.util.SelfRenderer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GridColumnTest extends TestSupport {

    @Test
    @SuppressWarnings("unchecked")
    public void testGridColumns() throws Exception {

        // Build grid
        final Grid<Foo> grid = new Grid<>(Foo.class);

        // Remove and replace "field1" column with "key" and "field2"
        grid.removeColumnByKey("field1");
        new GridColumnScanner<>(Foo.class).addColumnsTo(grid);

        // Retrieve columns
        final Grid.Column<Foo> col1 = grid.getColumnByKey("key1");
        final Grid.Column<Foo> col2 = grid.getColumnByKey("field2");
        final Grid.Column<Foo> col3 = grid.getColumnByKey("field3");
        Assert.assertNotNull(col1);
        Assert.assertNotNull(col2);
        Assert.assertNotNull(col3);

        // Verify col1
        Assert.assertTrue(col1.getRenderer() instanceof MyRenderer);
        Assert.assertFalse(col1.isAutoWidth());
        Assert.assertTrue(col1.getClassNameGenerator() instanceof MyClassNameGenerator);
        Assert.assertEquals(col1.getFlexGrow(), 10);
        Assert.assertTrue(col1.isFrozen());
        Assert.assertEquals(col1.getId().get(), "id123");
        Assert.assertEquals(col1.getKey(), "key1");
        Assert.assertTrue(col1.isResizable());
        Assert.assertTrue(col1.isSortable());
        Assert.assertEquals(col1.getTextAlign(), ColumnTextAlign.CENTER);
        Assert.assertFalse(col1.isVisible());
        Assert.assertEquals(col1.getWidth(), "100px");

        // Verify defaults - col2 and col3 should be configured the same except for renderer and sorting
        Assert.assertEquals(col2.getRenderer().getClass(), SelfRenderer.class);
        Assert.assertEquals(col3.getRenderer().getClass(), ColumnPathRenderer.class);
        Assert.assertEquals(col2.isAutoWidth(), col3.isAutoWidth());
        Assert.assertEquals(col2.getClassNameGenerator(), col3.getClassNameGenerator());
//        Assert.assertEquals(col2.getComparator(SortDirection.ASCENDING).getClass(),
//          col3.getComparator(SortDirection.ASCENDING).getClass());
        Assert.assertEquals(col2.getEditorComponent(), col3.getEditorComponent());
        Assert.assertEquals(col2.getFlexGrow(), col3.getFlexGrow());
        Assert.assertEquals(col2.isFrozen(), col3.isFrozen());
        Assert.assertFalse(col2.getId().isPresent());
        Assert.assertEquals(col2.getKey(), "field2");
        Assert.assertEquals(col2.isResizable(), col3.isResizable());
//        Assert.assertEquals(col2.isSortable(), col3.isSortable());
        Assert.assertEquals(col2.getTextAlign(), col3.getTextAlign());
        Assert.assertEquals(col2.isVisible(), col3.isVisible());
        Assert.assertEquals(col2.getWidth(), col3.getWidth());
    }

// Classes

    public abstract static class Foo {

        @GridColumn(
          renderer = MyRenderer.class,
          autoWidth = false,
          classNameGenerator = MyClassNameGenerator.class,
          comparator = MyComparator.class,
          editorComponent = MyEditorComponent.class,
          flexGrow = 10,
          footer = "footer",
          frozen = true,
          header = "header",
          id = "id123",
          key = "key1",
          resizable = true,
          sortable = true,
          sortOrderProvider = MySortOrderProvider.class,
          sortProperties = { "key1", "field2" },
          textAlign = ColumnTextAlign.CENTER,
          visible = false,
          width = "100px",
          order = 2)
        public abstract String getField1();
        public abstract void setField1(String field1);

        @GridColumn(key = "field2", order = 1)
        public abstract Image buildField2();

        public abstract String getField3();
        public abstract void setField3(String field3);
    }

    @SuppressWarnings("serial")
    public static class MyRenderer extends ComponentRenderer<Label, Foo> {
        public MyRenderer(ValueProvider<Foo, ?> valueProvider) {
            super(() -> new Label());
        }
    }

    @SuppressWarnings("serial")
    public static class MyComparator implements Comparator<Foo> {
        @Override
        public int compare(Foo foo1, Foo foo2) {
            return 0;
        }
    }

    @SuppressWarnings("serial")
    public static class MyEditorComponent implements SerializableFunction<Foo, Label> {
        @Override
        public Label apply(Foo foo) {
            return new Label();
        }
    }

    @SuppressWarnings("serial")
    public static class MyClassNameGenerator implements SerializableFunction<Foo, String> {
        @Override
        public String apply(Foo foo) {
            return "foo";
        }
    }

    @SuppressWarnings("serial")
    public static class MySortOrderProvider implements SortOrderProvider {
        @Override
        public Stream<QuerySortOrder> apply(SortDirection sortDirection) {
            return null;
        }
    }
}
