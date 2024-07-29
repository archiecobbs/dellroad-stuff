
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.util;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Iterator;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LongSetTest extends TestSupport {

    @Test
    public void testLongSet() throws Exception {

        final LongSet actual = new LongSet();
        final HashSet<Long> expected = new HashSet<>();

        for (int i = 0; i < 5000; i++) {
            int shift = 0;
            while (shift < 31 && this.random.nextBoolean())
                shift++;
            final Long id = this.random.nextLong();
            final int action = this.random.nextInt(100);
            boolean expectedResult = false;
            boolean actualResult = false;
            if (action < 3) {
                actual.clear();
                expected.clear();
            } else if (action < 6) {
                final int pos = this.random.nextInt(10);
                final Iterator<Long> iter = actual.iterator();
                final Long id2 = Iterators.get(iter, pos, null);
                if (id2 != null) {
                    iter.remove();
                    expected.remove(id2);
                }
            } else if (action < 37) {
                final boolean actualWasEmpty = actual.isEmpty();
                final boolean expectedWasEmpty = expected.isEmpty();
                final long removed = actual.removeOne();
                if (removed != 0) {
                    actualResult = true;
                    expectedResult = expected.remove(removed);
                } else {
                    actualResult = false;
                    expectedResult = expected.remove(removed);
                }
            } else if (action < 65) {
                actualResult = actual.add(id);
                expectedResult = expected.add(id);
            } else if (action < 85) {
                actualResult = actual.remove(id);
                expectedResult = expected.remove(id);
            } else {
                actualResult = actual.contains(id);
                expectedResult = expected.contains(id);
            }
            Assert.assertEquals(actual, expected);
            Assert.assertEquals(actualResult, expectedResult,
              "wrong result: actual=" + actual.debugDump() + " expected=" + expected);
        }
    }
}

