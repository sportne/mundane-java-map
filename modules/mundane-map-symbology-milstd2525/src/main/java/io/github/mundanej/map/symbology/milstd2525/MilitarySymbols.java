package io.github.mundanej.map.symbology.milstd2525;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explicit resolver for the bounded MIL-STD-2525 point-symbol profile. */
public final class MilitarySymbols {
    private static final Envelope VIEW_BOX = new Envelope(0, 0, 100, 100);
    private static final int FIRST_ENTITY = 0x121100;

    private MilitarySymbols() {}

    /**
     * Resolves a supported identifier at full opacity.
     *
     * @param id canonical identifier
     * @param placement caller-owned ordinary marker placement
     * @param palette approved palette
     * @return ordinary composite symbol
     */
    public static Symbol resolveStrict(
            MilitarySymbolId id, MarkerPlacement placement, MilitarySymbolPalette palette) {
        return resolveStrict(id, placement, palette, 1.0);
    }

    /**
     * Resolves a supported identifier.
     *
     * @param id canonical identifier
     * @param placement caller-owned ordinary marker placement
     * @param palette approved palette
     * @param opacity caller opacity from zero through one
     * @return ordinary composite symbol
     */
    public static Symbol resolveStrict(
            MilitarySymbolId id,
            MarkerPlacement placement,
            MilitarySymbolPalette palette,
            double opacity) {
        MilitarySymbolAssessment assessment = assess(id, placement, palette, opacity);
        if (assessment.support() != MilitarySymbolSupport.SUPPORTED) {
            throw new MilitarySymbolException(
                    "SIDC is outside the strict supported rendering profile",
                    assessment.problem().orElseThrow());
        }
        MilitarySymbolProblem renderingProblem = renderingProblem(id);
        if (renderingProblem != null) {
            throw new MilitarySymbolException(
                    "SIDC is supported but outside the first rendering slice", renderingProblem);
        }
        return compose(id, placement, palette, opacity, true);
    }

    /**
     * Resolves a supported or degradable identifier at full opacity.
     *
     * @param id canonical identifier
     * @param placement caller-owned ordinary marker placement
     * @param palette approved palette
     * @return symbol and optional degradation
     */
    public static MilitarySymbolResolution resolveDegraded(
            MilitarySymbolId id, MarkerPlacement placement, MilitarySymbolPalette palette) {
        return resolveDegraded(id, placement, palette, 1.0);
    }

    /**
     * Resolves a supported or degradable identifier.
     *
     * @param id canonical identifier
     * @param placement caller-owned ordinary marker placement
     * @param palette approved palette
     * @param opacity caller opacity from zero through one
     * @return symbol and optional degradation
     */
    public static MilitarySymbolResolution resolveDegraded(
            MilitarySymbolId id,
            MarkerPlacement placement,
            MilitarySymbolPalette palette,
            double opacity) {
        MilitarySymbolAssessment assessment = assess(id, placement, palette, opacity);
        if (assessment.support() == MilitarySymbolSupport.UNSUPPORTED) {
            throw new MilitarySymbolException(
                    "SIDC cannot be rendered by the bounded profile",
                    assessment.problem().orElseThrow());
        }
        MilitarySymbolProblem renderingProblem =
                assessment.support() == MilitarySymbolSupport.SUPPORTED
                        ? renderingProblem(id)
                        : null;
        boolean includeEntity = assessment.support() != MilitarySymbolSupport.DEGRADED_ENTITY;
        return new MilitarySymbolResolution(
                compose(id, placement, palette, opacity, includeEntity),
                renderingProblem == null ? assessment.problem() : Optional.of(renderingProblem));
    }

    private static MilitarySymbolAssessment assess(
            MilitarySymbolId id,
            MarkerPlacement placement,
            MilitarySymbolPalette palette,
            double opacity) {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(palette, "palette");
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        return MilitarySymbolProfile.standard2525EChange1()
                .assess(Objects.requireNonNull(id, "id"));
    }

    private static Symbol compose(
            MilitarySymbolId id,
            MarkerPlacement placement,
            MilitarySymbolPalette palette,
            double opacity,
            boolean includeEntity) {
        boolean segmented =
                id.status() == 1
                        || id.standardIdentity() == 0
                        || id.standardIdentity() == 2
                        || id.standardIdentity() == 5;
        Rgba ink = palette.ink();
        SymbolStroke frameStroke =
                new SymbolStroke(ink, new SymbolLength(2.5, placement.size().unit()));
        List<Symbol> components = new ArrayList<>(3);
        components.add(
                VectorMarkerSymbol.of(
                        MilitarySymbolPaths.frame(id.standardIdentity()),
                        VIEW_BOX,
                        palette.fillForIdentity(id.standardIdentity()),
                        segmented ? Optional.empty() : Optional.of(frameStroke),
                        placement,
                        1.0));
        if (segmented) {
            components.add(
                    VectorMarkerSymbol.of(
                            MilitarySymbolPaths.segmentedFrame(id.standardIdentity()),
                            VIEW_BOX,
                            Rgba.TRANSPARENT,
                            Optional.of(frameStroke),
                            placement,
                            1.0));
        }
        if (includeEntity && id.entityCode() == FIRST_ENTITY) {
            components.add(
                    VectorMarkerSymbol.of(
                            MilitarySymbolPaths.INFANTRY,
                            VIEW_BOX,
                            Rgba.TRANSPARENT,
                            Optional.of(
                                    new SymbolStroke(
                                            ink, new SymbolLength(3.0, placement.size().unit()))),
                            placement,
                            1.0));
        }
        return CompositeSymbol.of(components, opacity);
    }

    private static MilitarySymbolProblem renderingProblem(MilitarySymbolId id) {
        if (id.entityCode() != FIRST_ENTITY) {
            return new MilitarySymbolProblem(
                    "MIL2525_RENDER_LIMIT", "entity", 11, 16, id.slice(11, 16));
        }
        if (id.sectorOneModifier() != 0) {
            return new MilitarySymbolProblem(
                    "MIL2525_RENDER_LIMIT", "sectorOneModifier", 17, 18, id.slice(17, 18));
        }
        if (id.sectorTwoModifier() != 0) {
            return new MilitarySymbolProblem(
                    "MIL2525_RENDER_LIMIT", "sectorTwoModifier", 19, 20, id.slice(19, 20));
        }
        return null;
    }
}
