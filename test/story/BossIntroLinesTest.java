// 스테이지 3 보스 두 명의 이름과 영어 등장 대사가 정해진 대로 있고 두 줄로 되어 있는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.LanguageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BossIntroLinesTest {

    private LanguageManager.Language previousLanguage;

    @BeforeEach
    void useEnglish() {
        previousLanguage = LanguageManager.getCurrentLanguage();
        LanguageManager.setCurrentLanguage(LanguageManager.Language.ENGLISH);
    }

    @AfterEach
    void restoreLanguage() {
        LanguageManager.setCurrentLanguage(previousLanguage);
    }

    @Test
    void theBossesOfStageThreeHaveTheirNames() {
        assertEquals("ARGUS", StoryScript.bossName(0));
        assertEquals("NOX", StoryScript.bossName(1));
    }

    @Test
    void eachBossSaysItsOwnTwoLinesWhenItAppears() {
        assertEquals("A worm of a puny planet...\nAnd you made it this far.",
                StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 0));
        assertEquals("There are no survivors.\nI will leave none alive.",
                StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 1));
    }

    @Test
    void everyLineIsWrittenAsExactlyTwoLines() {
        for (int boss = 0; boss <= 1; boss++) {
            String line = StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, boss);
            assertNotNull(line);
            String[] lines = line.split("\n", -1);
            assertEquals(2, lines.length, "boss " + boss);
            for (String one : lines) {
                assertTrue(!one.isBlank() && one.equals(one.strip()),
                        "no empty line and no spaces around: '" + one + "'");
            }
        }
    }

    @Test
    void theBossesSpeakKoreanWhenKoreanIsSelected() {
        LanguageManager.setCurrentLanguage(LanguageManager.Language.KOREAN);
        assertEquals("아르고스", StoryScript.bossName(0));
        assertEquals("녹스", StoryScript.bossName(1));
        assertEquals("하찮은 행성의 벌레가…\n여기까지 오다니.",
                StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 0));
        assertEquals("생존자는 없다.\n남겨두지 않는다.",
                StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 1));
        for (int boss = 0; boss <= 1; boss++)
            assertEquals(2, StoryScript.bossIntroLine(
                    StoryProgress.MID_BOSS_STAGE, boss).split("\n", -1).length);
    }

    @Test
    void theLinesAreEnglishTextTheGameFontCanDraw() {
        for (int boss = 0; boss <= 1; boss++) {
            String line = StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, boss);
            for (char c : (line + StoryScript.bossName(boss)).toCharArray())
                assertTrue(c < 128, "only ASCII, the game font has no other glyphs");
        }
    }

    @Test
    void onlyTheBossStageHasABossLine() {
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
            if (stage != StoryProgress.MID_BOSS_STAGE) {
                assertNull(StoryScript.bossIntroLine(stage, 0), "stage " + stage);
                assertNull(StoryScript.bossIntroLine(stage, 1), "stage " + stage);
            }
        }
    }

    @Test
    void theTwoBossesSayDifferentThings() {
        assertNotEquals(StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 0),
                StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 1));
    }

    @Test
    void onlyTheAnswersZeroAndOneAreAccepted() {
        assertThrows(IllegalArgumentException.class, () -> StoryScript.bossName(2));
        assertThrows(IllegalArgumentException.class, () -> StoryScript.bossName(-1));
        assertThrows(IllegalArgumentException.class,
                () -> StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, 2));
    }
}
