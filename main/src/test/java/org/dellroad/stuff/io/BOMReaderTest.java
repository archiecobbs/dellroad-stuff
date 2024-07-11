
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.dellroad.stuff.string.ByteArrayEncoder;
import org.dellroad.stuff.string.StringEncoder;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BOMReaderTest extends TestSupport {

    @Test(dataProvider = "bomTests")
    public void testBOM(String bytestr, BOM bom, CodingErrorAction action, String expected) throws Exception {
        final byte[] bytes = ByteArrayEncoder.decode(bytestr);
        final StringWriter buf = new StringWriter();
        final ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        final BOMReader reader = new BOMReader(input, action, StandardCharsets.UTF_8);
        Assert.assertEquals(reader.getBOM(), bom);
        try {
            for (int r; (r = reader.read()) != -1; )
                buf.write(r);
            final String actual = buf.toString();
            Assert.assertEquals(StringEncoder.enquote(actual), StringEncoder.enquote(expected));
        } catch (IOException e) {
            this.log.debug("BOMReaderTest: got exception", e);
            final String className = e.getClass().getName();
            Assert.assertEquals(className, expected);
        }
    }

    @DataProvider(name = "bomTests")
    public Object[][] simpleRegexCases() {
        return new Object[][] {

            {
                "ef bb bf 74 65 73 74 31  32 33 ff 34 35 36",       // invalid UTF-8 byte sequence at file offset 10
                BOM.UTF_8,
                CodingErrorAction.REPORT,
                "java.nio.charset.MalformedInputException"          // illegal byte reported as exception
            },

            {
                "ef bb bf 74 65 73 74 31  32 33 ff 34 35 36",       // invalid UTF-8 byte sequence at file offset 10
                BOM.UTF_8,
                CodingErrorAction.IGNORE,
                "test123456"                                        // illegal byte discarded
            },

            {
                "ef bb bf 74 65 73 74 31  32 33 ff 34 35 36",       // invalid UTF-8 byte sequence at file offset 10
                BOM.UTF_8,
                CodingErrorAction.REPLACE,
                "test123\ufffd456"                                  // illegal byte replaced with the replacement character
            },

            {
                "ef bb bf 70 61 72 74 69  61 6c 20 61 72 72 6f 77"
             + " 3a 20 e2 86",                                      // invalid UTF-8 byte sequence at file offset 18
                BOM.UTF_8,
                CodingErrorAction.REPORT,
                "java.nio.charset.MalformedInputException"
            },

            {
                "fe ff 00 41 00 42 00 43 00 31 00 32 00 33",
                BOM.UTF_16BE,
                CodingErrorAction.REPORT,
                "ABC123"
            },

            {
                "ff fe 41 00 42 00 43 00 31 00 32 00 33 00",
                BOM.UTF_16LE,
                CodingErrorAction.REPORT,
                "ABC123"
            },

            {
                "41 42 43 31 32 33",
                null,
                CodingErrorAction.REPORT,
                "ABC123"
            },
        };
    }
}
