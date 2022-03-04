
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dellroad.stuff.test.TestSupport;
import org.testng.annotations.Test;

public class NullModemTest extends TestSupport implements ReadCallback, WriteCallback {

    private byte[] inputData;
    private byte[] outputData;

// NullModemOutputStream

    @Test
    public synchronized void testOutput() throws Exception {

        // Create data
        byte[] data = new byte[this.random.nextInt(1000)];
        this.random.nextBytes(data);

        // Write data
        NullModemOutputStream output = new NullModemOutputStream(this, "Null Output");
        DataOutputStream dataOutput = new DataOutputStream(output);
        dataOutput.write(data);
        dataOutput.close();

        // Wait for reader to finish reading
        synchronized (this) {
            while (this.outputData == null)
                this.wait();
        }

        // Check
        assert Arrays.equals(this.outputData, data);
    }

    @Override
    public void readFrom(InputStream input) throws IOException {
        this.outputData = StreamsTest.readAll(input);
        synchronized (this) {
            this.notifyAll();
        }
    }

// NullModemInputStream

    @Test
    public synchronized void testInput() throws IOException {

        // Create data
        this.inputData = new byte[this.random.nextInt(1000)];
        this.random.nextBytes(this.inputData);

        // Read data
        NullModemInputStream input = new NullModemInputStream(this, "Null Input");
        DataInputStream dataInput = new DataInputStream(input);
        byte[] data = StreamsTest.readAll(dataInput);
        dataInput.close();

        // Check
        assert Arrays.equals(data, this.inputData);
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        DataOutputStream dataOutput = new DataOutputStream(output);
        dataOutput.write(this.inputData);
        dataOutput.flush();
    }

// Exception Tests

    @Test
    public void testCloseNullModemOutputStream() throws Exception {
        final ReadCallback reader = input -> {
            this.log.debug("testCloseNullModemOutputStream(): reader thread closing input...");
            input.close();
        };
        try (NullModemOutputStream output = new NullModemOutputStream(reader, "Null Output 2")) {
            this.log.debug("testCloseNullModemOutputStream(): main thread sleeping 200ms...");
            Thread.sleep(200);                  // give reader thread time to run
            this.log.debug("testCloseNullModemOutputStream(): main thread writing to output...");
            output.write(0);
            assert false : "No exception";
        } catch (IOException e) {
            this.log.debug("testCloseNullModemOutputStream(): got expected exception", e);
        }
    }

    @Test
    public void testErrorNullModemOutputStream() throws Exception {
        final ReadCallback reader = input -> {
            this.log.debug("testErrorNullModemOutputStream(): reader thread throwing exception...");
            throw new IOException("testErrorNullModemOutputStream() exception");
        };
        final NullModemOutputStream output = new NullModemOutputStream(reader, "Null Output 3");
        try {
            this.log.debug("testErrorNullModemOutputStream(): main thread sleeping 200ms...");
            Thread.sleep(200);                  // give reader thread time to run
            this.log.debug("testErrorNullModemOutputStream(): main thread writing to output...");
            output.write(0);
            assert false : "No exception";
        } catch (IOException e) {
            this.log.debug("testErrorNullModemOutputStream(): got expected exception", e);
        }
        output.close();
    }

    @Test
    public void testCloseNullModemInputStream() throws Exception {
        final AtomicBoolean success = new AtomicBoolean();
        final WriteCallback writer = output -> {
            try {
                this.log.debug("testCloseNullModemInputStream(): writer thread sleeping 100ms...");
                Thread.sleep(100);                  // give input side time to close
                this.log.debug("testCloseNullModemInputStream(): writer thread writing to output...");
                output.write(0);
            } catch (IOException e) {
                this.log.debug("testCloseNullModemInputStream(): got expected exception", e);
                success.set(true);
                throw e;
            } catch (InterruptedException e) {
                this.log.error("testCloseNullModemInputStream(): unexpected exception", e);
            }
        };
        new NullModemInputStream(writer, "Null Input 2").close();
        this.log.debug("testCloseNullModemInputStream(): main thread sleeping 200ms...");
        Thread.sleep(200);                          // give writer time to run
        this.log.debug("testCloseNullModemInputStream(): main thread waking up...");
        assert success.get() : "failed to detect exception in writer";
    }

    @Test
    public void testErrorNullModemInputStream() throws Exception {
        final WriteCallback writer = output -> {
            this.log.debug("testErrorNullModemInputStream(): writer thread throwing exception...");
            throw new IOException("testErrorNullModemInputStream() exception");
        };
        final NullModemInputStream input = new NullModemInputStream(writer, "Null Input 2");
        try {
            this.log.debug("testErrorNullModemInputStream(): main thread sleeping 200ms...");
            Thread.sleep(200);                          // give writer time to throw exception
            this.log.debug("testErrorNullModemInputStream(): main thread reading from input...");
            int r = input.read();
            assert false : "No exception (read byte " + r + " instead)";
        } catch (IOException e) {
            this.log.debug("testErrorNullModemInputStream(): got expected exception", e);
        }
        input.close();
    }
}
