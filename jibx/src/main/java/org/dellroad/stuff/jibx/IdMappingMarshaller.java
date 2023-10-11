
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import java.io.IOException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;

import org.dellroad.stuff.java.IdGenerator;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;

/**
 * Wrapper for Spring's {@link JibxMarshaller} that performs marshalling and unmarshalling operations
 * within an invocation of {@link IdGenerator#run IdGenerator.run()}. Simply set your
 * normal {@link JibxMarshaller} to the {@linkplain #setJibxMarshaller jibxMarshaller}
 * property and use this class in its place.
 *
 * <p>
 * This is required when marshalling with JiBX mappings that utilize {@link IdMapper}.
 *
 * @see IdMapper
 */
public class IdMappingMarshaller implements Marshaller, Unmarshaller {

    @SuppressWarnings("deprecation")
    private JibxMarshaller jibxMarshaller;

    /**
     * Configure the nested {@link JibxMarshaller}. Required property.
     *
     * @param jibxMarshaller nested marshaller
     */
    @SuppressWarnings("deprecation")
    public void setJibxMarshaller(JibxMarshaller jibxMarshaller) {
        this.jibxMarshaller = jibxMarshaller;
    }

    /**
     * Invokdes {@link JibxMarshaller#marshal JibxMarshaller.marshal()} on the configured
     * {@link JibxMarshaller} within an invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()}.
     */
    @Override
    public void marshal(final Object graph, final Result result) throws IOException {
        IdMappingMarshaller.runIdGenerator(() -> {
            this.jibxMarshaller.marshal(graph, result);
            return null;
        });
    }

    /**
     * Invokdes {@link JibxMarshaller#unmarshal JibxMarshaller.unmarshal()} on the configured
     * {@link JibxMarshaller} within an invocation of {@link IdGenerator#run(Supplier) IdGenerator.run()}.
     */
    @Override
    public Object unmarshal(final Source source) throws IOException {
        return IdMappingMarshaller.runIdGenerator(() -> this.jibxMarshaller.unmarshal(source));
    }

    @Override
    public boolean supports(Class<?> type) {
        return this.jibxMarshaller.supports(type);
    }

// Exception wrangling

    private static <R> R runIdGenerator(JiBXSupplier<R> action) throws IOException {
        if (action == null)
            throw new IllegalArgumentException("null action");
        try {
            return IdGenerator.run(() -> {
                try {
                    return action.get();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            throw e;
        }
    };

    @FunctionalInterface
    private interface JiBXSupplier<R> {
        R get() throws IOException;
    }
}
