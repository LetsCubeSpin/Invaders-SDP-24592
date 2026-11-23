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

/**
 * Progress, tiers, hidden achievements and requirements, the example
 * achievements of the game, and keeping the saved unlocks.
 */
class AchievementDefinitionTest {

    @TempDir Path temp;

    /** A TOTAL achievement with three tiers. */
    private static final Achievement KILLS = Achievement.define("kills",
            "Exterminator", "Defeat {target} enemies in total.")
            .total(Stat.ENEMIES_DEFEATED, 100, 500, 1000).build();
    /** A single-tier RUN achievement. */
    private static final Achievement HITS = Achievement.define("hits",
            "Sharpshooter", "Hit {target} bullets in one run.")
            .run(Stat.BULLET_HITS, 20).build();
    /** A hidden single-tier achievement. */
    private static final Achievement PUNCHING_BAG = Achievement.define("punching_bag",
            "Punching Bag", "Get hit {target} times in one run.")
            .run(Stat.TIMES_HIT, 3).hidden().build();

    private Path profile() {
        return this.temp.resolve("profile.properties");
    }

    private RecordsService service(final Achievement... achievements) {
        return new RecordsService(profile(), Arrays.asList(achievements));
    }

    /** A Campaign run with every bullet hitting and no life lost. */
    private static RunRecord run(final String id, final int enemies,
            final long completed) {
        return run(id, completed, enemies, 0, 0, 0);
    }

    private static RunRecord run(final String id, final long completed,
            final int hits, final int timesHit) {
        return run(id, completed, 0, hits, hits, timesHit);
    }

    private static RunRecord run(final String id, final long completed,
            final int enemies, final int shots, final int hits, final int timesHit) {
        return new RunRecord(id, RunMode.CAMPAIGN, 100, enemies, 1000, completed,
                new RunStats(1, 1, shots, hits, timesHit, 0, 0));
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
            RecordsService records = service(KILLS);
            try {
                records.completeRun(run("a", 40, 1));
                records.completeRun(run("b", 25, 2));

                assertEquals(65, records.getProgress(KILLS));
                assertEquals("65/100", status(records, KILLS).getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void anAbandonedRunDoesNotCountTowardsATotal() {
            RecordsService records = service(KILLS);
            try {
                records.startRun("abandoned");
                for (int i = 0; i < 150; i++)
                    records.enemyDefeated("abandoned", "enemy-" + i);
                records.abandonRun("abandoned");

                assertEquals(0, records.getProgress(KILLS));
                assertNull(records.getUnlockTime(KILLS));
            } finally {
                records.close();
            }
        }

        @Test
        void aRunAchievementShowsTheBestSingleRun() {
            RecordsService records = service(HITS);
            try {
                records.completeRun(run("a", 1, 13, 0));
                records.completeRun(run("b", 2, 9, 0));

                assertEquals(13, records.getProgress(HITS));
                assertEquals("13/20", status(records, HITS).getProgressText());
                assertNull(records.getUnlockTime(HITS), "13 + 9 is not one run");
            } finally {
                records.close();
            }
        }

        @Test
        void progressIsCappedAtTheTargetOnceComplete() {
            RecordsService records = service(HITS);
            try {
                records.completeRun(run("a", 5, 31, 0));

                AchievementStatus status = status(records, HITS);
                assertTrue(status.isComplete());
                assertEquals("20/20", status.getProgressText());
                assertEquals(Long.valueOf(5), status.getLastUnlockTime());
            } finally {
                records.close();
            }
        }

        @Test
        void progressIsRebuiltFromTheSavedRunsAfterARestart() {
            RecordsService records = service(KILLS, HITS);
            records.completeRun(run("a", 1, 13, 0));
            records.completeRun(run("b", 70, 2));
            records.close();

            RecordsService restarted = service(KILLS, HITS);
            try {
                assertEquals(70, restarted.getProgress(KILLS));
                assertEquals(13, restarted.getProgress(HITS));
            } finally {
                restarted.close();
            }
        }

        @Test
        void aRunAchievementAddedLaterUnlocksOnTheNextCompletedRun() {
            RecordsService before = service();
            before.completeRun(run("old", 1, 25, 0));
            before.close();

            RecordsService records = service(HITS);
            try {
                assertEquals("20/20", status(records, HITS).getProgressText());
                assertNull(records.getUnlockTime(HITS), "no unlock while loading");
                records.completeRun(run("next", 9, 0, 0));
                assertEquals(Long.valueOf(9), records.getUnlockTime(HITS));
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
            assertEquals(3, KILLS.getTierCount());
            assertEquals("Exterminator (Bronze)", KILLS.getTitle(1));
            assertEquals("Exterminator (Gold)", KILLS.getTitle(3));
            assertEquals("Defeat 500 enemies in total.", KILLS.getCondition(2));
            assertEquals("Ace Pilot", Achievement.TWENTY_ENEMIES.getTitle(1),
                    "a single tier has no tier name");
        }

        @Test
        void tiersReachedCountsTheTargetsMet() {
            assertEquals(0, KILLS.tiersReached(99));
            assertEquals(1, KILLS.tiersReached(100));
            assertEquals(2, KILLS.tiersReached(999));
            assertEquals(3, KILLS.tiersReached(5000));
        }

        @Test
        void onlyTheReachedTierUnlocksAndTheNextTargetIsShown() {
            RecordsService records = service(KILLS);
            try {
                records.completeRun(run("a", 120, 10));

                assertEquals(1, records.getUnlockedTiers(KILLS));
                assertEquals(Long.valueOf(10), records.getUnlockTime(KILLS, 1));
                assertNull(records.getUnlockTime(KILLS, 2));
                AchievementStatus status = status(records, KILLS);
                assertEquals("Exterminator (Bronze)", status.getTitle());
                assertEquals("Defeat 500 enemies in total.", status.getCondition());
                assertEquals("120/500", status.getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void skippingTiersUnlocksEachOneInOrder() {
            RecordsService records = service(KILLS);
            try {
                records.completeRun(run("a", 120, 10));
                records.pollNotification();
                records.startRun("b");
                records.completeRun(run("b", 1000, 20));

                assertEquals(new AchievementUnlock(KILLS, 2), records.pollNotification());
                assertEquals(new AchievementUnlock(KILLS, 3), records.pollNotification());
                assertNull(records.pollNotification());
                assertEquals(Arrays.asList(new AchievementUnlock(KILLS, 2),
                        new AchievementUnlock(KILLS, 3)), records.getCurrentRunUnlocks());
                assertEquals("Exterminator (Gold)", status(records, KILLS).getTitle());
                assertEquals(Long.valueOf(20), records.getUnlockTime(KILLS, 3));
                assertEquals(Long.valueOf(10), records.getUnlockTime(KILLS, 1),
                        "an earlier tier keeps its time");
            } finally {
                records.close();
            }
        }

        @Test
        void eachTierKeepsItsTimeAfterARestart() throws IOException {
            RecordsService records = service(KILLS);
            records.completeRun(run("a", 120, 10));
            records.completeRun(run("b", 400, 20));
            records.close();
            String saved = new String(Files.readAllBytes(profile()), StandardCharsets.UTF_8);
            assertTrue(saved.contains("tier=2"), saved);

            RecordsService restarted = service(KILLS);
            try {
                assertEquals(2, restarted.getUnlockedTiers(KILLS));
                assertEquals(Long.valueOf(10), restarted.getUnlockTime(KILLS, 1));
                assertEquals(Long.valueOf(20), restarted.getUnlockTime(KILLS, 2));
            } finally {
                restarted.close();
            }
        }

        @Test
        void invalidTargetsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .total(Stat.SCORE, 500, 100).build());
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .total(Stat.SCORE, 1, 2, 3, 4).build());
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .total(Stat.SCORE).build());
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .build(), "a definition must say what it measures");
        }

        @Test
        void aTierFieldOutsideTheTiersIsRejected() throws IOException {
            Files.write(profile(), ("version=2\nrecord.count=0\nachievement.count=1\n"
                    + "achievement.0.id=kills\nachievement.0.tier=4\n"
                    + "achievement.0.unlocked=5\n").getBytes(StandardCharsets.UTF_8));
            RecordsService records = service(KILLS);
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
            RecordsService records = service(HITS);
            try {
                AchievementStatus status = status(records, HITS);
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
    class Requirements {

        /** At most a value: enemies only count in a run without losing a life. */
        private final Achievement cleanKills = Achievement.define("clean_kills",
                "Clean Kills", "Defeat {target} enemies in one run without losing a life.")
                .run(Stat.ENEMIES_DEFEATED, 30).atMost(Stat.TIMES_HIT, 0).build();
        /** A requirement on a TOTAL achievement. */
        private final Achievement cleanRuns = Achievement.define("clean_runs",
                "Clean Record", "Complete {target} runs without losing a life.")
                .total(Stat.RUNS_COMPLETED, 3).atMost(Stat.TIMES_HIT, 0).build();
        /** Every value counted live, so it is checked during play. */
        private final Achievement quickKills = Achievement.define("quick_kills",
                "Blitz", "Defeat {target} enemies in the first 10 seconds.")
                .run(Stat.ENEMIES_DEFEATED, 5).atMost(Stat.ACTIVE_SECONDS, 10).build();

        @Test
        void onlyRunsMeetingTheRequirementCount() {
            RecordsService records = service(this.cleanKills);
            try {
                records.completeRun(run("hit", 1, 40, 0, 0, 1));
                records.completeRun(run("clean", 2, 25, 0, 0, 0));

                assertEquals("25/30", status(records, this.cleanKills).getProgressText(),
                        "the 40-enemy run lost a life");
                records.completeRun(run("cleaner", 3, 30, 0, 0, 0));
                assertEquals(Long.valueOf(3), records.getUnlockTime(this.cleanKills));
            } finally {
                records.close();
            }
        }

        @Test
        void aTotalOnlyAddsUpRunsMeetingTheRequirement() {
            RecordsService records = service(this.cleanRuns);
            try {
                records.completeRun(run("a", 1, 5, 0, 0, 0));
                records.completeRun(run("b", 2, 5, 0, 0, 2));
                records.completeRun(run("c", 3, 5, 0, 0, 0));

                assertEquals("2/3", status(records, this.cleanRuns).getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void aRequirementNotCountedLiveWaitsForTheCompletedRun() {
            RecordsService records = service(this.cleanKills);
            try {
                records.startRun("live");
                for (int i = 0; i < 30; i++)
                    records.enemyDefeated("live", "enemy-" + i);
                assertNull(records.getUnlockTime(this.cleanKills),
                        "lives lost are not known during play");

                records.completeRun(run("live", 7, 30, 0, 0, 0));
                assertEquals(Long.valueOf(7), records.getUnlockTime(this.cleanKills));
            } finally {
                records.close();
            }
        }

        @Test
        void requirementsOnLiveValuesAreCheckedDuringPlay() {
            RecordsService records = service(this.quickKills);
            try {
                records.startRun("slow");
                records.activeDuration("slow", 11000);
                for (int i = 0; i < 5; i++)
                    records.enemyDefeated("slow", "enemy-" + i);
                assertNull(records.getUnlockTime(this.quickKills));

                records.startRun("fast");
                records.activeDuration("fast", 9000);
                for (int i = 0; i < 5; i++)
                    records.enemyDefeated("fast", "enemy-" + i);
                assertNotNull(records.getUnlockTime(this.quickKills));
            } finally {
                records.close();
            }
        }

        @Test
        void aRunWithoutStatisticsNeverMeetsARequirementOnThem() {
            RecordsService records = service(this.cleanKills);
            try {
                // Version 1 records stored no lives lost, which reads as 0.
                records.completeRun(new RunRecord("v1", 100, 40, 1000, 1));

                assertEquals(0, records.getProgress(this.cleanKills));
                assertNull(records.getUnlockTime(this.cleanKills));
            } finally {
                records.close();
            }
        }

        @Test
        void invalidCombinationsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .total(Stat.ACCURACY_PERCENT, 80).build(), "accuracy does not add up");
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .run(Stat.ENDINGS_SEEN, 1).build(), "endings are not per run");
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .total(Stat.ENDINGS_SEEN, 4).atLeast(Stat.SCORE, 1).build(),
                    "endings cannot be limited by runs");
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .run(Stat.SCORE, 1).atMost(Stat.ENDINGS_SEEN, 1));
        }
    }

    @Nested
    class Stages {

        /** Plays stages of one run, publishing events the way GameScreen does. */
        static final class Play {
            private final RecordsService records;
            private final RunSession session;
            private final event.GameplayEventPublisher events =
                    new event.GameplayEventPublisher();
            private int nextId;
            private StageClear lastClear;

            Play(final RecordsService records, final String runId) {
                this.records = records;
                this.session = new RunSession(runId, RunMode.CAMPAIGN);
                this.session.listenTo(this.events);
                records.startRun(runId);
            }

            Play stage(final int stage) {
                this.session.stageStarted(stage);
                return this;
            }

            Play shoot(final int shots, final int hits) {
                for (int i = 0; i < shots; i++)
                    this.events.publish(new event.ShotFiredEvent(
                            this.session.getRunId(), "shot-" + this.nextId++, 1));
                for (int i = 0; i < hits; i++)
                    this.events.publish(new event.BulletHitEvent(
                            this.session.getRunId(), "hit-" + this.nextId++));
                return this;
            }

            Play hit() {
                this.events.publish(new event.PlayerHitEvent(this.session.getRunId(),
                        "player-hit-" + this.nextId++, event.PlayerHitSource.ENEMY_BULLET, 1, 2));
                return this;
            }

            Play defeat(final int enemies) {
                for (int i = 0; i < enemies; i++)
                    this.events.publish(new event.EnemyDefeatedEvent(
                            this.session.getRunId(), "enemy-" + this.nextId++));
                return this;
            }

            /** Clears the stage, as GameScreen does on LevelCompletedEvent. */
            boolean clear() {
                this.lastClear = this.session.stageCleared(System.nanoTime());
                return this.lastClear != null && this.records.stageCleared(
                        this.session.getRunId(), this.lastClear);
            }

            /** Clears stages one after another; each costs a life if asked. */
            Play clearStages(final int count, final boolean hit) {
                for (int i = 0; i < count; i++) {
                    stage(this.session.getStats().getStageReached() + 1);
                    if (hit)
                        hit();
                    clear();
                }
                return this;
            }

            RunRecord complete(final long completed) {
                RunRecord record = this.session.finish(100, 0, completed);
                this.records.completeRun(record);
                return record;
            }

            void abandon() {
                this.records.abandonRun(this.session.getRunId());
            }
        }

        private final Achievement flawless = Achievement.define("flawless",
                "Flawless", "Clear a stage without losing a life.")
                .stage(Stat.STAGES_CLEARED, 1).atMost(Stat.TIMES_HIT, 0).build();
        private final Achievement steadyAim = Achievement.define("steady_aim",
                "Steady Aim", "Clear a stage with {target}% accuracy (20+ shots).")
                .stage(Stat.ACCURACY_PERCENT, 70, 85, 100).atLeast(Stat.SHOTS_FIRED, 20).build();
        private final Achievement cleanStages = Achievement.define("clean_stages",
                "Clean Stages", "Clear {target} stages without losing a life.")
                .stageTotal(Stat.STAGES_CLEARED, 3, 6).atMost(Stat.TIMES_HIT, 0).build();

        @Test
        void stageValuesCountOnlyFromTheStartOfTheStage() {
            RecordsService records = service();
            try {
                Play play = new Play(records, "values");
                play.stage(1).shoot(10, 2).hit().defeat(4).clear();
                play.stage(2).shoot(5, 4).defeat(3).clear();

                StageClear stage = play.lastClear;
                assertEquals(2, stage.getStage());
                assertEquals(5, stage.getShotsFired());
                assertEquals(4, stage.getHits());
                assertEquals(0, stage.getTimesHit(), "the hit was in stage 1");
                assertEquals(3, stage.getEnemiesDefeated());
                StatTally values = stage.toTally();
                assertEquals(80, values.get(Stat.ACCURACY_PERCENT));
                assertEquals(1, values.get(Stat.STAGES_CLEARED));
                assertFalse(values.has(Stat.SCORE), "score is not counted per stage");
            } finally {
                records.close();
            }
        }

        @Test
        void aStageIsClearedOnlyOnce() {
            RecordsService records = service();
            try {
                Play play = new Play(records, "once");
                play.stage(1);
                assertTrue(play.clear());
                assertFalse(play.clear(), "a second clear of the same stage is ignored");
                assertEquals(1, play.complete(1).getStagesCleared().size());
            } finally {
                records.close();
            }
        }

        @Test
        void aStageClearedWithoutLosingALifeUnlocksAtOnce() {
            RecordsService records = service(this.flawless);
            try {
                Play play = new Play(records, "run");
                play.stage(1).defeat(5).hit();
                assertTrue(play.clear());
                assertNull(records.getUnlockTime(this.flawless), "stage 1 cost a life");

                play.stage(2).defeat(5).clear();
                assertNotNull(records.getUnlockTime(this.flawless),
                        "a life lost in an earlier stage does not count");
                assertEquals(new AchievementUnlock(this.flawless, 1), records.pollNotification());
            } finally {
                records.close();
            }
        }

        @Test
        void aSingleStageAchievementStaysUnlockedWhenTheRunIsAbandoned() {
            RecordsService records = service(this.flawless);
            try {
                Play play = new Play(records, "abandoned");
                play.stage(1).clear();
                play.abandon();

                assertNotNull(records.getUnlockTime(this.flawless));
                assertEquals("1/1", status(records, this.flawless).getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void aStageAchievementHasTiers() {
            RecordsService records = service(this.steadyAim);
            try {
                Play play = new Play(records, "aim");
                play.stage(1).shoot(19, 19).clear();
                assertNull(records.getUnlockTime(this.steadyAim), "too few shots");

                play.stage(2).shoot(20, 17).clear();
                assertEquals(2, records.getUnlockedTiers(this.steadyAim), "85% reaches Silver");
                AchievementStatus status = status(records, this.steadyAim);
                assertEquals("Steady Aim (Silver)", status.getTitle());
                assertEquals("Clear a stage with 100% accuracy (20+ shots).", status.getCondition());
                assertEquals("85/100", status.getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void stageTotalsOnlyCountStagesOfCompletedRuns() {
            RecordsService records = service(this.cleanStages);
            try {
                Play first = new Play(records, "first");
                first.clearStages(2, false).clearStages(1, true);
                assertEquals(0, records.getProgress(this.cleanStages),
                        "nothing counts before the run is completed");
                first.complete(1);
                assertEquals("2/3", status(records, this.cleanStages).getProgressText(),
                        "the stage that cost a life does not count");

                Play abandoned = new Play(records, "abandoned");
                abandoned.clearStages(4, false);
                abandoned.abandon();
                assertEquals(2, records.getProgress(this.cleanStages),
                        "stages of an abandoned run never count");

                Play third = new Play(records, "third");
                third.clearStages(1, false).complete(3);
                assertEquals(Long.valueOf(3), records.getUnlockTime(this.cleanStages));
                assertEquals("Clean Stages (Bronze)", status(records, this.cleanStages).getTitle());
                assertEquals("3/6", status(records, this.cleanStages).getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void stageClearsAreSavedWithTheirRun() throws IOException {
            RecordsService records = service(this.cleanStages, this.steadyAim);
            Play play = new Play(records, "saved");
            play.stage(1).shoot(20, 15).clear();
            play.stage(2).hit().clear();
            play.complete(1);
            records.close();

            String saved = new String(Files.readAllBytes(profile()), StandardCharsets.UTF_8);
            assertTrue(saved.contains("record.0.stages=2"), saved);
            RunRecord loaded = new ProfileStore(profile()).load().getRecords().get(0);
            assertEquals(2, loaded.getStagesCleared().size());
            assertEquals(15, loaded.getStagesCleared().get(0).getHits());
            assertEquals(1, loaded.getStagesCleared().get(1).getTimesHit());

            RecordsService restarted = service(this.cleanStages, this.steadyAim);
            try {
                assertEquals(1, restarted.getProgress(this.cleanStages));
                assertEquals(75, restarted.getProgress(this.steadyAim),
                        "a stage achievement shows the best saved stage");
            } finally {
                restarted.close();
            }
        }

        @Test
        void aMalformedStageClearIsRejected() throws IOException {
            Files.write(profile(), ("version=2\nrecord.count=1\nrecord.0.id=r\n"
                    + "record.0.score=1\nrecord.0.enemies=1\nrecord.0.duration=1\n"
                    + "record.0.completed=1\nrecord.0.mode=CAMPAIGN\nrecord.0.stage=1\n"
                    + "record.0.waves=1\nrecord.0.shots=1\nrecord.0.hits=1\n"
                    + "record.0.timesHit=0\nrecord.0.bosses=0\nrecord.0.items=0\n"
                    + "record.0.stages=1\nrecord.0.stage.0=1,2,3\n"
                    + "achievement.count=0\n").getBytes(StandardCharsets.UTF_8));
            RecordsService records = service();
            try {
                assertTrue(records.isWritesBlocked());
            } finally {
                records.close();
            }
        }

        @Test
        void onlyStagesOfTheActiveRunCount() {
            RecordsService records = service(this.flawless);
            try {
                Play play = new Play(records, "old");
                records.startRun("newer");
                play.stage(1);
                assertFalse(play.clear());
                assertNull(records.getUnlockTime(this.flawless));
            } finally {
                records.close();
            }
        }

        @Test
        void stageValuesAreOnlyForStageAchievements() {
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .run(Stat.STAGES_CLEARED, 1).build());
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .total(Stat.RUNS_COMPLETED, 1).atLeast(Stat.STAGES_CLEARED, 1).build());
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .stage(Stat.SCORE, 100).build(), "score is not counted per stage");
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .stage(Stat.STAGES_CLEARED, 1).atMost(Stat.RUNS_COMPLETED, 1).build());
            assertThrows(IllegalArgumentException.class, () -> Achievement.define("a", "A", "c")
                    .stageTotal(Stat.ACCURACY_PERCENT, 80).build(), "accuracy does not add up");
        }
    }

    @Nested
    class GameExamples {

        private RecordsService game() {
            return new RecordsService(profile());
        }

        @Test
        void exterminatorHasBronzeSilverAndGoldOverEveryRun() {
            RecordsService records = game();
            try {
                records.completeRun(run("a", 1, 60, 0, 0, 1));
                records.completeRun(run("b", 2, 60, 0, 0, 1));
                assertEquals("Exterminator (Bronze)",
                        status(records, Achievement.EXTERMINATOR).getTitle());
                assertEquals("120/500",
                        status(records, Achievement.EXTERMINATOR).getProgressText());
                records.completeRun(run("c", 3, 900, 0, 0, 1));
                assertEquals(3, records.getUnlockedTiers(Achievement.EXTERMINATOR));
            } finally {
                records.close();
            }
        }

        @Test
        void sharpshooterNeedsFiftyPercentWithFiftyShots() {
            RecordsService records = game();
            try {
                records.completeRun(run("few-shots", 1, 1, 49, 49, 1));
                records.completeRun(run("49-percent", 2, 1, 100, 49, 1));
                assertNull(records.getUnlockTime(Achievement.SHARPSHOOTER));
                assertEquals("49/50",
                        status(records, Achievement.SHARPSHOOTER).getProgressText());

                records.completeRun(run("50-percent", 3, 1, 50, 25, 1));
                assertEquals(Long.valueOf(3), records.getUnlockTime(Achievement.SHARPSHOOTER));
            } finally {
                records.close();
            }
        }

        @Test
        void cleanSweepCountsStagesClearedWithoutLosingALife() {
            RecordsService records = game();
            try {
                Stages.Play play = new Stages.Play(records, "sweep");
                play.clearStages(9, false).clearStages(3, true).complete(1);
                assertEquals("9/10", status(records, Achievement.CLEAN_SWEEP).getProgressText());

                new Stages.Play(records, "more").clearStages(1, false).complete(2);
                assertEquals("Clean Sweep (Bronze)",
                        status(records, Achievement.CLEAN_SWEEP).getTitle());
                assertEquals("10/25", status(records, Achievement.CLEAN_SWEEP).getProgressText());
            } finally {
                records.close();
            }
        }

        @Test
        void untouchableIsHiddenUntilAStageIsClearedWithoutLosingALife() {
            RecordsService records = game();
            try {
                Stages.Play play = new Stages.Play(records, "untouchable");
                play.stage(1).hit().clear();
                AchievementStatus hidden = status(records, Achievement.UNTOUCHABLE);
                assertEquals("???", hidden.getTitle());
                assertEquals("???", hidden.getCondition());

                play.stage(2).clear();
                AchievementStatus revealed = status(records, Achievement.UNTOUCHABLE);
                assertEquals("Untouchable", revealed.getTitle());
                assertEquals("Clear a stage without losing a life.", revealed.getCondition());
                assertNotNull(revealed.getLastUnlockTime());
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
                // A completed run saves the profile again; the old unlocks
                // keep their times and are not notified again.
                records.completeRun(run("new", 1800000000000L, 30, 0, 0, 1));
                for (AchievementUnlock unlock : records.getCurrentRunUnlocks())
                    assertFalse(Arrays.asList(LEGACY_IDS).contains(
                            unlock.getAchievement().getId()), unlock.toString());
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
            RecordsService records = service(KILLS);
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
