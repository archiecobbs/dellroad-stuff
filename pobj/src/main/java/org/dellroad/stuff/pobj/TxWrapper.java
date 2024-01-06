
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.pobj;

import org.springframework.transaction.support.SmartTransactionObject;

final class TxWrapper<T> implements SmartTransactionObject {

    private TxInfo<T> info;

    TxWrapper(TxInfo<T> info) {
        this.info = info;
    }

    public TxInfo<T> getInfo() {
        return this.info;
    }
    public void setInfo(TxInfo<T> info) {
        this.info = info;
    }

    @Override
    public boolean isRollbackOnly() {
        return this.info != null && this.info.isRollbackOnly();
    }

    @Override
    public void flush() {
    }

    @Override
    public String toString() {
        return "TxWrapper[info=" + this.info + "]";
    }
}
