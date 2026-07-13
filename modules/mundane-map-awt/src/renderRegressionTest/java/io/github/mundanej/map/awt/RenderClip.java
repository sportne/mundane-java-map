package io.github.mundanej.map.awt;

record RenderClip(int x, int y, int width, int height) {
    RenderClip {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "clip must have non-negative origin and positive size");
        }
    }
}
