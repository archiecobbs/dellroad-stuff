
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
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
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
     * The name of the generated "defaults" method.
     */
    @Parameter(defaultValue = "annotationDefaultsMethod", property = "annotationDefaultsMethodName")
    private String annotationDefaultsMethodName;

    /**
     * The name of the implementing class property, if any (e.g., {@code "implementation"}).
     */
    @Parameter(property = "implementationPropertyName")
    private String implementationPropertyName;

    /**
     * Specify whether to include any abstract classes.
     *
     * <p>
     * For abstract classes, the implementing class property (see {@link implementationPropertyName})
     * is generated with no default value.
     */
    @Parameter(defaultValue = "true", property = "includeAbstractClasses")
    private boolean includeAbstractClasses;

    /**
     * Specify customizations to the generated annotation names.
     *
     * <p>
     * Match classes using {@code &lt;className&gt;} or {@code &lt;classNamePattern&gt;} and specify
     * the corresponding annotation names using {@code &lt;annotationName&gt;}
     */
    @Parameter
    private List<Customization> annotationNameCustomizations;

    /**
     * Specify customizations to specific annotation properties.
     *
     * <p>
     * Match {@link Method#toString} descriptions using {@code &lt;method&gt;} or {@code &lt;methodPattern&gt;}.
     * Optionally, also limit the affected widget classes using {@code &lt;className&gt;} or {@code &lt;classNamePattern&gt;}.
     * Then specify an alternate property name via {@code &lt;propertyName&gt;} and/or default value via
     * {@code &lt;defaultValue&gt;}.
     */
    @Parameter
    private List<Customization> propertyCustomizations;

    /**
     * Specify widget properties to exclude.
     *
     * <p>
     * Match {@link Method#toString} descriptions using {@code &lt;method&gt;} or {@code &lt;methodPattern&gt;}.
     * Optionally, also limit the affected widget classes using {@code &lt;className&gt;} or {@code &lt;classNamePattern&gt;}.
     */
    @Parameter
    private List<Customization> excludeMethods;

    /**
     * Specify widget classes to exclude.
     *
     * <p>
     * Match widget classes using {@code &lt;className&gt;} or {@code &lt;classNamePattern&gt;}.
     */
    @Parameter
    private List<Customization> excludeClasses;

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

        // Build class -> whether included predicate
        final Predicate<? super Class<?>> classInclusionPredicate = cl -> {

            // Are we including abstract classes?
            if (!this.includeAbstractClasses && (cl.getModifiers() & Modifier.ABSTRACT) != 0)
                return false;

            // Does configuration allow it?
            if (this.isExcludedClass(cl))
                return false;

            // OK
            return true;
        };

        // Build class -> annotation name function
        final Function<? super Class<?>, String> annotationNameFunction = cl -> {
            return this.findCustomization(this.annotationNameCustomizations, cl, null)
              .map(Customization::getAnnotationName)
              .orElseGet(cl::getSimpleName);
        };

        // Build method -> property name function
        final BiFunction<? super Class<?>, ? super Method, String> methodPropertyNameFunction = (cl, method) -> {

            // Is method excluded?
            if (this.isExcludedMethod(cl, method))
                return null;

            // Is method property name customized?
            return this.findCustomization(this.propertyCustomizations, cl, method)
              .map(Customization::getPropertyName)
              .orElseGet(() -> this.defaultPropertyNameFor(method));
        };

        // Build default value override function
        final BiFunction<? super Class<?>, ? super Method, String> defaultOverrideFunction = (cl, method) -> {

            // Method should not be excluded at this point
            assert !this.isExcludedMethod(cl, method);

            // Is method default value customized?
            return this.findCustomization(this.propertyCustomizations, cl, method)
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
                generator.setLogger(this.buildLogger());
                generator.setAnnotationDefaultsMethodName(this.annotationDefaultsMethodName);
                generator.setImplementationPropertyName(this.implementationPropertyName);
                generator.setClassInclusionPredicate(classInclusionPredicate);
                generator.setAnnotationNameFunction(annotationNameFunction);
                generator.setMethodPropertyNameFunction(methodPropertyNameFunction);
                generator.setDefaultOverrideFunction(defaultOverrideFunction);

                // Scan classpath to find the types to be generated
                generator.findFieldClasses();
                if (generator.getFieldTypes().isEmpty())
                    throw new MojoExecutionException("No field classes found assignable to " + this.requiredType + "");
                this.getLog().info("Found these sub-types of " + requiredTypeClass.getName() + ":");
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

    protected FieldBuilderGenerator.Logger buildLogger() {
        return (level, format, params) -> {
            final Log log = this.getLog();
            final BiConsumer<Log, String> dest;
            switch (level) {
            case FieldBuilderGenerator.Logger.INFO:
                if (!log.isInfoEnabled())
                    return;
                dest = Log::info;
                break;
            case FieldBuilderGenerator.Logger.WARN:
                if (!log.isWarnEnabled())
                    return;
                dest = Log::warn;
                break;
            case FieldBuilderGenerator.Logger.ERROR:
                if (!log.isErrorEnabled())
                    return;
                dest = Log::error;
                break;
            case FieldBuilderGenerator.Logger.DEBUG:
            default:
                if (!log.isDebugEnabled())
                    return;
                dest = Log::debug;
                break;
            }
            dest.accept(log, String.format(format, params));
        };
    }

    protected boolean isExcludedClass(Class<?> cl) {
        return this.findCustomization(this.excludeClasses, cl, null).isPresent();
    }

    protected boolean isExcludedMethod(Class<?> cl, Method method) {
        return this.findCustomization(this.excludeMethods, cl, method).isPresent();
    }

    protected String defaultPropertyNameFor(Method method) {
        return Introspector.decapitalize(method.getName().substring(3));          // skip the "set" or "add"
    }

    private Optional<Customization> findCustomization(List<Customization> list, Class<?> cl, Method method) {
        if (list == null)
            return Optional.empty();
        return list.stream()
          .filter(customization -> customization.matches(cl, method))
          .findFirst();
    }

// Customization

    public static class Customization {

        private String className;
        private String classNamePattern;
        private String method;
        private String methodPattern;
        private String annotationName;
        private String propertyName;
        private String defaultValue;

        private Pattern compiledClassNamePattern;
        private Pattern compiledMethodPattern;

        // Class name to match exactly (if configured)
        public String getClassName() {
            return this.className;
        }
        public void setClassName(final String className) {
            this.className = className;
        }

        // Class name to match by regex (if configured)
        public String getClassNamePattern() {
            return this.classNamePattern;
        }
        public void setClassNamePattern(final String classNamePattern) {
            this.classNamePattern = classNamePattern;
        }

        private void compileClassNamePattern() {
            if (this.classNamePattern != null && this.compiledClassNamePattern == null)
                this.compiledClassNamePattern = Pattern.compile(this.classNamePattern);
        }

        // Method toString() to match exactly - option #1 for matching method
        public String getMethod() {
            return this.method;
        }
        public void setMethod(final String method) {
            this.method = method;
        }

        // Method toString() to match by regex (if configured) - option #2 for matching method
        public String getMethodPattern() {
            return this.methodPattern;
        }
        public void setMethodPattern(final String methodPattern) {
            this.methodPattern = methodPattern;
        }

        private void compileMethodPattern() {
            if (this.methodPattern != null && this.compiledMethodPattern == null)
                this.compiledMethodPattern = Pattern.compile(this.methodPattern);
        }

        // Annotation name
        public String getAnnotationName() {
            return this.annotationName;
        }
        public void setAnnotationName(final String annotationName) {
            this.annotationName = annotationName;
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

        public boolean matches(Class<?> cl, Method method) {

            // Target class specification, if any, must match
            if (this.className != null && !this.className.equals(cl.getName()))
                return false;
            this.compileClassNamePattern();
            if (this.compiledClassNamePattern != null && !this.compiledClassNamePattern.matcher(cl.getName()).matches())
                return false;

            // If we're just checking class name, we're done
            if (method == null)
                return true;

            // Either method name or method description must match
            final String description = method.toString();
            if (description.equals(this.method))
                return true;
            this.compileMethodPattern();
            if (this.compiledMethodPattern != null && this.compiledMethodPattern.matcher(description).matches())
                return true;

            // No match
            return false;
        }

        @Override
        public String toString() {
            final String properties = Stream.of(new String[][] {
              { "className",          this.className },
              { "classNamePattern",   this.classNamePattern },
              { "method",             this.method },
              { "methodPattern",      this.methodPattern },
              { "annotationName",     this.annotationName },
              { "propertyName",       this.propertyName },
              { "defaultValue",       this.defaultValue }
            })
              .filter(pair -> pair[1] != null)
              .map(pair -> String.format("%s=\"%s\"", pair[0], pair[1]))
              .collect(Collectors.joining(","));
            return "Customization[" + properties + "]";
        }
    }
}
