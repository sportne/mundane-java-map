package io.github.mundanej.map.nativeimage;

import java.util.List;

/** Fixed, checksummed resource inventory for the native GPX smoke. */
final class NativeGpxResources {
    private static final String RESOURCE_DIRECTORY = "/io/github/mundanej/map/nativeimage/gpx/";

    static final Entry VALID =
            new Entry(
                    RESOURCE_DIRECTORY + "gpxpy-waypoint-track.gpx",
                    "gpxpy-waypoint-track.gpx",
                    1_342,
                    "9ba7ee5166f37d2ce817b4eff012c614f86d143829dc5df3719baa34c5b21a86");
    static final List<Entry> INVENTORY = List.of(VALID);

    private NativeGpxResources() {}

    record Entry(String resourceName, String localName, int length, String sha256)
            implements NativeFixtureResource {
        Entry {
            NativeFixtureResource.validate(
                    resourceName, RESOURCE_DIRECTORY, localName, length, sha256, "GPX");
        }
    }
}
