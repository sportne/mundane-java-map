package io.github.mundanej.map.nativeimage;

import java.util.List;

/** Fixed, checksummed resource inventory for the native raster smoke. */
final class NativeRasterResources {
    static final Entry PNG =
            entry(
                    "png-affine-smoke.png",
                    93,
                    "8a000426ca9cf59284f23d527c2e5f5fc1a4177b7c0746f43cdaabebbc1f0749");
    static final Entry PNG_WORLD =
            entry(
                    "png-affine-smoke.pgw",
                    21,
                    "22957d91d69b3a1f9a5c802989cfa155705fe632f234dc3756f22ff4aa62d6ff");
    static final Entry JPEG =
            entry(
                    "jpeg-affine-smoke.jpg",
                    368,
                    "ec45fd822819b3e2e390e384150628ac409dfc5803ca080da4a1610aa9da5cb9");
    static final Entry JPEG_WORLD =
            entry(
                    "jpeg-affine-smoke.jgw",
                    22,
                    "a161caf538f992a30ddc4f1109bdffcb8b4bd08e8384e958e4094c619bc54561");
    static final Entry MALFORMED =
            entry(
                    "malformed-idat-crc.png",
                    70,
                    "ab60a5ad65801a858409ea3b424abef5a14e2df852f9b1b75c7cf8461436c7c6");
    static final List<Entry> INVENTORY = List.of(PNG, PNG_WORLD, JPEG, JPEG_WORLD, MALFORMED);

    private static final String RESOURCE_DIRECTORY = "/io/github/mundanej/map/nativeimage/raster/";

    private NativeRasterResources() {}

    private static Entry entry(String localName, int length, String sha256) {
        return new Entry(RESOURCE_DIRECTORY + localName, localName, length, sha256);
    }

    record Entry(String resourceName, String localName, int length, String sha256)
            implements NativeFixtureResource {
        Entry {
            NativeFixtureResource.validate(
                    resourceName, RESOURCE_DIRECTORY, localName, length, sha256, "raster");
        }
    }
}
