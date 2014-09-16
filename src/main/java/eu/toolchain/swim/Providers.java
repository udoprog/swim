package eu.toolchain.swim;

public abstract class Providers {
    public static <T> Provider<T> ofValue(final T value) {
        return new Provider<T>() {
            @Override
            public T get() {
                return value;
            }
        };
    }
}
