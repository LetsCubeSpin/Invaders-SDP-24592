// 관제탑 여성 직원 초상화를 조종사와 같은 초록 단색 그림체로 그리고, 말할 때 입이 움직이게 하는 렌더러
package story;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Portrait of the woman at the control tower, in the same green phosphor
 * style as {@link PilotPortraitTraced}. The drawing is a line image
 * ({@code /story/controller_head.png}) made from a black-on-white sketch:
 * every line glows green on the dark background. The scan lines and the
 * flicker are drawn here, and so are the things that move: the mouth while
 * she speaks, a blink now and then, and a slight drift of the whole picture.
 * All geometry lives in a {@link #SRC_W} x {@link #SRC_H} source space.
 */
public final class ControllerPortrait {

    /** Width of the source space, the size of the line image. */
    public static final double SRC_W = 400;
    /** Height of the source space. */
    public static final double SRC_H = 556;

    private static final Color BRIGHT = new Color(90, 255, 110);
    private static final Color BACK = new Color(2, 12, 4);
    private static final Color MOUTH = new Color(8, 58, 22);

    /** Resource that holds the lines, drawn in the 400 x 556 source space. */
    private static final String HEAD_RESOURCE = "/story/controller_head.png";
    /** Half-size copy of the lines, used when the portrait is drawn small. */
    private static final String HEAD_SMALL_RESOURCE = "/story/controller_head_small.png";
    /** On-screen scale below which the small copy is used. */
    private static final double SMALL_BELOW = 0.5;

    /** Lower lip and the chin under it; it drops when the mouth opens. */
    private static final Rectangle LOWER_LIP = new Rectangle(164, 395, 50, 26);
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

    private static final BufferedImage HEAD = load(HEAD_RESOURCE);
    private static final BufferedImage HEAD_SMALL = load(HEAD_SMALL_RESOURCE);

    private ControllerPortrait() {
    }

    private static BufferedImage load(final String resource) {
        try (InputStream in = ControllerPortrait.class
                .getResourceAsStream(resource)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException exception) {
            return null;
        }
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
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
        boolean small = HEAD_SMALL != null && scale < SMALL_BELOW;
        paintLines(g, small, open);
        paintMouth(g, open, px);
        paintBlink(g, blink(t), px);

        // Frame, scan lines and flicker stay in screen space.
        g.setTransform(new AffineTransform());
        g.setClip(x, y, w, h);
        paintOverlay(g, x, y, w, h, t);
        g.dispose();
    }

    /** Draws the line image; the lower lip is lifted out and moved down. */
    private static void paintLines(final Graphics2D g, final boolean small,
            final double open) {
        BufferedImage img = small ? HEAD_SMALL : HEAD;
        if (img == null) {
            return;
        }
        int w = (int) SRC_W;
        int h = (int) SRC_H;
        if (open < 0.4) {
            g.drawImage(img, 0, 0, w, h, null);
            return;
        }
        int drop = (int) Math.round(open);
        // Everything except the lip piece and the room it moves into.
        Rectangle vacated = new Rectangle(LOWER_LIP.x, LOWER_LIP.y,
                LOWER_LIP.width, LOWER_LIP.height + drop);
        java.awt.Shape saved = g.getClip();
        Area rest = new Area(new Rectangle(-50, -50, w + 100, h + 100));
        rest.subtract(new Area(vacated));
        g.clip(rest);
        g.drawImage(img, 0, 0, w, h, null);
        g.setClip(saved);

        // The lip and chin piece, shifted down by the opening: the clip is
        // where it lands, and the picture is moved so its own rows show.
        Rectangle landed = new Rectangle(LOWER_LIP.x, LOWER_LIP.y + drop,
                LOWER_LIP.width, LOWER_LIP.height);
        g.clip(landed);
        g.translate(0, drop);
        g.drawImage(img, 0, 0, w, h, null);
        g.translate(0, -drop);
        g.setClip(saved);
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
