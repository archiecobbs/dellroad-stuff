
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.dellroad.stuff.java.ThrowableUtil;

/**
 * Utility methods for {@link NullModemInputStream} and {@link NullModemOutputStream}.
 */
final class NullUtil {

    private NullUtil() {
    }

    public static void wrap(AtomicReference<Throwable> error, IO io) throws IOException {
        try {
            io.run();
        } catch (IOException e) {
            final Throwable cause = error.get();
            if (cause != null)
                ThrowableUtil.prependCurrentStackTrace(e);
            throw e;
        }
    }

    public static int wrapInt(AtomicReference<Throwable> error, IntIO io) throws IOException {
        try {
            return io.run();
        } catch (IOException e) {
            final Throwable cause = error.get();
            if (cause != null)
                ThrowableUtil.prependCurrentStackTrace(e);
            throw e;
        }
    }

    public static long wrapLong(AtomicReference<Throwable> error, LongIO io) throws IOException {
        try {
            return io.run();
        } catch (IOException e) {
            final Throwable cause = error.get();
            if (cause != null)
                ThrowableUtil.prependCurrentStackTrace(e);
            throw e;
        }
    }

    /**
     * Create an {@link Executor} that spawns a new {@link Thread}.
     *
     * @param name thread name
     * @throws IllegalArgumentException if {@code name} is null
     */
    public static Executor newThreadExecutor(String name) {
        if (name == null)
            throw new IllegalArgumentException("null name");
        return runnable -> {
            final Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.start();
        };
    }

// IOs

    @FunctionalInterface
    interface IO {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface IntIO {
        int run() throws IOException;
    }

    @FunctionalInterface
    interface LongIO {
        long run() throws IOException;
    }
}
