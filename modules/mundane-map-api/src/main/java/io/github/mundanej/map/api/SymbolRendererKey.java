package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A stable, namespaced key used for explicit symbol-renderer lookup.
 *
 * @param value exact lowercase dot-separated namespaced key
 */
public record SymbolRendererKey(String value) {
    private static final Pattern VALID_KEY =
            Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+");

    /** Creates and validates an exact, case-sensitive renderer key. */
    public SymbolRendererKey {
        Objects.requireNonNull(value, "value");
        if (!VALID_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "value must contain at least two lowercase ASCII name segments");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
