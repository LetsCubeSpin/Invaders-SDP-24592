// 스테이지 3 보스 초상화가 초록 선으로 그려지고 영상에서 본 포효, 눈 번쩍임, 기울임이 시각에 맞게 움직이는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BossPortraitTest {

    private static final int SIZE = (int) BossPortrait.SRC_W;

    private static BufferedImage render(final int boss, final int size,
            final double seconds) {
        BufferedImage img = new BufferedImage(size, size,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, size, size);
        BossPortrait.draw(g, boss, 0, 0, size, size, seconds);
        g.dispose();
        return img;
    }

    private static int[] pixels(final BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0,
                img.getWidth());
    }

    /** Lowest row, in the middle columns, that has a bright line pixel. */
    private static int lowestBrightRow(final BufferedImage img, final int fromX,
            final int toX) {
        for (int y = img.getHeight() - 1; y >= 0; y--) {
            for (int x = fromX; x < toX; x++) {
                if (((img.getRGB(x, y) >> 8) & 255) > 170) {
                    return y;
                }
            }
        }
        return -1;
    }

    @Test
    void thereIsOnePortraitForEachFirstAnswer() {
        assertEquals(2, BossPortrait.count());
        assertEquals(7.0, BossPortrait.loopSeconds(0), 0.0);
        assertEquals(4.0, BossPortrait.loopSeconds(1), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> BossPortrait.loopSeconds(2));
        assertThrows(IllegalArgumentException.class,
                () -> BossPortrait.draw(null, -1, 0, 0, 1, 1, 0));
    }

    @Test
    void lineImagesAreOnTheClasspathInTheSourceSize() throws Exception {
        for (int boss = 0; boss < BossPortrait.count(); boss++) {
            BufferedImage large = ImageIO.read(BossPortraitTest.class
                    .getResource("/story/boss_" + boss + ".png"));
            BufferedImage small = ImageIO.read(BossPortraitTest.class
                    .getResource("/story/boss_" + boss + "_small.png"));
            assertNotNull(large, "boss " + boss);
            assertNotNull(small, "small boss " + boss);
            assertEquals(SIZE, large.getWidth());
            assertEquals(SIZE, large.getHeight());
            assertEquals(SIZE / 2, small.getWidth());
            assertEquals(SIZE / 2, small.getHeight());
        }
    }

    @Test
    void drawsGreenLinesOnly() {
        for (int boss = 0; boss < BossPortrait.count(); boss++) {
            int bright = 0;
            for (int p : pixels(render(boss, SIZE, 0.3))) {
                int r = (p >> 16) & 255;
                int g = (p >> 8) & 255;
                int b = p & 255;
                assertTrue(g >= r && g >= b, "every pixel stays green or gray");
                if (g > 170) {
                    bright++;
                }
            }
            assertTrue(bright > SIZE * SIZE / 40,
                    "boss " + boss + " lines are visible: " + bright);
        }
    }

    @Test
    void drawsAtTheSizeOfTheStoryDialogueToo() {
        // The dialogue portrait box is 160 high, and the boss's is square.
        for (int boss = 0; boss < BossPortrait.count(); boss++) {
            int bright = 0;
            for (int p : pixels(render(boss, 160, 0.3))) {
                if (((p >> 8) & 255) > 150) {
                    bright++;
                }
            }
            assertTrue(bright > 900, "boss " + boss + " small: " + bright);
        }
    }

    @Test
    void boss0RoarsThreeTimesInItsLoop() {
        assertTrue(BossPortrait.roar(0, 2.5) > 0.99);
        assertTrue(BossPortrait.roar(0, 4.7) > 0.99);
        assertTrue(BossPortrait.roar(0, 5.7) > 0.99);
        assertEquals(0.0, BossPortrait.roar(0, 0.5), 0.0);
        assertEquals(0.0, BossPortrait.roar(0, 3.6), 0.0);
        assertEquals(0.0, BossPortrait.roar(0, 6.6), 0.0);
        // Its eyes flash with the roars.
        assertEquals(BossPortrait.roar(0, 2.5), BossPortrait.eyeFlash(0, 2.5), 0.0);
        assertEquals(0.0, BossPortrait.eyeFlash(0, 0.5), 0.0);
    }

    @Test
    void boss1FlashesItsEyesTwiceAndSnarlsOnTheFirstFlash() {
        assertTrue(BossPortrait.eyeFlash(1, 1.35) > 0.99);
        assertTrue(BossPortrait.eyeFlash(1, 2.3) > 0.99);
        assertEquals(0.0, BossPortrait.eyeFlash(1, 0.3), 0.0);
        assertEquals(0.0, BossPortrait.eyeFlash(1, 3.0), 0.0);
        assertTrue(BossPortrait.roar(1, 1.45) > 0.99, "snarls with the first flash");
        assertEquals(0.0, BossPortrait.roar(1, 2.3), 0.0);
    }

    @Test
    void boss1TiltsItsHeadNearTheEndOfTheLoopAndStraightensAgain() {
        double tilted = BossPortrait.tilt(1, 3.45);
        assertTrue(Math.abs(tilted) > Math.toRadians(2.5), "tilted: " + tilted);
        assertTrue(Math.abs(BossPortrait.tilt(1, 1.0)) < Math.toRadians(1.0));
        // The loop ends straight, so it can start again without a jump.
        assertEquals(BossPortrait.tilt(1, 0.0) - 0.010 * Math.sin(0.0),
                BossPortrait.tilt(1, 4.0) - 0.010 * Math.sin(4.0 * 0.9), 1e-9);
    }

    @Test
    void theMovementsLoop() {
        for (int boss = 0; boss < BossPortrait.count(); boss++) {
            double loop = BossPortrait.loopSeconds(boss);
            for (double t = 0; t < loop; t += 0.25) {
                assertEquals(BossPortrait.roar(boss, t),
                        BossPortrait.roar(boss, t + loop), 1e-9);
                assertEquals(BossPortrait.eyeFlash(boss, t),
                        BossPortrait.eyeFlash(boss, t + 3 * loop), 1e-9);
            }
            // Any time gives a value from 0 to 1, even before the start.
            for (double t = -10; t < 20; t += 0.37) {
                double jaw = BossPortrait.jawOpening(boss, t);
                assertTrue(jaw >= 0 && jaw <= 1, "jaw " + jaw + " at " + t);
            }
        }
    }

    @Test
    void theJawOpensWideWhileItRoarsAndStaysNearlyShutOtherwise() {
        assertTrue(BossPortrait.jawOpening(0, 2.5) > 0.99);
        assertTrue(BossPortrait.jawOpening(0, 0.5) < 0.2);
        assertTrue(BossPortrait.jawOpening(1, 1.45) > 0.99);
        assertTrue(BossPortrait.jawOpening(1, 0.5) < 0.1);
    }

    @Test
    void boss0LowerJawReallyDropsWhenItRoars() {
        // The jaw is the lowest part of the picture in the middle columns.
        int rest = lowestBrightRow(render(0, SIZE, 0.5), 160, 240);
        int roar = lowestBrightRow(render(0, SIZE, 2.5), 160, 240);
        assertTrue(roar >= rest + 8,
                "the chin goes from row " + rest + " to " + roar);
    }

    @Test
    void theEyesFlashAndTheyDoNotWhenCalm() {
        // Boss 1's left eye is around (67, 100).
        int calm = brightness(render(1, SIZE, 0.3), 50, 90, 90, 112);
        int flash = brightness(render(1, SIZE, 1.35), 50, 90, 90, 112);
        assertTrue(flash > calm * 1.3, "calm " + calm + ", flash " + flash);
    }

    private static int brightness(final BufferedImage img, final int x0,
            final int y0, final int x1, final int y1) {
        int sum = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                sum += (img.getRGB(x, y) >> 8) & 255;
            }
        }
        return sum;
    }

    @Test
    void theTwoBossesLookDifferentAndEachMovesOverTime() {
        assertFalse(Arrays.equals(pixels(render(0, SIZE, 0.5)),
                pixels(render(1, SIZE, 0.5))));
        for (int boss = 0; boss < BossPortrait.count(); boss++) {
            assertFalse(Arrays.equals(pixels(render(boss, SIZE, 0.3)),
                    pixels(render(boss, SIZE, 1.4))), "boss " + boss);
        }
    }

    @Test
    void isDeterministicForTheSameTime() {
        assertEquals(Arrays.hashCode(pixels(render(0, SIZE, 2.5))),
                Arrays.hashCode(pixels(render(0, SIZE, 2.5))));
        assertEquals(Arrays.hashCode(pixels(render(1, SIZE, 3.45))),
                Arrays.hashCode(pixels(render(1, SIZE, 3.45))));
    }
}
