package io.github.mundanej.map.io.shapefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

final class JdkShapefileFileAccess implements ShapefileFileAccess {
    @Override
    public boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class);
            return true;
        } catch (NoSuchFileException exception) {
            return false;
        }
    }

    @Override
    public boolean isSameFile(Path first, Path second) throws IOException {
        return Files.isSameFile(first, second);
    }

    @Override
    public Channel open(Path path) throws IOException {
        return new JdkChannel(FileChannel.open(path, StandardOpenOption.READ));
    }

    private record JdkChannel(FileChannel channel) implements Channel {
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
    }
}
