package records;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import story.Ending;

/** Sorting and filtering every run, and the cumulative stats, for the records page. */
class RunBrowsingTest {

    @TempDir Path temp;
    private RecordsService service;

    private RecordsService service() {
        this.service = new RecordsService(this.temp.resolve("profile.properties"));
        return this.service;
    }

    @AfterEach
    void close() {
        if (this.service != null)
            this.service.close();
    }

    private static RunRecord run(final String id, final RunMode mode,
            final int score, final long completed) {
        return new RunRecord(id, mode, score, 0, 0, completed, RunStats.EMPTY);
    }

    private static List<String> ids(final List<RunRecord> runs) {
        List<String> ids = new ArrayList<String>();
        for (RunRecord run : runs)
            ids.add(run.getRunId());
        return ids;
    }

    @Test
    void noRunsGiveEmptyListsForEveryOrderAndFilter() {
        RecordsService records = service();
        for (RunOrder order : RunOrder.values())
            for (RunFilter filter : RunFilter.values())
                assertTrue(records.getRuns(order, filter).isEmpty());
    }

    @Test
    void scoreOrderPutsTheHighestFirstAndBreaksTiesByTimeThenId() {
        RecordsService records = service();
        records.completeRun(run("late", RunMode.CAMPAIGN, 100, 20));
        records.completeRun(run("b", RunMode.CAMPAIGN, 100, 10));
        records.completeRun(run("a", RunMode.CAMPAIGN, 100, 10));
        records.completeRun(run("best", RunMode.INFINITE, 500, 30));
        assertEquals(Arrays.asList("best", "a", "b", "late"),
                ids(records.getRuns(RunOrder.SCORE, RunFilter.ALL)));
    }

    @Test
    void newestOrderPutsTheLatestFirstAndBreaksTiesById() {
        RecordsService records = service();
        records.completeRun(run("old", RunMode.CAMPAIGN, 900, 1));
        records.completeRun(run("b", RunMode.CAMPAIGN, 10, 5));
        records.completeRun(run("a", RunMode.CAMPAIGN, 20, 5));
        records.completeRun(run("new", RunMode.STORY, 0, 9));
        assertEquals(Arrays.asList("new", "a", "b", "old"),
                ids(records.getRuns(RunOrder.NEWEST, RunFilter.ALL)));
    }

    @Test
    void runsAreNotCappedAtTen() {
        RecordsService records = service();
        for (int i = 0; i < 15; i++)
            records.completeRun(run("run-" + i, RunMode.CAMPAIGN, i, i + 1));
        assertEquals(15, records.getRuns(RunOrder.SCORE, RunFilter.ALL).size());
        assertEquals(10, records.getTopRuns().size(), "the top runs keep their limit");
        assertEquals("run-14", records.getTopRuns().get(0).getRunId());
    }

    @Test
    void eachModeFilterShowsOnlyItsModeAndAllShowsEveryRun() {
        RecordsService records = service();
        records.completeRun(run("campaign", RunMode.CAMPAIGN, 1, 1));
        records.completeRun(run("infinite", RunMode.INFINITE, 2, 2));
        records.completeRun(run("story", RunMode.STORY, 3, 3));
        assertEquals(Arrays.asList("story", "infinite", "campaign"),
                ids(records.getRuns(RunOrder.SCORE, RunFilter.ALL)));
        assertEquals(Arrays.asList("campaign"),
                ids(records.getRuns(RunOrder.SCORE, RunFilter.CAMPAIGN)));
        assertEquals(Arrays.asList("infinite"),
                ids(records.getRuns(RunOrder.SCORE, RunFilter.INFINITE)));
        assertEquals(Arrays.asList("story"),
                ids(records.getRuns(RunOrder.SCORE, RunFilter.STORY)));
    }

    @Test
    void aFilterWithoutMatchingRunsIsEmpty() {
        RecordsService records = service();
        records.completeRun(run("campaign", RunMode.CAMPAIGN, 1, 1));
        assertTrue(records.getRuns(RunOrder.NEWEST, RunFilter.STORY).isEmpty());
    }

    @Test
    void nextCyclesThroughEveryOrderAndFilter() {
        assertEquals(RunOrder.NEWEST, RunOrder.SCORE.next());
        assertEquals(RunOrder.SCORE, RunOrder.NEWEST.next());
        assertEquals(RunFilter.CAMPAIGN, RunFilter.ALL.next());
        assertEquals(RunFilter.INFINITE, RunFilter.CAMPAIGN.next());
        assertEquals(RunFilter.STORY, RunFilter.INFINITE.next());
        assertEquals(RunFilter.ALL, RunFilter.STORY.next());
    }

    @Test
    void nullOrderOrFilterIsRejected() {
        RecordsService records = service();
        assertThrows(IllegalArgumentException.class,
                () -> records.getRuns(null, RunFilter.ALL));
        assertThrows(IllegalArgumentException.class,
                () -> records.getRuns(RunOrder.SCORE, null));
    }

    @Test
    void cumulativeStatsWithoutRunsAreZero() {
        CumulativeStats stats = service().getCumulativeStats();
        assertEquals(0, stats.getRunsCompleted());
        assertEquals(0, stats.getEnemiesDefeated());
        assertEquals(0, stats.getActiveDurationMillis());
        assertEquals(0, stats.getShotsFired());
        assertEquals(0.0, stats.getAccuracy());
        assertEquals(0, stats.getStagesCleared());
        assertEquals(0, stats.getEndingsSeen());
        assertEquals(Ending.values().length, stats.getEndingCount());
    }

    @Test
    void cumulativeStatsAddUpEveryRun() {
        RecordsService records = service();
        StageClear stage = new StageClear(1, 10, 3, 50, 30, 0, 0, 1, 40);
        records.completeRun(new RunRecord("campaign", RunMode.CAMPAIGN, 1000, 40,
                90000, 1, new RunStats(2, 6, 200, 120, 1, 1, 2),
                Arrays.asList(stage, new StageClear(2, 30, 3, 150, 90, 1, 1, 1, 50))));
        records.completeRun(new RunRecord("infinite", RunMode.INFINITE, 500, 25,
                30500, 2, new RunStats(0, 0, 100, 30, 3, 0, 4)));
        records.endingSeen(Ending.ENDING2);

        CumulativeStats stats = records.getCumulativeStats();
        assertEquals(2, stats.getRunsCompleted());
        assertEquals(65, stats.getEnemiesDefeated());
        assertEquals(120500, stats.getActiveDurationMillis());
        assertEquals(300, stats.getShotsFired());
        assertEquals(150, stats.getHits());
        assertEquals(0.5, stats.getAccuracy(), 1e-9);
        assertEquals(4, stats.getTimesHit());
        assertEquals(1, stats.getBossesDefeated());
        assertEquals(6, stats.getItemsUsed());
        assertEquals(2, stats.getStagesCleared());
        assertEquals(1, stats.getEndingsSeen());
    }

    @Test
    void abandonedRunsAreNotCounted() {
        RecordsService records = service();
        records.startRun("abandoned");
        records.enemyDefeated("abandoned", "enemy-1");
        records.abandonRun("abandoned");
        records.completeRun(run("done", RunMode.CAMPAIGN, 1, 1));
        assertEquals(1, records.getCumulativeStats().getRunsCompleted());
        assertEquals(0, records.getCumulativeStats().getEnemiesDefeated());
    }

    @Test
    void cumulativeStatsAreRebuiltAfterARestart() {
        RecordsService records = service();
        records.completeRun(new RunRecord("campaign", RunMode.CAMPAIGN, 1000, 40,
                90000, 1, new RunStats(2, 6, 200, 120, 1, 1, 2)));
        records.completeRun(new RunRecord("infinite", RunMode.INFINITE, 700, 15,
                20000, 3, new RunStats(0, 0, 50, 10, 0, 0, 0)));
        records.close();
        CumulativeStats stats = service().getCumulativeStats();
        assertEquals(2, stats.getRunsCompleted());
        assertEquals(55, stats.getEnemiesDefeated());
        assertEquals(250, stats.getShotsFired());
    }
}
