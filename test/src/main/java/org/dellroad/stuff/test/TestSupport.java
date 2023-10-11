
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
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
}

