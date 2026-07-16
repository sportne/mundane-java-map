package io.github.mundanej.map.awt;

/** Package-private evidence control; public views use every evidence-retained partition. */
enum AwtRenderCacheMode {
    DISABLED(false),
    VECTOR_TEMPLATE(true);

    private final boolean vectorTemplates;

    AwtRenderCacheMode(boolean vectorTemplates) {
        this.vectorTemplates = vectorTemplates;
    }

    boolean vectorTemplates() {
        return vectorTemplates;
    }

    boolean enabled() {
        return vectorTemplates;
    }
}
