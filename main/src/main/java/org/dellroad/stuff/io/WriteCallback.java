
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Callback interface used to write output to an {@link OutputStream}.
 */
@FunctionalInterface
public interface WriteCallback {

    /**
     * Write the output to the given output stream.
     *
     * <p>
     * This method should not {@link OutputStream#close close()} the given {@code output}.
     *
     * @param output output stream
     * @throws IOException if an I/O error occurs
     */
    void writeTo(OutputStream output) throws IOException;
}
