// 스토리 모드 대사를 현재 선택된 언어로 제공하는 클래스
package story;

import engine.LanguageManager;

/**
 * Localized story mode narration and dialogue.
 */
public final class StoryScript {

    private StoryScript() {
    }

    /** @return Name shown above the pilot's lines in the selected language. */
    public static String pilotName() {
        return LanguageManager.get("story.pilotName");
    }

    /** @return Opening narration in the selected language. */
    public static String openingNarration() {
        return LanguageManager.get("story.openingNarration");
    }

    /**
     * @param stage
     *            Story stage that was just cleared.
     * @param firstChoice
     *            Answer to the first question, 0 or 1. Only the second
     *            question depends on it.
     * @return The question asked after the stage, or null when the stage is
     *         not followed by one. After the first answer 1 it starts with a
     *         notice, and the text is Sera's line.
     */
    public static StoryQuestion question(final int stage,
            final int firstChoice) {
        if (stage == StoryProgress.FIRST_QUESTION_STAGE)
            return question(null, "story.question.first.text",
                    "story.question.first.assault.title",
                    "story.question.first.rescue.title",
                    "story.question.first.assault.detail",
                    "story.question.first.rescue.detail");
        if (stage == StoryProgress.MID_BOSS_STAGE) {
            if (firstChoice == 0)
                return question(null, "story.question.assault.text",
                        "story.question.assault.absorb.title",
                        "story.question.assault.destroy.title",
                        "story.question.assault.absorb.detail",
                        "story.question.assault.destroy.detail");
            return question("story.question.rescue.lead",
                    "story.question.rescue.text",
                    "story.question.rescue.escort.title",
                    "story.question.rescue.infiltrate.title",
                    "story.question.rescue.escort.detail",
                    "story.question.rescue.infiltrate.detail");
        }
        return null;
    }

    private static StoryQuestion question(final String leadKey,
            final String textKey, final String firstTitleKey,
            final String secondTitleKey, final String firstDetailKey,
            final String secondDetailKey) {
        return new StoryQuestion(
                leadKey == null ? null : LanguageManager.get(leadKey),
                LanguageManager.get(textKey),
                new String[] { LanguageManager.get(firstTitleKey),
                        LanguageManager.get(secondTitleKey) },
                new String[] { LanguageManager.get(firstDetailKey),
                        LanguageManager.get(secondDetailKey) });
    }

    /**
     * @param stage
     *            Story stage that was just cleared.
     * @param firstChoice
     *            Answer to the first question, 0 or 1. Only the second
     *            question depends on it.
     * @return Who asks the question after the stage, as a conversation at the
     *         top of the board, or null when the stage has no question. The
     *         woman at the control tower asks the first question and the
     *         second one after the first answer 0; the second one after the
     *         answer 1 is Sera's, after a notice.
     */
    public static StorySpeaker questionSpeaker(final int stage,
            final int firstChoice) {
        if (stage == StoryProgress.FIRST_QUESTION_STAGE)
            return StorySpeaker.CONTROL;
        if (stage == StoryProgress.MID_BOSS_STAGE)
            return firstChoice == 0 ? StorySpeaker.CONTROL : StorySpeaker.SERA;
        return null;
    }

    /**
     * @param stage
     *            Story stage, starting at 1.
     * @return What the pilot says when the stage begins, or null when the
     *         stage starts without a line.
     */
    public static String stageIntroLine(final int stage) {
        return stage == 1 ? LanguageManager.get("story.stage1Line") : null;
    }

    /**
     * @param firstChoice
     *            Answer to the first question, 0 or 1.
     * @return Name of the boss of stage 3 that this answer leads to, in the
     *         selected language.
     */
    public static String bossName(final int firstChoice) {
        return LanguageManager.get(
                "story.boss." + checkChoice(firstChoice) + ".name");
    }

    /**
     * @param stage
     *            Story stage, starting at 1.
     * @param firstChoice
     *            Answer to the first question, 0 or 1.
     * @return What the boss says when it appears at the start of the stage,
     *         in two lines that fit the dialogue box, or null when the stage
     *         has no boss line. Only the boss of stage 3 speaks so far.
     */
    public static String bossIntroLine(final int stage, final int firstChoice) {
        checkChoice(firstChoice);
        return stage == StoryProgress.MID_BOSS_STAGE
                ? LanguageManager.get("story.boss." + firstChoice + ".line")
                : null;
    }

    private static int checkChoice(final int choice) {
        if (choice != 0 && choice != 1)
            throw new IllegalArgumentException("firstChoice must be 0 or 1.");
        return choice;
    }
}
