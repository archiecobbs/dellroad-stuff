
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProvidesPropertyTest extends TestSupport {

    @Test
    @SuppressWarnings("unchecked")
    public void test1() throws Exception {
        final PropertySet<Foo> propertySet = new ProvidesPropertyScanner<>(Foo.class).getPropertySet();

        final Foo foo = new Foo();

        final PropertyDefinition<Foo, String> def1 = (PropertyDefinition<Foo, String>)propertySet.getProperty("field1").get();
        Assert.assertEquals(def1.getName(), "field1");
        Assert.assertEquals(def1.getType(), String.class);
        Assert.assertEquals(def1.getPropertySet(), propertySet);
        Assert.assertEquals(def1.getCaption(), "caption1");

        def1.getSetter().get().accept(foo, "value1");
        Assert.assertEquals(foo.getField1(), "value1");
        Assert.assertEquals(def1.getGetter().apply(foo), "value1");

        final PropertyDefinition<Foo, Integer> def2 = (PropertyDefinition<Foo, Integer>)propertySet.getProperty("foo").get();
        Assert.assertEquals(def2.getName(), "foo");
        Assert.assertEquals(def2.getType(), Integer.class);
        Assert.assertEquals(def2.getPropertySet(), propertySet);
        Assert.assertEquals(def2.getCaption(), "caption2");

        final PropertyDefinition<Foo, Object> def3 = (PropertyDefinition<Foo, Object>)propertySet.getProperty("field3").get();
        Assert.assertEquals(def3.getPropertySet(), propertySet);
        Assert.assertFalse(def3.getSetter().isPresent());
    }

// Classes

    public class Foo {

        private String field1;
        private int field2;

        @ProvidesProperty(caption = "caption1")
        public String getField1() {
            return this.field1;
        }
        public void setField1(String field1) {
            this.field1 = field1;
        }

        @ProvidesProperty(value = "foo", caption = "caption2")
        public int getField2() {
            return this.field2;
        }
        public void setField2(int field2) {
            this.field2 = field2;
        }

        @ProvidesProperty
        public Object getField3() {
            return null;
        }
    }
}

