package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import java.util.IdentityHashMap;
import org.springframework.stereotype.Component;

/** Application owner that closes every still-live viewer session during shutdown. */
@Component
public final class ViewerSessionRegistry implements AutoCloseable {
    private final IdentityHashMap<ViewerSession, Runnable> sessions = new IdentityHashMap<>();
    private boolean closed;

    /** Creates an initially open application-scoped viewer-session registry. */
    public ViewerSessionRegistry() {}

    synchronized Registration register(ViewerSession session) {
        return register(session, session::close);
    }

    synchronized Registration register(ViewerSession session, Runnable closeAction) {
        if (closed) {
            closeAction.run();
            throw new IllegalStateException("viewer session registry is closed");
        }
        sessions.put(session, closeAction);
        return () -> remove(session);
    }

    private synchronized void remove(ViewerSession session) {
        sessions.remove(session);
    }

    /** Closes every registered route session exactly once. */
    @Override
    @PreDestroy
    public void close() {
        Runnable[] snapshot;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            snapshot = sessions.values().toArray(Runnable[]::new);
            sessions.clear();
        }
        Throwable primary = null;
        for (Runnable closeAction : snapshot) {
            try {
                closeAction.run();
            } catch (RuntimeException | Error failure) {
                if (primary == null) {
                    primary = failure;
                } else {
                    primary.addSuppressed(failure);
                }
            }
        }
        if (primary instanceof RuntimeException failure) {
            throw failure;
        }
        if (primary instanceof Error failure) {
            throw failure;
        }
    }
}
