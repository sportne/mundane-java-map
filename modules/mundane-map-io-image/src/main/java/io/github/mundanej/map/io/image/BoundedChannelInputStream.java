package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

final class BoundedChannelInputStream extends InputStream {
    private static final int MAXIMUM_READ = 4096;

    private final ImageChannel channel;
    private final long length;
    private final EncodedRasterDecodeContext context;
    private long position;

    BoundedChannelInputStream(
            ImageChannel channel, long length, EncodedRasterDecodeContext context) {
        this.channel = Objects.requireNonNull(channel, "channel");
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        this.length = length;
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int count = read(one, 0, 1);
        return count < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int requested) throws IOException {
        Objects.checkFromIndexSize(offset, requested, bytes.length);
        if (requested == 0) {
            return 0;
        }
        if (position >= length) {
            return -1;
        }
        context.checkpoint();
        int count = (int) Math.min(Math.min((long) requested, MAXIMUM_READ), length - position);
        ByteBuffer target = ByteBuffer.wrap(bytes, offset, count);
        int read;
        do {
            read = channel.read(target, position);
        } while (read == 0);
        if (read > 0) {
            position += read;
        }
        context.checkpoint();
        return read;
    }

    @Override
    public long skip(long count) {
        if (count <= 0) {
            return 0;
        }
        context.checkpoint();
        long skipped = Math.min(count, length - position);
        position += skipped;
        context.checkpoint();
        return skipped;
    }

    @Override
    public int available() {
        return (int) Math.min(Integer.MAX_VALUE, length - position);
    }

    @Override
    public void close() {
        // Borrowed operation input; lifecycle remains with the source.
    }
}
