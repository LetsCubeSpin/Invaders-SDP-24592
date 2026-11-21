// 추적 벡터 조종사 초상화가 비어 있지 않게 그려지고 애니메이션으로 변하는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PilotPortraitTracedTest {

    private static final int W = 300;
    private static final int H = (int) Math.round(W * PilotPortraitTraced.SRC_H
            / PilotPortraitTraced.SRC_W);

    private static BufferedImage render(final double seconds) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);
        PilotPortraitTraced.draw(g, 0, 0, W, H, seconds);
        g.dispose();
        return img;
    }

    private static int[] pixels(final BufferedImage img) {
        return img.getRGB(0, 0, W, H, null, 0, W);
    }

    @Test
    void drawsGreenLinesOnly() {
        int bright = 0;
        for (int p : pixels(render(0.5))) {
            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;
            assertTrue(g >= r && g >= b, "every pixel stays green or gray");
            if (g > 180) {
                bright++;
            }
        }
        assertTrue(bright > W * H / 40, "outline lines should be visible");
    }

    @Test
    void animatesOverTime() {
        assertFalse(Arrays.equals(pixels(render(0.0)), pixels(render(1.7))),
                "head sway and glint change the picture");
    }

    @Test
    void isDeterministicForTheSameTime() {
        assertEquals(Arrays.hashCode(pixels(render(2.3))),
                Arrays.hashCode(pixels(render(2.3))));
    }

    @Test
    void glintBrightensTheLensOnlyDuringTheSweep() {
        long sweeping = lensBrightness(render(0.5));
        long resting = lensBrightness(render(3.5));
        assertTrue(sweeping > resting, "a light band crosses the goggle");
    }

    /** Sums green values over the goggle lens area of the frame. */
    private static long lensBrightness(final BufferedImage img) {
        long sum = 0;
        for (int y = (int) (H * 0.30); y < H * 0.56; y++) {
            for (int x = (int) (W * 0.10); x < W * 0.72; x++) {
                sum += (img.getRGB(x, y) >> 8) & 255;
            }
        }
        return sum;
    }
}
