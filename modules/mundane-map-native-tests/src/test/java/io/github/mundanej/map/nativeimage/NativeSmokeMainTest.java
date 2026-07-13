package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NativeSmokeMainTest {
    @Test
    void rendersThroughTheSmokePathOnTheJvm() {
        NativeSmokeMain.runSmoke();
    }

    @Test
    void rendersWhenAlreadyOnTheEventDispatchThread() throws Exception {
        SwingUtilities.invokeAndWait(NativeSmokeMain::runSmoke);
    }

    @Test
    void sharedRenderingAssertionRejectsUntouchedImages() {
        BufferedImage transparent = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        assertThrows(
                IllegalStateException.class, () -> NativeSmokeMain.verifyRendered(transparent));

        BufferedImage white = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = white.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, white.getWidth(), white.getHeight());
        } finally {
            graphics.dispose();
        }
        assertThrows(IllegalStateException.class, () -> NativeSmokeMain.verifyRendered(white));
    }
}
