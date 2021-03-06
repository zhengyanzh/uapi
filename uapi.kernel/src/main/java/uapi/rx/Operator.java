/**
 * Copyright (C) 2010 The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.rx;

import uapi.helper.ArgumentChecker;
import uapi.helper.Functionals;

import java.util.List;

/**
 * Generic Operator
 */
abstract class Operator<T> implements IOperator<T> {

    private final Operator<?> _previously;

    Operator() {
        this._previously = null;
    }

    Operator(Operator<?> previously) {
        ArgumentChecker.required(previously, "previously");
        this._previously = previously;
    }

    Operator<?> getPreviously() {
        return this._previously;
    }

    boolean hasItem() {
        if (this._previously != null) {
            return this._previously.hasItem();
        }
        return false;
    }

    abstract T getItem();

    void done() {
        getPreviously().done();
    }

    @Override
    public <O> IOperator<O> map(Functionals.Convert<T, O> operator) {
        return new MapOperator<>(this, operator);
    }

    @Override
    public IOperator<T> filter(Functionals.Filter<T> operator) {
        return new FilterOperator<>(this, operator);
    }

    @Override
    public IOperator<T> limit(int count) {
        return new LimitOperator<>(this, count);
    }

    @Override
    public void foreach(Functionals.Action<T> action) {
        ForeachOperator operator = new ForeachOperator(this, action);
        operator.getItem();
        operator.done();
    }

    @Override
    public T first() {
        FirstOperator<T> operator = new FirstOperator<>(this);
        T result = operator.getItem();
        operator.done();
        return result;
    }

    @Override
    public List<T> toList() {
        ToListOperator<T> operator = new ToListOperator<>(this);
        List<T> result = operator.getItem();
        operator.done();
        return result;
    }
}
