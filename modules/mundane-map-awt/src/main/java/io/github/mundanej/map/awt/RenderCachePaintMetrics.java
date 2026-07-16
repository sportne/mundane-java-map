package io.github.mundanej.map.awt;

/** Immutable operation-local cache facts exposed only to package-confined evidence code. */
record RenderCachePaintMetrics(CachePartitionMetrics vectorTemplate) {
    static RenderCachePaintMetrics empty() {
        return new RenderCachePaintMetrics(CachePartitionMetrics.empty());
    }
}
