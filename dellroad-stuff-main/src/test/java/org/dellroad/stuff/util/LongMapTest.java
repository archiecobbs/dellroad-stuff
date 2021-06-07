
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.util;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LongMapTest extends TestSupport {

    @Test
    public void testLongMap() throws Exception {

        final LongMap<Integer> actual = new LongMap<>();
        final HashMap<Long, Integer> expected = new HashMap<>();

        for (int i = 0; i < 5000; i++) {
            int shift = 0;
            while (shift < 31 && this.random.nextBoolean())
                shift++;
            final Long id = this.random.nextLong();
            final int action = this.random.nextInt(100);
            final Integer value = new Integer(this.random.nextInt(4));
            boolean expectedResult = false;
            boolean actualResult = false;
            if (action < 3) {
                actual.clear();
                expected.clear();
            } else if (action < 6) {
                final int pos = this.random.nextInt(10);
                final Iterator<Long> iter = actual.keySet().iterator();
                final Long id2 = Iterators.get(iter, pos, null);
                if (id2 != null) {
                    iter.remove();
                    expected.remove(id2);
                }
            } else if (action < 40) {
                actualResult = !actual.entrySet().equals(expected.entrySet());
            } else if (action < 45) {
                actualResult = value.equals(actual.put(id, value));
                expectedResult = value.equals(expected.put(id, value));
            } else if (action < 85) {
                actualResult = value.equals(actual.remove(id));
                expectedResult = value.equals(expected.remove(id));
            } else if (action < 90) {
                actualResult = actual.containsValue(value);
                expectedResult = expected.containsValue(value);
            } else {
                actualResult = actual.containsKey(id);
                expectedResult = expected.containsKey(id);
            }
            Assert.assertEquals(actual, expected);
            Assert.assertEquals(actual.keySet(), expected.keySet());
            Assert.assertEquals(actual.entrySet(), expected.entrySet());
            Assert.assertEquals(new HashSet<>(actual.values()), new HashSet<>(expected.values()));
            Assert.assertEquals(actualResult, expectedResult,
              "wrong result: actual=" + actual.debugDump() + " expected=" + expected);
        }
    }
}
