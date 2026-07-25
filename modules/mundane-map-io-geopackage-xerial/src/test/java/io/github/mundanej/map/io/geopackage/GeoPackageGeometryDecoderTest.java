package io.github.mundanej.map.io.geopackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoPackageGeometryDecoderTest {
    @Test
    void decodesAllSixFamiliesWithMixedByteOrdersAndPackedParts() {
        var point =
                decode(
                        packageGeometry(
                                point(ByteOrder.BIG_ENDIAN, 2, 3), ByteOrder.LITTLE_ENDIAN, null),
                        GeoPackageGeometryType.POINT);
        assertEquals(2, point.geometry().envelope().minX());

        LineStringGeometry line =
                assertInstanceOf(
                        LineStringGeometry.class,
                        decode(
                                        packageGeometry(
                                                line(
                                                        ByteOrder.LITTLE_ENDIAN,
                                                        new double[] {0, 0, 2, 1, 4, 0}),
                                                ByteOrder.BIG_ENDIAN,
                                                null),
                                        GeoPackageGeometryType.LINE_STRING)
                                .geometry());
        assertEquals(3, line.coordinates().size());

        PolygonGeometry polygon =
                assertInstanceOf(
                        PolygonGeometry.class,
                        decode(
                                        packageGeometry(
                                                polygon(
                                                        ByteOrder.BIG_ENDIAN,
                                                        List.of(
                                                                new double[] {
                                                                    0, 0, 8, 0, 8, 8, 0, 8, 0, 0
                                                                },
                                                                new double[] {
                                                                    2, 2, 2, 4, 4, 4, 4, 2, 2, 2
                                                                })),
                                                ByteOrder.LITTLE_ENDIAN,
                                                null),
                                        GeoPackageGeometryType.POLYGON)
                                .geometry());
        assertEquals(1, polygon.holes().size());

        MultiPointGeometry points =
                assertInstanceOf(
                        MultiPointGeometry.class,
                        decode(
                                        packageGeometry(
                                                collection(
                                                        4,
                                                        ByteOrder.LITTLE_ENDIAN,
                                                        List.of(
                                                                point(ByteOrder.BIG_ENDIAN, 1, 2),
                                                                point(
                                                                        ByteOrder.LITTLE_ENDIAN,
                                                                        3,
                                                                        4))),
                                                ByteOrder.BIG_ENDIAN,
                                                null),
                                        GeoPackageGeometryType.MULTI_POINT)
                                .geometry());
        assertEquals(2, points.coordinates().size());

        MultiLineStringGeometry lines =
                assertInstanceOf(
                        MultiLineStringGeometry.class,
                        decode(
                                        packageGeometry(
                                                collection(
                                                        5,
                                                        ByteOrder.BIG_ENDIAN,
                                                        List.of(
                                                                line(
                                                                        ByteOrder.LITTLE_ENDIAN,
                                                                        new double[] {0, 0, 1, 1}),
                                                                line(
                                                                        ByteOrder.BIG_ENDIAN,
                                                                        new double[] {
                                                                            4, 4, 5, 5, 6, 4
                                                                        }))),
                                                ByteOrder.LITTLE_ENDIAN,
                                                null),
                                        GeoPackageGeometryType.MULTI_LINE_STRING)
                                .geometry());
        assertEquals(2, lines.partCount());
        assertEquals(5, lines.coordinates().size());

        MultiPolygonGeometry polygons =
                assertInstanceOf(
                        MultiPolygonGeometry.class,
                        decode(
                                        packageGeometry(
                                                collection(
                                                        6,
                                                        ByteOrder.LITTLE_ENDIAN,
                                                        List.of(
                                                                polygon(
                                                                        ByteOrder.BIG_ENDIAN,
                                                                        List.of(
                                                                                new double[] {
                                                                                    0, 0, 2, 0,
                                                                                    2, 2, 0, 2,
                                                                                    0, 0
                                                                                })),
                                                                polygon(
                                                                        ByteOrder.LITTLE_ENDIAN,
                                                                        List.of(
                                                                                new double[] {
                                                                                    4, 4, 6, 4,
                                                                                    6, 6, 4, 6,
                                                                                    4, 4
                                                                                })))),
                                                ByteOrder.BIG_ENDIAN,
                                                null),
                                        GeoPackageGeometryType.GEOMETRY)
                                .geometry());
        assertEquals(2, polygons.polygonCount());
        assertEquals(2, polygons.ringCount());
    }

    @Test
    void validatesWkbBeforeUsingHeaderEnvelopeForQueryRejection() {
        byte[] malformedOutside =
                packageGeometry(
                        java.util.Arrays.copyOf(
                                line(ByteOrder.LITTLE_ENDIAN, new double[] {10, 10, 20, 20}), 5),
                        ByteOrder.LITTLE_ENDIAN,
                        new Envelope(10, 10, 20, 20));
        SourceException malformed =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "unfiltered",
                                        malformedOutside,
                                        4326,
                                        GeoPackageGeometryType.LINE_STRING,
                                        GeoPackageLimits.DEFAULTS,
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_RECORD_INVALID", malformed.terminal().code());
        SourceException filteredMalformed =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "filtered",
                                        malformedOutside,
                                        4326,
                                        GeoPackageGeometryType.LINE_STRING,
                                        GeoPackageLimits.DEFAULTS,
                                        CancellationToken.none(),
                                        Optional.of(new Envelope(0, 0, 5, 5))));
        assertEquals("GEOPACKAGE_RECORD_INVALID", filteredMalformed.terminal().code());

        byte[] validOutside =
                packageGeometry(
                        line(ByteOrder.LITTLE_ENDIAN, new double[] {10, 10, 20, 20}),
                        ByteOrder.LITTLE_ENDIAN,
                        new Envelope(10, 10, 20, 20));
        GeoPackageGeometryDecoder.DecodedGeometry decoded =
                GeoPackageGeometryDecoder.decode(
                        "filtered",
                        validOutside,
                        4326,
                        GeoPackageGeometryType.LINE_STRING,
                        GeoPackageLimits.DEFAULTS,
                        CancellationToken.none(),
                        Optional.of(new Envelope(0, 0, 5, 5)));
        assertTrue(decoded.filtered());
    }

    @Test
    void rejectsOpenRingsWrongNestedTypesAndEnvelopeMismatch() {
        byte[] open =
                packageGeometry(
                        polygon(
                                ByteOrder.LITTLE_ENDIAN,
                                List.of(new double[] {0, 0, 2, 0, 2, 2, 0, 2})),
                        ByteOrder.LITTLE_ENDIAN,
                        null);
        assertRecordInvalid(open, GeoPackageGeometryType.POLYGON);

        byte[] wrongChild =
                packageGeometry(
                        collection(
                                5,
                                ByteOrder.LITTLE_ENDIAN,
                                List.of(point(ByteOrder.BIG_ENDIAN, 1, 2))),
                        ByteOrder.LITTLE_ENDIAN,
                        null);
        assertRecordInvalid(wrongChild, GeoPackageGeometryType.MULTI_LINE_STRING);

        byte[] outsideEnvelope =
                packageGeometry(
                        line(ByteOrder.LITTLE_ENDIAN, new double[] {0, 0, 4, 4}),
                        ByteOrder.LITTLE_ENDIAN,
                        new Envelope(0, 0, 2, 2));
        assertRecordInvalid(outsideEnvelope, GeoPackageGeometryType.LINE_STRING);
    }

    @Test
    void recognizesStandardEmptyEncodingForEveryGeometryFamily() {
        List<String> expected =
                List.of("point", "line", "polygon", "multipoint", "multiline", "multipolygon");
        List<String> actual = new ArrayList<>();
        for (int type = 1; type <= 6; type++) {
            ByteBuffer wkb = ByteBuffer.allocate(type == 1 ? 21 : 9).order(ByteOrder.LITTLE_ENDIAN);
            wkb.put((byte) 1).putInt(type);
            if (type == 1) {
                wkb.putDouble(Double.NaN).putDouble(Double.NaN);
            } else {
                wkb.putInt(0);
            }
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            header.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 0x11).putInt(4326);
            GeoPackageGeometryDecoder.DecodedGeometry decoded =
                    decode(
                            concatenate(finish(header), finish(wkb)),
                            GeoPackageGeometryType.GEOMETRY);
            assertTrue(decoded.isEmpty());
            actual.add(decoded.emptyType());
        }
        assertEquals(expected, actual);
    }

    @Test
    void enforcesCoordinateAndPartLimitsBeforeLargeAllocation() {
        SourceException coordinates =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "coordinate-limit",
                                        packageGeometry(
                                                line(
                                                        ByteOrder.LITTLE_ENDIAN,
                                                        new double[] {0, 0, 1, 1, 2, 2}),
                                                ByteOrder.LITTLE_ENDIAN,
                                                null),
                                        4326,
                                        GeoPackageGeometryType.LINE_STRING,
                                        limits(2, 10),
                                        CancellationToken.none()));
        assertEquals("coordinates", coordinates.terminal().context().get("limit"));

        SourceException parts =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "part-limit",
                                        packageGeometry(
                                                polygon(
                                                        ByteOrder.LITTLE_ENDIAN,
                                                        List.of(
                                                                new double[] {
                                                                    0, 0, 4, 0, 4, 4, 0, 4, 0, 0
                                                                },
                                                                new double[] {
                                                                    1, 1, 1, 2, 2, 2, 2, 1, 1, 1
                                                                })),
                                                ByteOrder.LITTLE_ENDIAN,
                                                null),
                                        4326,
                                        GeoPackageGeometryType.POLYGON,
                                        limits(100, 1),
                                        CancellationToken.none()));
        assertEquals("parts", parts.terminal().context().get("limit"));

        byte[] twoPolygons =
                packageGeometry(
                        collection(
                                6,
                                ByteOrder.LITTLE_ENDIAN,
                                List.of(
                                        polygon(
                                                ByteOrder.LITTLE_ENDIAN,
                                                List.of(
                                                        new double[] {
                                                            0, 0, 2, 0, 2, 2, 0, 2, 0, 0
                                                        })),
                                        polygon(
                                                ByteOrder.LITTLE_ENDIAN,
                                                List.of(
                                                        new double[] {
                                                            3, 3, 5, 3, 5, 5, 3, 5, 3, 3
                                                        })))),
                        ByteOrder.LITTLE_ENDIAN,
                        null);
        assertTrue(
                GeoPackageGeometryDecoder.decode(
                                        "exact-multipolygon-parts",
                                        twoPolygons,
                                        4326,
                                        GeoPackageGeometryType.MULTI_POLYGON,
                                        limits(100, 4),
                                        CancellationToken.none())
                                .geometry()
                        instanceof MultiPolygonGeometry);
        SourceException multipolygonParts =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "multipolygon-part-limit",
                                        twoPolygons,
                                        4326,
                                        GeoPackageGeometryType.MULTI_POLYGON,
                                        limits(100, 3),
                                        CancellationToken.none()));
        assertEquals("parts", multipolygonParts.terminal().context().get("limit"));
    }

    private static GeoPackageGeometryDecoder.DecodedGeometry decode(
            byte[] bytes, GeoPackageGeometryType type) {
        return GeoPackageGeometryDecoder.decode(
                "geometry", bytes, 4326, type, GeoPackageLimits.DEFAULTS, CancellationToken.none());
    }

    private static void assertRecordInvalid(byte[] bytes, GeoPackageGeometryType type) {
        SourceException failure = assertThrows(SourceException.class, () -> decode(bytes, type));
        assertEquals("GEOPACKAGE_RECORD_INVALID", failure.terminal().code());
    }

    private static byte[] packageGeometry(byte[] wkb, ByteOrder headerOrder, Envelope envelope) {
        ByteBuffer header = ByteBuffer.allocate(envelope == null ? 8 : 40).order(headerOrder);
        header.put((byte) 'G').put((byte) 'P').put((byte) 0);
        int flags = headerOrder == ByteOrder.LITTLE_ENDIAN ? 1 : 0;
        if (envelope != null) {
            flags |= 2;
        }
        header.put((byte) flags).putInt(4326);
        if (envelope != null) {
            header.putDouble(envelope.minX())
                    .putDouble(envelope.maxX())
                    .putDouble(envelope.minY())
                    .putDouble(envelope.maxY());
        }
        return concatenate(finish(header), wkb);
    }

    private static byte[] point(ByteOrder order, double x, double y) {
        ByteBuffer bytes = buffer(order, 1 + 4 + 16);
        bytes.put((byte) endian(order)).putInt(1).putDouble(x).putDouble(y);
        return finish(bytes);
    }

    private static byte[] line(ByteOrder order, double[] coordinates) {
        ByteBuffer bytes = buffer(order, 1 + 4 + 4 + coordinates.length * 8);
        bytes.put((byte) endian(order)).putInt(2).putInt(coordinates.length / 2);
        for (double value : coordinates) {
            bytes.putDouble(value);
        }
        return finish(bytes);
    }

    private static byte[] polygon(ByteOrder order, List<double[]> rings) {
        int size = 1 + 4 + 4;
        for (double[] ring : rings) {
            size += 4 + ring.length * 8;
        }
        ByteBuffer bytes = buffer(order, size);
        bytes.put((byte) endian(order)).putInt(3).putInt(rings.size());
        for (double[] ring : rings) {
            bytes.putInt(ring.length / 2);
            for (double value : ring) {
                bytes.putDouble(value);
            }
        }
        return finish(bytes);
    }

    private static byte[] collection(int type, ByteOrder order, List<byte[]> children) {
        int size = 1 + 4 + 4 + children.stream().mapToInt(value -> value.length).sum();
        ByteBuffer prefix = buffer(order, 1 + 4 + 4);
        prefix.put((byte) endian(order)).putInt(type).putInt(children.size());
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        output.writeBytes(finish(prefix));
        children.forEach(output::writeBytes);
        return output.toByteArray();
    }

    private static ByteBuffer buffer(ByteOrder order, int size) {
        return ByteBuffer.allocate(size).order(order);
    }

    private static int endian(ByteOrder order) {
        return order == ByteOrder.LITTLE_ENDIAN ? 1 : 0;
    }

    private static byte[] finish(ByteBuffer buffer) {
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(first.length + second.length);
        output.writeBytes(first);
        output.writeBytes(second);
        return output.toByteArray();
    }

    private static GeoPackageLimits limits(int coordinates, int parts) {
        GeoPackageLimits defaults = GeoPackageLimits.DEFAULTS;
        return new GeoPackageLimits(
                defaults.maximumInputBytes(),
                defaults.maximumSchemaObjects(),
                defaults.maximumColumns(),
                defaults.maximumIdentifierCharacters(),
                defaults.maximumMetadataRows(),
                defaults.maximumTextValueCharacters(),
                defaults.maximumTextCharacters(),
                defaults.maximumBlobBytes(),
                defaults.maximumRows(),
                defaults.maximumVmOpcodes(),
                defaults.maximumOwnedBytes(),
                defaults.maximumZoomLevels(),
                defaults.maximumZoom(),
                defaults.maximumMatrixAxis(),
                coordinates,
                parts,
                defaults.maximumCacheEntries(),
                defaults.maximumCacheBytes());
    }
}
