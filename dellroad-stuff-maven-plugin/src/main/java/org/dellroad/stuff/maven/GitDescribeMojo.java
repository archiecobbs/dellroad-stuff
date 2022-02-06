
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.maven;

import java.io.File;
import java.util.ArrayList;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Sets a Maven project property based on the output of {@code git describe}.
 */
@Mojo(name = "git-describe", threadSafe = true, defaultPhase = LifecyclePhase.INITIALIZE)
public class GitDescribeMojo extends AbstractExecSetPropertyMojo {

    /**
     * The name of the Maven property to set.
     */
    @Parameter(defaultValue = "git.describe", property = "propertyName")
    private String propertyName;

    /**
     * The {@code git(1)} working directory where the code is checked out.
     */
    @Parameter(defaultValue = "${project.basedir}", property = "workingDirectory")
    private File workingDirectory;

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
    protected String getPropertyName() {
        return this.propertyName;
    }

    @Override
    public void execute() throws MojoExecutionException {

        // Validate
        if (this.isNonEmpty(this.committish) && this.isNonEmpty(this.dirty))
            throw new MojoExecutionException("only one of <committish> or <dirty> can be set");

        // Setup command
        final ArrayList<String> command = new ArrayList<>(20);
        command.add("git");
        command.add("-C");
        command.add(this.workingDirectory.toString());
        command.add("describe");
        if (this.isNonEmpty(this.dirty))
            command.add("--dirty=" + this.dirty);
        if (this.all)
            command.add("--all");
        if (this.tags)
            command.add("--tags");
        if (this.abbrev >= 0)
            command.add("--abbrev=" + this.abbrev);
        if (this.candidates >= 0)
            command.add("--candidates=" + this.candidates);
        if (this.exactMatch)
            command.add("--exact-match");
        if (this.longFormat)
            command.add("--long");
        if (this.isNonEmpty(this.match)) {
            command.add("--match");
            command.add(this.match);
        }
        if (this.always)
            command.add("--always");
        if (this.firstParent)
            command.add("--first-parent");
        if (this.isNonEmpty(this.committish)) {
            if (this.committish.charAt(0) == '-')
                command.add("--");
            command.add(this.committish);
        }

        // Exec process and set property
        this.executeAndSetProperty(this.workingDirectory, command);
    }
}
