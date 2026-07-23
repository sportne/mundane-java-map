package io.github.mundanej.map.symbology.milstd2525;

/** Support classification for a syntactically valid military symbol identifier. */
public enum MilitarySymbolSupport {
    /** Every field is supported by the bounded profile. */
    SUPPORTED,
    /** Only the entity is outside the inventory, so a recognized frame can be displayed. */
    DEGRADED_ENTITY,
    /** Only a symbol-set sector modifier is unsupported, so a frame can be displayed. */
    DEGRADED_MODIFIER,
    /** A non-degradable field is unsupported. */
    UNSUPPORTED
}
