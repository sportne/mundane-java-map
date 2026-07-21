package io.github.mundanej.map.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class WorkspaceThrowableAssertions {
    private WorkspaceThrowableAssertions() {}

    static void assertOmits(Throwable root, String... forbidden) {
        var pending = new ArrayDeque<Throwable>();
        Set<Throwable> visited =
                Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(root);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            String rendered = current.toString();
            for (String value : forbidden) {
                assertFalse(rendered.contains(value), () -> "Throwable leaked: " + value);
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            Collections.addAll(pending, current.getSuppressed());
        }
    }
}
