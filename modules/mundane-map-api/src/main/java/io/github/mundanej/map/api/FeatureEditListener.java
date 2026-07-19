package io.github.mundanej.map.api;

/** Receives one synchronous notification after an edit revision commits. */
@FunctionalInterface
public interface FeatureEditListener {
    /**
     * Receives an immutable committed transition.
     *
     * @param event committed event
     */
    void onFeatureEdit(FeatureEditEvent event);
}
