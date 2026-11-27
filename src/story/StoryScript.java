// 스토리 모드 오프닝 설정 문장과 조종사 대사를 영어 상수로 모아 둔 클래스
package story;

/**
 * English texts of the story mode: the opening narration and the lines the
 * pilot says when a stage begins.
 */
public final class StoryScript {

    /** Name shown above the pilot's lines. */
    public static final String PILOT_NAME = "PILOT";

    /** Name shown above the lines of the woman at the control tower. */
    public static final String CONTROL_NAME = "CONTROL";

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

    private static final StoryQuestion FIRST_QUESTION = new StoryQuestion(
            "Two signals at once. The position of the Swarm vanguard fleet, "
                    + "and a distress call from the Moon base.",
            new String[] { "ASSAULT", "RESCUE" },
            new String[] { "Strike the enemy commander first",
                    "Save the Moon base survivors" });

    private static final StoryQuestion SECOND_QUESTION_AFTER_ASSAULT =
            new StoryQuestion(
                    "In the wreckage of ARGUS, we found an alien core, "
                            + "still beating.",
                    new String[] { "ABSORB", "DESTROY" },
                    new String[] { "Absorb the core", "Destroy the core" });

    private static final StoryQuestion SECOND_QUESTION_AFTER_RESCUE =
            new StoryQuestion(
                    "Among the people we rescued was a Swarm scientist, "
                            + "Sera. \"Trust me. I will show you the way to "
                            + "the mothership.\"",
                    new String[] { "ESCORT", "INFILTRATE" },
                    new String[] { "Guard the flight home",
                            "Infiltrate the mothership" });

    private StoryScript() {
    }

    /**
     * @param stage
     *            Story stage that was just cleared.
     * @param firstChoice
     *            Answer to the first question, 0 or 1. Only the second
     *            question depends on it.
     * @return The question asked after the stage, or null when the stage is
     *         not followed by one.
     */
    public static StoryQuestion question(final int stage,
            final int firstChoice) {
        if (stage == StoryProgress.FIRST_QUESTION_STAGE)
            return FIRST_QUESTION;
        if (stage == StoryProgress.MID_BOSS_STAGE)
            return firstChoice == 0 ? SECOND_QUESTION_AFTER_ASSAULT
                    : SECOND_QUESTION_AFTER_RESCUE;
        return null;
    }

    /**
     * @param stage
     *            Story stage that was just cleared.
     * @param firstChoice
     *            Answer to the first question, 0 or 1. Only the second
     *            question depends on it.
     * @return Who asks the question after the stage as a conversation at the
     *         top of the board, or null when it is shown in the plain box.
     *         The woman at the control tower asks the first question and the
     *         second one after the first answer 0; the second one after the
     *         answer 1 is Sera's, who has no portrait yet.
     */
    public static String questionSpeaker(final int stage,
            final int firstChoice) {
        if (stage == StoryProgress.FIRST_QUESTION_STAGE)
            return CONTROL_NAME;
        if (stage == StoryProgress.MID_BOSS_STAGE && firstChoice == 0)
            return CONTROL_NAME;
        return null;
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
