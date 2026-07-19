package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.TextAttribute;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PointLabelTextResolverTest {
    private static final Symbol MARKER = new TestSymbol();

    @Test
    void resolvesExactFeatureNameAndTextAttributeWithOrdinaryOmissions() {
        FeaturePortrayalResolver names = resolver(FeatureName.INSTANCE, ResolutionRange.ALL);
        assertEquals(Optional.of("  exact  "), names.resolveLabelText("  exact  ", Map.of(), 1));
        assertEquals(Optional.empty(), names.resolveLabelText("  ", Map.of(), 1));

        FeaturePortrayalResolver attributes =
                resolver(new TextAttribute("label"), ResolutionRange.ALL);
        assertEquals(
                Optional.of("  exact  "),
                attributes.resolveLabelText("name", Map.of("label", "  exact  "), 1));
        assertEquals(Optional.empty(), attributes.resolveLabelText("name", Map.of(), 1));
        assertEquals(
                Optional.empty(),
                attributes.resolveLabelText("name", Map.of("label", AttributeNull.INSTANCE), 1));
        assertEquals(
                Optional.empty(), attributes.resolveLabelText("name", Map.of("label", 10L), 1));
        assertEquals(
                Optional.empty(), attributes.resolveLabelText("name", Map.of("label", "\t"), 1));
    }

    @Test
    void usesInclusiveResolutionAndOrderedUniquePaintProjection() {
        FeaturePortrayalResolver resolver =
                resolver(new TextAttribute("symbol"), new ResolutionRange(0.5, 2));

        assertEquals(List.of("symbol"), resolver.requiredConfigurationAttributes());
        assertEquals(List.of("symbol"), resolver.requiredPaintAttributes(0.5));
        assertEquals(List.of("symbol"), resolver.requiredPaintAttributes(2));
        assertEquals(List.of(), resolver.requiredPaintAttributes(0.49));
        assertEquals(
                Optional.of("edge"),
                resolver.resolveLabelText("name", Map.of("symbol", "edge"), 0.5));
        assertEquals(
                Optional.of("edge"),
                resolver.resolveLabelText("name", Map.of("symbol", "edge"), 2));
        assertEquals(
                Optional.empty(),
                resolver.resolveLabelText("name", Map.of("symbol", "edge"), 2.01));
    }

    @Test
    void appendsDistinctVisibleLabelAttributeAfterSymbolProjection() {
        io.github.mundanej.map.api.CategoricalSymbolSelector selector =
                new io.github.mundanej.map.api.CategoricalSymbolSelector(
                        "kind",
                        List.of(
                                new io.github.mundanej.map.api.CategoricalSymbolRule(
                                        io.github.mundanej.map.api.ThematicValue.text("point"),
                                        MARKER)),
                        Optional.of(MARKER));
        PointLabelProfile profile = profile(new TextAttribute("label"), ResolutionRange.ALL);
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        FeaturePortrayal.markers(selector).withPointLabel(profile));

        assertEquals(List.of("kind"), resolver.requiredSymbolAttributes());
        assertEquals(List.of("kind", "label"), resolver.requiredPaintAttributes(1));
        assertEquals(List.of("kind", "label"), resolver.requiredConfigurationAttributes());
    }

    private static FeaturePortrayalResolver resolver(
            io.github.mundanej.map.api.LabelTextSource source, ResolutionRange range) {
        return FeaturePortrayalResolver.compile(
                FeaturePortrayal.markers(new FixedSymbolSelector(MARKER))
                        .withPointLabel(profile(source, range)));
    }

    private static PointLabelProfile profile(
            io.github.mundanej.map.api.LabelTextSource source, ResolutionRange range) {
        return new PointLabelProfile(
                source,
                new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.NORMAL, 12),
                List.of(PointLabelPosition.NE),
                4,
                0,
                0,
                1,
                0,
                range);
    }

    private record TestSymbol(SymbolRole role, SymbolRendererKey rendererKey) implements Symbol {
        private TestSymbol() {
            this(SymbolRole.MARKER, new SymbolRendererKey("test.label-marker"));
        }

        @Override
        public double opacity() {
            return 1;
        }
    }
}
