
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.dellroad.stuff.xml.TransformErrorListener;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * {@link SpringPersistentObjectSchemaUpdate} that applies a configured XSL transform to the XML form of the persistent object.
 *
 * <p>
 * The {@link #setTransform transform} property is required.
 *
 * <p>
 * See {@link SpringPersistentObjectSchemaUpdater} for a Spring XML configuration example.
 *
 * @param <T> type of the persistent object
 */
public class SpringXSLPersistentObjectSchemaUpdate<T> extends SpringPersistentObjectSchemaUpdate<T> {

    private Resource transform;
    private Properties parameters;
    private TransformerFactory transformerFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (this.transform == null)
            throw new Exception("no transform configured");
    }

    /**
     * Configure the XSLT transform as a resource.
     *
     * @param transform transform for this schema update
     */
    public void setTransform(Resource transform) {
        this.transform = transform;
    }

    public Resource getTransform() {
        return this.transform;
    }

    /**
     * Configure XSLT parameters. This is an optional property.
     *
     * @param parameters transform parameters
     */
    public void setParameters(Properties parameters) {
        this.parameters = parameters;
    }

    /**
     * Get the configured {@link TransformerFactory} that will be used to create the {@link Transformer}
     * that will be used to actually apply the {@linkplain #setTransform configured XSL transform}.
     *
     * <p>
     * This property is optional; if not specified, {@link TransformerFactory#newInstance} is used.
     *
     * @return custom factory for XSL transformers, or null if none configured
     */
    public TransformerFactory getTransformerFactory() {
        return this.transformerFactory;
    }

    /**
     * Set the {@link TransformerFactory} to use.
     *
     * <p>
     * This property is optional; if not specified, {@link TransformerFactory#newInstance} is used.
     *
     * @param transformerFactory custom factory for XSL transformers, or null for none
     * @see #getTransformerFactory
     */
    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    /**
     * Apply this update to the given transaction.
     */
    @Override
    public void apply(PersistentFileTransaction transaction) {

        // Get transform source
        try (InputStream input = this.transform.getInputStream()) {

            // Setup transformer
            TransformerFactory tf = this.transformerFactory;
            if (tf == null)
                tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer(new StreamSource(input, this.transform.getURI().toString()));
            transformer.setErrorListener(new TransformErrorListener(LoggerFactory.getLogger(this.getClass()), true));
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (this.parameters != null) {
                for (String name : this.parameters.stringPropertyNames())
                    transformer.setParameter(name, this.parameters.getProperty(name));
            }

            // Do the transform
            transaction.transform(transformer);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TransformerException && e.getCause().getCause() instanceof RuntimeException)
                e = (RuntimeException)e.getCause().getCause();
            throw e;
        }
    }
}
