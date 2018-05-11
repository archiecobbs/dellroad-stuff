
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Sets a Maven project property based on the output of an arbitrary command.
 */
@Mojo(name = "exec-set-property", threadSafe = true, defaultPhase = LifecyclePhase.INITIALIZE)
public class ExecSetPropertyMojo extends AbstractExecSetPropertyMojo {

    /**
     * The name of the Maven property to set.
     */
    @Parameter(property = "propertyName")
    private String propertyName;

    /**
     * The command to execute.
     */
    @Parameter(required = true)
    private String command;

    /**
     * The command's parameters, if any.
     */
    @Parameter
    private List<String> parameters;

    /**
     * The current working directory for the command.
     */
    @Parameter(defaultValue = "${project.basedir}", property = "directory")
    private File directory;

    @Override
    protected String getPropertyName() {
        return this.propertyName;
    }

    @Override
    public void execute() throws MojoExecutionException {

        // Setup command
        final ArrayList<String> commandLine = new ArrayList<>();
        commandLine.add(this.command);
        if (this.parameters != null)
            commandLine.addAll(this.parameters);

        // Exec process and set property
        this.executeAndSetProperty(this.directory, commandLine);
    }
}
