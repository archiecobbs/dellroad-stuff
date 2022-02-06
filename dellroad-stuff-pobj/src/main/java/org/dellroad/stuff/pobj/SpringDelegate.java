
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import java.io.IOException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;

/**
 * {@link PersistentObjectDelegate} that uses Spring's {@link Marshaller} and {@link Unmarshaller} interfaces
 * for XML conversion.
 *
 * @param <T> type of the root persistent object
 */
public class SpringDelegate<T> extends AbstractDelegate<T> implements InitializingBean {

    private Marshaller marshaller;
    private Unmarshaller unmarshaller;

    /**
     * Set the {@link Marshaller} used to convert instances to XML. Required property.
     *
     * @param marshaller XML marshaller
     */
    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    /**
     * Set the {@link Marshaller} used to convert instances to XML. Required property.
     *
     * @param unmarshaller XML unmarshaller
     */
    public void setUnmarshaller(Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.marshaller == null)
            throw new Exception("no marshaller configured");
        if (this.unmarshaller == null)
            throw new Exception("no unmarshaller configured");
    }

    @Override
    public void serialize(T obj, Result result) throws IOException {
        try {
            this.marshaller.marshal(obj, result);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new PersistentObjectException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(Source source) throws IOException {
        try {
            return (T)this.unmarshaller.unmarshal(source);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new PersistentObjectException(e);
        }
    }
}

