
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.xml;

import java.util.Arrays;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.dellroad.stuff.string.StringEncoder;

/**
 * Represents some XML output event(s) that can be applied to an {@link XMLStreamWriter}.
 */
public abstract class StreamWriterAction {

    protected StreamWriterAction() {
    }

    /**
     * Apply this action to the given {@link XMLStreamWriter} by invoking method(s) to effect some XML output event(s).
     *
     * @param writer XML writer
     * @throws XMLStreamException if an XML error occurs
     * @throws NullPointerException if {@code writer} is null
     */
    public abstract void apply(XMLStreamWriter writer) throws XMLStreamException;

    /**
     * Get an instance that corresponds to the current event state of the given {@link XMLStreamReader}, such that
     * a sequence of such instances created from consecutive input events will recreate the input XML.
     *
     * <p>
     * This method does not advance or modify {@code reader}.
     *
     * @param reader XML input
     * @return action corresponding to {@code reader}
     * @throws XMLStreamException if an error occurs
     * @throws NullPointerException if {@code reader} is null
     */
    public static StreamWriterAction of(XMLStreamReader reader) throws XMLStreamException {
        switch (reader.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
            return new StartElementAction(reader);
        case XMLStreamConstants.END_ELEMENT:
            return new EndElementAction();
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            return new PIAction(reader);
        case XMLStreamConstants.CHARACTERS:
        case XMLStreamConstants.SPACE:
            return new CharactersAction(reader);
        case XMLStreamConstants.COMMENT:
            return new CommentAction(reader);
        case XMLStreamConstants.START_DOCUMENT:
            return new StartDocumentAction(reader);
        case XMLStreamConstants.END_DOCUMENT:
            return new EndDocumentAction();
        case XMLStreamConstants.ENTITY_REFERENCE:
            return new EntityRefAction(reader);
        case XMLStreamConstants.DTD:
            return new DTDAction(reader);
        case XMLStreamConstants.CDATA:
            return new CDataAction(reader);
        case XMLStreamConstants.ATTRIBUTE:
        case XMLStreamConstants.NAMESPACE:
            return new EmptyAction();
        case XMLStreamConstants.NOTATION_DECLARATION:
        case XMLStreamConstants.ENTITY_DECLARATION:
            return new EmptyAction();                       // ???
        default:
            throw new XMLStreamException("unknown event type " + reader.getEventType());
        }
    }

// StreamWriterActions

    private static final class StartElementAction extends StreamWriterAction {

        final String prefix;
        final String localName;
        final String namespaceURI;
        final NamespaceContext namespaceContext;
        final String[] nsPrefix;
        final String[] nsURI;
        final String[] attrPrefix;
        final String[] attrNamespace;
        final String[] attrLocalName;
        final String[] attrValue;

        StartElementAction(XMLStreamReader reader) {
            this.prefix = reader.getPrefix();
            this.localName = reader.getLocalName();
            this.namespaceURI = reader.getNamespaceURI();
            this.namespaceContext = reader.getNamespaceContext();
            final int numNS = reader.getNamespaceCount();
            this.nsPrefix = new String[numNS];
            this.nsURI = new String[numNS];
            for (int i = 0; i < numNS; i++) {
                this.nsPrefix[i] = reader.getNamespacePrefix(i);
                this.nsURI[i] = reader.getNamespaceURI(i);
            }
            final int numAttrs = reader.getAttributeCount();
            this.attrPrefix = new String[numAttrs];
            this.attrNamespace = new String[numAttrs];
            this.attrLocalName = new String[numAttrs];
            this.attrValue = new String[numAttrs];
            for (int i = 0; i < numAttrs; i++) {
                this.attrPrefix[i] = reader.getAttributePrefix(i);
                this.attrNamespace[i] = reader.getAttributeNamespace(i);
                this.attrLocalName[i] = reader.getAttributeLocalName(i);
                this.attrValue[i] = reader.getAttributeValue(i);
            }
        }

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            if (this.namespaceURI != null)
                writer.writeStartElement(this.prefix, this.localName, this.namespaceURI);
            else
                writer.writeStartElement(this.localName);
            writer.setNamespaceContext(namespaceContext);
            for (int i = 0; i < this.nsPrefix.length; i++)
                writer.writeNamespace(this.nsPrefix[i], this.nsURI[i]);
            for (int i = 0; i < this.attrPrefix.length; i++) {
                if (this.attrNamespace[i] != null)
                    writer.writeAttribute(this.attrPrefix[i], this.attrNamespace[i], this.attrLocalName[i], this.attrValue[i]);
                else
                    writer.writeAttribute(this.attrLocalName[i], this.attrValue[i]);
            }
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append("StartElement[");
            buf.append("prefix=\"").append(this.prefix).append("\"");
            buf.append(",localName=\"").append(this.localName).append("\"");
            buf.append(",namespaceURI=\"").append(this.namespaceURI).append("\"");
            buf.append(",namespaceContext=\"").append(this.namespaceContext).append("\"");
            buf.append(",attrPrefix=").append(Arrays.asList(this.attrPrefix));
            buf.append(",attrNamespace=").append(Arrays.asList(this.attrNamespace));
            buf.append(",attrLocalName=").append(Arrays.asList(this.attrLocalName));
            buf.append(",attrValue=").append(Arrays.asList(this.attrValue));
            buf.append("]");
            return buf.toString();
        }
    }

    private static final class EndElementAction extends StreamWriterAction {

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeEndElement();
        }

        @Override
        public String toString() {
            return "EndElement";
        }
    }

    private static final class PIAction extends StreamWriterAction {

        private final String target;
        private final String data;

        PIAction(XMLStreamReader reader) {
            this.target = reader.getPITarget();
            this.data = reader.getPIData();
        }

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeProcessingInstruction(this.target, this.data);
        }

        @Override
        public String toString() {
            return "PI[target=\"" + this.target + "\",data=\"" + this.data + "\"]";
        }
    }

    private static class CharactersAction extends StreamWriterAction {

        protected final String text;

        CharactersAction(XMLStreamReader reader) {
            this.text = reader.getText();
        }

        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeCharacters(this.text);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName().replaceAll("Action$", "") + "[text="
              + StringEncoder.enquote(this.text) + "]";
        }
    }

    private static final class CommentAction extends CharactersAction {

        CommentAction(XMLStreamReader reader) {
            super(reader);
        }

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeComment(this.text);
        }
    }

    private static final class DTDAction extends CharactersAction {

        DTDAction(XMLStreamReader reader) {
            super(reader);
        }

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeDTD(this.text);
        }
    }

    private static final class CDataAction extends CharactersAction {

        CDataAction(XMLStreamReader reader) {
            super(reader);
        }

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeCData(this.text);
        }
    }

    private static final class StartDocumentAction extends StreamWriterAction {

        protected final String encoding;
        protected final String version;

        StartDocumentAction(XMLStreamReader reader) {
            this.encoding = reader.getEncoding();
            this.version = reader.getVersion();
        }

        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeStartDocument(this.encoding, this.version);
        }

        @Override
        public String toString() {
            return "StartDocument[encoding=\"" + this.encoding + "\",version=\"" + this.version + "\"]";
        }
    }

    private static final class EndDocumentAction extends StreamWriterAction {

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeEndDocument();
            writer.flush();
        }

        @Override
        public String toString() {
            return "EndDocument";
        }
    }

    private static final class EntityRefAction extends StreamWriterAction {

        private final String localName;

        EntityRefAction(XMLStreamReader reader) {
            this.localName = reader.getLocalName();
        }

        public void apply(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeEntityRef(this.localName);
        }

        @Override
        public String toString() {
            return "EntityRef[\"" + this.localName + "\"]";
        }
    }

    private static final class EmptyAction extends StreamWriterAction {

        @Override
        public void apply(XMLStreamWriter writer) throws XMLStreamException {
        }

        @Override
        public String toString() {
            return "Empty";
        }
    }
}
