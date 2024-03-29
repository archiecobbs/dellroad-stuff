
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.dellroad.stuff.java.ProcessRunner;

/**
 * Support superclass for mojo's that execute some command and set a property based on the resulting output.
 */
public abstract class AbstractExecSetPropertyMojo extends AbstractMojo {

    /**
     * Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Whether to trim whitespace before setting the property value.
     */
    @Parameter(defaultValue = "true", property = "trim")
    private boolean trim;

    /**
     * The name of the character encoding to use when converting the command's standard output bytes into a {@link String}.
     */
    @Parameter(defaultValue = "UTF-8", property = "encoding")
    private String encoding;

    /**
     * Get the name of the property to set.
     *
     * @return the name of the property to set
     */
    protected abstract String getPropertyName();

    /**
     * Execute command and set the property from standard output.
     *
     * @param directory new process current working directory, or null to inherit
     * @param command command and parameters
     * @throws MojoExecutionException if execution fails
     */
    protected void executeAndSetProperty(File directory, List<String> command) throws MojoExecutionException {

        // Get encoding
        final Charset charset;
        try {
            charset = Charset.forName(this.encoding);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("invalid character encoding \"" + this.encoding + "\": " + e, e);
        }

        // Start process
        this.getLog().debug("Invoking command: " + command);
        final ProcessRunner runner;
        try {
            runner = new ProcessRunner(Runtime.getRuntime().exec(command.toArray(new String[command.size()]), null, directory));
        } catch (IOException e) {
            throw new MojoExecutionException("execution of command"
              + (directory != null ? " in directory " + directory : "") + " failed: " + command, e);
        }

        // Wait for process to finish
        final int result;
        try {
            result = runner.run();
        } catch (InterruptedException e) {
            throw new MojoExecutionException("invocation of command interrupted", e);
        }
        this.getLog().debug("Command returned " + result);
        final String stderr = new String(runner.getStandardError(), charset);
        this.getLog().debug("Command standard error: \"" + stderr + "\"");
        final String stdout = new String(runner.getStandardOutput(), charset);
        this.getLog().debug("Command standard output: \"" + stdout + "\"");
        if (result != 0)
            throw new MojoExecutionException("command failed: " + new String(runner.getStandardError(), charset));

        // Get output text and optionally trim whitespace
        final String text = this.trim ? stdout.trim() : stdout;

        // Set maven property
        final String propertyName = this.getPropertyName();
        if (propertyName == null)
            throw new MojoExecutionException("internal error: no property name set");
        this.getLog().debug("Seting property \"" + propertyName + "\" to \"" + text + "\"");
        this.project.getProperties().setProperty(propertyName, text);
        this.getLog().info("Set property \"" + propertyName + "\"");
    }

    protected boolean isNonEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}
