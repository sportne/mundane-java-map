package io.github.mundanej.map.awt;

import java.awt.image.BufferedImage;

@FunctionalInterface
interface RenderInvariant {
    void verify(String scenarioId, BufferedImage image);
}
