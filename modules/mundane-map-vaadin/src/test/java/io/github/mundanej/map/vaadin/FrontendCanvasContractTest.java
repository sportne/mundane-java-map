package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class FrontendCanvasContractTest {
    @Test
    void bundledElementContainsClosedLocalViewportInputResizeAndPaintContract() throws IOException {
        String source;
        try (var input =
                FrontendCanvasContractTest.class
                        .getClassLoader()
                        .getResourceAsStream("META-INF/frontend/mundane-map-canvas.js")) {
            assertTrue(input != null);
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(source.contains("const PROTOCOL_VERSION = 1"));
        assertTrue(source.contains("export function validateViewport"));
        assertTrue(source.contains("export function resizeViewport"));
        assertTrue(source.contains("export function panViewport"));
        assertTrue(source.contains("export function zoomViewport"));
        assertTrue(source.contains("export function collectDrawOrder"));
        assertTrue(source.contains("new ResizeObserver"));
        assertTrue(source.contains("window.devicePixelRatio"));
        assertTrue(source.contains("MAX_BACKING_PIXELS"));
        assertTrue(source.contains("addEventListener('pointerdown'"));
        assertTrue(source.contains("addEventListener('wheel'"));
        assertTrue(source.contains("this.pointers.size === 2"));
        assertTrue(source.contains("this.context.fill('evenodd')"));
        assertTrue(source.contains("for (const layer of this.scene.layers)"));
        assertTrue(source.contains("for (const feature of layer.features)"));
        assertTrue(source.contains("for (const primitive of feature.primitives)"));
        assertTrue(source.contains("cancelAnimationFrame"));
        assertTrue(source.contains("releasePointerCapture"));
        assertTrue(source.contains("this.resizeObserver.disconnect()"));
        assertTrue(source.contains("acceptSettledViewport"));
        assertFalse(source.contains("fetch("));
        assertFalse(source.contains("eval("));
        assertFalse(source.contains("innerHTML"));
        assertFalse(source.contains("http://"));
        assertFalse(source.contains("https://"));
    }
}
