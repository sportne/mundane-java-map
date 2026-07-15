package io.github.mundanej.map.io.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

interface ImageChannel extends AutoCloseable {
    long size() throws IOException;

    int read(ByteBuffer target, long position) throws IOException;

    @Override
    void close() throws IOException;

    static ImageChannel open(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        return new ImageChannel() {
            @Override
            public long size() throws IOException {
                return channel.size();
            }

            @Override
            public int read(ByteBuffer target, long position) throws IOException {
                return channel.read(target, position);
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }

    @FunctionalInterface
    interface Opener {
        ImageChannel open(Path path) throws IOException;
    }
}
