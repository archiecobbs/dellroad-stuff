
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
 * An open instance can be thought of as representing an open transaction to rewrite the file.
 * The "transaction" is committed via {@link #close}, or t may be explicitly rolled back via {@link #cancel}
 * (this also deletes the temporary file). In addition, if any method throws {@link IOException}, the
 * {@link #cancel} is implicitly invoked.
 *
 * <p>
 * Note: to guarantee that the new content will always be found in the future, even if there is a sudden system crash,
 * the caller must also {@link java.nio.channels.FileChannel#force} this instance (before closing) and the containing
 * directory (after closing).
 */
public class AtomicUpdateFileOutputStream extends FileOutputStream {

    private static final int OPEN = 0;
    private static final int CLOSED = 1;
    private static final int CANCELED = 2;

    private final File targetFile;
    private final File tempFile;

    private int state;
    private long timestamp;

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
        super(tempFile);
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
     * @throws NullPointerException if either parameter is null
     */
    public AtomicUpdateFileOutputStream(File targetFile) throws IOException {
        this(targetFile, File.createTempFile("atomicupdate", null, targetFile.getAbsoluteFile().getParentFile()));
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
     * If this instance has already been {@linkplain #close closed} (either successfully or not)
     * or {@linkplain #cancel canceled}, this will return null.
     *
     * @return temporary file, or null if {@link #close} or {@link #cancel} has already been invoked
     */
    public synchronized File getTempFile() {
        return this.tempFile;
    }

// Cancel

    /**
     * Cancel this instance. This "aborts" the open "transaction", and deletes the temporary file.
     *
     * <p>
     * Does nothing if {@link #close} or {@link #cancel} has already been invoked.
     *
     * @return true if canceled, false if this instance is already closed or canceled
     */
    public synchronized boolean cancel() {

        // Already closed?
        if (this.state != OPEN)
            return false;

        // Update state - we're committed now
        this.state = CANCELED;

        // Close output stream to release file descriptor
        try {
            super.close();
        } catch (IOException e) {
            // ignore
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
     * Close this instance. This "commits" the open "transaction" if not already committed.
     *
     * <p>
     * If successful, the configured {@code tempFile} will be {@linkplain atomically StandardCopyOption#ATOMIC_MOVE}
     * {@linkplain Files#move renamed} to the configured destination file {@code targetFile}. In any case, after
     * this method returns (either normally or abnormally), the temporary file will no longer exist.
     *
     * <p>
     * Does nothing if this instance has already been successfully closed.
     *
     * @throws IOException if an I/O error occurs
     * @throws IOException if {@link #cancel} has already been invoked
     */
    @Override
    public synchronized void close() throws IOException {

        // Check state
        switch (this.state) {
        case CLOSED:
            throw new IOException("already closed");
        case CANCELED:
            throw new IOException("already canceled");
        default:
            break;
        }

        // If anything goes wrong, automatically cancel
        try {

            // Close temporary file
            super.close();

            // Read updated modification time
            final long newTimestamp = this.tempFile.lastModified();

            // Rename file, or delete it if that fails
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
    protected void finalize() throws IOException {
        try {
            this.cancel();
        } finally {
            super.finalize();
        }
    }
}
