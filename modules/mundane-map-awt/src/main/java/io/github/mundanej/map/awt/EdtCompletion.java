package io.github.mundanej.map.awt;

import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Runs one bounded AWT cleanup action to completion without creating a worker. */
final class EdtCompletion {
    private EdtCompletion() {}

    static Throwable runAndWait(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (EventQueue.isDispatchThread()) {
            action.run();
            return null;
        }
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeLater(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    } finally {
                        complete.countDown();
                    }
                });
        boolean interrupted = false;
        while (true) {
            try {
                complete.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return failure.get();
    }
}
