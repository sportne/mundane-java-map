package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.VectorPath;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One view-owned, EDT-confined owner for the evidence-retained vector-template cache. */
final class AwtRenderCache {
    static final int VECTOR_MAX_ENTRIES = 512;
    static final long VECTOR_MAX_BYTES = 4L * 1_024L * 1_024L;
    static final long VECTOR_MAX_ENTRY_BYTES = 256L * 1_024L;

    private final Partition<IdentityKey<VectorPath>, VectorPath2D.Converted> vectorTemplates;

    AwtRenderCache() {
        this(VECTOR_MAX_ENTRIES, VECTOR_MAX_BYTES, VECTOR_MAX_ENTRY_BYTES);
    }

    AwtRenderCache(int vectorEntries, long vectorBytes, long vectorEntryBytes) {
        vectorTemplates = new Partition<>(vectorEntries, vectorBytes, vectorEntryBytes);
    }

    VectorLookup lookupVectorTemplate(VectorPath path, CacheEventCollector events) {
        requireEdt();
        IdentityKey<VectorPath> key = new IdentityKey<>(path);
        VectorPath2D.Converted hit = vectorTemplates.lookup(key, events.vectorTemplate);
        return new VectorLookup(key, hit);
    }

    VectorPath2D.Converted buildVectorTemplate(VectorPath path, CacheEventCollector events) {
        requireEdt();
        VectorPath2D.StreamCounts counts = VectorPath2D.preflight(path);
        long weight;
        try {
            weight = vectorTemplateWeight(path, counts);
        } catch (ArithmeticException failure) {
            events.vectorTemplate.bypass();
            return VectorPath2D.convert(path);
        }
        if (!vectorTemplates.accepts(weight)) {
            events.vectorTemplate.bypass();
            return VectorPath2D.convert(path);
        }
        events.vectorTemplate.build(Math.addExact(counts.strokeUnits(), counts.fillUnits()));
        return VectorPath2D.convert(path);
    }

    void completeVectorTemplate(
            VectorLookup lookup,
            VectorPath path,
            VectorPath2D.Converted built,
            boolean rendered,
            CacheEventCollector events) {
        requireEdt();
        if (!rendered) {
            return;
        }
        if (lookup.hit() != null) {
            vectorTemplates.promote(lookup.key(), events.vectorTemplate);
            return;
        }
        VectorPath2D.StreamCounts counts = VectorPath2D.preflight(path);
        long weight;
        try {
            weight = vectorTemplateWeight(path, counts);
        } catch (ArithmeticException failure) {
            return;
        }
        if (!vectorTemplates.accepts(weight)) {
            return;
        }
        vectorTemplates.admit(lookup.key(), built, weight, events.vectorTemplate);
    }

    void recordUncachedVectorBuild(VectorPath path, CacheEventCollector events) {
        requireEdt();
        VectorPath2D.StreamCounts counts = VectorPath2D.preflight(path);
        events.vectorTemplate.build(Math.addExact(counts.strokeUnits(), counts.fillUnits()));
    }

    void clearVectorTemplates() {
        requireEdt();
        vectorTemplates.clear();
    }

    void clear() {
        requireEdt();
        vectorTemplates.clear();
    }

    void snapshotState(CacheEventCollector events) {
        requireEdt();
        events.vectorTemplate.observe(vectorTemplates.size(), vectorTemplates.logicalBytes());
    }

    static long vectorTemplateWeight(VectorPath path, VectorPath2D.StreamCounts counts) {
        long result = 96L; // map/key/path/converted/path-stream retained reference slots
        result = Math.addExact(result, 32L); // retained VectorPath envelope doubles
        result = Math.addExact(result, path.commandCount());
        result = Math.addExact(result, Math.multiplyExact((long) path.ordinateCount(), 8L));
        result = Math.addExact(result, counts.strokeCommands());
        result = Math.addExact(result, Math.multiplyExact(counts.strokeOrdinates(), 8L));
        result = Math.addExact(result, counts.fillCommands());
        return Math.addExact(result, Math.multiplyExact(counts.fillOrdinates(), 8L));
    }

    private static void requireEdt() {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "AWT render cache is confined to the event dispatch thread");
        }
    }

    record VectorLookup(IdentityKey<VectorPath> key, VectorPath2D.Converted hit) {}

    static final class CacheEventCollector {
        final MutablePartitionMetrics vectorTemplate = new MutablePartitionMetrics();

        RenderCachePaintMetrics result() {
            return new RenderCachePaintMetrics(vectorTemplate.result());
        }
    }

    static final class MutablePartitionMetrics {
        private long requests;
        private long hits;
        private long misses;
        private long builds;
        private long admissions;
        private long evictions;
        private long bypasses;
        private long buildUnits;
        private long currentEntries;
        private long currentBytes;
        private long peakEntries;
        private long peakBytes;

        MutablePartitionMetrics() {}

        MutablePartitionMetrics(long admissions, long evictions) {
            this.admissions = admissions;
            this.evictions = evictions;
        }

        void request(boolean hit) {
            requests = Math.addExact(requests, 1);
            if (hit) {
                hits = Math.addExact(hits, 1);
            } else {
                misses = Math.addExact(misses, 1);
            }
        }

        void build(long units) {
            builds = Math.addExact(builds, 1);
            buildUnits = Math.addExact(buildUnits, units);
        }

        AdmissionCounts preflightAdmission(int evictionCount) {
            return new AdmissionCounts(
                    Math.addExact(admissions, 1), Math.addExact(evictions, evictionCount));
        }

        void commitAdmission(AdmissionCounts counts) {
            admissions = counts.admissions();
            evictions = counts.evictions();
        }

        void bypass() {
            bypasses = Math.addExact(bypasses, 1);
        }

        void observe(long entries, long bytes) {
            currentEntries = entries;
            currentBytes = bytes;
            peakEntries = Math.max(peakEntries, entries);
            peakBytes = Math.max(peakBytes, bytes);
        }

        CachePartitionMetrics result() {
            return new CachePartitionMetrics(
                    requests,
                    hits,
                    misses,
                    builds,
                    admissions,
                    evictions,
                    bypasses,
                    buildUnits,
                    currentEntries,
                    currentBytes,
                    peakEntries,
                    peakBytes);
        }

        record AdmissionCounts(long admissions, long evictions) {}
    }

    static final class Partition<K, V> {
        private final int maximumEntries;
        private final long maximumBytes;
        private final long maximumEntryBytes;
        private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>();
        private long logicalBytes;

        Partition(int maximumEntries, long maximumBytes, long maximumEntryBytes) {
            if (maximumEntries <= 0
                    || maximumBytes <= 0
                    || maximumEntryBytes <= 0
                    || maximumEntryBytes > maximumBytes) {
                throw new IllegalArgumentException("Invalid cache partition limits");
            }
            this.maximumEntries = maximumEntries;
            this.maximumBytes = maximumBytes;
            this.maximumEntryBytes = maximumEntryBytes;
        }

        V lookup(K key, MutablePartitionMetrics events) {
            Entry<V> entry = entries.get(key);
            events.request(entry != null);
            events.observe(entries.size(), logicalBytes);
            return entry == null ? null : entry.value;
        }

        void promote(K key, MutablePartitionMetrics events) {
            Entry<V> value = entries.remove(key);
            if (value != null) {
                entries.put(key, value);
            }
            events.observe(entries.size(), logicalBytes);
        }

        void admit(K key, V value, long weight, MutablePartitionMetrics events) {
            if (weight <= 0 || weight > maximumEntryBytes || weight > maximumBytes) {
                events.bypass();
                events.observe(entries.size(), logicalBytes);
                return;
            }
            Entry<V> old = entries.get(key);
            long prospectiveBytes =
                    old == null ? logicalBytes : Math.subtractExact(logicalBytes, old.weight);
            int prospectiveEntries = entries.size() - (old == null ? 0 : 1);
            List<K> evictions = new ArrayList<>();
            for (Map.Entry<K, Entry<V>> entry : entries.entrySet()) {
                if (entry.getKey().equals(key)) {
                    continue;
                }
                if (prospectiveEntries < maximumEntries
                        && weight <= maximumBytes - prospectiveBytes) {
                    break;
                }
                prospectiveBytes = Math.subtractExact(prospectiveBytes, entry.getValue().weight);
                prospectiveEntries--;
                evictions.add(entry.getKey());
            }
            if (prospectiveEntries >= maximumEntries || weight > maximumBytes - prospectiveBytes) {
                events.bypass();
                events.observe(entries.size(), logicalBytes);
                return;
            }
            long admittedBytes = Math.addExact(prospectiveBytes, weight);
            MutablePartitionMetrics.AdmissionCounts admissionCounts =
                    events.preflightAdmission(evictions.size());
            entries.remove(key);
            for (K eviction : evictions) {
                entries.remove(eviction);
            }
            entries.put(key, new Entry<>(value, weight));
            logicalBytes = admittedBytes;
            events.commitAdmission(admissionCounts);
            events.observe(entries.size(), logicalBytes);
        }

        int size() {
            return entries.size();
        }

        long logicalBytes() {
            return logicalBytes;
        }

        boolean accepts(long weight) {
            return weight <= maximumEntryBytes && weight <= maximumBytes;
        }

        void clear() {
            entries.clear();
            logicalBytes = 0;
        }

        private record Entry<V>(V value, long weight) {}
    }

    static final class IdentityKey<T> {
        private final T value;

        IdentityKey(T value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey<?> key && value == key.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }
}
