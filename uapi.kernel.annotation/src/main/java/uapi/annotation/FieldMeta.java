/**
 * Copyright (C) 2010 The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.annotation;

import uapi.InvalidArgumentException;
import uapi.KernelException;
import uapi.helper.ArgumentChecker;
import uapi.helper.CollectionHelper;
import uapi.helper.StringHelper;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class FieldMeta {

    private Builder _builder;

    private FieldMeta(final Builder builder) {
        this._builder = builder;
    }

    public String getName() {
        return this._builder._name;
    }

    public String getTypeName() {
        return this._builder._typeName;
    }

    public String getInjectServiceId() {
        return this._builder._injectServiceId;
    }

    public boolean getIsList() {
        return this._builder._isList;
    }

    public String getModifiers() {
        return CollectionHelper.asString(this._builder._modifiers, " ");
    }

    @Override
    public String toString() {
        return this._builder.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The builder for <code>FieldMeta</code>
     */
    public static final class Builder extends uapi.helper.Builder<FieldMeta> {

        private String _name;
        private String _typeName;
        private String _injectServiceId;
        private boolean _isList;
        private List<Modifier> _modifiers = new ArrayList<>();

        public Builder setName(
                final String name
        ) throws KernelException {
            checkStatus();
            this._name = name;
            return this;
        }

        public Builder setTypeName(
                final String typeName
        ) throws KernelException {
            checkStatus();
            this._typeName = typeName;
            return this;
        }

        public Builder setInjectServiceId(
                final String injectServiceId
        ) throws KernelException {
            checkStatus();
            this._injectServiceId = injectServiceId;
            return this;
        }

        public Builder setIsList(
                final boolean isList
        ) throws KernelException {
            checkStatus();
            this._isList = isList;
            return this;
        }

        public Builder addModifier(
                final Modifier modifier
        ) throws KernelException {
            checkStatus();
            ArgumentChecker.notNull(modifier, "modifier");
            this._modifiers.add(modifier);
            return this;
        }


        @Override
        protected void validation() throws InvalidArgumentException {
            ArgumentChecker.required(this._name, "fieldName");
            ArgumentChecker.required(this._typeName, "fieldTypeName");
        }

        @Override
        protected void initProperties() { }

        @Override
        protected FieldMeta createInstance() {
            return new FieldMeta(this);
        }

        @Override
        public String toString() {
            return StringHelper.makeString(
                    "FieldMeta[" +
                            "fieldName={}, " +
                            "fieldTypeName={}," +
                            "injectServiceId={}",
                    this._name,
                    this._typeName,
                    this._injectServiceId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Builder builder = (Builder) o;

            if (_isList != builder._isList) return false;
            if (!_name.equals(builder._name)) return false;
            return _typeName.equals(builder._typeName);

        }

        @Override
        public int hashCode() {
            int result = _name.hashCode();
            result = 31 * result + _typeName.hashCode();
            result = 31 * result + (_isList ? 1 : 0);
            return result;
        }
    }
}
