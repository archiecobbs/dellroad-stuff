
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.dellroad.stuff.java.ThreadLocalHolder;

/**
 * Provides additional context for {@link javax.validation.ConstraintValidator} implementations.
 *
 * <p>
 * {@link ValidationContext} gives {@link javax.validation.ConstraintValidator} implementations access to the root object
 * being validated. This breaks the usual principle of locality for validation (i.e., that validation of a specific bean
 * proceeds unaware of that bean's parents) but it can make custom validators more convenient to implement.
 * Subclasses are encouraged to provide additional application-specific information.
 *
 * <p>
 * Validation must be performed via {@link #validate(Validator) validate()} for this class to work.
 *
 * @param <T> type of the root object being validated
 */
public class ValidationContext<T> {

    private static final ThreadLocalHolder<ValidationContext<?>> CURRENT = new ThreadLocalHolder<ValidationContext<?>>();

    private final HashMap<String, Set<Object>> uniqueDomainMap = new HashMap<String, Set<Object>>();
    private final T root;
    private final Class<?>[] groups;

    /**
     * Construct a new validation context configured to validate the given root object, using the given validation group(s).
     *
     * @param root root object to be validated
     * @param groups group(s) targeted for validation (if empty, defaults to {@link javax.validation.groups.Default})
     * @throws IllegalArgumentException if either paramter is null
     */
    public ValidationContext(T root, Class<?>... groups) {
        if (root == null)
            throw new IllegalArgumentException("null root");
        if (groups == null)
            throw new IllegalArgumentException("null groups");
        this.root = root;
        this.groups = groups;
    }

    /**
     * Get the root object associated with this instance.
     *
     * @return root of validation object graph
     */
    public final T getRoot() {
        return this.root;
    }

    /**
     * Get the validation groups associated with this instance.
     *
     * @return configured validation groups
     */
    public final Class<?>[] getGroups() {
        return this.groups.clone();
    }

    /**
     * Validate this instance's root object. This is a convenience method, equivalent to:
     *  <blockquote>
     *  <code>{@link #validate(Validator) validate}(Validation.buildDefaultValidatorFactory().getValidator())</code>
     *  </blockquote>
     *
     * @return zero or more violations
     * @throws IllegalStateException if this method is invoked re-entrantly
     */
    public Set<ConstraintViolation<T>> validate() {
        return this.validate(Validation.buildDefaultValidatorFactory().getValidator());
    }

    /**
     * Validate this instance's root object using the given {@link Validator}, making the context
     * available to the current thread during the validation process via {@link #getCurrentContext}.
     *
     * @param validator the validator
     * @return zero or more violations
     * @throws IllegalStateException if this method is invoked re-entrantly
     */
    public Set<ConstraintViolation<T>> validate(final Validator validator) {

        // Sanity check
        if (ValidationContext.CURRENT.get() != null)
            throw new IllegalStateException("re-entrant invocation is not allowed");

        // Validate
        try {
            return ValidationContext.CURRENT.invoke(this, new Callable<Set<ConstraintViolation<T>>>() {
                @Override
                public Set<ConstraintViolation<T>> call() {
                    return validator.validate(ValidationContext.this.root, ValidationContext.this.groups);
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the {@link ValidationContext} associated with the current thread.
     * This method is only valid during invocations of {@link #validate(Validator) validate()}.
     *
     * @return current {@link ValidationContext}
     * @throws IllegalStateException if {@link #validate(Validator) validate()} is not currently executing
     */
    public static ValidationContext<?> getCurrentContext() {
        ValidationContext<?> context = ValidationContext.CURRENT.get();
        if (context == null)
            throw new IllegalStateException("current thread is not executing validate()");
        return context;
    }

    /**
     * Get the {@link ValidationContext} associated with the current thread, cast to the desired type.
     * This method is only valid during invocations of {@link #validate(Validator) validate()}.
     *
     * @param type required type
     * @param <V> root validation object type
     * @return current {@link ValidationContext}
     * @throws IllegalStateException if {@link #validate(Validator) validate()} is not currently executing
     * @throws ClassCastException if the current {@link ValidationContext} is not of type {@code type}
     * @throws NullPointerException if {@code type} is null
     */
    public static <V extends ValidationContext<?>> V getCurrentContext(Class<V> type) {
        return type.cast(ValidationContext.getCurrentContext());
    }

    /**
     * Convenience method to get the root object being validated by the current thread.
     * This method is only valid during invocations of {@link #validate(Validator) validate()}.
     *
     * @return current validation root object
     * @throws IllegalStateException if {@link #validate(Validator) validate()} is not currently executing
     */
    public static Object getCurrentRoot() {
        return ValidationContext.getCurrentContext().getRoot();
    }

    /**
     * Convenience method to get the root object being validated by the current thread, cast to the desired type.
     * This method is only valid during invocations of {@link #validate(Validator) validate()}.
     *
     * @param type required type
     * @param <T> root validation object type
     * @return current validation root object
     * @throws IllegalStateException if {@link #validate(Validator) validate()} is not currently executing
     * @throws ClassCastException if the current validation root is not of type {@code type}
     * @throws NullPointerException if {@code type} is null
     */
    public static <T> T getCurrentRoot(Class<T> type) {
        return type.cast(ValidationContext.getCurrentRoot());
    }

    /**
     * Get the uniqueness domain with the given name. Used to validate {@link Unique @Unique} constraints.
     *
     * @param domain name of the uniqueness domain
     * @return set containing the unique values that have already been seen during this validation check
     */
    public Set<Object> getUniqueDomain(String domain) {
        Set<Object> uniqueDomain = this.uniqueDomainMap.get(domain);
        if (uniqueDomain == null) {
            uniqueDomain = new HashSet<Object>();
            this.uniqueDomainMap.put(domain, uniqueDomain);
        }
        return uniqueDomain;
    }
}

