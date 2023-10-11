
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Callback interface used to read input from an {@link InputStream}.
 */
@FunctionalInterface
public interface ReadCallback {

    /**
     * Read from the given input stream.
     *
     * <p>
     * This method should not {@link InputStream#close close()} the given {@code input}.
     *
     * @param input input stream
     * @throws IOException if an I/O error occurs
     */
    void readFrom(InputStream input) throws IOException;
}
