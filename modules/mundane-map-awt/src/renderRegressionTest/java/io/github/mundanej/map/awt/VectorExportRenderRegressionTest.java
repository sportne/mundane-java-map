package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.io.svg.SvgMapExports;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.junit.jupiter.api.Test;

class VectorExportRenderRegressionTest {
    private static final String FIXTURE = "/io/github/mundanej/map/awt/vector-export-review.svg";

    @Test
    void canonicalFixtureRetainsBroadVisualStructureWithoutPixelIdentity() throws Exception {
        byte[] actual = SvgMapExports.encode(reviewSnapshot());
        byte[] expected = readFixture();
        assertArrayEquals(expected, actual);

        String document = new String(actual, StandardCharsets.UTF_8);
        assertTrue(document.contains("width=\"240\" height=\"160\" viewBox=\"0 0 240 160\""));
        assertTrue(document.contains("fill=\"#f5f6f7\""));
        assertTrue(
                document.contains(
                        "M 20.0 20.0 L 110.0 20.0 L 110.0 70.0 L 20.0 70.0 Z"
                                + " M 45.0 35.0 L 85.0 35.0 L 85.0 55.0 L 45.0 55.0 Z"));
        assertTrue(document.contains("M 20.0 90.0 L 105.0 90.0 L 125.0 75.0"));
        assertTrue(document.contains("fill=\"#236eaf\""));
        assertTrue(document.contains("clip-path=\"url(#c1)\""));
        assertTrue(document.contains(">TRACK 7</text>"));
        assertTrue(document.indexOf("fill=\"#5ba36a\"") < document.indexOf("stroke=\"#344454\""));
        assertTrue(document.indexOf("stroke=\"#d04b3d\"") < document.indexOf(">TRACK 7</text>"));

        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        int clipPaths = 0;
        int paths = 0;
        int labels = 0;
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(actual));
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "clipPath" -> clipPaths++;
                    case "path" -> paths++;
                    case "text" -> labels++;
                    default -> {
                        // Closed grammar is asserted in the SVG module; count closeout landmarks.
                    }
                }
            }
        }
        reader.close();
        assertEquals(2, clipPaths);
        assertTrue(paths >= 8);
        assertEquals(1, labels);
    }

    private static VectorExportSnapshot reviewSnapshot() {
        SolidLineSymbol outline = SolidLineSymbol.of(stroke(Rgba.rgb(52, 68, 84), 2), 1);
        SolidFillSymbol land = SolidFillSymbol.of(Rgba.rgb(91, 163, 106), Optional.of(outline), 1);
        PolygonGeometry polygon =
                new PolygonGeometry(
                        CoordinateSequence.of(20, 20, 110, 20, 110, 70, 20, 70, 20, 20),
                        List.of(CoordinateSequence.of(45, 35, 85, 35, 85, 55, 45, 55, 45, 35)));

        var arrow = BuiltInMarkers.filledScreen(BuiltInMarker.ARROW, Rgba.rgb(52, 68, 84), 16, 1);
        SolidLineSymbol route =
                SolidLineSymbol.of(
                        stroke(Rgba.rgb(52, 68, 84), 3), Optional.empty(), Optional.of(arrow), 1);
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(35, 110, 175), 18, 1);
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.CROSS_DIAGONAL,
                        stroke(Rgba.rgb(208, 75, 61), 2),
                        new SymbolLength(12, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.SCREEN_RELATIVE,
                        Optional.of(outline),
                        0.9,
                        128);

        return VectorExportSnapshot.of(
                240,
                160,
                Rgba.rgb(245, 246, 247),
                new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                1,
                List.of(
                        new VectorExportSnapshot.Primitive(0, 0, polygon, land),
                        new VectorExportSnapshot.Primitive(
                                0,
                                1,
                                new LineStringGeometry(
                                        CoordinateSequence.of(20, 90, 105, 90, 125, 75)),
                                route),
                        new VectorExportSnapshot.Primitive(
                                0, 2, new PointGeometry(new Coordinate(145, 45)), marker),
                        new VectorExportSnapshot.Primitive(
                                0,
                                3,
                                new PolygonGeometry(
                                        CoordinateSequence.of(
                                                155, 90, 225, 90, 225, 140, 155, 140, 155, 90)),
                                hatch)),
                List.of(
                        new VectorExportSnapshot.Label(
                                "TRACK 7",
                                new LabelTextStyle(Rgba.rgb(35, 55, 75), LabelWeight.BOLD, 14),
                                130,
                                30,
                                64,
                                4)));
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static byte[] readFixture() throws IOException {
        try (InputStream input =
                VectorExportRenderRegressionTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "missing checked-in vector-export browser fixture");
            return input.readAllBytes();
        }
    }
}
