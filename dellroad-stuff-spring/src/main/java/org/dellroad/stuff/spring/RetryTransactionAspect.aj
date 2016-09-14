
/*
 * Copyright (C) 2011 Archie L. Cobbs and other authors. All rights reserved.
 */

package org.dellroad.stuff.spring;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

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

        // Do nothing unless this method is about to create a new transaction
        switch (transactionAttribute.getPropagationBehavior()) {
        case TransactionDefinition.PROPAGATION_REQUIRES_NEW:
            break;
        case TransactionDefinition.PROPAGATION_REQUIRED:
            if (TransactionSynchronizationManager.isActualTransactionActive() && this.getAttemptNumber(transactionManagerName) > 0) {
                if (this.log.isTraceEnabled())
                    this.log.trace("skipping retry logic; transaction already open in @Transactional method {}", method);
                return proceed(txObject);
            }
            break;
        default:
            return proceed(txObject);
        }

        // Find @RetryTransaction annotation
        RetryTransaction retryTransaction = AnnotationUtils.getAnnotation(method, RetryTransaction.class);
        if (retryTransaction == null)
            retryTransaction = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RetryTransaction.class);
        if (retryTransaction == null)
            throw new RuntimeException("internal error: no @RetryTransaction annotation found for method " + method);

        // Get retry parameters, applying defaults if necessary
        final int maxRetries = retryTransaction.maxRetries() != -1 ?
          retryTransaction.maxRetries() : this.maxRetriesDefault;
        final long initialDelay = retryTransaction.initialDelay() != -1 ?
          retryTransaction.initialDelay() : this.initialDelayDefault;
        final long maximumDelay = retryTransaction.maximumDelay() != -1 ?
          retryTransaction.maximumDelay() : this.maximumDelayDefault;

        // Sanity check we are configured
        if (this.persistenceExceptionTranslator == null)
            throw new RuntimeException("@RetryTransaction aspect must be configured before use");

        // Perform attempts
        final RetryInfo retryInfo = new RetryInfo(transactionManagerName);
        TransientDataAccessException transientException;
        do {

            // If this is not the first attempt, sleep for a while
            final int attempt = retryInfo.getAttemptNumber();
            if (retryInfo.getAttemptNumber() > 1) {
                final long delay = this.calculateDelay(attempt, initialDelay, maximumDelay);
                if (this.log.isDebugEnabled())
                    this.log.debug("pausing {}ms before retrying @Transactional method {}", delay, method);
                this.pause(delay);
                if (this.log.isDebugEnabled())
                    this.log.debug("retrying @Transactional method {} (attempt #{})", method, attempt);
            }

            // Make next attempt
            try {

                // Make attempt
                if (this.log.isTraceEnabled())
                    this.log.trace("starting @Transactional method {} (attempt #{})", method, attempt);
                this.retryInfos.get().push(retryInfo);
                final Object result;
                try {
                    result = proceed(txObject);
                } finally {
                    this.retryInfos.get().pop();
                }

                // Success
                if (attempt > 1) {
                    if (this.log.isDebugEnabled())
                        this.log.debug("successfully completed @Transactional method {} on re-try attempt #{}", method, attempt);
                } else {
                    if (this.log.isTraceEnabled())
                        this.log.trace("successfully completed @Transactional method {} on first attempt", method);
                }
                return result;
            } catch (RuntimeException e) {

                // Translate the exception, if not already translated into something recognizable
                DataAccessException translatedException;
                if (e instanceof UncategorizedDataAccessException && e.getCause() instanceof RuntimeException) {
                    translatedException = this.persistenceExceptionTranslator
                      .translateExceptionIfPossible((RuntimeException)e.getCause());
                } else if (e instanceof DataAccessException)
                    translatedException = (DataAccessException)e;               // exception is already translated
                else
                    translatedException = this.persistenceExceptionTranslator.translateExceptionIfPossible(e);
                if (this.log.isDebugEnabled()) {
                    this.log.debug("exception from @Transactional method {} on attempt #{}: {} (translates to {})",
                      method, attempt, e, translatedException != null ? translatedException.getClass().getSimpleName() : null);
                }

                // If it's not a transient exception, re-throw it
                if (!(translatedException instanceof TransientDataAccessException))
                    throw e;

                // Re-throw the transient exception when we've run out of attempts
                transientException = (TransientDataAccessException)translatedException;
            }
        } while (retryInfo.incrementAttemptNumber() <= maxRetries);

        // All attempts failed
        this.log.error("@Transactional method {} failed after {} attempts, giving up!", method, maxRetries);
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
        final int randomRange = Math.max(1, (int)(delay >> 3));
        synchronized (this.random) {
            delay += this.random.nextInt(randomRange) - randomRange / 2;
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
     * Matches the execution of any public method in a type with the @Transactional annotation, or any subtype of a type with the
     * @Transactional annotation.
     */
    private pointcut executionOfPublicMethodInTransactionalType() : execution(public * ((@Transactional *)+).*(..));

    /*
     * Matches the execution of any public method in a type with the @RetryTransaction annotation, or any subtype of a type with the
     * @RetryTransaction annotation.
     */
    private pointcut executionOfPublicMethodInRetryTransactionType() : execution(public * ((@RetryTransaction *)+).*(..));

    /*
     * Matches the execution of any method with the @Transactional annotation.
     */
    private pointcut executionOfTransactionalMethod() : execution(@Transactional * *(..));

    /*
     * Matches the execution of any method with the @RetryTransaction annotation.
     */
    private pointcut executionOfRetryTransactionMethod() : execution(@RetryTransaction * *(..));

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

