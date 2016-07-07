
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.dellroad.stuff.java.ProcessRunner;

/**
 * Sets a Maven project property based on the output of {@code git describe}.
 */
@Mojo(name = "git-describe", threadSafe = true)
public class GitDescribeMojo extends AbstractMojo {

    /**
     * Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * The {@code git(1)} working directory where the code is checked out.
     */
    @Parameter(defaultValue = "${project.directory}", property = "workingDirectory")
    private File workingDirectory;

    /**
     * The name of the Maven property to set.
     */
    @Parameter(defaultValue = "git.describe", property = "propertyName")
    private String propertyName;

    /**
     * Commit-ish object name to describe. Defaults to {@code HEAD} if not set.
     * Note: this property is incompatible with {@code dirty}.
     */
    @Parameter(defaultValue = "", property = "committish")
    private String committish;

    /**
     * Describe the working tree. It means describe {@code HEAD} and appends the configured value
     * if the working tree is dirty.
     * Note: this property is incompatible with {@code committish}.
     */
    @Parameter(defaultValue = "", property = "dirty")
    private String dirty;

    /**
     * Instead of using only the annotated tags, use any ref found in {@code refs/} namespace.
     * This option enables matching any known branch, remote-tracking branch, or lightweight tag.
     */
    @Parameter(defaultValue = "false", property = "all")
    private boolean all;

    /**
     * Instead of using only the annotated tags, use any tag found in {@code refs/tags} namespace.
     * This option enables matching a lightweight (non-annotated) tag.
     */
    @Parameter(defaultValue = "false", property = "tags")
    private boolean tags;

    /**
     * Instead of finding the tag that predates the commit, find the tag that comes after the commit, and thus contains it.
     * Automatically implies {@code tags}.
     */
    @Parameter(defaultValue = "false", property = "contains")
    private boolean contains;

    /**
     * Instead of using the default 7 hexadecimal digits as the abbreviated object name, use this many digits,
     * or as many digits as needed to form a unique object name. A value of 0 will suppress long format,
     * only showing the closest tag.
     */
    @Parameter(defaultValue = "-1", property = "abbrev")
    private int abbrev;

    /**
     * Instead of considering only the 10 most recent tags as candidates to describe the input consider up to this many candidates.
     * Increasing this value above 10 will take slightly longer but may produce a more accurate result.
     * A value of 0 will cause only exact matches to be output.
     */
    @Parameter(defaultValue = "-1", property = "candidates")
    private int candidates;

    /**
     * Only output exact matches (a tag directly references the supplied commit).
     * This is the same as setting {@code candidates} to zero.
     */
    @Parameter(defaultValue = "false", property = "exactMatch")
    private boolean exactMatch;

    /**
     * Always output the long format (the tag, the number of commits and the abbreviated commit name) even when it matches a tag.
     * This is useful when you want to see parts of the commit object name in "describe" output, even when the commit in question
     * happens to be a tagged version. Instead of just emitting the tag name, it will describe such a commit as
     * {@code v1.2-0-gdeadbee} (0th commit since tag {@code v1.2} that points at object {@code deadbee....}).
     */
    @Parameter(defaultValue = "false", property = "long")
    private boolean longFormat;

    /**
     * Only consider tags matching the given {@code glob(7)} pattern, excluding the {@code refs/tags/} prefix.
     * This can be used to avoid leaking private tags from the repository.
     */
    @Parameter(defaultValue = "", property = "match")
    private String match;

    /**
     * Show uniquely abbreviated commit object as fallback.
     */
    @Parameter(defaultValue = "false", property = "always")
    private boolean always;

    /**
     * Follow only the first parent commit upon seeing a merge commit.
     * This is useful when you wish to not match tags on branches merged in the history of the target commit.
     */
    @Parameter(defaultValue = "false", property = "firstParent")
    private boolean firstParent;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // Validate
        if (this.isSet(this.committish) && this.isSet(this.dirty))
            throw new MojoExecutionException("only one of <committish> or <dirty> can be set");

        // Setup command
        final ArrayList<String> params = new ArrayList<>(20);
        params.add("git");
        params.add("-C");
        params.add(this.workingDirectory.toString());
        params.add("describe");
        if (this.isSet(this.dirty))
            params.add("--dirty=" + this.dirty);
        if (this.all)
            params.add("--all");
        if (this.tags)
            params.add("--tags");
        if (this.abbrev >= 0)
            params.add("--abbrev=" + this.abbrev);
        if (this.candidates >= 0)
            params.add("--candidates=" + this.candidates);
        if (this.exactMatch)
            params.add("--exact-match");
        if (this.longFormat)
            params.add("--long");
        if (this.isSet(this.match)) {
            params.add("--match");
            params.add(this.match);
        }
        if (this.always)
            params.add("--always");
        if (this.firstParent)
            params.add("--first-parent");
        if (this.isSet(this.committish)) {
            if (this.committish.charAt(0) == '-')
                params.add("--");
            params.add(this.committish);
        }

        // Start process
        this.getLog().debug("invoking `git describe' command using these parameters: " + params);
        final ProcessRunner runner;
        try {
            runner = new ProcessRunner(Runtime.getRuntime().exec(params.toArray(new String[params.size()])));
        } catch (IOException e) {
            throw new MojoExecutionException("execution of `git describe' failed", e);
        }

        // Wait for process to finish
        final int result;
        try {
            result = runner.run();
        } catch (InterruptedException e) {
            throw new MojoExecutionException("invocation of `git describe' interrupted", e);
        }
        this.getLog().debug("`git describe' returned " + result);
        if (result != 0)
            throw new MojoExecutionException("`git describe' failed: " + new String(runner.getStandardError()));

        // Get output text
        final String text = new String(runner.getStandardOutput()).trim();

        // Set maven property
        this.project.getProperties().setProperty(this.propertyName, text);
    }

    private boolean isSet(String value) {
        return value != null && value.length() > 0;
    }
}
