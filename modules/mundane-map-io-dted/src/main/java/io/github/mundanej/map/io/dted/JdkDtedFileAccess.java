package io.github.mundanej.map.io.dted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** FileChannel-backed positional access used by the public facade. */
final class JdkDtedFileAccess implements DtedFileAccess {
    private final FileChannel channel;

    private JdkDtedFileAccess(FileChannel channel) {
        this.channel = channel;
    }

    static DtedFileAccess open(Path path) throws IOException {
        return new JdkDtedFileAccess(FileChannel.open(path, StandardOpenOption.READ));
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public int read(ByteBuffer destination, long position) throws IOException {
        return channel.read(destination, position);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
