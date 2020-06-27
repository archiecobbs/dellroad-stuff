
/*
 * Copyright (C) 2012 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;

import org.w3c.dom.Document;

/**
 * Represents an open "transaction" on a {@link PersistentObject}'s persistent file.
 *
 * <p>
 * This class is used by {@link PersistentObjectSchemaUpdater} and would normally not be used directly.
 */
public class PersistentFileTransaction {

    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    private final ArrayList<String> updates = new ArrayList<String>();
    private final String systemId;

    private Document current;

    /**
     * Constructor.
     *
     * @param source XML input
     * @throws PersistentObjectException if no updates are found
     * @throws IllegalArgumentException if {@code source} is null
     * @throws XMLStreamException if XML parsing fails
     * @throws IOException if an I/O error occurs
     */
    public PersistentFileTransaction(Source source) throws IOException, XMLStreamException {
        this(source, null);
    }

    /**
     * Constructor.
     *
     * @param source XML input
     * @param transformerFactory transformer factory, or null for platform default
     * @throws PersistentObjectException if no updates are found
     * @throws IllegalArgumentException if {@code source} is null
     * @throws XMLStreamException if XML parsing fails
     * @throws IOException if an I/O error occurs
     */
    public PersistentFileTransaction(Source source, TransformerFactory transformerFactory) throws IOException, XMLStreamException {
        if (source == null)
            throw new IllegalArgumentException("null source");
        if (transformerFactory == null)
            transformerFactory = TransformerFactory.newInstance();
        this.systemId = source.getSystemId();
        this.read(source, transformerFactory);
    }

    /**
     * Get the current XML data. Does not include the XML update list.
     *
     * <p>
     * During a schema update the caller may modify the document in place, or invoke {@link #setData} to completely replace it.
     *
     * @return XML data
     */
    public Document getData() {
        return this.current;
    }

    /**
     * Change the current XML data.
     *
     * <p>
     * This would be invoked by a schema update if it wanted to completely replace the XML document,
     * rather than just modifying it in place.
     *
     * @param data new XML data; does not include the XML update list
     */
    public void setData(final Document data) {
        this.current = data;
    }

    /**
     * Get the system ID of the original source input.
     *
     * @return XML system ID
     */
    public String getSystemId() {
        return this.systemId;
    }

    /**
     * Get the updates list associated with this transaction.
     *
     * @return list of updates
     */
    public List<String> getUpdates() {
        return this.updates;
    }

    /**
     * Apply an XSLT transform to the current XML object in this transaction.
     *
     * <p>
     * This is an alternative to modifying the DOM, when XSL is a simpler way to perform the update.
     *
     * @param transformer XSLT transformer
     * @throws IllegalStateException if the current root object is null
     * @throws PersistentObjectException if an error occurs
     * @throws TransformerException if the transformation fails
     */
    public void transform(Transformer transformer) throws TransformerException {

        // Sanity check
        if (this.current == null)
            throw new PersistentObjectException("no data to transform");

        // Set up source and result
        final DOMSource source = new DOMSource(this.current, this.systemId);
        final DOMResult result = new DOMResult();
        result.setSystemId(this.systemId);

        // Apply transform
        transformer.transform(source, result);

        // Save result as the new current value
        this.setData((Document)result.getNode());
    }

    private void read(Source input, TransformerFactory transformerFactory) throws IOException, XMLStreamException {

        // Read in XML into memory, extracting and removing the updates list in the process
        final UpdatesXMLStreamReader reader = new UpdatesXMLStreamReader(this.xmlInputFactory.createXMLStreamReader(input));
        final StAXSource source = new StAXSource(reader);
        final DOMResult result = new DOMResult();
        result.setSystemId(this.systemId);
        try {
            transformerFactory.newTransformer().transform(source, result);
        } catch (TransformerException e) {
            throw new XMLStreamException("error reading XML input from " + this.systemId, e);
        }
        reader.close();

        // Was the update list found?
        final List<String> updateNames = reader.getUpdates();
        if (updateNames == null)
            throw new PersistentObjectException("XML file does not contain an updates list");

        // Save current content (without updates) and updates list
        this.current = (Document)result.getNode();
        this.updates.clear();
        this.updates.addAll(updateNames);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + this.systemId + "]";
    }
}
