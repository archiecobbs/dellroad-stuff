
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Base class for unit tests providing logging and random seed setup.
 */
public abstract class TestSupport {

    private static boolean reportedSeed;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected Random random;

    private Validator validator;

    public Validator getValidator() {
        if (this.validator == null) {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            this.validator = factory.getValidator();
        }
        return this.validator;
    }

    @BeforeClass
    @Parameters({ "randomSeed" })
    public void seedRandom(String randomSeed) {
        this.random = getRandom(randomSeed);
    }

    public static Random getRandom(String randomSeed) {
        long seed;
        try {
            seed = Long.parseLong(randomSeed);
        } catch (NumberFormatException e) {
            seed = System.currentTimeMillis();
        }
        if (!reportedSeed) {
            reportedSeed = true;
            LoggerFactory.getLogger(TestSupport.class).info("test seed = " + seed);
        }
        return new Random(seed);
    }

    protected <T> Set<ConstraintViolation<T>> checkValid(T object, boolean valid) {
        Set<ConstraintViolation<T>> violations = this.getValidator().validate(object);
        if (valid) {
            for (ConstraintViolation<T> violation : violations)
                log.error("unexpected validation error: [" + violation.getPropertyPath() + "]: " + violation.getMessage());
            assertTrue(violations.isEmpty(), "found constraint violations: " + violations);
        } else
            assertFalse(violations.isEmpty(), "expected constraint violations but none were found");
        return violations;
    }

    /**
     * Read some file in as a UTF-8 encoded string.
     *
     * @param file file to read from
     * @return contents of file
     */
    protected String readResource(File file) {
        try {
            return this.readResource(file.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("can't URL'ify file: " + file);
        }
    }

    /**
     * Read some classpath resource in as a UTF-8 encoded string.
     *
     * @param path classpath resource to read from
     * @return contents of resource
     */
    protected String readResource(String path) {
        final URL url = getClass().getResource(path);
        if (url == null)
            throw new RuntimeException("can't find resource `" + path + "'");
        return this.readResource(url);
    }

    /**
     * Read some URL resource in as a UTF-8 encoded string.
     *
     * @param url resource to read from
     * @return contents of resource
     */
    protected String readResource(URL url) {
        try (InputStreamReader reader = new InputStreamReader(url.openStream(), "UTF-8")) {
            final StringWriter writer = new StringWriter();
            char[] buf = new char[1024];
            for (int r; (r = reader.read(buf)) != -1; )
                writer.write(buf, 0, r);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("error reading from " + url, e);
        }
    }

// Diffs

    protected void assertSameOrDiff(String expected, String actual) {
        final String diff = this.diff(expected, actual);
        if (diff != null)
            throw new AssertionError("differences in strings found:\n" + diff);
    }

    /**
     * Run {@code diff(1)} on two strings.
     *
     * @param s1 first string
     * @param s2 second string
     * @return the diff, or null if strings are the same
     */
    protected String diff(String s1, String s2) {

        // Quick check for equality
        if (s1.equals(s2))
            return null;

        // Write strings to temp files
        final File[] files = new File[2];
        try {

            // Write strings to temp files
            for (int i = 0; i < files.length; i++) {
                files[i] = File.createTempFile("diff.", ".txt");
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(files[i]))) {
                    output.write((i == 0 ? s1 : s2).getBytes(StandardCharsets.UTF_8));
                }
            }

            // Run diff(1) command
            final Process process = Runtime.getRuntime().exec(new String[] {
              "diff",
                "-U", "5",
                "--strip-trailing-cr",
                "--text",
                "--expand-tabs",
              files[0].toString(),
              files[1].toString()
              });

            // Close stdin and read back stderr and stdout
            process.getOutputStream().close();
            final ByteArrayOutputStream[] outbufs = new ByteArrayOutputStream[] {
              new ByteArrayOutputStream(),      // stdout
              new ByteArrayOutputStream()       // stderr
            };
            final InputStream[] ins = new InputStream[] {
              process.getInputStream(),
              process.getErrorStream()
            };
            for (int i = 0; i < 2; i++) {
                final byte[] inbuf = new byte[1000];
                for (int r; (r = ins[i].read(inbuf)) != -1; )
                    outbufs[i].write(inbuf, 0, r);
                ins[i].close();
            }
            String stdout = new String(outbufs[0].toByteArray(), StandardCharsets.UTF_8);
            String stderr = new String(outbufs[1].toByteArray(), StandardCharsets.UTF_8);

            // Wait for process to exit
            final int exitValue = process.waitFor();
            if (exitValue != 0 && exitValue != 1)
                throw new RuntimeException("diff(1) error: " + stderr);

            // Skip first two lines of the diff (i.e., filenames)
            for (int i = 0; i < 2; i++)
                stdout = stdout.substring(stdout.indexOf('\n') + 1);

            // Done
            return stdout;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException("diff(1) error", e);
        } finally {
            for (File file : files) {               // delete temp files
                if (file != null)
                    file.delete();
            }
        }
    }
}
