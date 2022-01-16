
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin.fieldbuilder;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.dellroad.stuff.java.Primitive;
import org.dellroad.stuff.string.StringEncoder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/**
 * Generates {@code FieldBuilder} source files.
 */
public class FieldBuilderGenerator {

    public static final int LOG_ERROR = 0;
    public static final int LOG_WARN = 1;
    public static final int LOG_INFO = 2;

    private static final String TAB = "    ";

    private final Class<?> requiredType;
    private final List<String> packageRoots = new ArrayList<>();
    private final HashSet<Method> warnedAboutDefault = new HashSet<>();
    private final HashSet<Method> warnedAboutWrongType = new HashSet<>();

    private BiConsumer<? super Integer, ? super String> logger = this.getDefaultLogger();
    private Function<? super Method, String> methodPropertyNameFunction;
    private Function<? super Method, String> defaultOverrideFunction;
    private final List<Class<?>> fieldTypes = new ArrayList<>();
    private String implementationPropertyName;
    private String separatorLine;

    // Runtime info
    private Reader input;
    private PrintWriter output;

// Constructor

    public FieldBuilderGenerator(Class<?> requiredType) {
        if (requiredType == null)
            throw new IllegalArgumentException("null requiredType");
        this.requiredType = requiredType;
    }

// Config

    public void setSeparatorLine(String separatorLine) {
        this.separatorLine = separatorLine;
    }

    public List<Class<?>> getFieldTypes() {
        return this.fieldTypes;
    }

    public List<String> getPackageRoots() {
        return this.packageRoots;
    }

    public void setLogger(BiConsumer<? super Integer, ? super String> logger) {
        this.logger = Optional.ofNullable(logger).orElse((level, line) -> { });
    }

    public void setMethodPropertyNameFunction(Function<? super Method, String> methodPropertyNameFunction) {
        this.methodPropertyNameFunction = methodPropertyNameFunction;
    }

    public void setDefaultOverrideFunction(Function<? super Method, String> defaultOverrideFunction) {
        this.defaultOverrideFunction = defaultOverrideFunction;
    }

    public void setImplementationPropertyName(String implementationPropertyName) {
        this.implementationPropertyName = implementationPropertyName;
    }

    protected BiConsumer<? super Integer, ? super String> getDefaultLogger() {
        return (level, line) -> (level <= LOG_WARN ? System.err : System.out).println(line);
    }

    /**
     * Scan classpath for qualifing field classes.
     *
     * <p>
     * This must be invoked prior to {@link #generate}.
     */
    public void findFieldClasses() {

        // Setup class loader, etc.
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final PathMatchingResourcePatternResolver resourceLoader = new PathMatchingResourcePatternResolver(loader);
        final SimpleMetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory(resourceLoader);

        // Scan for classes under each package root
        this.packageRoots.forEach(packageRoot -> {

            // Build resource path
            this.logger.accept(LOG_INFO, "Searching under " + packageRoot);
            final String resourcePath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
              + packageRoot.replace('.', '/') + "/" + "**/*.class";
            //this.logger.accept(LOG_INFO, "Searching for " + resourcePath);

            // Find classes under this package root that subtype our required type
            try {
                for (Resource resource : resourceLoader.getResources(resourcePath)) {

                    // Determine class name
                    final String uriString = resource.getURI().toString();
                    if (!resource.getURI().toString().endsWith(".class"))
                        continue;
                    final String className = metadataReaderFactory.getMetadataReader(resource).getClassMetadata().getClassName();

                    // Load class
                    final Class<?> cl;
                    try {
                        cl = Thread.currentThread().getContextClassLoader().loadClass(className);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("failed to load class \"" + className
                          + "\" from resource \"" + uriString + "\"", e);
                    }

                    // Check it has the rigth type and is instantiable with zero-arg constructor
                    if (!this.requiredType.isAssignableFrom(cl))
                        continue;
                    if ((cl.getModifiers() & Modifier.ABSTRACT) != 0)
                        continue;
                    try {
                        cl.getConstructor();
                    } catch (NoSuchMethodException e) {
                        continue;
                    }

                    // Add class to our list
                    this.fieldTypes.add(cl);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Sort by name
        this.fieldTypes.sort(Comparator.comparing(Class::getName));
    }

    private void copyInputUpThroughSeparator() throws IOException {
        final LineNumberReader reader = new LineNumberReader(this.input);
        for (String line; (line = reader.readLine()) != null; ) {
            this.output.println(line);
            if (line.equals(this.separatorLine))
                return;
        }
        throw new RuntimeException("separator line not found in input");
    }

    public void generate(Reader input, Writer output) throws IOException {

        // Sanity check
        if (input == null)
            throw new IllegalArgumentException("null input");
        if (output == null)
            throw new IllegalArgumentException("null output");
        if (separatorLine == null)
            throw new IllegalStateException("null separatorLine");
        if (this.methodPropertyNameFunction == null)
            throw new IllegalStateException("null methodPropertyNameFunction");

        // Reset runtime state
        this.input = input;
        this.output = new PrintWriter(output);

        // Regenerate file
        this.copyInputUpThroughSeparator();
        this.generateSourceAfterSeparator();
    }

    private void generateSourceAfterSeparator() {

        // Generate method having the default annotation for all generated annotations
        this.output.println();
        this.fieldTypes.stream().forEach(cl -> this.lines(1, "@" + cl.getSimpleName()));
        this.lines(1,
            "private static void defaultsMethod() {",
            "}");

        // Generate annotations for all widget classes
        this.fieldTypes.stream().forEach(this::generateAnnotationClass);

        // Output final stuff
        this.lines(0, "}");
    }

    private void generateAnnotationClass(Class<?> cl) {

        // Get info
        final String name = cl.getSimpleName();
        final String longName = cl.getName();
        final String a = cl.getSimpleName().charAt(0) == 'A' ? "an" : "a";

        // Output initial stuff
        this.lines(1,
          "",
          "/**",
          " * Specifies how a Java bean property should be edited using " + a + " {@link " + longName + "}.",
          " *",
          " * @see FieldBuilder",
          " * @see " + longName,
          " */",
          "@Retention(RetentionPolicy.RUNTIME)",
          "@Target(ElementType.METHOD)",
          "@Documented",
          "public @interface " + name + " {");

        // Output implementation() property
        if (this.implementationPropertyName != null) {
            this.lines(2,
              "",
              "/**",
              " * Get the sub-type of {@link " + longName + "} that will edit the property.",
              " *",
              " * <p>",
              " * This property allows custom widget subclasses to be used.",
              " *",
              " * <p>",
              " * The specified type must have a public no-arg constructor.",
              " *",
              " * @return field type",
              " */",
              "@SuppressWarnings(\"rawtypes\")",
              "Class<? extends " + longName + "> " + this.implementationPropertyName + "() default " + longName + ".class;");
        }

        // Scan for qualifying setFoo() and addFoo() methods and key them by the desired annotation property name
        final TreeMap<String, Method> setterMap = new TreeMap<>();
        this.findPropertySetters(cl, "set", setterMap);
        this.findPropertySetters(cl, "add", setterMap);

        // Generate corresponding annotation properties
        setterMap.forEach((propertyName, method) -> {
            if (method.getName().startsWith("set"))
                this.addAnnotationSetMethod(cl, propertyName, method);
             else
                this.addAnnotationAddMethod(cl, propertyName, method);
        });

        // Output final stuff
        this.lines(1, "}");
    }

    // Add annotation property, either using method type directly, or by specifying a class to instantiate
    private void addAnnotationSetMethod(Class<?> cl, String propertyName, Method method) {

        // Handle annotation-supported property type vs. class to instantiate
        final Class<?> ptype = method.getParameterTypes()[0];
        if (this.isSupportedAnnotationPropertyType(ptype)) {

            // We must be able to determine a default value
            String defaultValue;
            try {
                defaultValue = this.getDefaultValue(cl, method);
            } catch (IllegalArgumentException e) {
                if (this.warnedAboutDefault.add(method))
                    this.logger.accept(LOG_ERROR, "No default value possible for " + method + ": " + e.getMessage());
                return;
            }

            // Output annotation property
            this.lines(2,
              "",
              "/**",
              " * Get the value desired for the {@code " + propertyName + "} property.",
              " *",
              " * @return desired {@code " + propertyName + "} property value",
              " * @see " + this.linkFor(method),
              " */",
              String.format("%s %s() default %s;", this.srcName(ptype), propertyName, defaultValue));
        } else {

            // Output annotation property with instantiation class
            this.lines(2,
              "",
              "/**",
              " * Get the class to instantiate for the {@code " + propertyName + "} property.",
              " *",
              " * @return desired {@code " + propertyName + "} property value type",
              " * @see " + this.linkFor(method),
              " */",
              "@SuppressWarnings(\"rawtypes\")",
              String.format("Class<? extends %s> %s() default %s.class;", this.srcName(ptype), propertyName, this.srcName(ptype)));
        }
    }

    // Add annotation property, either using method type directly, or by specifying a class to instantiate
    private void addAnnotationAddMethod(Class<?> cl, String propertyName, Method method) {

        // We only support methods taking arrays of annotation-supported property types (e.g., addStyleNames(), not addStyleName())
        Class<?> ptype = method.getParameterTypes()[0];
        if (!this.isSupportedAnnotationPropertyType(ptype) || !ptype.isArray())
            return;
        ptype = ptype.getComponentType();
        if (ptype.isArray())
            return;

        // Output "add" annotation property
        final StringBuilder plurals = new StringBuilder();
        for (int i = 3; i < method.getName().length(); i++) {
            char ch = method.getName().charAt(i);
            if (Character.isUpperCase(ch)) {
                if (plurals.length() > 0)
                    plurals.append(' ');
                ch = Character.toLowerCase(ch);
            }
            plurals.append(ch);
        }
        this.lines(2,
          "",
          "/**",
          " * Add the specified " + plurals + ".",
          " *",
          " * @return zero or more " + plurals + " to add",
          " * @see " + this.linkFor(method),
          " */",
          String.format("%s[] %s() default {};", this.srcName(ptype), method.getName()));
    };

    private void findPropertySetters(Class<?> type, String methodPrefix, Map<String, Method> setterMap) {

        // Get all setter methods
        final ArrayList<Method> methodList = new ArrayList<>();
        for (Method method : type.getMethods()) {

            // Set if method is a setter method
            if (!method.getName().startsWith(methodPrefix) || method.getName().length() <= methodPrefix.length())
                continue;
            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1)
                continue;

            // Skip bridge methods
            if (method.isBridge())
                continue;

            // Add to list
            methodList.add(method);
        }

        // Sort the methods by name
        methodList.sort(Comparator.comparing(Method::getName));

        // Map methods to property name and check for conflicts
        for (Method method : methodList) {

            // Get property name for this method (or null to skip)
            final String propertyName = this.methodPropertyNameFunction.apply(method);
            if (propertyName == null)
                continue;

            // Verify there's no conflict
            if (propertyName.equals(this.implementationPropertyName)) {
                throw new IllegalArgumentException("conflicting property name "
                  + propertyName + "() used for implemenation methods and " + method);
            }
            final Method conflict = setterMap.put(propertyName, method);
            if (conflict != null) {
                throw new IllegalArgumentException("conflicting property name "
                  + propertyName + "() used for both [" + conflict + "] and [" + method + "]");
            }
        }
    }

    private String getDefaultValue(Class<?> cl, Method method) {

        // Initialize
        final Class<?> ptype = method.getParameterTypes()[0];
        final Class<?> wtype = ptype.isPrimitive() ? Primitive.get(ptype).getWrapperType() : ptype;
        final Object unknown = new Object();
        Object actualDefault = unknown;

        // Check for an explicitly provided default
        if (this.defaultOverrideFunction != null) {
            final String explicitDefault = this.defaultOverrideFunction.apply(method);
            if (explicitDefault != null)
                return explicitDefault;
        }

        // Instantiate the class and try to see what the actual default is
        try {
            final Object obj = cl.newInstance();
            final String getterName = method.getName().replaceAll("^set", ptype == boolean.class ? "is" : "get");
            final Method getter = cl.getMethod(getterName);
            actualDefault = getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            // ignore
        } catch (InvocationTargetException e) {
            try {
                throw e.getCause();
            } catch (UnsupportedOperationException cause) {
                // ignore
            } catch (Throwable cause) {
                throw new IllegalArgumentException(cause);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }

        // Deal with Optionals
        if (actualDefault instanceof Optional && !ptype.isAssignableFrom(Optional.class))
            actualDefault = ((Optional<?>)actualDefault).orElse(null);

        // Verify default value has the right type
        if (actualDefault != null && actualDefault != unknown && !wtype.isInstance(actualDefault)) {
            if (this.warnedAboutWrongType.add(method)) {
                this.logger.accept(LOG_WARN, "Default value for " + method + " has type "
                  + actualDefault.getClass().getName() + " which is incompatible with " + wtype);
            }
            actualDefault = null;
        }

        // If default is still null, too bad - annotations can't have null defaults
        if (actualDefault == unknown)
            throw new IllegalArgumentException("no default value found");
        if (actualDefault == null)
            throw new IllegalArgumentException("default value is null");

        // Express default as annotation default value in Java source code
        if (ptype.isPrimitive()) {
            final Primitive<?> primitive = Primitive.get(ptype);
            switch (primitive.getLetter()) {
            case 'F':       // float
            {
                final float value = (float)(Float)actualDefault;
                if (Float.isNaN(value))
                    return "Float.NaN";
                if (Float.compare(value, Float.POSITIVE_INFINITY) == 0)
                    return "Float.POSITIVE_INFINITY";
                if (Float.compare(value, Float.NEGATIVE_INFINITY) == 0)
                    return "Float.NEGATIVE_INFINITY";
                if (Float.compare(value, Float.MAX_VALUE) == 0)
                    return "Float.MAX_VALUE";
                if (Float.compare(value, Float.MIN_NORMAL) == 0)
                    return "Float.MIN_NORMAL";
                if (Float.compare(value, Float.MIN_VALUE) == 0)
                    return "Float.MIN_VALUE";
                break;
            }
            case 'D':       // double
            {
                final double value = (double)(Double)actualDefault;
                if (Double.isNaN(value))
                    return "Double.NaN";
                if (Double.compare(value, Double.POSITIVE_INFINITY) == 0)
                    return "Double.POSITIVE_INFINITY";
                if (Double.compare(value, Double.NEGATIVE_INFINITY) == 0)
                    return "Double.NEGATIVE_INFINITY";
                if (Double.compare(value, Double.MAX_VALUE) == 0)
                    return "Double.MAX_VALUE";
                if (Double.compare(value, Double.MIN_NORMAL) == 0)
                    return "Double.MIN_NORMAL";
                if (Double.compare(value, Double.MIN_VALUE) == 0)
                    return "Double.MIN_VALUE";
                break;
            }
            case 'C':       // char
            {
                final char value = (char)(Character)actualDefault;
                switch (value) {
                case '\'':
                case '\\':
                    return "'\\" + value + "'";
                default:
                    if (String.valueOf(value).matches("\\p{Print}"))
                        return "'" + String.valueOf(value) + "'";
                    return String.format("'\\u%04x'", value & 0xffff);
                }
            }
            default:
                break;
            }
            return "" + actualDefault;
        }
        if (ptype == String.class)
            return StringEncoder.enquote((String)actualDefault);
        if (ptype.isArray() && actualDefault.getClass().isArray() && Array.getLength(actualDefault) == 0)
            return "{}";
        if (ptype.isInterface())
            return ptype.getName() + ".class";
        if (ptype.isEnum())
            return actualDefault.getClass().getName() + "." + ((Enum<?>)actualDefault).name();

        // Fail
        throw new IllegalArgumentException("can't describe default value for " + method + ": " + actualDefault);
    }

    private boolean isSupportedAnnotationPropertyType(Class<?> cl) {
        if (cl.isPrimitive() && cl != void.class)
            return true;
        if (cl == String.class || cl == Class.class)
            return true;
        if (Enum.class.isAssignableFrom(cl))
            return true;
        if (Annotation.class.isAssignableFrom(cl))
            return true;
        if (cl.isArray() && !cl.getComponentType().isArray())
            return this.isSupportedAnnotationPropertyType(cl.getComponentType());
        return false;
    }

    private String linkFor(Method method) {
        final StringBuilder buf = new StringBuilder();
        buf.append(method.getDeclaringClass().getName())
          .append('#')
          .append(method.getName())
          .append('(');
        boolean firstParam = true;
        for (Class<?> ptype : method.getParameterTypes()) {
            if (firstParam)
                firstParam = false;
            else
                buf.append(", ");
            buf.append(this.srcName(ptype));
        }
        buf.append(")");
        return buf.toString();
    }

    private void lines(int indent, String... lines) {
        for (String line : lines) {
            if (!line.isEmpty()) {
                for (int i = 0; i < indent; i++)
                    this.output.print(TAB);
            }
            this.output.println(line);
        }
    }

    private String srcName(Class<?> type) {
        if (type.isArray())
            return this.srcName(type.getComponentType()) + "[]";
        return type.getName()
          .replaceAll("\\$", ".")
          .replaceAll("^java\\.lang\\.([A-Z].*)$", "$1");
    }
}
