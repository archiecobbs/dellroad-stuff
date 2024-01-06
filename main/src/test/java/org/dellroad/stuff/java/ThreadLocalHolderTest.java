
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.util.HashSet;
import java.util.function.Supplier;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ThreadLocalHolderTest extends TestSupport {

    @Test
    public void testThreadLocalHolder() throws Exception {

    // Setup

        final HashSet<Object> destroyed = new HashSet<>();

        final ThreadLocalHolder<Object> t = new ThreadLocalHolder<Object>() {
            @Override
            protected void destroy(Object obj) {
                destroyed.add(obj);
            }
        };

    // Check not executing

        Assert.assertNull(t.get());

        try {
            t.require();
            assert false;
        } catch (IllegalStateException e) {
            // ok
        }

        assert destroyed.isEmpty();

    // Test Runnable

        final Object foo = new Object();

        Runnable rable = new Runnable() {
            @Override
            public void run() {
                Object current = t.require();
                assert current == foo;
                current = t.get();
                assert current == foo;
                assert destroyed.isEmpty();
            }
        };

        t.invoke(foo, rable);

        assert destroyed.iterator().next() == foo;

        destroyed.clear();

    // Test Supplier

        final Object foo2 = new Object();
        final Object bar = new Object();

        Supplier<Object> supplier = () -> {
            Object current = t.require();
            assert current == foo2;
            current = t.get();
            assert current == foo2;
            assert destroyed.isEmpty();
            return bar;
        };

        Object bar2 = t.invoke(foo2, supplier);
        assert bar2 == bar;

        assert destroyed.iterator().next() == foo2;

    // Check resetedness

        Assert.assertNull(t.get());

        try {
            t.require();
            assert false;
        } catch (IllegalStateException e) {
            // ok
        }
    }
}
