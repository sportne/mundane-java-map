package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Envelope;
import java.util.Objects;
import java.util.Optional;

/** Immutable result of a domain-aware query-envelope transformation. */
public record QueryEnvelopeTransform(
        QueryEnvelopeStatus status, Optional<Envelope> transformedEnvelope) {
    /** Creates and validates a result. */
    public QueryEnvelopeTransform {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(transformedEnvelope, "transformedEnvelope");
        if ((status == QueryEnvelopeStatus.OUTSIDE) != transformedEnvelope.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only an OUTSIDE query transform may omit its envelope");
        }
    }
}
