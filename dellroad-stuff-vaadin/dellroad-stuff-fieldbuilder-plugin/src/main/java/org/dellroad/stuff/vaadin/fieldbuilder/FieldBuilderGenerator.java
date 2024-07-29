
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin.fieldbuilder;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.dellroad.stuff.java.Primitive;
import org.dellroad.stuff.java.ReflectUtil;
import org.dellroad.stuff.string.StringEncoder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/**
 * Generates {@code FieldBuilder} source files.
 */
public class FieldBuilderGenerator {

    private static final String HAS_STYLE_CLASS_NAME = "com.vaadin.flow.component.HasStyle";
    private static final String STYLE_CLASS_NAME = "com.vaadin.flow.dom.Style";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");
    private static final String INDENT = "    ";

    private final Class<?> requiredType;
    private final List<String> packageRoots = new ArrayList<>();
    private final HashSet<Method> warnedAboutDefault = new HashSet<>();
    private final HashSet<Method> warnedAboutWrongType = new HashSet<>();

    private Logger log = this.getDefaultLogger();
    private Function<? super Class<?>, String> annotationNameFunction;
    private BiFunction<? super Class<?>, ? super Method, String> methodPropertyNameFunction;
    private BiFunction<? super Class<?>, ? super Method, String> defaultOverrideFunction;
    private Predicate<? super Class<?>> classInclusionPredicate;
    private final List<Class<?>> fieldTypes = new ArrayList<>();
    private String annotationDefaultsMethodName;
    private String implementationPropertyName;
    private String separatorLine;
    private boolean includeStyleProperties;

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

    public void setLogger(Logger log) {
        this.log = Optional.ofNullable(log).orElse((level, format, params) -> { });
    }

    public void setAnnotationNameFunction(Function<? super Class<?>, String> annotationNameFunction) {
        this.annotationNameFunction = annotationNameFunction;
    }

    public void setMethodPropertyNameFunction(BiFunction<? super Class<?>, ? super Method, String> methodPropertyNameFunction) {
        this.methodPropertyNameFunction = methodPropertyNameFunction;
    }

    public void setDefaultOverrideFunction(BiFunction<? super Class<?>, ? super Method, String> defaultOverrideFunction) {
        this.defaultOverrideFunction = defaultOverrideFunction;
    }

    public void setAnnotationDefaultsMethodName(String annotationDefaultsMethodName) {
        this.annotationDefaultsMethodName = annotationDefaultsMethodName;
    }

    public void setImplementationPropertyName(String implementationPropertyName) {
        this.implementationPropertyName = implementationPropertyName;
    }

    public void setClassInclusionPredicate(Predicate<? super Class<?>> classInclusionPredicate) {
        this.classInclusionPredicate = classInclusionPredicate;
    }

    public void setIncludeStyleProperties(boolean includeStyleProperties) {
        this.includeStyleProperties = includeStyleProperties;
    }

    protected Logger getDefaultLogger() {
        return (level, format, params) -> {
            if (level >= Logger.DEBUG)
                return;
            final PrintStream dest = level <= Logger.WARN ? System.err : System.out;
            dest.println(String.format(format, params));
        };
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
            this.log.log(Logger.INFO, "Searching under %s", packageRoot);
            final String resourcePath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
              + packageRoot.replace('.', '/') + "/" + "**/*.class";

            // Find classes under this package root that subtype our required type
            this.log.log(Logger.DEBUG, "Searching for resources matching \"%s\"", resourcePath);
            try {
                for (Resource resource : resourceLoader.getResources(resourcePath)) {

                    // Determine class name
                    final String uriString = resource.getURI().toString();
                    if (!resource.getURI().toString().endsWith(".class"))
                        continue;
                    this.log.log(Logger.DEBUG, "Checking resource: %s", uriString);
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
                    if (!this.requiredType.isAssignableFrom(cl)) {
                        this.log.log(Logger.DEBUG, "--> %s is not assignable to %s", cl, this.requiredType);
                        continue;
                    }
                    if (this.classInclusionPredicate != null && !this.classInclusionPredicate.test(cl)) {
                        this.log.log(Logger.DEBUG, "--> %s is explicity not included", cl);
                        continue;
                    }
                    try {
                        cl.getConstructor();
                    } catch (NoSuchMethodException e) {
                        this.log.log(Logger.DEBUG, "--> %s has no default constructor", cl);
                        continue;
                    }

                    // Add class to our list
                    this.log.log(Logger.DEBUG, "--> %s matched", cl);
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
        if (this.annotationDefaultsMethodName == null)
            throw new IllegalStateException("null annotationDefaultsMethodName");
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
        this.fieldTypes.stream().forEach(this::generateDefaultAnnotation);
        this.lines(1, "private static java.util.List<Class<? extends java.lang.annotation.Annotation>> "
          + this.annotationDefaultsMethodName + "() {");
        this.lines(2, "return java.util.Arrays.asList(");
        for (int i = 0; i < this.fieldTypes.size(); i++) {
            final String comma = i < this.fieldTypes.size() - 1 ? "," : "";
            this.lines(3, this.getAnnotationName(this.fieldTypes.get(i)) + ".class" + comma);
        }
        this.lines(2, ");");
        this.lines(1, "}");

        // Generate annotations for all widget classes
        this.fieldTypes.stream().forEach(this::generateAnnotationClass);

        // Output final stuff
        this.lines(0, "}");
    }

    private void generateDefaultAnnotation(Class<?> cl) {

        // For abstract classes, we have to provide a value for the required implementation property
        final String propertiesText = this.implementationPropertyName != null && (cl.getModifiers() & Modifier.ABSTRACT) != 0 ?
          String.format("(%s = %s.class)", this.implementationPropertyName, this.getSourceName(cl)) : "";

        // Output annotation
        this.lines(1, String.format("@%s%s", this.getAnnotationName(cl), propertiesText));
    }

    private void generateAnnotationClass(final Class<?> cl) {

        // Get info
        final String sourceName = this.getSourceName(cl);
        final String article;
        switch (Character.toUpperCase(this.getShortSourceName(cl).charAt(0))) {
        case 'A':
        case 'E':
        case 'I':
        case 'O':
        case 'U':
            article = "an";
            break;
        default:
            article = "a";
            break;
        }
        final String annotationName = this.getAnnotationName(cl);

        // Output initial stuff
        this.lines(1,
          "",
          "/**",
          " * Specifies how a Java bean property should be edited using " + article + " " + this.wrapLink(cl) + ".",
          " *",
          " * @see FieldBuilder",
          " * @see " + sourceName,
          " */",
          "@Retention(RetentionPolicy.RUNTIME)",
          "@Target(ElementType.METHOD)",
          "@Documented",
          "public @interface " + annotationName + " {");

        // Output implementation() property
        final boolean isabstract = (cl.getModifiers() & Modifier.ABSTRACT) != 0;
        if (this.implementationPropertyName != null) {

            // Tweak property depending on whether class is abstract
            final String blurb = !isabstract ?
              "This property allows custom widget subclasses to be used." :
              "This property is required because " + this.wrapLink(cl) + " is an abstract class.";
            final String defaultClause = !isabstract ? " default " + sourceName + ".class" : "";

            // Output property
            this.lines(2,
              "",
              "/**",
              " * Get the sub-type of " + this.wrapLink(cl) + " that will edit the property.",
              " *",
              " * <p>",
              " * " + blurb,
              " *",
              " * <p>",
              " * The specified type must have a public constructor that takes either no arguments,",
              " * or one {@code FieldBuilderContext}.",
              " *",
              " * @return field type",
              " */",
              "@SuppressWarnings(\"rawtypes\")",
              "Class<? extends " + sourceName + "> " + this.implementationPropertyName + "()" + defaultClause + ";");
        } else if (isabstract) {
            throw new IllegalArgumentException(
              String.format("An implementationPropertyName is required to handle abstract classes such as %s", sourceName));
        }

        // Output styleProperties() if enabled
        if (this.includeStyleProperties && this.hasStyle(cl)) {
            this.lines(2,
              "",
              "/**",
              " * Specify CSS properties to be set via {@link " + STYLE_CLASS_NAME + "#set Style.set()}.",
              " *",
              " * <p>",
              " * The array value consists of name, value pairs. If the array has odd length, the last element is ignored.",
              " *",
              " * @return zero or more style property name, value pairs",
              " * @see " + STYLE_CLASS_NAME,
              " * @see " + HAS_STYLE_CLASS_NAME,
              " */",
              "String[] styleProperties() default {};");
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
                    this.log.log(Logger.ERROR, "No default value possible for %s: %s", method, e.getMessage());
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

        // Debug
        this.log.log(Logger.DEBUG, "Looking for %s*() setters in %s", methodPrefix, type);

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
        this.log.log(Logger.DEBUG, "Found these %s*() setters in %s:\n  %s", methodPrefix, type,
          methodList.stream().map(Object::toString).collect(Collectors.joining("\n  ")));

        // Map methods to property name and check for conflicts
        final HashMap<String, List<Method>> settersMap = new HashMap<>();
        for (Method method : methodList) {

            // Get property name for this method (or null to skip)
            final String propertyName = this.methodPropertyNameFunction.apply(type, method);
            if (propertyName == null) {
                this.log.log(Logger.DEBUG, "--> Setter %s is to be skipped", method);
                continue;
            }
            this.log.log(Logger.DEBUG, "--> Setter %s gets property name \"%s\"", method, propertyName);

            // Verify property name is allowed
            if (propertyName.equals(this.implementationPropertyName)) {
                throw new IllegalArgumentException("conflicting property name "
                  + propertyName + "() used for implemenation methods and " + method);
            }

            // Add to the list of methods for this property name
            settersMap.computeIfAbsent(propertyName, n -> new ArrayList<>()).add(method);
        }

        // Sort methods with narrower parameter types first
        settersMap.values().forEach(list -> ReflectUtil.sortByType(list, method -> method.getParameterTypes()[0]));

        // For property names that map to multiple methods with the same name, we pick
        // the last method, with the widest parameter type, as the representative method.
        // But all other methods must have parameters that are sub-types of it.
        settersMap.forEach((propertyName, propertyMethodList) -> {

            // Get preferred method from the end of the list
            final Method method = propertyMethodList.get(propertyMethodList.size() - 1);

            // Verify all other methods have comparable (and therefore narrower) types
            final Class<?> paramType = method.getParameterTypes()[0];
            for (Method otherMethod : propertyMethodList) {
                if (otherMethod == method)
                    continue;
                final Class<?> otherParamType = otherMethod.getParameterTypes()[0];
                if (!paramType.isAssignableFrom(otherParamType)) {
                    throw new IllegalArgumentException("conflicting property name " + propertyName
                      + "() used for methods [" + method + "] and [" + otherMethod + "] having"
                      + " incomparable parameter types - METHOD LIST: " + propertyMethodList);
                }
            }

            // Map property to method
            this.log.log(Logger.DEBUG, "Mapping property name \"%s\" to %s", propertyName, method);
            setterMap.put(propertyName, method);
        });
    }

    private boolean hasStyle(Class<?> cl) {
        final Class<?> hasStyleClass;
        try {
            hasStyleClass = Thread.currentThread().getContextClassLoader().loadClass(HAS_STYLE_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            return false;
        }
        return hasStyleClass.isAssignableFrom(cl);
    }

    // Get the (simple) name of one of our generated annotation classes
    private String getAnnotationName(Class<?> cl) {
        assert cl.getEnclosingClass() == null;
        if (this.annotationNameFunction == null)
            return cl.getSimpleName();
        final String annotationName = this.annotationNameFunction.apply(cl);
        if (annotationName == null || !IDENTIFIER_PATTERN.matcher(annotationName).matches())
            throw new IllegalArgumentException("invalid result from annotation name function for " + cl + ": " + annotationName);
        return annotationName;
    }

    // Get the fully-qualified name of the given class as it should appear in source code
    private String getSourceName(Class<?> cl) {
        return cl.getCanonicalName();
    }

    // Get the unqualified name of the given class as it should appear in source code
    private String getShortSourceName(Class<?> cl) {
        String name = cl.getCanonicalName();
        final String packageName = cl.getPackage().getName();
        if (!packageName.isEmpty())
            name = name.substring(packageName.length() + 1);
        return name;
    }

    private String getDefaultValue(Class<?> cl, Method method) {

        // Initialize
        final Class<?> ptype = method.getParameterTypes()[0];
        final Class<?> wtype = Primitive.wrap(ptype);
        final Object unknown = new Object();
        Object actualDefault = unknown;

        // Check for an explicitly provided default
        if (this.defaultOverrideFunction != null) {
            final String explicitDefault = this.defaultOverrideFunction.apply(cl, method);
            if (explicitDefault != null)
                return explicitDefault;
        }

        // Instantiate the class and try to see what the actual default is
        try {
            final Object obj = cl.getDeclaredConstructor().newInstance();
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
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }

        // Deal with Optionals
        if (actualDefault instanceof Optional && !ptype.isAssignableFrom(Optional.class))
            actualDefault = ((Optional<?>)actualDefault).orElse(null);

        // Verify default value has the right type
        if (actualDefault != null && actualDefault != unknown && !wtype.isInstance(actualDefault)) {
            if (this.warnedAboutWrongType.add(method)) {
                this.log.log(Logger.WARN, "Default value for %s has type %s which is incompatible with %s",
                  method, this.getSourceName(actualDefault.getClass()), wtype);
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
            return this.getSourceName(actualDefault.getClass()) + "." + ((Enum<?>)actualDefault).name();

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
        buf.append(this.getSourceName(method.getDeclaringClass()))
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

    private String wrapLink(Class<?> cl) {
        return String.format("{@link %s}", this.getSourceName(cl));
    }

    private void lines(int indent, String... lines) {
        for (String line : lines) {
            if (!line.isEmpty()) {
                for (int i = 0; i < indent; i++)
                    this.output.print(INDENT);
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

// Logger

    /**
     * Callback logging interface used by {@link FieldBuilderGenerator}.
     */
    @FunctionalInterface
    public interface Logger {

        int ERROR = 0;
        int WARN = 1;
        int INFO = 2;
        int DEBUG = 3;

        /**
         * Format the log message using {@link String#format String.format()} and log it at the given level.
         *
         * @param level log level, one of {@link #ERROR}, {@link #WARN}, {@link #INFO}, or {@link #DEBUG}
         * @param format format string
         * @param params format string parameters
         */
        void log(int level, String format, Object... params);
    }
}
