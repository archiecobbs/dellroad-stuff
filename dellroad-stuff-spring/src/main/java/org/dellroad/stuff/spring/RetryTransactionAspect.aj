
/*
 * Copyright (C) 2011 Archie L. Cobbs and other authors. All rights reserved.
 */

package org.dellroad.stuff.spring;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.function.Supplier;

import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An aspect that automatically retries failed but "retryable" transactions.
 *
 * @see RetryTransaction
 */
public aspect RetryTransactionAspect extends AbstractBean implements RetryTransactionProvider {

    private final ThreadLocal<ArrayDeque<RetryInfo>> retryInfos = new ThreadLocal<ArrayDeque<RetryInfo>>() {
        @Override
        public ArrayDeque<RetryInfo> initialValue() {
            return new ArrayDeque<>(2);
        }
    };
    private final Random random = new Random();

    // Default values for retry settings
    private int maxRetriesDefault = RetryTransaction.DEFAULT_MAX_RETRIES;
    private long initialDelayDefault = RetryTransaction.DEFAULT_INITIAL_DELAY;
    private long maximumDelayDefault = RetryTransaction.DEFAULT_MAXIMUM_DELAY;

    // This knows how to read information from @Transactional annotation
    private final AnnotationTransactionAttributeSource transactionAttributeSource = new AnnotationTransactionAttributeSource(false);

    // Tells us what is a "transient" exception; must be explicitly configured
    private PersistenceExceptionTranslator persistenceExceptionTranslator;

// Property accessors and RetryTransactionProvider implementation

    @Override
    public PersistenceExceptionTranslator getPersistenceExceptionTranslator() {
        return this.persistenceExceptionTranslator;
    }
    public void setPersistenceExceptionTranslator(PersistenceExceptionTranslator persistenceExceptionTranslator) {
        this.persistenceExceptionTranslator = persistenceExceptionTranslator;
    }

    @Override
    public int getMaxRetriesDefault() {
        return this.maxRetriesDefault;
    }
    public void setMaxRetriesDefault(int maxRetriesDefault) {
        this.maxRetriesDefault = maxRetriesDefault;
    }

    @Override
    public long getInitialDelayDefault() {
        return this.initialDelayDefault;
    }
    public void setInitialDelayDefault(long initialDelayDefault) {
        this.initialDelayDefault = initialDelayDefault;
    }

    @Override
    public long getMaximumDelayDefault() {
        return this.maximumDelayDefault;
    }
    public void setMaximumDelayDefault(long maximumDelayDefault) {
        this.maximumDelayDefault = maximumDelayDefault;
    }

    @Override
    public int getAttemptNumber() {
        return this.getAttemptNumber(null);
    }

    @Override
    public int getAttemptNumber(String transactionManagerName) {
        final ArrayDeque<RetryInfo> retryInfoStack = this.retryInfos.get();
        for (Iterator<RetryInfo> i = retryInfoStack.descendingIterator(); i.hasNext(); ) {
            final RetryInfo retryInfo = i.next();
            if (transactionManagerName == null || transactionManagerName.equals(retryInfo.getTransactionManagerName()))
                return retryInfo.getAttemptNumber();
        }
        return 0;
    }

// InitializingBean

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (this.persistenceExceptionTranslator == null)
            throw new IllegalArgumentException("no PersistenceExceptionTranslator configured");
    }

// Aspect

    // Ensure that this aspect is woven outside of the AnnotationTransactionAspect but inside of AnnotationAsyncExecutionAspect
    declare precedence :
        org.springframework.scheduling.aspectj.*, RetryTransactionAspect, org.springframework.transaction.aspectj.*;

    /**
     * Attempt to execute a transactional method. If the method throws any exception which is translated
     * to a subclass of TransientDataAccessException, the method will be executed again, with a delay
     * between attempts.
     */
    Object around(final Object txObject) : retryTransactionalMethodExecution(txObject) {

        // Get method info
        final MethodSignature methodSignature = (MethodSignature)thisJoinPoint.getSignature();
        final Method method = methodSignature.getMethod();

        // Get transaction attribute from @Transactional annotation
        final TransactionAttribute transactionAttribute = this.transactionAttributeSource
          .getTransactionAttribute(method, txObject.getClass());
        if (transactionAttribute == null) {
            throw new RuntimeException("no @Transactional annotation found for method "
              + method + "; required for @RetryTransaction");
        }
        final String transactionManagerName = transactionAttribute.getQualifier();
        final String description = "@Transactional method " + method;

        // Do nothing unless this method is about to create a new transaction
        switch (transactionAttribute.getPropagationBehavior()) {
        case TransactionDefinition.PROPAGATION_REQUIRES_NEW:
            break;
        case TransactionDefinition.PROPAGATION_REQUIRED:
            if (TransactionSynchronizationManager.isActualTransactionActive()
              && this.getAttemptNumber(transactionManagerName) > 0) {
                if (this.log.isTraceEnabled()) {
                    this.log.trace("skipping retry logic; transaction already open for {} in {}",
                      transactionManagerName, description);
                }
                return proceed(txObject);
            }
            break;
        default:
            return proceed(txObject);
        }

        // Find @RetryTransaction annotation (there should always be one unless this aspect was extended)
        RetryTransaction retryTransaction = AnnotationUtils.getAnnotation(method, RetryTransaction.class);
        if (retryTransaction == null)
            retryTransaction = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RetryTransaction.class);

        // Setup retry and perform attempt(s)
        return this.retry(new RetrySetup<Object>(transactionManagerName, description, () -> proceed(txObject), retryTransaction));
    }

    /**
     * Perform the specified action (presumably some transactional operation), retrying as necessary.
     *
     * <p>
     * This method provides a way to apply retry logic explicitly without going through an aspect.
     *
     * @param setup retried transaction setup
     * @throws IllegalArgumentException if {@code setup} is null
     */
    public <T> T retry(RetrySetup<T> setup) {

        // Sanity check
        if (setup == null)
            throw new IllegalArgumentException("null setup");

        // Get retry parameters, applying defaults if necessary
        final int maxRetries = setup.getMaxRetries() != -1 ? setup.getMaxRetries() : this.maxRetriesDefault;
        final long initialDelay = setup.getInitialDelay() != -1 ? setup.getInitialDelay() : this.initialDelayDefault;
        final long maximumDelay = setup.getMaximumDelay() != -1 ? setup.getMaximumDelay() : this.maximumDelayDefault;

        // Sanity check we are configured
        if (this.persistenceExceptionTranslator == null) {
            throw new RuntimeException("@RetryTransaction aspect must be configured with a "
              + PersistenceExceptionTranslator.class.getSimpleName() + " before use");
        }

        // Perform attempts
        final RetryInfo retryInfo = new RetryInfo(setup.getTransactionManagerName());
        TransientDataAccessException transientException;
        do {

            // If this is not the first attempt, sleep for a while
            final int attempt = retryInfo.getAttemptNumber();
            if (retryInfo.getAttemptNumber() > 1) {
                final long delay = this.calculateDelay(attempt, initialDelay, maximumDelay);
                if (this.log.isDebugEnabled())
                    this.log.debug("pausing {}ms before retrying {}", delay, setup.getDescription());
                this.pause(delay);
                if (this.log.isDebugEnabled())
                    this.log.debug("retrying {} (attempt #{})", setup.getDescription(), attempt);
            }

            // Make next attempt
            try {

                // Make attempt
                if (this.log.isTraceEnabled())
                    this.log.trace("starting {} (attempt #{})", setup.getDescription(), attempt);
                this.retryInfos.get().push(retryInfo);
                final T result;
                try {
                    result = setup.getTransaction().get();
                } finally {
                    this.retryInfos.get().pop();
                }

                // Success
                if (attempt > 1) {
                    if (this.log.isDebugEnabled())
                        this.log.debug("successfully completed {} on re-try attempt #{}", setup.getDescription(), attempt);
                } else {
                    if (this.log.isTraceEnabled())
                        this.log.trace("successfully completed {} on first attempt", setup.getDescription());
                }
                return result;
            } catch (RuntimeException e) {

                // Translate the exception; if already translated, translate it again in case our translator has another opinion
                DataAccessException translatedException;
                if (e instanceof DataAccessException && e.getCause() instanceof RuntimeException) {
                    translatedException = this.persistenceExceptionTranslator
                      .translateExceptionIfPossible((RuntimeException)e.getCause());
                } else if (e instanceof DataAccessException)
                    translatedException = (DataAccessException)e;               // exception is already translated
                else
                    translatedException = this.persistenceExceptionTranslator.translateExceptionIfPossible(e);
                if (this.log.isDebugEnabled()) {
                    this.log.debug("exception from {} on attempt #{}: {}{}", setup.getDescription(), attempt, e.toString(),
                      translatedException != null ? " (translates to " + translatedException.getClass().getSimpleName() + ")" : "");
                }

                // If it's not a transient exception, re-throw it
                if (!(translatedException instanceof TransientDataAccessException))
                    throw e;

                // Re-throw the transient exception when we've run out of attempts
                transientException = (TransientDataAccessException)translatedException;
            }
        } while (retryInfo.incrementAttemptNumber() <= maxRetries);

        // All attempts failed
        this.log.error("{} failed after {} attempts, giving up!", setup.getDescription(), maxRetries);
        throw transientException;
    }

// Helper methods

    /**
     * Calculate how long to sleep.
     *
     * @param attempt attempt number (one-based)
     * @param initialDelay initial delay
     * @param maximumDelay maximum delay
     */
    protected long calculateDelay(int attempt, long initialDelay, long maximumDelay) {

        // Enforce sanity
        initialDelay = Math.max(initialDelay, 1);
        maximumDelay = Math.max(maximumDelay, 1);
        initialDelay = Math.min(initialDelay, maximumDelay);

        // Calculate nominal delay for this attempt, using exponential back-off; be careful to avoid overflow
        long delay = initialDelay;
        while (attempt-- > 1) {
            delay <<= 1;
            if (delay >= maximumDelay) {
                delay = maximumDelay;
                break;
            }
        }

        // Add in +/- 12.5% randomness
        final int randomRange = Math.max(1, (int)(delay >> 2));         // divide by 4 => 25% total range from -12.5% to +12.5%
        synchronized (this.random) {
            delay += this.random.nextInt(randomRange) - (randomRange / 2);
        }

        // Enforce upper bound again
        delay = Math.min(delay, maximumDelay);

        // Done
        return delay;
    }

    /**
     * Sleep for the specified period.
     *
     * @param delay delay in milliseconds
     */
    protected void pause(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /*
     * Matches the execution of any public method in a type with the @Transactional (meta-)annotation,
     * or any subtype of a type with the @Transactional annotation.
     */
    private pointcut executionOfPublicMethodInTransactionalType() :
       execution(public * ((@Transactional *)+).*(..)) || execution(public * ((@(@Transactional *) *)+).*(..));

    /*
     * Matches the execution of any public method in a type with the @RetryTransaction (meta-)annotation,
     * or any subtype of a type with the @RetryTransaction annotation.
     */
    private pointcut executionOfPublicMethodInRetryTransactionType() :
      execution(public * ((@RetryTransaction *)+).*(..)) || execution(public * ((@(@RetryTransaction *) *)+).*(..));

    /*
     * Matches the execution of any method with the @Transactional (meta-)annotation.
     */
    private pointcut executionOfTransactionalMethod() :
      execution(@Transactional * *(..)) || execution(@(@Transactional *) * *(..));

    /*
     * Matches the execution of any method with the @RetryTransaction (meta-)annotation.
     */
    private pointcut executionOfRetryTransactionMethod() :
      execution(@RetryTransaction * *(..)) || execution(@(@RetryTransaction *) * *(..));

    /*
     * Main pointcut - matched join points have both @Transactional and @RetryTransaction on method and/or type
     */
    private pointcut retryTransactionalMethodExecution(Object txObject) : this(txObject)
        && (executionOfPublicMethodInTransactionalType() || executionOfTransactionalMethod())
        && (executionOfPublicMethodInRetryTransactionType() || executionOfRetryTransactionMethod());

// Info about an active retry in progress

    private static class RetryInfo {

        private final String transactionManagerName;
        private int attemptNumber = 1;

        RetryInfo(String transactionManagerName) {
            this.transactionManagerName = transactionManagerName;
        }

        public String getTransactionManagerName() {
            return this.transactionManagerName;
        }

        public int getAttemptNumber() {
            return this.attemptNumber;
        }

        public int incrementAttemptNumber() {
            return this.attemptNumber++;
        }
    }
}
