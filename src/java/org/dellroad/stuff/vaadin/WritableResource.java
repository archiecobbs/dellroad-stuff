
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin;

import com.vaadin.terminal.StreamResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.dellroad.stuff.io.NullModemInputStream;
import org.dellroad.stuff.io.WriteCallback;

/**
 * Support superclass for {@link StreamResource} implementations that can be more easily implemented
 * by writing to an {@link OutputStream} than providing an {@link InputStream}.
 *
 * @see <a href="http://dev.vaadin.com/ticket/5145">Vaadin Ticket #5145</a>
 */
@SuppressWarnings("serial")
public abstract class WritableResource extends StreamResource implements WriteCallback {

    /**
     * Constructor.
     *
     * @param filename resource file name
     */
    protected WritableResource(final String filename) {
        super(null, filename, ContextApplication.currentApplication());
        this.setStreamSource(new StreamResource.StreamSource() {
            @Override
            public InputStream getStream() {
                return new NullModemInputStream(WritableResource.this, "WritableResource for \"" + filename + "\"");
            }
        });
    }

    /**
     * Convenience constructor that also sets the MIME type.
     *
     * @param filename resource file name
     * @param mimeType resource MIME type
     */
    protected WritableResource(String filename, String mimeType) {
        this(filename);
        this.setMIMEType(mimeType);
    }

    /**
     * Write the resource contents to the given {@link OutputStream} and then close it.
     *
     * <p>
     * Note that the {@link com.vaadin.Application} associated with this resource will <b>not</b> be locked
     * when this method is invoked.
     *
     * @param output resource output stream
     * @throws IOException if an I/O error occurs
     */
    @Override
    public abstract void writeTo(OutputStream output) throws IOException;
}

