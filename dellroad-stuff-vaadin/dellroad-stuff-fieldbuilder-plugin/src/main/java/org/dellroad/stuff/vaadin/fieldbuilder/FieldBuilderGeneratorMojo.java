
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin.fieldbuilder;

import java.beans.Introspector;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generates {@code FieldBuilder.java}.
 */
@Mojo(name = "generate-field-builder", threadSafe = true, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class FieldBuilderGeneratorMojo extends AbstractMojo {

    /**
     * Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * The current working directory for the command.
     */
    @Parameter(required = true, property = "sourceFile")
    private File sourceFile;

    /**
     * Top-level Java packages to search under (e.g., {@code "com.vaadin.flow.component"}).
     */
    @Parameter(required = true, property = "packageRoots")
    private List<String> packageRoots;

    /**
     * The required Java type for matched widget classes (e.g., {@code "com.vaadin.flow.component.AbstractField"}).
     */
    @Parameter(required = true, property = "requiredType")
    private String requiredType;

    /**
     * The separator line in the source file after which all source is generated.
     */
    @Parameter(required = true, property = "separatorLine")
    private String separatorLine;

    /**
     * The name of the implementing class property, if any (e.g., {@code "implementation"}).
     */
    @Parameter(property = "implementationPropertyName")
    private String implementationPropertyName;

    /**
     * Methods to customize.
     */
    @Parameter
    private List<Customization> customizations;

    /**
     * Methods to exclude.
     */
    @Parameter
    private List<Customization> excludeMethods;

    @Override
    public void execute() throws MojoExecutionException {

        // Get required type
        this.getLog().info("Regenerating " + this.sourceFile);
        final Class<?> requiredTypeClass;
        try {
            requiredTypeClass = Thread.currentThread().getContextClassLoader().loadClass(this.requiredType);
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException("class " + this.requiredType + " not found", e);
        }
        this.getLog().info("Searching for classes that are sub-types of " + requiredTypeClass);

        // Create temporary file
        final File tempFile;
        try {
            tempFile = File.createTempFile(this.sourceFile.getName() + ".", ".tmp", this.sourceFile.getParentFile());
        } catch (IOException e) {
            throw new MojoExecutionException("failed to create temporary file in directory " + this.sourceFile.getParentFile(), e);
        }

        // Build method -> property name function
        final Function<? super Method, String> methodPropertyNameFunction = method -> {

            // Is method excluded?
            if (this.isExcluded(method))
                return null;

            // Is method property name customized?
            return this.findCustomization(method)
              .map(Customization::getPropertyName)
              .orElseGet(() -> this.defaultPropertyNameFor(method));
        };

        // Build default value override function
        final Function<? super Method, String> defaultOverrideFunction = method -> {

            // Method should not be excluded at this point
            assert !this.isExcluded(method);

            // Is method default value customized?
            return this.findCustomization(method)
              .map(Customization::getDefaultValue)
              .orElse(null);
        };

        // Regenerate file
        try {

            // Generate new file into temporary file using original as a template
            try (
              InputStreamReader input = new InputStreamReader(new FileInputStream(this.sourceFile), StandardCharsets.UTF_8);
              OutputStreamWriter output = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {

                // Configure generator
                final FieldBuilderGenerator generator = new FieldBuilderGenerator(requiredTypeClass);
                generator.getPackageRoots().addAll(this.packageRoots);
                generator.setSeparatorLine(this.separatorLine);
                generator.setLogger(this.getLogger());
                generator.setImplementationPropertyName(this.implementationPropertyName);
                generator.setMethodPropertyNameFunction(methodPropertyNameFunction);
                generator.setDefaultOverrideFunction(defaultOverrideFunction);

                // Scan classpath to find the types to be generated
                generator.findFieldClasses();
                if (generator.getFieldTypes().isEmpty())
                    throw new MojoExecutionException("No field classes found assignable to " + this.requiredType + "");
                this.getLog().info("Found these sub-types of " + requiredTypeClass + ":");
                generator.getFieldTypes().forEach(type -> this.getLog().info("  " + type.getName()));

                // Execute generator
                generator.generate(input, output);
            } catch (IOException | RuntimeException e) {
                throw new MojoExecutionException("FieldBuilder generation failed", e);
            }

            // Move temporary onto original
            if (!tempFile.renameTo(this.sourceFile))
                throw new MojoExecutionException("failed to replace " + this.sourceFile);
        } finally {
            tempFile.delete();              // OK if this fails
        }
    }

    protected BiConsumer<? super Integer, ? super String> getLogger() {
        return (level, line) -> {
            switch (level) {
            case FieldBuilderGenerator.LOG_WARN:
                this.getLog().warn(line);
                break;
            case FieldBuilderGenerator.LOG_ERROR:
                this.getLog().error(line);
                break;
            default:
                this.getLog().info(line);
                break;
            }
        };
    }

    protected boolean isExcluded(Method method) {
        return this.findCustomization(this.excludeMethods, method) != null;
    }

    protected Optional<Customization> findCustomization(Method method) {
        return Optional.ofNullable(this.findCustomization(this.customizations, method));
    }

    protected String defaultPropertyNameFor(Method method) {
        return Introspector.decapitalize(method.getName().substring(3));          // skip the "set" or "add"
    }

    private Customization findCustomization(List<Customization> list, Method method) {
        if (list == null)
            return null;
        final String description = method.toString();
        for (Customization customization : list) {
            if (description.equals(customization.getMethodName()))
                return customization;
            final Pattern pattern = customization.getCompiledMethodNamePattern();
            if (pattern != null && pattern.matcher(description).matches())
                return customization;
        }
        return null;
    }

// Customization

    public static class Customization {

        private String methodName;
        private String methodNamePattern;
        private String propertyName;
        private String defaultValue;
        private Pattern compiledMethodNamePattern;

        // Method toString() to match exactly
        public String getMethodName() {
            return this.methodName;
        }
        public void setMethodName(final String methodName) {
            this.methodName = methodName;
        }

        // Method toString() to match by regex (complete match)
        public String getMethodNamePattern() {
            return this.methodNamePattern;
        }
        public void setMethodNamePattern(final String methodNamePattern) {
            this.methodNamePattern = methodNamePattern;
        }

        public Pattern getCompiledMethodNamePattern() {
            if (this.methodNamePattern != null && this.compiledMethodNamePattern == null)
                this.compiledMethodNamePattern = Pattern.compile(this.methodNamePattern);
            return this.compiledMethodNamePattern;
        }

        // Annotation property name
        public String getPropertyName() {
            return this.propertyName;
        }
        public void setPropertyName(final String propertyName) {
            this.propertyName = propertyName;
        }

        // Annotation default value
        public String getDefaultValue() {
            return this.defaultValue;
        }
        public void setDefaultValue(final String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}
