
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import javax.xml.stream.XMLStreamWriter;

import org.dellroad.stuff.xml.EmptyTagXMLStreamWriter;
import org.dellroad.stuff.xml.IndentXMLStreamWriter;
import org.springframework.oxm.jibx.JibxMarshaller;

/**
 * {@link JibxMarshaller} that works around two bugs: <a href="http://jira.codehaus.org/browse/JIBX-492">JIBX-492</a>
 * and <a href="https://bugs.openjdk.java.net/browse/JDK-8016914">JDK-8016914</a>.
 */
public class WorkaroundJibxMarshaller extends JibxMarshaller {

    @Override
    protected void marshalXmlStreamWriter(Object graph, XMLStreamWriter writer) {
        super.marshalXmlStreamWriter(graph, new EmptyTagXMLStreamWriter(new IndentXMLStreamWriter(writer)));
    }
}

