// 관제탑 여성과 세라 초상화가 초록 선으로 그려지고, 말할 때 입이 움직이며 눈을 깜빡이는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The two talking women are drawn by the same code, so they are tested together. */
class LinePortraitTest {

    /** Full source size, so one pixel of the picture is one source pixel. */
    private static final int W = (int) LinePortrait.SRC_W;
    private static final int H = (int) LinePortrait.SRC_H;

    /** Draws a portrait and gives the rectangles of its mouth and eyes. */
    private interface Portrait {
        void draw(Graphics2D g, int width, int height, double seconds,
                boolean speaking);
    }

    private record Woman(String name, String[] lines, int spaceWidth,
            int spaceHeight, Portrait portrait, Rectangle mouth,
            Rectangle eyes) {
    }

    private static final Woman[] WOMEN = {
        new Woman("control", ControllerPortraitData.LINES,
                ControllerPortraitData.SPACE_WIDTH,
                ControllerPortraitData.SPACE_HEIGHT,
                (g, w, h, t, speaking) ->
                        ControllerPortrait.draw(g, 0, 0, w, h, t, speaking),
                new Rectangle(155, 385, 90, 55),
                new Rectangle(110, 255, 180, 50)),
        new Woman("sera", SeraPortraitData.LINES,
                SeraPortraitData.SPACE_WIDTH, SeraPortraitData.SPACE_HEIGHT,
                (g, w, h, t, speaking) ->
                        SeraPortrait.draw(g, 0, 0, w, h, t, speaking),
                new Rectangle(218, 316, 54, 38),
                new Rectangle(195, 215, 125, 60))};

    private static BufferedImage render(final Woman woman, final int width,
            final double seconds, final boolean speaking) {
        int height = (int) Math.round(width * LinePortrait.SRC_H
                / LinePortrait.SRC_W);
        BufferedImage img = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        woman.portrait().draw(g, width, height, seconds, speaking);
        g.dispose();
        return img;
    }

    private static int[] pixels(final BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0,
                img.getWidth());
    }

    /** First time after the first blink where the mouth is open this much. */
    private static double timeWithMouthOpen(final double least) {
        for (double t = 0.3; t < 20; t += 0.005) {
            if (LinePortrait.mouthOpening(t, true) >= least) {
                return t;
            }
        }
        throw new AssertionError("The mouth never opens that far.");
    }

    private static double brightShare(final BufferedImage img, final int least) {
        int bright = 0;
        for (int p : pixels(img)) {
            if (((p >> 8) & 255) > least) {
                bright++;
            }
        }
        return bright / (double) (img.getWidth() * img.getHeight());
    }

    @Test
    void theTracedLinesAreVectorLoopsInsideTheirSpace() {
        for (Woman woman : WOMEN) {
            List<int[][]> loops = TracedLoops.parse(woman.lines());
            assertTrue(loops.size() > 50, woman.name() + " has loops: " + loops.size());
            int points = TracedLoops.assertInside(loops, woman.spaceWidth(),
                    woman.spaceHeight(), woman.name());
            assertTrue(points > 1000, woman.name() + " has points: " + points);
            // The space is the source space, drawn twice as fine.
            assertEquals(2 * W, woman.spaceWidth());
            assertEquals(2 * H, woman.spaceHeight());
        }
    }

    @Test
    void bothWomenHaveTheSameFrame() {
        assertEquals(ControllerPortrait.SRC_W, SeraPortrait.SRC_W, 0.0);
        assertEquals(ControllerPortrait.SRC_H, SeraPortrait.SRC_H, 0.0);
        assertEquals(LinePortrait.SRC_W, SeraPortrait.SRC_W, 0.0);
    }

    @Test
    void drawGreenLinesOnlyAndAreNotTheSamePicture() {
        for (Woman woman : WOMEN) {
            int bright = 0;
            for (int p : pixels(render(woman, W, 0.5, false))) {
                int g = (p >> 8) & 255;
                assertTrue(g >= ((p >> 16) & 255) && g >= (p & 255),
                        woman.name() + ": every pixel stays green or gray");
                if (g > 180) {
                    bright++;
                }
            }
            assertTrue(bright > W * H / 25, woman.name() + ": lines are visible: " + bright);
        }
        assertFalse(Arrays.equals(pixels(render(WOMEN[0], W, 0.5, false)),
                pixels(render(WOMEN[1], W, 0.5, false))));
    }

    @Test
    void animateOverTimeAndAreDeterministic() {
        for (Woman woman : WOMEN) {
            assertFalse(Arrays.equals(pixels(render(woman, W, 0.5, false)),
                    pixels(render(woman, W, 1.7, false))),
                    woman.name() + ": drift, flicker and the scan bar change the picture");
            assertEquals(Arrays.hashCode(pixels(render(woman, W, 2.3, true))),
                    Arrays.hashCode(pixels(render(woman, W, 2.3, true))));
        }
    }

    @Test
    void mouthStaysShutUnlessSpeaking() {
        for (double t = 0; t < 10; t += 0.05) {
            assertEquals(0.0, LinePortrait.mouthOpening(t, false), 0.0);
            assertEquals(0.0, ControllerPortrait.mouthOpening(t, false), 0.0);
            assertEquals(0.0, SeraPortrait.mouthOpening(t, false), 0.0);
        }
    }

    @Test
    void mouthOpensWideAndPausesBetweenWordsWhileSpeaking() {
        double most = 0;
        boolean paused = false;
        for (double t = 0; t < 20; t += 0.01) {
            double open = LinePortrait.mouthOpening(t, true);
            assertTrue(open >= 0 && open <= 1, "opening " + open + " at " + t);
            most = Math.max(most, open);
            paused |= open < 0.02;
        }
        assertTrue(most > 0.9, "the mouth opens wide: " + most);
        assertTrue(paused, "the mouth closes between words");
    }

    @Test
    void speakingMovesTheMouthAndNothingElse() {
        double t = timeWithMouthOpen(0.8);
        for (Woman woman : WOMEN) {
            int[] shut = pixels(render(woman, W, t, false));
            int[] talking = pixels(render(woman, W, t, true));
            int changedMouth = 0;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    boolean same = shut[y * W + x] == talking[y * W + x];
                    if (woman.mouth().contains(x, y) && !same) {
                        changedMouth++;
                    } else {
                        assertTrue(same, woman.name() + ": pixel (" + x + "," + y
                                + ") changed outside the mouth");
                    }
                }
            }
            assertTrue(changedMouth > 60, woman.name() + ": the mouth changes: " + changedMouth);
        }
    }

    @Test
    void blinkNowAndThenAndTheLidsCloseOverTheEyes() {
        assertEquals(0.0, LinePortrait.blink(1.0), 0.0);
        assertEquals(0.0, LinePortrait.blink(3.0), 0.0);
        assertTrue(LinePortrait.blink(0.08) > 0.9, "mid blink is shut");
        // The same blink comes again one period later.
        assertEquals(LinePortrait.blink(0.08), LinePortrait.blink(0.08 + 3.7), 1e-9);
        for (double t = 0; t < 20; t += 0.01) {
            double closed = LinePortrait.blink(t);
            assertTrue(closed >= 0 && closed <= 1, "closed " + closed);
        }
        for (Woman woman : WOMEN) {
            int[] open = pixels(render(woman, W, 1.0, false));
            int[] shut = pixels(render(woman, W, 0.08, false));
            int changedEyes = 0;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (open[y * W + x] != shut[y * W + x]
                            && woman.eyes().contains(x, y)) {
                        changedEyes++;
                    }
                }
            }
            assertTrue(changedEyes > 100, woman.name() + ": the lids close: " + changedEyes);
        }
    }

    @Test
    void theLinesStayVisibleAtAnySizeAndAtTheDialogueSize() {
        for (Woman woman : WOMEN) {
            // Vector lines are drawn again at each size instead of stretched.
            for (int width : new int[] {80, 115, 200, 400, 800}) {
                double share = brightShare(render(woman, width, 0.5, false), 140);
                assertTrue(share > 0.03 && share < 0.45,
                        woman.name() + " at width " + width + ": " + share);
            }
            // 115 x 160 is the size of the portrait in the story dialogue.
            BufferedImage small = render(woman, 115, 1.0, true);
            assertTrue(brightShare(small, 150) * small.getWidth() * small.getHeight() > 600,
                    woman.name() + ": lines stay visible when drawn small");
        }
    }
}
