package io.github.mundanej.map.nativeimage;

import java.util.List;

/** Fixed, checksummed resource inventory for the native GeoTIFF smoke. */
final class NativeGeoTiffResources {
    private static final String RESOURCE_DIRECTORY = "/io/github/mundanej/map/nativeimage/geotiff/";

    static final Entry RASTER_NONE =
            entry(
                    "gdal-rgb-strip-none-4326.tif",
                    1_450,
                    "d71b92274a28d39a17b1b189092adb7e5e4a46b6a414e21cdc2b044bceba1616");
    static final Entry RASTER_DEFLATE =
            entry(
                    "gdal-gray-tile-deflate-3857.tif",
                    567,
                    "9791990e4efff48ba80cd7665766da81aba570381c8d6fd895ccba1bf328d885");
    static final Entry ELEVATION_PACKBITS =
            entry(
                    "gdal-int16-strip-packbits-4326.tif",
                    814,
                    "690bb35cc0b91053f2b5236c259af6931714b8ebd6c811f907c204519be7cb4f");
    static final Entry ELEVATION_DEFLATE =
            entry(
                    "gdal-float32-tile-deflate-3857.tif",
                    453,
                    "2cb8508be0580e66c31e2808544927b20debbfb82afa58a89f2b6d18c8914646");
    static final List<Entry> INVENTORY =
            List.of(RASTER_NONE, RASTER_DEFLATE, ELEVATION_PACKBITS, ELEVATION_DEFLATE);

    private NativeGeoTiffResources() {}

    private static Entry entry(String name, int length, String sha256) {
        return new Entry(RESOURCE_DIRECTORY + name, name, length, sha256);
    }

    record Entry(String resourceName, String localName, int length, String sha256)
            implements NativeFixtureResource {
        Entry {
            NativeFixtureResource.validate(
                    resourceName, RESOURCE_DIRECTORY, localName, length, sha256, "GeoTIFF");
        }
    }
}
