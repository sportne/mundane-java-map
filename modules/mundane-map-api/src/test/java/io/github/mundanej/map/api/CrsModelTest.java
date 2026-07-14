package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrsModelTest {
    private static final CrsDefinition GEOGRAPHIC =
            new CrsDefinition(
                    "EPSG:4326",
                    CrsKind.GEOGRAPHIC,
                    new CrsAxis(CrsAxisMeaning.LONGITUDE, CrsUnit.DEGREE),
                    new CrsAxis(CrsAxisMeaning.LATITUDE, CrsUnit.DEGREE),
                    new Envelope(-180.0, -90.0, 180.0, 90.0));

    @Test
    void recognizedAndUnknownMetadataRetainExactBoundedProvenance() {
        String retained = "x".repeat(CrsMetadata.RETAINED_DEFINITION_LIMIT);
        CrsMetadata recognized =
                CrsMetadata.recognized(GEOGRAPHIC, Optional.of("declared"), Optional.of(retained));
        CrsMetadata unknown = CrsMetadata.unknown(Optional.empty(), Optional.of(" raw WKT "));

        assertEquals(Optional.of("EPSG:4326"), recognized.canonicalIdentifier());
        assertEquals(CrsKind.GEOGRAPHIC, recognized.kind());
        assertEquals(retained, recognized.retainedDefinition().orElseThrow());
        assertEquals(CrsKind.UNKNOWN, unknown.kind());
        assertTrue(unknown.definition().isEmpty());
        String maximumIdentifier = "i".repeat(CrsMetadata.DECLARED_IDENTIFIER_LIMIT);
        CrsMetadata maximum =
                CrsMetadata.recognized(
                        GEOGRAPHIC, Optional.of(maximumIdentifier), Optional.empty());
        assertEquals(maximumIdentifier, maximum.declaredIdentifier().orElseThrow());
        assertNotEquals(
                recognized,
                CrsMetadata.recognized(
                        GEOGRAPHIC, Optional.of("different declaration"), Optional.of(retained)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CrsMetadata.recognized(
                                GEOGRAPHIC,
                                Optional.of("i".repeat(CrsMetadata.DECLARED_IDENTIFIER_LIMIT + 1)),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CrsMetadata.unknown(
                                Optional.empty(),
                                Optional.of(
                                        "x".repeat(CrsMetadata.RETAINED_DEFINITION_LIMIT + 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> CrsMetadata.unknown(Optional.empty(), Optional.empty()));
    }

    @Test
    void definitionsRejectUnknownKindsAndWrongAxisProfiles() {
        CrsAxis longitude = new CrsAxis(CrsAxisMeaning.LONGITUDE, CrsUnit.DEGREE);
        CrsAxis latitude = new CrsAxis(CrsAxisMeaning.LATITUDE, CrsUnit.DEGREE);
        Envelope domain = new Envelope(-1.0, -1.0, 1.0, 1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new CrsDefinition("unknown", CrsKind.UNKNOWN, longitude, latitude, domain));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CrsDefinition("wrong", CrsKind.PROJECTED, longitude, latitude, domain));
    }

    @Test
    void envelopesRequireFiniteSpansAndUseStableMidpoints() {
        Envelope large = new Envelope(1.0e308, -10.0, 1.1e308, 10.0);
        assertEquals(1.05e308, large.center().x(), 1.0e292);
        assertThrows(
                IllegalArgumentException.class,
                () -> new Envelope(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0));
    }

    @Test
    void crsProblemsOwnLexicallyOrderedBoundedContext() {
        LinkedHashMap<String, String> input = new LinkedHashMap<>();
        input.put("z", "last");
        input.put("a", "first");
        CrsProblem problem = new CrsProblem("CRS_TEST", "A stable problem", input);
        input.clear();

        assertEquals(Map.of("a", "first", "z", "last"), problem.context());
        assertEquals("a", problem.context().keySet().iterator().next());
        assertThrows(UnsupportedOperationException.class, () -> problem.context().clear());
        assertEquals(problem, new CrsException(problem).problem());
        assertFalse(new CrsException(problem).getMessage().isBlank());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CrsProblem("bad code", "Invalid", Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CrsProblem("crs_lower", "Invalid", Map.of()));
    }
}
