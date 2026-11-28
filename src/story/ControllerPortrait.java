// 관제탑 여성 직원 초상화를 조종사와 같은 초록 선 그림체의 벡터로 그리고, 말할 때 입이 움직이게 하는 렌더러
package story;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Portrait of the woman at the control tower, in the same green phosphor
 * style as {@link PilotPortraitTraced}. The lines are vector outlines traced
 * from a black-on-white sketch ({@link ControllerPortraitData}), so they stay
 * sharp at any size: every line glows green on the dark background. The scan
 * lines and the flicker are drawn here, and so are the things that move: the
 * mouth while she speaks, a blink now and then, and a slight drift of the
 * whole picture. All geometry lives in a {@link #SRC_W} x {@link #SRC_H}
 * source space.
 */
public final class ControllerPortrait {

    /** Width of the source space, the size of the line image. */
    public static final double SRC_W = 400;
    /** Height of the source space. */
    public static final double SRC_H = 556;

    private static final Color BRIGHT = new Color(90, 255, 110);
    private static final Color BACK = new Color(2, 12, 4);
    private static final Color MOUTH = new Color(8, 58, 22);

    /** Longest stretch of a line kept as one piece, so the lip can bend it. */
    private static final double MAX_SEGMENT = 4;

    /**
     * The traced lines of the drawing, one set of loops for each strength of
     * ink, from faint to strong.
     */
    private static final TracedLines[] LINES = traceLevels();

    /**
     * How bright each strength of ink is drawn, from the dark of the screen
     * (0) to the full green (1); faint ink is dim, strong ink is bright.
     */
    private static final double[] TONES = {0.28, 0.58, 1.0};

    /**
     * The lower lip and the crease of the chin under it follow the mouth down.
     * They follow between these columns, and the pull fades out over the
     * feather at their sides, so the corners of the mouth stay where they are.
     */
    private static final double LIP_LEFT = 168;
    private static final double LIP_RIGHT = 206;
    private static final double LIP_FEATHER = 8;
    /** Rows where the pull starts, where it is full, and where it fades out. */
    private static final double PULL_FROM = 392;
    private static final double PULL_FULL = 399;
    private static final double RELEASE_FROM = 418;
    private static final double RELEASE_TO = 434;

    /** Corners of the mouth, on the line where the lips meet. */
    private static final double MOUTH_LEFT = 163;
    private static final double MOUTH_RIGHT = 236;
    private static final double MOUTH_Y = 393;
    /** Most the lower lip drops, in source pixels. */
    private static final double MOUTH_OPEN = 8;

    /** Centers of the eyes and half the width of one. */
    private static final double[][] EYES = {{144, 284}, {255, 284}};
    private static final double EYE_HALF_WIDTH = 27;
    /** Seconds between blinks and how long a blink lasts. */
    private static final double BLINK_PERIOD = 3.7;
    private static final double BLINK_LENGTH = 0.16;

    private ControllerPortrait() {
    }

    private static TracedLines[] traceLevels() {
        TracedLines[] lines = new TracedLines[ControllerPortraitData.LEVELS.length];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new TracedLines(ControllerPortraitData.LEVELS[i],
                    ControllerPortraitData.SPACE_WIDTH, SRC_W, MAX_SEGMENT);
        }
        return lines;
    }

    /**
     * How far the mouth is open, from 0 to 1, for a speaker at a given time.
     * It follows a made-up rhythm of syllables with a pause between words,
     * so the same time always gives the same mouth.
     *
     * @param seconds animation time.
     * @param speaking whether she is talking; if not the mouth stays shut.
     * @return how open the mouth is.
     */
    public static double mouthOpening(final double seconds,
            final boolean speaking) {
        if (!speaking) {
            return 0;
        }
        double syllable = Math.abs(Math.sin(seconds * 10.6))
                * (0.6 + 0.4 * Math.sin(seconds * 4.1 + 0.8));
        // Short pauses, as between words.
        double word = Math.sin(seconds * 2.3) + 0.55 * Math.sin(seconds * 5.9);
        double gate = smooth((word + 0.9) / 0.6);
        return Math.max(0, Math.min(1, syllable * gate * 1.15));
    }

    private static double smooth(final double v) {
        double a = v < 0 ? 0 : v > 1 ? 1 : v;
        return a * a * (3 - 2 * a);
    }

    /**
     * How closed the eyes are, from 0 (open) to 1 (shut).
     *
     * @param seconds animation time.
     * @return how closed the eyes are.
     */
    public static double blink(final double seconds) {
        double phase = (seconds % BLINK_PERIOD) / BLINK_LENGTH;
        if (phase >= 1) {
            return 0;
        }
        return Math.sin(phase * Math.PI);
    }

    /**
     * Draws the portrait with her mouth shut.
     *
     * @param g0 target graphics.
     * @param x left edge of the frame.
     * @param y top edge of the frame.
     * @param w frame width.
     * @param h frame height.
     * @param seconds animation time.
     */
    public static void draw(final Graphics2D g0, final int x, final int y,
            final int w, final int h, final double seconds) {
        draw(g0, x, y, w, h, seconds, false);
    }

    /**
     * Draws the portrait into the given screen rectangle.
     *
     * @param g0 target graphics.
     * @param x left edge of the frame.
     * @param y top edge of the frame.
     * @param w frame width.
     * @param h frame height.
     * @param seconds animation time.
     * @param speaking whether her mouth moves.
     */
    public static void draw(final Graphics2D g0, final int x, final int y,
            final int w, final int h, final double seconds,
            final boolean speaking) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setClip(x, y, w, h);
        g.setColor(BACK);
        g.fillRect(x, y, w, h);

        double scale = w / SRC_W;
        g.translate(x, y);
        g.scale(scale, scale);
        float px = (float) (1.0 / scale);

        // The picture drifts a little and breathes, like a live feed.
        double t = seconds;
        double breath = Math.sin(t * 2 * Math.PI / 4.4);
        g.translate(1.6 * Math.sin(t * 0.6 + 0.4),
                1.2 * Math.sin(t * 0.9 + 2) + 1.4 * breath);
        g.rotate(0.005 * Math.sin(t * 0.8), SRC_W / 2, SRC_H);

        double open = mouthOpening(t, speaking) * MOUTH_OPEN;
        paintLines(g, open, w);
        paintMouth(g, open, px);
        paintBlink(g, blink(t), px);

        // Frame, scan lines and flicker stay in screen space.
        g.setTransform(new AffineTransform());
        g.setClip(x, y, w, h);
        paintOverlay(g, x, y, w, h, t);
        g.dispose();
    }

    /** A green between the dark of the screen and the full green. */
    private static Color tone(final double level) {
        return new Color(
                (int) Math.round(BACK.getRed() + level * (BRIGHT.getRed() - BACK.getRed())),
                (int) Math.round(BACK.getGreen() + level * (BRIGHT.getGreen() - BACK.getGreen())),
                (int) Math.round(BACK.getBlue() + level * (BRIGHT.getBlue() - BACK.getBlue())));
    }

    /** Smooth step from 0 to 1 between two values. */
    private static double ease(final double v, final double from,
            final double to) {
        double a = Math.max(0, Math.min(1, (v - from) / (to - from)));
        return a * a * (3 - 2 * a);
    }

    /**
     * How far down a point of the lines is moved when the mouth opens: all
     * the way for the lower lip and the chin crease, none above the lips, and
     * a smooth stretch in between.
     */
    private static double lipShift(final double x, final double y,
            final double open) {
        double side = ease(x, LIP_LEFT - LIP_FEATHER, LIP_LEFT)
                * (1 - ease(x, LIP_RIGHT, LIP_RIGHT + LIP_FEATHER));
        double follow = ease(y, PULL_FROM, PULL_FULL);
        double release = ease(y, RELEASE_FROM, RELEASE_TO);
        return open * side * follow * (1 - release);
    }

    /** Rendered lines: the key is the opening of the mouth and the width. */
    private static final Map<Long, BufferedImage> LAYERS =
            new ConcurrentHashMap<Long, BufferedImage>();
    /** Widths whose mouth openings are being rendered ahead of time. */
    private static final Set<Integer> WARMED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WARMER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable,
                        "controller-portrait-warmer");
                thread.setDaemon(true);
                return thread;
            });
    /** The mouth is drawn in steps of this many source pixels, each rendered once. */
    private static final double MOUTH_STEP = 0.75;
    /** Most layers kept, over all sizes; the cache starts again beyond it. */
    private static final int MAX_LAYERS = 160;

    private static int mouthLevel(final double open) {
        return open < MOUTH_STEP / 2 ? 0 : (int) Math.round(open / MOUTH_STEP);
    }

    /**
     * Draws the lines from a picture of them made for this width and this
     * opening of the mouth. Drawing the lines takes longer than a frame, so
     * each picture is made once and kept; the pictures of all the openings are
     * made in the background the first time a width is drawn.
     */
    private static void paintLines(final Graphics2D g, final double open,
            final int width) {
        if (LAYERS.size() > MAX_LAYERS) {
            LAYERS.clear();
            WARMED.clear();
        }
        if (WARMED.add(width)) {
            WARMER.execute(() -> {
                for (int level = 1; level <= mouthLevel(MOUTH_OPEN); level++) {
                    layer(level, width);
                }
            });
        }
        g.drawImage(layer(mouthLevel(open), width), 0, 0, (int) SRC_W,
                (int) SRC_H, null);
    }

    private static BufferedImage layer(final int level, final int width) {
        return LAYERS.computeIfAbsent(((long) level << 32) | width,
                key -> renderLines(level * MOUTH_STEP, width));
    }

    /**
     * Renders the lines: a soft glow, then the faint, the medium and the
     * strong ink in brighter and brighter green. With the mouth open, the
     * points of the lines around the lower lip are moved down.
     */
    private static BufferedImage renderLines(final double open,
            final int width) {
        double scale = width / SRC_W;
        BufferedImage picture = new BufferedImage(width,
                (int) Math.ceil(SRC_H * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = picture.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.scale(scale, scale);
        float px = (float) (1.0 / scale);
        Path2D[] paths = new Path2D[LINES.length];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = open < MOUTH_STEP / 2 ? LINES[i].still()
                    : LINES[i].build((x, y) -> lipShift(x, y, open));
        }
        // A soft glow around the strong ink, then each strength of ink from
        // the faintest to the strongest, each one inside the one before.
        g.setStroke(new BasicStroke(4f * px, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(90, 255, 110, 18));
        g.draw(paths[paths.length - 1]);
        for (int i = 0; i < paths.length; i++) {
            g.setColor(tone(TONES[i]));
            g.fill(paths[i]);
        }
        g.dispose();
        return picture;
    }

    /** The dark inside of the mouth and the teeth, between the lips. */
    private static void paintMouth(final Graphics2D g, final double open,
            final float px) {
        if (open < 0.4) {
            return;
        }
        double depth = open + 2;
        Path2D.Double inside = new Path2D.Double();
        inside.moveTo(MOUTH_LEFT + 2, MOUTH_Y + 2);
        inside.curveTo(MOUTH_LEFT + 14, MOUTH_Y + 2 + depth * 1.35,
                MOUTH_RIGHT - 18, MOUTH_Y + 2 + depth * 1.35, MOUTH_RIGHT - 3,
                MOUTH_Y + 2);
        inside.closePath();
        g.setColor(MOUTH);
        g.fill(inside);
        g.setColor(new Color(90, 255, 110, 34));
        g.setStroke(new BasicStroke(4.6f * px, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(inside);
        g.setColor(BRIGHT);
        g.setStroke(new BasicStroke(1.6f * px, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(inside);
        // A row of teeth under the upper lip.
        if (open > 2) {
            g.setStroke(new BasicStroke(1.5f * px, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.setColor(new Color(150, 255, 170, 200));
            g.draw(new Arc2D.Double(MOUTH_LEFT + 10, MOUTH_Y - 1,
                    MOUTH_RIGHT - MOUTH_LEFT - 22, 7, 180, 180, Arc2D.OPEN));
        }
    }

    /** Covers the eyes with a closed lid while the blink lasts. */
    private static void paintBlink(final Graphics2D g, final double closed,
            final float px) {
        if (closed < 0.25) {
            return;
        }
        for (double[] eye : EYES) {
            double hw = EYE_HALF_WIDTH;
            double hh = 8 + 4 * closed;
            g.setColor(BACK);
            g.fill(new Ellipse2D.Double(eye[0] - hw + 1, eye[1] - hh,
                    2 * hw - 2, 2 * hh));
            Path2D.Double lid = new Path2D.Double();
            lid.moveTo(eye[0] - hw, eye[1] + 2);
            lid.quadTo(eye[0], eye[1] + 2 + 6 * closed, eye[0] + hw,
                    eye[1] + 2);
            g.setStroke(new BasicStroke(4.6f * px, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.setColor(new Color(90, 255, 110, 34));
            g.draw(lid);
            g.setStroke(new BasicStroke(2.4f * px, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.setColor(BRIGHT);
            g.draw(lid);
        }
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
