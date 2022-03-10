
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import com.google.common.base.Preconditions;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Optional;

/**
 * A {@link Reader} that wraps an {@link InputStream}, detects and strips the byte order mark, and then converts
 * bytes into characters accordingly.
 *
 * <p>
 * If no byte order mark is found, the input is implicitly assumed to be in some default character encoding.
 *
 * <p>
 * The default character encoding and the action to take on malformed input are configurable.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Byte_order_mark">Byte order mark</a>
 */
public class BOMReader extends Reader {

    static final int BUFFER_SIZE = 4080;

    private static final int MAX_BOM_LENGTH = 16;

    private final BufferedInputStream input;
    private final CodingErrorAction errorAction;
    private final Charset defaultCharset;

    private InputStreamReader reader;
    private BOM bom;

// Constructors

    /**
     * Constructor.
     *
     * <p>
     * Equivalent to {@link #BOMReader(InputStream CodingErrorAction)
     *  BOMReader(input, CodingErrorAction.REPORT, Charset.defaultCharset())}.
     *
     * @param input data input
     * @throws IllegalArgumentException if {@code input} is null
     */
    public BOMReader(InputStream input) {
        this(input, CodingErrorAction.REPORT, Charset.defaultCharset());
    }

    /**
     * Primary constructor.
     *
     * <p>
     * The {@code errorAction} configures the behavior when malformed input is encountered; in the case of
     * {@link CodingErrorAction#REPORT}, a {@link java.nio.charset.MalformedInputException} exception is thrown.
     *
     * @param input data input
     * @param errorAction what to do about malformed input
     * @param defaultCharset character encoding to use if no BOM is found
     * @throws IllegalArgumentException if any parameter is null
     */
    public BOMReader(InputStream input, CodingErrorAction errorAction, Charset defaultCharset) {
        Preconditions.checkArgument(input != null, "null input");
        Preconditions.checkArgument(errorAction != null, "null errorAction");
        Preconditions.checkArgument(defaultCharset != null, "null defaultCharset");
        this.input = new BufferedInputStream(input, BUFFER_SIZE);
        this.errorAction = errorAction;
        this.defaultCharset = defaultCharset;
        this.input.mark(MAX_BOM_LENGTH);
    }

// Methods

    /**
     * Report the {@link BOM} found at the beginning of the input.
     *
     * <p>
     * If no input has been read yet, this method will trigger the reading of the first few bytes.
     *
     * @return the BOM that was detected, or null if no supported BOM was found
     */
    public BOM getBOM() throws IOException {
        this.getReader();
        return this.bom;
    }

// Reader

    @Override
    public void close() throws IOException {
        if (this.reader != null)
            this.reader.close();
        else
            this.input.close();
    }

    @Override
    public void mark(int limit) throws IOException {
        this.getReader().mark(limit);
    }

    @Override
    public int read() throws IOException {
        return this.getReader().read();
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        return this.getReader().read(cbuf);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        return this.getReader().read(cbuf, off, len);
    }

    @Override
    public int read(CharBuffer target) throws IOException {
        return this.getReader().read(target);
    }

    @Override
    public boolean ready() throws IOException {
        return this.getReader().ready();
    }

    @Override
    public void reset() throws IOException {
        this.getReader().reset();
    }

    @Override
    public long skip(long num) throws IOException {
        return this.getReader().skip(num);
    }

// Internal Methods

    private Reader getReader() throws IOException {

        // Already detected?
        if (this.reader != null)
            return this.reader;
        assert this.bom == null;

        // Try to detect the BOM
        final EnumSet<BOM> remainingBOMs = EnumSet.allOf(BOM.class);
        int bytesRead = 0;
        do {

            // Read next byte
            final int r = this.input.read();
            if (r == -1)
                break;
            final byte nextByte = (byte)r;
            bytesRead++;

            // Eliminate BOM's that no longer match, and detect if any BOM now does match
            for (Iterator<BOM> i = remainingBOMs.iterator(); i.hasNext(); ) {
                final BOM remainingBOM = i.next();
                final byte[] bytes = remainingBOM.getSignature();
                if (bytes[bytesRead - 1] != nextByte) {
                    i.remove();                                     // we just eliminated this BOM
                    continue;
                } else if (bytesRead == bytes.length) {
                    this.bom = remainingBOM;                        // we just matched this BOM
                    break;
                }
            }
        } while (this.bom == null && !remainingBOMs.isEmpty());

        // If we did not detect a BOM, unread the bytes we just read
        if (this.bom == null)
            this.input.reset();

        // Build an appropriate decoder
        final CharsetDecoder decoder = Optional.ofNullable(this.bom)
          .map(BOM::getCharset)
          .orElse(this.defaultCharset)
          .newDecoder()
          .onMalformedInput(this.errorAction);

        // Build decoding reader
        this.reader = new InputStreamReader(this.input, decoder);

        // Done
        return this.reader;
    }
}
