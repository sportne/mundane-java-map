package io.github.mundanej.map.io.dted.corpus;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class DtedCorpusExpectations {
    private static final Map<String, Expectation> VALUES =
            Map.of(
                    "expect-gdal-zone-v-l0-complete",
                    new Expectation(
                            21,
                            121,
                            List.of(
                                    new Post(0, 0, 1500),
                                    new Post(20, 0, 2500),
                                    new Post(0, 120, -500),
                                    new Post(20, 120, 500),
                                    new Post(10, 120, 0),
                                    new Post(10, 60, 1000),
                                    new Post(1, 2, 1517)),
                            Set.of()),
                    "expect-gdal-zone-v-l1-complete",
                    new Expectation(
                            201,
                            1201,
                            List.of(
                                    new Post(0, 0, 1500),
                                    new Post(200, 0, 2500),
                                    new Post(0, 1200, -500),
                                    new Post(200, 1200, 500),
                                    new Post(100, 1200, 0),
                                    new Post(100, 600, 1000),
                                    new Post(17, 23, 1547)),
                            Set.of()),
                    "expect-gdal-zone-v-l2-partial",
                    new Expectation(
                            601,
                            3601,
                            List.of(
                                    new Post(0, 0, 1500),
                                    new Post(600, 0, 2500),
                                    new Post(0, 3600, -500),
                                    new Post(600, 3600, 500),
                                    new Post(300, 3600, 0),
                                    new Post(17, 23, 1516),
                                    new Post(298, 1800, 996),
                                    new Post(302, 1800, 1003),
                                    new Post(300, 1798, 1002),
                                    new Post(300, 1802, 999)),
                            Set.of(
                                    new Index(299, 1799),
                                    new Index(300, 1799),
                                    new Index(301, 1799),
                                    new Index(299, 1800),
                                    new Index(300, 1800),
                                    new Index(301, 1800),
                                    new Index(299, 1801),
                                    new Index(300, 1801),
                                    new Index(301, 1801))));

    private DtedCorpusExpectations() {}

    static Set<String> ids() {
        return VALUES.keySet();
    }

    static Expectation get(String id) {
        Expectation expectation = VALUES.get(id);
        if (expectation == null) {
            throw new IllegalArgumentException("Unknown DTED corpus expectation: " + id);
        }
        return expectation;
    }

    record Expectation(int columns, int rows, List<Post> finitePosts, Set<Index> voidPosts) {
        Expectation {
            finitePosts = List.copyOf(finitePosts);
            voidPosts = Set.copyOf(voidPosts);
        }
    }

    record Post(int column, int row, int value) {}

    record Index(int column, int row) {}
}
