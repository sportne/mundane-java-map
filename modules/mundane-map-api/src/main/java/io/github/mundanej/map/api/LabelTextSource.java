package io.github.mundanej.map.api;

/** Closed literal, feature-name, or attribute source for one singular-point label's text. */
public sealed interface LabelTextSource
        permits FeatureName, LiteralLabelText, StringifiedTextAttribute, TextAttribute {}
