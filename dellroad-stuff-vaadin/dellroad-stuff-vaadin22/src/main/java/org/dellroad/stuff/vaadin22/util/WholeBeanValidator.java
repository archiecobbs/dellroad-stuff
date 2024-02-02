
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.util;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.validator.BeanValidator;

import java.lang.annotation.ElementType;
import java.util.Locale;
import java.util.Optional;

import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.TraversableResolver;
import javax.validation.Valid;
import javax.validation.ValidatorFactory;
import javax.validation.groups.Default;

/**
 * Applies JSR 303 bean validation constraints that are attached to the bean as a whole (not to an individual property).
 *
 * <p>
 * The {@link BeanValidator} class validates individual bean properties but does not handle "whole bean" validation
 * constraints. This class can be used to cover that gap. This validator does <i>not</i> recurse on the properties
 * of the bean (for that, annotate those properties with {@link Valid &#64;Valid} and rely on {@link BeanValidator}
 * per-property validation).
 *
 * <p>
 * This is a bean-level validator, so any {@link Binder} using this validator must have an actual bean bound to it to validate
 * (via {@link Binder#setBean Binder.setBean()}), otherwise you'll get an {@link IllegalStateException} with
 * <i>bean level validators have been configured but no bean is currently set</i>.
 */
@SuppressWarnings("serial")
public class WholeBeanValidator implements Validator<Object> {

    /**
     * Bean type.
     */
    protected final Class<?> beanType;

    private Class<?>[] groups;
    private ValidatorFactory validatorFactory;

// Constructor

    /**
     * Constructor.
     *
     * @param beanType bean type
     * @throws NullPointerException if {@code beanType} is null
     */
    public WholeBeanValidator(Class<?> beanType) {
        this.beanType = beanType;
    }

// Methods

    /**
     * Configure an alternate set of validation group(s) which will get passed to
     * {@link javax.validation.Validator#validate(Object, Class[]) Validator.validate()}.
     *
     * <p>
     * The default is an empty array, i.e., {@link Default}.
     *
     * @param groups group(s) to validate, or null for {@link Default}
     */
    public void setValidationGroups(Class<?>[] groups) {
        this.groups = groups != null ? groups : new Class<?>[0];
    }

    /**
     * Configure a custom {@link ValidatorFactory}.
     *
     * <p>
     * If this property is left unset, then the factory returned by {@link BeanValidator#getJavaxBeanValidatorFactory} is used.
     *
     * @param factory custom validation factory, or null for none
     */
    public void setValidatorFactory(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
    }

// Validator

    @Override
    public ValidationResult apply(Object bean, ValueContext valueContext) {

        // Build groups array
        final Class<?>[] groupsArray = this.groups != null ? this.groups : new Class<?>[0];

        // Get locale from context
        final Locale locale = valueContext.getLocale().orElse(Locale.getDefault());

        // Build a Validator that won't recurse into bean properties, apply validator, and convert to ValidationResult
        final WorkaroundBeanValidator beanValidator = new WorkaroundBeanValidator(this.beanType, this.validatorFactory);
        return beanValidator.getValidatorFactory()
          .usingContext()
          .traversableResolver(new NeverTraversableResolver())
          .getValidator()
          .validate(bean, groupsArray)
          .stream()
          .map(violation -> ValidationResult.error(beanValidator.getMessage(violation, locale)))
          .findFirst()
          .orElseGet(ValidationResult::ok);
    }

// NeverTraversableResolver

    private static class NeverTraversableResolver implements TraversableResolver {

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty,
          Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
            return false;
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty,
          Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
            return false;
        }
    }

// WorkaroundBeanValidator

    // Workaround for Vaadin methods being protected for no good reason
    static class WorkaroundBeanValidator extends BeanValidator {

        private final ValidatorFactory validatorFactory;

        WorkaroundBeanValidator(Class<?> beanType, ValidatorFactory validatorFactory) {
            super(beanType, "ignored");
            this.validatorFactory = validatorFactory;
        }

        public ValidatorFactory getValidatorFactory() {
            return Optional.ofNullable(this.validatorFactory)
              .orElseGet(BeanValidator::getJavaxBeanValidatorFactory);
        }

        @Override
        public javax.validation.Validator getJavaxBeanValidator() {
            return this.getValidatorFactory().getValidator();
        }

        @Override
        public String getMessage(ConstraintViolation<?> violation, Locale locale) {
            return this.getValidatorFactory().getMessageInterpolator().interpolate(
              violation.getMessageTemplate(), this.createContext(violation), locale);
        }
    }
}
