
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.function.Supplier;

import org.dellroad.stuff.java.IdGenerator;
import org.jibx.runtime.BindingDirectory;
import org.jibx.runtime.IBindingFactory;
import org.jibx.runtime.IMarshallable;
import org.jibx.runtime.IMarshallingContext;
import org.jibx.runtime.IUnmarshallingContext;
import org.jibx.runtime.JiBXException;

/**
 * Some simplified API methods for JiBX XML encoding/decoding.
 */
public final class JiBXUtil {

    public static final String XML_ENCODING = "UTF-8";

    private JiBXUtil() {
    }

    /**
     * Read in an object encoded as XML.
     * This method assumes there is exactly one binding for the given class.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * <p>
     * The {@code input} is not closed by this method.
     *
     * @param <T> type of object to read
     * @param targetClass target class
     * @param input source for the XML document
     * @return decoded object
     * @throws JiBXException if there is a JiBX parse error
     * @throws IOException if an error occurs reading from {@code input}
     */
    public static <T> T readObject(Class<T> targetClass, InputStream input) throws JiBXException, IOException {
        return JiBXUtil.readObject(targetClass, null, input);
    }

    /**
     * Read in an object encoded as XML.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * <p>
     * The {@code input} is not closed by this method.
     *
     * @param <T> type of object to read
     * @param targetClass target class
     * @param bindingName binding name, or null to choose the only one
     * @param input source for the XML document
     * @return decoded object
     * @throws JiBXException if there is a JiBX parse error
     * @throws IOException if an error occurs reading from {@code input}
     */
    public static <T> T readObject(final Class<T> targetClass, String bindingName, final InputStream input)
      throws JiBXException, IOException {
        IBindingFactory bindingFactory = bindingName != null ?
          BindingDirectory.getFactory(bindingName, targetClass) : BindingDirectory.getFactory(targetClass);
        final IUnmarshallingContext unmarshallingContext = bindingFactory.createUnmarshallingContext();
        return JiBXUtil.runIdGenerator(() -> targetClass.cast(unmarshallingContext.unmarshalDocument(input, null)));
    }

    /**
     * Read in an object encoded as XML.
     * This method assumes there is exactly one binding for the given class.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * <p>
     * The {@code input} is not closed by this method.
     *
     * @param <T> type of object to read
     * @param targetClass target class
     * @param input source for the XML document
     * @return decoded object
     * @throws JiBXException if there is a JiBX parse error
     * @throws IOException if an error occurs reading from {@code input}
     */
    public static <T> T readObject(Class<T> targetClass, Reader input) throws JiBXException, IOException {
        return JiBXUtil.readObject(targetClass, null, input);
    }

    /**
     * Read in an object encoded as XML.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * <p>
     * The {@code input} is not closed by this method.
     *
     * @param <T> type of object to read
     * @param targetClass target class
     * @param bindingName binding name, or null to choose the only one
     * @param input source for the XML document
     * @return decoded object
     * @throws JiBXException if there is a JiBX parse error
     * @throws IOException if an error occurs reading from {@code input}
     */
    public static <T> T readObject(final Class<T> targetClass, String bindingName, final Reader input)
      throws JiBXException, IOException {
        IBindingFactory bindingFactory = bindingName != null ?
          BindingDirectory.getFactory(bindingName, targetClass) : BindingDirectory.getFactory(targetClass);
        final IUnmarshallingContext unmarshallingContext = bindingFactory.createUnmarshallingContext();
        return JiBXUtil.runIdGenerator(() -> targetClass.cast(unmarshallingContext.unmarshalDocument(input)));
    }

    /**
     * Read in an object encoded as XML from an {@link URL}.
     * This method assumes there is exactly one binding for the given class.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param <T> type of object to read
     * @param targetClass target class
     * @param url source for the XML document
     * @return decoded object
     * @throws JiBXException if there is a JiBX parse error
     * @throws IOException if an error occurs reading the referenced document
     */
    public static <T> T readObject(Class<T> targetClass, URL url) throws JiBXException, IOException {
        return JiBXUtil.readObject(targetClass, null, url);
    }

    /**
     * Read in an object encoded as XML from an {@link URL}.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param <T> type of object to read
     * @param targetClass target class
     * @param url source for the XML document
     * @param bindingName binding name, or null to choose the only one
     * @return decoded object
     * @throws JiBXException if there is a JiBX parse error
     * @throws IOException if an error occurs reading the referenced document
     */
    public static <T> T readObject(Class<T> targetClass, String bindingName, URL url) throws JiBXException, IOException {
        try (InputStream in = url.openStream()) {
            return JiBXUtil.readObject(targetClass, bindingName, in);
        }
    }

    /**
     * Write out the given instance encoded as a UTF-8 encoded XML document.
     * This method assumes there is exactly one binding for the given class.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param <T> object type
     * @param obj object to write
     * @param output output destination; will <b>not</b> be closed by this method
     * @throws JiBXException if there is a JiBX encoding error
     * @throws IOException if an error occurs writing to {@code output}
     */
    public static <T> void writeObject(T obj, OutputStream output) throws JiBXException, IOException {
        JiBXUtil.writeObject(obj, null, output);
    }

    /**
     * Write out the given instance encoded as a UTF-8 encoded XML document.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param <T> object type
     * @param obj object to write
     * @param bindingName binding name, or null to choose the only one
     * @param output output destination; will <b>not</b> be closed by this method
     * @throws JiBXException if there is a JiBX encoding error
     * @throws IOException if an error occurs writing to {@code output}
     */
    public static <T> void writeObject(T obj, String bindingName, OutputStream output) throws JiBXException, IOException {
        JiBXUtil.writeObject(obj, bindingName, new OutputStreamWriter(output, XML_ENCODING));
    }

    /**
     * Write out the given instance encoded as an XML document with "UTF-8" as the declared encoding.
     * This method assumes there is exactly one binding for the given class.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param <T> object type
     * @param obj object to write
     * @param writer output destination; will <b>not</b> be closed by this method
     * @throws JiBXException if there is a JiBX encoding error
     * @throws IOException if an error occurs writing to {@code writer}
     */
    public static <T> void writeObject(T obj, Writer writer) throws JiBXException, IOException {
        JiBXUtil.writeObject(obj, null, writer);
    }

    /**
     * Write out the given instance encoded as an XML document with "UTF-8" as the declared encoding.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param obj object to write
     * @param bindingName binding name, or null to choose the only one
     * @param writer output destination; will <b>not</b> be closed by this method
     * @throws JiBXException if there is a JiBX encoding error
     * @throws IOException if an error occurs writing to {@code writer}
     */
    public static void writeObject(final Object obj, String bindingName, final Writer writer) throws JiBXException, IOException {
        IBindingFactory bindingFactory = bindingName != null ?
          BindingDirectory.getFactory(bindingName, obj.getClass()) : BindingDirectory.getFactory(obj.getClass());
        final IMarshallingContext marshallingContext = bindingFactory.createMarshallingContext();
        JiBXUtil.runIdGenerator(() -> {
            final BufferedWriter bufferedWriter = new BufferedWriter(writer);
            marshallingContext.setIndent(4);
            marshallingContext.setOutput(bufferedWriter);
            marshallingContext.startDocument(XML_ENCODING, null);
            ((IMarshallable)obj).marshal(marshallingContext);
            marshallingContext.getXmlWriter().flush();
            bufferedWriter.newLine();
            bufferedWriter.flush();
            return null;
        });
    }

    /**
     * Encode the given instance as an XML document and return it as a {@link String}.
     * This method assumes there is exactly one binding for the given class.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param obj object to encode
     * @return XML encoding of {@code obj}
     * @throws JiBXException if there is a JiBX encoding error
     */
    public static String toString(Object obj) throws JiBXException {
        return JiBXUtil.toString(obj, null);
    }

    /**
     * Encode the given instance as an XML document and return it as a {@link String}.
     *
     * <p>
     * This method runs within a new invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()} to support object references
     * (see {@link IdMapper}).
     *
     * @param obj object to encode
     * @param bindingName binding name, or null to choose the only one
     * @return XML encoding of {@code obj}
     * @throws JiBXException if there is a JiBX encoding error
     */
    public static String toString(Object obj, String bindingName) throws JiBXException {
        final StringWriter w = new StringWriter();
        try {
            JiBXUtil.writeObject(obj, bindingName, w);
            w.close();
        } catch (IOException e) {
            throw new JiBXException("unexpected exception", e);
        }
        return w.toString();
    }

// Exception wrangling

    private static <R> R runIdGenerator(JiBXSupplier<R> action) throws JiBXException, IOException {
        if (action == null)
            throw new IllegalArgumentException("null action");
        try {
            return IdGenerator.run(() -> {
                try {
                    return action.get();
                } catch (JiBXException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof JiBXException)
                throw (JiBXException)e.getCause();
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            throw e;
        }
    };

    @FunctionalInterface
    private interface JiBXSupplier<R> {
        R get() throws JiBXException, IOException;
    }
}
