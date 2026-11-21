package records;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Progress, tiers and hidden achievements, and keeping the saved unlocks. */
class AchievementDefinitionTest {

    @TempDir Path temp;

    /** A TOTAL achievement with three tiers, like the game could define. */
    private static final Achievement EXTERMINATOR = new Achievement("exterminator",
            "Exterminator", "Defeat {target} enemies in total.",
            Achievement.Scope.TOTAL, Stat.ENEMIES_DEFEATED, false, 100, 500, 1000);
    /** A single-tier RUN achievement. */
    private static final Achievement SHARPSHOOTER = new Achievement("sharpshooter",
            "Sharpshooter", "Hit {target} bullets in one run.",
            Achievement.Scope.RUN, Stat.BULLET_HITS, false, 20);
    /** A hidden single-tier achievement. */
    private static final Achievement PUNCHING_BAG = new Achievement("punching_bag",
            "Punching Bag", "Get hit {target} times in one run.",
            Achievement.Scope.RUN, Stat.TIMES_HIT, true, 3);

    private Path profile() {
        return this.temp.resolve("profile.properties");
    }

    private RecordsService service(final Achievement... achievements) {
        return new RecordsService(profile(), Arrays.asList(achievements));
    }

    private static RunRecord run(final String id, final int enemies,
            final long completed) {
        return new RunRecord(id, RunMode.CAMPAIGN, 100, enemies, 1000, completed,
                RunStats.EMPTY);
    }

    private static RunRecord run(final String id, final long completed,
            final int hits, final int timesHit) {
        return new RunRecord(id, RunMode.CAMPAIGN, 100, 0, 1000, completed,
                new RunStats(1, 1, hits, hits, timesHit, 0, 0));
    }

    private static AchievementStatus status(final RecordsService records,
            final Achievement achievement) {
        for (AchievementStatus status : records.getAchievementStatuses())
            if (status.getAchievement() == achievement)
                return status;
        throw new AssertionError("no status for " + achievement);
    }

    @Nested
    class Progress {

        @Test
        void aTotalAchievementAddsUpEveryCompletedRun() {
            RecordsService records = service(EXTERMINATOR);
            try {
                records.completeRun(run("a", 40, 1));
                records.completeRun(run("b", 25, 2));

                assertEquals(65, records.getProgress(EXTERMINATOR));
                assertEquals("65/100", status(records, EXTERMINATOR).getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void anAbandonedRunDoesNotCountTowardsATotal() {
            RecordsService records = service(EXTERMINATOR);
            try {
                records.startRun("abandoned");
                for (int i = 0; i < 150; i++)
                    records.enemyDefeated("abandoned", "enemy-" + i);
                records.abandonRun("abandoned");

                assertEquals(0, records.getProgress(EXTERMINATOR));
                assertNull(records.getUnlockTime(EXTERMINATOR));
            } finally {
                records.close();
            }
        }

        @Test
        void aRunAchievementShowsTheBestSingleRun() {
            RecordsService records = service(SHARPSHOOTER);
            try {
                records.completeRun(run("a", 1, 13, 0));
                records.completeRun(run("b", 2, 9, 0));

                assertEquals(13, records.getProgress(SHARPSHOOTER));
                assertEquals("13/20", status(records, SHARPSHOOTER).getProgressText());
                assertNull(records.getUnlockTime(SHARPSHOOTER), "13 + 9 is not one run");
            } finally {
                records.close();
            }
        }

        @Test
        void progressIsCappedAtTheTargetOnceComplete() {
            RecordsService records = service(SHARPSHOOTER);
            try {
                records.completeRun(run("a", 5, 31, 0));

                AchievementStatus status = status(records, SHARPSHOOTER);
                assertTrue(status.isComplete());
                assertEquals("20/20", status.getProgressText());
                assertEquals(Long.valueOf(5), status.getLastUnlockTime());
            } finally {
                records.close();
            }
        }

        @Test
        void progressIsRebuiltFromTheSavedRunsAfterARestart() {
            RecordsService records = service(EXTERMINATOR, SHARPSHOOTER);
            records.completeRun(run("a", 1, 13, 0));
            records.completeRun(run("b", 70, 2));
            records.close();

            RecordsService restarted = service(EXTERMINATOR, SHARPSHOOTER);
            try {
                assertEquals(70, restarted.getProgress(EXTERMINATOR));
                assertEquals(13, restarted.getProgress(SHARPSHOOTER));
            } finally {
                restarted.close();
            }
        }

        @Test
        void aRunAchievementAddedLaterUnlocksOnTheNextCompletedRun() {
            RecordsService before = service();
            before.completeRun(run("old", 1, 25, 0));
            before.close();

            RecordsService records = service(SHARPSHOOTER);
            try {
                assertEquals("20/20", status(records, SHARPSHOOTER).getProgressText());
                assertNull(records.getUnlockTime(SHARPSHOOTER), "no unlock while loading");
                records.completeRun(run("next", 9, 0, 0));
                assertEquals(Long.valueOf(9), records.getUnlockTime(SHARPSHOOTER));
            } finally {
                records.close();
            }
        }

        @Test
        void endingsCountTowardsEveryEnding() {
            RecordsService records = service(Achievement.ALL_ENDINGS);
            try {
                records.endingSeen(story.Ending.ENDING1);
                records.endingSeen(story.Ending.ENDING3);
                assertEquals("2/4", status(records, Achievement.ALL_ENDINGS).getProgressText());
            } finally {
                records.close();
            }
        }
    }

    @Nested
    class Tiers {

        @Test
        void tiersAreBronzeSilverAndGold() {
            assertEquals(3, EXTERMINATOR.getTierCount());
            assertEquals("Exterminator (Bronze)", EXTERMINATOR.getTitle(1));
            assertEquals("Exterminator (Gold)", EXTERMINATOR.getTitle(3));
            assertEquals("Defeat 500 enemies in total.", EXTERMINATOR.getCondition(2));
            assertEquals("Ace Pilot", Achievement.TWENTY_ENEMIES.getTitle(1),
                    "a single tier has no tier name");
        }

        @Test
        void tiersReachedCountsTheTargetsMet() {
            assertEquals(0, EXTERMINATOR.tiersReached(99));
            assertEquals(1, EXTERMINATOR.tiersReached(100));
            assertEquals(2, EXTERMINATOR.tiersReached(999));
            assertEquals(3, EXTERMINATOR.tiersReached(5000));
        }

        @Test
        void onlyTheReachedTierUnlocksAndTheNextTargetIsShown() {
            RecordsService records = service(EXTERMINATOR);
            try {
                records.completeRun(run("a", 120, 10));

                assertEquals(1, records.getUnlockedTiers(EXTERMINATOR));
                assertEquals(Long.valueOf(10), records.getUnlockTime(EXTERMINATOR, 1));
                assertNull(records.getUnlockTime(EXTERMINATOR, 2));
                AchievementStatus status = status(records, EXTERMINATOR);
                assertEquals("Exterminator (Bronze)", status.getTitle());
                assertEquals("Defeat 500 enemies in total.", status.getCondition());
                assertEquals("120/500", status.getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void skippingTiersUnlocksEachOneInOrder() {
            RecordsService records = service(EXTERMINATOR);
            try {
                records.completeRun(run("a", 120, 10));
                records.pollNotification();
                records.startRun("b");
                records.completeRun(run("b", 1000, 20));

                assertEquals(new AchievementUnlock(EXTERMINATOR, 2), records.pollNotification());
                assertEquals(new AchievementUnlock(EXTERMINATOR, 3), records.pollNotification());
                assertNull(records.pollNotification());
                assertEquals(Arrays.asList(new AchievementUnlock(EXTERMINATOR, 2),
                        new AchievementUnlock(EXTERMINATOR, 3)), records.getCurrentRunUnlocks());
                assertEquals("Exterminator (Gold)", status(records, EXTERMINATOR).getTitle());
                assertEquals(Long.valueOf(20), records.getUnlockTime(EXTERMINATOR, 3));
                assertEquals(Long.valueOf(10), records.getUnlockTime(EXTERMINATOR, 1),
                        "an earlier tier keeps its time");
            } finally {
                records.close();
            }
        }

        @Test
        void eachTierKeepsItsTimeAfterARestart() throws IOException {
            RecordsService records = service(EXTERMINATOR);
            records.completeRun(run("a", 120, 10));
            records.completeRun(run("b", 400, 20));
            records.close();
            String saved = new String(Files.readAllBytes(profile()), StandardCharsets.UTF_8);
            assertTrue(saved.contains("tier=2"), saved);

            RecordsService restarted = service(EXTERMINATOR);
            try {
                assertEquals(2, restarted.getUnlockedTiers(EXTERMINATOR));
                assertEquals(Long.valueOf(10), restarted.getUnlockTime(EXTERMINATOR, 1));
                assertEquals(Long.valueOf(20), restarted.getUnlockTime(EXTERMINATOR, 2));
            } finally {
                restarted.close();
            }
        }

        @Test
        void invalidTargetsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> new Achievement("a", "A", "c",
                    Achievement.Scope.TOTAL, Stat.SCORE, false, 500, 100));
            assertThrows(IllegalArgumentException.class, () -> new Achievement("a", "A", "c",
                    Achievement.Scope.TOTAL, Stat.SCORE, false, 1, 2, 3, 4));
            assertThrows(IllegalArgumentException.class, () -> new Achievement("a", "A", "c",
                    Achievement.Scope.TOTAL, Stat.SCORE, false));
            assertThrows(IllegalArgumentException.class, () -> new Achievement("a", "A", "c",
                    Achievement.Scope.RUN, Stat.ENDINGS_SEEN, false, 1),
                    "endings are not counted per run");
        }

        @Test
        void aTierFieldOutsideTheTiersIsRejected() throws IOException {
            Files.write(profile(), ("version=2\nrecord.count=0\nachievement.count=1\n"
                    + "achievement.0.id=exterminator\nachievement.0.tier=4\n"
                    + "achievement.0.unlocked=5\n").getBytes(StandardCharsets.UTF_8));
            RecordsService records = service(EXTERMINATOR);
            try {
                assertTrue(records.isWritesBlocked());
            } finally {
                records.close();
            }
        }
    }

    @Nested
    class Hidden {

        @Test
        void aLockedHiddenAchievementShowsQuestionMarks() {
            RecordsService records = service(PUNCHING_BAG);
            try {
                records.completeRun(run("a", 1, 0, 2));

                AchievementStatus status = status(records, PUNCHING_BAG);
                assertFalse(status.isRevealed());
                assertEquals("???", status.getTitle());
                assertEquals("???", status.getCondition());
                assertEquals("", status.getProgressText(), "progress would give it away");
            } finally {
                records.close();
            }
        }

        @Test
        void unlockingRevealsItAndTheNoticeNamesIt() {
            RecordsService records = service(PUNCHING_BAG);
            try {
                records.completeRun(run("a", 1, 0, 3));

                AchievementStatus status = status(records, PUNCHING_BAG);
                assertTrue(status.isRevealed());
                assertEquals("Punching Bag", status.getTitle());
                assertEquals("Get hit 3 times in one run.", status.getCondition());
                assertEquals("3/3", status.getProgressText());
                AchievementUnlock notice = records.pollNotification();
                assertEquals("Punching Bag", notice.getTitle());
            } finally {
                records.close();
            }
        }

        @Test
        void aVisibleLockedAchievementShowsItsNameAndCondition() {
            RecordsService records = service(SHARPSHOOTER);
            try {
                AchievementStatus status = status(records, SHARPSHOOTER);
                assertTrue(status.isRevealed());
                assertEquals("Sharpshooter", status.getTitle());
                assertEquals("Hit 20 bullets in one run.", status.getCondition());
                assertEquals("0/20", status.getProgressText());
            } finally {
                records.close();
            }
        }
    }

    @Nested
    class SavedUnlocks {

        private static final String[] LEGACY_IDS = { "first_run", "first_enemy",
                "twenty_enemies", "sixty_seconds", "beat_high_score", "all_endings" };

        private String legacyProfile() {
            StringBuilder text = new StringBuilder("version=2\nrecord.count=0\n"
                    + "achievement.count=" + LEGACY_IDS.length + "\n");
            for (int i = 0; i < LEGACY_IDS.length; i++)
                text.append("achievement.").append(i).append(".id=").append(LEGACY_IDS[i])
                        .append("\nachievement.").append(i).append(".unlocked=")
                        .append(1700000000000L + i).append('\n');
            return text.toString();
        }

        @Test
        void theExistingAchievementsKeepTheirIds() {
            List<String> ids = new ArrayList<String>();
            for (Achievement achievement : Achievement.values())
                ids.add(achievement.getId());
            assertEquals(Arrays.asList(LEGACY_IDS), ids.subList(0, LEGACY_IDS.length));
        }

        @Test
        void unlockStatesAndTimesSurviveLoadingAndSaving() throws IOException {
            Files.write(profile(), legacyProfile().getBytes(StandardCharsets.UTF_8));
            RecordsService records = new RecordsService(profile());
            try {
                assertFalse(records.isWritesBlocked());
                for (int i = 0; i < LEGACY_IDS.length; i++)
                    assertEquals(Long.valueOf(1700000000000L + i),
                            records.getUnlockTime(Achievement.fromId(LEGACY_IDS[i])));
                // A completed run saves the profile again; nothing new unlocks
                // and nothing is notified for the old unlocks.
                records.completeRun(run("new", 30, 1800000000000L));
                assertTrue(records.getCurrentRunUnlocks().isEmpty());
            } finally {
                records.close();
            }

            String saved = new String(Files.readAllBytes(profile()), StandardCharsets.UTF_8);
            assertFalse(saved.contains(".tier="),
                    "single-tier achievements are saved as before tiers existed");
            RecordsService restarted = new RecordsService(profile());
            try {
                for (int i = 0; i < LEGACY_IDS.length; i++)
                    assertEquals(Long.valueOf(1700000000000L + i),
                            restarted.getUnlockTime(Achievement.fromId(LEGACY_IDS[i])));
            } finally {
                restarted.close();
            }
        }

        @Test
        void unknownAchievementIdsAreKept() throws IOException {
            Files.write(profile(), ("version=2\nrecord.count=0\nachievement.count=2\n"
                    + "achievement.0.id=from_another_branch\nachievement.0.unlocked=7\n"
                    + "achievement.1.id=from_another_branch\nachievement.1.tier=2\n"
                    + "achievement.1.unlocked=8\n").getBytes(StandardCharsets.UTF_8));
            RecordsService records = service(EXTERMINATOR);
            records.completeRun(run("a", 1, 9));
            records.close();
            assertFalse(records.isWritesBlocked());

            ProfileSnapshot saved = new ProfileStore(profile()).load();
            assertEquals(Long.valueOf(7), saved.getUnlockTimes().get("from_another_branch"));
            assertEquals(Long.valueOf(8), saved.getUnlockTimes().get("from_another_branch#2"));
        }

        @Test
        void legacyAchievementsStillUnlockAsBefore() {
            RecordsService records = new RecordsService(profile());
            try {
                records.startRun("first");
                for (int i = 0; i < 20; i++)
                    records.enemyDefeated("first", "enemy-" + i);
                records.activeDuration("first", 60000);
                assertEquals(Arrays.asList(new AchievementUnlock(Achievement.FIRST_ENEMY, 1),
                        new AchievementUnlock(Achievement.TWENTY_ENEMIES, 1),
                        new AchievementUnlock(Achievement.SIXTY_SECONDS, 1)),
                        records.getCurrentRunUnlocks(), "unlocked during play");
                records.completeRun(run("first", 20, 5));
                assertEquals(Long.valueOf(5), records.getUnlockTime(Achievement.FIRST_RUN));
                assertNull(records.getUnlockTime(Achievement.BEAT_HIGH_SCORE));
                records.startRun("second");
                records.completeRun(new RunRecord("second", 101, 0, 0, 6));
                assertEquals(Long.valueOf(6), records.getUnlockTime(Achievement.BEAT_HIGH_SCORE));
                assertNotNull(records.getUnlockTime(Achievement.FIRST_ENEMY));
            } finally {
                records.close();
            }
        }
    }

    @Test
    void theGameListsEveryDefinedAchievement() {
        RecordsService records = new RecordsService(profile());
        try {
            List<Achievement> listed = new ArrayList<Achievement>();
            for (AchievementStatus status : records.getAchievementStatuses())
                listed.add(status.getAchievement());
            assertEquals(Achievement.values(), listed);
            assertEquals(Collections.emptyList(), records.getCurrentRunUnlocks());
        } finally {
            records.close();
        }
    }
}
