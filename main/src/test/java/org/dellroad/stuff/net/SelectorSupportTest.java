
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.net;

import java.io.IOException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SelectorSupportTest extends TestSupport {

    @Test
    public void testCloseDetect() throws Exception {

        final Pipe pipe = Pipe.open();
        final Pipe.SourceChannel input = pipe.source();
        final Pipe.SinkChannel output = pipe.sink();

        final SelectorSupport test = new SelectorSupport(input.provider());
        test.setHousekeepingInterval(100);
        test.start();

        final AtomicBoolean notified = new AtomicBoolean();
        final SelectorSupport.IOHandler handler = new SelectorSupport.IOHandler() {

            @Override
            public void serviceIO(SelectionKey key) throws IOException {
                SelectorSupportTest.this.log.warn("got notification: {}", key);
                notified.set(true);
            }

            @Override
            public void close(Throwable cause) {
                SelectorSupportTest.this.log.warn("got exception", cause);
            }
        };
        final SelectionKey key = test.createSelectionKey(input, handler, true);
        this.log.info("registered key {}", key);

        // Sleep a bit
        this.log.info("sleeping for 150ms");
        Thread.sleep(50);

        // Close the channel
        this.log.info("closing input channel");
        input.close();

        // Sleep long enough for housekeeping to run at least once
        this.log.info("sleeping for 250ms");
        Thread.sleep(250);

        this.log.info("checking whether notified");
        Assert.assertTrue(notified.get());

        // Done
        test.stop();
    }
}
