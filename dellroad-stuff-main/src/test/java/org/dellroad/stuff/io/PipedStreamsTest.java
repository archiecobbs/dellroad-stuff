
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PipedStreamsTest extends TestSupport {

    @Test
    public void testPipedStreams() throws Exception {

        final PipedStreams ps = new PipedStreams(13);

        final MessageDigest readerDigest = this.newSHA1();
        final MessageDigest writerDigest = this.newSHA1();

        final AtomicReference<Throwable> readerError = new AtomicReference<>();
        final AtomicReference<Throwable> writerError = new AtomicReference<>();
        final AtomicBoolean readerClosed = new AtomicBoolean();
        final AtomicBoolean writerClosed = new AtomicBoolean();

        // Reader
        final Thread writer = new Thread(() -> {
            try (final OutputStream out = ps.getOutputStream()) {
                for (int i = 0; i < 1000; i++) {
                    if (this.random.nextInt(10) == 7) {
                        final byte b = (byte)this.random.nextInt(256);
                        writerDigest.update(b);
                        out.write(b);
                    } else {
                        final int num = this.random.nextInt(32);            // from zero to 31
                        final int off = this.random.nextInt(num + 1);       // from zero to 31
                        final int len = off == num ? 0 : this.random.nextInt(num - off);
                        final byte[] data = new byte[num];
                        this.random.nextBytes(data);
                        writerDigest.update(data, off, len);
                        out.write(data, off, len);
                    }
                    if (this.random.nextInt(10) == 7)
                        Thread.sleep(this.random.nextInt(37));
                    if (this.random.nextInt(1000) == 1) {
                        writerClosed.set(true);
                        break;
                    }
                }
            } catch (Throwable t) {
                this.log.info(this.getClass().getSimpleName() + " writer error", t);
                writerError.set(t);
            }
        }, this.getClass().getSimpleName() + " writer");

        // Writer
        final Thread reader = new Thread(() -> {
            try (final InputStream in = ps.getInputStream()) {
                while (true) {
                    if (this.random.nextInt(10) == 7) {
                        final int b = in.read();
                        if (b == -1)
                            break;
                        readerDigest.update((byte)b);
                    } else {
                        final int num = this.random.nextInt(32);            // from zero to 31
                        final int off = this.random.nextInt(num + 1);       // from zero to 31
                        final int len = off == num ? 0 : this.random.nextInt(num - off);
                        final byte[] data = new byte[num];
                        final int r = in.read(data, off, len);
                        if (r == -1)
                            break;
                        readerDigest.update(data, off, r);
                    }
                    if (this.random.nextInt(10) == 7)
                        Thread.sleep(this.random.nextInt(37));
                    if (this.random.nextInt(1000) == 1) {
                        readerClosed.set(true);
                        break;
                    }
                }
            } catch (Throwable t) {
                this.log.info(this.getClass().getSimpleName() + " reader error", t);
                readerError.set(t);
            }
        }, this.getClass().getSimpleName() + " reader");

        // Run threads
        writer.start();
        reader.start();
        writer.join();
        reader.join();

        // Check results
        if (readerError.get() != null) {
            assert writerClosed.get() : "writer didn't close() but reader got " + readerError.get();
            return;
        }
        if (writerError.get() != null) {
            assert readerClosed.get() : "reader didn't close() but writer got " + writerError.get();
            return;
        }
        Assert.assertEquals(readerDigest.digest(), writerDigest.digest());
    }

    public MessageDigest newSHA1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
