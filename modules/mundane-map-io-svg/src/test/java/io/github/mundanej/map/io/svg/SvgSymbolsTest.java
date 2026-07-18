package io.github.mundanej.map.io.svg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPathCommand;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvgSymbolsTest {
    private static final SourceIdentity ID = new SourceIdentity("svg-test", "SVG test");
    private static final MarkerPlacement PLACEMENT = MarkerPlacement.centeredScreen(20);

    @TempDir Path temporaryDirectory;

    @Test
    void importsBasicShapesAndOrderedComposition() {
        Symbol result =
                parse(
                        """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                          <rect x="1" y="1" width="4" height="4" fill="#ff0000"/>
                          <circle cx="7" cy="7" r="2" fill="#0000ff"/>
                        </svg>
                        """);

        CompositeSymbol composite = assertInstanceOf(CompositeSymbol.class, result);
        assertEquals(2, composite.children().size());
        VectorMarkerSymbol circle =
                assertInstanceOf(VectorMarkerSymbol.class, composite.children().get(1));
        assertEquals(6, circle.path().commandCount());
        assertEquals(VectorPathCommand.CUBIC_TO, circle.path().commandAt(1));
    }

    @Test
    void expandsPathShorthandAndSupportsStrokeOnlyOpenGeometry() {
        VectorMarkerSymbol symbol =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        parse(
                                """
                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                                  <path d="M1 1 h4 v4 q1 1 2 0 t1 -1" fill="none"
                                    stroke="#112233" stroke-width="1"
                                    stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                                """));

        assertEquals(5, symbol.path().commandCount());
        assertTrue(symbol.stroke().isPresent());
        assertEquals(0, symbol.fill().alpha());
    }

    @Test
    void appliesInheritedPaintAndAffineTransform() {
        VectorMarkerSymbol symbol =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        parse(
                                """
                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20">
                                  <g fill="#abcdef" transform="translate(2 3) scale(2)">
                                    <rect width="4" height="4"/>
                                  </g>
                                </svg>
                                """));
        assertEquals(2.0, symbol.path().ordinateAt(0));
        assertEquals(3.0, symbol.path().ordinateAt(1));
        assertEquals(0xab, symbol.fill().red());
    }

    @Test
    void rejectsReferencesUnsupportedProfilesAndUnrepresentablePaint() {
        assertFailure(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\">&amp;</svg>",
                "SVG_XML_INVALID");
        assertFailure(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><text>x</text></svg>",
                "SVG_PROFILE_UNSUPPORTED");
        assertFailure(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <line x1="1" y1="1" x2="9" y2="9"/>
                </svg>
                """,
                "SVG_VALUE_INVALID");
    }

    @Test
    void enforcesInputLimitsCancellationAndDefensiveInputOwnership() {
        byte[] bytes = svg("<rect width=\"1\" height=\"1\"/>");
        SourceException limited =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        bytes,
                                        PLACEMENT,
                                        SvgImportLimits.defaults()
                                                .withMaximumInputBytes(bytes.length - 1),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limited.terminal().code());

        AtomicBoolean cancelled = new AtomicBoolean(true);
        SourceException cancellation =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        bytes,
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        cancelled::get));
        assertEquals("SOURCE_CANCELLED", cancellation.terminal().code());
        assertEquals('s', bytes[1]);
    }

    @Test
    void rejectsBomMalformedUtf8DtdAndForeignNamespaces() {
        assertFailure(
                new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '<'}, "SVG_ENCODING_INVALID");
        assertFailure(new byte[] {(byte) 0xc3, 0x28}, "SVG_ENCODING_INVALID");
        assertFailure(
                "<!DOCTYPE svg><svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>",
                "SVG_XML_INVALID");
        assertFailure(
                "<svg xmlns=\"urn:no\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>",
                "SVG_PROFILE_UNSUPPORTED");
    }

    @Test
    void rejectsTheClosedUnsupportedXmlAndSvgProfile() {
        assertContext(
                document("<svg viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "nestedSvg");
        assertContext(
                document("<rect width=\"1\" height=\"1\" style=\"fill:#000000\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "attribute");
        assertContext(
                document("<path d=\"M1 1 A1 1 0 0 0 2 2 Z\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "pathCommand");
        assertContext(
                document("<rect width=\"1\" height=\"1\" fill=\"red\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "paint");
        assertContext(
                document("<rect width=\"1px\" height=\"1\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "unit");
        assertContext(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:x=\"urn:x\" viewBox=\"0 0 10 10\"><rect x:foreign=\"v\" width=\"1\" height=\"1\"/></svg>",
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "namespace");
        assertContext(
                "<s:svg xmlns:s=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
                        + "<s:rect width=\"1\" height=\"1\"/></s:svg>",
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "qualifiedName");
        assertContext(
                document("<image href=\"data:image/png;base64,AA==\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "element");
        assertContext(
                document("plain text"), "SVG_PROFILE_UNSUPPORTED", "construct", "characterData");
        assertContext(
                "<?xml-stylesheet href=\"local.css\"?>"
                        + document("<rect width=\"1\" height=\"1\"/>"),
                "SVG_XML_INVALID",
                "reason",
                "processingInstruction");
    }

    @Test
    void importsEverySupportedElementAndPathCommandFamily() {
        List<String> leaves =
                List.of(
                        "<path d=\"M1 1 L2 1 H3 V2 Q4 3 5 2 T7 2 C8 2 8 4 7 4 S5 6 4 4 Z\" fill-rule=\"evenodd\"/>",
                        "<rect x=\"1\" y=\"1\" width=\"2\" height=\"2\"/>",
                        "<circle cx=\"5\" cy=\"5\" r=\"1\"/>",
                        "<ellipse cx=\"5\" cy=\"5\" rx=\"2\" ry=\"1\"/>",
                        stroke("<line x1=\"2\" y1=\"2\" x2=\"8\" y2=\"8\""),
                        stroke("<polyline points=\"2,2 5,7 8,2\""),
                        "<polygon points=\"2,2 8,2 5,8\" fill-rule=\"evenodd\"/>");
        for (String leaf : leaves) {
            assertEquals(
                    io.github.mundanej.map.api.SymbolRole.MARKER, parse(document(leaf)).role());
        }
        Symbol relative =
                parse(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 30 30\"><g transform=\"translate(1 1)\"><path d=\"m1 1 l2 0 h1 v1 q1 1 2 0 t2 0 c1 0 1 1 2 1 s1 1 2 0 z\" fill-rule=\"evenodd\"/></g></svg>");
        assertEquals(io.github.mundanej.map.api.SymbolRole.MARKER, relative.role());
    }

    @Test
    void expandsRepeatedAndSmoothPathGroupsToExactCoordinates() {
        VectorMarkerSymbol symbol =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        parse(
                                document(
                                        "<path d=\"M1 1 2 1 3 1 V2 Q4 3 5 2 T7 2 "
                                                + "C8 2 8 4 7 4 S5 6 4 4 Z\" fill-rule=\"evenodd\"/>")));
        assertArrayEquals(
                new VectorPathCommand[] {
                    VectorPathCommand.MOVE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.QUADRATIC_TO,
                    VectorPathCommand.QUADRATIC_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CLOSE
                },
                symbol.path().toCommandArray());
        assertArrayEquals(
                new double[] {
                    1, 1, 2, 1, 3, 1, 3, 2, 4, 3, 5, 2, 6, 1, 7, 2, 8, 2, 8, 4, 7, 4, 6, 4, 5, 6, 4,
                    4
                },
                symbol.path().toOrdinateArray(),
                0.0);

        VectorMarkerSymbol relative =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        parse(
                                document(
                                        "<path d=\"m1 1 1 0 1 0 v1 q1 1 2 0 t2 0 "
                                                + "c1 0 1 2 0 2 s-2 2 -3 0 z\" fill-rule=\"evenodd\"/>")));
        assertArrayEquals(symbol.path().toOrdinateArray(), relative.path().toOrdinateArray(), 0.0);
    }

    @Test
    void usesApprovedFourCubicCircleApproximation() {
        VectorMarkerSymbol circle =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        parse(document("<circle cx=\"5\" cy=\"5\" r=\"2\"/>")));
        double kappa = 4.0 * (StrictMath.sqrt(2.0) - 1.0) / 3.0;
        assertArrayEquals(
                new VectorPathCommand[] {
                    VectorPathCommand.MOVE_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CLOSE
                },
                circle.path().toCommandArray());
        assertArrayEquals(
                new double[] {
                    7,
                    5,
                    7,
                    5 + 2 * kappa,
                    5 + 2 * kappa,
                    7,
                    5,
                    7,
                    5 - 2 * kappa,
                    7,
                    3,
                    5 + 2 * kappa,
                    3,
                    5,
                    3,
                    5 - 2 * kappa,
                    5 - 2 * kappa,
                    3,
                    5,
                    3,
                    5 + 2 * kappa,
                    3,
                    7,
                    5 - 2 * kappa,
                    7,
                    5
                },
                circle.path().toOrdinateArray(),
                1.0e-14);
    }

    @Test
    void supportsEveryTransformKindAndPreserveAspectMode() {
        for (String transform :
                List.of(
                        "matrix(1 0 0 1 1 1)",
                        "translate(1)",
                        "scale(0.5)",
                        "rotate(10 5 5)",
                        "skewX(5)",
                        "skewY(5)")) {
            assertInstanceOf(
                    VectorMarkerSymbol.class,
                    parse(
                            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-10 -10 30 30\" preserveAspectRatio=\"none\"><rect x=\"2\" y=\"2\" width=\"2\" height=\"2\" transform=\""
                                    + transform
                                    + "\"/></svg>"));
        }

        assertTransformedStart("matrix(2 0 0 3 4 5)", 6, 8);
        assertTransformedStart("translate(4 5)", 5, 6);
        assertTransformedStart("scale(2)", 2, 2);
        assertTransformedStart("rotate(90)", -1, 1);
        assertTransformedStart("skewX(45)", 2, 1);
        assertTransformedStart("skewY(45)", 1, 2);
        assertTransformedStart("translate(1 2) scale(2)", 3, 4);
    }

    @Test
    void rejectsEmptyMalformedTransformListsAndNonAsciiPaintDigits() {
        for (String transform :
                List.of(
                        "",
                        "translate(1),",
                        "translate(1),,scale(1)",
                        "translate(1)scale(1)",
                        "translate(1)\u2003scale(1)")) {
            assertContext(
                    document("<rect width=\"1\" height=\"1\" transform=\"" + transform + "\"/>"),
                    "SVG_VALUE_INVALID",
                    "field",
                    "transform");
        }
        assertContext(
                document("<rect width=\"1\" height=\"1\" fill=\"#ＦＦ0000\"/>"),
                "SVG_VALUE_INVALID",
                "field",
                "fill");
    }

    @Test
    void rejectsLeafChildrenAndSingularCombinedAncestorTransforms() {
        assertContext(
                document(
                        "<rect width=\"2\" height=\"2\"><circle cx=\"1\" cy=\"1\" r=\"1\"/></rect>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "element");
        assertContext(
                document(
                        "<path d=\"M1 1 L2 1 L2 2 Z\" fill-rule=\"evenodd\">"
                                + "<rect width=\"1\" height=\"1\"/></path>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "element");
        assertContext(
                document(
                        "<g transform=\"scale(1e-100)\"><g transform=\"scale(1e-100)\">"
                                + "<rect width=\"1\" height=\"1\"/></g></g>"),
                "SVG_VALUE_INVALID",
                "reason",
                "singular");
    }

    @Test
    void validatesXmlDeclarationsSeparatorsUnitsAndStablePrecedence() {
        assertInstanceOf(
                VectorMarkerSymbol.class,
                parse(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                                + document("<rect width=\"1\" height=\"1\"/>")));
        assertContext(
                "<?xml version=\"1.1\"?>" + document("<rect width=\"1\" height=\"1\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "xmlDeclaration");
        assertContext(
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                        + document("<rect width=\"1\" height=\"1\"/>"),
                "SVG_PROFILE_UNSUPPORTED",
                "construct",
                "xmlDeclaration");
        for (String viewBox : List.of(",0 0 10 10", "0 0 10 10,", "0,,0 10 10")) {
            assertFailure(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""
                            + viewBox
                            + "\"><rect width=\"1\" height=\"1\"/></svg>",
                    "SVG_VALUE_INVALID");
        }
        assertContext(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\",0 0 10 10\">"
                        + "<rect width=\"1\" height=\"1\"/></svg>",
                "SVG_VALUE_INVALID",
                "field",
                "viewBox");
        assertContext(
                document(stroke("<polyline points=\",0 0 1 1\"")),
                "SVG_VALUE_INVALID",
                "field",
                "points");
        assertContext(
                document("<rect width=\"1\" height=\"1\" transform=\"translate(,1)\"/>"),
                "SVG_VALUE_INVALID",
                "field",
                "transform");
        assertFailure(document("<rect width=\"1px\" height=\"1\"/>"), "SVG_PROFILE_UNSUPPORTED");
        for (String path :
                List.of("M1px 2 L2 2 Z", "M1 2px L2 2 Z", "M1em 2 L2 2 Z", "M1% 2 L2 2 Z")) {
            assertContext(
                    document("<path d=\"" + path + "\" fill-rule=\"evenodd\"/>"),
                    "SVG_PROFILE_UNSUPPORTED",
                    "construct",
                    "unit");
        }
        for (String path : List.of("M1 2P3 4", "M1 2p3 4")) {
            assertContext(
                    document("<path d=\"" + path + "\" fill-rule=\"evenodd\"/>"),
                    "SVG_PROFILE_UNSUPPORTED",
                    "construct",
                    "pathCommand");
        }
        String malformedPath = document("<path d=\"M1..2 3 L4 4 Z\" fill-rule=\"evenodd\"/>");
        assertContext(malformedPath, "SVG_VALUE_INVALID", "field", "d");
        assertContext(malformedPath, "SVG_VALUE_INVALID", "reason", "syntax");
        assertInstanceOf(
                VectorMarkerSymbol.class,
                parse(document("<path d=\"M1 1L2 1L2 2Z\" fill-rule=\"evenodd\"/>")));
        assertContext(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"bad\"><rect/></svg>",
                "SVG_VALUE_INVALID",
                "field",
                "viewBox");
        assertContext(document("<rect fill=\"bad\"/>"), "SVG_VALUE_INVALID", "field", "width");
    }

    @Test
    void mapsHostileTransformsAndStrokeOverflowToStableDiagnostics() {
        assertContext(
                document(
                        "<rect width=\"1\" height=\"1\" transform=\"scale(1e308) scale(1e308)\"/>"),
                "SVG_VALUE_INVALID",
                "field",
                "transform");
        assertContext(
                document(stroke("<line x1=\"0\" y1=\"5\" x2=\"10\" y2=\"5\"")),
                "SVG_VALUE_INVALID",
                "reason",
                "outsideViewBox");
        assertContext(
                document(
                        stroke(
                                "<line x1=\"2\" y1=\"2\" x2=\"8\" y2=\"8\" transform=\"scale(1e-20 1.0001e-20)\"")),
                "SVG_VALUE_INVALID",
                "reason",
                "strokeTransform");
        assertContext(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-1 -1 2 2\">"
                        + "<line x1=\"0\" y1=\"0\" x2=\"1e-308\" y2=\"-1e-308\" "
                        + "transform=\"matrix(1e308 0 1e308 1e-308 0 0)\" fill=\"none\" "
                        + "stroke=\"#112233\" stroke-width=\"1e-308\" "
                        + "stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>",
                "SVG_VALUE_INVALID",
                "reason",
                "strokeTransform");
    }

    @Test
    void enforcesAggregateElementCommandAndOwnedMemoryLimits() {
        SvgImportLimits elementLimits =
                SvgImportLimits.defaults().withMaximumPaintedOutputPaths(2).withMaximumElements(2);
        assertLimit(document("<g><rect width=\"1\" height=\"1\"/></g>"), elementLimits, "elements");

        SvgImportLimits commandLimits =
                SvgImportLimits.defaults()
                        .withMaximumDrawingSegments(2)
                        .withMaximumExpandedCommands(3);
        assertLimit(
                document(
                        stroke("<line x1=\"2\" y1=\"2\" x2=\"4\" y2=\"4\"")
                                + stroke("<line x1=\"6\" y1=\"6\" x2=\"8\" y2=\"8\"")),
                commandLimits,
                "expandedCommands");

        byte[] bytes =
                document("<rect width=\"1\" height=\"1\"/>").getBytes(StandardCharsets.UTF_8);
        long initialOwned = bytes.length * 3L + 256L;
        SvgImportLimits ownedLimits =
                SvgImportLimits.defaults().withMaximumOwnedBytes(initialOwned);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        bytes,
                                        PLACEMENT,
                                        ownedLimits,
                                        CancellationToken.none()));
        assertEquals("ownedBytes", failure.terminal().context().get("limit"));

        StringBuilder hostilePoints = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            hostilePoints.append(index % 10).append(',').append(index % 10).append(' ');
        }
        assertLimit(
                document(
                        "<polyline points=\""
                                + hostilePoints
                                + "\" fill=\"none\" stroke=\"#000000\" stroke-width=\"1\" "
                                + "stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"),
                SvgImportLimits.defaults()
                        .withMaximumDrawingSegments(2)
                        .withMaximumExpandedCommands(3),
                "expandedCommands");
    }

    @Test
    void acceptsExactAndRejectsOneOverForEveryStructuralLimit() {
        String rect = document("<rect width=\"1\" height=\"1\"/>");
        byte[] rectBytes = rect.getBytes(StandardCharsets.UTF_8);
        assertParses(rect, SvgImportLimits.defaults().withMaximumInputBytes(rectBytes.length));
        assertLimit(
                rect,
                SvgImportLimits.defaults().withMaximumInputBytes(rectBytes.length - 1),
                "inputBytes");

        SvgImportLimits elements =
                SvgImportLimits.defaults().withMaximumPaintedOutputPaths(1).withMaximumElements(2);
        assertParses(rect, elements);
        assertLimit(
                rect,
                SvgImportLimits.defaults().withMaximumPaintedOutputPaths(1).withMaximumElements(1),
                "elements");

        String nested = document("<g><g><rect width=\"1\" height=\"1\"/></g></g>");
        SvgImportLimits depth =
                SvgImportLimits.defaults()
                        .withMaximumTransformAncestorDepth(1)
                        .withMaximumElementDepth(4);
        assertParses(nested, depth);
        assertLimit(
                nested,
                SvgImportLimits.defaults()
                        .withMaximumTransformAncestorDepth(1)
                        .withMaximumElementDepth(3),
                "elementDepth");

        assertParses(rect, SvgImportLimits.defaults().withMaximumAttributes(3));
        assertLimit(rect, SvgImportLimits.defaults().withMaximumAttributes(2), "attributes");
        assertParses(rect, SvgImportLimits.defaults().withMaximumAttributeCharacters(9));
        assertLimit(
                rect,
                SvgImportLimits.defaults().withMaximumAttributeCharacters(8),
                "attributeCharacters");
        assertParses(
                rect,
                SvgImportLimits.defaults()
                        .withMaximumAttributeCharacters(9)
                        .withMaximumAggregateAttributeCharacters(11));
        assertLimit(
                rect,
                SvgImportLimits.defaults()
                        .withMaximumAttributeCharacters(9)
                        .withMaximumAggregateAttributeCharacters(10),
                "aggregateAttributeCharacters");

        String token = document("<rect width=\"1.234\" height=\"1\"/>");
        assertParses(token, SvgImportLimits.defaults().withMaximumNumberTokenCharacters(5));
        assertLimit(
                token,
                SvgImportLimits.defaults().withMaximumNumberTokenCharacters(4),
                "numberTokenCharacters");

        SvgImportLimits pathExact =
                SvgImportLimits.defaults()
                        .withMaximumDrawingSegments(4)
                        .withMaximumExpandedCommands(5);
        assertParses(rect, pathExact);
        assertLimit(
                rect,
                SvgImportLimits.defaults()
                        .withMaximumDrawingSegments(3)
                        .withMaximumExpandedCommands(4),
                "expandedCommands");
        assertLimit(
                rect,
                SvgImportLimits.defaults()
                        .withMaximumDrawingSegments(3)
                        .withMaximumExpandedCommands(5),
                "drawingSegments");

        String transforms =
                document(
                        "<g transform=\"translate(0)\"><g transform=\"scale(1)\">"
                                + "<rect width=\"1\" height=\"1\"/></g></g>");
        assertParses(
                transforms,
                SvgImportLimits.defaults()
                        .withMaximumTransformFunctions(2)
                        .withMaximumTransformAncestorDepth(2));
        assertLimit(
                transforms,
                SvgImportLimits.defaults()
                        .withMaximumTransformFunctions(1)
                        .withMaximumTransformAncestorDepth(2),
                "transformFunctions");
        assertLimit(
                transforms,
                SvgImportLimits.defaults()
                        .withMaximumTransformFunctions(2)
                        .withMaximumTransformAncestorDepth(1),
                "transformAncestorDepth");

        String two =
                document(
                        "<rect width=\"1\" height=\"1\"/><rect x=\"2\" width=\"1\" height=\"1\"/>");
        assertParses(two, SvgImportLimits.defaults().withMaximumPaintedOutputPaths(2));
        assertLimit(
                two,
                SvgImportLimits.defaults().withMaximumPaintedOutputPaths(1),
                "paintedOutputPaths");

        long exactOwned = minimumOwnedBytes(rectBytes);
        assertParses(rect, SvgImportLimits.defaults().withMaximumOwnedBytes(exactOwned));
        assertLimit(
                rect,
                SvgImportLimits.defaults().withMaximumOwnedBytes(exactOwned - 1),
                "ownedBytes");

        String transformed =
                document("<rect width=\"1\" height=\"1\" transform=\"translate(1 1)\"/>");
        byte[] transformedBytes = transformed.getBytes(StandardCharsets.UTF_8);
        long exactTransformOwned = minimumOwnedBytes(transformedBytes);
        assertParses(
                transformed, SvgImportLimits.defaults().withMaximumOwnedBytes(exactTransformOwned));
        assertLimit(
                transformed,
                SvgImportLimits.defaults().withMaximumOwnedBytes(exactTransformOwned - 1),
                "ownedBytes");
    }

    @Test
    void omitsQuantizedTransparentPaintAndPreservesInputOwnership() {
        assertFailure(
                document("<rect width=\"1\" height=\"1\" fill-opacity=\"0.001\"/>"),
                "SVG_EMPTY_GRAPHIC");
        byte[] bytes =
                document("<rect width=\"1\" height=\"1\"/>").getBytes(StandardCharsets.UTF_8);
        Symbol symbol = SvgSymbols.parse(ID, bytes, PLACEMENT);
        int hash = symbol.hashCode();
        java.util.Arrays.fill(bytes, (byte) 0);
        assertEquals(hash, symbol.hashCode());
    }

    @Test
    void sanitizesFileFailuresAndNeverResolvesExternalIdentifiers() throws Exception {
        Path missing = temporaryDirectory.resolve("secret-missing.svg");
        SourceException notFound =
                assertThrows(SourceException.class, () -> SvgSymbols.read(ID, missing, PLACEMENT));
        assertEquals("notFound", notFound.terminal().context().get("reason"));
        assertTrue(notFound.getCause() == null);

        Path canary = temporaryDirectory.resolve("must-not-read.txt");
        Files.writeString(canary, "unchanged");
        String external =
                "<!DOCTYPE svg SYSTEM \""
                        + canary.toUri()
                        + "\">"
                        + document("<rect width=\"1\" height=\"1\"/>");
        assertFailure(external, "SVG_XML_INVALID");
        assertEquals("unchanged", Files.readString(canary));

        Path supported = temporaryDirectory.resolve("supported.svg");
        Files.writeString(supported, document("<rect width=\"1\" height=\"1\"/>"));
        assertEquals(
                io.github.mundanej.map.api.SymbolRole.MARKER,
                SvgSymbols.read(ID, supported, PLACEMENT).role());
        byte[] supportedBytes = Files.readAllBytes(supported);
        long exactOwned = minimumOwnedBytes(supportedBytes);
        assertEquals(
                io.github.mundanej.map.api.SymbolRole.MARKER,
                SvgSymbols.read(
                                ID,
                                supported,
                                PLACEMENT,
                                SvgImportLimits.defaults().withMaximumOwnedBytes(exactOwned),
                                CancellationToken.none())
                        .role());
        assertLimitFromPath(
                supported,
                SvgImportLimits.defaults().withMaximumOwnedBytes(exactOwned - 1),
                "ownedBytes");

        try (ServerSocket trap =
                new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            trap.setSoTimeout(100);
            String remote =
                    "<!DOCTYPE svg SYSTEM \"http://127.0.0.1:"
                            + trap.getLocalPort()
                            + "/external.dtd\">"
                            + document("<rect width=\"1\" height=\"1\"/>");
            assertFailure(remote, "SVG_XML_INVALID");
            assertThrows(SocketTimeoutException.class, trap::accept);
        }
    }

    @Test
    void boundedPathReadingHandlesGrowthShrinkCleanupAndClosedFileSystems() throws Exception {
        byte[] bytes =
                document("<rect width=\"1\" height=\"1\"/>").getBytes(StandardCharsets.UTF_8);
        assertEquals(
                io.github.mundanej.map.api.SymbolRole.MARKER,
                SvgSymbols.readOpened(
                                ID,
                                new ByteArrayInputStream(bytes),
                                1,
                                PLACEMENT,
                                SvgImportLimits.defaults(),
                                CancellationToken.none())
                        .role());
        assertEquals(
                io.github.mundanej.map.api.SymbolRole.MARKER,
                SvgSymbols.readOpened(
                                ID,
                                new ByteArrayInputStream(bytes),
                                bytes.length + 10L,
                                PLACEMENT,
                                SvgImportLimits.defaults(),
                                CancellationToken.none())
                        .role());

        SourceException primary =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.readOpened(
                                        ID,
                                        new InputStream() {
                                            @Override
                                            public int read() throws IOException {
                                                throw new IOException("secret read path");
                                            }

                                            @Override
                                            public int read(byte[] target, int offset, int length)
                                                    throws IOException {
                                                throw new IOException("secret read path");
                                            }

                                            @Override
                                            public void close() throws IOException {
                                                throw new IOException("secret close path");
                                            }
                                        },
                                        0,
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("read", primary.terminal().context().get("operation"));
        assertEquals(1, primary.getSuppressed().length);
        SourceException cleanup =
                assertInstanceOf(SourceException.class, primary.getSuppressed()[0]);
        assertEquals("close", cleanup.terminal().context().get("operation"));
        assertTrue(primary.getCause() == null);
        assertTrue(cleanup.getCause() == null);

        InputStream closeOnlyFailure =
                new ByteArrayInputStream(bytes) {
                    @Override
                    public void close() throws IOException {
                        throw new IOException("secret close path");
                    }
                };
        SourceException closeOnly =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.readOpened(
                                        ID,
                                        closeOnlyFailure,
                                        bytes.length,
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("close", closeOnly.terminal().context().get("operation"));
        assertEquals("other", closeOnly.terminal().context().get("reason"));

        assertReadLifecycleReason(
                new NoSuchFileException("secret"), "read", "notFound", bytes, false);
        assertReadLifecycleReason(
                new AccessDeniedException("secret"), "read", "accessDenied", bytes, false);
        assertReadLifecycleReason(
                new NoSuchFileException("secret"), "close", "notFound", bytes, true);
        assertReadLifecycleReason(
                new AccessDeniedException("secret"), "close", "accessDenied", bytes, true);

        Path archive = temporaryDirectory.resolve("closed.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        Path closedPath;
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            closedPath = fileSystem.getPath("/marker.svg");
            Files.write(closedPath, bytes);
        }
        SourceException closed =
                assertThrows(
                        SourceException.class, () -> SvgSymbols.read(ID, closedPath, PLACEMENT));
        assertEquals("closed", closed.terminal().context().get("reason"));
        assertEquals("open", closed.terminal().context().get("operation"));
    }

    @Test
    void cancelsDuringLargePathAndBoundsDeterministicMutationOutcomes() {
        StringBuilder path = new StringBuilder("M1 1");
        for (int index = 0; index < 10_000; index++) {
            path.append(" L2 2");
        }
        AtomicInteger polls = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        document(
                                                        "<path d=\""
                                                                + path
                                                                + "\" fill=\"none\" stroke=\"#000000\" "
                                                                + "stroke-width=\"1\" stroke-linecap=\"round\" "
                                                                + "stroke-linejoin=\"round\"/>")
                                                .getBytes(StandardCharsets.UTF_8),
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        () -> polls.incrementAndGet() > 200));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());

        byte[] seed = document("<rect width=\"1\" height=\"1\"/>").getBytes(StandardCharsets.UTF_8);
        for (int iteration = 0; iteration < 128; iteration++) {
            byte[] mutated = seed.clone();
            int changes = 1 + iteration % 3;
            for (int change = 0; change < changes; change++) {
                int index = (iteration * 31 + change * 17) % mutated.length;
                mutated[index] = (byte) ((iteration * 13 + change * 19) & 127);
            }
            try {
                SvgSymbols.parse(ID, mutated, PLACEMENT);
            } catch (SourceException expected) {
                assertTrue(
                        expected.terminal().code().startsWith("SVG_")
                                || expected.terminal().code().startsWith("SOURCE_"));
            }
        }
    }

    @Test
    void pollsCancellationAfterDecodeAndAcrossLongTransformLists() {
        AtomicInteger decodePolls = new AtomicInteger();
        SourceException afterDecode =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        document("<rect width=\"1\" height=\"1\"/>")
                                                .getBytes(StandardCharsets.UTF_8),
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        () -> decodePolls.incrementAndGet() >= 2));
        assertEquals("SOURCE_CANCELLED", afterDecode.terminal().code());
        assertEquals(2, decodePolls.get());

        StringBuilder transforms = new StringBuilder();
        for (int index = 0; index < 4_096; index++) {
            transforms.append("translate(0) ");
        }
        AtomicInteger transformPolls = new AtomicInteger();
        SourceException duringTransforms =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        document(
                                                        "<g transform=\""
                                                                + transforms
                                                                + "\"><rect width=\"1\" height=\"1\"/></g>")
                                                .getBytes(StandardCharsets.UTF_8),
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        () -> transformPolls.incrementAndGet() >= 5));
        assertEquals("SOURCE_CANCELLED", duringTransforms.terminal().code());
        assertEquals(5, transformPolls.get());
    }

    @Test
    void honorsLateCancellationBeforeDefensiveSymbolConstructions() {
        String single = document("<rect width=\"1\" height=\"1\"/>");
        int singlePolls = cancellationPolls(single);
        assertCancelledAtPoll(single, singlePolls - 2);

        String composite =
                document(
                        "<rect width=\"1\" height=\"1\"/>"
                                + "<rect x=\"2\" width=\"1\" height=\"1\"/>");
        int compositePolls = cancellationPolls(composite);
        assertCancelledAtPoll(composite, compositePolls - 1);
    }

    private static Symbol parse(String value) {
        return SvgSymbols.parse(ID, value.getBytes(StandardCharsets.UTF_8), PLACEMENT);
    }

    private static void assertTransformedStart(String transform, double x, double y) {
        VectorMarkerSymbol symbol =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        parse(
                                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-20 -20 60 60\" preserveAspectRatio=\"none\">"
                                        + "<rect x=\"1\" y=\"1\" width=\"1\" height=\"1\" transform=\""
                                        + transform
                                        + "\"/></svg>"));
        assertEquals(x, symbol.path().ordinateAt(0), 1.0e-12);
        assertEquals(y, symbol.path().ordinateAt(1), 1.0e-12);
    }

    private static void assertReadLifecycleReason(
            IOException failure,
            String operation,
            String reason,
            byte[] validBytes,
            boolean failOnClose) {
        InputStream input =
                new FilterInputStream(new ByteArrayInputStream(validBytes)) {
                    @Override
                    public int read(byte[] target, int offset, int length) throws IOException {
                        if (!failOnClose) {
                            throw failure;
                        }
                        return super.read(target, offset, length);
                    }

                    @Override
                    public void close() throws IOException {
                        if (failOnClose) {
                            throw failure;
                        }
                        super.close();
                    }
                };
        SourceException mapped =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.readOpened(
                                        ID,
                                        input,
                                        validBytes.length,
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals(operation, mapped.terminal().context().get("operation"));
        assertEquals(reason, mapped.terminal().context().get("reason"));
        assertTrue(mapped.getCause() == null);
    }

    private static int cancellationPolls(String value) {
        AtomicInteger polls = new AtomicInteger();
        SvgSymbols.parse(
                ID,
                value.getBytes(StandardCharsets.UTF_8),
                PLACEMENT,
                SvgImportLimits.defaults(),
                () -> {
                    polls.incrementAndGet();
                    return false;
                });
        return polls.get();
    }

    private static void assertCancelledAtPoll(String value, int cancelAt) {
        AtomicInteger polls = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        value.getBytes(StandardCharsets.UTF_8),
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        () -> polls.incrementAndGet() >= cancelAt));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals(cancelAt, polls.get());
    }

    private static String document(String leaves) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
                + leaves
                + "</svg>";
    }

    private static String stroke(String prefix) {
        return prefix
                + " fill=\"none\" stroke=\"#112233\" stroke-width=\"1\" "
                + "stroke-linecap=\"round\" stroke-linejoin=\"round\"/>";
    }

    private static void assertLimit(String value, SvgImportLimits limits, String limit) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        value.getBytes(StandardCharsets.UTF_8),
                                        PLACEMENT,
                                        limits,
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
    }

    private static void assertParses(String value, SvgImportLimits limits) {
        SvgSymbols.parse(
                ID,
                value.getBytes(StandardCharsets.UTF_8),
                PLACEMENT,
                limits,
                CancellationToken.none());
    }

    private static void assertLimitFromPath(Path path, SvgImportLimits limits, String limit) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.read(
                                        ID, path, PLACEMENT, limits, CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
    }

    private static long minimumOwnedBytes(byte[] bytes) {
        long maximum = bytes.length * 3L + 256L;
        while (true) {
            try {
                SvgSymbols.parse(
                        ID,
                        bytes,
                        PLACEMENT,
                        SvgImportLimits.defaults().withMaximumOwnedBytes(maximum),
                        CancellationToken.none());
                return maximum;
            } catch (SourceException failure) {
                assertEquals("ownedBytes", failure.terminal().context().get("limit"));
                maximum = Long.parseLong(failure.terminal().context().get("requested"));
            }
        }
    }

    private static void assertContext(String value, String code, String key, String expectedValue) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID, value.getBytes(StandardCharsets.UTF_8), PLACEMENT));
        assertEquals(code, failure.terminal().code());
        assertEquals(expectedValue, failure.terminal().context().get(key));
    }

    private static byte[] svg(String content) {
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 2 2\">"
                        + content
                        + "</svg>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void assertFailure(String value, String code) {
        assertFailure(value.getBytes(StandardCharsets.UTF_8), code);
    }

    private static void assertFailure(byte[] value, String code) {
        SourceException failure =
                assertThrows(SourceException.class, () -> SvgSymbols.parse(ID, value, PLACEMENT));
        assertEquals(code, failure.terminal().code());
        assertEquals("svg", failure.terminal().location().orElseThrow().component().orElseThrow());
    }
}
