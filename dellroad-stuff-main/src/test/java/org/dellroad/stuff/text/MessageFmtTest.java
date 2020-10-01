
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.text;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class MessageFmtTest extends TestSupport {

    @Test
    public void testLocaleCapture() {

        final Object[] args = new Object[] { new java.util.Date(1590000000000L) };

        final MessageFormat messageFormat = new MessageFormat("date = {0,date,short}", Locale.US);

        final MessageFmt messageFmt1 = new MessageFmt(messageFormat, false);
        final MessageFmt messageFmt2 = new MessageFmt(messageFormat, true);

        Assert.assertEquals(messageFmt1.toPattern(), "date = {0,date,short}");
        Assert.assertEquals(messageFmt2.toPattern(), "date = {0,date,M/d/yy}");

        final MessageFormat messageFormat1 = messageFmt1.toMessageFormat(Locale.FRANCE);
        final MessageFormat messageFormat2 = messageFmt2.toMessageFormat(Locale.FRANCE);

        Assert.assertEquals(messageFormat1.format(args), "date = 20/05/20");
        Assert.assertEquals(messageFormat2.format(args), "date = 5/20/20");
    }

    @Test(dataProvider = "cases")
    public void testMessageFmt(Locale locale, String format, String expected, List<?> argList) {

        final Object[] args = argList.toArray();

        //this.log.info("testMessageFmt.1:\n    format=\"{}\"\n  expected=\"{}\"\n   argList={}", format, expected, argList);

        final MessageFormat messageFormat1 = new MessageFormat(format, locale);
        final String pattern1 = messageFormat1.toPattern();
        final String actual1 = messageFormat1.format(args);

        //this.log.info("testMessageFmt.2:\n    pattern1=\"{}\"\n     actual1=\"{}\"{}",
          //pattern1, actual1, !actual1.equals(expected) ? " *** WRONG ***" : " - correct");

        Assert.assertEquals(actual1, expected);

        final MessageFmt messageFmt1 = new MessageFmt(messageFormat1);
        final MessageFormat messageFormat2 = messageFmt1.toMessageFormat(locale);
        final String pattern2 = messageFormat2.toPattern();
        final String actual2 = messageFormat2.format(args);

        //this.log.info("testMessageFmt.3:\n    mesgFmt1={}\n  pattern2=\"{}\"\n   actual2=\"{}\"{}",
          //messageFmt1, pattern2, actual2, !actual2.equals(expected) ? " *** WRONG ***" : " - correct");

        Assert.assertEquals(actual2, expected);
    }

    @DataProvider(name = "cases")
    public Object[][] genEncodeCases() {
        final ArrayList<Object[]> caseList = new ArrayList<>();

        // Do some manual tests
        final Object[][] manualTests = new Object[][] {

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

            {
                Locale.US,
                "for appt #{2}, call {0,choice,0.0#{1}|1.0#'your physician''s office'}",
                "for appt #6, call your physician's office",
                1, "555-1212", 6
            },

            {
                Locale.US,
                "{0,choice,0.0#'for appt #{2}, call {1}'|1.0#'for appt #{2}, call your physician''''s office'}",
                "for appt #6, call your physician's office",
                1, "555-1212", 6
            },

            {
                Locale.US,
                "{0,choice,0#1-zero: {1}|1#1-one: {1}|1<1-''''more than one'''': '{2,choice,0#3-zero: {3}|1#3-one: {3}|1<3-''''''''more than one'''''''': {3}}'}",
                "1-'more than one': 3-'more than one': bbb",
                2, "aaa", 4, "bbb"
            },

        };
        Stream.of(manualTests)
          .map(array -> {
              final Object[] triad = new Object[4];
              System.arraycopy(array, 0, triad, 0, 3);
              triad[3] = Arrays.asList(array).subList(3, array.length);
              return triad;
          })
          .forEach(caseList::add);

        // Create massively nested format
        final int maxLimit = 5;
        final HashSet<Integer> withParamSteps = new HashSet<>();
        MessageFmt fmt = this.textify(0, Math.nextUp((double)maxLimit), true);
        withParamSteps.add(maxLimit + 1);
        //this.log.info("testLotsOfQuotes:\n  initial fmt={}\n  initial format=\"{}\"", fmt, fmt.toPattern());
        boolean withParam = false;
        for (int step = maxLimit; step >= 0; step--) {

            // Sanity check that going to MessageFormat and back we end up with the same thing
            MessageFmt fmt2 = new MessageFmt(fmt.toMessageFormat());
            Assert.assertEquals(fmt2.toString(), fmt.toString());

            // Nest "fmt" inside a choice and make that the new "fmt"
            fmt = this.choiceify(0, step, withParam, fmt);
            //this.log.info("testLotsOfQuotes[step={}]:\n  next fmt={}\n  next format=\"{}\"", step, fmt, fmt.toPattern());

            // Alternate with vs. without parameter (this determines whether a '{' appears in the string)
            if (withParam)
                withParamSteps.add(step);
            withParam = !withParam;
        }

        // Execute format with various arguments
        //this.log.info("testLotsOfQuotes:\n  final fmt={}\n  final format=\"{}\"", fmt, fmt.toPattern());
        for (int step = 0; step <= maxLimit + 1; step++) {
            //this.log.info("testLotsOfQuotes[step={}]...", step);
            final double limit = Math.min(step, maxLimit);
            final String relation = step > maxLimit ? "more than" : "at most";
            final String expected = withParamSteps.contains(step) ?
              String.format("the 'param' is %s %.1f: %d", relation, limit, step) :
              String.format("the 'param' is %s %.1f", relation, limit);
            caseList.add(new Object[] { Locale.US, fmt.toPattern(), expected, Arrays.asList(step) });
        }

        // Done
        return caseList.toArray(new Object[0][]);
    }

    private MessageFmt choiceify(int argnum, double limit, boolean withParam, MessageFmt other) {
        return new MessageFmt(
          new MessageFmt.ChoiceArgumentSegment(argnum,
            new MessageFmt.ChoiceArgumentSegment.Option(limit, this.textify(argnum, limit, withParam)),
            new MessageFmt.ChoiceArgumentSegment.Option(Math.nextUp(limit), other)));
    }

    private MessageFmt textify(int argnum, double limit, boolean withParam) {
        String prefix = limit == Math.nextUp((double)Math.round(limit)) ?
          String.format("the 'param' is more than %.1f", (double)Math.round(limit)) :
          String.format("the 'param' is at most %.1f", limit);
        if (!withParam)
            return new MessageFmt(new MessageFmt.TextSegment(prefix));
        prefix += ": ";
        return new MessageFmt(new MessageFmt.TextSegment(prefix), new MessageFmt.DefaultArgumentSegment(argnum));
    }
}
