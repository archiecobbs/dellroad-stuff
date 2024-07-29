
/*
 * Copyright (C) 2011 Archie L. Cobbs and other authors. All rights reserved.
 */

package org.dellroad.stuff.spring;

import java.util.function.Supplier;

import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * Interface implemented by the {@code RetryTransactionAspect}, which implements the {@link RetryTransaction} functionality.
 *
 * @see RetryTransaction
 */
public interface RetryTransactionProvider {

    /**
     * Get the configured exception translator.
     *
     * @return exception translator used to determine which transactions are retryable
     */
    PersistenceExceptionTranslator getPersistenceExceptionTranslator();

    /**
     * Set the configured exception translator.
     *
     * @param persistenceExceptionTranslator exception translator used to determine which transactions are retryable
     */
    void setPersistenceExceptionTranslator(PersistenceExceptionTranslator persistenceExceptionTranslator);

    /**
     * Get the aspect-wide default for {@link RetryTransaction#maxRetries}.
     *
     * @return default maximum number of transaction retry attempts
     */
    int getMaxRetriesDefault();

    /**
     * Set the aspect-wide default for {@link RetryTransaction#maxRetries}.
     *
     * @param maxRetriesDefault default maximum number of transaction retry attempts
     */
    void setMaxRetriesDefault(int maxRetriesDefault);

    /**
     * Get the aspect-wide default for {@link RetryTransaction#initialDelay}.
     *
     * @return default initial delay between retry attempts in milliseconds
     */
    long getInitialDelayDefault();

    /**
     * Set the aspect-wide default for {@link RetryTransaction#initialDelay}.
     *
     * @param initialDelayDefault default initial delay between retry attempts in milliseconds
     */
    void setInitialDelayDefault(long initialDelayDefault);

    /**
     * Get the aspect-wide default for {@link RetryTransaction#maximumDelay}.
     *
     * @return default maximum delay between retry attempts in milliseconds
     */
    long getMaximumDelayDefault();

    /**
     * Set the aspect-wide default for {@link RetryTransaction#maximumDelay}.
     *
     * @param maximumDelayDefault default maximum delay between retry attempts in milliseconds
     */
    void setMaximumDelayDefault(long maximumDelayDefault);

    /**
     * Get the current transaction attempt number in the inner-most active transaction.
     *
     * <p>
     * Equivalent to {@code getAttemptNumber(null)}.
     *
     * @return transaction attempt number, or zero if the aspect is not active in the current thread
     */
    int getAttemptNumber();

    /**
     * Get the current transaction attempt number for the specified transaction manager.
     *
     * @param transactionManagerName transaction manager name, or null to match all
     * @return transaction attempt number, or zero if the aspect is not active in the current thread for the transaction manager
     */
    int getAttemptNumber(String transactionManagerName);

    /**
     * Perform a transaction, retrying as necessary.
     *
     * <p>
     * This method provides a way to apply retry logic directly without going through a method woven with the aspect.
     *
     * @param setup retryable transaction setup
     * @param <T> transaction return type
     * @return result from transaction
     * @throws IllegalArgumentException if {@code setup} is null
     */
    <T> T retry(RetrySetup<T> setup);

// RetrySetup

    /**
     * Holds the configuration information that is required when applying retry logic.
     *
     * <p>
     * Instances are immutable.
     *
     * @param <T> transaction return type
     * @see RetryTransactionProvider#retry RetryTransactionProvider.retry()
     */
    class RetrySetup<T> {

        private final String transactionManagerName;
        private final String description;
        private final Supplier<T> transaction;
        private final int maxRetries;
        private final long initialDelay;
        private final long maximumDelay;

        /**
         * Constructor that uses the aspect default values for maximum retries, initial delay, and maximum delay.
         *
         * @param transactionManagerName the name of the associated transaction manager, if any, otherwise null
         * @param description description of {@code transaction} for logging purposes
         * @param transaction the transaction that should be invoked and retried as needed
         */
        public RetrySetup(String transactionManagerName, String description, Supplier<T> transaction) {
            this(transactionManagerName, description, transaction, -1, -1, -1);
        }

        /**
         * Constructor that uses the values from a {@link RetryTransaction &#64;RetryTransaction} annotation, if not null,
         * for maximum retries, initial delay, and maximum delay, otherwise falling back to the aspect defaults.
         *
         * @param transactionManagerName the name of the associated transaction manager, if any, otherwise null
         * @param description description of {@code transaction} for logging purposes
         * @param transaction the transaction that should be invoked and retried as needed
         * @param annotation {@link RetryTransaction &#64;RetryTransaction} annotation, or null to use aspect defaults
         */
        public RetrySetup(String transactionManagerName, String description, Supplier<T> transaction, RetryTransaction annotation) {
            this(transactionManagerName, description, transaction,
              annotation != null ? annotation.maxRetries() : -1,
              annotation != null ? annotation.initialDelay() : -1,
              annotation != null ? annotation.maximumDelay() : -1);
        }

        /**
         * Primary constructor.
         *
         * @param transactionManagerName the name of the associated transaction manager, if any, otherwise null
         * @param description description of {@code transaction} for logging purposes
         * @param transaction the transaction that should be invoked and retried as needed
         * @param maxRetries maximum number of transaction retry attempts, or -1 to use the aspect default
         * @param initialDelay initial delay between retry attempts in milliseconds, or -1 to use the aspect default
         * @param maximumDelay maximum delay between retry attempts in milliseconds, or -1 to use the aspect default
         */
        public RetrySetup(String transactionManagerName, String description,
          Supplier<T> transaction, int maxRetries, long initialDelay, long maximumDelay) {
            if (description == null)
                throw new IllegalArgumentException("null description");
            if (transaction == null)
                throw new IllegalArgumentException("null transaction");
            this.transactionManagerName = transactionManagerName;
            this.description = description;
            this.transaction = transaction;
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
            this.maximumDelay = maximumDelay;
        }

        /**
         * Get name of the associated transaction manager, if any.
         *
         * <p>
         * This value is used by the woven aspect to avoid redundant nested retries within an already-open transaction.
         *
         * @return transaction manager name, or null to be invisible to redundancy checks
         */
        public String getTransactionManagerName() {
            return this.transactionManagerName;
        }

        /**
         * Get a description of the transaction for logging purposes.
         *
         * @return transaction description
         */
        public String getDescription() {
            return this.description;
        }

        /**
         * Get the transaction to perform and possibly retry.
         *
         * @return transaction
         */
        public Supplier<T> getTransaction() {
            return this.transaction;
        }

        /**
         * Get the maximum number of transaction retry attempts.
         *
         * @return maximum number of retries, or -1 to use the aspect default
         */
        public int getMaxRetries() {
            return this.maxRetries;
        }

        /**
         * Get the initial delay between retry attempts in milliseconds.
         *
         * @return initial delay between retry attempts, or -1 to use the aspect default
         */
        public long getInitialDelay() {
            return this.initialDelay;
        }

        /**
         * Get the maximum delay between retry attempts in milliseconds.
         *
         * @return maximum delay between retry attempts, or -1 to use the aspect default
         */
        public long getMaximumDelay() {
            return this.maximumDelay;
        }
    }
}
