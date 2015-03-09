package eu.toolchain.swim.async;

public interface DatagramBindListener<T> {
    T setup(DatagramBindChannel channel);
}
