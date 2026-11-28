// 관제탑 직원 초상화가 초록 선으로 그려지고, 말할 때 입이 움직이며 눈을 깜빡이는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControllerPortraitTest {

    /** Full source size, so one pixel of the picture is one source pixel. */
    private static final int W = (int) ControllerPortrait.SRC_W;
    private static final int H = (int) ControllerPortrait.SRC_H;

    private static BufferedImage render(final int width, final double seconds,
            final boolean speaking) {
        int height = (int) Math.round(width * ControllerPortrait.SRC_H
                / ControllerPortrait.SRC_W);
        BufferedImage img = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        ControllerPortrait.draw(g, 0, 0, width, height, seconds, speaking);
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
            if (ControllerPortrait.mouthOpening(t, true) >= least) {
                return t;
            }
        }
        throw new AssertionError("The mouth never opens that far.");
    }

    /** Reads the traced loops of one strength of ink: each loop is a list of points. */
    private static List<int[][]> loops(final String[] chunks) {
        List<int[][]> result = new ArrayList<int[][]>();
        for (String loop : String.join("", chunks).split(";")) {
            String[] tok = loop.trim().split("\\s+");
            assertTrue(tok.length >= 6 && tok.length % 2 == 0,
                    "a loop has at least three points: " + tok.length);
            int n = tok.length / 2;
            int[][] points = new int[n][2];
            int x = 0;
            int y = 0;
            for (int i = 0; i < n; i++) {
                x += Integer.parseInt(tok[2 * i]);
                y += Integer.parseInt(tok[2 * i + 1]);
                points[i][0] = x;
                points[i][1] = y;
            }
            result.add(points);
        }
        return result;
    }

    @Test
    void theTracedLinesAreVectorLoopsInsideTheirSpace() {
        String[][] levels = ControllerPortraitData.LEVELS;
        assertTrue(levels.length >= 2, "faint and strong ink are traced apart");
        for (int level = 0; level < levels.length; level++) {
            List<int[][]> loops = loops(levels[level]);
            assertTrue(loops.size() > 50, "level " + level + " has loops: " + loops.size());
            int points = 0;
            for (int[][] loop : loops) {
                points += loop.length;
                for (int[] point : loop) {
                    assertTrue(point[0] >= 0 && point[0] <= ControllerPortraitData.SPACE_WIDTH
                            && point[1] >= 0 && point[1] <= ControllerPortraitData.SPACE_HEIGHT,
                            "point (" + point[0] + "," + point[1] + ") of level " + level);
                }
            }
            assertTrue(points > 1000, "level " + level + " has points: " + points);
        }
        // The space is the source space, drawn twice as fine.
        assertEquals(2 * W, ControllerPortraitData.SPACE_WIDTH);
        assertEquals(2 * H, ControllerPortraitData.SPACE_HEIGHT);
    }

    @Test
    void noPictureFileIsNeededAnymore() {
        assertNull(ControllerPortraitTest.class.getResource("/story/controller_head.png"));
        assertNull(ControllerPortraitTest.class
                .getResource("/story/controller_head_small.png"));
    }

    @Test
    void theLinesStayVisibleAtAnySize() {
        // Vector lines are drawn again at each size instead of stretched, so
        // a big portrait has as many lines as a small one.
        for (int width : new int[] {80, 115, 200, 400, 800}) {
            int bright = 0;
            BufferedImage img = render(width, 0.5, false);
            for (int p : pixels(img)) {
                if (((p >> 8) & 255) > 140) {
                    bright++;
                }
            }
            double share = bright / (double) (img.getWidth() * img.getHeight());
            assertTrue(share > 0.03 && share < 0.45, "width " + width + ": " + share);
        }
    }

    @Test
    void drawsGreenLinesOnly() {
        int bright = 0;
        for (int p : pixels(render(W, 0.5, false))) {
            int r = (p >> 16) & 255;
            int g = (p >> 8) & 255;
            int b = p & 255;
            assertTrue(g >= r && g >= b, "every pixel stays green or gray");
            if (g > 180) {
                bright++;
            }
        }
        assertTrue(bright > W * H / 25, "the line drawing should be visible");
    }

    @Test
    void animatesOverTime() {
        assertFalse(Arrays.equals(pixels(render(W, 0.5, false)),
                pixels(render(W, 1.7, false))),
                "drift, flicker and the scan bar change the picture");
    }

    @Test
    void isDeterministicForTheSameTime() {
        assertEquals(Arrays.hashCode(pixels(render(W, 2.3, true))),
                Arrays.hashCode(pixels(render(W, 2.3, true))));
    }

    @Test
    void mouthStaysShutUnlessSpeaking() {
        for (double t = 0; t < 10; t += 0.05) {
            assertEquals(0.0, ControllerPortrait.mouthOpening(t, false), 0.0);
        }
    }

    @Test
    void mouthOpensWideAndPausesBetweenWordsWhileSpeaking() {
        double most = 0;
        boolean paused = false;
        for (double t = 0; t < 20; t += 0.01) {
            double open = ControllerPortrait.mouthOpening(t, true);
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
        int[] shut = pixels(render(W, t, false));
        int[] talking = pixels(render(W, t, true));
        int changedMouth = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean same = shut[y * W + x] == talking[y * W + x];
                boolean inMouth = x >= 155 && x < 245 && y >= 385 && y < 440;
                if (inMouth && !same) {
                    changedMouth++;
                } else {
                    assertTrue(same, "pixel (" + x + "," + y + ") changed outside the mouth");
                }
            }
        }
        assertTrue(changedMouth > 100, "the mouth area changes: " + changedMouth);
    }

    @Test
    void blinksNowAndThenAndOtherwiseKeepsTheEyesOpen() {
        assertEquals(0.0, ControllerPortrait.blink(1.0), 0.0);
        assertEquals(0.0, ControllerPortrait.blink(3.0), 0.0);
        assertTrue(ControllerPortrait.blink(0.08) > 0.9, "mid blink is shut");
        // The same blink comes again one period later.
        assertEquals(ControllerPortrait.blink(0.08),
                ControllerPortrait.blink(0.08 + 3.7), 1e-9);
        for (double t = 0; t < 20; t += 0.01) {
            double closed = ControllerPortrait.blink(t);
            assertTrue(closed >= 0 && closed <= 1, "closed " + closed);
        }
    }

    @Test
    void drawsAtTheStoryDialogueSizeToo() {
        // 115 x 160 is the size of the portrait in the story dialogue.
        BufferedImage img = render(115, 1.0, true);
        int bright = 0;
        for (int p : pixels(img)) {
            if (((p >> 8) & 255) > 150) {
                bright++;
            }
        }
        assertTrue(bright > 600, "lines stay visible when drawn small: " + bright);
    }
}
