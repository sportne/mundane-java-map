package io.github.mundanej.map.io.shapefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

interface ShapefileFileAccess {
    boolean exists(Path path) throws IOException;

    boolean isSameFile(Path first, Path second) throws IOException;

    Channel open(Path path) throws IOException;

    interface Channel extends AutoCloseable {
        long size() throws IOException;

        int read(ByteBuffer target, long position) throws IOException;

        @Override
        void close() throws IOException;
    }
}
