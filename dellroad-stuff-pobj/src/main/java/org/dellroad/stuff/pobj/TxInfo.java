
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

final class TxInfo<T> {

    private final boolean readOnly;
    private PersistentObject<T>.Snapshot snapshot;
    private boolean rollbackOnly;
    private boolean xaCleanup;

    TxInfo(PersistentObject<T>.Snapshot snapshot, boolean readOnly) {
        this.readOnly = readOnly;
        this.setSnapshot(snapshot);
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public PersistentObject<T>.Snapshot getSnapshot() {
        return this.snapshot;
    }
    public void setSnapshot(PersistentObject<T>.Snapshot snapshot) {
        if (snapshot == null)
            throw new IllegalArgumentException("null snapshot");
        this.snapshot = snapshot;
    }

    public boolean isRollbackOnly() {
        return this.rollbackOnly;
    }
    public void setRollbackOnly(boolean rollbackOnly) {
        this.rollbackOnly = rollbackOnly;
    }

    public boolean isXACleanup() {
        return this.xaCleanup;
    }
    public void setXACleanup(boolean xaCleanup) {
        this.xaCleanup = xaCleanup;
    }

    @Override
    public String toString() {
        return "TxInfo[root=" + this.snapshot.getRoot() + ",version=" + this.snapshot.getVersion()
          + (this.readOnly ? ",readOnly" : "")
          + (this.rollbackOnly ? ",rollbackOnly" : "")
          + (this.xaCleanup ? ",xaCleanup" : "")
          + "]";
    }
}

