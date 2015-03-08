package eu.toolchain.swim.async;

public interface DatagramBindListener<T extends DatagramBindChannel> {
    T ready(DatagramBindChannel channel);
}
