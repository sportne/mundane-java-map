package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.core.CrsDefinitions;
import org.junit.jupiter.api.Test;

class ShapefileValuesTest {
    @Test
    void everyLimitWitherIsImmutableAndPositive() {
        ShapefileLimits defaults = ShapefileLimits.defaults();
        ShapefileLimits changed =
                defaults.withMaximumComponentBytes(1)
                        .withMaximumPhysicalRecords(2)
                        .withMaximumRecordBytes(3)
                        .withMaximumParts(4)
                        .withMaximumPoints(5)
                        .withMaximumTopologyComparisons(6)
                        .withMaximumDbfFields(7)
                        .withMaximumDbfFieldWidth(8)
                        .withMaximumCpgBytes(9)
                        .withMaximumPrjBytes(10)
                        .withMaximumDecodedTextCharacters(11)
                        .withMaximumParserAllocationBytes(12);
        assertEquals(1, changed.maximumComponentBytes());
        assertEquals(12, changed.maximumParserAllocationBytes());
        assertNotEquals(defaults, changed);
        assertEquals(changed, changed);
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotEquals(defaults, defaults.withMaximumPoints(0)));
    }

    @Test
    void optionsHaveValueSemanticsAndExplicitOverride() {
        ShapefileOpenOptions defaults = ShapefileOpenOptions.defaults();
        ShapefileOpenOptions override = defaults.withCrsOverride(CrsDefinitions.EPSG_4326);
        assertEquals(CrsDefinitions.EPSG_4326, override.crsOverride().orElseThrow());
        assertEquals(defaults, override.withoutCrsOverride());
        assertEquals(defaults.hashCode(), override.withoutCrsOverride().hashCode());

        ShapefileOpenOptions encoded = defaults.withDbfEncodingOverride(DbfEncoding.IBM850);
        assertEquals(DbfEncoding.IBM850, encoded.dbfEncodingOverride().orElseThrow());
        assertEquals(defaults, encoded.withoutDbfEncodingOverride());
        assertNotEquals(defaults, encoded);
        assertEquals(
                java.util.List.of(
                        DbfEncoding.UTF_8,
                        DbfEncoding.ISO_8859_1,
                        DbfEncoding.WINDOWS_1252,
                        DbfEncoding.IBM437,
                        DbfEncoding.IBM850),
                java.util.List.of(DbfEncoding.values()));
        NullPointerException failure =
                assertThrows(
                        NullPointerException.class,
                        () -> assertEquals(defaults, defaults.withDbfEncodingOverride(null)));
        assertEquals("dbfEncodingOverride", failure.getMessage());
    }
}
