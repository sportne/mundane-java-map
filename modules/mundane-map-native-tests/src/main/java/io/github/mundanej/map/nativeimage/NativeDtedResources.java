package io.github.mundanej.map.nativeimage;

import java.util.List;

/** Fixed, checksummed resource inventory for the native DTED smoke. */
final class NativeDtedResources {
    private static final String RESOURCE_DIRECTORY = "/io/github/mundanej/map/nativeimage/dted/";

    static final Entry LEVEL_ZERO =
            new Entry(
                    RESOURCE_DIRECTORY + "zone-v-l0-smoke.dt0",
                    "s81.dt0",
                    8_762,
                    "9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802");
    static final List<Entry> INVENTORY = List.of(LEVEL_ZERO);
    static final String TRUNCATED_LOCAL_NAME = "s81-truncated.dt0";

    private NativeDtedResources() {}

    record Entry(String resourceName, String localName, int length, String sha256)
            implements NativeFixtureResource {
        Entry {
            NativeFixtureResource.validate(
                    resourceName,
                    RESOURCE_DIRECTORY,
                    "zone-v-l0-smoke.dt0",
                    localName,
                    length,
                    sha256,
                    "DTED");
        }
    }
}
