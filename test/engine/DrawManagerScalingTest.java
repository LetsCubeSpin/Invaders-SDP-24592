// 화면 배율이 125% 같은 소수일 때 가는 테두리 선이 중간에 한 칸씩 어긋나지 않게 키워서 보여 주는지 확인하는 테스트
package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DrawManagerScalingTest {

    private static final int GREEN = Color.GREEN.getRGB() & 0xFFFFFF;

    /** A black picture with the border of a box, as a dialogue box has. */
    private static BufferedImage box() {
        BufferedImage img = new BufferedImage(448, 520,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 448, 520);
        g.setColor(Color.GREEN);
        g.drawRect(179, 44, 263, 159);
        g.dispose();
        return img;
    }

    private static int greenRowsAtColumn(final BufferedImage img, final int x,
            final int fromY, final int toY) {
        int count = 0;
        for (int y = fromY; y <= toY; y++) {
            if ((img.getRGB(x, y) & 0xFFFFFF) == GREEN) {
                count++;
            }
        }
        return count;
    }

    private static int greenColumnsAtRow(final BufferedImage img, final int y,
            final int fromX, final int toX) {
        int count = 0;
        for (int x = fromX; x <= toX; x++) {
            if ((img.getRGB(x, y) & 0xFFFFFF) == GREEN) {
                count++;
            }
        }
        return count;
    }

    @Test
    void onlyWholeNumbersAreWholeScales() {
        for (double scale : new double[] {1.0, 2.0, 3.0, 1.0000000001}) {
            assertTrue(DrawManager.isWholeScale(scale), "scale " + scale);
        }
        for (double scale : new double[] {1.1, 1.2, 1.25, 1.5, 1.75, 2.5}) {
            assertFalse(DrawManager.isWholeScale(scale), "scale " + scale);
        }
    }

    @Test
    void theBorderKeepsTheSameThicknessAllAlongItsLength() {
        BufferedImage source = box();
        for (double scale : new double[] {1.1, 1.2, 1.25, 1.5, 1.75, 2.25}) {
            int width = (int) Math.round(448 * scale);
            int height = (int) Math.round(520 * scale);
            BufferedImage scaled = DrawManager.scaleNearest(source, width,
                    height, null);
            assertEquals(width, scaled.getWidth());
            assertEquals(height, scaled.getHeight());

            // Top border: the rows it covers are the same in every column
            // between the corners.
            int fromX = (int) Math.ceil(185 * scale);
            int toX = (int) Math.floor(437 * scale);
            int topFrom = (int) Math.floor(40 * scale);
            int topTo = (int) Math.ceil(49 * scale);
            int thickness = greenRowsAtColumn(scaled, fromX, topFrom, topTo);
            assertTrue(thickness >= 1, "the top border is there at " + scale);
            for (int x = fromX; x <= toX; x++) {
                assertEquals(thickness,
                        greenRowsAtColumn(scaled, x, topFrom, topTo),
                        "top border, scale " + scale + ", column " + x);
            }

            // Left border, the same down its length.
            int fromY = (int) Math.ceil(50 * scale);
            int toY = (int) Math.floor(197 * scale);
            int leftFrom = (int) Math.floor(175 * scale);
            int leftTo = (int) Math.ceil(184 * scale);
            int width1 = greenColumnsAtRow(scaled, fromY, leftFrom, leftTo);
            assertTrue(width1 >= 1, "the left border is there at " + scale);
            for (int y = fromY; y <= toY; y++) {
                assertEquals(width1,
                        greenColumnsAtRow(scaled, y, leftFrom, leftTo),
                        "left border, scale " + scale + ", row " + y);
            }
        }
    }

    @Test
    void scalingBlursNothing() {
        BufferedImage scaled = DrawManager.scaleNearest(box(), 560, 650, null);
        Set<Integer> colors = new HashSet<Integer>();
        for (int y = 0; y < 650; y++) {
            for (int x = 0; x < 560; x++) {
                colors.add(scaled.getRGB(x, y) & 0xFFFFFF);
            }
        }
        assertEquals(Set.of(0, GREEN), colors, "only black and green");
    }

    @Test
    void anEarlierPictureOfTheSameSizeIsReused() {
        BufferedImage first = DrawManager.scaleNearest(box(), 560, 650, null);
        assertSame(first, DrawManager.scaleNearest(box(), 560, 650, first));
        assertNotSame(first, DrawManager.scaleNearest(box(), 672, 780, first));
    }
}
