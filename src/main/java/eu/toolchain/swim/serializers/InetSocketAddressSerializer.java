package eu.toolchain.swim.serializers;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public final class InetSocketAddressSerializer implements Serializer<InetSocketAddress> {
    private InetSocketAddressSerializer() {
    }

    private static final Serializer<InetSocketAddress> instance = new InetSocketAddressSerializer();

    public static Serializer<InetSocketAddress> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, InetSocketAddress a) {
        final byte version = getVersion(a.getAddress());
        final byte[] address = a.getAddress().getAddress();
        b.put(version);
        b.put(address);
        b.putInt(a.getPort());
    }

    @Override
    public InetSocketAddress deserialize(ByteBuffer b) throws Exception {
        final byte version = b.get();
        final InetAddress address = parseAddress(version, b);
        final int port = b.getInt();
        return new InetSocketAddress(address, port);
    }

    private InetAddress parseAddress(byte version, ByteBuffer buffer) throws UnknownHostException {
        switch (version) {
        case 4:
            return parseInet4(buffer);
        case 6:
            return parseInet6(buffer);
        default:
            throw new IllegalArgumentException();
        }
    }

    private InetAddress parseInet4(ByteBuffer buffer)
            throws UnknownHostException {
        final byte[] b = new byte[4];
        buffer.get(b);
        return Inet4Address.getByAddress(b);
    }

    private InetAddress parseInet6(ByteBuffer buffer)
            throws UnknownHostException {
        final byte[] b = new byte[16];
        buffer.get(b);
        return Inet6Address.getByAddress(b);
    }

    private byte getVersion(final InetAddress address) {
        if (address instanceof Inet4Address)
            return 4;

        if (address instanceof Inet6Address)
            return 6;

        throw new IllegalStateException();
    }
}