package eu.toolchain.swim.async;

public interface DatagramBindListener {
    void ready(EventLoop eventLoop, DatagramBindChannel channel);
}
