
/*
 * Copyright (C) 2012 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.stuff.pobj;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.dellroad.stuff.java.ThreadLocalHolder;
import org.dellroad.stuff.spring.AbstractBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * "Disruptor" style transaction manager for {@link PersistentObject} databases.
 *
 * <h3>Overview</h3>
 *
 * <p>
 * Instances of this class maintain a dedicated thread for performing transactions on a specific {@link PersistentObject}
 * database. The {@link #performTransaction performTransaction()} and {@link #scheduleTransaction scheduleTransaction()}
 * methods schedule synchronous and asynchronous transactions (respectively) to be executed by this thread.
 * </p>
 *
 * <p>
 * By performing all transactions on the same thread, the {@link PersistentObject} root object is stays in the cache
 * of a single CPU core, improving performance ("Disruptor" style). The single threading also guarantees transaction
 * serialization, atomicity, and isolation. The capacity of the queue that holds upcoming transactions can be configured
 * via {@link #setQueueCapacity setQueueCapacity()} (default {@link Integer#MAX_VALUE}). When the queue is full,
 * scheduling a new transaction blocks until space is available.
 * </p>
 *
 * <p>
 * Within a transaction, {@link #getRoot} is used to access that transaction's {@link PersistentObject} root object.
 * The returned object graph is private to that thread's transaction (except in shared mode; see below).
 * </p>
 *
 * <h3>Transaction Types</h3>
 *
 * <p>
 * Transactions may be read-only or read-write. Read-only transactions may optionally be configured to access a
 * {@linkplain PersistentObject#getSharedRoot shared root}, in which case all read-only transactions share the same
 * object graph (so no per-transation copies are required), but these transactions must also be careful not to modify
 * the object graph returned by {@link #getRoot}. For read-write transactions, any modifications to the
 * object graph returned by {@link #getRoot} will be persisted when the transaction completes (assuming it validates).
 * In either case, references to the object graph returned by {@link #getRoot} may be returned to callers outside of the
 * transaction, who may then safely access them with the assurance that they won't be modified by another thread (although in the
 * {@linkplain PersistentObject#getSharedRoot shared root} case, modifications must still be avoided). Of course, once
 * the transaction ends the transaction's object graph is no longer guaranteed to be up-to-date.
 * </p>
 *
 * <p>
 * Normally all changes to the associated {@link PersistentObject} database are being handled via this class: if not, and
 * another thread updates the {@link PersistentObject} while a transaction is being handled,
 * a {@link PersistentObjectVersionException} can result (the transaction may then be retried if desired).
 * </p>
 *
 * <h3>Transaction Reentrancy</h3>
 *
 * <p>
 * Transactions are reentrant, so that if {@link #performTransaction performTransaction()} is invoked by a thread while
 * executing within an existing transaction, no new transaction is created; instead, execution proceeds in the context of
 * the existing transaction. However, if the second transaction is not compatible with the first (e.g., a read-write
 * transaction inside a read-only one), an exception is thrown.
 * </p>
 *
 * <h3>Multiple Transactions</h3>
 *
 * <p>
 * A thread executing within one {@link PersistentObjectTransactionManager}'s transaction may safely invoke
 * {@link #performTransaction performTransaction()} on another {@link PersistentObjectTransactionManager}; however,
 * the first transaction will block while the second executes, and callers must be careful to always nest these
 * transactions in the same order to avoid possible deadlock.
 * </p>
 *
 * <p>
 * To allow distingushing among multiple {@link PersistentObjectTransactionManager} instances that may have open transactions
 * at the same time, instances may be given unique {@linkplain #getName names} (names are automatically inherited from the
 * {@linkplain #setBeanName bean name} when instances are declared in a Spring bean factory). Within an open transaction,
 * the associated {@link PersistentObjectTransactionManager} can be accessed by name via {@link #getCurrent(String)}.
 * As a convenience, in the common case of there only being one open transaction in the current thread, the
 * {@link #getCurrent()} variant works; dealing with names is not necessary when there is only one instance in play.
 * </p>
 *
 * <h3>Annotation Usage</h3>
 *
 * <p>
 * The easiest way to use this class is to annotate transactional methods with the
 * {@link PersistentObjectTransactional &#64;PersistentObjectTransactional} annotation and apply the associated AOP aspect,
 * and then use {@link #getCurrent()} within the transaction to access the {@link PersistentObjectTransactionManager} and
 * the associated transaction root object. For example:
 * <pre>
 *  <b>&#64;PersistentObjectTransactional</b>
 *  public User loginUser(String username) throws LoginException {
 *      final MyDatabase database = <b>PersistentObjectTransactionManager.&lt;MyDatabase&gt;getCurrent().getRoot()</b>;
 *      for (User user : database.getUsers()) {
 *          if (user.getUsername().equals(username)) {
 *              if (user.getLoginCount() &gt;= MAX_CONCURRENT_LOGINS)
 *                  throw new LoginException("too many logins");
 *              user.setLoginCount(user.getLoginCount() + 1);
 *              return user;
 *          }
 *      }
 *      throw new LoginException("user not found");
 *  }
 * </pre>
 * Don't forget to configure the {@link PersistentObjectTransactional &#64;PersistentObjectTransactional} AOP aspect
 * via Spring as described in {@link PersistentObjectTransactional &#64;PersistentObjectTransactional}.
 * </p>
 *
 * @param <T> root object type
 * @see PersistentObjectTransactional &#64;PersistentObjectTransactional
 */
@Component
public class PersistentObjectTransactionManager<T> extends AbstractBean implements BeanNameAware {

    /**
     * Default transaction queue capacity ({@link Integer#MAX_VALUE}).
     */
    public static final int DEFAULT_QUEUE_CAPACITY = Integer.MAX_VALUE;

    static final ThreadLocal<HashMap<String, PersistentObjectTransactionManager<?>>> ASPECT_MANAGER_MAP
      = new ThreadLocal<HashMap<String, PersistentObjectTransactionManager<?>>>();

    private static final FutureTask<Void> TERMINATE_TASK = new FutureTask<Void>(new Runnable() { public void run() { } }, null);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final ThreadLocalHolder<TxInfo> currentRoot = new ThreadLocalHolder<TxInfo>();

    @Autowired
    @Qualifier("dataPersistentObject")
    private PersistentObject<T> persistentObject;

    private String name = "default";

    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    private volatile LinkedBlockingQueue<FutureTask<?>> queue;
    private volatile WorkerThread workerThread;

// Lifecycle

    /**
     * Set the name of this instance.
     *
     * <p>
     * The implementation in {@link PersistentObjectTransactionManager} invokes {@link #setName}, so that instances'
     * names are automatically inherited from their Spring bean names, unless the name is already set to something else.
     * </p>
     */
    @Override
    public void setBeanName(String beanName) {
        if (this.getName() == null)
            this.setName(beanName);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        // Check name
        if (this.name == null)
            throw new IllegalStateException("no bean name configured");

        // Initialize queue
        this.queue = new LinkedBlockingQueue<FutureTask<?>>(this.getQueueCapacity());

        // Start worker thread
        if (this.workerThread != null)
            throw new IllegalStateException("instance is already started");
        this.workerThread = new WorkerThread();
        this.workerThread.start();
    }

    @Override
    public void destroy() throws Exception {

        // Stop worker thread
        if (this.workerThread == null)
            throw new IllegalStateException("instance is already shutdown");
        this.queue.add(TERMINATE_TASK);

        // Wait for worker thread to finish
        this.workerThread.join();
        this.workerThread = null;

        // Reset queue
        this.queue = null;

        // Done
        super.destroy();
    }

// Public API

    /**
     * Get the configured name of this instance. Default is {@code "default"}.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the configured name of this instance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the configured capacity of the transaction queue. Default is {@link Integer#MAX_VALUE}.
     */
    public int getQueueCapacity() {
        return this.queueCapacity;
    }

    /**
     * Set the capacity of the transaction queue.
     *
     * @throws IllegalArgumentException if {@code queueCapacity} is zero or less
     */
    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity <= 0)
            throw new IllegalArgumentException("queueCapacity <= 0");
        this.queueCapacity = queueCapacity;
    }

    /**
     * Schedule a transaction. The transaction will execute asynchronously.
     *
     * @param action action to perform within the transaction
     * @param readOnly true for a read-only transaction
     * @param shared if {@code readOnly} is true, access the {@linkplain PersistentObject#getSharedRoot shared root}
     *  to avoid a copy operation; in this case, {@code action} must not make any modifications
     * @return a {@link Future} representing the completion of the transaction
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if this instance is not yet {@linkplain #afterPropertiesSet started}
     *  or has been {@linkplain #destroy} shut down
     */
    public <V> Future<V> scheduleTransaction(Callable<V> action, boolean readOnly, boolean shared) {
        return this.scheduleTransaction(new FutureTask<V>(this.getTask(action, readOnly, shared)));
    }

    /**
     * Schedule a transaction. The transaction will execute asynchronously.
     *
     * @param action action to perform within the transaction
     * @param readOnly true for a read-only transaction
     * @param shared if {@code readOnly} is true, access the {@linkplain PersistentObject#getSharedRoot shared root}
     *  to avoid a copy operation; in this case, {@code action} must not make any modifications
     * @return a {@link Future} representing the completion of the transaction
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if this instance is not yet {@linkplain #afterPropertiesSet started}
     *  or has been {@linkplain #destroy} shut down
     */
    public Future<Void> scheduleTransaction(Runnable action, boolean readOnly, boolean shared) {
        return this.scheduleTransaction(new FutureTask<Void>(this.getTask(action, readOnly, shared)));
    }

    /**
     * Schedule a transaction, wait for it to complete, and return the result.
     *
     * <p>
     * If the current thread is already executing within a transaction associated with this instance, then
     * no new transaction is created; instead, the {@code action} executes in the context of the existing
     * transaction.
     * </p>
     *
     * @param action action to perform within the transaction
     * @param readOnly true for a read-only transaction
     * @param shared if {@code readOnly} is true, access the {@linkplain PersistentObject#getSharedRoot shared root}
     *  to avoid a copy operation; in this case, {@code action} must not make any modifications
     * @return result of executing the {@code action}
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if this instance is not yet {@linkplain #afterPropertiesSet started}
     *  or has been {@linkplain #destroy} shut down
     * @throws IllegalStateException if this instance already has a read-only transaction open in the current thread
     *  and {@code readOnly} is false
     * @throws IllegalStateException if this instance already has a shared mode read-only transaction open in the current thread
     *  and {@code shared} is false
     * @throws PersistentObjectValidationException if {@code readOnly} is false and the modified root does not validate
     * @throws PersistentObjectVersionException if {@code readOnly} is false and the database was modified by another thread
     * @throws PersistentObjectException if {@code readOnly} is false and some other error occurs
     * @throws Exception if thrown by {@code action}
     */
    public <V> V performTransaction(Callable<V> action, boolean readOnly, boolean shared) throws Exception {
        if (this.isReentrant(readOnly, shared))
            return action.call();
        return this.performTransaction(this.scheduleTransaction(action, readOnly, shared));
    }

    /**
     * Schedule a transaction and wait for it to complete.
     *
     * @param action action to perform within the transaction
     * @param readOnly true for a read-only transaction
     * @param shared if {@code readOnly} is true, access the {@linkplain PersistentObject#getSharedRoot shared root}
     *  to avoid a copy operation; in this case, {@code action} must not make any modifications
     * @throws IllegalArgumentException if {@code action} is null
     * @throws IllegalStateException if this instance is not yet {@linkplain #afterPropertiesSet started}
     *  or has been {@linkplain #destroy} shut down
     * @throws IllegalStateException if this instance already has a read-only transaction open in the current thread
     *  and {@code readOnly} is false
     * @throws IllegalStateException if this instance already has a shared mode read-only transaction open in the current thread
     *  and {@code shared} is false
     * @throws PersistentObjectValidationException if {@code readOnly} is false and the modified root does not validate
     * @throws PersistentObjectVersionException if {@code readOnly} is false and the database was modified by another thread
     * @throws PersistentObjectException if {@code readOnly} is false and some other error occurs
     */
    public void performTransaction(Runnable action, boolean readOnly, boolean shared) {
        if (this.isReentrant(readOnly, shared)) {
            action.run();
            return;
        }
        this.performTransaction(this.scheduleTransaction(action, readOnly, shared));
    }

    /**
     * Get the instance associated with an open transaction in the current thread and having the given manager name.
     *
     * @param name name assigned to the desired instance
     * @throws IllegalArgumentException if {@code name} is null
     * @throws IllegalStateException if the current thread is not running within a transaction managed by
     *  a {@link PersistentObjectTransactionManager} assigned the given name
     */
    @SuppressWarnings("unchecked")
    public static <T> PersistentObjectTransactionManager<T> getCurrent(String name) {
        if (name == null)
            throw new IllegalArgumentException("null name");
        final HashMap<String, PersistentObjectTransactionManager<?>> managerMap = ASPECT_MANAGER_MAP.get();
        final PersistentObjectTransactionManager<?> manager = managerMap != null ? managerMap.get(name) : null;
        if (manager == null) {
            throw new IllegalStateException("no PersistentObjectTransactionManager named `" + name
              + "' has an open transaction in the current thread");
        }
        return (PersistentObjectTransactionManager<T>)manager;
    }

    /**
     * Get the (unique) instance associated with an open transaction in the current thread.
     *
     * <p>
     * This is a convenience method for the common case where there is only one instance associated with the current thread.
     * </p>
     *
     * @throws IllegalStateException if the current thread is not running within a
     *  {@link PersistentObjectTransactionManager} transaction
     * @throws IllegalStateException there is more than one {@link PersistentObjectTransactionManager} transaction
     *  open in the current thread
     */
    @SuppressWarnings("unchecked")
    public static <T> PersistentObjectTransactionManager<T> getCurrent() {
        final HashMap<String, PersistentObjectTransactionManager<?>> managerMap = ASPECT_MANAGER_MAP.get();
        if (managerMap == null || managerMap.isEmpty()) {
            throw new IllegalStateException("there are no PersistentObjectTransactionManager transactions open"
              + " in the current thread");
        }
        if (managerMap.size() > 1) {
            throw new IllegalStateException("there is more than one PersistentObjectTransactionManager transaction open"
              + " in the current thread");
        }
        return (PersistentObjectTransactionManager<T>)managerMap.values().iterator().next();
    }

    /**
     * Get the {@link PersistentObject} root object in the transaction associated with the current thread.
     *
     * @throws IllegalStateException if the current thread is not running within a transaction
     */
    public T getRoot() {
        return this.currentRoot.require().getSnapshot().getRoot();
    }

    /**
     * Get the {@linkplain PersistentObject#getVersion version} of the root object in the transaction
     * associated with the current thread.
     *
     * @throws IllegalStateException if the current thread is not running within a transaction
     */
    public long getVersion() {
        return this.currentRoot.require().getSnapshot().getVersion();
    }

    /**
     * Determine if the current thread is running within a transaction.
     */
    public boolean isTransaction() {
        return this.currentRoot.get() != null;
    }

    /**
     * Determine if the current thread is running within a transaction and that transaction is read-only.
     */
    public boolean isReadOnlyTransaction() {
        final TxInfo txInfo = this.currentRoot.get();
        return txInfo != null && txInfo.isReadOnly();
    }

    /**
     * Determine if the current thread is running within a transaction and that transaction is a read-only shared root transaction.
     */
    public boolean isReadOnlySharedRootTransaction() {
        final TxInfo txInfo = this.currentRoot.get();
        return txInfo != null && txInfo.isReadOnly() && txInfo.isShared();
    }

// Internal methods

    private boolean isReentrant(boolean readOnly, boolean shared) {

        // Are we already within a transaction?
        final TxInfo txInfo = this.currentRoot.get();
        if (txInfo == null)
            return false;

        // Check for incompatibilities
        if (txInfo.isReadOnly()) {
            if (!readOnly) {
                throw new IllegalStateException("illegal read-write transaction created reentrantly"
                  + " within a read-only transaction");
            }
            if (txInfo.isShared() && !shared) {
                throw new IllegalStateException("illegal non-shared read-only transaction created reentrantly"
                  + " within a shared read-only transaction");
            }
        }
        return true;
    }

    private <V> FutureTask<V> scheduleTransaction(FutureTask<V> task) {

        // Sanity check
        if (task == null)
            throw new IllegalArgumentException("null task");

        // Enqueue transaction
        try {
            this.queue.add(task);
        } catch (NullPointerException e) {
            throw new IllegalStateException("instance not started or has been shutdown");
        }

        // Done
        return task;
    }

    private <V> V performTransaction(Future<V> future) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            this.prependCurrentStackTrace(cause);
            throw PersistentObjectTransactionManager.<RuntimeException>maskException(cause);        // re-throw original exception
        } catch (CancellationException e) {
            throw new RuntimeException("transaction method execution was canceled", e);             // should never happen
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    private <V> Callable<V> getTask(final Callable<V> action, final boolean readOnly, final boolean shared) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                final PersistentObject<T>.Snapshot snapshot = readOnly && shared ?
                  PersistentObjectTransactionManager.this.persistentObject.getSharedRootSnapshot() :
                  PersistentObjectTransactionManager.this.persistentObject.getRootSnapshot();
                final TxInfo txInfo = new TxInfo(snapshot, readOnly, shared);
                return PersistentObjectTransactionManager.this.currentRoot.invoke(txInfo, new Callable<V>() {
                    @Override
                    public V call() throws Exception {
                        return PersistentObjectTransactionManager.this.executeTransaction(action, txInfo);
                    }
                });
            }
        };
    }

    private Callable<Void> getTask(final Runnable action, boolean readOnly, boolean shared) {
        return this.getTask(new Callable<Void>() {
            @Override
            public Void call() {
                action.run();
                return null;
            }
        }, readOnly, shared);
    }

    private <V> V executeTransaction(Callable<V> action, TxInfo txInfo) throws Exception {

        // Associate this instance with the current thread
        HashMap<String, PersistentObjectTransactionManager<?>> managerMap = ASPECT_MANAGER_MAP.get();
        final boolean emptyMap = managerMap == null;
        if (emptyMap) {
            managerMap = new HashMap<String, PersistentObjectTransactionManager<?>>();
            ASPECT_MANAGER_MAP.set(managerMap);
        }
        if (managerMap.containsKey(this.name)) {
            throw new IllegalStateException("A PersistentObjectTransactionManager named `" + this.name
              + "' already has an open transaction in the current thread; all names must be distinct");
        }
        managerMap.put(this.name, this);

        // Perform transaction
        V result;
        try {

            // Invoke callback
            try {
                result = action.call();
            } catch (Exception e) {
                throw e;
            }

            // Update database (read-write transactions only)
            if (!txInfo.isReadOnly())
                this.persistentObject.setRoot(txInfo.getSnapshot().getRoot(), txInfo.getSnapshot().getVersion());
        } finally {
            if (emptyMap)
                ASPECT_MANAGER_MAP.remove();
            else
                managerMap.remove(this.name);
        }

        // Done
        return result;
    }

    // Checked exception masker
    @SuppressWarnings("unchecked")
    static <T extends Throwable> T maskException(Throwable t) throws T {
        throw (T)t;
    }

    // Prepend stack frames from the current thread onto exception trace
    private void prependCurrentStackTrace(Throwable t) {
        final StackTraceElement[] innerFrames = t.getStackTrace();
        final StackTraceElement[] outerFrames = new Throwable().getStackTrace();
        final StackTraceElement[] frames = new StackTraceElement[innerFrames.length + outerFrames.length];
        System.arraycopy(innerFrames, 0, frames, 0, innerFrames.length);
        frames[innerFrames.length] = new StackTraceElement(this.getClass().getName(),
          "<placeholder>", "Synchronous PersistentObjectTransactionManager Transaction", -1);
        for (int i = 1; i < outerFrames.length; i++)
            frames[innerFrames.length + i] = outerFrames[i];
        t.setStackTrace(frames);
    }

// Worker thread

    private class WorkerThread extends Thread {

        private final Logger log = LoggerFactory.getLogger(this.getClass());

        public WorkerThread() {
            super("PersistentObjectTransactionManager-" + PersistentObjectTransactionManager.THREAD_COUNTER.incrementAndGet());
        }

        @Override
        public void run() {
            this.log.debug(this + " transaction thread starting");
            int count = 0;
            try {
                while (true) {

                    // Get next in queue
                    final FutureTask<?> task = PersistentObjectTransactionManager.this.queue.take();
                    if (task == TERMINATE_TASK)
                        break;

                    // Execute task
                    task.run();
                    count++;
                }
            } catch (Throwable t) {
                this.log.error(this + " transaction thread caught unexpected exception", t);
            } finally {
                this.log.debug(this + " transaction thread stopping after " + count + " transactions");
            }
        }
    }

// Transaction Info

    private class TxInfo {

        private final PersistentObject<T>.Snapshot snapshot;
        private final boolean readOnly;
        private final boolean shared;

        TxInfo(PersistentObject<T>.Snapshot snapshot, boolean readOnly, boolean shared) {
            this.snapshot = snapshot;
            this.readOnly = readOnly;
            this.shared = shared;
        }

        public PersistentObject<T>.Snapshot getSnapshot() {
            return this.snapshot;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        public boolean isShared() {
            return this.shared;
        }
    }
}
