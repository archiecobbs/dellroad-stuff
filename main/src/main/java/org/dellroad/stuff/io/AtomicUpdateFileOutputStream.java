
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * A {@link FileOutputStream} that atomically updates the target file.
 *
 * <p>
 * Instances write to a temporary file until {@link #close} is invoked, at which time the temporary file
 * gets {@linkplain File#renameTo renamed} to the target file. This rename operation is atomic on most systems
 * (e.g., all UNIX variants). The result is that the target file always exists, and if opened at any time,
 * will contain either the previous content or the new content, but never a mix of the two.
 *
 * <p>
 * Instances therefore represent a "transaction" for rewriting the file. As such, they can be in one of three states:
 * {@link #OPEN}, {@link #CLOSED}, or {@link #CANCELED}. State {@link #CLOSED} implies that the file update was
 * successful (no {@link IOException}s occurred); state {@link #CANCELED} implies the file update was either
 * explicitly canceled (via {@link #cancel}) or implicitly canceled due to an {@link IOException} being thrown by any method.
 *
 * <p>
 * When still {@link #OPEN}, the transaction is "comitted" by invoking {@link #close}, or rolled back by invoking {@link #cancel}.
 * Once an instance has been closed or canceled, the temporary file will have been deleted, and any subsequent invocations
 * of {@link #close} or {@link #cancel} have no effect.
 *
 * <p>
 * Note: to guarantee that the new content will always be found in the future, even if there is a sudden system crash,
 * the caller must also {@link java.nio.channels.FileChannel#force} this instance (before closing) and the containing
 * directory (after closing).
 */
public class AtomicUpdateFileOutputStream extends FileOutputStream {

    /**
     * The stream is open for writing.
     */
    public static final int OPEN = 0;

    /**
     * The stream is closed and the update has been successful.
     */
    public static final int CLOSED = 1;

    /**
     * The stream is closed, and the update has been canceled either explicitly via {@link #cancel}
     * or implicitly due to an {@link IOException} having been thrown.
     */
    public static final int CANCELED = 2;

    private final File targetFile;
    private final File tempFile;

    private int state;
    private long timestamp;
    private boolean invokingSuperClose;             // workaround for a stupid re-entrancy bug in FileOutputStream.close()

// Constructors

    /**
     * Constructor.
     *
     * @param targetFile the ultimate destination for the output when {@linkplain #close closed}.
     * @param tempFile temporary file that accumulates output until {@linkplain #close close}.
     * @throws FileNotFoundException if {@code tempFile} cannot be opened for any reason
     * @throws SecurityException if a security manager prevents writing to {@code tempFile}
     * @throws NullPointerException if either parameter is null
     */
    public AtomicUpdateFileOutputStream(File targetFile, File tempFile) throws FileNotFoundException {
        this(targetFile, tempFile, false);
    }

    /**
     * Constructor.
     *
     * @param targetFile the ultimate destination for the output when {@linkplain #close closed}.
     * @param tempFile temporary file that accumulates output until {@linkplain #close close}.
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws FileNotFoundException if {@code tempFile} cannot be opened for any reason
     * @throws SecurityException if a security manager prevents writing to {@code tempFile}
     * @throws NullPointerException if either parameter is null
     */
    public AtomicUpdateFileOutputStream(File targetFile, File tempFile, boolean append) throws FileNotFoundException {
        super(tempFile, append);
        this.tempFile = tempFile;
        if (targetFile == null)
            throw new NullPointerException("null targetFile");
        this.targetFile = targetFile;
    }

    /**
     * Convenience constructor.
     *
     * <p>
     * This constructor uses a temporary file within the same directory as {@code targetFile}.
     *
     * @param targetFile the ultimate destination for the output when {@linkplain #close closed}.
     * @throws FileNotFoundException if {@code tempFile} cannot be opened for any reason
     * @throws IOException if a temporary file could not be created
     * @throws SecurityException if a security manager prevents writing to {@code tempFile}
     * @throws NullPointerException if {@code targetFile} is null
     */
    public AtomicUpdateFileOutputStream(File targetFile) throws IOException {
        this(targetFile, false);
    }

    /**
     * Convenience constructor.
     *
     * <p>
     * This constructor uses a temporary file within the same directory as {@code targetFile}.
     *
     * @param targetFile the ultimate destination for the output when {@linkplain #close closed}.
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws FileNotFoundException if {@code tempFile} cannot be opened for any reason
     * @throws IOException if a temporary file could not be created
     * @throws SecurityException if a security manager prevents writing to {@code tempFile}
     * @throws NullPointerException if {@code targetFile} is null
     */
    public AtomicUpdateFileOutputStream(File targetFile, boolean append) throws IOException {
        this(targetFile, AtomicUpdateFileOutputStream.tempFileFor(targetFile), append);
    }

    private static File tempFileFor(File targetFile) throws IOException {
        return File.createTempFile("atomicupdate", null, targetFile.getAbsoluteFile().getParentFile());
    }

// Accessors

    /**
     * Get the target file.
     *
     * @return target file, never null
     */
    public synchronized File getTargetFile() {
        return this.targetFile;
    }

    /**
     * Get the temporary file.
     *
     * <p>
     * If this instance is in state {@link #CLOSED} or {@link #CANCELED}, the file will no longer exist.
     *
     * @return temporary file, never null
     */
    public synchronized File getTempFile() {
        return this.tempFile;
    }

    /**
     * Get the state of this instance
     *
     * @return the current state of this instance
     */
    public synchronized int getState() {
        return this.state;
    }

// Cancel

    /**
     * Cancel this instance if still open. This rolls back the open transaction.
     *
     * <p>
     * This method does nothing (and returns false) if {@link #close} or {@link #cancel} has already been invoked.
     *
     * @return true if this instance was canceled, false if this instance is already closed or canceled
     */
    public synchronized boolean cancel() {

        // Already closed or canceled?
        if (this.state != OPEN)
            return false;

        // Update state - we're "committed" now
        this.state = CANCELED;

        // Close output stream to release file descriptor
        if (!this.invokingSuperClose) {
            try {
                super.close();
            } catch (IOException e) {
                // ignore
            } finally {
                this.invokingSuperClose = false;
            }
        }

        // Delete the temporary file
        this.tempFile.delete();

        // Done
        return true;
    }

// OutputStream Wrappers

    @Override
    public void write(byte[] b) throws IOException {
        try {
            super.write(b);
        } catch (IOException e) {
            this.cancel();
            throw e;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            super.write(b, off, len);
        } catch (IOException e) {
            this.cancel();
            throw e;
        }
    }

    @Override
    public void write(int b) throws IOException {
        try {
            super.write(b);
        } catch (IOException e) {
            this.cancel();
            throw e;
        }
    }

    @Override
    public void flush() throws IOException {
        try {
            super.flush();
        } catch (IOException e) {
            this.cancel();
            throw e;
        }
    }

    /**
     * Close this instance if still open. This commits the open transaction.
     *
     * <p>
     * If this instance is still open, this method will close the temporary file and then attempt to
     * {@linkplain StandardCopyOption#ATOMIC_MOVE atomically} {@linkplain Files#move rename} it onto the target file.
     * In any case, after this method returns (either normally or abnormally), the temporary file will no longer exist.
     *
     * <p>
     * If this instance has already been closed or canceled, this method does nothing.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public synchronized void close() throws IOException {

        // JDK bug workaround
        if (this.invokingSuperClose) {
            super.close();
            return;
        }

        // Check state
        switch (this.state) {
        case CLOSED:
        case CANCELED:
            return;
        default:
            break;
        }

        // If anything goes wrong, automatically cancel
        try {

            // Close temporary file
            this.invokingSuperClose = true;
            try {
                super.close();
            } finally {
                this.invokingSuperClose = false;
            }

            // Read updated modification time
            final long newTimestamp = this.tempFile.lastModified();

            // Rename file
            Files.move(this.tempFile.toPath(), this.targetFile.toPath(),
              StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // Update target file timestamp
            this.timestamp = newTimestamp;
        } catch (IOException e) {

            // We failed - cancel instead
            this.cancel();
            throw e;
        }

        // Done
        this.state = CLOSED;
    }

    /**
     * Get the last modification timestamp of the target file as it was at the time it was updated by this instance.
     *
     * <p>
     * This method only works after {@link #close} has been successfully invoked, otherwise it returns zero.
     *
     * @return target file modification time, or zero if {@link #close} has not been successfully invoked
     */
    public synchronized long getTimestamp() {
        return this.timestamp;
    }

// Object

    /**
     * Ensure the temporary file is deleted in cases where this instance never got successfully closed.
     */
    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() throws Throwable {
        try {
            this.cancel();
        } finally {
            super.finalize();
        }
    }
}
