
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin23.data;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.PropertyDefinition;
import com.vaadin.flow.data.binder.PropertySet;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.function.ValueProvider;

import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.dellroad.stuff.java.Primitive;
import org.dellroad.stuff.java.ReflectUtil;

/**
 * Straightforward implementation of {@link PropertySet} using caller-supplied getters and setters.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * This class is useful for building arbitrary property sets, e.g., see {@link MapPropertySet}.
 *
 * <p>
 * It's also useful when you need to detect Java bean properties defined by default interface methods.
 * Due to <a href="https://bugs.openjdk.org/browse/JDK-8071693">JDK-8071693</a>, Vaadin's {@link Binder}
 * fails to detect such bean properties. To work around that bug, you can do something like this:
 *
 * <pre><code class="language-java">
 *  public class MyBinder&lt;T&gt; extends Binder&lt;T&gt; {
 *
 *      public MyBinder(Class&lt;T&gt; beanType) {
 *          super(MyBinder.buildPropertySet(beanType), false);
 *      }
 *
 *     // Here we use Spring's BeanUtil to avoid JDK-8071693, which Vaadin's Binder suffers from
 *     private static &lt;T&gt; PropertySet&lt;T&gt; buildPropertySet(Class&lt;T&gt; beanType) {
 *         final SimplePropertySet&lt;T&gt; propertySet = new SimplePropertySet&lt;&gt;(beanType);
 *         Stream.of(BeanUtils.getPropertyDescriptors(beanType))
 *           .filter(pd -&gt; !(pd instanceof IndexedPropertyDescriptor))
 *           .filter(pd -&gt; pd.getReadMethod() != null)
 *           .filter(pd -&gt; pd.getWriteMethod() != null)
 *           .forEach(propertySet::addPropertyDefinition);
 *         return propertySet;
 *     }
 *  }
 * </code></pre>
 *
 * <p>
 * This class allows you to recover the original {@linkplain Definition property definition} from
 * a {@link Binder.Binding} instance.
 *
 * <p>
 * Does not support sub-properties.
 *
 * @param <T> underlying target type
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8071693">JDK-8071693</a>
 */
@SuppressWarnings("serial")
public class SimplePropertySet<T> implements PropertySet<T> {

    private final Map<String, Definition<?>> propertyMap = new LinkedHashMap<>();
    private final Class<T> targetType;

    /**
     * Constructor.
     *
     * @param targetType object type that contains property values
     * @throws IllegalArgumentException if {@code targetType} is null
     */
    public SimplePropertySet(Class<T> targetType) {
        if (targetType == null)
            throw new IllegalArgumentException("null targetType");
        this.targetType = targetType;
    }

// PropertySet

    @Override
    public Stream<PropertyDefinition<T, ?>> getProperties() {
        return this.propertyMap.values().stream().map(x -> x);
    }

    @Override
    public Optional<PropertyDefinition<T, ?>> getProperty(String name) {
        return Optional.ofNullable(this.propertyMap.get(name));
    }

// Methods

    /**
     * Get the target object type associated with this instance.
     *
     * <p>
     * The target object stores the actual property values.
     *
     * @return target object type
     */
    public Class<T> getTargetType() {
        return this.targetType;
    }

    /**
     * Add a new property to this instance.
     *
     * @param name property name
     * @param type property type
     * @param caption property caption
     * @param getter getter method
     * @param setter setter method, or null for none
     * @return newly created property definition
     * @throws IllegalArgumentException if any parameter other than {@code setter} is null
     * @throws IllegalArgumentException if a property with the same name has already been added
     */
    public <V> Definition<V> addPropertyDefinition(String name, Class<V> type,
      String caption, ValueProvider<? super T, ? extends V> getter, Setter<? super T, ? super V> setter) {
        final Definition<V> newDefinition = new Definition<V>(name, type, caption, getter, setter);
        final Definition<?> oldDefinition = this.propertyMap.putIfAbsent(name, newDefinition);
        if (oldDefinition != null)
            throw new IllegalArgumentException("duplicate name");
        return newDefinition;
    }

    /**
     * Add a new property to this instance corresponding to the given Java bean {@link PropertyDescriptor}.
     *
     * <p>
     * The caller is responsible for ensuring that {@link propertyDescriptor} is compatible with the target object type.
     *
     * @param propertyDescriptor property descriptor
     * @return newly created property definition
     * @throws IllegalArgumentException if {@code propertyDescriptor} is null
     * @throws IllegalArgumentException if {@code propertyDescriptor} is an {@link IndexedPropertyDescriptor}
     * @throws IllegalArgumentException if {@code propertyDescriptor} has no getter method
     * @throws IllegalArgumentException if a property with the same name has already been added
     */
    public Definition<?> addPropertyDefinition(PropertyDescriptor propertyDescriptor) {
        if (propertyDescriptor == null)
            throw new IllegalArgumentException("null propertyDescriptor");
        if (propertyDescriptor instanceof IndexedPropertyDescriptor)
            throw new IllegalArgumentException(IndexedPropertyDescriptor.class + " unsupported");
        return this.addPropertyDefinition(propertyDescriptor.getName(), propertyDescriptor.getPropertyType(),
          propertyDescriptor.getDisplayName(), propertyDescriptor.getReadMethod(), propertyDescriptor.getWriteMethod());
    }

    // This method exists solely to bind the generic type
    private <V> Definition<V> addPropertyDefinition(String name, Class<V> type,
      String caption, Method getter, Method setter) {
        if (getter == null)
            throw new IllegalArgumentException("null getter");
        return this.addPropertyDefinition(name, type, caption,
          target -> Primitive.wrap(type).cast(ReflectUtil.invoke(getter, target)),
          setter != null ? (target, value) -> ReflectUtil.invoke(setter, target, value) : null);
    }

    /**
     * Recover the {@link SimplePropertySet.Definition} from the given binding, assuming the associated {@link Binder}
     * was created using a {@link SimplePropertySet}.
     *
     * @param binding {@link Binder} binding
     * @return binding's associated property definition
     * @throws IllegalArgumentException if the associated {@link Binder} does not use a {@link SimplePropertySet}
     * @throws IllegalArgumentException if {@code binding} is null
     */
    public static SimplePropertySet<?>.Definition<?> propertyDefinitionForBinding(Binder.Binding<?, ?> binding) {
        if (binding == null)
            throw new IllegalArgumentException("null binding");
        return Optional.ofNullable(binding.getGetter())
          .filter(SimplePropertySet.Definition.Getter.class::isInstance)
          .map(SimplePropertySet.Definition.Getter.class::cast)
          .map(SimplePropertySet<?>.Definition<?>.Getter::getDefinition)
          .orElseThrow(() -> new IllegalArgumentException("binding's binder does not use SimplePropertySet"));
    }

    /**
     * Create a new {@link Definition}.
     *
     * <p>
     * The implementation in {@link SimplePropertySet} just invokes the {@link Definition} constructor directly.
     *
     * @param name property name
     * @param type property type
     * @param caption property caption
     * @param getter getter method
     * @param setter setter method, or null for none
     * @return newly created property definition
     * @throws IllegalArgumentException if any parameter other than {@code setter} is null
     */
    protected <V> Definition<V> createDefinition(String name, Class<V> type,
      String caption, ValueProvider<? super T, ? extends V> getter, Setter<? super T, ? super V> setter) {
        return new Definition<V>(name, type, caption, getter, setter);
    }

// Definition

    /**
     * A {@link PropertyDefinition} within a {@link SimplePropertySet}.
     *
     * <p>
     * Instances provide {@link Getter}s that allow recovery of the originating instance; see
     * {@link SimplePropertySet#propertyDefinitionForBinding SimplePropertySet.propertyDefinitionForBinding()}.
     *
     * @param <V> property value type
     */
    @SuppressWarnings("serial")
    public class Definition<V> implements PropertyDefinition<T, V> {

        private final String name;
        private final Class<V> type;
        private final String caption;
        private final ValueProvider<? super T, ? extends V> getter;
        private final Setter<? super T, ? super V> setter;

        /**
         * Constructor.
         *
         * @param name property name
         * @param type property type
         * @param caption property caption
         * @param getter getter method
         * @param setter setter method, or null for none
         * @throws IllegalArgumentException if any parameter other than {@code setter} is null
         */
        public Definition(String name, Class<V> type, String caption,
          ValueProvider<? super T, ? extends V> getter, Setter<? super T, ? super V> setter) {
            if (name == null)
                throw new IllegalArgumentException("null name");
            if (type == null)
                throw new IllegalArgumentException("null type");
            if (caption == null)
                throw new IllegalArgumentException("null caption");
            if (getter == null)
                throw new IllegalArgumentException("null getter");
            this.name = name;
            this.type = type;
            this.caption = caption;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String getCaption() {
            return this.caption;
        }

        @Override
        public Getter getGetter() {
            return new Getter();
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public PropertyDefinition<T, ?> getParent() {
            return null;
        }

        @Override
        public Class<T> getPropertyHolderType() {
            return SimplePropertySet.this.targetType;
        }

        @Override
        public PropertySet<T> getPropertySet() {
            return SimplePropertySet.this;
        }

        @Override
        public Optional<Setter<T, V>> getSetter() {
            return Optional.ofNullable(this.setter).map(s -> s::accept);
        }

        @Override
        public Class<V> getType() {
            return this.type;
        }

    // Getter

        // We use a wrapper class here so propertyDefinitionForBinding() can work
        @SuppressWarnings("serial")
        public class Getter implements ValueProvider<T, V> {

            public Definition<V> getDefinition() {
                return Definition.this;
            }

            @Override
            public V apply(T target) {
                if (target == null)
                    throw new IllegalArgumentException("null target");
                return Definition.this.getter.apply(target);
            }
        }
    }
}
