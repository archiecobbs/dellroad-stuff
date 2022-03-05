
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.xml;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Wrapper for an underlying {@link XMLStreamWriter} that "pretty-prints" the XML by replacing the whitespace between XML tags
 * so that the result is properly indented.
 *
 * <p>
 * This class will also fixup a missing/incomplete initial XML declaration.
 */
public class IndentXMLStreamWriter extends StreamWriterDelegate {

    /**
     * Initial value for the {@linkplain #setDefaultVersion default XML version}.
     */
    public static final String DEFAULT_VERSION = "1.0";

    /**
     * Initial value for the {@linkplain #setDefaultEncoding default character encoding}.
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Default number of spaces corresponding to one indent level.
     */
    public static final int DEFAULT_INDENT = 4;

    private final String newline = System.getProperty("line.separator", "\\n");
    private final StringBuilder whitespaceBuffer = new StringBuilder();
    private final int indent;

    private boolean addMissingXmlDeclaration = true;
    private boolean indentAfterXmlDeclaration = true;
    private String defaultVersion = DEFAULT_VERSION;
    private String defaultEncoding = DEFAULT_ENCODING;

    private boolean started;
    private int lastEvent = -1;
    private int depth;

// Constructors

    /**
     * Default constructor. Sets the indent to {@link #DEFAULT_INDENT}.
     * The parent must be configured via {@link #setParent setParent()}.
     */
    public IndentXMLStreamWriter() {
        this.indent = DEFAULT_INDENT;
    }

    /**
     * Convenience constructor. Equivalent to:
     * <blockquote>
     *  {@link #IndentXMLStreamWriter(XMLStreamWriter, int) IndentXMLStreamWriter}{@code (writer, }{@link #DEFAULT_INDENT}{@code )};
     * </blockquote>
     *
     * @param writer underlying writer
     */
    public IndentXMLStreamWriter(XMLStreamWriter writer) {
        this(writer, DEFAULT_INDENT);
    }

    /**
     * Primary constructor.
     *
     * @param writer underlying writer
     * @param indent number of spaces corresponding to one indent level, or negative for no inter-tag whitespace at all
     */
    public IndentXMLStreamWriter(XMLStreamWriter writer, int indent) {
        super(writer);
        this.indent = indent;
    }

// Config Properties

    /**
     * Set whether to add an XML declaration, if missing.
     *
     * <p>
     * Default is true.
     *
     * @param addMissingXmlDeclaration true to add XML declaration
     */
    public void setAddMissingXmlDeclaration(boolean addMissingXmlDeclaration) {
        this.addMissingXmlDeclaration = addMissingXmlDeclaration;
    }

    /**
     * Set whether to "indent" (actually, just output a newline) after the XML declaration if necessary.
     * In some cases, such as transforming into a DOM, this behavior must be disabled to avoid hierarchy
     * exceptions due to characters not being allowed before the document element.
     *
     * <p>
     * Default is true.
     *
     * @param indentAfterXmlDeclaration true to indent after XML declaration
     */
    public void setIndentAfterXmlDeclaration(boolean indentAfterXmlDeclaration) {
        this.indentAfterXmlDeclaration = indentAfterXmlDeclaration;
    }

    /**
     * Set the version for the XML declaration in case it's not already specified.
     *
     * <p>
     * Default is {@link #DEFAULT_VERSION} ({@value #DEFAULT_VERSION}).
     *
     * @param defaultVersion XML version
     */
    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    /**
     * Set the character encoding for the XML declaration in case it's not already specified.
     *
     * <p>
     * Default is {@link #DEFAULT_ENCODING} ({@value #DEFAULT_ENCODING}).
     *
     * @param defaultEncoding character encoding name
     */
    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

// XMLStreamWriter

    @Override
    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        if (this.started)
            return;
        this.started = true;
        this.handleOther(XMLStreamConstants.START_DOCUMENT);
        super.writeStartDocument(encoding, version);
    }

    @Override
    public void writeStartDocument(String version) throws XMLStreamException {
        this.writeStartDocument(this.defaultEncoding, version);
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        this.writeStartDocument(this.defaultEncoding, this.defaultVersion);
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        this.handleStartElement(false);
        super.writeStartElement(localName);
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        this.handleStartElement(false);
        super.writeStartElement(namespaceURI, localName);
    }

    @Override
    public void writeStartElement(String prefix, String namespaceURI, String localName) throws XMLStreamException {
        this.handleStartElement(false);
        super.writeStartElement(prefix, namespaceURI, localName);
    }

    @Override
    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        this.handleStartElement(true);
        super.writeEmptyElement(namespaceURI, localName);
    }

    @Override
    public void writeEmptyElement(String prefix, String namespaceURI, String localName) throws XMLStreamException {
        this.handleStartElement(true);
        super.writeEmptyElement(prefix, namespaceURI, localName);
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        this.handleStartElement(true);
        super.writeEmptyElement(localName);
    }

    @Override
    public void writeComment(String data) throws XMLStreamException {
        this.handleComment(data);
    }

    @Override
    public void writeProcessingInstruction(String target) throws XMLStreamException {
        this.handleOther(XMLStreamConstants.PROCESSING_INSTRUCTION);
        super.writeProcessingInstruction(target);
    }

    @Override
    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        this.handleOther(XMLStreamConstants.PROCESSING_INSTRUCTION);
        super.writeProcessingInstruction(target, data);
    }

    @Override
    public void writeCData(String data) throws XMLStreamException {
        this.handleOther(XMLStreamConstants.CDATA);
        super.writeCData(data);
    }

    @Override
    public void writeDTD(String dtd) throws XMLStreamException {
        this.handleOther(XMLStreamConstants.DTD);
        super.writeDTD(dtd);
    }

    @Override
    public void writeEntityRef(String name) throws XMLStreamException {
        this.handleOther(XMLStreamConstants.ENTITY_REFERENCE);
        super.writeEntityRef(name);
    }

    @Override
    public void writeCharacters(String text) throws XMLStreamException {
        this.handleCharacters(text);
    }

    @Override
    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        this.writeCharacters(new String(text, start, len));
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        while (this.depth > 0) {
            this.writeEndElement();
            this.depth--;
        }
        super.writeEndDocument();
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        this.depth--;
        if (this.lastEvent == XMLStreamConstants.END_ELEMENT)
            this.reindent();
        this.handleOther(XMLStreamConstants.END_ELEMENT);
        super.writeEndElement();
    }

// Internal Methods

    private void handleStartElement(boolean selfClosing) throws XMLStreamException {
        this.reindentIfNecessary();
        this.handleOther(selfClosing ? XMLStreamConstants.END_ELEMENT : XMLStreamConstants.START_ELEMENT);
        if (!selfClosing)
            this.depth++;
    }

    private void handleCharacters(String text) throws XMLStreamException {
        this.writeStartDocumentIfNecessary();
        if ((this.lastEvent == XMLStreamConstants.START_ELEMENT || this.lastEvent == XMLStreamConstants.END_ELEMENT)
          && text.trim().length() == 0)
            this.whitespaceBuffer.append(text);
        else {
            this.handleOther(XMLStreamConstants.CHARACTERS);
            super.writeCharacters(text);
        }
    }

    private void handleComment(String comment) throws XMLStreamException {

        // If no newline precedes the comment, don't try to reformat anything
        if (this.newlinesInWhitespaceBuffer() == 0) {
            this.handleOther(XMLStreamConstants.COMMENT);
            super.writeComment(comment);
        }

        // Do the same reformatting we do with elements
        this.reindentIfNecessary();

        // Handle a one line comment
        if (comment.indexOf('\n') == -1) {
            super.writeComment(comment);
            return;
        }

        // Handle multi-line comment
        final String indentation = this.indentString(this.depth + 1);
        final String[] lines = comment.split("\\r?\\n");
        final int num = lines.length;
        for (int i = 0; i < num; i++)                           // trim all lines
            lines[i] = lines[i].trim();
        if (lines.length > 0 && !lines[0].isEmpty())            // add space after "<!--" if needed
            lines[0] = " " + lines[0];
        if (lines.length > 0 && !lines[num - 1].isEmpty())      // add space before "-->" if needed
            lines[num - 1] = lines[0] + " ";
        for (int i = 1; i < num - 1; i++)                       // indent all but first & last line to depth + 1
            lines[i] = indentation + lines[i];
        if (num > 1)                                            // indent the last only to depth
            lines[num - 1] = this.indentString(this.depth) + lines[num - 1];
        comment = Stream.of(lines).collect(Collectors.joining("\n"));
        super.writeComment(comment);
    }

    private void handleOther(int eventType) throws XMLStreamException {
        this.writeStartDocumentIfNecessary();
        this.flushWitespace();
        this.lastEvent = eventType;
    }

    private void reindentIfNecessary() throws XMLStreamException {
        this.writeStartDocumentIfNecessary();
        if (this.lastEvent == XMLStreamConstants.START_ELEMENT
          || this.lastEvent == XMLStreamConstants.END_ELEMENT
          || (this.lastEvent == XMLStreamConstants.START_DOCUMENT && this.indentAfterXmlDeclaration)) {
            this.reindent();
            this.flushWitespace();
        }
    }

    private void flushWitespace() throws XMLStreamException {
        if (this.whitespaceBuffer.length() > 0) {
            super.writeCharacters(this.whitespaceBuffer.toString());
            this.whitespaceBuffer.setLength(0);
        }
    }

    private void writeStartDocumentIfNecessary() throws XMLStreamException {
        if (!this.started && this.addMissingXmlDeclaration)
            this.writeStartDocument();
    }

    /**
     * Replace existing content of whitespaceBuffer with newline(s) followed by indentation to the current depth.
     */
    private void reindent() {

        // Are we doing any indenting?
        if (this.indent < 0) {
            this.whitespaceBuffer.setLength(0);
            return;
        }

        // Count how many initial newlines there were in the original stream
        int newlines = this.newlinesInWhitespaceBuffer();

        // Add back that many newline(s) (at least one) followed by indent
        this.whitespaceBuffer.setLength(0);
        this.whitespaceBuffer.append(this.repeatString('\n', Math.max(newlines, 1)));
        this.whitespaceBuffer.append(this.indentString(this.depth));
    }

    // Count how many newlines there are in the current whitespace buffer
    private int newlinesInWhitespaceBuffer() {
        int newlines = 0;
        for (int i = 0; i < this.whitespaceBuffer.length(); i++) {
            if (this.whitespaceBuffer.charAt(i) == '\n'
              || (this.whitespaceBuffer.charAt(i++) == '\r'
               && i < this.whitespaceBuffer.length() && this.whitespaceBuffer.charAt(i) == '\n')) {
                newlines++;
                continue;
            }
            break;
        }
        return newlines;
    }

    private String indentString(int count) {
        return this.repeatString(' ', Math.max(count, 0) * this.indent);
    }

    private String repeatString(char ch, int count) {
        final char[] buf = new char[count];
        Arrays.fill(buf, ch);
        return new String(buf);
    }
}
