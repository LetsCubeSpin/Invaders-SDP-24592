// 스테이지 3 보스 초상화를 벡터 선으로 그리고, 영상에서 본 움직임(포효, 눈 번쩍임, 고개 기울임)을 입히는 렌더러
package story;

import static story.PortraitStyle.BACK;
import static story.PortraitStyle.BRIGHT;
import static story.PortraitStyle.ease;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Portraits of the two bosses of stage 3, one for each answer to the first
 * story question, in the same green phosphor style as the pilot and the woman
 * at the control tower. The lines are vector outlines traced from a drawing
 * ({@link BossPortraitData}), so they stay sharp at any size; the scan lines,
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

    /** Width of the source space. */
    public static final double SRC_W = 340;
    /** Height of the source space. */
    public static final double SRC_H = 340;

    private static final Color GLOW = new Color(120, 255, 140);

    /** Seconds after which the movements of each boss start again. */
    private static final double[] LOOP_SECONDS = {7.0, 4.0};

    /** Longest stretch of a line kept as one piece, so the jaw can bend it. */
    private static final double MAX_SEGMENT = 5;

    /** The traced lines of each boss, as loops around the ink. */
    private static final TracedLines[] LINES = {
        new TracedLines(BossPortraitData.LINES_0, BossPortraitData.SPACE,
                SRC_W, MAX_SEGMENT),
        new TracedLines(BossPortraitData.LINES_1, BossPortraitData.SPACE,
                SRC_W, MAX_SEGMENT)};

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
    /** Pixels over which the jaw's pull fades out at its left and right edges. */
    private static final int FEATHER = 14;
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

    /** @return how many bosses have a portrait, one for each first answer. */
    public static int count() {
        return LOOP_SECONDS.length;
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
        if (boss < 0 || boss >= LOOP_SECONDS.length)
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
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
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
        paintLines(g, boss, drop, w);
        paintFlashes(g, boss, roar, eyeFlash(boss, seconds));

        // Frame, scan lines and flicker stay in screen space.
        g.setTransform(new AffineTransform());
        g.setClip(x, y, w, h);
        PortraitStyle.paintOverlay(g, x, y, w, h, seconds);
        g.dispose();
    }

    /**
     * How far down a point of the lines is moved when the jaw is dropped: all
     * the way inside the jaw, none above it, and a smooth stretch in between;
     * the pull also fades out over the feather at the sides of the jaw.
     */
    private static double jawShift(final int[] zone, final double x,
            final double y, final double drop) {
        double side = ease(x, zone[0] - FEATHER, zone[0])
                * (1 - ease(x, zone[1], zone[1] + FEATHER));
        double follow = ease(y, zone[2], zone[3]);
        double release = zone[4] < 0 ? 0 : ease(y, zone[4], zone[5]);
        return drop * side * follow * (1 - release);
    }

    /** The jaw is drawn in steps of this many source pixels, each rendered once. */
    private static final double JAW_STEP = 0.75;

    private static int jawLevel(final double drop) {
        return drop < JAW_STEP / 2 ? 0 : (int) Math.round(drop / JAW_STEP);
    }

    /** Rendered lines of each boss, for each width and opening of the jaw. */
    private static final LayerCache[] LAYERS = {
        new LayerCache((level, width) -> renderLines(0, level * JAW_STEP, width),
                jawLevel(MAX_DROP[0])),
        new LayerCache((level, width) -> renderLines(1, level * JAW_STEP, width),
                jawLevel(MAX_DROP[1]))};

    /** Draws the lines of the boss from the picture made for this width and jaw. */
    private static void paintLines(final Graphics2D g, final int boss,
            final double drop, final int width) {
        g.drawImage(LAYERS[boss].get(jawLevel(drop), width), 0, 0,
                (int) SRC_W, (int) SRC_H, null);
    }

    /**
     * Renders the lines of the boss: a soft glow, then the ink itself, and a
     * thin edge so even the finest lines stay visible when drawn small. With
     * the jaw dropped, the points of the lines around it are moved down.
     */
    private static BufferedImage renderLines(final int boss, final double drop,
            final int width) {
        BufferedImage picture = new BufferedImage(width, width,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = picture.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        double scale = width / SRC_W;
        g.scale(scale, scale);
        float px = (float) (1.0 / scale);
        int[] zone = JAW_ZONES[boss];
        Path2D path = drop < JAW_STEP / 2 ? LINES[boss].still()
                : LINES[boss].build((x, y) -> jawShift(zone, x, y, drop));
        g.setStroke(new BasicStroke(6.5f * px, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL));
        g.setColor(new Color(90, 255, 110, 22));
        g.draw(path);
        g.setStroke(new BasicStroke(3.2f * px, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL));
        g.setColor(new Color(90, 255, 110, 46));
        g.draw(path);
        g.setColor(BRIGHT);
        g.fill(path);
        g.setStroke(new BasicStroke(1.1f * px, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL));
        g.draw(path);
        g.dispose();
        return picture;
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
                g.setStroke(new BasicStroke(5f));
                g.draw(eye);
            }
        }
    }

    private static Color withAlpha(final Color c, final double alpha) {
        int a = (int) Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
