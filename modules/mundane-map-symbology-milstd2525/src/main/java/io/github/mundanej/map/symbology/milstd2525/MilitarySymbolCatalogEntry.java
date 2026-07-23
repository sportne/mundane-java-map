package io.github.mundanej.map.symbology.milstd2525;

import java.util.Objects;

/**
 * One immutable entity in the bounded profile catalog.
 *
 * @param symbolSet two-position hexadecimal symbol-set value
 * @param entityCode six-position hexadecimal entity/type/subtype value
 * @param name approved display name
 */
public record MilitarySymbolCatalogEntry(int symbolSet, int entityCode, String name) {
    /** Validates one finite catalog row. */
    public MilitarySymbolCatalogEntry {
        if (symbolSet < 0 || symbolSet > 0xff) {
            throw new IllegalArgumentException("symbolSet must fit two hexadecimal positions");
        }
        if (entityCode < 0 || entityCode > 0xffffff) {
            throw new IllegalArgumentException("entityCode must fit six hexadecimal positions");
        }
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("name must be non-blank and bounded");
        }
    }
}
