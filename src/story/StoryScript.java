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
     *         not followed by one.
     */
    public static StoryQuestion question(final int stage,
            final int firstChoice) {
        if (stage == StoryProgress.FIRST_QUESTION_STAGE)
            return question("story.question.first.text",
                    "story.question.first.assault.title",
                    "story.question.first.rescue.title",
                    "story.question.first.assault.detail",
                    "story.question.first.rescue.detail");
        if (stage == StoryProgress.MID_BOSS_STAGE) {
            if (firstChoice == 0)
                return question("story.question.assault.text",
                        "story.question.assault.absorb.title",
                        "story.question.assault.destroy.title",
                        "story.question.assault.absorb.detail",
                        "story.question.assault.destroy.detail");
            return question("story.question.rescue.text",
                    "story.question.rescue.escort.title",
                    "story.question.rescue.infiltrate.title",
                    "story.question.rescue.escort.detail",
                    "story.question.rescue.infiltrate.detail");
        }
        return null;
    }

    private static StoryQuestion question(final String textKey,
            final String firstTitleKey, final String secondTitleKey,
            final String firstDetailKey, final String secondDetailKey) {
        return new StoryQuestion(LanguageManager.get(textKey),
                new String[] { LanguageManager.get(firstTitleKey),
                        LanguageManager.get(secondTitleKey) },
                new String[] { LanguageManager.get(firstDetailKey),
                        LanguageManager.get(secondDetailKey) });
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
}
