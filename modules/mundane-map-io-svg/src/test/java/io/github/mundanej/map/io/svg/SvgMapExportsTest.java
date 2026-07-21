package io.github.mundanej.map.io.svg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvgMapExportsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void encodesCanonicalSolidSliceDeterministically() throws Exception {
        VectorExportSnapshot snapshot = representativeSnapshot();

        byte[] first = SvgMapExports.encode(snapshot);
        byte[] second = SvgMapExports.encode(representativeSnapshot());
        String document = new String(first, StandardCharsets.UTF_8);

        assertArrayEquals(first, second);
        assertTrue(document.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"));
        assertTrue(
                document.contains(
                        "<path d=\"M 9.0 9.0 L 11.0 9.0 L 10.0 11.0 Z\" fill=\"#c81428\" fill-rule=\"evenodd\"/>"));
        assertTrue(
                document.contains(
                        "<path d=\"M 1.0 2.0 L 3.0 4.0\" fill=\"none\" stroke=\"#0a141e\" stroke-width=\"2.0\""));
        assertTrue(
                document.contains(
                        "<path d=\"M 20.0 20.0 L 30.0 20.0 L 25.0 30.0 Z\""
                                + " fill=\"#46505a\" fill-rule=\"evenodd\""
                                + " fill-opacity=\"0.5\"/>"));
        assertTrue(document.contains(">A &amp; &lt;label&gt;</text>"));
        assertTrue(document.endsWith("</svg>\n"));
        assertFalse(document.contains("metadata"));
        assertClosedGrammar(first);
    }

    @Test
    void encodesMultipartCompositeEndpointsAndHatchesInStablePaintOrder() {
        VectorMarkerSymbol marker =
                VectorMarkerSymbol.filledScreen(
                        VectorPath.builder().moveTo(0, 0).lineTo(2, 1).lineTo(0, 2).close().build(),
                        new Envelope(0, 0, 2, 2),
                        Rgba.rgb(200, 10, 20),
                        4,
                        1);
        SolidLineSymbol endpointLine =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(10, 20, 30), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        Optional.of(marker),
                        Optional.of(marker),
                        1);
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.CROSS_DIAGONAL,
                        new SymbolStroke(
                                Rgba.rgb(30, 40, 50), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                        new SymbolLength(5, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.SCREEN_RELATIVE,
                        Optional.of(SolidLineSymbol.of(stroke(), 1)),
                        0.75,
                        100);
        PolygonGeometry first =
                new PolygonGeometry(
                        CoordinateSequence.of(20, 20, 40, 20, 40, 40, 20, 40, 20, 20),
                        List.of(CoordinateSequence.of(25, 25, 30, 25, 30, 30, 25, 30, 25, 25)));
        PolygonGeometry second =
                new PolygonGeometry(CoordinateSequence.of(50, 20, 60, 20, 60, 30, 50, 30, 50, 20));
        VectorExportSnapshot snapshot =
                VectorExportSnapshot.of(
                        100,
                        80,
                        Rgba.rgb(255, 255, 255),
                        new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                        1,
                        List.of(
                                new VectorExportSnapshot.Primitive(
                                        0,
                                        0,
                                        new MultiPointGeometry(CoordinateSequence.of(5, 5, 10, 5)),
                                        CompositeSymbol.of(List.of(marker), 0.5)),
                                new VectorExportSnapshot.Primitive(
                                        0,
                                        1,
                                        MultiLineStringGeometry.of(
                                                CoordinateSequence.of(5, 10, 15, 10, 5, 15, 15, 15),
                                                new int[] {0, 2, 4}),
                                        endpointLine),
                                new VectorExportSnapshot.Primitive(
                                        0,
                                        2,
                                        MultiPolygonGeometry.ofPolygons(List.of(first, second)),
                                        hatch)),
                        List.of());

        String document = new String(SvgMapExports.encode(snapshot), StandardCharsets.UTF_8);

        assertTrue(document.contains("<clipPath id=\"c1\""));
        assertTrue(document.contains("<clipPath id=\"c2\""));
        assertTrue(document.contains("clip-rule=\"evenodd\""));
        assertTrue(document.contains("clip-path=\"url(#c1)\""));
        assertTrue(document.contains("clip-path=\"url(#c2)\""));
        assertTrue(
                document.indexOf("M 5.0 10.0 L 15.0 10.0")
                        < document.indexOf("M 5.0 15.0 L 15.0 15.0"));
        assertTrue(document.contains("fill-opacity=\"0.5\""));
        assertTrue(document.contains("stroke-opacity=\"0.75\""));
    }

    @Test
    void enforcesLimitsAndCancellationWithoutPublishingPartialBytes() {
        SvgExportException limited =
                assertThrows(
                        SvgExportException.class,
                        () ->
                                SvgMapExports.encode(
                                        representativeSnapshot(),
                                        SvgExportLimits.defaults().withMaximumElements(2)));
        assertEquals("SVG_EXPORT_LIMIT_EXCEEDED", limited.problem().code());
        assertEquals("elements", limited.problem().context().get("limit"));

        SvgExportException cancelled =
                assertThrows(
                        SvgExportException.class,
                        () ->
                                SvgMapExports.encode(
                                        representativeSnapshot(),
                                        SvgExportLimits.defaults(),
                                        () -> true));
        assertEquals("SVG_EXPORT_CANCELLED", cancelled.problem().code());
    }

    @Test
    void atomicallyWritesAndPreservesExistingTargetOnEncodeFailure() throws Exception {
        Path target = temporaryDirectory.resolve("picture.svg");
        Files.writeString(target, "existing", StandardCharsets.UTF_8);

        assertThrows(
                SvgExportException.class,
                () ->
                        SvgMapExports.writeAtomically(
                                target,
                                representativeSnapshot(),
                                SvgExportLimits.defaults().withMaximumElements(1)));
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));

        SvgMapExports.writeAtomically(target, representativeSnapshot());
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).endsWith("</svg>\n"));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void mapsExtremeFramesAndSymbolTransformsWithCancellationPrecedence() {
        VectorExportSnapshot extremeFrame =
                VectorExportSnapshot.of(
                        10,
                        10,
                        Rgba.TRANSPARENT,
                        new VectorExportSnapshot.ViewFrame(1.0e200, 0, new Coordinate(0, 0)),
                        0,
                        List.of(),
                        List.of());
        SvgExportException cancelled =
                assertThrows(
                        SvgExportException.class,
                        () ->
                                SvgMapExports.encode(
                                        extremeFrame, SvgExportLimits.defaults(), () -> true));
        assertEquals("SVG_EXPORT_CANCELLED", cancelled.problem().code());

        SvgExportException frameFailure =
                assertThrows(SvgExportException.class, () -> SvgMapExports.encode(extremeFrame));
        assertEquals("SVG_EXPORT_VALUE_INVALID", frameFailure.problem().code());
        assertEquals(
                List.of("field", "reason"),
                frameFailure.problem().context().keySet().stream().toList());

        MarkerPlacement hugePlacement =
                new MarkerPlacement(
                        SymbolSize.square(1.0e300, SymbolUnit.MAP_UNIT),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        VectorMarkerSymbol hugeMarker =
                VectorMarkerSymbol.of(
                        VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build(),
                        new Envelope(0, 0, 1, 1),
                        Rgba.rgb(1, 2, 3),
                        Optional.empty(),
                        hugePlacement,
                        1);
        VectorExportSnapshot overflow =
                VectorExportSnapshot.of(
                        10,
                        10,
                        Rgba.TRANSPARENT,
                        new VectorExportSnapshot.ViewFrame(1.0e100, 0, new Coordinate(0, 0)),
                        1,
                        List.of(
                                new VectorExportSnapshot.Primitive(
                                        0, 0, new PointGeometry(new Coordinate(1, 1)), hugeMarker)),
                        List.of());
        SvgExportException symbolFailure =
                assertThrows(SvgExportException.class, () -> SvgMapExports.encode(overflow));
        assertEquals("SVG_EXPORT_VALUE_INVALID", symbolFailure.problem().code());
        assertEquals("symbolTransform", symbolFailure.problem().context().get("field"));
    }

    @Test
    void rejectsAReplacedTemporaryByFileIdentity() throws Exception {
        Path target = temporaryDirectory.resolve("identity.svg");
        Files.writeString(target, "existing", StandardCharsets.UTF_8);
        ReplacingOutputAccess access = new ReplacingOutputAccess();

        SvgExportException failure =
                assertThrows(
                        SvgExportException.class,
                        () ->
                                SvgAtomicFiles.write(
                                        target,
                                        SvgMapExports.encode(representativeSnapshot()),
                                        () -> false,
                                        access));

        assertEquals("SVG_EXPORT_IO_FAILED", failure.problem().code());
        assertEquals("temporary", failure.problem().context().get("operation"));
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(access.temporary));
    }

    private static VectorExportSnapshot representativeSnapshot() {
        VectorMarkerSymbol marker =
                VectorMarkerSymbol.filledScreen(
                        VectorPath.builder().moveTo(0, 0).lineTo(2, 0).lineTo(1, 2).close().build(),
                        new Envelope(0, 0, 2, 2),
                        Rgba.rgb(200, 20, 40),
                        2,
                        1);
        SymbolStroke stroke =
                new SymbolStroke(
                        Rgba.rgb(10, 20, 30), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL));
        SolidLineSymbol line = SolidLineSymbol.of(stroke, 1);
        SolidFillSymbol fill =
                SolidFillSymbol.of(new Rgba(70, 80, 90, 255), Optional.of(line), 0.5);
        List<VectorExportSnapshot.Primitive> primitives =
                List.of(
                        new VectorExportSnapshot.Primitive(
                                0, 0, new PointGeometry(new Coordinate(10, 10)), marker),
                        new VectorExportSnapshot.Primitive(
                                0,
                                1,
                                new LineStringGeometry(CoordinateSequence.of(1, 2, 3, 4)),
                                line),
                        new VectorExportSnapshot.Primitive(
                                0,
                                2,
                                new PolygonGeometry(
                                        CoordinateSequence.of(20, 20, 30, 20, 25, 30, 20, 20)),
                                fill));
        VectorExportSnapshot.Label label =
                new VectorExportSnapshot.Label(
                        "A & <label>",
                        new LabelTextStyle(new Rgba(1, 2, 3, 128), LabelWeight.BOLD, 12),
                        5,
                        40,
                        50,
                        3);
        return VectorExportSnapshot.of(
                100,
                50,
                Rgba.rgb(240, 241, 242),
                new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                1,
                primitives,
                List.of(label));
    }

    private static SymbolStroke stroke() {
        return new SymbolStroke(Rgba.rgb(70, 80, 90), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL));
    }

    private static void assertClosedGrammar(byte[] bytes) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
        List<String> allowed = List.of("svg", "defs", "clipPath", "rect", "g", "path", "text");
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                assertTrue(allowed.contains(reader.getLocalName()));
            }
        }
        reader.close();
    }

    private static final class ReplacingOutputAccess implements SvgAtomicFiles.OutputAccess {
        private final SvgAtomicFiles.OutputAccess delegate = SvgAtomicFiles.OutputAccess.JDK;
        private Path temporary;
        private boolean replaced;

        @Override
        public Path realParent(Path parent) throws IOException {
            return delegate.realParent(parent);
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            if (!replaced && temporary != null && temporary.equals(path)) {
                Files.delete(path);
                Files.createDirectory(path);
                replaced = true;
            }
            return delegate.attributes(path);
        }

        @Override
        public SvgAtomicFiles.Temporary createTemporary(Path parent) throws IOException {
            SvgAtomicFiles.Temporary result = delegate.createTemporary(parent);
            temporary = result.path();
            return result;
        }

        @Override
        public void moveAtomic(Path temporaryPath, Path target) throws IOException {
            delegate.moveAtomic(temporaryPath, target);
        }

        @Override
        public void deleteTemporary(Path temporaryPath) throws IOException {
            delegate.deleteTemporary(temporaryPath);
        }
    }
}
