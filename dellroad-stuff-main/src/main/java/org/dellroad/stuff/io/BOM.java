
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.dellroad.stuff.string.ByteArrayEncoder;

/**
 * Catalog of common byte order marks.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Byte_order_mark">Byte order mark</a>
 */
public enum BOM {
  //UTF_7("2b2f76"),                     /* not suported by Java */
    UTF_8("efbbbf"),
    UTF_16BE("feff"),
    UTF_16LE("fffe"),
    UTF_32BE("0000feff"),
    UTF_32LE("fffe0000"),
    GB18030("84319533");

    private final byte[] bytes;

    BOM(String bytes) {
        this.bytes = ByteArrayEncoder.decode(bytes);
    }

    /**
     * Get the byte signature for this instance.
     *
     * @return this BOM's byte signature (modifications to the returned array do not effect this instance)
     */
    public byte[] getSignature() {
        return this.bytes.clone();
    }

    /**
     * Get the character encoding that this BOM indicates.
     *
     * @return this BOM's implied character encoding
     */
    public Charset getCharset() {
        try {
            return Charset.forName(this.name().replace('_', '-'));
        } catch (UnsupportedCharsetException e) {
            throw new RuntimeException("unexpected error", e);
        }
    }
}
