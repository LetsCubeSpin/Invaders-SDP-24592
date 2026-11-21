// 참고 이미지에서 추적한 벡터 윤곽으로 조종사 초상화를 초록색으로 그리고 고개 흔들림과 고글 반사를 입히는 렌더러
package story;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * Pilot portrait drawn from vector outlines traced out of a reference
 * drawing. The head sways on its own layer, the body breathes, and a green
 * glint sweeps across the goggle lens. All geometry lives in a 1024 x 1024
 * source space ({@link PilotPortraitData}).
 */
public final class PilotPortraitTraced {

    /** Left edge of the visible source area. */
    private static final double SRC_X = 235;
    /** Top edge of the visible source area. */
    private static final double SRC_Y = -72;
    /** Width of the visible source area. */
    public static final double SRC_W = 789;
    /** Height of the visible source area. */
    public static final double SRC_H = 1096;

    private static final Color BRIGHT = new Color(90, 255, 110);
    private static final Color MID = new Color(48, 190, 74);
    private static final Color HAIR_FILL = new Color(10, 70, 26);
    private static final Color SKIN_FILL = new Color(6, 40, 16);
    private static final Color LENS_FILL = new Color(8, 58, 25);
    private static final Color BACK = new Color(2, 12, 4);

    /** Point the head rotates around, near the collar. */
    private static final Point2D NECK = new Point2D.Double(800, 780);

    /** Seconds per glint cycle and how long the sweep itself takes. */
    private static final double GLINT_PERIOD = 4.2;
    private static final double GLINT_SWEEP = 1.1;

    private static final Path2D HEAD_LINES = flatten(decode(PilotPortraitData.HEAD_LINES));
    private static final Path2D BODY_LINES = flatten(decode(PilotPortraitData.BODY_LINES));
    private static final Path2D HEAD_SOLIDS = flatten(decode(PilotPortraitData.HEAD_SOLIDS));
    private static final Path2D BODY_SOLIDS = flatten(decode(PilotPortraitData.BODY_SOLIDS));
    private static final Path2D HAIR = flatten(decode(PilotPortraitData.HAIR));
    private static final Path2D HAIR_STROKES = flatten(decode(PilotPortraitData.HAIR_STROKES));
    private static final Path2D LENS = flatten(decode(PilotPortraitData.LENS));
    private static final Path2D SKIN = flatten(decode(PilotPortraitData.SKIN));

    private PilotPortraitTraced() {
    }

    /** Right and bottom edge of the source image, pinned while moving. */
    private static final double IMAGE_EDGE = 1024;
    /** The traced hair is cut flat at the image top and rounded into a dome. */
    private static final double DOME_LEFT = 420;
    private static final double DOME_RIGHT = 900;
    private static final double DOME_HEIGHT = 38;
    private static final double DOME_DEPTH = 40;

    private static double smooth(final double v) {
        double a = v < 0 ? 0 : v > 1 ? 1 : v;
        return a * a * (3 - 2 * a);
    }

    /**
     * How much a source point follows the head instead of the body. The
     * blend runs along the collar, so the neck stretches instead of tearing.
     * The left shoulder strap stays with the body.
     */
    private static double headWeight(final double x, final double y) {
        double s = -0.619 * (x - 560) - 0.786 * (y - 1040);
        double w = smooth((s + 30) / 180);
        double strap = smooth((y - 820) / 80) * smooth((400 - x) / 120);
        return w * (1 - 0.85 * strap);
    }

    /** Rounds the flat cut at the top of the hair into a dome. */
    private static double domeLift(final double x, final double y) {
        if (x <= DOME_LEFT || x >= DOME_RIGHT || y >= DOME_DEPTH) {
            return y < 0 ? y : 0;
        }
        double height = DOME_HEIGHT * Math.pow(Math.sin(Math.PI
                * (x - DOME_LEFT) / (DOME_RIGHT - DOME_LEFT)), 0.7);
        // Points above the image are folded onto the dome curve.
        return y < 0 ? y + height : height * (1 - y / DOME_DEPTH);
    }

    /** Straight-segment copy of a path with the hair dome applied. */
    private static Path2D flatten(final Path2D src) {
        Path2D.Double out = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        PathIterator it = src.getPathIterator(null, 0.5);
        double[] c = new double[6];
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            double y = c[1] - domeLift(c[0], c[1]);
            if (type == PathIterator.SEG_MOVETO) {
                out.moveTo(c[0], y);
            } else if (type == PathIterator.SEG_LINETO) {
                out.lineTo(c[0], y);
            } else if (type == PathIterator.SEG_CLOSE) {
                out.closePath();
            }
            it.next();
        }
        return out;
    }

    /** One frame of motion: the head turns, the body breathes. */
    private static final class Pose {
        private final double cos;
        private final double sin;
        private final double headDx;
        private final double headDy;
        private final double bodyDy;

        Pose(final double rot, final double headDx, final double headDy,
                final double bodyDy) {
            this.cos = Math.cos(rot);
            this.sin = Math.sin(rot);
            this.headDx = headDx;
            this.headDy = headDy;
            this.bodyDy = bodyDy;
        }

        void apply(final double[] c) {
            double x = c[0];
            double y = c[1];
            double w = headWeight(x, y);
            double rx = x - NECK.getX();
            double ry = y - NECK.getY();
            double hx = NECK.getX() + rx * cos - ry * sin + headDx;
            double hy = NECK.getY() + rx * sin + ry * cos + headDy;
            double nx = w * hx + (1 - w) * x;
            double ny = w * hy + (1 - w) * (y + bodyDy);
            double pin = smooth((IMAGE_EDGE - x) / 70)
                    * smooth((IMAGE_EDGE - y) / 70) * smooth((x - SRC_X) / 50);
            c[0] = x + pin * (nx - x);
            c[1] = y + pin * (ny - y);
        }

        Path2D warp(final Path2D src) {
            Path2D.Double out = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            PathIterator it = src.getPathIterator(null);
            double[] c = new double[6];
            while (!it.isDone()) {
                int type = it.currentSegment(c);
                if (type == PathIterator.SEG_CLOSE) {
                    out.closePath();
                } else {
                    apply(c);
                    if (type == PathIterator.SEG_MOVETO) {
                        out.moveTo(c[0], c[1]);
                    } else {
                        out.lineTo(c[0], c[1]);
                    }
                }
                it.next();
            }
            return out;
        }
    }

    /**
     * Turns delta-encoded loops into one even-odd path. Corners are rounded by
     * using every vertex as a quadratic control point between edge midpoints.
     */
    private static Path2D decode(final String[] chunks) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (String loop : String.join("", chunks).split(";")) {
            String trimmed = loop.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tok = trimmed.split("\\s+");
            int n = tok.length / 2;
            if (n < 3) {
                continue;
            }
            double[] xs = new double[n];
            double[] ys = new double[n];
            double x = 0;
            double y = 0;
            for (int i = 0; i < n; i++) {
                x += Integer.parseInt(tok[i * 2]);
                y += Integer.parseInt(tok[i * 2 + 1]);
                xs[i] = x;
                ys[i] = y;
            }
            path.moveTo((xs[n - 1] + xs[0]) / 2, (ys[n - 1] + ys[0]) / 2);
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                path.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2,
                        (ys[i] + ys[j]) / 2);
            }
            path.closePath();
        }
        return path;
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
     */
    public static void draw(final Graphics2D g0, final int x, final int y,
            final int w, final int h, final double seconds) {
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
        g.translate(-SRC_X, -SRC_Y);
        float px = (float) (1.0 / scale);

        double t = seconds;
        double breath = Math.sin(t * 2 * Math.PI / 4.4);
        double nodPhase = (t % 8.0) / 8.0;
        double nod = nodPhase < 0.12
                ? Math.pow(Math.sin(nodPhase / 0.12 * Math.PI), 2) : 0;
        double headRot = 0.026 * Math.sin(t * 0.83)
                + 0.012 * Math.sin(t * 1.91 + 1.3) + 0.02 * nod;
        double headDx = 9 * Math.sin(t * 0.61 + 0.4)
                + 4 * Math.sin(t * 1.57);
        double headDy = 4 * breath + 5 * Math.sin(t * 0.73 + 2) + 12 * nod;

        // One continuous warp: the head turns and the body breathes, with
        // the neck and collar blending the two so nothing separates.
        Pose pose = new Pose(headRot, headDx, headDy + 5 * breath,
                5 * breath);
        paintLines(g, pose.warp(BODY_LINES), pose.warp(BODY_SOLIDS), px);
        g.setColor(SKIN_FILL);
        g.fill(pose.warp(SKIN));
        Path2D hair = pose.warp(HAIR);
        g.setColor(HAIR_FILL);
        g.fill(hair);
        g.setColor(MID);
        g.fill(pose.warp(HAIR_STROKES));
        g.setStroke(new BasicStroke(2.2f * px, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(BRIGHT);
        g.draw(hair);
        Path2D lens = pose.warp(LENS);
        g.setColor(LENS_FILL);
        g.fill(lens);
        paintGlint(g, t, lens);
        paintLines(g, pose.warp(HEAD_LINES), pose.warp(HEAD_SOLIDS), px);

        // Frame, scan lines and flicker stay in screen space.
        g.setTransform(new AffineTransform());
        g.setClip(x, y, w, h);
        paintOverlay(g, x, y, w, h, t);
        g.dispose();
    }

    /** Glow, bright lines, then dark masses on top. */
    private static void paintLines(final Graphics2D g, final Path2D lines,
            final Path2D solids, final float px) {
        g.setStroke(new BasicStroke(3.4f * px, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(90, 255, 110, 38));
        g.draw(lines);
        g.setColor(BRIGHT);
        g.fill(lines);
        g.setColor(BACK);
        g.fill(solids);
    }

    /** A slanted band of light that periodically crosses the lens. */
    private static void paintGlint(final Graphics2D g, final double t,
            final Path2D lens) {
        double phase = (t % GLINT_PERIOD) / GLINT_SWEEP;
        double shimmer = 0.5 + 0.5 * Math.sin(t * 1.7);
        g.setColor(new Color(60, 255, 100, (int) (10 + 14 * shimmer)));
        g.fill(lens);
        if (phase >= 1.0) {
            return;
        }
        double ease = phase * phase * (3 - 2 * phase);
        double angle = Math.toRadians(24);
        double dirX = Math.cos(angle);
        double dirY = Math.sin(angle);
        double cx = 120 + 860 * ease;
        double cy = 440;
        Color clear = new Color(120, 255, 150, 0);
        Color peak = new Color(190, 255, 205, 215);
        fillBand(g, lens, cx, cy, dirX, dirY, 62, clear, peak);
        fillBand(g, lens, cx - 120 * dirX, cy - 120 * dirY, dirX, dirY, 16, clear,
                new Color(190, 255, 205, 170));
    }

    private static void fillBand(final Graphics2D g, final Path2D lens,
            final double cx,
            final double cy, final double dirX, final double dirY,
            final double half, final Color clear, final Color peak) {
        Point2D a = new Point2D.Double(cx - dirX * half, cy - dirY * half);
        Point2D b = new Point2D.Double(cx + dirX * half, cy + dirY * half);
        g.setPaint(new LinearGradientPaint(a, b, new float[] {0f, 0.5f, 1f},
                new Color[] {clear, peak, clear},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(lens);
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
