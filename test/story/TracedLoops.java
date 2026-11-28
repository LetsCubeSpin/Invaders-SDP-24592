// 초상화 벡터 데이터 문자열을 점 목록으로 읽어 테스트에서 쓰게 하는 도우미
package story;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

/** Reads the traced loops of a portrait data class, for the tests. */
final class TracedLoops {

    private TracedLoops() {
    }

    /**
     * @param chunks
     *            The pieces of the data text.
     * @return the loops, each a list of {x, y} points.
     */
    static List<int[][]> parse(final String[] chunks) {
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

    /**
     * Checks that every point lies in the space of the drawing.
     *
     * @return the number of points.
     */
    static int assertInside(final List<int[][]> loops, final int width,
            final int height, final String name) {
        int points = 0;
        for (int[][] loop : loops) {
            points += loop.length;
            for (int[] point : loop) {
                assertTrue(point[0] >= 0 && point[0] <= width
                        && point[1] >= 0 && point[1] <= height,
                        "point (" + point[0] + "," + point[1] + ") of " + name);
            }
        }
        return points;
    }
}
