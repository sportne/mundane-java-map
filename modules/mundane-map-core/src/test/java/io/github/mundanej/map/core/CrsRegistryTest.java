package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Projection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("StringConcatToTextBlock")
class CrsRegistryTest {
    private static final CrsDefinition LOCAL_PROJECTED =
            new CrsDefinition(
                    "LOCAL:1",
                    CrsKind.PROJECTED,
                    new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                    new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                    new Envelope(-1000.0, -1000.0, 1000.0, 1000.0));

    @Test
    void level1ResolvesOnlyCanonicalKeysAndExactApprovedAliases() {
        CrsRegistry registry = CrsRegistry.level1();

        assertEquals(CrsDefinitions.EPSG_4326, registry.resolve("EPSG:4326"));
        assertEquals(CrsDefinitions.EPSG_4326, registry.resolve("urn:ogc:def:crs:EPSG::4326"));
        assertEquals(
                CrsDefinitions.EPSG_4326,
                registry.resolve("http://www.opengis.net/def/crs/EPSG/0/4326"));
        assertEquals(CrsDefinitions.EPSG_3857, registry.resolve("urn:ogc:def:crs:EPSG::3857"));
        assertEquals(
                CrsDefinitions.EPSG_3857,
                registry.resolve("http://www.opengis.net/def/crs/EPSG/0/3857"));
        CrsException unknown =
                assertThrows(CrsException.class, () -> registry.resolve(" epsg:4326 "));
        assertEquals("CRS_REGISTRY_KEY_UNKNOWN", unknown.problem().code());
        for (String rejected :
                List.of(
                        "CRS:84",
                        "OGC:CRS84",
                        "WGS84",
                        "EPSG:900913",
                        "ESRI:102100",
                        "epsg:4326",
                        " EPSG:4326")) {
            assertThrows(CrsException.class, () -> registry.resolve(rejected), rejected);
        }
    }

    @Test
    void buildersRejectCollisionsAreSingleUseAndRemainIsolated() {
        CrsRegistry.Builder builder = CrsRegistry.builder();
        builder.registerDefinition(CrsDefinitions.EPSG_4326, List.of("geo"));
        CrsException collision =
                assertThrows(
                        CrsException.class,
                        () -> builder.registerDefinition(CrsDefinitions.EPSG_3857, List.of("geo")));
        assertEquals(
                Map.of(
                        "conflictingDeclarationIndex",
                        "3",
                        "existingDeclarationIndex",
                        "1",
                        "key",
                        "geo"),
                collision.problem().context());

        CrsRegistry first = builder.build();
        assertThrows(IllegalStateException.class, builder::build);
        assertEquals(CrsDefinitions.EPSG_4326, first.resolve("geo"));
        assertThrows(CrsException.class, () -> CrsRegistry.builder().build().resolve("geo"));

        CrsException withinCall =
                assertThrows(
                        CrsException.class,
                        () ->
                                CrsRegistry.builder()
                                        .registerDefinition(
                                                CrsDefinitions.EPSG_4326,
                                                List.of("duplicate", "duplicate")));
        assertEquals("1", withinCall.problem().context().get("existingDeclarationIndex"));
        assertEquals("2", withinCall.problem().context().get("conflictingDeclarationIndex"));

        CrsRegistry.Builder canonicalBuilder = CrsRegistry.builder();
        canonicalBuilder.registerDefinition(CrsDefinitions.EPSG_4326, List.of());
        CrsException canonicalCollision =
                assertThrows(
                        CrsException.class,
                        () ->
                                canonicalBuilder.registerDefinition(
                                        CrsDefinitions.EPSG_4326, List.of()));
        assertEquals("0", canonicalCollision.problem().context().get("existingDeclarationIndex"));
        assertEquals(
                "1", canonicalCollision.problem().context().get("conflictingDeclarationIndex"));

        CrsRegistry.Builder canonicalAliasBuilder = CrsRegistry.builder();
        canonicalAliasBuilder.registerDefinition(CrsDefinitions.EPSG_4326, List.of("EPSG:3857"));
        CrsException canonicalAliasCollision =
                assertThrows(
                        CrsException.class,
                        () ->
                                canonicalAliasBuilder.registerDefinition(
                                        CrsDefinitions.EPSG_3857, List.of()));
        assertEquals(
                "1", canonicalAliasCollision.problem().context().get("existingDeclarationIndex"));
        assertEquals(
                "2",
                canonicalAliasCollision.problem().context().get("conflictingDeclarationIndex"));

        CrsRegistry.Builder projectionBuilder = CrsRegistry.builderWithLevel1();
        CrsException projectionCollision =
                assertThrows(
                        CrsException.class,
                        () -> projectionBuilder.registerProjection(new WebMercatorProjection()));
        assertEquals("0", projectionCollision.problem().context().get("existingDeclarationIndex"));
        assertEquals(
                "1", projectionCollision.problem().context().get("conflictingDeclarationIndex"));
    }

    @Test
    void resolvesForwardInverseIdentityAndDomainAwareQueries() {
        CrsRegistry registry = CrsRegistry.level1();
        CrsOperation forward =
                registry.operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        CrsOperation inverse =
                registry.operation(CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_4326);
        CrsOperation identity =
                registry.operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);

        Coordinate geographic = new Coordinate(-71.0, 42.0);
        Coordinate restored = inverse.transform(forward.transform(geographic));
        assertEquals(geographic.x(), restored.x(), 1.0e-9);
        assertEquals(geographic.y(), restored.y(), 1.0e-9);
        assertEquals(geographic, identity.transform(geographic));
        assertEquals(new Coordinate(0.0, 90.0), identity.transform(new Coordinate(0.0, 90.0)));

        QueryEnvelopeTransform complete =
                forward.transformQueryEnvelope(new Envelope(-10.0, -10.0, 10.0, 10.0));
        assertEquals(QueryEnvelopeStatus.COMPLETE, complete.status());
        QueryEnvelopeTransform clipped =
                forward.transformQueryEnvelope(new Envelope(-10.0, 80.0, 10.0, 90.0));
        assertEquals(QueryEnvelopeStatus.CLIPPED, clipped.status());
        assertTrue(clipped.transformedEnvelope().isPresent());
        QueryEnvelopeTransform outside =
                forward.transformQueryEnvelope(new Envelope(-10.0, 86.0, 10.0, 90.0));
        assertEquals(QueryEnvelopeStatus.OUTSIDE, outside.status());
        assertFalse(outside.transformedEnvelope().isPresent());
        QueryEnvelopeTransform touching =
                forward.transformQueryEnvelope(new Envelope(180.0, 0.0, Math.nextUp(180.0), 1.0));
        assertEquals(QueryEnvelopeStatus.CLIPPED, touching.status());
        assertEquals(
                WebMercatorProjection.WORLD_LIMIT,
                touching.transformedEnvelope().orElseThrow().minX());
    }

    @Test
    void rejectsFabricatedDefinitionsAndUnavailableDirectPairs() {
        CrsDefinition fabricated =
                new CrsDefinition(
                        CrsDefinitions.EPSG_4326.canonicalIdentifier(),
                        CrsDefinitions.EPSG_4326.kind(),
                        CrsDefinitions.EPSG_4326.xAxis(),
                        CrsDefinitions.EPSG_4326.yAxis(),
                        new Envelope(-170.0, -80.0, 170.0, 80.0));
        CrsRegistry registry = CrsRegistry.level1();
        CrsException mismatch =
                assertThrows(
                        CrsException.class,
                        () -> registry.operation(fabricated, CrsDefinitions.EPSG_3857));
        assertEquals("CRS_DEFINITION_MISMATCH", mismatch.problem().code());

        CrsRegistry.Builder missingEndpoint = CrsRegistry.builder();
        missingEndpoint.registerDefinition(CrsDefinitions.EPSG_4326, List.of());
        assertEquals(
                "CRS_DEFINITION_MISMATCH",
                assertThrows(
                                CrsException.class,
                                () ->
                                        missingEndpoint.registerProjection(
                                                new WebMercatorProjection()))
                        .problem()
                        .code());

        CrsRegistry directOnly =
                CrsRegistry.builderWithLevel1()
                        .registerDefinition(LOCAL_PROJECTED, List.of())
                        .build();
        assertEquals(
                "CRS_TRANSFORM_UNAVAILABLE",
                assertThrows(
                                CrsException.class,
                                () ->
                                        directOnly.operation(
                                                CrsDefinitions.EPSG_4326, LOCAL_PROJECTED))
                        .problem()
                        .code());
    }

    @Test
    void metadataResolutionDistinguishesMissingUnknownAndRecognizedValues() {
        CrsRegistry registry = CrsRegistry.level1();
        CrsException missing =
                assertThrows(
                        CrsException.class,
                        () ->
                                registry.operationFromMetadata(
                                        Optional.empty(), CrsDefinitions.EPSG_3857));
        CrsException unknown =
                assertThrows(
                        CrsException.class,
                        () ->
                                registry.operationFromMetadata(
                                        Optional.of(
                                                CrsMetadata.unknown(
                                                        Optional.of("CUSTOM"), Optional.empty())),
                                        CrsDefinitions.EPSG_3857));
        assertEquals("CRS_METADATA_MISSING", missing.problem().code());
        assertEquals("CRS_DEFINITION_UNKNOWN", unknown.problem().code());

        CrsOperation operation =
                registry.operationFromMetadata(
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())),
                        CrsDefinitions.EPSG_3857);
        assertEquals(CrsDefinitions.EPSG_4326, operation.sourceCrs());
    }

    @Test
    void operationRejectsFaultyProjectionResultsOutsideTheRegisteredTarget() {
        CrsRegistry registry =
                CrsRegistry.builder()
                        .registerDefinition(CrsDefinitions.EPSG_4326, List.of())
                        .registerDefinition(CrsDefinitions.EPSG_3857, List.of())
                        .registerProjection(new FaultyProjection())
                        .build();
        CrsException failure =
                assertThrows(
                        CrsException.class,
                        () ->
                                registry.operation(
                                                CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857)
                                        .transform(new Coordinate(0.0, 0.0)));
        assertEquals("CRS_TRANSFORM_NON_FINITE", failure.problem().code());
        assertEquals("easting", failure.problem().context().get("axis"));
    }

    private static final class FaultyProjection implements Projection {
        private final WebMercatorProjection delegate = new WebMercatorProjection();

        @Override
        public CrsDefinition sourceCrs() {
            return delegate.sourceCrs();
        }

        @Override
        public CrsDefinition targetCrs() {
            return delegate.targetCrs();
        }

        @Override
        public Envelope sourceDomain() {
            return delegate.sourceDomain();
        }

        @Override
        public Envelope targetDomain() {
            return delegate.targetDomain();
        }

        @Override
        public Coordinate project(Coordinate source) {
            return new Coordinate(Math.nextUp(WebMercatorProjection.WORLD_LIMIT), 0.0);
        }

        @Override
        public Coordinate unproject(Coordinate target) {
            return delegate.unproject(target);
        }

        @Override
        public Envelope projectEnvelope(Envelope source) {
            return delegate.projectEnvelope(source);
        }

        @Override
        public Envelope unprojectEnvelope(Envelope target) {
            return delegate.unprojectEnvelope(target);
        }
    }
}
