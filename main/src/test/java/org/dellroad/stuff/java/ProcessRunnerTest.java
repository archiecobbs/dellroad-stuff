
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ProcessRunnerTest extends TestSupport {

    public static final int NUM_LINES = 10000;

    private int numMatch;

    @Test
    public void testProcessRunner() throws Exception {

        // Don't run this on Windows
        if (System.getProperty("os.name").toLowerCase().startsWith("win"))
            return;

        // Start up "grep"
        Process p = Runtime.getRuntime().exec(new String[] { "grep", "a" });
        ProcessRunner runner = new ProcessRunner(p, output -> {
            for (int i = 0; i < NUM_LINES; i++) {
                int letter = 'a' + ProcessRunnerTest.this.random.nextInt(26);
                output.write(letter);
                output.write('\n');
                if (letter == 'a')
                    ProcessRunnerTest.this.numMatch++;
            }
        });

        // Check premature grabs
        try {
            runner.getStandardOutput();
            assert false;
        } catch (IllegalStateException e) {
            // expected
        }
        try {
            runner.getStandardError();
            assert false;
        } catch (IllegalStateException e) {
            // expected
        }

        // Check exit value
        int r = runner.run();
        Assert.assertEquals(r, 0);

        // Check stderr
        byte[] stderr = runner.getStandardError();
        Assert.assertEquals(stderr.length, 0);

        // Check stdout
        byte[] stdout = runner.getStandardOutput();
        Assert.assertEquals(stdout.length, this.numMatch * 2);
        for (int i = 0; i < stdout.length; i += 2) {
            Assert.assertEquals(stdout[i], (byte)'a');
            Assert.assertEquals(stdout[i + 1], (byte)'\n');
        }

        // Check duplicate run
        try {
            runner.run();
            assert false;
        } catch (IllegalStateException e) {
            // expected
        }
    }
}
