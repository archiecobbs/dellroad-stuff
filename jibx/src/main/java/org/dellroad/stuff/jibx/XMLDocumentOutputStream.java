
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.dellroad.stuff.io.OutputStreamWriter;
import org.jibx.runtime.JiBXException;

/**
 * {@link OutputStream} over which XML documents are passed. This class is a companion to {@link XMLDocumentInputStream}.
 *
 * <p>
 * XML documents are created from Java objects via {@link JiBXUtil#writeObject(Object, OutputStream) JiBXUtil.writeObject()}.
 *
 * <p>
 * Instances of this class are thread-safe.
 *
 * @param <T> XML document type
 * @see XMLDocumentInputStream
 */
public class XMLDocumentOutputStream<T> {

    private final OutputStreamWriter output;

    /**
     * Constructor.
     *
     * @param output data destination
     */
    public XMLDocumentOutputStream(OutputStream output) {
        if (output == null)
            throw new IllegalArgumentException("null output");
        this.output = new OutputStreamWriter(new BufferedOutputStream(output));
    }

    /**
     * Write the object encoded as XML to the underlying output stream.
     * The underlying output stream is flushed.
     *
     * @param obj object to write
     * @throws IOException if an I/O error occurs
     * @throws JiBXException if JiBX encoding fails
     */
    public synchronized void write(T obj) throws IOException, JiBXException {
        this.output.start();
        JiBXUtil.writeObject(obj, this.output);
        this.output.stop();
    }

    /**
     * Close the underlying output stream.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        this.output.close();
    }
}

