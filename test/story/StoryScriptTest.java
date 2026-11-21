// 오프닝 설정 문장과 스테이지별 조종사 대사가 올바르게 정의됐는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoryScriptTest {

    @Test
    void openingNarrationIsEnglishText() {
        String text = StoryScript.OPENING_NARRATION;
        assertFalse(text.isBlank());
        assertTrue(text.contains("OCTO-1"));
        for (char c : text.toCharArray())
            assertTrue(c < 128, "only ASCII, the game font has no other glyphs");
    }

    @Test
    void onlyStageOneStartsWithAPilotLine() {
        String line = StoryScript.stageIntroLine(1);
        assertNotNull(line);
        assertFalse(line.isBlank());
        for (int stage = 2; stage <= StoryProgress.TOTAL_STAGES; stage++)
            assertNull(StoryScript.stageIntroLine(stage), "stage " + stage);
    }

    @Test
    void pilotHasAName() {
        assertEquals("PILOT", StoryScript.PILOT_NAME);
    }

    @Test
    void questionsFollowStageOneAndStageThreeOnly() {
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
            boolean asked = stage == StoryProgress.FIRST_QUESTION_STAGE
                    || stage == StoryProgress.MID_BOSS_STAGE;
            assertEquals(asked, StoryScript.question(stage, 0) != null,
                    "stage " + stage);
        }
    }

    @Test
    void secondQuestionDependsOnTheFirstAnswer() {
        StoryQuestion assault = StoryScript.question(3, 0);
        StoryQuestion rescue = StoryScript.question(3, 1);
        assertFalse(assault.getText().equals(rescue.getText()));
        assertEquals("ABSORB", assault.getTitle(0));
        assertEquals("INFILTRATE", rescue.getTitle(1));
    }

    @Test
    void questionTextsAreAscii() {
        for (int stage : new int[] { 1, 3 })
            for (int first = 0; first < 2; first++) {
                StoryQuestion question = StoryScript.question(stage, first);
                String all = question.getText();
                for (int choice = 0; choice < 2; choice++)
                    all += question.getTitle(choice) + question.getDetail(choice);
                for (char c : all.toCharArray())
                    assertTrue(c < 128, "stage " + stage);
            }
    }
}
