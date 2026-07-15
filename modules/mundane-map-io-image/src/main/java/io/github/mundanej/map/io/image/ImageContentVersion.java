package io.github.mundanej.map.io.image;

import java.util.Arrays;

final class ImageContentVersion {
    private final long length;
    private final byte[] sha256;

    ImageContentVersion(long length, byte[] sha256) {
        this.length = length;
        this.sha256 = sha256.clone();
    }

    long length() {
        return length;
    }

    byte[] sha256() {
        return sha256.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ImageContentVersion version
                && length == version.length
                && Arrays.equals(sha256, version.sha256);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(length) + Arrays.hashCode(sha256);
    }
}
