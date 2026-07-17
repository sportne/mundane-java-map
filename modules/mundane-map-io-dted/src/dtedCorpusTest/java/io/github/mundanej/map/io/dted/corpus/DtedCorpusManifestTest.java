package io.github.mundanej.map.io.dted.corpus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class DtedCorpusManifestTest {
    private static final Path ROOT =
            Path.of(System.getProperty("mundane.map.dted.corpus.root"))
                    .toAbsolutePath()
                    .normalize();

    private final java.util.ArrayList<Path> temporaryRoots = new java.util.ArrayList<>();

    @AfterEach
    void deleteTemporaryRoots() throws IOException {
        for (Path root : temporaryRoots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @Test
    void verifiesExactApprovedInventoryAndCaps() throws Exception {
        DtedCorpusManifest manifest = DtedCorpusManifest.loadAndVerify(ROOT);
        assertEquals(
                DtedCorpusManifest.MANIFEST_SHA256,
                DtedCorpusTestSupport.sha256(ROOT.resolve("manifest.tsv")));
        assertEquals(3, manifest.rows().size());
        assertEquals(
                List.of(
                        "gdal-zone-v-l0-complete",
                        "gdal-zone-v-l1-complete",
                        "gdal-zone-v-l2-partial"),
                manifest.datasets().stream().map(DtedCorpusManifest.Row::datasetId).toList());
        assertEquals(
                Set.of("GENERATED"),
                manifest.rows().stream()
                        .map(DtedCorpusManifest.Row::role)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(4_848_167L, manifest.resourceBytes());
        assertTrue(manifest.resourceBytes() <= DtedCorpusManifest.MAXIMUM_CORPUS_BYTES);
        for (var approved : DtedCorpusManifest.APPROVED_AUXILIARY_SHA256.entrySet()) {
            assertEquals(
                    approved.getValue(),
                    DtedCorpusTestSupport.sha256(ROOT.resolve(approved.getKey())));
        }
        assertDoesNotThrow(
                () ->
                        DtedCorpusManifest.assertAtMost(
                                DtedCorpusManifest.MAXIMUM_CORPUS_BYTES,
                                DtedCorpusManifest.MAXIMUM_CORPUS_BYTES,
                                "inclusive ceiling"));
        assertThrows(
                AssertionFailedError.class,
                () ->
                        DtedCorpusManifest.assertAtMost(
                                DtedCorpusManifest.MAXIMUM_CORPUS_BYTES + 1,
                                DtedCorpusManifest.MAXIMUM_CORPUS_BYTES,
                                "tree ceiling"));
        assertThrows(
                AssertionFailedError.class,
                () ->
                        DtedCorpusManifest.assertDatasetCount(
                                DtedCorpusManifest.MAXIMUM_DATASETS + 1));
    }

    @Test
    void rejectsUnknownRoleUnsafePathAndUnknownTag() throws Exception {
        assertManifestMutationRejected("\tGENERATED\t", "\tIMPORTED\t");
        assertManifestMutationRejected("\tGENERATED\t", "\tCURATED\t");
        assertManifestMutationRejected("\tGENERATED\t", "\tDERIVED\t");
        assertManifestMutationRejected("data/gdal-zone-v-l0-complete/w001/s81.dt0", "../s81.dt0");
        assertManifestMutationRejected("checksum,complete,level0,", "checksum,complete,imaginary,");
    }

    @Test
    void rejectsNoncanonicalLengthsAndPerFileCapExcess() throws Exception {
        assertManifestMutationRejected("\t8762\t", "\t08762\t");
        assertManifestMutationRejected("\t8762\t", "\t5242881\t");
    }

    @Test
    void rejectsMalformedUtf8() throws Exception {
        Path root = copyCorpus("malformed-utf8");
        Path manifest = root.resolve("manifest.tsv");
        byte[] bytes = Files.readAllBytes(manifest);
        bytes[0] = (byte) 0x80;
        Files.write(manifest, bytes);
        assertThrows(AssertionError.class, () -> DtedCorpusManifest.loadAndVerify(root));
    }

    @Test
    void rejectsDuplicateOrUnsortedRowsAndTags() throws Exception {
        assertManifestTextRejected(
                text -> {
                    List<String> lines = new java.util.ArrayList<>(text.lines().toList());
                    lines.add(lines.get(1));
                    return String.join("\n", lines) + '\n';
                },
                "duplicate-row");
        assertManifestTextRejected(
                text -> {
                    List<String> lines = new java.util.ArrayList<>(text.lines().toList());
                    String first = lines.get(1);
                    lines.set(1, lines.get(2));
                    lines.set(2, first);
                    return String.join("\n", lines) + '\n';
                },
                "unsorted-row");
        assertManifestMutationRejected(
                "checksum,complete,level0", "checksum,checksum,complete,level0");
        assertManifestMutationRejected("checksum,complete,level0", "complete,checksum,level0");
    }

    @Test
    void rejectsInconsistentGeneratedRoleFields() throws Exception {
        assertManifestMutationRejected(
                "licenses/BSD-3-Clause.txt\t\tgenerate:",
                "licenses/BSD-3-Clause.txt\tgdal-zone-v-l1-complete\tgenerate:");
        assertManifestMutationRejected("generate:recipes/gdal-3.13.0-zone-v.sh", "none");
    }

    @Test
    void rejectsDigestMismatchAndUnreferencedResource() throws Exception {
        Path digestRoot = copyCorpus("digest");
        Path data = digestRoot.resolve("data/gdal-zone-v-l0-complete/w001/s81.dt0");
        Path detached = data.resolveSibling("detached.dt0");
        Files.copy(data, detached);
        Files.move(detached, data, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        byte[] bytes = Files.readAllBytes(data);
        bytes[bytes.length - 1] ^= 1;
        Files.write(data, bytes);
        assertThrows(
                AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(digestRoot));

        Path extraRoot = copyCorpus("extra");
        Files.writeString(extraRoot.resolve("licenses/unreferenced.txt"), "not referenced\n");
        assertThrows(AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(extraRoot));
    }

    @Test
    void rejectsSameSizeApprovedRecipeOrLicenseReplacement() throws Exception {
        Path recipeRoot = copyCorpus("changed-recipe");
        mutateOneByte(recipeRoot.resolve("recipes/gdal-3.13.0-zone-v.sh"));
        assertThrows(
                AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(recipeRoot));

        Path licenseRoot = copyCorpus("changed-license");
        mutateOneByte(licenseRoot.resolve("licenses/BSD-3-Clause.txt"));
        assertThrows(
                AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(licenseRoot));
    }

    @Test
    void rejectsMissingLicenseRecipeAndExpectationReference() throws Exception {
        Path licenseRoot = copyCorpus("missing-license");
        Files.delete(licenseRoot.resolve("licenses/BSD-3-Clause.txt"));
        assertThrows(
                AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(licenseRoot));

        Path recipeRoot = copyCorpus("missing-recipe");
        Files.delete(recipeRoot.resolve("recipes/gdal-3.13.0-zone-v.sh"));
        assertThrows(
                AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(recipeRoot));

        assertManifestMutationRejected(
                "expect-gdal-zone-v-l0-complete", "expect-unknown-corpus-oracle");
    }

    private void assertManifestMutationRejected(String target, String replacement)
            throws Exception {
        assertManifestTextRejected(
                text -> {
                    assertTrue(text.contains(target), "mutation target must exist");
                    return text.replaceFirst(java.util.regex.Pattern.quote(target), replacement);
                },
                "mutation-" + Integer.toUnsignedString(target.hashCode()));
    }

    private void assertManifestTextRejected(
            java.util.function.UnaryOperator<String> mutation, String name) throws Exception {
        Path root = copyCorpus(name);
        Path manifest = root.resolve("manifest.tsv");
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        Files.writeString(manifest, mutation.apply(text), StandardCharsets.UTF_8);
        assertThrows(AssertionFailedError.class, () -> DtedCorpusManifest.loadAndVerify(root));
    }

    private static void mutateOneByte(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
    }

    private Path copyCorpus(String name) throws IOException {
        Path moduleDirectory = ROOT;
        for (int index = 0; index < 4; index++) {
            moduleDirectory = java.util.Objects.requireNonNull(moduleDirectory.getParent());
        }
        Path destination =
                moduleDirectory.resolve(
                        "build/dted-corpus-mutation-" + name + '-' + UUID.randomUUID());
        temporaryRoots.add(destination);
        try (var paths = Files.walk(ROOT)) {
            for (Path source : paths.toList()) {
                Path target = destination.resolve(ROOT.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (ROOT.relativize(source).startsWith("data")) {
                    Files.createLink(target, source);
                } else {
                    Files.copy(source, target);
                }
            }
        }
        return destination;
    }
}
