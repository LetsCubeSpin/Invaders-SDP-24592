// 스토리 모드 오프닝 설정 문장과 조종사 대사를 영어 상수로 모아 둔 클래스
package story;

/**
 * English texts of the story mode: the opening narration and the lines the
 * pilot says when a stage begins.
 */
public final class StoryScript {

    /** Name shown above the pilot's lines. */
    public static final String PILOT_NAME = "PILOT";

    /** Opening narration, shown typed out before stage 1. */
    public static final String OPENING_NARRATION =
            "Year 2189.\n"
            + "The defense line on Mars has fallen.\n"
            + "\n"
            + "The alien fleet, the Swarm,\n"
            + "is heading for Earth.\n"
            + "\n"
            + "Only one interceptor remains:\n"
            + "OCTO-1.\n"
            + "\n"
            + "\"Pilot, you are the last line of defense.\n"
            + "Launch.\"";

    /** What the pilot says at the start of stage 1. */
    private static final String STAGE_1_LINE = "Let's go wipe them all out.";

    private StoryScript() {
    }

    /**
     * @param stage
     *            Story stage, starting at 1.
     * @return What the pilot says when the stage begins, or null when the
     *         stage starts without a line.
     */
    public static String stageIntroLine(final int stage) {
        return stage == 1 ? STAGE_1_LINE : null;
    }
}
