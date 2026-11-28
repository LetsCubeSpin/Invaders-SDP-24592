// 그림에서 따낸 선 윤곽 데이터(닫힌 도형들)를 읽어 Path2D로 만들고, 입이 열릴 때처럼 점을 움직여 주는 공용 클래스
package story;

import java.awt.geom.Path2D;
import java.util.Arrays;

/**
 * The lines of a drawing, traced into closed loops around the ink. A loop is
 * stored as delta-encoded integers, {@code x0 y0 dx dy ...}, and loops are
 * separated by {@code ;}, as the data classes of the portraits write them.
 * Filled with the even-odd rule, the loops give the drawing back.
 *
 * <p>
 * Long stretches of a loop are cut into short pieces, so that a jaw or a lip
 * can pull the lines open smoothly instead of cutting them.
 */
final class TracedLines {

    /** How far down a point of the lines is moved. */
    interface Shift {
        /**
         * @param x
         *            Place of the point, in source pixels.
         * @param y
         *            Place of the point, in source pixels.
         * @return how far the point moves down, in source pixels.
         */
        double of(double x, double y);
    }

    private final double[] xs;
    private final double[] ys;
    /** Index of the first point of each loop, and one more for the end. */
    private final int[] starts;
    /** The loops as traced, with no extra points. */
    private final Path2D still;

    /**
     * @param chunks
     *            The pieces of the data text, joined as they are.
     * @param space
     *            Width of the space the traced numbers live in.
     * @param size
     *            Width of the source space they are drawn in.
     * @param maxSegment
     *            Longest stretch kept as one piece, in source pixels.
     */
    TracedLines(final String[] chunks, final double space, final double size,
            final double maxSegment) {
        double scale = size / space;
        double[] px = new double[4096];
        double[] py = new double[4096];
        int[] loopStarts = new int[1024];
        int points = 0;
        int loops = 0;
        Path2D.Double traced = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (String loop : String.join("", chunks).split(";")) {
            String[] tok = loop.trim().split("\\s+");
            if (tok.length < 6) {
                continue;
            }
            if (loops + 2 >= loopStarts.length) {
                loopStarts = Arrays.copyOf(loopStarts, loops * 2);
            }
            loopStarts[loops++] = points;
            double x = Integer.parseInt(tok[0]);
            double y = Integer.parseInt(tok[1]);
            double firstX = x;
            double firstY = y;
            traced.moveTo(x * scale, y * scale);
            for (int i = 2; i <= tok.length; i += 2) {
                double nx = i < tok.length ? x + Integer.parseInt(tok[i])
                        : firstX;
                double ny = i < tok.length ? y + Integer.parseInt(tok[i + 1])
                        : firstY;
                if (i < tok.length) {
                    traced.lineTo(nx * scale, ny * scale);
                }
                // The point itself, then pieces to the next one.
                double length = Math.hypot(nx - x, ny - y) * scale;
                int pieces = Math.max(1, (int) Math.ceil(length / maxSegment));
                for (int k = 0; k < pieces; k++) {
                    if (points + 1 >= px.length) {
                        px = Arrays.copyOf(px, px.length * 2);
                        py = Arrays.copyOf(py, py.length * 2);
                    }
                    px[points] = (x + (nx - x) * k / pieces) * scale;
                    py[points] = (y + (ny - y) * k / pieces) * scale;
                    points++;
                }
                x = nx;
                y = ny;
            }
            traced.closePath();
        }
        this.xs = Arrays.copyOf(px, points);
        this.ys = Arrays.copyOf(py, points);
        this.starts = Arrays.copyOf(loopStarts, loops + 1);
        this.starts[loops] = points;
        this.still = traced;
    }

    /** @return the loops with just the points that were traced. */
    Path2D still() {
        return this.still;
    }

    /**
     * @param shift
     *            How far each point moves down.
     * @return the loops as one even-odd path, with every point moved.
     */
    Path2D build(final Shift shift) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (int loop = 0; loop + 1 < this.starts.length; loop++) {
            for (int i = this.starts[loop]; i < this.starts[loop + 1]; i++) {
                double y = this.ys[i] + shift.of(this.xs[i], this.ys[i]);
                if (i == this.starts[loop]) {
                    path.moveTo(this.xs[i], y);
                } else {
                    path.lineTo(this.xs[i], y);
                }
            }
            path.closePath();
        }
        return path;
    }
}
