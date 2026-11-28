// 초상화들이 함께 쓰는 초록 단색 그림체의 색, 완만한 단계 함수, 스캔라인 덮개를 모아 둔 클래스
package story;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * What the talking portraits share: the colors of the green phosphor screen,
 * a smooth step, and the scan lines and flicker drawn over a portrait.
 */
final class PortraitStyle {

    /** The dark of the screen. */
    static final Color BACK = new Color(2, 12, 4);
    /** The full green of a line. */
    static final Color BRIGHT = new Color(90, 255, 110);

    private PortraitStyle() {
    }

    /** Smooth step from 0 to 1 between two values. */
    static double ease(final double v, final double from, final double to) {
        double a = Math.max(0, Math.min(1, (v - from) / (to - from)));
        return a * a * (3 - 2 * a);
    }

    /** Scan lines, a slow scan bar and a faint flicker. */
    static void paintOverlay(final Graphics2D g, final int x, final int y,
            final int w, final int h, final double t) {
        g.setColor(new Color(0, 0, 0, 70));
        for (int ly = y + 2; ly < y + h; ly += 3) {
            g.fillRect(x, ly, w, 1);
        }
        double barPos = ((t % 5.0) / 5.0) * (h + 40) - 20;
        g.setColor(new Color(110, 255, 140, 16));
        g.fillRect(x, (int) (y + barPos), w, 14);
        double flick = 0.5 + 0.5 * Math.sin(t * 37) * Math.sin(t * 11);
        g.setColor(new Color(110, 255, 140, (int) (4 + 8 * flick)));
        g.fillRect(x, y, w, h);
    }
}
