// 스테이지 3 보스 초상화를 초록 선 그림체로 그리고, 영상에서 본 움직임(포효, 눈 번쩍임, 고개 기울임)을 입히는 렌더러
package story;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Portraits of the two bosses of stage 3, one for each answer to the first
 * story question, in the same green phosphor style as the pilot and the woman
 * at the control tower. The lines come from a drawing
 * ({@code /story/boss_0.png} and {@code /story/boss_1.png}); the scan lines,
 * the flicker and the movements are drawn here. The movements follow the two
 * reference videos:
 * <ul>
 * <li>boss 0 roars three times in a loop of seven seconds: the lower jaw drops
 * and the inside of the mouth and the eyes flash, while the head shakes;</li>
 * <li>boss 1 flashes its eyes twice in a loop of four seconds and snarls on
 * the first flash, then tilts its head slowly and straightens it again.</li>
 * </ul>
 * All geometry lives in a {@link #SRC_W} x {@link #SRC_H} source space.
 */
public final class BossPortrait {

    /** Width of the source space, the size of the line images. */
    public static final double SRC_W = 340;
    /** Height of the source space. */
    public static final double SRC_H = 340;

    private static final Color BACK = new Color(2, 12, 4);
    private static final Color GLOW = new Color(120, 255, 140);

    private static final String[] RESOURCES = {
        "/story/boss_0.png", "/story/boss_1.png"};
    /** Half-size copies, used when a portrait is drawn small. */
    private static final String[] SMALL_RESOURCES = {
        "/story/boss_0_small.png", "/story/boss_1_small.png"};
    /** On-screen scale below which the small copy is used. */
    private static final double SMALL_BELOW = 0.65;

    /** Seconds after which the movements of each boss start again. */
    private static final double[] LOOP_SECONDS = {7.0, 4.0};

    private static final BufferedImage[] LINES = {load(0), load(1)};
    private static final BufferedImage[] SMALL = {loadSmall(0), loadSmall(1)};

    /**
     * Where the jaw of each boss moves, as left edge, right edge, the rows
     * over which the jaw starts to follow (from, to), and the rows over which
     * it stops again (from, to), or -1 when it follows to the bottom. The
     * rows between the two start values are stretched, so the lines of the
     * mouth are pulled open instead of cut. Boss 0 drops its whole lower jaw,
     * boss 1 its chin and lower lip.
     */
    private static final int[][] JAW_ZONES = {
        {143, 262, 152, 212, -1, -1},
        {137, 227, 212, 236, 296, 322}};
    /** Most the jaws drop, in source pixels. */
    private static final double[] MAX_DROP = {17, 11};

    /** Inside of the mouth, which flashes. */
    private static final Ellipse2D[] MOUTH_GLOW = {
        new Ellipse2D.Double(150, 150, 90, 76),
        new Ellipse2D.Double(142, 214, 76, 20)};
    /** Eyes of each boss, which flash. */
    private static final Shape[][] EYES = {
        {new Ellipse2D.Double(131, 80, 20, 20),
            new Ellipse2D.Double(205, 67, 22, 22)},
        {eye(45.8, 96.8, 63.7, 91.1, 82.6, 94.4, 88.8, 110.5, 70.8, 111.4,
                51.9, 106.2),
            eye(313.0, 96.8, 295.1, 91.1, 276.2, 94.4, 270.0, 110.5, 288.0,
                    111.4, 306.9, 106.2)}};

    private BossPortrait() {
    }

    private static Shape eye(final double... xy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        path.closePath();
        return path;
    }

    private static BufferedImage read(final String resource) {
        try (InputStream in = BossPortrait.class
                .getResourceAsStream(resource)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException exception) {
            return null;
        }
    }

    private static BufferedImage load(final int boss) {
        return read(RESOURCES[boss]);
    }

    private static BufferedImage loadSmall(final int boss) {
        return read(SMALL_RESOURCES[boss]);
    }

    /** @return how many bosses have a portrait, one for each first answer. */
    public static int count() {
        return RESOURCES.length;
    }

    /**
     * @param boss
     *            Boss of stage 3: the first answer, 0 or 1, that leads to it.
     * @return seconds after which its movements start again.
     */
    public static double loopSeconds(final int boss) {
        return LOOP_SECONDS[checkBoss(boss)];
    }

    private static int checkBoss(final int boss) {
        if (boss < 0 || boss >= RESOURCES.length)
            throw new IllegalArgumentException("boss must be 0 or 1.");
        return boss;
    }

    /** A smooth pulse from 0 up to 1 and back, around a time. */
    private static double bump(final double t, final double center,
            final double width) {
        double d = Math.abs(t - center);
        return d >= width / 2 ? 0 : 0.5 * (1 + Math.cos(2 * Math.PI * d / width));
    }

    private static double loopTime(final int boss, final double seconds) {
        double loop = LOOP_SECONDS[boss];
        return ((seconds % loop) + loop) % loop;
    }

    /**
     * How strongly the boss roars or snarls, from 0 to 1: boss 0 roars at
     * 2.5 s, 4.7 s and 5.7 s of its loop, boss 1 snarls at 1.45 s.
     *
     * @param boss
     *            Boss of stage 3, 0 or 1.
     * @param seconds
     *            Animation time.
     * @return the strength of the roar.
     */
    public static double roar(final int boss, final double seconds) {
        double t = loopTime(checkBoss(boss), seconds);
        if (boss == 0) {
            return Math.max(bump(t, 2.5, 0.9),
                    Math.max(bump(t, 4.7, 0.6), bump(t, 5.7, 0.6)));
        }
        return bump(t, 1.45, 0.8);
    }

    /**
     * How strongly the eyes flash, from 0 to 1: boss 0 flashes them with its
     * roars, boss 1 at 1.35 s and 2.3 s of its loop.
     *
     * @param boss
     *            Boss of stage 3, 0 or 1.
     * @param seconds
     *            Animation time.
     * @return the strength of the flash.
     */
    public static double eyeFlash(final int boss, final double seconds) {
        double t = loopTime(checkBoss(boss), seconds);
        if (boss == 0) {
            return roar(0, seconds);
        }
        return Math.max(bump(t, 1.35, 0.6), bump(t, 2.3, 0.35));
    }

    /**
     * How far the lower jaw is open, from 0 (shut) to 1 (wide), counting the
     * small chatter of the teeth between roars.
     *
     * @param boss
     *            Boss of stage 3, 0 or 1.
     * @param seconds
     *            Animation time.
     * @return how open the jaw is.
     */
    public static double jawOpening(final int boss, final double seconds) {
        double roar = roar(boss, seconds);
        double chatter = boss == 0
                ? 0.10 + 0.07 * Math.sin(seconds * 7.3) : 0.04;
        return Math.max(0, Math.min(1, chatter * (1 - roar) + roar));
    }

    /**
     * How far the head is tilted, in radians: a slow sway, plus a tilt of
     * boss 1 near the end of its loop and a shake of boss 0 while it roars.
     *
     * @param boss
     *            Boss of stage 3, 0 or 1.
     * @param seconds
     *            Animation time.
     * @return the tilt.
     */
    public static double tilt(final int boss, final double seconds) {
        double t = loopTime(checkBoss(boss), seconds);
        double sway = 0.010 * Math.sin(seconds * 0.9);
        if (boss == 0) {
            return sway + 0.012 * roar(0, seconds) * Math.sin(seconds * 26);
        }
        return sway + 0.055 * bump(t, 3.45, 1.1);
    }

    /**
     * Draws the portrait into the given screen rectangle.
     *
     * @param g0 target graphics.
     * @param boss boss of stage 3, 0 or 1.
     * @param x left edge of the frame.
     * @param y top edge of the frame.
     * @param w frame width.
     * @param h frame height.
     * @param seconds animation time.
     */
    public static void draw(final Graphics2D g0, final int boss, final int x,
            final int y, final int w, final int h, final double seconds) {
        checkBoss(boss);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setClip(x, y, w, h);
        g.setColor(BACK);
        g.fillRect(x, y, w, h);

        double scale = w / SRC_W;
        g.translate(x, y);
        g.scale(scale, scale);

        // The head tilts on its base and shakes a little while it roars.
        double roar = roar(boss, seconds);
        double shake = boss == 0 ? 2.5 * roar * Math.sin(seconds * 31) : 0;
        g.translate(shake + 1.6 * Math.sin(seconds * 0.6 + 0.4),
                1.2 * Math.sin(seconds * 0.8 + 1.0));
        g.rotate(tilt(boss, seconds), SRC_W / 2, SRC_H - 10);

        double drop = jawOpening(boss, seconds) * MAX_DROP[boss];
        paintLines(g, boss, scale, drop);
        paintFlashes(g, boss, roar, eyeFlash(boss, seconds));

        // Frame, scan lines and flicker stay in screen space.
        g.setTransform(new AffineTransform());
        g.setClip(x, y, w, h);
        paintOverlay(g, x, y, w, h, seconds);
        g.dispose();
    }

    /** Smooth step from 0 to 1 between two values. */
    private static double ease(final double v, final double from, final double to) {
        double a = Math.max(0, Math.min(1, (v - from) / (to - from)));
        return a * a * (3 - 2 * a);
    }

    /** How far down the picture is moved at a row, when the jaw is dropped. */
    private static double jawShift(final int[] zone, final double row,
            final double drop) {
        double follow = ease(row, zone[2], zone[3]);
        double release = zone[4] < 0 ? 0 : ease(row, zone[4], zone[5]);
        return drop * follow * (1 - release);
    }

    /**
     * Draws the lines. While the jaw is dropped, the strip of picture it
     * belongs to is drawn in thin bands, each moved down a little more than
     * the one above, so the jaw slides open and nothing is cut.
     */
    private static void paintLines(final Graphics2D g, final int boss,
            final double scale, final double drop) {
        boolean small = SMALL[boss] != null && scale < SMALL_BELOW;
        BufferedImage img = small ? SMALL[boss] : LINES[boss];
        if (img == null) {
            return;
        }
        int w = (int) SRC_W;
        int h = (int) SRC_H;
        if (drop < 0.5) {
            g.drawImage(img, 0, 0, w, h, null);
            return;
        }
        int[] zone = JAW_ZONES[boss];
        int left = Math.max(0, zone[0] - FEATHER);
        int right = Math.min(w, zone[1] + FEATHER);
        int top = zone[2];
        int stripW = right - left;
        int stripH = h - top;
        double f = img.getWidth() / SRC_W;

        // The strip as it was, and as it is with the jaw dropped.
        BufferedImage still = new BufferedImage(stripW, stripH,
                BufferedImage.TYPE_INT_ARGB);
        BufferedImage moved = new BufferedImage(stripW, stripH,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = still.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Only the two edges of the still strip are needed: in between the
        // moved strip is all there is.
        int edgeSrc = (int) Math.round(FEATHER * f);
        int srcTop = (int) Math.round(top * f);
        int srcBottom = (int) Math.round(h * f);
        gs.drawImage(img, 0, 0, FEATHER, stripH, (int) Math.round(left * f),
                srcTop, (int) Math.round(left * f) + edgeSrc, srcBottom, null);
        gs.drawImage(img, stripW - FEATHER, 0, stripW, stripH,
                (int) Math.round(right * f) - edgeSrc, srcTop,
                (int) Math.round(right * f), srcBottom, null);
        gs.dispose();
        Graphics2D gm = moved.createGraphics();
        gm.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int band = 2;
        for (int row = top; row < h; row += band) {
            int next = Math.min(h, row + band);
            double y0 = row + jawShift(zone, row, drop) - top;
            double y1 = next + jawShift(zone, next, drop) - top;
            gm.drawImage(img, 0, (int) Math.floor(y0), stripW,
                    (int) Math.ceil(y1), (int) Math.round(left * f),
                    (int) Math.round(row * f), (int) Math.round(right * f),
                    (int) Math.round(next * f), null);
        }
        gm.dispose();

        // Fade the moved strip in over its edges and the still one out, so
        // no edge of the strip shows where it meets the rest of the picture.
        fade(moved, FEATHER, false);
        fade(still, FEATHER, true);

        // Everything except the strip, then the two layers of the strip.
        Shape saved = g.getClip();
        Area rest = new Area(new Rectangle2D.Double(-60, -60, w + 120, h + 120));
        rest.subtract(new Area(new Rectangle2D.Double(left, top, stripW, stripH)));
        g.clip(rest);
        g.drawImage(img, 0, 0, w, h, null);
        g.setClip(saved);
        g.drawImage(still, left, top, null);
        g.drawImage(moved, left, top, null);
    }

    /** Pixels over which the moved strip fades into the rest of the picture. */
    private static final int FEATHER = 14;

    /**
     * Multiplies the alpha of an image by a mask that rises over the feather
     * on its left and right edges; when inverted, by the opposite mask.
     */
    private static void fade(final BufferedImage img, final int feather,
            final boolean inverted) {
        int w = img.getWidth();
        for (int x = 0; x < feather; x++) {
            fadeColumn(img, x, x, feather, inverted);
            fadeColumn(img, w - 1 - x, x, feather, inverted);
        }
    }

    private static void fadeColumn(final BufferedImage img, final int x,
            final int edge, final int feather, final boolean inverted) {
        double mask = Math.max(0, Math.min(1, (double) edge / feather));
        mask = mask * mask * (3 - 2 * mask);
        double keep = inverted ? 1 - mask : mask;
        for (int y = 0; y < img.getHeight(); y++) {
            int argb = img.getRGB(x, y);
            int alpha = (int) Math.round((argb >>> 24) * keep);
            img.setRGB(x, y, (alpha << 24) | (argb & 0xFFFFFF));
        }
    }

    /** Glow inside the mouth and in the eyes while the boss roars or flashes. */
    private static void paintFlashes(final Graphics2D g, final int boss,
            final double roar, final double eyes) {
        if (roar > 0.02) {
            Ellipse2D mouth = MOUTH_GLOW[boss];
            float radius = (float) Math.max(mouth.getWidth(), mouth.getHeight()) / 2;
            g.setPaint(new RadialGradientPaint(
                    (float) mouth.getCenterX(), (float) mouth.getCenterY(),
                    radius, new float[] {0f, 1f},
                    new Color[] {withAlpha(GLOW, 0.50 * roar),
                        withAlpha(GLOW, 0)}));
            g.fill(new Ellipse2D.Double(mouth.getCenterX() - radius * 1.25,
                    mouth.getCenterY() - radius, radius * 2.5, radius * 2));
        }
        if (eyes > 0.02) {
            for (Shape eye : EYES[boss]) {
                g.setColor(withAlpha(GLOW, 0.55 * eyes));
                g.fill(eye);
                g.setColor(withAlpha(GLOW, 0.20 * eyes));
                g.setStroke(new java.awt.BasicStroke(5f));
                g.draw(eye);
            }
        }
    }

    private static Color withAlpha(final Color c, final double alpha) {
        int a = (int) Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Scan lines, a slow scan bar and a faint flicker. */
    private static void paintOverlay(final Graphics2D g, final int x,
            final int y, final int w, final int h, final double t) {
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
