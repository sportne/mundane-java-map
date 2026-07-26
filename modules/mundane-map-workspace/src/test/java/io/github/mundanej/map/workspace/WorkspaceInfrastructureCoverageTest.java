package io.github.mundanej.map.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceInfrastructureCoverageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void jdkOutputAccessCreatesForcesMovesInspectsAndDeletesPrivateTemporary() throws Exception {
        WorkspaceOutputAccess access = WorkspaceOutputAccess.JDK;
        assertEquals(temporaryDirectory.toRealPath(), access.realParent(temporaryDirectory));
        assertTrue(access.attributes(temporaryDirectory).isDirectory());
        assertEquals(null, access.attributes(temporaryDirectory.resolve("missing")));

        WorkspaceTemporary temporary = access.createTemporary(temporaryDirectory);
        assertTrue(Files.isRegularFile(temporary.path()));
        assertTrue(temporary.path().getFileName().toString().startsWith(".mmap-"));
        assertEquals(3, temporary.channel().write(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        temporary.channel().force(true);
        temporary.channel().close();

        Path target = temporaryDirectory.resolve("workspace.mmap.xml");
        access.moveAtomic(temporary.path(), target);
        assertEquals(3, Files.size(target));
        access.deleteTemporary(temporary.path());
        assertFalse(Files.exists(temporary.path()));
    }

    @Test
    void workspaceSymbolBoundaryAcceptsOnlyTheRequiredRole() {
        Symbol marker = new TestSymbol(SymbolRole.MARKER);
        assertSame(marker, WorkspaceSymbols.requireRole(marker, SymbolRole.MARKER));
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkspaceSymbols.requireRole(marker, SymbolRole.LINE));
        assertThrows(
                NullPointerException.class,
                () -> WorkspaceSymbols.requireRole(null, SymbolRole.LINE));
    }

    @Test
    void failedTemporaryValidationClosesTheChannelAndDeletesTheCandidate() throws Exception {
        Path candidate = temporaryDirectory.resolve("failed.tmp");
        FileChannel channel =
                FileChannel.open(
                        candidate, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        invokePrivate(
                WorkspaceOutputAccess.class,
                "cleanupFailedCreation",
                new Class<?>[] {Path.class, FileChannel.class, java.io.IOException.class},
                candidate,
                channel,
                new java.io.IOException("primary"));

        assertFalse(channel.isOpen());
        assertFalse(Files.exists(candidate));
    }

    private record TestSymbol(SymbolRole role) implements Symbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("test.workspace");
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private static void invokePrivate(
            Class<?> owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
        }
    }
}
