
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
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
            final Integer value = (Integer)this.random.nextInt(4);
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
            } else if (action < 37) {
                final boolean actualWasEmpty = actual.isEmpty();
                final boolean expectedWasEmpty = expected.isEmpty();
                final Map.Entry<Long, Integer> entry = actual.removeOne();
                if (entry != null) {
                    final Long key = entry.getKey();
                    final Integer aval = entry.getValue();
                    final Integer eval = expected.remove(key);
                    Assert.assertFalse(actualWasEmpty);
                    Assert.assertFalse(expectedWasEmpty);
                    Assert.assertEquals(aval, eval);
                } else {
                    Assert.assertTrue(actualWasEmpty);
                    Assert.assertTrue(expectedWasEmpty);
                }
                actualResult = expectedResult = true;
            } else if (action < 40) {
                actualResult = !actual.entrySet().equals(expected.entrySet());
            } else if (action < 65) {
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

    @Test
    public void testLongMapIterator() throws Exception {
        final LongMap<String> x = new LongMap<>();
        final long key1 = 0x0000000000000068L;
        final String val1 = "foo";
        final long key2 = 0x000000000000003dL;
        final String val2 = "bar";
        x.put(key1, val1);
        x.put(key2, val2);
        final Iterator<Map.Entry<Long, String>> i = x.entrySet().iterator();
        assert i.hasNext();
        Map.Entry<Long, String> entry1 = i.next();
        i.remove();
        assert i.hasNext();
        Map.Entry<Long, String> entry2 = i.next();
        i.remove();
        assert !i.hasNext();
        if (entry1.getKey().equals(key2)) {
            Map.Entry<Long, String> temp = entry1;
            entry1 = entry2;
            entry2 = temp;
        }
        assert entry1.getKey().equals(key1);
        assert entry1.getValue().equals(val1);
        assert entry2.getKey().equals(key2);
        assert entry2.getValue().equals(val2);
    }

    @Test
    public void testLongIterator2() throws Exception {
        final LongMap<Void> actual = new LongMap<>();
        final HashMap<Long, Void> expected = new HashMap<>();
        Iterator<Map.Entry<Long, Void>> i = null;
        for (int count = 0; count < 1000; count++) {
            if (this.random.nextInt(100) < 30) {
                long val;
                do
                    val = this.random.nextInt(1 << 30);
                while (val == 0);
                actual.put(val, null);
                expected.put(val, null);
                i = null;
            } else if (i == null) {
                i = actual.entrySet().iterator();
            } else if (!i.hasNext()) {
                i = null;
            } else {
                final long val = i.next().getKey();
                Assert.assertTrue(actual.containsKey(val));
                Assert.assertTrue(expected.containsKey(val));
                if (this.random.nextBoolean())
                    i.hasNext();
                if (this.random.nextBoolean()) {
                    i.remove();
                    expected.remove(val);
                    Assert.assertTrue(!actual.containsKey(val));
                }
            }
            Assert.assertEquals(actual, expected);
        }
    }
}
