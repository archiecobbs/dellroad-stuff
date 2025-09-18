
/*
 * Copyright (C) 2023 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.CodeSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An {@link URLClassLoader} that maintains an updatable cache of class files (as {@code byte[]} arrays)
 * in memory from which classes may be loaded.
 *
 * <p>
 * The internal cache of classes is initially empty. Binary class files may be added (or removed) via
 * {@link #putClass putClass()}, and retrieved via {@link #getClass(String) getClass()}. The {@code byte[]}
 * arrays passed to and from these methods are copied to ensure immutability.
 *
 * <p>
 * Classes defined by this loader are assigned {@link URL}s that look like {@code memory:/com.example.MyClass}.
 *
 * <p>
 * This class is thread safe and {@linkplain #registerAsParallelCapable parallel capable}.
 */
public class MemoryClassLoader extends URLClassLoader {

    public static final String MEMORY_URL_SCHEME = "memory";

    private final Map<String, ClassData> classDataMap = new HashMap<>();

    static {
        ClassLoader.registerAsParallelCapable();
    }

// Constructor

    /**
     * Default constructor.
     *
     * <p>
     * Uses the current thread's context loader as the parent loader.
     */
    public MemoryClassLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Constructor.
     *
     * @param parent parent class loader, possibly null
     */
    public MemoryClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

// Public Methods

    /**
     * Add a class to this loader, making it available for class resolution.
     *
     * <p>
     * If an existing class already exists under {@code className}, it will be replaced.
     * The {@code classbytes} array is copied to ensure immutability.
     *
     * <p>
     * If {@code classbytes} is null, any existing class will be removed.
     *
     * @param className Java class name
     * @param classbytes Java class file data
     * @throws IllegalArgumentException if either parameter is null
     */
    public void putClass(String className, byte[] classbytes) {
        if (className == null)
            throw new IllegalArgumentException("null className");
        synchronized (this) {
            if (classbytes != null)
                this.classDataMap.put(className, new ClassData(classbytes.clone()));
            else
                this.classDataMap.remove(className);
        }
    }

    /**
     * Get the class from this loader that was previously added via {@link #putClass putClass()}, if any.
     *
     * <p>
     * The returned array is a copy to ensure immutability.
     *
     * @param className Java class name
     * @return class file previously added under the name {@code className}, or null if none exists
     */
    public synchronized byte[] getClass(String className) {
        return this.classDataMap.get(className).getClassbytes().clone();
    }

    /**
     * Links the specified class.
     *
     * <p>
     * This method just invokes {@code this.}{@link #resolveClass resolveClass(c)}.
     *
     * @param c the class to link
     * @see #resolveClass
     */
    public void linkClass(Class<?> c) {
        this.resolveClass(c);
    }

// URLClassLoader

    // Make this public
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
        final ClassData classData;
        synchronized (this) {
            classData = this.classDataMap.get(className);
        }
        if (classData == null)
            return super.findClass(className);
        final byte[] classbytes = classData.getClassbytes();
        return this.defineClass(className, classbytes, 0, classbytes.length, (CodeSource)null);
    }

    @Override
    public URL findResource(String resourceName) {
        final URL resource = this.findClassDataResource(resourceName);
        if (resource != null)
            return resource;
        return super.findResource(resourceName);
    }

    @Override
    public Enumeration<URL> findResources(String resourceName) throws IOException {
        Enumeration<URL> resources = super.findResources(resourceName);
        final URL classDataResource = this.findClassDataResource(resourceName);
        if (classDataResource != null) {
            final ArrayList<URL> list = new ArrayList<>();
            while (resources.hasMoreElements())
                list.add(resources.nextElement());
            list.add(classDataResource);
            resources = Collections.enumeration(list);
        }
        return resources;
    }

    // Locate the class file having the given resource filename
    private URL findClassDataResource(String resourceName) {

        // Validate
        if (resourceName == null)
            throw new IllegalArgumentException("null resourceName");

        // Get the class name that would correspond to the given resource
        if (!resourceName.endsWith(".class") || resourceName.startsWith("/"))
            return null;
        final String className = resourceName.substring(0, resourceName.length() - 6).replace('/', '.');

        // Do we have data for that class?
        final ClassData classData;
        synchronized (this) {
            classData = this.classDataMap.get(className);
        }
        if (classData == null)
            return null;

        // Build an URL for it
        try {
            final URI memoryURI = new URI(MEMORY_URL_SCHEME, null, "/" + className, null);
            return new URL(null, memoryURI.toString(), new MemoryURLStreamHandler(classData));
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException("internal error", e);
        }
    }

// ClassData

    // Holds a class definable by this loader
    private static class ClassData {

        private final byte[] classbytes;
        private final long createTime = System.currentTimeMillis();

        ClassData(byte[] classbytes) {
            if (classbytes == null)
                throw new IllegalArgumentException("null classbytes");
            this.classbytes = classbytes;
        }

        public byte[] getClassbytes() {
            return this.classbytes;
        }

        public long getCreateTime() {
            return this.createTime;
        }
    }

// MemoryURLStreamHandler

    private static class MemoryURLStreamHandler extends URLStreamHandler {

        private final ClassData classData;

        MemoryURLStreamHandler(ClassData classData) {
            if (classData == null)
                throw new IllegalArgumentException("null classData");
            this.classData = classData;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new MemoryURLConnection(url, this.classData.getClassbytes(), this.classData.getCreateTime());
        }
    }

// MemoryURLConnection

    private static class MemoryURLConnection extends URLConnection {

        private static final String CONTENT_LENGTH_HEADER = "Content-Length";
        private static final String DATE_HEADER = "Date";
        private static final String LAST_MODIFIED_HEADER = "Last-Modified";

        private final InputStream in;
        private final String[][] fields;

    // Constructors

        MemoryURLConnection(URL url, ClassData classData) {
            this(url, classData.getClassbytes(), classData.getCreateTime());
        }

        MemoryURLConnection(URL url, byte[] classbytes, long timestamp) {
            super(url);
            if (classbytes == null)
                throw new IllegalArgumentException("null classbytes");
            this.in = new ByteArrayInputStream(classbytes);
            final Instant instant = Instant.ofEpochMilli(timestamp);
            final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("GMT"));
            final String timestampString = DateTimeFormatter.RFC_1123_DATE_TIME.format(zonedDateTime);
            this.fields = new String[][] {
              { CONTENT_LENGTH_HEADER,   "" + classbytes.length },
              { DATE_HEADER,             timestampString },
              { LAST_MODIFIED_HEADER,    timestampString }
            };
        }

    // URLConnection

        @Override
        public void connect() {
            // nothing to do
        }

        @Override
        public InputStream getInputStream() {
            return this.in;
        }

        @Override
        public String getHeaderField(String name) {
            return Stream.of(this.fields)
              .filter(pair -> pair[0].equals(name))
              .map(pair -> pair[1])
              .findFirst()
              .orElse(null);
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return Stream.of(this.fields)
              .collect(Collectors.toMap(pair -> pair[0], pair -> Collections.singletonList(pair[1])));
        }

        @Override
        public String getHeaderFieldKey(int index) {
            return index < this.fields.length ? this.fields[index][0] : null;
        }

        @Override
        public String getHeaderField(int index) {
            return index < this.fields.length ? this.fields[index][1] : null;
        }
    }
}
