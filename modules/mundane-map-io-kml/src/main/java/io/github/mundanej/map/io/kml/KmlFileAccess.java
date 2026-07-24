package io.github.mundanej.map.io.kml;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

interface KmlFileAccess {
    BasicFileAttributes readAttributes(Path path) throws IOException;

    SeekableByteChannel open(Path path) throws IOException;
}
