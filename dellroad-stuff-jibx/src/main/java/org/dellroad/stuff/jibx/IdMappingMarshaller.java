
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import java.io.IOException;
import java.util.concurrent.Callable;

import javax.annotation.PostConstruct;
import javax.xml.transform.Result;
import javax.xml.transform.Source;

import org.dellroad.stuff.java.IdGenerator;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;

/**
 * Wrapper for Spring's {@link org.springframework.oxm.jibx.JibxMarshaller} that performs marshalling and unmarshalling operations
 * within an invocation of {@link IdGenerator#run IdGenerator.run()}. Simply set your
 * normal {@link org.springframework.oxm.jibx.JibxMarshaller} to the {@linkplain #setJibxMarshaller jibxMarshaller}
 * property and use this class in its place.
 *
 * <p>
 * This is required when marshalling with JiBX mappings that utilize {@link IdMapper}.
 *
 * @see IdMapper
 */
public class IdMappingMarshaller implements Marshaller, Unmarshaller {

    @SuppressWarnings("deprecation")
    private org.springframework.oxm.jibx.JibxMarshaller jibxMarshaller;

    /**
     * Configure the nested {@link org.springframework.oxm.jibx.JibxMarshaller}. Required property.
     *
     * @param jibxMarshaller nested marshaller
     */
    @SuppressWarnings("deprecation")
    public void setJibxMarshaller(org.springframework.oxm.jibx.JibxMarshaller jibxMarshaller) {
        this.jibxMarshaller = jibxMarshaller;
    }

    @PostConstruct
    public void afterPropertiesSet() {
        if (this.jibxMarshaller == null)
            throw new IllegalArgumentException("no jibxMarshaller configured");
    }

    /**
     * Invokdes {@link org.springframework.oxm.jibx.JibxMarshaller#marshal JibxMarshaller.marshal()} on the configured
     * {@link org.springframework.oxm.jibx.JibxMarshaller} within an invocation of
     * {@link IdGenerator#run(Callable) IdGenerator.run()}.
     */
    @Override
    public void marshal(final Object graph, final Result result) throws IOException {
        try {
            IdGenerator.run(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    IdMappingMarshaller.this.jibxMarshaller.marshal(graph, result);
                    return null;
                }
            });
        } catch (Exception e) {
            this.unwrapException(e);
        }
    }

    /**
     * Invokdes {@link org.springframework.oxm.jibx.JibxMarshaller#unmarshal JibxMarshaller.unmarshal()} on the configured
     * {@link org.springframework.oxm.jibx.JibxMarshaller} within an invocation of
     * {@link IdGenerator#run(Callable) IdGenerator.run()}.
     */
    @Override
    public Object unmarshal(final Source source) throws IOException {
        try {
            return IdGenerator.run(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return IdMappingMarshaller.this.jibxMarshaller.unmarshal(source);
                }
            });
        } catch (Exception e) {
            this.unwrapException(e);
            return null;                // never reached
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return this.jibxMarshaller.supports(type);
    }

    private void unwrapException(Exception e) throws IOException {
        if (e instanceof IOException)
            throw (IOException)e.getCause();
        if (e instanceof RuntimeException)
            throw (RuntimeException)e;
        throw new RuntimeException(e);
    }
}

