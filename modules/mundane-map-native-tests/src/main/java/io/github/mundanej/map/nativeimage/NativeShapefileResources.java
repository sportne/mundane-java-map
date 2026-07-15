package io.github.mundanej.map.nativeimage;

import java.util.List;
import java.util.Objects;

/** Fixed, checksummed resource inventory for the native Shapefile smoke. */
final class NativeShapefileResources {
    static final Entry SHP =
            entry(
                    "polygon-smoke.shp",
                    540,
                    "5c2862ee77214a2c979ef2b4750de82b864eb5652c9ce1c4a952af4bbe8bfe37");
    static final Entry SHX =
            entry(
                    "polygon-smoke.shx",
                    116,
                    "a1b843a5ac60ee7d17de18d5b12cfe55df68ea93b755a0213429d9bc8cb9791c");
    static final Entry DBF =
            entry(
                    "polygon-smoke.dbf",
                    139,
                    "aeff5d58a12d628862343ba0ed0ae8e6ea156289be446ef666147f97dd039add");
    static final Entry CPG =
            entry(
                    "polygon-smoke.cpg",
                    12,
                    "ae03d34572181876684790d8b478a10d95055bbd039289095e8b6af1a20733a2");
    static final Entry PRJ =
            entry(
                    "polygon-smoke.prj",
                    413,
                    "4f01f36bf95963fb9174c50d396f1201b266ef7fa43304e718a7cb324ad71394");
    static final Entry MALFORMED =
            entry(
                    "malformed-record.shp",
                    108,
                    "9ed470ac950deebfe867a423efeb3f4a11c81f559c69a5d42178caf11c4db07a");
    static final List<Entry> INVENTORY = List.of(SHP, SHX, DBF, CPG, PRJ, MALFORMED);

    private static final String RESOURCE_DIRECTORY =
            "/io/github/mundanej/map/nativeimage/shapefile/";

    private NativeShapefileResources() {}

    private static Entry entry(String localName, int length, String sha256) {
        return new Entry(RESOURCE_DIRECTORY + localName, localName, length, sha256);
    }

    record Entry(String resourceName, String localName, int length, String sha256) {
        Entry {
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(localName, "localName");
            Objects.requireNonNull(sha256, "sha256");
            if (!resourceName.equals(RESOURCE_DIRECTORY + localName)
                    || localName.contains("/")
                    || localName.contains("\\")
                    || length <= 0
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid native Shapefile resource entry");
            }
        }
    }
}
