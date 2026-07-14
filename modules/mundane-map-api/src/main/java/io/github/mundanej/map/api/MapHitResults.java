package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable topmost-first map hit results. */
public final class MapHitResults implements Iterable<MapHit> {
    private final List<MapHit> hits;

    private MapHitResults(List<MapHit> hits) {
        this.hits = hits;
    }

    /** Creates results from a topmost-first list, rejecting duplicate identities. */
    public static MapHitResults of(List<MapHit> hits) {
        Objects.requireNonNull(hits, "hits");
        for (int index = 0; index < hits.size(); index++) {
            if (hits.get(index) == null) {
                throw new IllegalArgumentException("hits[" + index + "] must not be null");
            }
        }
        List<MapHit> copy = List.copyOf(hits);
        Set<MapHit> unique = new HashSet<>();
        for (MapHit hit : copy) {
            if (!unique.add(hit)) {
                throw new IllegalArgumentException("hits must not contain duplicate identities");
            }
        }
        return new MapHitResults(copy);
    }

    /** Returns the number of feature hits. */
    public int size() {
        return hits.size();
    }

    /** Returns the immutable topmost-first list. */
    public List<MapHit> hits() {
        return List.copyOf(hits);
    }

    /** Returns the topmost hit, if present. */
    public Optional<MapHit> topmost() {
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.getFirst());
    }

    @Override
    public Iterator<MapHit> iterator() {
        return hits.iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MapHitResults results && hits.equals(results.hits);
    }

    @Override
    public int hashCode() {
        return hits.hashCode();
    }

    @Override
    public String toString() {
        return "MapHitResults" + hits;
    }
}
