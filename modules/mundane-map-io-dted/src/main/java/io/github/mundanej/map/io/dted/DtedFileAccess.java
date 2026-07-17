package io.github.mundanej.map.io.dted;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Package-private positional file seam for deterministic transaction tests. */
interface DtedFileAccess extends AutoCloseable {
    long size() throws IOException;

    int read(ByteBuffer destination, long position) throws IOException;

    @Override
    void close() throws IOException;
}
