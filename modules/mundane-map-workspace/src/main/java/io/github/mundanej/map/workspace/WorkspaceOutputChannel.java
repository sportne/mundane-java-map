package io.github.mundanej.map.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;

interface WorkspaceOutputChannel extends AutoCloseable {
    /** Writes bytes from the supplied buffer. */
    int write(ByteBuffer source) throws IOException;

    /** Forces file content and metadata to stable storage. */
    void force(boolean metadata) throws IOException;

    /** Closes the temporary output channel. */
    @Override
    void close() throws IOException;
}
