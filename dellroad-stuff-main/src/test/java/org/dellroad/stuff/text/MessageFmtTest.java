
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.text;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class MessageFmtTest extends TestSupport {

    @Test(dataProvider = "cases")
    public void testMessageFmt(Locale locale, String format, String expected, List<?> argList) {

        final Object[] args = argList.toArray();

        final MessageFormat messageFormat1 = new MessageFormat(format, locale);
        final String pattern1 = messageFormat1.toPattern();
        final String actual1 = messageFormat1.format(args);

        Assert.assertEquals(actual1, expected);

        final MessageFmt messageFmt1 = new MessageFmt(messageFormat1);
        this.log.info("testMessageFmt:\n  messageFormat1={}\n  messageFmt1={}", messageFormat1, messageFmt1);
        final MessageFormat messageFormat2 = messageFmt1.toMessageFormat();
        final String pattern2 = messageFormat2.toPattern();
        final String actual2 = messageFormat2.format(args);

        Assert.assertEquals(actual2, expected);
    }

    @DataProvider(name = "cases")
    public Object[][] genEncodeCases() {
        final Object[][] cases = new Object[][] {

            {
                Locale.US,
                "simple test",
                "simple test"
            },

            {
                Locale.US,
                "you owe me {0,number,#,##0.00}",
                "you owe me 12,345.68",
                12345.6789
            },

            {
                Locale.FRENCH,
                "you owe me {0,number,#,##0.00}",
                "you owe me 12\u00a0345,68",
                12345.6789
            },

            {
                Locale.US,
                "one: {0} two: {1} three: {2}",
                "one: 1 two: 2 three: 3",
                1, 2, 3
            },

            {
                Locale.US,
                "The {0,choice,0#'{1,choice,0#|1#first|2#next}'|1#only} appointment is on {2}",
                "The  appointment is on Monday",
                0, 0, "Monday"
            },

            {
                Locale.US,
                "The {0,choice,0#'{1,choice,0#|1#first|2#next}'|1#only} appointment is on {2}",
                "The first appointment is on Monday",
                0, 1, "Monday"
            },

            {
                Locale.US,
                "The {0,choice,0#'{1,choice,0#|1#first|2#next}'|1#only} appointment is on {2}",
                "The next appointment is on Tuesday",
                0, 2, "Tuesday"
            },

            {
                Locale.US,
                "The {0,choice,0#'{1,choice,0#|1#first|2#next}'|1#only} appointment is on {2}",
                "The only appointment is on Friday",
                1, 0, "Friday"
            },

            {
                Locale.US,
                "Result: {0,choice,0#literal|1#'nested: {0}, {1}'}",
                "Result: literal",
                0, 0
            },

            {
                Locale.US,
                "Result: {0,choice,0#literal|1#'nested: {0}, {1}'}",
                "Result: nested: 7, 8",
                7, 8
            },

            {
                Locale.US,
                "Result: {0,choice,0#literal|1#'nested: {1,choice,0#literal2|1#''nested2: {0}, {1}''}'}",
                "Result: nested: nested2: 7, 8",
                7, 8
            },

        };
        return Stream.of(cases)
          .map(array -> {
              final Object[] triad = new Object[4];
              System.arraycopy(array, 0, triad, 0, 3);
              triad[3] = Arrays.asList(array).subList(3, array.length);
              return triad;
          })
          .toArray(Object[][]::new);
    }
}
