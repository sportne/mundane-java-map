package io.github.mundanej.map.io.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationToken;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvgAtomicFilesTest {
    @TempDir Path directory;

    @Test
    void preservesPrimaryFailureAndSuppressesCloseThenDelete() throws Exception {
        Path target = existingTarget("suppressed.svg");
        FaultingAccess access = new FaultingAccess(Fault.WRITE, Fault.CLOSE, Fault.DELETE);

        SvgExportException failure =
                assertThrows(
                        SvgExportException.class,
                        () -> SvgAtomicFiles.write(target, new byte[8], () -> false, access));

        assertEquals("write", failure.problem().context().get("operation"));
        assertEquals(2, failure.getSuppressed().length);
        assertEquals(
                "close",
                ((SvgExportException) failure.getSuppressed()[0])
                        .problem()
                        .context()
                        .get("operation"));
        assertEquals(
                "delete",
                ((SvgExportException) failure.getSuppressed()[1])
                        .problem()
                        .context()
                        .get("operation"));
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(1, access.closeCount);
        assertFalse(Files.exists(access.temporary));
    }

    @Test
    void mapsEveryIndependentFileFailureAndNeverReplacesTheTarget() throws Exception {
        assertFailure(Fault.PREFLIGHT, "preflight");
        assertFailure(Fault.TEMPORARY, "temporary");
        assertFailure(Fault.WRITE, "write");
        assertFailure(Fault.FORCE, "force");
        assertFailure(Fault.CLOSE, "close");
        assertFailure(Fault.MOVE, "move");

        Path target = existingTarget("atomic.svg");
        FaultingAccess access = new FaultingAccess(Fault.ATOMIC_MOVE);
        SvgExportException unsupported =
                assertThrows(
                        SvgExportException.class,
                        () -> SvgAtomicFiles.write(target, new byte[8], () -> false, access));
        assertEquals("SVG_EXPORT_ATOMIC_MOVE_UNSUPPORTED", unsupported.problem().code());
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(access.temporary));
    }

    @Test
    void keepsIoFailurePrimaryAndSuppressesCancellationObservedAfterward() throws Exception {
        Path target = existingTarget("failure-cancellation.svg");
        FaultingAccess access = new FaultingAccess(Fault.WRITE);
        AtomicInteger polls = new AtomicInteger();

        SvgExportException failure =
                assertThrows(
                        SvgExportException.class,
                        () ->
                                SvgAtomicFiles.write(
                                        target,
                                        new byte[8],
                                        () -> polls.incrementAndGet() == 6,
                                        access));

        assertEquals("write", failure.problem().context().get("operation"));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(
                "SVG_EXPORT_CANCELLED",
                ((SvgExportException) failure.getSuppressed()[0]).problem().code());
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(access.temporary));
    }

    @Test
    void observesCancellationThatArrivesDuringMandatoryClose() throws Exception {
        Path target = existingTarget("close-cancellation.svg");
        FaultingAccess access = new FaultingAccess(Fault.WRITE);
        AtomicBoolean cancelled = new AtomicBoolean();
        access.onClose = () -> cancelled.set(true);

        SvgExportException failure =
                assertThrows(
                        SvgExportException.class,
                        () -> SvgAtomicFiles.write(target, new byte[8], cancelled::get, access));

        assertEquals("write", failure.problem().context().get("operation"));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(
                "SVG_EXPORT_CANCELLED",
                ((SvgExportException) failure.getSuppressed()[0]).problem().code());
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(access.temporary));
    }

    @Test
    void cancellationAtEveryFileCheckpointClosesAndCleansUp() throws Exception {
        for (int cancelAt = 1; cancelAt <= 15; cancelAt++) {
            int checkpoint = cancelAt;
            Path target = existingTarget("cancel-" + cancelAt + ".svg");
            FaultingAccess access = new FaultingAccess();
            AtomicInteger polls = new AtomicInteger();
            CancellationToken token = () -> polls.incrementAndGet() == checkpoint;

            SvgExportException failure =
                    assertThrows(
                            SvgExportException.class,
                            () -> SvgAtomicFiles.write(target, new byte[140_000], token, access),
                            "checkpoint " + cancelAt);

            assertEquals("SVG_EXPORT_CANCELLED", failure.problem().code());
            assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
            if (access.temporary != null) {
                assertFalse(Files.exists(access.temporary));
                assertEquals(1, access.closeCount);
            } else {
                assertEquals(0, access.closeCount);
            }
        }
    }

    private void assertFailure(Fault fault, String operation) throws Exception {
        Path target = existingTarget(fault.name() + ".svg");
        FaultingAccess access = new FaultingAccess(fault);
        SvgExportException failure =
                assertThrows(
                        SvgExportException.class,
                        () -> SvgAtomicFiles.write(target, new byte[8], () -> false, access));
        assertEquals("SVG_EXPORT_IO_FAILED", failure.problem().code());
        assertEquals(operation, failure.problem().context().get("operation"));
        assertEquals("existing", Files.readString(target, StandardCharsets.UTF_8));
        if (access.temporary != null) {
            assertFalse(Files.exists(access.temporary));
            assertEquals(1, access.closeCount);
        }
    }

    private Path existingTarget(String name) throws IOException {
        Path target = directory.resolve(name);
        Files.writeString(target, "existing", StandardCharsets.UTF_8);
        return target;
    }

    private enum Fault {
        PREFLIGHT,
        TEMPORARY,
        WRITE,
        FORCE,
        CLOSE,
        MOVE,
        ATOMIC_MOVE,
        DELETE
    }

    private static final class FaultingAccess implements SvgAtomicFiles.OutputAccess {
        private final SvgAtomicFiles.OutputAccess delegate = SvgAtomicFiles.OutputAccess.JDK;
        private final EnumSet<Fault> faults;
        private Path temporary;
        private int closeCount;
        private Runnable onClose = () -> {};

        private FaultingAccess(Fault... faults) {
            this.faults =
                    faults.length == 0
                            ? EnumSet.noneOf(Fault.class)
                            : EnumSet.of(faults[0], faults);
        }

        @Override
        public Path realParent(Path parent) throws IOException {
            if (faults.contains(Fault.PREFLIGHT)) {
                throw new IOException("injected preflight failure");
            }
            return delegate.realParent(parent);
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            return delegate.attributes(path);
        }

        @Override
        public SvgAtomicFiles.Temporary createTemporary(Path parent) throws IOException {
            if (faults.contains(Fault.TEMPORARY)) {
                throw new IOException("injected temporary failure");
            }
            SvgAtomicFiles.Temporary created = delegate.createTemporary(parent);
            temporary = created.path();
            SvgAtomicFiles.OutputChannel channel = created.channel();
            return new SvgAtomicFiles.Temporary(
                    created.path(),
                    new SvgAtomicFiles.OutputChannel() {
                        @Override
                        public int write(ByteBuffer source) throws IOException {
                            if (faults.contains(Fault.WRITE)) {
                                throw new IOException("injected write failure");
                            }
                            return channel.write(source);
                        }

                        @Override
                        public void force(boolean metadata) throws IOException {
                            if (faults.contains(Fault.FORCE)) {
                                throw new IOException("injected force failure");
                            }
                            channel.force(metadata);
                        }

                        @Override
                        public void close() throws IOException {
                            closeCount++;
                            onClose.run();
                            IOException failure = null;
                            try {
                                channel.close();
                            } catch (IOException exception) {
                                failure = exception;
                            }
                            if (faults.contains(Fault.CLOSE)) {
                                IOException injected = new IOException("injected close failure");
                                if (failure != null) {
                                    injected.addSuppressed(failure);
                                }
                                throw injected;
                            }
                            if (failure != null) {
                                throw failure;
                            }
                        }
                    },
                    created.fileKey());
        }

        @Override
        public void moveAtomic(Path temporaryPath, Path target) throws IOException {
            if (faults.contains(Fault.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(
                        temporaryPath.toString(), target.toString(), "injected");
            }
            if (faults.contains(Fault.MOVE)) {
                throw new IOException("injected move failure");
            }
            delegate.moveAtomic(temporaryPath, target);
        }

        @Override
        public void deleteTemporary(Path temporaryPath) throws IOException {
            IOException failure = null;
            try {
                delegate.deleteTemporary(temporaryPath);
            } catch (IOException exception) {
                failure = exception;
            }
            if (faults.contains(Fault.DELETE)) {
                IOException injected = new IOException("injected delete failure");
                if (failure != null) {
                    injected.addSuppressed(failure);
                }
                throw injected;
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
