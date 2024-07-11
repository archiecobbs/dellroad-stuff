
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ReflectUtilTest extends TestSupport {

    @Test
    public void testFindPropertySetters1() {

        final Map<String, List<Method>> map = ReflectUtil.findPropertySetters(Class2.class, "set");

        //this.log.info("MAP: {}", map);

        Assert.assertEquals(map.size(), 2);
        Assert.assertTrue(map.containsKey("foo"));
        Assert.assertTrue(map.containsKey("bar"));

        final List<Method> fooList = map.get("foo");
        Assert.assertEquals(fooList.size(), 2);
        Assert.assertEquals(fooList.get(0).toString(),
          "public void " + Class2.class.getName() + ".setFoo(java.lang.Integer)");
        Assert.assertEquals(fooList.get(1).toString(),
          "public void " + Class1.class.getName() + ".setFoo(java.lang.Object)");

        final List<Method> barList = map.get("bar");
        Assert.assertEquals(barList.size(), 3);
        Assert.assertEquals(barList.get(0).toString(),
          "public void " + Class2.class.getName() + ".setBar(java.lang.Integer)");
        Assert.assertEquals(barList.get(1).toString(),
          "public void " + Class2.class.getName() + ".setBar(java.lang.Number)");
        Assert.assertEquals(barList.get(2).toString(),
          "public void " + Class2.class.getName() + ".setBar(java.lang.Object)");
    }

    private static class Class1<FOO> {

        public void setFoo(FOO param) {
        }

        public void setBar(FOO param) {
        }
    }

    private static class Class2 extends Class1<Number> {

        // This is not an override - no bridge method is generated
        public void setFoo(Integer foo) {
        }

        // This is an override - a bridge method will be generated
        @Override
        public void setBar(Number param) {
        }

        // This has an even narrower different parameter type
        public void setBar(Integer param) {
        }
    }
}
