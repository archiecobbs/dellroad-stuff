
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class PrimitiveTest extends TestSupport {

    @Test(dataProvider = "names")
    public <T> void testName(Primitive<?> primitive, String name) {
        if (primitive != null)
            Assert.assertEquals(primitive.getName(), name);
        try {
            Assert.assertSame(primitive, Primitive.forName(name));
            assert name != null;
        } catch (IllegalArgumentException e) {
            assert name == null;
        }
    }

    @Test(dataProvider = "data")
    public <T> void testPrimitive(Primitive<T> primitive, Iterator<T> i) throws Exception {
        while (i.hasNext()) {
            final T value = i.next();
            final String string = "" + value;
            Assert.assertTrue(primitive.getParsePattern().matcher(string).matches(),
              primitive + " parse pattern " + primitive.getParsePattern() + " does not match \"" + string + "\"");
            Assert.assertEquals(primitive.parseValue(string), value,
              primitive + " parsed value " + primitive.parseValue(string) + " for \"" + string + "\" does not equal " + value);
        }
    }

    @Test(dataProvider = "parse")
    public <T> void testPrimitiveParse(Primitive<T> primitive, String string) throws Exception {
        Assert.assertTrue(primitive.getParsePattern().matcher(string).matches(),
          primitive + " parse pattern " + primitive.getParsePattern() + " does not match \"" + string + "\"");
        final T value = primitive.parseValue(string);
        final T value2 = primitive.parseValue("" + value);
        Assert.assertEquals(value2, value,
          primitive + " parsed value " + value2 + " for \"" + value + "\" does not equal " + value + " for \"" + string + "\"");
    }

    @DataProvider(name = "names")
    public Object[][] getNameData() {
        return new Object[][] {

            // Success
            { Primitive.VOID, "void" },
            { Primitive.BOOLEAN, "boolean" },
            { Primitive.BYTE, "byte" },
            { Primitive.CHARACTER, "char" },
            { Primitive.SHORT, "short" },
            { Primitive.INTEGER, "int" },
            { Primitive.FLOAT, "float" },
            { Primitive.LONG, "long" },
            { Primitive.DOUBLE, "double" },

            // Fails
            { null, null },
            { null, "VOID" },
            { null, "integer" },
            { null, "Integer" },
        };
    }

    @DataProvider(name = "data")
    public Object[][] getData() {
        final ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(new Object[] { Primitive.VOID, Collections.<Void>emptySet().iterator() });
        list.add(new Object[] { Primitive.BOOLEAN, Arrays.<Boolean>asList(true, false).iterator() });
        list.add(new Object[] { Primitive.BYTE, new RandomIterator<Byte>(200, Byte.MIN_VALUE, Byte.MAX_VALUE) {
            @Override
            protected Byte getNextRandom(Random r) {
                return (byte)r.nextInt();
            }
        }});
        list.add(new Object[] { Primitive.CHARACTER, new RandomIterator<Character>(5000, (char)0, (char)0xffff) {
            @Override
            protected Character getNextRandom(Random r) {
                return (char)r.nextInt();
            }
        }});
        list.add(new Object[] { Primitive.SHORT, new RandomIterator<Short>(5000, Short.MIN_VALUE, Short.MAX_VALUE) {
            @Override
            protected Short getNextRandom(Random r) {
                return (short)r.nextInt();
            }
        }});
        list.add(new Object[] { Primitive.INTEGER, new RandomIterator<Integer>(20000, Integer.MIN_VALUE, Integer.MAX_VALUE) {
            @Override
            protected Integer getNextRandom(Random r) {
                return r.nextInt();
            }
        }});
        list.add(new Object[] { Primitive.FLOAT, new RandomIterator<Float>(20000,
          Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY) {
            @Override
            protected Float getNextRandom(Random r) {
                return Float.intBitsToFloat(r.nextInt());
            }
        }});
        list.add(new Object[] { Primitive.LONG, new RandomIterator<Long>(20000, Long.MIN_VALUE, Long.MAX_VALUE) {
            @Override
            protected Long getNextRandom(Random r) {
                return r.nextLong();
            }
        }});
        list.add(new Object[] { Primitive.DOUBLE, new RandomIterator<Double>(20000,
          Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) {
            @Override
            protected Double getNextRandom(Random r) {
                return Double.longBitsToDouble(r.nextLong());
            }
        }});
        return list.toArray(new Object[8][]);
    }

    @DataProvider(name = "parse")
    public Object[][] getParseData() {
        return new Object[][] {

            { Primitive.INTEGER, "0xffff" },
            { Primitive.INTEGER, "0x8000" },
            { Primitive.INTEGER, "0x7fff" },
            { Primitive.INTEGER, "0x0000" },

            { Primitive.LONG, "0xffffffff" },
            { Primitive.LONG, "0x80000000" },
            { Primitive.LONG, "0x7fffffff" },
            { Primitive.LONG, "0x00000000" },
        };
    }

    private abstract class RandomIterator<T> implements Iterator<T> {

        private final ArrayList<T> list;
        private int randomRemaining;

        @SafeVarargs
        @SuppressWarnings("varargs")
        RandomIterator(int totalRandom, T... values) {
            this.randomRemaining = totalRandom;
            this.list = new ArrayList<T>(Arrays.<T>asList(values));
        }

        @Override
        public boolean hasNext() {
            return !this.list.isEmpty() || this.randomRemaining > 0;
        }

        @Override
        public T next() {
            if (!this.hasNext())
                throw new NoSuchElementException();
            if (!this.list.isEmpty())
                return this.list.remove(this.list.size() - 1);
            this.randomRemaining--;
            return this.getNextRandom(PrimitiveTest.this.random);
        }

        protected abstract T getNextRandom(Random r);

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

