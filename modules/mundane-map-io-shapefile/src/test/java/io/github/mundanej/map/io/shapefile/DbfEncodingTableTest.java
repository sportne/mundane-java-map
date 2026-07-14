package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DbfEncodingTableTest {
    private static final char INVALID = '\uffff';
    private static final String WINDOWS_SUFFIX =
            "\u20ac\uffff\u201a\u0192\u201e\u2026\u2020\u2021\u02c6\u2030\u0160\u2039\u0152\uffff\u017d\uffff"
                    + "\uffff\u2018\u2019\u201c\u201d\u2022\u2013\u2014\u02dc\u2122\u0161\u203a\u0153\uffff\u017e\u0178"
                    + "\u00a0\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af"
                    + "\u00b0\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7\u00b8\u00b9\u00ba\u00bb\u00bc\u00bd\u00be\u00bf"
                    + "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf"
                    + "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df"
                    + "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef"
                    + "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7"
                    + "\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff";
    private static final String IBM437_SUFFIX =
            "\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5"
                    + "\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00a2\u00a3\u00a5\u20a7\u0192"
                    + "\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u2310\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb"
                    + "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510"
                    + "\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567"
                    + "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580"
                    + "\u03b1\u00df\u0393\u03c0\u03a3\u03c3\u00b5\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u03c6\u03b5\u2229"
                    + "\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248"
                    + "\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u00a0";
    private static final String IBM850_SUFFIX =
            "\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5"
                    + "\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192"
                    + "\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb"
                    + "\u2591\u2592\u2593\u2502\u2524\u00c1\u00c2\u00c0\u00a9\u2563\u2551\u2557\u255d\u00a2\u00a5\u2510"
                    + "\u2514\u2534\u252c\u251c\u2500\u253c\u00e3\u00c3\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u00a4"
                    + "\u00f0\u00d0\u00ca\u00cb\u00c8\u0131\u00cd\u00ce\u00cf\u2518\u250c\u2588\u2584\u00a6\u00cc\u2580"
                    + "\u00d3\u00df\u00d4\u00d2\u00f5\u00d5\u00b5\u00fe\u00de\u00da\u00db\u00d9\u00fd\u00dd\u00af\u00b4"
                    + "\u00ad\u00b1\u2017\u00be\u00b6\u00a7\u00f7\u00b8"
                    + "\u00b0\u00a8\u00b7\u00b9\u00b3\u00b2\u25a0\u00a0";

    @Test
    void everyManualSingleByteEntryMatchesTheReviewedTables() {
        assertTable(DbfEncoding.WINDOWS_1252, WINDOWS_SUFFIX);
        assertTable(DbfEncoding.IBM437, IBM437_SUFFIX);
        assertTable(DbfEncoding.IBM850, IBM850_SUFFIX);
    }

    @Test
    void manualTableChecksumsRemainStable() {
        assertEquals(
                0x4a473fd3b52c15e6L, DbfValueDecoder.mappingChecksum(DbfEncoding.WINDOWS_1252));
        assertEquals(0x1966adee79eb9660L, DbfValueDecoder.mappingChecksum(DbfEncoding.IBM437));
        assertEquals(0xee723f0c00c07210L, DbfValueDecoder.mappingChecksum(DbfEncoding.IBM850));
    }

    @Test
    void tableSeamsRejectStandardCharsetsAndOutOfRangeBytes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DbfValueDecoder.mappedCodeUnit(DbfEncoding.UTF_8, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> DbfValueDecoder.mappingChecksum(DbfEncoding.ISO_8859_1));
        assertThrows(
                IllegalArgumentException.class,
                () -> DbfValueDecoder.mappedCodeUnit(DbfEncoding.IBM437, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> DbfValueDecoder.mappedCodeUnit(DbfEncoding.IBM850, 256));
    }

    private static void assertTable(DbfEncoding encoding, String suffix) {
        assertEquals(128, suffix.length());
        for (int value = 0; value < 128; value++) {
            assertEquals(value, DbfValueDecoder.mappedCodeUnit(encoding, value), "byte " + value);
        }
        for (int value = 128; value < 256; value++) {
            int expected = suffix.charAt(value - 128);
            assertEquals(
                    expected == INVALID ? -1 : expected,
                    DbfValueDecoder.mappedCodeUnit(encoding, value),
                    "byte " + value);
        }
    }
}
