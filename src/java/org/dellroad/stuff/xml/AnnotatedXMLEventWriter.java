
/*
 * Copyright (C) 2012 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.xml;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * {@link XMLEventWriter} that adds an extra annotation element to an XML document as it is written.
 * The annotation element will be added as the first element inside the top-level document element.
 *
 * <p>
 * This class can be used in combination with {@link AnnotatedXMLEventReader} to transparently annotate XML documents.
 *
 * @see AnnotatedXMLEventReader
 */
public abstract class AnnotatedXMLEventWriter extends EventWriterDelegate {

    protected final XMLEventFactory xmlEventFactory = XMLEventFactory.newFactory();

    private final StringBuilder trailingSpace = new StringBuilder();

    // State:
    //  0 = before document element
    //  1 = after document element but before annotation element
    //  2 = after annotation element
    private byte state;

    public AnnotatedXMLEventWriter(XMLEventWriter inner) {
        super(inner);
    }

    @Override
    public void add(XMLEvent event) throws XMLStreamException {
        switch (this.state) {
        case 0:
            if (event.isStartElement())
                this.state++;
            super.add(event);
            break;
        case 1:
            if (event.isNamespace() || event.isAttribute()) {
                super.add(event);
                break;
            }
            if (event.isCharacters() && event.asCharacters().isWhiteSpace()) {
                this.trailingSpace.append(event.asCharacters().getData());
                super.add(event);
                break;
            }
            this.state++;
            this.addAnnotationElement(this.getParent());
            if (this.trailingSpace.length() > 0)
                super.add(this.xmlEventFactory.createCharacters(this.trailingSpace.toString()));
            super.add(event);
            break;
        case 2:
            super.add(event);
            break;
        default:
            throw new RuntimeException("internal error");
        }
    }

    @Override
    public void add(XMLEventReader reader) throws XMLStreamException {
        if (reader == null)
            throw new XMLStreamException("null reader");
        while (reader.hasNext())
            this.add(reader.nextEvent());
    }

    /**
     * Get the whitespace found between the opening document tag and the first non-space child.
     */
    protected String getTrailingSpace() {
        return this.trailingSpace.toString();
    }

    /**
     * Add the annotation element.
     *
     * <p>
     * This method should {@link #add add()} the {@link javax.xml.stream.events.StartElement} for the annotation element, followed
     * by any nested content, and then lastly the {@link javax.xml.stream.events.EndElement} for the annotation element.
     *
     * @param writer output to which the annotation element should be written
     */
    protected abstract void addAnnotationElement(XMLEventWriter writer) throws XMLStreamException;
}

