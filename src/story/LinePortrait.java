// 선 그림에서 따낸 벡터 초상화를 초록 단색 그림체로 그리고, 말할 때 입이 움직이고 눈을 깜빡이게 하는 공용 렌더러
package story;

import static story.PortraitStyle.BACK;
import static story.PortraitStyle.BRIGHT;
import static story.PortraitStyle.ease;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Draws a talking portrait in the green phosphor style of the pilot: vector
 * lines traced from a black-on-white sketch, glowing on the dark screen, with
 * scan lines and a flicker over them. It also moves the things that move: the
 * mouth while the character speaks, a blink now and then, and a slight drift
 * of the whole picture. What differs from one character to the next, the
 * traced lines and where the lips and the eyes are, is given by a {@link Look}.
 * All geometry lives in a {@link #SRC_W} x {@link #SRC_H} source space.
 */
final class LinePortrait {

    /** Width of the source space the drawings are made in. */
    static final double SRC_W = 400;
    /** Height of the source space. */
    static final double SRC_H = 556;

    private static final Color MOUTH = new Color(8, 58, 22);

    /** Longest stretch of a line kept as one piece, so the lip can bend it. */
    private static final double MAX_SEGMENT = 4;
    /** Seconds between blinks and how long a blink lasts. */
    private static final double BLINK_PERIOD = 3.7;
    private static final double BLINK_LENGTH = 0.16;
    /** The mouth is drawn in steps of this many source pixels, each rendered once. */
    private static final double MOUTH_STEP = 0.75;

    /**
     * The lower lip and the crease of the chin under it follow the mouth down.
     * They follow between two columns, and the pull fades out over the feather
     * at their sides, so the corners of the mouth stay where they are.
     *
     * @param left
     *            Column where the pull is full on the left.
     * @param right
     *            Column where the pull is full on the right.
     * @param feather
     *            Pixels over which the pull fades out at both sides.
     * @param pullFrom
     *            Row where the pull starts.
     * @param pullFull
     *            Row where the pull is full.
     * @param releaseFrom
     *            Row where the pull starts to fade out again.
     * @param releaseTo
     *            Row where it is gone.
     */
    record Lip(double left, double right, double feather, double pullFrom,
            double pullFull, double releaseFrom, double releaseTo) {
    }

    /**
     * The mouth between the lips, drawn while it is open.
     *
     * @param left
     *            Left corner of the mouth, on the line where the lips meet.
     * @param right
     *            Right corner of the mouth.
     * @param y
     *            Row of that line.
     * @param scale
     *            Size of the mouth compared with the widest one drawn, 1 for
     *            a mouth 73 pixels wide.
     * @param open
     *            Most the lower lip drops, in source pixels.
     */
    record Mouth(double left, double right, double y, double scale,
            double open) {
    }

    /**
     * An eye, covered by a closed lid while the character blinks.
     *
     * @param x
     *            Center of the eye.
     * @param y
     *            Center of the eye.
     * @param halfWidth
     *            Half the width of the eye.
     * @param heightScale
     *            Height of the lid compared with a wide open eye, 1 for the
     *            biggest.
     */
    record Eye(double x, double y, double halfWidth, double heightScale) {
    }

    /**
     * What makes one portrait different from another.
     *
     * @param lines
     *            The traced lines ({@link TracedLines} data).
     * @param spaceWidth
     *            Width of the space the traced numbers live in.
     * @param lip
     *            The lower lip and chin that follow the mouth down.
     * @param mouth
     *            The mouth between the lips.
     * @param eyes
     *            The eyes that blink.
     */
    record Look(String[] lines, int spaceWidth, Lip lip, Mouth mouth,
            Eye[] eyes) {
    }

    private final Look look;
    /** The traced lines, as loops around the ink. */
    private final TracedLines lines;
    /** Rendered lines, for each width and opening of the mouth. */
    private final LayerCache layers;

    LinePortrait(final Look look) {
        this.look = look;
        this.lines = new TracedLines(look.lines(), look.spaceWidth(), SRC_W,
                MAX_SEGMENT);
        this.layers = new LayerCache(
                (level, width) -> renderLines(level * MOUTH_STEP, width),
                mouthLevel(look.mouth().open()));
    }

    /**
     * How far the mouth is open, from 0 to 1, for a speaker at a given time.
     * It follows a made-up rhythm of syllables with a pause between words,
     * so the same time always gives the same mouth.
     *
     * @param seconds
     *            Animation time.
     * @param speaking
     *            Whether the character is talking; if not the mouth stays shut.
     * @return how open the mouth is.
     */
    static double mouthOpening(final double seconds, final boolean speaking) {
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
     * @param seconds
     *            Animation time.
     * @return how closed the eyes are.
     */
    static double blink(final double seconds) {
        double phase = (seconds % BLINK_PERIOD) / BLINK_LENGTH;
        if (phase >= 1) {
            return 0;
        }
        return Math.sin(phase * Math.PI);
    }

    /**
     * Draws the portrait into the given screen rectangle.
     *
     * @param g0
     *            Target graphics.
     * @param x
     *            Left edge of the frame.
     * @param y
     *            Top edge of the frame.
     * @param w
     *            Frame width.
     * @param h
     *            Frame height.
     * @param seconds
     *            Animation time.
     * @param speaking
     *            Whether the mouth moves.
     */
    void draw(final Graphics2D g0, final int x, final int y, final int w,
            final int h, final double seconds, final boolean speaking) {
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

        double open = mouthOpening(t, speaking) * this.look.mouth().open();
        paintLines(g, open, w);
        paintMouth(g, open, px);
        paintBlink(g, blink(t), px);

        // Frame, scan lines and flicker stay in screen space.
        g.setTransform(new AffineTransform());
        g.setClip(x, y, w, h);
        PortraitStyle.paintOverlay(g, x, y, w, h, t);
        g.dispose();
    }

    /**
     * How far down a point of the lines is moved when the mouth opens: all
     * the way for the lower lip and the chin crease, none above the lips, and
     * a smooth stretch in between.
     */
    private double lipShift(final double x, final double y,
            final double open) {
        Lip lip = this.look.lip();
        double side = ease(x, lip.left() - lip.feather(), lip.left())
                * (1 - ease(x, lip.right(), lip.right() + lip.feather()));
        double follow = ease(y, lip.pullFrom(), lip.pullFull());
        double release = ease(y, lip.releaseFrom(), lip.releaseTo());
        return open * side * follow * (1 - release);
    }

    private static int mouthLevel(final double open) {
        return open < MOUTH_STEP / 2 ? 0 : (int) Math.round(open / MOUTH_STEP);
    }

    /** Draws the lines from the picture made for this width and opening. */
    private void paintLines(final Graphics2D g, final double open,
            final int width) {
        g.drawImage(this.layers.get(mouthLevel(open), width), 0, 0,
                (int) SRC_W, (int) SRC_H, null);
    }

    /**
     * Renders the lines: a soft glow, then the ink itself. With the mouth
     * open, the points of the lines around the lower lip are moved down.
     */
    private BufferedImage renderLines(final double open, final int width) {
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
        Path2D path = open < MOUTH_STEP / 2 ? this.lines.still()
                : this.lines.build((x, y) -> lipShift(x, y, open));
        g.setStroke(new BasicStroke(4f * px, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(90, 255, 110, 18));
        g.draw(path);
        g.setColor(BRIGHT);
        g.fill(path);
        g.dispose();
        return picture;
    }

    /** The dark inside of the mouth and the teeth, between the lips. */
    private void paintMouth(final Graphics2D g, final double open,
            final float px) {
        if (open < 0.4) {
            return;
        }
        Mouth mouth = this.look.mouth();
        double s = mouth.scale();
        double depth = open + 2;
        Path2D.Double inside = new Path2D.Double();
        inside.moveTo(mouth.left() + 2 * s, mouth.y() + 2);
        inside.curveTo(mouth.left() + 14 * s, mouth.y() + 2 + depth * 1.35,
                mouth.right() - 18 * s, mouth.y() + 2 + depth * 1.35,
                mouth.right() - 3 * s, mouth.y() + 2);
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
            g.draw(new Arc2D.Double(mouth.left() + 10 * s, mouth.y() - 1,
                    mouth.right() - mouth.left() - 22 * s, 7 * s, 180, 180,
                    Arc2D.OPEN));
        }
    }

    /** Covers the eyes with a closed lid while the blink lasts. */
    private void paintBlink(final Graphics2D g, final double closed,
            final float px) {
        if (closed < 0.25) {
            return;
        }
        for (Eye eye : this.look.eyes()) {
            double hw = eye.halfWidth();
            double hh = (8 + 4 * closed) * eye.heightScale();
            g.setColor(BACK);
            g.fill(new Ellipse2D.Double(eye.x() - hw + 1, eye.y() - hh,
                    2 * hw - 2, 2 * hh));
            Path2D.Double lid = new Path2D.Double();
            lid.moveTo(eye.x() - hw, eye.y() + 2);
            lid.quadTo(eye.x(), eye.y() + 2 + 6 * closed * eye.heightScale(),
                    eye.x() + hw, eye.y() + 2);
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
}
