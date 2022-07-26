
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AtomicUpdateFileOutputStreamTest extends TestSupport {

    private final String content1 = "foobar1";
    private final String content2 = "janfu2";
    private final File targetFile;

    public AtomicUpdateFileOutputStreamTest() throws Exception {
        this.targetFile = File.createTempFile(this.getClass().getSimpleName(), null);
    }

// Cleanup

    @AfterClass
    public void cleanupTargetFile() throws Exception {
        this.targetFile.delete();
    }

// Tests

    @Test
    public synchronized void testOK() throws Exception {
        this.resetTargetFile(this.content1);
        AtomicUpdateFileOutputStream out2;
        try (AtomicUpdateFileOutputStream out = new AtomicUpdateFileOutputStream(this.targetFile)) {
            out2 = out;
            Assert.assertEquals(out.getState(), AtomicUpdateFileOutputStream.OPEN);
            out.write(this.content2.getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertEquals(out2.getState(), AtomicUpdateFileOutputStream.CLOSED);
        Assert.assertEquals(this.readTargetFile(), this.content2);
    }

    @Test
    public synchronized void testCancel() throws Exception {
        this.resetTargetFile(this.content1);
        AtomicUpdateFileOutputStream out2;
        try (AtomicUpdateFileOutputStream out = new AtomicUpdateFileOutputStream(this.targetFile)) {
            out2 = out;
            Assert.assertEquals(out.getState(), AtomicUpdateFileOutputStream.OPEN);
            out.write(this.content2.getBytes(StandardCharsets.UTF_8));
            Assert.assertEquals(out.getState(), AtomicUpdateFileOutputStream.OPEN);
            final boolean canceled1 = out.cancel();
            assert canceled1;
            Assert.assertEquals(out.getState(), AtomicUpdateFileOutputStream.CANCELED);
            final boolean canceled2 = out.cancel();
            assert !canceled2;
            Assert.assertEquals(out.getState(), AtomicUpdateFileOutputStream.CANCELED);
        }
        Assert.assertEquals(out2.getState(), AtomicUpdateFileOutputStream.CANCELED);
        Assert.assertEquals(this.readTargetFile(), this.content1);
    }

    @Test
    public synchronized void testError() throws Exception {
        this.resetTargetFile(this.content1);
        AtomicUpdateFileOutputStream out2 = null;
        IOException exception = null;
        try (AtomicUpdateFileOutputStream out = new AtomicUpdateFileOutputStream(this.targetFile)) {
            out2 = out;
            Assert.assertEquals(out.getState(), AtomicUpdateFileOutputStream.OPEN);
            out.write(this.content2.getBytes(StandardCharsets.UTF_8));
            Files.delete(out.getTempFile().toPath());             // to cause an error on close()
        } catch (IOException e) {
            this.log.debug("got expected {}", e.toString());
            exception = e;
        }
        Assert.assertNotNull(exception, "didn't get expected exception");
        Assert.assertEquals(out2.getState(), AtomicUpdateFileOutputStream.CANCELED);
        Assert.assertEquals(this.readTargetFile(), this.content1);
    }

// Internal methods

    public void resetTargetFile(String content) throws Exception {
        try (FileOutputStream out = new FileOutputStream(this.targetFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertEquals(this.readTargetFile(), content);
    }

    public String readTargetFile() throws Exception {
        return new String(Files.readAllBytes(this.targetFile.toPath()), StandardCharsets.UTF_8);
    }
}
