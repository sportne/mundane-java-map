package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.ElevationUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-side DTED oracle derived from the reviewed fixture formulas and corpus inventory. */
final class IndependentDtedOracle {
    private IndependentDtedOracle() {}

    static Map<String, String> derive(EvidenceConfiguration.Profile profile) {
        int posts = profile == EvidenceConfiguration.Profile.BASELINE ? 3_601 : 121;
        long samples = Math.multiplyExact((long) posts, posts);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("dted-corpus-open", corpusOpen(profile));
        result.put("dted-eager-open", eagerOpen(profile, posts, samples));
        result.put("dted-sequential-scan", scan(profile, posts, samples));
        result.put("dted-position-query", query(profile, posts));
        return result;
    }

    private static String corpusOpen(EvidenceConfiguration.Profile profile) {
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        int files = profile == EvidenceConfiguration.Profile.BASELINE ? 3 : 1;
        counters.put("filesOpened", (long) files);
        counters.put("profiles", profile == EvidenceConfiguration.Profile.BASELINE ? 823L : 121L);
        counters.put(
                "samplesPublished",
                profile == EvidenceConfiguration.Profile.BASELINE ? 2_408_143L : 14_641L);
        counters.put(
                "encodedBytes",
                profile == EvidenceConfiguration.Profile.BASELINE ? 4_836_446L : 34_162L);
        counters.put("noDataSamples", profile == EvidenceConfiguration.Profile.BASELINE ? 9L : 0L);
        ReferenceDigest digest = observation(profile, "dted-corpus-open");
        if (profile == EvidenceConfiguration.Profile.BASELINE) {
            addSource(digest, 21, 121, -1, -81, 0, -80, new double[] {1500, 2500, -500, 500});
            addSource(digest, 201, 1_201, -1, -81, 0, -80, new double[] {1500, 2500, -500, 500});
            addSource(digest, 601, 3_601, -1, -81, 0, -80, new double[] {1500, 2500, -500, 500});
        } else {
            addGeneratedSource(digest, 121);
        }
        addCounters(digest, counters);
        return hex(digest.value());
    }

    private static String eagerOpen(
            EvidenceConfiguration.Profile profile, int posts, long samples) {
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        counters.put("filesOpened", 1L);
        counters.put("profiles", (long) posts);
        counters.put("samplesPublished", samples);
        counters.put("logicalPublishedBytes", published(samples));
        counters.put("logicalOpenPeakBytes", openPeak(samples, posts));
        ReferenceDigest digest = observation(profile, "dted-eager-open");
        addGeneratedSource(digest, posts);
        addCounters(digest, counters);
        return hex(digest.value());
    }

    private static String scan(EvidenceConfiguration.Profile profile, int posts, long samples) {
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        counters.put("profiles", (long) posts);
        counters.put("samplesVisited", samples);
        counters.put("noDataSamples", 0L);
        ReferenceDigest timed =
                new ReferenceDigest(EvidenceConfiguration.SEED).text("dted-sequential-scan");
        for (int row = 0; row < posts; row++) {
            for (int column = 0; column < posts; column++) {
                timed.integer(column).integer(row).flag(true).decimal(value(column, row, posts));
            }
        }
        ReferenceDigest digest =
                observation(profile, "dted-sequential-scan").longInteger(timed.value());
        addCounters(digest, counters);
        return hex(digest.value());
    }

    private static String query(EvidenceConfiguration.Profile profile, int posts) {
        int queries = profile == EvidenceConfiguration.Profile.BASELINE ? 65_536 : 256;
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        counters.put("queries", (long) queries);
        counters.put("nearestQueries", queries / 2L);
        counters.put("bilinearQueries", queries / 2L);
        ReferenceDigest timed =
                new ReferenceDigest(EvidenceConfiguration.SEED).text("dted-position-query");
        int modulus = posts - 1;
        for (int query = 0; query < queries; query++) {
            int column = Math.floorMod(997 * query, modulus);
            int row = Math.floorMod(613 * query, modulus);
            boolean nearest = (query & 1) == 0;
            double elevation =
                    nearest
                            ? value(column, row, posts)
                            : bilinearAtTraceMidpoint(column, row, posts);
            timed.integer(query)
                    .flag(nearest)
                    .flag(true)
                    .decimal(elevation)
                    .enumeration(ElevationUnit.METRE);
        }
        ReferenceDigest digest =
                observation(profile, "dted-position-query").longInteger(timed.value());
        addCounters(digest, counters);
        return hex(digest.value());
    }

    private static void addGeneratedSource(ReferenceDigest digest, int posts) {
        addSource(
                digest,
                posts,
                posts,
                0,
                0,
                1,
                1,
                new double[] {
                    value(0, 0, posts),
                    value(posts - 1, 0, posts),
                    value(0, posts - 1, posts),
                    value(posts - 1, posts - 1, posts)
                });
    }

    private static void addSource(
            ReferenceDigest digest,
            int columns,
            int rows,
            double minX,
            double minY,
            double maxX,
            double maxY,
            double[] corners) {
        digest.integer(columns)
                .integer(rows)
                .decimal(minX)
                .decimal(minY)
                .decimal(maxX)
                .decimal(maxY)
                .text("EPSG:4326")
                .enumeration(ElevationUnit.METRE);
        int[][] indices = {{0, 0}, {columns - 1, 0}, {0, rows - 1}, {columns - 1, rows - 1}};
        for (int index = 0; index < indices.length; index++) {
            digest.integer(indices[index][0])
                    .integer(indices[index][1])
                    .flag(true)
                    .decimal(corners[index]);
        }
        digest.integer(0).longInteger(0);
    }

    private static ReferenceDigest observation(
            EvidenceConfiguration.Profile profile, String scenario) {
        return new ReferenceDigest(EvidenceConfiguration.SEED).text(profile.name()).text(scenario);
    }

    private static void addCounters(ReferenceDigest digest, Map<String, Long> counters) {
        counters.forEach((key, value) -> digest.text(key).longInteger(value));
    }

    private static int value(int column, int row, int posts) {
        return 1_200
                + Math.floorDiv(1_200 * column, posts - 1)
                - Math.floorDiv(800 * row, posts - 1);
    }

    private static double bilinearAtTraceMidpoint(int column, int row, int posts) {
        double west = sampleCoordinate(column, posts, 0.0, 1.0);
        double east = sampleCoordinate(column + 1, posts, 0.0, 1.0);
        double north = sampleCoordinate(row, posts, 1.0, 0.0);
        double south = sampleCoordinate(row + 1, posts, 1.0, 0.0);
        double x = (west + east) / 2.0;
        double y = (north + south) / 2.0;
        double columnWeight = (x - west) / (east - west);
        double rowWeight = (north - y) / (north - south);
        double northValue =
                convex(value(column, row, posts), value(column + 1, row, posts), columnWeight);
        double southValue =
                convex(
                        value(column, row + 1, posts),
                        value(column + 1, row + 1, posts),
                        columnWeight);
        return convex(northValue, southValue, rowWeight);
    }

    private static double sampleCoordinate(int index, int count, double first, double last) {
        if (index == 0) {
            return first;
        }
        if (index == count - 1) {
            return last;
        }
        double ratio = (double) index / (count - 1L);
        return Math.fma(ratio, last - first, first);
    }

    private static double convex(double first, double second, double weight) {
        if ((first < 0.0) == (second < 0.0)) {
            return Math.fma(weight, second - first, first);
        }
        return Math.fma(1.0 - weight, first, weight * second);
    }

    private static long published(long samples) {
        return 8L * samples + 8L * Math.floorDiv(samples + 63L, 64L);
    }

    private static long openPeak(long samples, int rows) {
        return 2_700L + 12L + 2L * rows + 2L * published(samples);
    }

    private static String hex(long value) {
        return String.format(java.util.Locale.ROOT, "%016x", value);
    }
}
