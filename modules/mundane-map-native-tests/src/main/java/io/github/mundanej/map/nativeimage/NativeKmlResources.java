package io.github.mundanej.map.nativeimage;

import java.util.List;

/** Fixed, checksummed resource inventory for the native KML smoke. */
final class NativeKmlResources {
    private static final String RESOURCE_DIRECTORY = "/io/github/mundanej/map/nativeimage/kml/";

    static final Entry VALID =
            new Entry(
                    RESOURCE_DIRECTORY + "simplekml-static-profile.kml",
                    "simplekml-static-profile.kml",
                    1_330,
                    "32fc9de3e4cc1a09254f01a3b922a406b2237f79c3c6dc403ede3b5c7f37e2f2");
    static final List<Entry> INVENTORY = List.of(VALID);

    private NativeKmlResources() {}

    record Entry(String resourceName, String localName, int length, String sha256)
            implements NativeFixtureResource {
        Entry {
            NativeFixtureResource.validate(
                    resourceName, RESOURCE_DIRECTORY, localName, length, sha256, "KML");
        }
    }
}
