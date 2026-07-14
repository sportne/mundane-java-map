package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrjGrammarTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @TempDir Path directory;
    private int fixture;

    @Test
    void acceptsTheBoundedWktGrammarWithoutAssigningGeneralSemantics() throws Exception {
        for (String value :
                List.of(
                        "AXIS[\"X\",EAST]",
                        "A[.5,5.,+5,-.5,1e3,-2.5E-2,_DIRECTION]",
                        "A[\"embedded \"\" quote\"]",
                        "A[\"﻿ is content here\"]",
                        "A[\"€ and 💩\"]",
                        "A[B[C[0]],D_1]")) {
            try (FeatureSource source = open(value.getBytes(StandardCharsets.UTF_8))) {
                assertTrue(source.metadata().crs().orElseThrow().definition().isEmpty(), value);
                assertEquals(
                        value,
                        source.metadata().crs().orElseThrow().retainedDefinition().orElseThrow(),
                        value);
                assertEquals(
                        "SHAPEFILE_PRJ_CRS_UNRECOGNIZED",
                        source.openingDiagnostics().entries().get(2).code(),
                        value);
            }
        }
    }

    @Test
    void reportsTheFirstGrammarFailureOrEofAtAnExactByte() throws Exception {
        List<SyntaxCase> cases =
                List.of(
                        new SyntaxCase("invalid punctuation", "A[(0)]", 2),
                        new SyntaxCase("empty arguments", "A[]", 2),
                        new SyntaxCase("missing comma", "A[0 1]", 4),
                        new SyntaxCase("missing argument", "A[0,]", 4),
                        new SyntaxCase("trailing token", "A[0]X", 4),
                        new SyntaxCase("multiple roots", "A[0] B[1]", 5),
                        new SyntaxCase("missing bracket eof", "A[0", 3),
                        new SyntaxCase("unterminated quote eof", "A[\"x", 4),
                        new SyntaxCase("exponent needs digit", "A[1e+]", 5),
                        new SyntaxCase("exponent eof", "A[1e+", 5),
                        new SyntaxCase("semicolon", "A[0;1]", 3),
                        new SyntaxCase("two commas", "A[0,,1]", 4),
                        new SyntaxCase("parenthesis form", "A(0)", 1));

        for (SyntaxCase value : cases) {
            assertInvalid(
                    value.text().getBytes(StandardCharsets.UTF_8),
                    "syntax",
                    value.offset(),
                    value.name());
        }

        assertInvalid(PrjFixtures.withBom("A[]"), "syntax", 5, "BOM-adjusted syntax offset");
        assertInvalid(
                "A[\"\u0001\"]".getBytes(StandardCharsets.UTF_8), "syntax", 3, "quoted control");
        assertInvalid(new byte[] {'A', '[', '"', 0x7f, '"', ']'}, "syntax", 3, "quoted delete");
    }

    @Test
    void rejectsMalformedUtf8AtTheLeadByteWithoutReplacingInput() throws Exception {
        List<BytesCase> cases =
                List.of(
                        new BytesCase("bad continuation", bytes(0xc3, 0x28), 3),
                        new BytesCase("truncated two byte", bytes(0xc3), 3),
                        new BytesCase("truncated three byte after lead", bytes(0xe2), 3),
                        new BytesCase(
                                "truncated three byte after first continuation",
                                bytes(0xe2, 0x82),
                                3),
                        new BytesCase("truncated four byte after lead", bytes(0xf0), 3),
                        new BytesCase(
                                "truncated four byte after first continuation",
                                bytes(0xf0, 0x9f),
                                3),
                        new BytesCase("isolated continuation", bytes(0x80), 3),
                        new BytesCase("illegal lead", bytes(0xff), 3),
                        new BytesCase("overlong", bytes(0xc0, 0xaf), 3),
                        new BytesCase("surrogate", bytes(0xed, 0xa0, 0x80), 3),
                        new BytesCase("above unicode", bytes(0xf4, 0x90, 0x80, 0x80), 3),
                        new BytesCase(
                                "truncated four byte after second continuation",
                                bytes(0xf0, 0x9f, 0x92),
                                3));

        for (BytesCase value : cases) {
            assertInvalid(quoted(value.bytes()), "encoding", value.offset(), value.name());
        }
        assertInvalid(new byte[] {(byte) 0xef, (byte) 0xbb}, "encoding", 0, "incomplete BOM");
    }

    @Test
    void acceptsExactlySixteenNestingLevelsAndRejectsTheSeventeenthBracket() throws Exception {
        try (FeatureSource source = open(PrjFixtures.utf8(PrjFixtures.nested(16)))) {
            assertEquals(
                    "SHAPEFILE_PRJ_CRS_UNRECOGNIZED",
                    source.openingDiagnostics().entries().get(2).code());
        }
        assertInvalid(PrjFixtures.utf8(PrjFixtures.nested(17)), "nesting", 33, "depth 17");
    }

    @Test
    void acceptsExactlyFiveHundredTwelveTokensAndRejectsTheNextToken() throws Exception {
        String boundary = PrjFixtures.tokenBoundary();
        assertEquals(512, boundary.length());
        try (FeatureSource source = open(PrjFixtures.utf8(boundary))) {
            assertEquals(
                    "SHAPEFILE_PRJ_CRS_UNRECOGNIZED",
                    source.openingDiagnostics().entries().get(2).code());
        }
        assertInvalid(PrjFixtures.utf8(boundary + 'X'), "tokens", 512, "token 513");
    }

    @Test
    void matcherChecksCancellationWithinLongNumericTokens() {
        byte[] input =
                PrjFixtures.utf8(
                        PrjFixtures.EPSG_4326.replace("6378137", "0".repeat(8192) + "6378137"));
        AtomicBoolean matching = new AtomicBoolean();
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> matching.get() && checks.incrementAndGet() >= 3;
        PrjTokenizer tokenizer = new PrjTokenizer("source", input, 0, cancellation);
        tokenizer.scan();
        matching.set(true);

        SourceException failure =
                assertThrows(SourceException.class, () -> PrjRecognizer.recognize(tokenizer));

        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertTrue(checks.get() >= 3);
    }

    @Test
    void tokenizerChecksCancellationWithinLongNumericTokens() {
        byte[] input = PrjFixtures.utf8("A[" + "1".repeat(8192) + "]");
        AtomicInteger checks = new AtomicInteger();
        PrjTokenizer tokenizer =
                new PrjTokenizer("source", input, 0, () -> checks.incrementAndGet() >= 2);

        SourceException failure = assertThrows(SourceException.class, tokenizer::scan);

        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertTrue(checks.get() >= 2);
    }

    private void assertInvalid(byte[] prj, String reason, long offset, String description)
            throws Exception {
        SourceException failure = assertThrows(SourceException.class, () -> open(prj), description);
        assertEquals("SHAPEFILE_PRJ_INVALID", failure.terminal().code(), description);
        assertEquals(
                "Shapefile coordinate-reference metadata is invalid",
                failure.terminal().message(),
                description);
        assertEquals(reason, failure.terminal().context().get("reason"), description);
        assertEquals(
                "prj",
                failure.terminal().location().orElseThrow().component().orElseThrow(),
                description);
        assertEquals(
                offset,
                failure.terminal().location().orElseThrow().byteOffset().orElseThrow(),
                description);
        assertEquals("SHAPEFILE_SHX_MISSING", failure.report().entries().get(0).code());
        assertEquals("SHAPEFILE_DBF_MISSING", failure.report().entries().get(1).code());
        assertEquals(failure.terminal(), failure.report().entries().get(2));
    }

    private FeatureSource open(byte[] prj) throws Exception {
        String stem = "grammar-" + fixture++;
        Path shp = directory.resolve(stem + ".shp");
        Files.write(shp, ShpFixtures.file(0, 0, 0, 0, 0));
        Files.write(directory.resolve(stem + ".prj"), prj);
        return Shapefiles.open(IDENTITY, shp, ShapefileOpenOptions.defaults());
    }

    private static byte[] quoted(byte[] encodedContent) {
        byte[] result = new byte[encodedContent.length + 5];
        result[0] = 'A';
        result[1] = '[';
        result[2] = '"';
        System.arraycopy(encodedContent, 0, result, 3, encodedContent.length);
        result[result.length - 2] = '"';
        result[result.length - 1] = ']';
        return result;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private record SyntaxCase(String name, String text, long offset) {}

    private static final class BytesCase {
        private final String name;
        private final byte[] bytes;
        private final long offset;

        private BytesCase(String name, byte[] bytes, long offset) {
            this.name = name;
            this.bytes = bytes;
            this.offset = offset;
        }

        String name() {
            return name;
        }

        byte[] bytes() {
            return bytes;
        }

        long offset() {
            return offset;
        }
    }
}
