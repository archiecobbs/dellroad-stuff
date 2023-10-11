
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stream.StreamSource;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class IndentXMLStreamWriterTest extends TestSupport {

    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();

    @Test(dataProvider = "files")
    public void testIndent(String inputResource, String expectedResource, boolean emptyTag,
      boolean addMissingXmlDeclaration, boolean indentAfterXmlDecl, String nestedTagName) throws Exception {

        // Read original XML
        final File actualFile = File.createTempFile("test1.", "xml");
        String input = this.readResource(inputResource);

        // Extract nested XML
        if (nestedTagName != null) {
            final String pattern = String.format("(?s)^(<\\?xml[^>]+\\?>).*(<%s>.*</%s>).*$", nestedTagName, nestedTagName);
            input = input.replaceAll(pattern, "$1\n$2");
        }

        // Reformat XML
        this.indent(input, actualFile, emptyTag, addMissingXmlDeclaration, indentAfterXmlDecl);

        // Compare actual vs. expected
        final String actual = this.readResource(actualFile);
        actualFile.delete();
        final String expected = this.readResource(expectedResource);
        Assert.assertEquals(actual.trim().replaceAll("(?s)\\r\\n?", "\n"), expected.trim());
    }

    private void indent(String input, File outputFile, boolean emptyTag,
      boolean addMissingXmlDeclaration, boolean indentAfterXmlDecl) throws Exception {
        final IndentXMLStreamWriter indentWriter = new IndentXMLStreamWriter(
          this.xmlOutputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), "UTF-8"), 4);
        indentWriter.setIndentAfterXmlDeclaration(indentAfterXmlDecl);
        XMLStreamWriter writer = indentWriter;
        if (emptyTag)
            writer = new EmptyTagXMLStreamWriter(writer);
        TransformerFactory.newInstance().newTransformer().transform(
          new StreamSource(new StringReader(input)), new StAXResult(writer));
        writer.close();
    }

    @DataProvider(name = "files")
    public Object[][] generateFiles() {
        return new Object[][] {
            new Object[] { "input1.xml", "output1.xml",     false,  true,   true,   null        },
            new Object[] { "input2.xml", "output2.xml",     false,  true,   true,   null        },
            new Object[] { "input3.xml", "output3.xml",     false,  true,   true,   null        },
            new Object[] { "input6.xml", "output6.xml",     false,  true,   true,   null        },
            new Object[] { "input6.xml", "output6a.xml",    true,   true,   true,   null        },
            new Object[] { "input6.xml", "output6b.xml",    false,  true,   false,  null        },
            new Object[] { "input7.xml", "output7.xml",     false,  true,   true,   null        },
            new Object[] { "input8.xml", "output8.xml",     true,   true,   true,   "JanFoo"    },
        };
    }
}
