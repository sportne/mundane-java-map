package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.kml.KmlFiles;
import io.github.mundanej.map.io.kml.KmlOpenOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeKmlSmokeTest {
    @Test
    void sharedScenarioQueriesRendersWarnsDiagnosesAndCleansUp() {
        Path directory;
        NativeKmlSmokeScenario.Result result;
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openKml()) {
            Path path = workspace.kmlPath();
            directory = path.getParent();
            result = NativeKmlSmokeScenario.run(path);
            assertTrue(Files.isDirectory(directory));
        }

        assertFalse(Files.exists(directory));
        assertEquals(3, result.records());
        assertTrue(result.coloredPixels() >= 80);
        assertTrue(result.sourceClosed());
        assertTrue(
                result.warnings().entries().stream()
                        .anyMatch(entry -> entry.code().equals("KML_ALTITUDE_IGNORED")));
        var terminal = result.malformed().entries().getLast();
        assertEquals("KML_XML_INVALID", terminal.code());
        assertEquals(Map.of("reason", "syntax"), terminal.context());
    }

    @Test
    void fixedResourceMatchesLiteralLengthAndHash() throws Exception {
        NativeKmlResources.Entry entry = NativeKmlResources.VALID;
        byte[] bytes = resource(entry.resourceName());
        assertEquals(entry.length(), bytes.length);
        assertEquals(entry.sha256(), hex(sha256().digest(bytes)));
        assertEquals(1, NativeKmlResources.INVENTORY.size());
    }

    @Test
    void preTransferQueryFailureClosesSourceAndPreservesPrimaryFailure() {
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openKml()) {
            Path path = workspace.kmlPath();
            FeatureSource delegate =
                    KmlFiles.open(
                            path,
                            new SourceIdentity("native-kml-seam", "Native KML seam"),
                            KmlOpenOptions.defaults(),
                            CancellationToken.none());
            FailingQuerySource source = new FailingQuerySource(delegate);
            IllegalStateException failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> NativeKmlSmokeScenario.runOpened(source, path));
            assertEquals("injected query failure", failure.getMessage());
            assertTrue(source.isClosed());
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = NativeKmlSmokeTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new AssertionError("missing resource " + name);
            }
            return input.readAllBytes();
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static final class FailingQuerySource implements FeatureSource {
        private final FeatureSource delegate;

        FailingQuerySource(FeatureSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            throw new IllegalStateException("injected query failure");
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
