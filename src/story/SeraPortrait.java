// 구출된 스웜 과학자 세라의 초상화를 조종사와 같은 초록 선 그림체의 벡터로 그리고, 말할 때 입이 움직이게 하는 렌더러
package story;

import java.awt.Graphics2D;

/**
 * Portrait of Sera, the Swarm scientist who is among the people rescued on
 * the second route, in the same green phosphor style as
 * {@link PilotPortraitTraced} and {@link ControllerPortrait}. The lines are
 * vector outlines traced from a black-on-white sketch
 * ({@link SeraPortraitData}); {@link LinePortrait} draws them and moves what
 * moves: the mouth while she speaks, a blink now and then, and a slight drift
 * of the whole picture. All geometry lives in a {@link #SRC_W} x
 * {@link #SRC_H} source space, the same size as the other portraits, so the
 * frame is the same too.
 */
public final class SeraPortrait {

    /** Width of the source space. */
    public static final double SRC_W = LinePortrait.SRC_W;
    /** Height of the source space. */
    public static final double SRC_H = LinePortrait.SRC_H;

    private static final LinePortrait PORTRAIT = new LinePortrait(
            new LinePortrait.Look(SeraPortraitData.LINES,
                    SeraPortraitData.SPACE_WIDTH,
                    new LinePortrait.Lip(234, 254, 8, 323, 328, 336, 346),
                    new LinePortrait.Mouth(224, 267, 322, 0.59, 5.5),
                    new LinePortrait.Eye[] {
                        new LinePortrait.Eye(226, 238, 19, 0.6),
                        new LinePortrait.Eye(293, 249, 13, 0.5)}));

    private SeraPortrait() {
    }

    /**
     * How far the mouth is open, from 0 to 1, for a speaker at a given time.
     *
     * @param seconds animation time.
     * @param speaking whether she is talking; if not the mouth stays shut.
     * @return how open the mouth is.
     */
    public static double mouthOpening(final double seconds,
            final boolean speaking) {
        return LinePortrait.mouthOpening(seconds, speaking);
    }

    /**
     * How closed the eyes are, from 0 (open) to 1 (shut).
     *
     * @param seconds animation time.
     * @return how closed the eyes are.
     */
    public static double blink(final double seconds) {
        return LinePortrait.blink(seconds);
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
        PORTRAIT.draw(g0, x, y, w, h, seconds, speaking);
    }
}
