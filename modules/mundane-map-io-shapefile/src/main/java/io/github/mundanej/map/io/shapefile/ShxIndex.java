package io.github.mundanej.map.io.shapefile;

/** Immutable packed SHX record addresses in physical record order. */
final class ShxIndex {
    private final long[] entries;

    ShxIndex(long[] entries) {
        this.entries = entries;
    }

    int size() {
        return entries.length;
    }

    long offsetBytes(int index) {
        return Math.multiplyExact(entries[index] >>> 32, 2L);
    }

    long contentBytes(int index) {
        return Math.multiplyExact(entries[index] & 0xffff_ffffL, 2L);
    }

    static long pack(int offsetWords, int contentWords) {
        return ((long) offsetWords << 32) | (contentWords & 0xffff_ffffL);
    }
}
