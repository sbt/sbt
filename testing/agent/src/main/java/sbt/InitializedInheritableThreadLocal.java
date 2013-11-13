// Copyright © 2011-2013, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package sbt;

class InitializedInheritableThreadLocal<T> extends InheritableThreadLocal<T> {

    private final T initialValue;

    public InitializedInheritableThreadLocal(T initialValue) {
        this.initialValue = initialValue;
    }

    @Override
    protected T initialValue() {
        return initialValue;
    }
}
