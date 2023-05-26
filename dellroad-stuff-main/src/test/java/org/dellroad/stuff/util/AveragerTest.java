
/*
 * Copyright (C) 2023 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.util;

import java.util.ArrayList;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AveragerTest extends TestSupport {

    @Test
    public void testAddValues() throws Exception {
        for (int i = 0; i < 1000; i++) {
            final Averager expected = new Averager();
            final Averager a0 = new Averager();
            final Averager a1 = new Averager();
            final Averager a2 = new Averager();
            final int count = this.random.nextInt(50) + 1;
            for (int j = 0; j < count; j++) {
                final float value = (float)(this.random.nextGaussian() * 100);
                expected.addValue(value);
                switch (this.random.nextInt(3)) {
                case 0:
                    a0.addValue(value);
                    break;
                case 1:
                    a1.addValue(value);
                    break;
                case 2:
                    a2.addValue(value);
                    break;
                default:
                    throw new RuntimeException();
                }
            }
            //this.log.info("[ADD] a0: " + a0);
            //this.log.info("[ADD] a1: " + a1);
            //this.log.info("[ADD] a2: " + a2);
            final Averager actual = a0.add(a1).add(a2);
            //this.log.info("[ADD] Actual: " + actual);
            //this.log.info("[ADD] Expected: " + expected);
            this.verify(actual, expected);
        }
    }

    @Test
    public void testRemoveValues() throws Exception {
        for (int i = 0; i < 1000; i++) {
            Averager actual = new Averager();
            final Averager expected = new Averager();
            final int count = this.random.nextInt(50) + 1;
            final ArrayList<Float> allValues = new ArrayList<>();
            final ArrayList<Float> removedValues = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                final float value = (float)(this.random.nextGaussian() * 100);
                actual.addValue(value);
                allValues.add(value);
                if (this.random.nextBoolean())
                    expected.addValue(value);
                else
                    removedValues.add(value);
            }
            //this.log.info("[REMOVE] ALL VALUES: " + allValues);
            //this.log.info("[REMOVE] REMOVED VALUES: " + removedValues);

            // Remove values either one at a time, or all at once
            if (this.random.nextBoolean()) {
                while (!removedValues.isEmpty())
                    actual = actual.subtract(new Averager(removedValues.remove(this.random.nextInt(removedValues.size()))));
            } else {
                final Averager temp = new Averager();
                for (float removedValue : removedValues)
                    temp.addValue(removedValue);
                actual = actual.subtract(temp);
            }
            //this.log.info("[REMOVE] Actual: " + actual);
            //this.log.info("[REMOVE] Expected: " + expected);
            this.verify(actual, expected);
        }
    }

    private void verify(Averager actual, Averager expect) {
        Assert.assertEquals(!actual.isEmpty(), !expect.isEmpty());
        Assert.assertEquals(actual.size(), expect.size());
        if (!actual.isEmpty()) {
            final double aavg = actual.getAverage().getAsDouble();
            final double astd = actual.getStandardDeviation().getAsDouble();
            final double eavg = expect.getAverage().getAsDouble();
            final double estd = expect.getStandardDeviation().getAsDouble();
            final double diff1 = Math.abs(aavg - eavg);
            final double diff2 = Math.abs(astd - estd);
            Assert.assertTrue(diff1 <= 0.00001, "AVERAGE difference: " + aavg + " vs. " + eavg);
            Assert.assertTrue(diff2 <= 0.00001, "STD-DEV difference: " + astd + " vs. " + estd);
        }
    }
}
