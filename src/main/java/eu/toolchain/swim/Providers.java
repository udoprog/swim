package eu.toolchain.swim;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Providers {
    public static <T> Provider<T> ofValue(final T value) {
        return new Provider<T>() {
            @Override
            public T get() {
                return value;
            }
        };
    }

    public static Provider<Boolean> ofAtomic(final AtomicBoolean source) {
        return new Provider<Boolean>() {
            @Override
            public Boolean get() {
                return source.get();
            }
        };
    }
}
