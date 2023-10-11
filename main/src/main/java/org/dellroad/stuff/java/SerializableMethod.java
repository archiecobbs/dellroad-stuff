
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.java;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Wrapper that makes a {@link Method} {@link Serializable}.
 */
public class SerializableMethod implements Serializable {

    private static final long serialVersionUID = 3761038535500978361L;

    private transient Method method;

    /**
     * Constructor.
     *
     * @param method the method
     * @throws IllegalArgumentException if {@code method} is null
     */
    public SerializableMethod(Method method) {
        if (method == null)
            throw new IllegalArgumentException("null method");
        this.method = method;
    }

    /**
     * Get the wrapped method.
     *
     * @return wrapped method
     */
    public Method getMethod() {
        return this.method;
    }

// Object

    @Override
    public String toString() {
        return this.method.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final SerializableMethod that = (SerializableMethod)obj;
        return this.method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return this.method.hashCode();
    }

// Serialization

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(method.getDeclaringClass());
        out.writeUTF(method.getName());
        out.writeObject(method.getParameterTypes());
    }

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        final Class<?> cl = (Class<?>)input.readObject();
        final String name = input.readUTF();
        final Class<?>[] ptypes = (Class<?>[])input.readObject();
        try {
            this.method = cl.getMethod(name, ptypes);
        } catch (Exception e) {
            throw new IOException("can't find method " + cl.getName() + "." + name + "()");
        }
    }
}
