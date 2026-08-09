package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class SourceCoverageBehaviorTest {
    private static final TestSymbol MARKER = new TestSymbol(SymbolRole.MARKER, "marker");
    private static final TestSymbol LINE = new TestSymbol(SymbolRole.LINE, "line");
    private static final TestSymbol FILL = new TestSymbol(SymbolRole.FILL, "fill");

    @Test
    void closedEnumInventoriesRemainStableAndComplete() {
        assertArrayEquals(
                new BuiltInMarker[] {
                    BuiltInMarker.CIRCLE,
                    BuiltInMarker.SQUARE,
                    BuiltInMarker.TRIANGLE,
                    BuiltInMarker.DIAMOND,
                    BuiltInMarker.CROSS,
                    BuiltInMarker.X,
                    BuiltInMarker.STAR,
                    BuiltInMarker.ARROW
                },
                BuiltInMarker.values());
        assertEquals(BuiltInMarker.CIRCLE, BuiltInMarker.valueOf("CIRCLE"));
        assertEquals(9, MapToolCancelReason.values().length);
        assertEquals(
                MapToolCancelReason.POINTER_STATE_LOST,
                MapToolCancelReason.valueOf("POINTER_STATE_LOST"));
        assertEquals(
                MapToolCancelReason.SOURCE_FAILURE, MapToolCancelReason.valueOf("SOURCE_FAILURE"));
        assertArrayEquals(PortrayalComparison.values(), PortrayalComparison.values().clone());
        assertEquals(
                PortrayalComparison.GREATER_THAN_OR_EQUAL,
                PortrayalComparison.valueOf("GREATER_THAN_OR_EQUAL"));
        assertArrayEquals(
                new PortrayalLogicalOperator[] {
                    PortrayalLogicalOperator.AND,
                    PortrayalLogicalOperator.OR,
                    PortrayalLogicalOperator.NOT
                },
                PortrayalLogicalOperator.values());
    }

    @Test
    void cancellationAndDecoderDefaultsExposeMonotonicNearestOnlyContracts() {
        CancellationSource source = new CancellationSource();
        assertSame(source.token(), source.token());
        assertFalse(source.token().isCancellationRequested());
        source.cancel();
        source.cancel();
        assertTrue(source.token().isCancellationRequested());

        EncodedRasterDecoder decoder =
                (input, context) -> RgbaPixelBuffer.copyOf(1, 1, new int[] {7});
        assertTrue(decoder.supportsInterpolation(RasterInterpolation.NEAREST));
        assertFalse(decoder.supportsInterpolation(RasterInterpolation.BILINEAR));
        assertThrows(NullPointerException.class, () -> decoder.supportsInterpolation(null));
        RgbaPixelBuffer pixels =
                decoder.decode(InputStream.nullInputStream(), new NearestDecodeContext());
        assertEquals(7, pixels.rgbaAt(0, 0));
        assertEquals(RasterInterpolation.NEAREST, new NearestDecodeContext().interpolation());
    }

    @Test
    void sourceMetadataAndLimitsValidateOptionalBoundaries() {
        SourceIdentity identity = new SourceIdentity("source", "Source");
        assertEquals("source", identity.id());
        assertThrows(IllegalArgumentException.class, () -> new SourceIdentity(" ", "Source"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceIdentity("x".repeat(257), "Source"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceIdentity("source", "x".repeat(257)));
        assertThrows(NullPointerException.class, () -> new SourceIdentity(null, "Source"));
        FeatureSourceMetadata metadata =
                new FeatureSourceMetadata(
                        identity,
                        Optional.of(new Envelope(0, 0, 1, 1)),
                        OptionalLong.of(2),
                        Optional.empty(),
                        Optional.empty());
        assertEquals(identity, metadata.identity());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FeatureSourceMetadata(
                                identity,
                                Optional.empty(),
                                OptionalLong.of(-1),
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new FeatureSourceMetadata(
                                null,
                                Optional.empty(),
                                OptionalLong.empty(),
                                Optional.empty(),
                                Optional.empty()));
        assertEquals(
                FeatureQueryLimits.LEVEL_1,
                new FeatureSourceLimits(FeatureQueryLimits.LEVEL_1).queryLimits());
        assertEquals(
                RasterRequestLimits.LEVEL_1,
                new RasterSourceLimits(RasterRequestLimits.LEVEL_1).requestLimits());
        assertThrows(NullPointerException.class, () -> new FeatureSourceLimits(null));
        assertThrows(NullPointerException.class, () -> new RasterSourceLimits(null));
    }

    @Test
    void labelSourcesAndReportEventsRejectAmbiguousInput() {
        assertEquals("fixed", new LiteralLabelText("fixed").text());
        assertEquals("name", new StringifiedTextAttribute("name").attribute());
        assertThrows(IllegalArgumentException.class, () -> new LiteralLabelText(" "));
        assertThrows(IllegalArgumentException.class, () -> new StringifiedTextAttribute(""));

        DiagnosticReport report = DiagnosticReport.empty();
        MapSourceReportEvent event =
                new MapSourceReportEvent("layer", Optional.empty(), Optional.of(report));
        assertEquals("layer", event.layerId());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapSourceReportEvent(" ", Optional.empty(), Optional.of(report)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapSourceReportEvent("layer", Optional.of(report), Optional.of(report)));
        assertThrows(
                NullPointerException.class,
                () -> new MapSourceReportEvent("layer", null, Optional.empty()));
    }

    @Test
    void mapToolDefaultsRemainPassiveAndDoNotRetainContexts() {
        MapTool tool = (event, context) -> MapToolResult.CONSUME;
        tool.onActivate(null);
        assertEquals(MapToolResult.PASS, tool.onMapToolCommand(null, null));
        tool.onDeactivate(null);
        assertEquals(MapCursorIntent.DEFAULT, tool.cursorIntent());
        assertEquals(MapToolResult.CONSUME, tool.onMapToolEvent(null, null));
    }

    @Test
    void portrayalOperandsAndPredicatesValidateEveryClosedVariant() {
        PortrayalOperand.Property property = new PortrayalOperand.Property("kind");
        PortrayalOperand.Literal literal = new PortrayalOperand.Literal("road");
        PortrayalOperand.TypedLiteral typed =
                new PortrayalOperand.TypedLiteral(ThematicValue.numeric(new BigDecimal("2")));
        assertEquals("kind", property.name());
        assertEquals("road", literal.text());
        assertEquals(ThematicValue.Kind.NUMERIC, typed.value().kind());
        assertThrows(IllegalArgumentException.class, () -> new PortrayalOperand.Property(" kind"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortrayalOperand.Literal("x".repeat(4_097)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PortrayalOperand.TypedLiteral(
                                ThematicValue.date(LocalDate.of(2026, 1, 1))));

        PortrayalPredicate.IsNull isNull = new PortrayalPredicate.IsNull(property);
        PortrayalPredicate.Exists exists = new PortrayalPredicate.Exists(property);
        PortrayalPredicate.GeometryTypeIs geometry =
                new PortrayalPredicate.GeometryTypeIs(Set.of(PortrayalGeometryType.POINT));
        PortrayalPredicate.Comparison comparison =
                new PortrayalPredicate.Comparison(PortrayalComparison.EQUAL, property, literal);
        PortrayalPredicate.Between between =
                new PortrayalPredicate.Between(property, literal, typed);
        PortrayalPredicate.Logical logical =
                new PortrayalPredicate.Logical(
                        PortrayalLogicalOperator.AND, List.of(isNull, exists));
        assertEquals(property, isNull.property());
        assertEquals(property, exists.property());
        assertEquals(Set.of(PortrayalGeometryType.POINT), geometry.types());
        assertEquals(literal, comparison.right());
        assertEquals(typed, between.upper());
        assertEquals(2, logical.children().size());
        assertTrue(new PortrayalPredicate.Constant(true).value());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PortrayalPredicate.GeometryTypeIs(Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortrayalPredicate.Comparison(PortrayalComparison.EQUAL, literal, typed));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PortrayalPredicate.Logical(
                                PortrayalLogicalOperator.NOT, List.of(isNull, exists)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortrayalPredicate.Logical(PortrayalLogicalOperator.OR, List.of(isNull)));
        PortrayalPredicate nested = isNull;
        for (int depth = 0; depth < 64; depth++) {
            nested = new PortrayalPredicate.Logical(PortrayalLogicalOperator.NOT, List.of(nested));
        }
        PortrayalPredicate tooDeep = nested;
        assertThrows(
                IllegalArgumentException.class,
                () -> new FilteredSymbolSelector(tooDeep, new FixedSymbolSelector(MARKER)));

        PortrayalPredicate wideChild =
                new PortrayalPredicate.Logical(
                        PortrayalLogicalOperator.OR,
                        java.util.Collections.nCopies(
                                1_024, new PortrayalPredicate.Constant(true)));
        PortrayalPredicate tooMany =
                new PortrayalPredicate.Logical(
                        PortrayalLogicalOperator.OR, java.util.Collections.nCopies(129, wideChild));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FilteredSymbolSelector(tooMany, new FixedSymbolSelector(MARKER)));
    }

    @Test
    void filteredAndResolvedPortrayalPreserveRolesAndOmissions() {
        PortrayalPredicate predicate = new PortrayalPredicate.Constant(true);
        FilteredSymbolSelector filtered =
                new FilteredSymbolSelector(predicate, new FixedSymbolSelector(MARKER));
        assertEquals(SymbolRole.MARKER, filtered.role());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FilteredSymbolSelector(predicate, filtered));

        OmittedSymbol omitted = OmittedSymbol.of(SymbolRole.LINE);
        assertEquals(SymbolRole.LINE, omitted.role());
        assertEquals(0, omitted.opacity());
        assertEquals(omitted, OmittedSymbol.of(SymbolRole.LINE));
        assertEquals(omitted.hashCode(), OmittedSymbol.of(SymbolRole.LINE).hashCode());
        assertNotEquals(omitted, OmittedSymbol.of(SymbolRole.FILL));
        assertThrows(
                IllegalArgumentException.class, () -> OmittedSymbol.of(SymbolRole.LEGACY_GEOMETRY));

        ResolvedFeaturePortrayal portrayal =
                new ResolvedFeaturePortrayal(
                        Optional.of(MARKER), Optional.of(LINE), Optional.of(FILL));
        assertEquals(Optional.of(MARKER), portrayal.forRole(SymbolRole.MARKER));
        assertEquals(Optional.of(LINE), portrayal.forRole(SymbolRole.LINE));
        assertEquals(Optional.of(FILL), portrayal.forRole(SymbolRole.FILL));
        assertEquals(Optional.empty(), portrayal.forRole(SymbolRole.LEGACY_GEOMETRY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResolvedFeaturePortrayal(
                                Optional.of(LINE), Optional.empty(), Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> new ResolvedFeaturePortrayal(null, Optional.empty(), Optional.empty()));
    }

    @Test
    void committedListenerFailureCarriesTheAppliedResultOnly() {
        FeatureEditSnapshot snapshot = new FeatureEditSnapshot(0, projectedCrs(), List.of());
        FeatureEditResult applied = FeatureEditResult.applied(snapshot);
        RuntimeException cause = new RuntimeException("listener");
        FeatureEditNotificationException failure =
                new FeatureEditNotificationException(applied, cause);
        assertSame(applied, failure.committedResult());
        assertSame(cause, failure.getCause());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertNotNull(
                                new FeatureEditNotificationException(
                                        FeatureEditResult.unchanged(snapshot), cause)));
        assertThrows(
                NullPointerException.class,
                () -> assertNotNull(new FeatureEditNotificationException(applied, null)));
        assertEquals("feature", new DeleteFeature("feature").featureId());
        assertThrows(IllegalArgumentException.class, () -> new DeleteFeature(" "));
        assertThrows(NullPointerException.class, () -> new DeleteFeature(null));
    }

    @Test
    void attributeSelectionsOwnOrderAndDistinguishAllNoneAndOnly() {
        AttributeSelection only = AttributeSelection.only(List.of("b", "a"));
        assertEquals(List.of("b", "a"), only.orderedNames());
        assertTrue(only.isOnly());
        assertFalse(AttributeSelection.ALL.isOnly());
        assertFalse(AttributeSelection.NONE.isOnly());
        assertEquals("ALL", AttributeSelection.ALL.toString());
        assertEquals("NONE", AttributeSelection.NONE.toString());
        assertEquals("ONLY[b, a]", only.toString());
        assertEquals(only, AttributeSelection.only(List.of("b", "a")));
        assertEquals(only.hashCode(), AttributeSelection.only(List.of("b", "a")).hashCode());
        assertNotEquals(only, AttributeSelection.only(List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> AttributeSelection.only(List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> AttributeSelection.only(List.of("a", "a")));
        assertThrows(IllegalArgumentException.class, () -> AttributeSelection.only(List.of(" ")));
        assertThrows(NullPointerException.class, () -> AttributeSelection.only(null));
    }

    @Test
    void geometryCategoriesNormalizeAllSupportedShapes() {
        PointGeometry point = new PointGeometry(new Coordinate(0, 0));
        assertEquals(new Coordinate(0, 0), point.coordinate());
        assertEquals(new Envelope(0, 0, 0, 0), point.envelope());
        assertEquals(point, new PointGeometry(new Coordinate(0, 0)));
        assertEquals(point.hashCode(), new PointGeometry(new Coordinate(0, 0)).hashCode());
        assertEquals("PointGeometry[coordinate=Coordinate[x=0.0, y=0.0]]", point.toString());
        LineStringGeometry line = new LineStringGeometry(CoordinateSequence.of(0, 0, 1, 1));
        PolygonGeometry polygon =
                new PolygonGeometry(CoordinateSequence.of(0, 0, 1, 0, 0, 1, 0, 0));
        assertEquals(PortrayalGeometryType.POINT, PortrayalGeometryType.fromGeometry(point));
        assertEquals(
                PortrayalGeometryType.POINT,
                PortrayalGeometryType.fromGeometry(
                        new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1))));
        assertEquals(PortrayalGeometryType.LINE_STRING, PortrayalGeometryType.fromGeometry(line));
        assertEquals(
                PortrayalGeometryType.LINE_STRING,
                PortrayalGeometryType.fromGeometry(
                        MultiLineStringGeometry.ofParts(List.of(line.coordinates()))));
        assertEquals(PortrayalGeometryType.POLYGON, PortrayalGeometryType.fromGeometry(polygon));
        assertEquals(
                PortrayalGeometryType.POLYGON,
                PortrayalGeometryType.fromGeometry(
                        MultiPolygonGeometry.ofPolygons(List.of(polygon))));
        assertThrows(NullPointerException.class, () -> PortrayalGeometryType.fromGeometry(null));
    }

    @Test
    void envelopesAndScaleIntervalsCoverEveryBoundary() {
        Envelope envelope = new Envelope(-2, -4, 6, 8);
        assertEquals(8, envelope.width());
        assertEquals(12, envelope.height());
        assertEquals(new Coordinate(2, 2), envelope.center());
        assertTrue(envelope.contains(new Coordinate(-2, -4)));
        assertTrue(envelope.contains(new Coordinate(6, 8)));
        assertFalse(envelope.contains(new Coordinate(7, 0)));
        assertFalse(envelope.contains(new Coordinate(0, 9)));
        assertEquals(new Envelope(-3, -4, 6, 10), envelope.union(new Envelope(-3, 0, 2, 10)));
        assertEquals(new Envelope(3, 4, 3, 4), Envelope.at(new Coordinate(3, 4)));
        assertThrows(NullPointerException.class, () -> Envelope.at(null));
        assertThrows(NullPointerException.class, () -> envelope.contains(null));
        assertThrows(NullPointerException.class, () -> envelope.union(null));
        assertThrows(IllegalArgumentException.class, () -> new Envelope(Double.NaN, 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Envelope(0, Double.POSITIVE_INFINITY, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Envelope(2, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Envelope(0, 2, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Envelope(-Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));

        ScaleInterval interval = new ScaleInterval(OptionalDouble.of(10), OptionalDouble.of(20));
        assertTrue(interval.constrained());
        assertTrue(interval.includes(10));
        assertTrue(interval.includes(19.999));
        assertFalse(interval.includes(9.999));
        assertFalse(interval.includes(20));
        assertFalse(ScaleInterval.ALL.constrained());
        assertTrue(ScaleInterval.ALL.includes(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScaleInterval(OptionalDouble.of(20), OptionalDouble.of(20)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScaleInterval(OptionalDouble.of(-1), OptionalDouble.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScaleInterval(OptionalDouble.empty(), OptionalDouble.of(Double.NaN)));
        assertThrows(
                NullPointerException.class, () -> new ScaleInterval(null, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> interval.includes(-1));
        assertThrows(
                IllegalArgumentException.class, () -> interval.includes(Double.POSITIVE_INFINITY));
    }

    @Test
    void attributeCandidatesAndBinaryValuesPreserveClosedValueSemantics() {
        AttributeValueCandidate.Attribute attribute =
                new AttributeValueCandidate.Attribute("population");
        AttributeValueCandidate.Literal literal =
                new AttributeValueCandidate.Literal(ThematicValue.numeric(4));
        assertEquals("population", attribute.name());
        assertEquals(ThematicValue.numeric(4), literal.value());
        assertThrows(
                IllegalArgumentException.class, () -> new AttributeValueCandidate.Attribute(" "));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AttributeValueCandidate.Literal(
                                ThematicValue.date(LocalDate.of(2026, 7, 26))));
        assertThrows(NullPointerException.class, () -> new AttributeValueCandidate.Literal(null));

        byte[] mutable = {1, 2, 3};
        AttributeBytes bytes = new AttributeBytes(mutable);
        mutable[0] = 9;
        assertEquals(3, bytes.length());
        assertEquals(1, bytes.byteAt(0));
        assertArrayEquals(new byte[] {1, 2, 3}, bytes.toArray());
        assertEquals(bytes, new AttributeBytes(new byte[] {1, 2, 3}));
        assertEquals(bytes.hashCode(), new AttributeBytes(new byte[] {1, 2, 3}).hashCode());
        assertNotEquals(bytes, new AttributeBytes(new byte[] {1, 2}));
        assertEquals("AttributeBytes[length=3]", bytes.toString());
        assertThrows(NullPointerException.class, () -> new AttributeBytes(null));
        assertThrows(IndexOutOfBoundsException.class, () -> assertEquals(0, bytes.byteAt(3)));
    }

    @Test
    void attributeFieldsRecognizeEveryCanonicalTypeAndNullPolicy() {
        assertTrue(new AttributeField("v", AttributeType.TEXT, false).accepts("text"));
        assertTrue(new AttributeField("v", AttributeType.LOGICAL, false).accepts(true));
        assertTrue(new AttributeField("v", AttributeType.INTEGER, false).accepts(1L));
        assertTrue(new AttributeField("v", AttributeType.FLOATING, false).accepts(1.0));
        assertTrue(new AttributeField("v", AttributeType.DECIMAL, false).accepts(BigDecimal.ONE));
        assertTrue(
                new AttributeField("v", AttributeType.DATE, false)
                        .accepts(LocalDate.of(2026, 7, 26)));
        assertTrue(
                new AttributeField("v", AttributeType.BINARY, false)
                        .accepts(new AttributeBytes(new byte[0])));
        assertTrue(
                new AttributeField("v", AttributeType.TEXT, true).accepts(AttributeNull.INSTANCE));
        assertFalse(
                new AttributeField("v", AttributeType.TEXT, false).accepts(AttributeNull.INSTANCE));
        assertFalse(new AttributeField("v", AttributeType.TEXT, false).accepts(1L));
        assertThrows(NullPointerException.class, () -> new AttributeField("v", null, false));
    }

    @Test
    void retainedCrsMetadataDistinguishesRecognizedAndUnknownDeclarations() {
        CrsDefinition definition = projectedCrs();
        CrsMetadata recognized =
                CrsMetadata.recognized(
                        definition, Optional.of("declared"), Optional.of("definition"));
        assertEquals(Optional.of(definition), recognized.definition());
        assertEquals(Optional.of("LOCAL:COVERAGE"), recognized.canonicalIdentifier());
        assertEquals(CrsKind.PROJECTED, recognized.kind());
        assertEquals(Optional.of("declared"), recognized.declaredIdentifier());
        assertEquals(Optional.of("definition"), recognized.retainedDefinition());
        assertEquals(
                recognized,
                CrsMetadata.recognized(
                        definition, Optional.of("declared"), Optional.of("definition")));
        assertEquals(
                recognized.hashCode(),
                CrsMetadata.recognized(
                                definition, Optional.of("declared"), Optional.of("definition"))
                        .hashCode());
        assertTrue(recognized.toString().contains("LOCAL:COVERAGE"));

        CrsMetadata unknown = CrsMetadata.unknown(Optional.of("UNKNOWN:1"), Optional.empty());
        assertEquals(Optional.empty(), unknown.definition());
        assertEquals(Optional.empty(), unknown.canonicalIdentifier());
        assertEquals(CrsKind.UNKNOWN, unknown.kind());
        assertNotEquals(recognized, unknown);
        assertThrows(
                IllegalArgumentException.class,
                () -> CrsMetadata.unknown(Optional.empty(), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CrsMetadata.unknown(Optional.of(" "), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CrsMetadata.unknown(Optional.of("x".repeat(257)), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CrsMetadata.unknown(Optional.empty(), Optional.of("x".repeat(16_385))));
        assertThrows(
                NullPointerException.class,
                () -> CrsMetadata.recognized(definition, null, Optional.empty()));
    }

    private static CrsDefinition projectedCrs() {
        return new CrsDefinition(
                "LOCAL:COVERAGE",
                CrsKind.PROJECTED,
                new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                new Envelope(-10, -10, 10, 10));
    }

    private record TestSymbol(SymbolRole role, SymbolRendererKey rendererKey) implements Symbol {
        private TestSymbol(SymbolRole role, String key) {
            this(role, new SymbolRendererKey("test." + key));
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private static final class NearestDecodeContext implements EncodedRasterDecodeContext {
        @Override
        public SourceIdentity sourceIdentity() {
            return new SourceIdentity("source", "Source");
        }

        @Override
        public EncodedRasterFormat format() {
            return EncodedRasterFormat.PNG;
        }

        @Override
        public long encodedByteLength() {
            return 1;
        }

        @Override
        public int width() {
            return 1;
        }

        @Override
        public int height() {
            return 1;
        }

        @Override
        public int channelCount() {
            return 4;
        }

        @Override
        public int bitsPerSample() {
            return 8;
        }

        @Override
        public RasterWindow sourceWindow() {
            return new RasterWindow(0, 0, 1, 1);
        }

        @Override
        public int outputWidth() {
            return 1;
        }

        @Override
        public int outputHeight() {
            return 1;
        }

        @Override
        public void checkpoint() {}

        @Override
        public void claimReservedIntermediateBytes(long bytes) {}
    }
}
