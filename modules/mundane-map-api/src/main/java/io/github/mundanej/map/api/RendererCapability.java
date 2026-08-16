package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit renderer decision for one symbol construct.
 *
 * @param support exact acceptance, named approximation, or rejection
 * @param approximationPolicy named policy only when approximating
 * @param diagnosticCode stable non-blank decision code
 */
public record RendererCapability(
        Support support, Optional<String> approximationPolicy, String diagnosticCode) {
    /** Renderer support decisions. */
    public enum Support {
        /** Render exactly within documented tolerances. */
        ACCEPT,
        /** Render using an explicitly named approximation policy. */
        APPROXIMATE,
        /** Reject with a stable diagnostic. */
        REJECT
    }

    /** Creates and validates a capability decision. */
    public RendererCapability {
        Objects.requireNonNull(support, "support");
        approximationPolicy =
                Objects.requireNonNull(approximationPolicy, "approximationPolicy")
                        .map(Objects::requireNonNull);
        if (support == Support.APPROXIMATE) {
            if (approximationPolicy.isEmpty() || approximationPolicy.orElseThrow().isBlank()) {
                throw new IllegalArgumentException("approximation requires a named policy");
            }
        } else if (approximationPolicy.isPresent()) {
            throw new IllegalArgumentException("only approximation decisions have a policy");
        }
        if (Objects.requireNonNull(diagnosticCode, "diagnosticCode").isBlank()) {
            throw new IllegalArgumentException("diagnosticCode must not be blank");
        }
    }

    /**
     * Creates an exact acceptance decision.
     *
     * @return accepted capability
     */
    public static RendererCapability accept() {
        return new RendererCapability(Support.ACCEPT, Optional.empty(), "RENDERER_ACCEPTED");
    }

    /**
     * Creates a named approximation decision.
     *
     * @param policy non-blank policy name
     * @return approximation capability
     */
    public static RendererCapability approximate(String policy) {
        return new RendererCapability(
                Support.APPROXIMATE, Optional.of(policy), "RENDERER_APPROXIMATION_SELECTED");
    }

    /**
     * Creates a stable rejection decision.
     *
     * @param diagnosticCode stable non-blank rejection code
     * @return rejected capability
     */
    public static RendererCapability reject(String diagnosticCode) {
        return new RendererCapability(Support.REJECT, Optional.empty(), diagnosticCode);
    }
}
