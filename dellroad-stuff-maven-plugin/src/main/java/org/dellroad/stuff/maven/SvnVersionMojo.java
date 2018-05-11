
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.maven;

import java.io.File;
import java.util.ArrayList;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Sets a Maven project property based on the output of {@code svnversion}.
 */
@Mojo(name = "svn-version", threadSafe = true, defaultPhase = LifecyclePhase.INITIALIZE)
public class SvnVersionMojo extends AbstractExecSetPropertyMojo {

    /**
     * The name of the Maven property to set.
     */
    @Parameter(defaultValue = "svn.version", property = "propertyName")
    private String propertyName;

    /**
     * The {@code svn(1)} working directory where the code is checked out.
     */
    @Parameter(defaultValue = "${project.basedir}", property = "workingDirectory")
    private File workingDirectory;

    /**
     * Report last changed rather than current revisions.
     */
    @Parameter(defaultValue = "false", property = "committed")
    private boolean committed;

    /**
     * Trailing portion of the URL used to determine if the working directory itself is switched.
     */
    @Parameter(defaultValue = "", property = "trailingUrl")
    private String trailingUrl;

    @Override
    protected String getPropertyName() {
        return this.propertyName;
    }

    @Override
    public void execute() throws MojoExecutionException {

        // Setup command
        final ArrayList<String> command = new ArrayList<>(4);
        command.add("svnversion");
        if (this.committed)
            command.add("--committed");
        command.add(this.workingDirectory.toString());
        if (this.isNonEmpty(this.trailingUrl))
            command.add(this.trailingUrl);

        // Exec process and set property
        this.executeAndSetProperty(this.workingDirectory, command);
    }
}
