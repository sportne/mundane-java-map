package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Envelope;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a domain-aware query-envelope transformation.
 *
 * @param status relationship between the input query and the operation's source domain
 * @param transformedEnvelope transformed intersection, absent exactly when {@code status} is {@link
 *     QueryEnvelopeStatus#OUTSIDE}
 */
public record QueryEnvelopeTransform(
        QueryEnvelopeStatus status, Optional<Envelope> transformedEnvelope) {
    /**
     * Creates and validates a result.
     *
     * @throws IllegalArgumentException when envelope presence disagrees with {@code status}
     */
    public QueryEnvelopeTransform {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(transformedEnvelope, "transformedEnvelope");
        if ((status == QueryEnvelopeStatus.OUTSIDE) != transformedEnvelope.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only an OUTSIDE query transform may omit its envelope");
        }
    }
}
