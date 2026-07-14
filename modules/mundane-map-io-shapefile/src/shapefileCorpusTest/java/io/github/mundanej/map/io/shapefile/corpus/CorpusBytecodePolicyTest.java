package io.github.mundanej.map.io.shapefile.corpus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusBytecodePolicyTest {
    @Test
    void corpusClassesCannotUseNetworkOrChildProcessApis() throws Exception {
        Path output =
                Path.of(
                                CorpusBytecodePolicyTest.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toAbsolutePath()
                        .normalize();
        assertTrue(Files.isDirectory(output), "corpus test output must be a class directory");
        List<Path> classes;
        try (var paths = Files.walk(output)) {
            classes =
                    paths.filter(path -> path.toString().endsWith(".class"))
                            .filter(
                                    path ->
                                            !path.getFileName()
                                                    .toString()
                                                    .startsWith("CorpusBytecodePolicyTest"))
                            .sorted(Comparator.naturalOrder())
                            .toList();
        }
        assertFalse(classes.isEmpty(), "corpus output has no classes");
        for (Path path : classes) {
            verify(path);
        }
    }

    private static void verify(Path path) throws IOException {
        String constants = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        for (String owner : forbiddenOwners()) {
            assertFalse(constants.contains(owner), path + " references " + owner);
        }
        String runtimeOwner = String.join("", "java/lang/", "Runtime");
        String execName = String.join("", "ex", "ec");
        assertFalse(
                constants.contains(runtimeOwner) && constants.contains(execName),
                path + " references Runtime.exec");
    }

    private static List<String> forbiddenOwners() {
        return List.of(
                String.join("", "java/", "net/"),
                String.join("", "java/net/", "http/"),
                String.join("", "java/nio/channels/", "SocketChannel"),
                String.join("", "java/nio/channels/", "ServerSocketChannel"),
                String.join("", "java/nio/channels/", "DatagramChannel"),
                String.join("", "java/lang/", "ProcessBuilder"));
    }
}
