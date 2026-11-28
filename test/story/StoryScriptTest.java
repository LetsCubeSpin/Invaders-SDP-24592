// 오프닝 설정 문장과 스테이지별 조종사 대사가 올바르게 정의됐는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.LanguageManager;
import engine.LanguageManager.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoryScriptTest {

    private Language previousLanguage;

    @BeforeEach
    void rememberLanguage() {
        previousLanguage = LanguageManager.getCurrentLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        LanguageManager.setCurrentLanguage(previousLanguage);
    }

    @Test
    void openingNarrationUsesKoreanWhenKoreanIsSelected() {
        LanguageManager.setCurrentLanguage(Language.KOREAN);
        assertTrue(StoryScript.openingNarration().contains("화성"));
        assertEquals("조종사", StoryScript.pilotName());
        assertEquals("놈들을 전부 쓸어버리자.", StoryScript.stageIntroLine(1));
    }

    @Test
    void storyQuestionUsesKoreanWhenKoreanIsSelected() {
        LanguageManager.setCurrentLanguage(Language.KOREAN);
        StoryQuestion first = StoryScript.question(1, 0);
        assertTrue(first.getText().contains("신호"));
        assertEquals("공격", first.getTitle(0));
        assertEquals("적 지휘관을 먼저 공격하기", first.getDetail(0));
        assertEquals("달 기지 생존자들을 구하기", first.getDetail(1));

        StoryQuestion assault = StoryScript.question(3, 0);
        assertTrue(assault.getText().contains("아르고스"));
        assertEquals("흡수", assault.getTitle(0));
        assertEquals("코어를 흡수하기", assault.getDetail(0));
        assertEquals("코어를 파괴하기", assault.getDetail(1));

        StoryQuestion rescue = StoryScript.question(3, 1);
        assertTrue(rescue.getLead().contains("세라"));
        assertEquals("날 믿어. 모선으로 가는 길을 알려줄게.", rescue.getText());
        assertEquals("호위", rescue.getTitle(0));
        assertEquals("귀환 비행을 호위하기", rescue.getDetail(0));
        assertEquals("모선에 침투하기", rescue.getDetail(1));
    }

    @Test
    void openingNarrationIsEnglishText() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        String text = StoryScript.openingNarration();
        assertFalse(text.isBlank());
        assertTrue(text.contains("OCTO-1"));
        for (char c : text.toCharArray())
            assertTrue(c < 128, "only ASCII, the game font has no other glyphs");
    }

    @Test
    void onlyStageOneStartsWithAPilotLine() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        String line = StoryScript.stageIntroLine(1);
        assertNotNull(line);
        assertFalse(line.isBlank());
        for (int stage = 2; stage <= StoryProgress.TOTAL_STAGES; stage++)
            assertNull(StoryScript.stageIntroLine(stage), "stage " + stage);
    }

    @Test
    void pilotHasAName() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        assertEquals("PILOT", StoryScript.pilotName());
    }

    @Test
    void questionsFollowStageOneAndStageThreeOnly() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
            boolean asked = stage == StoryProgress.FIRST_QUESTION_STAGE
                    || stage == StoryProgress.MID_BOSS_STAGE;
            assertEquals(asked, StoryScript.question(stage, 0) != null,
                    "stage " + stage);
        }
    }

    @Test
    void secondQuestionDependsOnTheFirstAnswer() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        StoryQuestion assault = StoryScript.question(3, 0);
        StoryQuestion rescue = StoryScript.question(3, 1);
        assertFalse(assault.getText().equals(rescue.getText()));
        assertEquals("ABSORB", assault.getTitle(0));
        assertEquals("INFILTRATE", rescue.getTitle(1));
    }

    @Test
    void afterTheAnswerOneSaraIsIntroducedByANoticeAndThenSpeaks() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        StoryQuestion rescue = StoryScript.question(3, 1);
        assertEquals("Among the people we rescued was a Swarm scientist, Sera.",
                rescue.getLead());
        assertEquals("Trust me. I will show you the way to the mothership.",
                rescue.getText());
        assertEquals(StorySpeaker.SERA,
                StoryScript.questionSpeaker(StoryProgress.MID_BOSS_STAGE, 1));
        // The other questions start with their text, without a notice.
        assertNull(StoryScript.question(3, 0).getLead());
        assertNull(StoryScript.question(1, 0).getLead());
    }

    @Test
    void theSpeakersHaveTheirNamesInBothLanguages() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        assertEquals("CONTROL", StorySpeaker.CONTROL.displayName());
        assertEquals("SERA", StorySpeaker.SERA.displayName());
        LanguageManager.setCurrentLanguage(Language.KOREAN);
        assertEquals("관제탑", StorySpeaker.CONTROL.displayName());
        assertEquals("세라", StorySpeaker.SERA.displayName());
    }

    @Test
    void everyQuestionHasASpeakerAndNoOtherStageHas() {
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++)
            for (int first = 0; first < 2; first++)
                assertEquals(StoryScript.question(stage, first) != null,
                        StoryScript.questionSpeaker(stage, first) != null,
                        "stage " + stage + ", answer " + first);
    }

    @Test
    void questionTextsAreAscii() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        for (int stage : new int[] { 1, 3 })
            for (int first = 0; first < 2; first++) {
                StoryQuestion question = StoryScript.question(stage, first);
                String all = question.getText();
                if (question.getLead() != null)
                    all += question.getLead();
                for (int choice = 0; choice < 2; choice++)
                    all += question.getTitle(choice) + question.getDetail(choice);
                for (char c : all.toCharArray())
                    assertTrue(c < 128, "stage " + stage);
            }
    }
}
