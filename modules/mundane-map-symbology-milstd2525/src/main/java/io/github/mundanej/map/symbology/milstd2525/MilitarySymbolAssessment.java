package io.github.mundanej.map.symbology.milstd2525;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable support result for a syntactically valid identifier.
 *
 * @param support support class
 * @param problem empty only for {@link MilitarySymbolSupport#SUPPORTED}
 */
public record MilitarySymbolAssessment(
        MilitarySymbolSupport support, Optional<MilitarySymbolProblem> problem) {
    /** Validates assessment invariants. */
    public MilitarySymbolAssessment {
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(problem, "problem");
        if ((support == MilitarySymbolSupport.SUPPORTED) != problem.isEmpty()) {
            throw new IllegalArgumentException("only supported assessments omit a problem");
        }
    }

    /**
     * Creates the supported result.
     *
     * @return supported assessment
     */
    public static MilitarySymbolAssessment supported() {
        return new MilitarySymbolAssessment(MilitarySymbolSupport.SUPPORTED, Optional.empty());
    }

    /**
     * Creates a non-supported result.
     *
     * @param support non-supported class
     * @param problem stable first problem
     * @return assessment
     */
    public static MilitarySymbolAssessment problem(
            MilitarySymbolSupport support, MilitarySymbolProblem problem) {
        if (support == MilitarySymbolSupport.SUPPORTED) {
            throw new IllegalArgumentException("problem assessment cannot be supported");
        }
        return new MilitarySymbolAssessment(support, Optional.of(problem));
    }
}
