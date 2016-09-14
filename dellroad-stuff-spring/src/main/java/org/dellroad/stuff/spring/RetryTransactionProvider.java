
/*
 * Copyright (C) 2011 Archie L. Cobbs and other authors. All rights reserved.
 */

package org.dellroad.stuff.spring;

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
     * Get the aspect-wide default for {@link RetryTransaction#maxRetries}.
     *
     * @return defatul maximum number of transaction retry attempts
     */
    int getMaxRetriesDefault();

    /**
     * Get the aspect-wide default for {@link RetryTransaction#initialDelay}.
     *
     * @return default initial delay between retry attempts in milliseconds
     */
    long getInitialDelayDefault();

    /**
     * Get the aspect-wide default for {@link RetryTransaction#maximumDelay}.
     *
     * @return default maximum delay between retry attempts in milliseconds
     */
    long getMaximumDelayDefault();

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
}

