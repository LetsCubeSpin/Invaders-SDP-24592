package records;
import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
public final class RecordsSmokeTest {
    private static int checks;
    private interface CheckedAction { void run(); }
    public static void main(final String[] args) throws Exception {
        if (args.length==2 && "--shutdown-probe".equals(args[0])) { runShutdownProbe(Paths.get(args[1])); return; }
        testRunRecordValidation();
        testRepositoryDeduplication();
        testRunSessionTimingAndFinish();
        testFormatterOutput();
        testPersonalBests();
        testRankingOrderAndLimit();
        testCorruptProfileRecovery();
        testProfilePersistence();
        testShutdownHookFlush();
        testAchievementsAndPersistence();
        testWriteFailureAndLifecycle();
        testCompletionThresholdBoundary();
        testLatestMainDefeatAdapter();
        testStageClearAdapter();
        testRecordsPageNavigationAndRender();
        testLatestMainMenuOrder();
        testRunRecordModeAndStats();
        testRunSessionStatistics();
        testProfileVersionTwoRoundTrip();
        testInvalidVersionTwoProfiles();
        testRunDetailLines();
        System.out.println("RecordsSmokeTest passed: "+checks+" checks.");
    }
private static void testRunRecordValidation() {
		RunRecord record = record("run-a", -100, 4, 2500, 123456);
		assertEquals("run-a", record.getRunId(), "run id");
		assertEquals(-100, record.getScore(), "score can follow game state");
		assertEquals(4, record.getEnemiesDefeated(), "enemies defeated");
		assertEquals(2500L, record.getActiveDurationMillis(),
				"active duration");
		assertEquals(123456L, record.getCompletionTimeMillis(),
				"completion time");

		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				record(null, 0, 0, 0, 1);
			}
		}, "null run id");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				record("  ", 0, 0, 0, 1);
			}
		}, "blank run id");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				record("bad-enemies", 0, -1, 0, 1);
			}
		}, "negative enemies");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				record("bad-duration", 0, 0, -1, 1);
			}
		}, "negative duration");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				record("bad-time", 0, 0, 0, 0);
			}
		}, "non-positive completion time");
	}

private static void testRepositoryDeduplication() {
		final RunRecordRepository repository =
				new InMemoryRunRecordRepository();
		RunRecord first = record("run-a", 100, 3, 2000, 10);
		RunRecord duplicate = record("run-a", 999, 9, 9000, 99);
		RunRecord sameScore = record("run-b", 100, 3, 2000, 20);

		assertEquals(0, repository.size(), "empty repository");
		assertTrue(repository.add(first), "first record is added");
		assertFalse(repository.add(duplicate), "duplicate run id is ignored");
		assertTrue(repository.add(sameScore),
				"same score with a new run id is added");
		assertEquals(2, repository.size(), "only unique runs are stored");

		final List<RunRecord> snapshot = repository.getAll();
		assertEquals(2, snapshot.size(), "snapshot size");
		assertSame(first, snapshot.get(0), "insertion order is retained");
		expectException(UnsupportedOperationException.class,
				new CheckedAction() {
					public void run() {
						snapshot.clear();
					}
				}, "snapshot cannot be modified");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				repository.add(null);
			}
		}, "null record");
	}

private static void testRunSessionTimingAndFinish() {
		RunSession session = new RunSession("run-timed", RunMode.CAMPAIGN);
		session.startActiveSegment(1000000000L);
		assertEquals(1000L, session.getActiveDurationMillis(2000000000L),
				"live active segment is visible to achievements");
		session.startActiveSegment(1500000000L);
		session.stopActiveSegment(3000000000L);
		session.stopActiveSegment(4000000000L);
		session.startActiveSegment(5000000000L);
		session.stopActiveSegment(8000000000L);

		RunRecord completed = session.finish(750, 12, 50000);
		assertEquals("run-timed", completed.getRunId(), "session run id");
		assertEquals(5000L, completed.getActiveDurationMillis(),
				"only active segments are accumulated");
		assertEquals(750, completed.getScore(), "session score");
		assertEquals(12, completed.getEnemiesDefeated(),
				"session enemies defeated");

		RunRecord duplicateFinish = session.finish(999, 99, 99999);
		assertSame(completed, duplicateFinish,
				"repeated finish returns the original record");
		assertEquals(750, duplicateFinish.getScore(),
				"repeated finish does not replace values");
		session.startActiveSegment(9000000000L);
		session.stopActiveSegment(12000000000L);
		assertEquals(5000L, session.finish(0, 0, 1)
				.getActiveDurationMillis(), "finished sessions no longer change");

		final RunSession activeSession = new RunSession("run-active", RunMode.CAMPAIGN);
		activeSession.startActiveSegment(10);
		expectException(IllegalStateException.class, new CheckedAction() {
			public void run() {
				activeSession.finish(0, 0, 1);
			}
		}, "active session must stop before finish");
		activeSession.stopActiveSegment(20);
		assertEquals(0L, activeSession.finish(0, 0, 1)
				.getActiveDurationMillis(), "sub-millisecond time rounds down");

		final RunSession invalidClock = new RunSession("run-clock", RunMode.CAMPAIGN);
		invalidClock.startActiveSegment(20);
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				invalidClock.stopActiveSegment(19);
			}
		}, "clock cannot move backwards");
		invalidClock.stopActiveSegment(30);

		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunSession("", RunMode.CAMPAIGN);
			}
		}, "session run id is required");
	}

private static void testFormatterOutput() {
		RunRecord record = record("run-output", 800, 15, 12000,
				987654321);
		String output = RunRecordFormatter.format(record);
		assertContains(output, "Run ID: run-output", "formatted run id");
		assertContains(output, "Score: 800", "formatted score");
		assertContains(output, "Enemies defeated: 15",
				"formatted enemies");
		assertContains(output, "Active duration: 12000 ms (12.000 s)",
				"formatted duration");
		assertContains(output, "Completion time: 987654321",
				"formatted completion time");
		assertContains(output, "Mode: Campaign", "formatted mode");
		String detailed = RunRecordFormatter.format(new RunRecord("run-detail",
				RunMode.STORY, 800, 15, 12000, 987654321,
				new RunStats(3, 7, 40, 10, 2, 1, 4)));
		assertContains(detailed, "Mode: Story", "formatted mode");
		assertContains(detailed, "Stage reached: 3", "formatted stage");
		assertContains(detailed, "Waves reached: 7", "formatted waves");
		assertContains(detailed, "Shots fired: 40", "formatted shots");
		assertContains(detailed, "Hits: 10", "formatted hits");
		assertContains(detailed, "Accuracy: 25.0%", "formatted accuracy");
		assertContains(detailed, "Times hit: 2", "formatted times hit");
		assertContains(detailed, "Bosses defeated: 1", "formatted bosses");
		assertContains(detailed, "Items used: 4", "formatted items");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				RunRecordFormatter.format(null);
			}
		}, "formatter rejects null");
	}

private static void testPersonalBests() throws Exception {
    RecordsService service = new RecordsService(testDirectory("personal-bests").resolve("profile.properties"));
    assertEquals(0, service.getHighestScore(), "empty score best");
    service.completeRun(record("a", -50, 4, 9000, 1));
    service.completeRun(record("b", -60, 7, 8000, 2));
    service.completeRun(record("c", -50, 7, 9000, 3));
    assertEquals(-50, service.getHighestScore(), "score best and ties");
    assertEquals(7, service.getMostEnemiesDefeated(), "enemy best and ties");
    assertEquals(9000L, service.getLongestSurvivalMillis(), "duration best and ties");
    service.close();
}
private static void testRankingOrderAndLimit() {
		try {
			Path profile = testDirectory("invaders-ranking-test")
					.resolve("profile.properties");
			RecordsService service = new RecordsService(profile);
			service.completeRun(record("z", 100, 0, 0, 20));
			service.completeRun(record("a", 100, 0, 0, 10));
			service.completeRun(record("b", 100, 0, 0, 10));
			for (int i = 0; i < 9; i++)
				service.completeRun(record("low-" + i, i, 0, 0, 30 + i));
			List<RunRecord> top = service.getTopRuns();
			assertEquals(10, top.size(), "ranking is capped at ten entries");
			assertEquals("a", top.get(0).getRunId(), "earlier tie sorts first");
			assertEquals("b", top.get(1).getRunId(), "run ID breaks timestamp tie");
			assertEquals("z", top.get(2).getRunId(), "later completion follows");
			service.close();
		} catch (IOException exception) {
			throw new AssertionError("ranking test failed", exception);
		}
	}

private static void testCorruptProfileRecovery() {
		try {
			Path directory = testDirectory("invaders-recovery-test");
			Path profile = directory.resolve("profile.properties");
			ProfileStore store = new ProfileStore(profile);
			List<RunRecord> records = new ArrayList<RunRecord>();
			records.add(record("recover-me", 42, 1, 2000, 77));
			ProfileSnapshot first = new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>());
			store.write(first);
			records.add(record("newer", 43, 1, 2000, 78));
			store.write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			Files.write(profile, "broken profile".getBytes(StandardCharsets.UTF_8));
			assertEquals(1, store.load().getRecords().size(),
					"valid backup is restored after corruption");
			assertTrue(store.getLoadWarning() != null,
					"backup recovery reports possible lost newer progress");
			try (java.nio.file.DirectoryStream<Path> preserved =
					Files.newDirectoryStream(directory, "profile.properties.corrupt-*")) {
				assertTrue(preserved.iterator().hasNext(),
						"a separate corrupt copy is preserved after recovery");
			}

			Path unrecoverable = directory.resolve("unrecoverable.properties");
			byte[] corrupt = "keep these bytes".getBytes(StandardCharsets.UTF_8);
			Files.write(unrecoverable, corrupt);
			RecordsService blocked = new RecordsService(unrecoverable);
			assertTrue(blocked.isWritesBlocked(),
					"invalid data without a backup blocks automatic writes");
			blocked.completeRun(record("ephemeral", 9, 0, 0, 100));
			blocked.close();
			assertTrue(java.util.Arrays.equals(corrupt,
					Files.readAllBytes(unrecoverable)),
					"unrecoverable source bytes remain untouched");
		} catch (IOException exception) {
			throw new AssertionError("profile recovery test failed", exception);
		}
	}

private static void testProfilePersistence() throws Exception {
    Path profile = testDirectory("profile-persistence").resolve("profile.properties");
    RecordsService service = new RecordsService(profile);
    service.completeRun(record("persist", 100, 3, 12000, 1));
    service.close(); service.close();
    assertTrue(service.getSaveError() == null, "profile writes successfully");
    RecordsService restored = new RecordsService(profile);
    assertEquals(1, restored.getRecordCount(), "records restore");
    assertEquals(100, restored.getHighestScore(), "best restores from records");
    restored.close();
    Path protectedProfile = testDirectory("protected-primary").resolve("profile.properties");
    new ProfileStore(protectedProfile).write(new ProfileSnapshot(
            java.util.Arrays.asList(record("original",100,0,0,1)), new LinkedHashMap<String,Long>()));
    byte[] original = Files.readAllBytes(protectedProfile);
    Path backup = protectedProfile.resolveSibling("profile.properties.bak");
    Files.createDirectory(backup);
    Files.write(backup.resolve("keep"), new byte[] {42});
    RecordsService blocked = new RecordsService(protectedProfile);
    blocked.completeRun(record("blocked", 200, 4, 20000, 2));
    blocked.close();
    assertTrue(blocked.getSaveError() != null, "backup failure is reported");
    assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(protectedProfile)), "backup failure preserves primary");
    assertFalse(blocked.completeRun(record("late", 0, 0, 0, 3)), "closed service rejects completions");
}
private static void testShutdownHookFlush() throws Exception {
		Path profile = testDirectory("shutdown-probe").resolve("profile.properties")
				.toAbsolutePath();
		String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
		String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
		List<String> command = new ArrayList<>();
		command.add(javaExecutable);
		String coverageAgent = System.getProperty("invaders.test.coverageAgent");
		if (coverageAgent != null && !coverageAgent.isBlank()) {
			command.add(coverageAgent);
		}
		java.util.Collections.addAll(command, "-Djava.awt.headless=true", "-cp",
				System.getProperty("java.class.path"), RecordsSmokeTest.class.getName(),
				"--shutdown-probe", profile.toString());
		Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
			child.destroyForcibly();
			throw new AssertionError("shutdown probe timed out");
		}
		assertEquals(0, child.exitValue(), "shutdown probe exits normally");
		RecordsService restored = new RecordsService(profile);
		try {
			assertEquals(80, restored.getRecordCount(),
					"production shutdown hook drains all pending record writes");
			assertEquals(79, restored.getHighestScore(),
					"the final queued snapshot survives process restart");
		} finally {
			restored.close();
		}
	}

private static void runShutdownProbe(final Path profile) throws Exception {
		RecordsService service = new RecordsService(profile);
		java.lang.reflect.Method register = engine.Core.class.getDeclaredMethod(
				"registerRecordsShutdownHook", RecordsService.class);
		register.setAccessible(true);
		register.invoke(null, service);
		for (int i = 0; i < 80; i++)
			service.completeRun(record("shutdown-" + i, i, 0, 0, i + 1));
		// Intentionally omit close(): the production hook must flush this queue.
		System.exit(0);
	}

private static void testAchievementsAndPersistence() {
		try {
			Path directory = testDirectory("invaders-profile-test");
			Path profile = directory.resolve("profile.properties");
			RecordsService service = new RecordsService(profile);
			service.startRun("first");
			assertTrue(service.enemyDefeated("first", "enemy-1"),
					"first defeat is accepted");
			assertFalse(service.enemyDefeated("first", "enemy-1"),
					"duplicate defeat event is ignored");
			for (int i = 2; i < 20; i++)
				service.enemyDefeated("first", "enemy-" + i);
			assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES) == null,
					"nineteen defeats do not unlock the twenty-defeat achievement");
			service.enemyDefeated("first", "enemy-20");
			assertTrue(service.getUnlockTime(Achievement.FIRST_ENEMY) != null,
					"first enemy achievement unlocks");
			assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES) != null,
					"twentieth enemy achievement unlocks");
			service.activeDuration("first", 59999L);
			assertTrue(service.getUnlockTime(Achievement.SIXTY_SECONDS) == null,
					"duration achievement waits for the threshold");
			service.activeDuration("first", 60000L);
			assertTrue(service.getUnlockTime(Achievement.SIXTY_SECONDS) != null,
					"duration achievement unlocks at the threshold");
			assertTrue(service.completeRun(record("first", 500, 20,
					60000L, 1000L)), "completed run is recorded");
			assertTrue(service.getUnlockTime(Achievement.FIRST_RUN) != null,
					"first completed run unlocks first-run achievement");
			assertEquals(4, service.getCurrentRunUnlocks().size(),
					"new unlocks are kept for the run summary");
			assertFalse(service.enemyDefeated("first", "late-defeat"),
					"finished runs reject late defeat events");
			assertFalse(service.completeRun(record("first", 900, 30,
					90000L, 2000L)), "duplicate completed run is ignored");
			service.startRun("same-score");
			assertTrue(service.pollNotification() != null,
					"unshown notices remain queued when a new run starts");
			service.completeRun(record("same-score", 500, 0, 1000,
					3000L));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) == null,
					"equal score does not beat the previous best");
			service.startRun("new-best");
			service.completeRun(record("new-best", 501, 0, 1000,
					4000L));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"strictly higher score unlocks the achievement");
			service.close();
			assertTrue(Files.exists(profile), "profile file was written: "
					+ service.getSaveError());

			RecordsService restored = new RecordsService(profile);
			assertEquals(3, restored.getRecordCount(),
					"records restore after restart: " + restored.getSaveError()
					+ " profile=" + profile + " contents="
					+ (Files.exists(profile) ? new String(Files.readAllBytes(profile),
							StandardCharsets.UTF_8) : "<missing>"));
			assertTrue(restored.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"achievement unlock restores after restart");
			assertEquals(501, restored.getHighestScore(),
					"personal best restores from records");
			restored.close();

			RecordsService negative = new RecordsService(
					testDirectory("invaders-negative-score").resolve("profile.properties"));
			negative.completeRun(record("negative", -50, 0, 0, 101));
			assertEquals(-50, negative.getHighestScore(),
					"personal best preserves a negative recorded score");
			negative.close();
		} catch (IOException exception) {
			throw new AssertionError("profile persistence test failed", exception);
		}
	}

private static void testWriteFailureAndLifecycle() {
		try {
			Path directory = testDirectory("invaders-write-failure");
			Path profile = directory.resolve("profile.properties");
			List<RunRecord> records = new ArrayList<RunRecord>();
			records.add(record("minimum", Integer.MIN_VALUE, 0, 0, 1));
			new ProfileStore(profile).write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			RecordsService service = new RecordsService(profile);
			service.startRun("greater");
			service.completeRun(record("greater", Integer.MIN_VALUE + 1,
					0, 0, 2));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"minimum integer score remains a valid previous best");
			service.close();
			service.close();
			assertTrue(service.getSaveError() == null, "repeated close flushes safely");
			assertFalse(service.completeRun(record("after-close", 0, 0, 0, 3)),
					"closed service rejects new completion events");
			assertFalse(service.enemyDefeated("greater", "after-close"),
					"closed service rejects defeat events");

			Path protectedProfile = directory.resolve("protected.properties");
			new ProfileStore(protectedProfile).write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			byte[] original = Files.readAllBytes(protectedProfile);
			Path blockedBackup = protectedProfile.resolveSibling("protected.properties.bak");
			Files.createDirectory(blockedBackup);
			Files.write(blockedBackup.resolve("keep.txt"), new byte[] { 42 });
			RecordsService failing = new RecordsService(protectedProfile);
			failing.completeRun(record("cannot-save", 10, 0, 0, 4));
			failing.close();
			assertTrue(failing.getSaveError() != null,
					"backup replacement failure is reported");
			assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(protectedProfile)),
					"failed backup replacement leaves the valid primary untouched");
			assertTrue(Files.exists(blockedBackup.resolve("keep.txt")),
					"failed backup replacement preserves the existing backup path");
			assertEquals(1, new ProfileStore(protectedProfile).load().getRecords().size(),
					"a failed save leaves a readable previous profile");
		} catch (IOException exception) {
			throw new AssertionError("write failure and lifecycle test failed", exception);
		}
	}

private static void testCompletionThresholdBoundary() {
		try {
			RecordsService service = new RecordsService(testDirectory("completion-boundary")
					.resolve("profile.properties"));
			service.startRun("last-frame");
			service.activeDuration("last-frame", 59999);
			service.completeRun(record("last-frame", 50, 20, 60000, 1));
			assertTrue(service.getUnlockTime(Achievement.SIXTY_SECONDS) != null,
					"last-frame duration threshold is checked at completion");
			assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES) != null,
					"completed defeat total provides the final threshold check");
			assertEquals(4, service.getCurrentRunUnlocks().size(),
					"completion unlocks are included once in the summary");
			service.close();
		} catch (IOException exception) {
			throw new AssertionError("completion boundary test failed", exception);
		}
	}

private static void testLatestMainDefeatAdapter() throws Exception {
    RecordsService service = new RecordsService(testDirectory("event-adapter").resolve("profile.properties"));
    java.lang.reflect.Field global = engine.Core.class.getDeclaredField("recordsService");
    global.setAccessible(true); Object previous = global.get(null); global.set(null,service);
    try {
        service.startRun("adapter");
        screen.GameScreen game = new screen.GameScreen(new engine.GameState(1,0,3,0,0),
                new engine.GameSettings(2,2,40,1000),false,432,490,60,new RunSession("adapter", RunMode.CAMPAIGN));
        java.lang.reflect.Method defeated = screen.GameScreen.class.getDeclaredMethod("recordEnemyDefeat",entity.EnemyShip.class);
        defeated.setAccessible(true);
        entity.EnemyShip first = new entity.EnemyShip();
        defeated.invoke(game,first); defeated.invoke(game,first);
        for (int i=0;i<18;i++) defeated.invoke(game,new entity.EnemyShip());
        assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES)==null,"duplicate source entity not double-counted");
        defeated.invoke(game,new entity.EnemyShip());
        assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES)!=null,"shared publisher forwards twentieth distinct defeat");
    } finally { global.set(null,previous); service.close(); }
}
private static void testStageClearAdapter() throws Exception {
    java.lang.reflect.Field global = engine.Core.class.getDeclaredField("recordsService");
    global.setAccessible(true); Object previous = global.get(null);
    java.lang.reflect.Field bus = screen.GameScreen.class.getDeclaredField("recordsEvents");
    bus.setAccessible(true);
    try {
        for (boolean hit : new boolean[] { true, false }) {
            RecordsService service = new RecordsService(testDirectory("stage-clear-" + hit)
                    .resolve("profile.properties"));
            global.set(null, service);
            try {
                service.startRun("stage-run");
                RunSession session = new RunSession("stage-run", RunMode.CAMPAIGN);
                screen.GameScreen game = new screen.GameScreen(new engine.GameState(1,0,3,0,0),
                        new engine.GameSettings(2,2,40,1000),false,432,490,60,session);
                session.stageStarted(1);
                event.GameplayEventPublisher events = (event.GameplayEventPublisher) bus.get(game);
                if (hit)
                    events.publish(new event.PlayerHitEvent("stage-run", "hit-1",
                            event.PlayerHitSource.ENEMY_BULLET, 1, 2));
                events.publish(new event.LevelCompletedEvent(1, engine.GameplayConfig.EASY));
                assertTrue(hit == (service.getUnlockTime(Achievement.UNTOUCHABLE) == null),
                        "a stage clear on the screen's bus checks stage achievements, hit=" + hit);
            } finally {
                service.close();
            }
        }
    } finally { global.set(null, previous); }
}
private static void testRecordsPageNavigationAndRender() {
		RecordsService service = null;
		try {
			Path directory = Paths.get("out", "records-tests", "render");
			Files.createDirectories(directory);
			service = new RecordsService(testDirectory("render-profile").resolve("profile.properties"));
			service.startRun("render-0");
			for (int i = 0; i < 20; i++)
				service.enemyDefeated("render-0", "enemy-" + i);
			service.activeDuration("render-0", 60000);
			for (int i = 0; i < 10; i++)
				service.completeRun(new RunRecord("render-" + i, RunMode.INFINITE,
						10000 + i * 100, 20 + i, 60000 + i * 1000, 1791850000000L + i,
						RunStats.EMPTY));
			service.completeRun(new RunRecord("render-story", RunMode.STORY, 30000, 64, 312000,
					1791850000100L, new RunStats(7, 18, 1240, 731, 4, 3, 9)));
			service.completeRun(new RunRecord("render-infinite", RunMode.INFINITE, 20000, 80, 240000,
					1791850000101L, new RunStats(0, 0, 400, 96, 3, 0, 0)));
			engine.InputManager input = engine.Core.getInputManager();
			java.awt.Canvas source = new java.awt.Canvas();
			for (int key : new int[] { java.awt.event.KeyEvent.VK_ESCAPE,
					java.awt.event.KeyEvent.VK_SPACE }) {
				screen.PlayerRecordsScreen page = new screen.PlayerRecordsScreen(432, 490, 60, service);
				input.keyPressed(new java.awt.event.KeyEvent(source,
						java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, key, '\u0000'));
				try {
					assertEquals(1, page.run(), "records page returns to menu for key " + key);
				} finally {
					input.keyReleased(new java.awt.event.KeyEvent(source,
							java.awt.event.KeyEvent.KEY_RELEASED, 0, 0, key, '\u0000'));
				}
			}
			engine.DrawManager draw = engine.Core.getDrawManager();
			screen.Screen page = new screen.Screen(432, 490, 60);
			java.lang.reflect.Field buffer = engine.DrawManager.class.getDeclaredField("backBuffer");
			buffer.setAccessible(true);
			String[] tabImages = { "records.png", "achievements.png", "stats.png" };
			for (int tab = 0; tab < tabImages.length; tab++)
				renderRecordsPage(draw, page, service, tab, RunOrder.SCORE, RunFilter.ALL,
						0, 0, directory.resolve(tabImages[tab]));
			// Rank 2 is the Infinite run and rank 3 a version 1 run.
			renderRecordsPage(draw, page, service, screen.PlayerRecordsScreen.TAB_RECORDS,
					RunOrder.SCORE, RunFilter.ALL, 1, 0, directory.resolve("records-infinite.png"));
			renderRecordsPage(draw, page, service, screen.PlayerRecordsScreen.TAB_RECORDS,
					RunOrder.SCORE, RunFilter.ALL, 2, 0, directory.resolve("records-v1.png"));
			renderRecordsPage(draw, page, service, screen.PlayerRecordsScreen.TAB_RECORDS,
					RunOrder.NEWEST, RunFilter.ALL, 11, 99, directory.resolve("records-newest-last.png"));
			renderRecordsPage(draw, page, service, screen.PlayerRecordsScreen.TAB_RECORDS,
					RunOrder.SCORE, RunFilter.STORY, 0, 0, directory.resolve("records-story.png"));
			renderRecordsPage(draw, page, service, screen.PlayerRecordsScreen.TAB_RECORDS,
					RunOrder.SCORE, RunFilter.ALL, 99, -5, null);

			screen.PlayerRecordsScreen browser = new screen.PlayerRecordsScreen(432, 490, 60, service);
			skipInputDelay(browser);
			assertEquals(java.awt.event.KeyEvent.VK_J, screen.PlayerRecordsScreen.SORT_KEY, "J switches the order");
			assertEquals(java.awt.event.KeyEvent.VK_K, screen.PlayerRecordsScreen.FILTER_KEY, "K switches the filter");
			int right = java.awt.event.KeyEvent.VK_RIGHT;
			int left = java.awt.event.KeyEvent.VK_LEFT;
			int up = java.awt.event.KeyEvent.VK_UP;
			int down = java.awt.event.KeyEvent.VK_DOWN;
			int sort = screen.PlayerRecordsScreen.SORT_KEY;
			int filter = screen.PlayerRecordsScreen.FILTER_KEY;
			pressOnRecordsPage(browser, right);
			assertEquals(screen.PlayerRecordsScreen.TAB_ACHIEVEMENTS, recordsPageInt(browser, "tab"), "Right opens achievements");
			pressOnRecordsPage(browser, right);
			assertEquals(screen.PlayerRecordsScreen.TAB_STATS, recordsPageInt(browser, "tab"), "Right then opens stats");
			pressOnRecordsPage(browser, right);
			assertEquals(screen.PlayerRecordsScreen.TAB_RECORDS, recordsPageInt(browser, "tab"), "tabs wrap around to the right");
			pressOnRecordsPage(browser, left);
			assertEquals(screen.PlayerRecordsScreen.TAB_STATS, recordsPageInt(browser, "tab"), "tabs wrap around to the left");
			pressOnRecordsPage(browser, sort);
			pressOnRecordsPage(browser, down);
			assertSame(RunOrder.SCORE, recordsPageField(browser, "order"), "J does nothing outside the records tab");
			assertEquals(0, recordsPageInt(browser, "selectedRun"), "Down selects no run outside the records tab");
			pressOnRecordsPage(browser, right);
			pressOnRecordsPage(browser, up);
			assertEquals(0, recordsPageInt(browser, "selectedRun"), "Up at the first run stays there");
			int perPage = draw.getRunsPerPage(browser);
			assertTrue(perPage >= 4 && perPage < 12, "twelve runs do not fit, a few do: " + perPage);
			for (int i = 0; i < 20; i++)
				pressOnRecordsPage(browser, down);
			assertEquals(11, recordsPageInt(browser, "selectedRun"), "Down stops at the last of every run, not the top ten");
			assertEquals(12 - perPage, recordsPageInt(browser, "firstRun"), "the list scrolls to show the last run");
			for (int i = 0; i < 11; i++)
				pressOnRecordsPage(browser, up);
			assertEquals(0, recordsPageInt(browser, "firstRun"), "the list scrolls back to the first run");
			pressOnRecordsPage(browser, down);
			pressOnRecordsPage(browser, sort);
			assertSame(RunOrder.NEWEST, recordsPageField(browser, "order"), "J switches to newest first");
			assertEquals(0, recordsPageInt(browser, "selectedRun"), "J selects the first run");
			assertEquals("render-infinite", service.getRuns(RunOrder.NEWEST, RunFilter.ALL).get(0).getRunId(),
					"newest first starts with the last completed run");
			pressOnRecordsPage(browser, sort);
			assertSame(RunOrder.SCORE, recordsPageField(browser, "order"), "J switches back to score");
			RunFilter[] filters = { RunFilter.CAMPAIGN, RunFilter.INFINITE, RunFilter.STORY, RunFilter.ALL };
			for (RunFilter expected : filters) {
				pressOnRecordsPage(browser, down);
				pressOnRecordsPage(browser, filter);
				assertSame(expected, recordsPageField(browser, "filter"), "K cycles the mode filter");
				assertEquals(0, recordsPageInt(browser, "selectedRun"), "K selects the first run");
			}
			pressOnRecordsPage(browser, filter);
			for (int i = 0; i < 3; i++)
				pressOnRecordsPage(browser, down);
			assertEquals(0, recordsPageInt(browser, "selectedRun"), "an empty Campaign list has nothing to select");
			renderRecordsPage(draw, page, service, screen.PlayerRecordsScreen.TAB_RECORDS,
					RunOrder.SCORE, RunFilter.CAMPAIGN, 0, 0, directory.resolve("records-empty-filter.png"));

			// Every tab, order and filter draws with no runs and with one run.
			for (int runs = 0; runs <= 1; runs++) {
				RecordsService few = new RecordsService(
						testDirectory("few-runs-" + runs).resolve("profile.properties"));
				try {
					if (runs == 1)
						few.completeRun(new RunRecord("only", RunMode.CAMPAIGN, 500, 12, 45000,
								1791850000000L, new RunStats(2, 5, 90, 40, 1, 0, 1)));
					for (int tab = 0; tab < screen.PlayerRecordsScreen.TAB_COUNT; tab++)
						for (RunOrder order : RunOrder.values())
							for (RunFilter shown : RunFilter.values())
								renderRecordsPage(draw, page, few, tab, order, shown, 0, 0, null);
					renderRecordsPage(draw, page, few, screen.PlayerRecordsScreen.TAB_RECORDS,
							RunOrder.SCORE, RunFilter.ALL, 0, 0,
							directory.resolve("records-" + runs + "-runs.png"));
					renderRecordsPage(draw, page, few, screen.PlayerRecordsScreen.TAB_STATS,
							RunOrder.SCORE, RunFilter.ALL, 0, 0,
							directory.resolve("stats-" + runs + "-runs.png"));
					screen.PlayerRecordsScreen small = new screen.PlayerRecordsScreen(432, 490, 60, few);
					skipInputDelay(small);
					pressOnRecordsPage(small, up);
					pressOnRecordsPage(small, down);
					assertEquals(0, recordsPageInt(small, "selectedRun"), "with " + runs + " runs the first stays selected");
				} finally {
					few.close();
				}
			}
			// More achievements than fit: Up/Down scrolls and stops at either end.
			List<Achievement> many = new ArrayList<Achievement>();
			for (int i = 0; i < 14; i++)
				many.add(Achievement.define("many_" + i, "Achievement " + (i + 1),
						"Defeat {target} enemies in total.")
						.total(Stat.ENEMIES_DEFEATED, 10 + i, 100 + i, 1000 + i).build());
			RecordsService crowded = new RecordsService(
					testDirectory("crowded-profile").resolve("profile.properties"), many);
			try {
				crowded.completeRun(record("crowded", 1, 105, 1, 1));
				screen.PlayerRecordsScreen list = new screen.PlayerRecordsScreen(432, 490, 60, crowded);
				skipInputDelay(list);
				pressOnRecordsPage(list, right);
				int achievementsPerPage = draw.getAchievementsPerPage(page);
				assertTrue(achievementsPerPage >= 6 && achievementsPerPage < many.size(),
						"the six game achievements fit, fourteen do not: " + achievementsPerPage);
				pressOnRecordsPage(list, up);
				assertEquals(0, recordsPageInt(list, "firstAchievement"), "Up at the top stays at the top");
				for (int i = 0; i < many.size(); i++)
					pressOnRecordsPage(list, down);
				assertEquals(many.size() - achievementsPerPage, recordsPageInt(list, "firstAchievement"),
						"Down stops once the last achievement is shown");
				draw.initDrawing(page);
				draw.drawRecordsPage(page, crowded, screen.PlayerRecordsScreen.TAB_ACHIEVEMENTS,
						RunOrder.SCORE, RunFilter.ALL, 0, 0, recordsPageInt(list, "firstAchievement"));
				javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
						"png", directory.resolve("achievements-scrolled.png").toFile());
				draw.completeDrawing(page);
			} finally {
				crowded.close();
			}
			draw.initDrawing(page);
			draw.drawAchievementResults(page, 10000, 0, 20, 0.5f, true,
					new char[] { 'A', 'A', 'A' }, 0,
					firstTiers(Achievement.values()), true, 0);
			javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
					"png", directory.resolve("results.png").toFile());
			draw.completeDrawing(page);
			System.out.println("UI review images: " + directory.toAbsolutePath());
		} catch (Exception exception) {
			throw new AssertionError("records page navigation/render test failed", exception);
		} finally {
			if (service != null)
				service.close();
		}
	}

/** Draws one view of the records page, saving it as an image unless the path is null. */
private static void renderRecordsPage(final engine.DrawManager draw, final screen.Screen page,
		final RecordsService service, final int tab, final RunOrder order, final RunFilter filter,
		final int selectedRun, final int firstRun, final Path image) throws Exception {
	draw.initDrawing(page);
	draw.drawRecordsPage(page, service, tab, order, filter, selectedRun, firstRun, 0);
	if (image != null) {
		java.lang.reflect.Field buffer = engine.DrawManager.class.getDeclaredField("backBuffer");
		buffer.setAccessible(true);
		javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
				"png", image.toFile());
	}
	draw.completeDrawing(page);
}

/** Lets the records page take keys at once instead of after the opening delay. */
private static void skipInputDelay(final screen.PlayerRecordsScreen page) throws Exception {
	java.lang.reflect.Field delay = screen.Screen.class.getDeclaredField("inputDelay");
	delay.setAccessible(true);
	delay.set(page, engine.Core.getCooldown(0));
}

/** Holds a key for one frame of the records page, as the player would. */
private static void pressOnRecordsPage(final screen.PlayerRecordsScreen page, final int key) throws Exception {
	engine.InputManager input = engine.Core.getInputManager();
	java.awt.Canvas source = new java.awt.Canvas();
	java.lang.reflect.Field next = screen.PlayerRecordsScreen.class.getDeclaredField("nextInputAt");
	next.setAccessible(true);
	next.setLong(page, 0);
	java.lang.reflect.Method update = screen.PlayerRecordsScreen.class.getDeclaredMethod("update");
	update.setAccessible(true);
	input.keyPressed(new java.awt.event.KeyEvent(source,
			java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, key, '\u0000'));
	try {
		update.invoke(page);
	} finally {
		input.keyReleased(new java.awt.event.KeyEvent(source,
				java.awt.event.KeyEvent.KEY_RELEASED, 0, 0, key, '\u0000'));
	}
}

private static Object recordsPageField(final screen.PlayerRecordsScreen page, final String name) throws Exception {
	java.lang.reflect.Field field = screen.PlayerRecordsScreen.class.getDeclaredField(name);
	field.setAccessible(true);
	return field.get(page);
}

private static int recordsPageInt(final screen.PlayerRecordsScreen page, final String name) throws Exception {
	return ((Integer) recordsPageField(page, name)).intValue();
}

private static List<AchievementUnlock> firstTiers(final List<Achievement> achievements) {
    List<AchievementUnlock> unlocks = new ArrayList<AchievementUnlock>();
    for (Achievement achievement : achievements)
        unlocks.add(new AchievementUnlock(achievement, 1));
    return unlocks;
}
private static void testLatestMainMenuOrder() throws Exception {
    screen.TitleScreen menu = new screen.TitleScreen(432,490,60);
    java.lang.reflect.Field code = screen.Screen.class.getDeclaredField("returnCode"); code.setAccessible(true);
    java.lang.reflect.Method next = screen.TitleScreen.class.getDeclaredMethod("nextMenuItem"); next.setAccessible(true);
    java.lang.reflect.Method previous = screen.TitleScreen.class.getDeclaredMethod("previousMenuItem"); previous.setAccessible(true);
    for (int expected : new int[]{3,9,4,0,2}) { next.invoke(menu); assertEquals(expected,code.getInt(menu),"forward menu includes High scores, Records, and Settings"); }
    for (int expected : new int[]{0,4,9,3,2}) { previous.invoke(menu); assertEquals(expected,code.getInt(menu),"reverse menu includes High scores, Records, and Settings"); }
}
private static void testRunRecordModeAndStats() {
    RunStats stats = new RunStats(4, 9, 50, 20, 3, 1, 2);
    RunRecord full = new RunRecord("full", RunMode.CAMPAIGN, 10, 20, 300, 4, stats);
    assertSame(RunMode.CAMPAIGN, full.getMode(), "mode is kept");
    assertSame(stats, full.getStats(), "stats are kept");
    assertTrue(Math.abs(stats.getAccuracy() - 0.4) < 1e-9, "accuracy is hits over shots");
    assertTrue(new RunStats(0, 0, 0, 5, 0, 0, 0).getAccuracy() == 0, "no shots means no accuracy");
    assertTrue(new RunStats(0, 0, 2, 5, 0, 0, 0).getAccuracy() == 1, "accuracy never passes 1");
    assertFalse(RunMode.INFINITE.hasStages(), "infinite has no stages");
    assertTrue(RunMode.CAMPAIGN.hasStages() && RunMode.STORY.hasStages(), "campaign and story have stages");
    expectException(IllegalArgumentException.class, new CheckedAction() {
        public void run() { new RunRecord("x", null, 0, 0, 0, 1, RunStats.EMPTY); }
    }, "mode is required");
    expectException(IllegalArgumentException.class, new CheckedAction() {
        public void run() { new RunRecord("x", RunMode.STORY, 0, 0, 0, 1, null); }
    }, "stats are required");
    final int[] valid = {1, 1, 1, 1, 1, 1, 1};
    for (int i = 0; i < valid.length; i++) {
        final int[] values = valid.clone();
        values[i] = -1;
        expectException(IllegalArgumentException.class, new CheckedAction() {
            public void run() { new RunStats(values[0], values[1], values[2], values[3], values[4], values[5], values[6]); }
        }, "negative statistic " + i);
    }
}
private static void testRunSessionStatistics() {
    RunSession session = new RunSession("stats", RunMode.CAMPAIGN);
    assertSame(RunMode.CAMPAIGN, session.getMode(), "session mode");
    event.GameplayEventPublisher first = new event.GameplayEventPublisher();
    session.listenTo(first);
    session.stageStarted(1); session.waveStarted(); session.waveStarted();
    first.publish(new event.ShotFiredEvent("stats", "shot-1", 3));
    first.publish(new event.ShotFiredEvent("stats", "shot-1", 3));
    first.publish(new event.ShotFiredEvent("stats", "shot-2", 1));
    first.publish(new event.ShotFiredEvent("other-run", "shot-3", 5));
    first.publish(new event.BulletHitEvent("stats", "bullet-hit-1"));
    first.publish(new event.BulletHitEvent("stats", "bullet-hit-1"));
    first.publish(new event.BulletHitEvent("stats", "bullet-hit-2"));
    first.publish(new event.BulletHitEvent("other-run", "bullet-hit-3"));
    first.publish(new event.EnemyDefeatedEvent("stats", "defeat-is-not-a-hit"));
    first.publish(new event.BossDefeatedEvent("stats", "boss-1", event.DefeatedEnemyType.BOSS_A, 1000));
    first.publish(new event.BossDefeatedEvent("stats", "boss-1", event.DefeatedEnemyType.BOSS_A, 1000));
    first.publish(new event.PlayerHitEvent("stats", "hit-1", event.PlayerHitSource.ENEMY_BULLET, 1, 2));
    first.publish(new event.ItemUsedEvent("stats", "item-1", event.UsedItemType.CLUSTER_GRENADE));
    RunStats live = session.getStats();
    assertEquals(4, live.getShotsFired(), "bullets add up once per shot event");
    assertEquals(2, live.getHits(), "bullet hits count once each; defeats are not hits");
    assertEquals(1, live.getBossesDefeated(), "boss defeats count once");
    assertEquals(1, live.getTimesHit(), "player hits count");
    assertEquals(1, live.getItemsUsed(), "item uses count");
    assertTrue(Math.abs(session.getAccuracy() - 0.5) < 1e-9, "live accuracy for achievements");

    // The next stage is a new screen with its own bus.
    event.GameplayEventPublisher second = new event.GameplayEventPublisher();
    session.listenTo(second);
    session.stageStarted(2); session.waveStarted();
    second.publish(new event.ShotFiredEvent("stats", "shot-4", 2));
    second.publish(new event.ItemUsedEvent("stats", "item-2", event.UsedItemType.ENERGY_SHIELD));
    RunRecord record = session.finish(500, 2, 99);
    assertSame(RunMode.CAMPAIGN, record.getMode(), "record keeps the session mode");
    assertEquals(2, record.getStats().getStageReached(), "highest stage reached");
    assertEquals(3, record.getStats().getWavesReached(), "waves add up over the whole run");
    assertEquals(6, record.getStats().getShotsFired(), "shots of every screen add up");
    assertEquals(2, record.getStats().getItemsUsed(), "items of every screen add up");

    second.publish(new event.ShotFiredEvent("stats", "shot-late", 9));
    session.waveStarted(); session.stageStarted(5);
    assertEquals(6, session.getStats().getShotsFired(), "finished run ignores late events");
    assertEquals(3, session.getStats().getWavesReached(), "finished run ignores late waves");
    assertSame(record, session.finish(0, 0, 1), "finish stays idempotent");
    final RunSession invalid = new RunSession("invalid", RunMode.CAMPAIGN);
    expectException(IllegalArgumentException.class, new CheckedAction() {
        public void run() { invalid.stageStarted(0); }
    }, "stage numbers start at 1");
    expectException(IllegalArgumentException.class, new CheckedAction() {
        public void run() { invalid.listenTo(null); }
    }, "event bus is required");
    expectException(IllegalArgumentException.class, new CheckedAction() {
        public void run() { new RunSession("x", null); }
    }, "session mode is required");
}
/** @return a Campaign record without play statistics, for checks that do not use them. */
private static RunRecord record(final String runId, final int score, final int enemiesDefeated,
        final long activeDurationMillis, final long completionTimeMillis) {
    return new RunRecord(runId, RunMode.CAMPAIGN, score, enemiesDefeated, activeDurationMillis,
            completionTimeMillis, RunStats.EMPTY);
}
private static void assertSameRecord(final RunRecord expected, final RunRecord actual, final String message) {
    assertEquals(expected.getRunId(), actual.getRunId(), message + " id");
    assertSame(expected.getMode(), actual.getMode(), message + " mode");
    assertEquals(expected.getScore(), actual.getScore(), message + " score");
    assertEquals(expected.getEnemiesDefeated(), actual.getEnemiesDefeated(), message + " enemies");
    assertEquals(expected.getActiveDurationMillis(), actual.getActiveDurationMillis(), message + " duration");
    assertEquals(expected.getCompletionTimeMillis(), actual.getCompletionTimeMillis(), message + " completion");
    RunStats e = expected.getStats(), a = actual.getStats();
    assertEquals(e.getStageReached(), a.getStageReached(), message + " stage");
    assertEquals(e.getWavesReached(), a.getWavesReached(), message + " waves");
    assertEquals(e.getShotsFired(), a.getShotsFired(), message + " shots");
    assertEquals(e.getHits(), a.getHits(), message + " hits");
    assertEquals(e.getTimesHit(), a.getTimesHit(), message + " times hit");
    assertEquals(e.getBossesDefeated(), a.getBossesDefeated(), message + " bosses");
    assertEquals(e.getItemsUsed(), a.getItemsUsed(), message + " items");
}
private static String profileVersion(final Path file) throws IOException {
    java.util.Properties properties = new java.util.Properties();
    try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
    return properties.getProperty("version");
}
private static void testProfileVersionTwoRoundTrip() throws Exception {
    Path profile = testDirectory("profile-v2").resolve("profile.properties");
    RunRecord campaign = new RunRecord("v2-campaign", RunMode.CAMPAIGN, 900, 25, 61000, 5,
            new RunStats(4, 11, 120, 25, 2, 1, 3));
    RunRecord infinite = new RunRecord("v2-infinite", RunMode.INFINITE, 300, 9, 20000, 6,
            new RunStats(0, 0, 45, 9, 3, 0, 0));
    RecordsService service = new RecordsService(profile);
    service.completeRun(campaign); service.completeRun(infinite);
    service.close();
    assertTrue(service.getSaveError() == null, "v2 profile writes successfully");
    assertEquals("2", profileVersion(profile), "profiles are written as version 2");
    RecordsService restarted = new RecordsService(profile);
    assertEquals(2, restarted.getRecordCount(), "v2 records restore after restart");
    for (RunRecord restored : restarted.getTopRuns())
        assertSameRecord(restored.getRunId().equals("v2-campaign") ? campaign : infinite, restored,
                "restored " + restored.getRunId());
    restarted.close();
}
private static void testRunDetailLines() {
    List<String> story = RunRecordFormatter.detailLines(new RunRecord("d-story", RunMode.STORY, 1, 1, 1, 1,
            new RunStats(3, 7, 40, 10, 2, 1, 4)));
    assertEquals("Mode: Story|Stage reached: 3|Waves reached: 7|Shots fired: 40|Hits: 10|Accuracy: 25.0%"
            + "|Times hit: 2|Bosses defeated: 1|Items used: 4", String.join("|", story), "story run details");
    List<String> infinite = RunRecordFormatter.detailLines(new RunRecord("d-inf", RunMode.INFINITE, 1, 1, 1, 1,
            new RunStats(0, 0, 8, 2, 1, 0, 0)));
    assertEquals("Stage reached: -", infinite.get(1), "infinite has no stage");
    assertEquals("Waves reached: -", infinite.get(2), "older infinite runs have no recorded wave");
    List<String> bossWave = RunRecordFormatter.detailLines(new RunRecord("d-boss", RunMode.INFINITE, 500, 1, 1, 1,
            new RunStats(0, 6, 20, 17, 0, 1, 0)));
    assertEquals("Waves reached: 6", bossWave.get(2), "infinite shows the wave reached after the boss");
    assertEquals("Accuracy: 25.0%", infinite.get(5), "infinite accuracy");
}
private static void testInvalidVersionTwoProfiles() throws Exception {
    Path directory = testDirectory("profile-v2-invalid");
    String header = "version=2\nrecord.count=1\nrecord.0.id=r\nrecord.0.score=1\nrecord.0.enemies=1\n"
            + "record.0.duration=1\nrecord.0.completed=1\nachievement.count=0\n";
    String stats = "record.0.stage=1\nrecord.0.waves=1\nrecord.0.shots=1\nrecord.0.hits=1\n"
            + "record.0.timesHit=0\nrecord.0.bosses=0\nrecord.0.items=0\n";
    String[] invalid = {
        header + stats,
        header + "record.0.mode=ARCADE\n" + stats,
        header + "record.0.mode=STORY\n" + stats.replace("record.0.hits=1\n", ""),
        header + "record.0.mode=STORY\n" + stats.replace("record.0.shots=1", "record.0.shots=-1"),
        header.replace("version=2", "version=1") + "record.0.mode=CAMPAIGN\n" + stats,
        header.replace("version=2", "version=1").replace("record.count=1", "record.count=0"),
        header + "record.0.mode=UNKNOWN\n" + stats,
        header.replace("version=2", "version=3") + "record.0.mode=CAMPAIGN\n" + stats,
    };
    String[] reason = {"missing mode", "unknown mode", "missing statistic", "negative statistic",
            "version 1", "empty version 1", "removed unknown mode", "future version"};
    for (int i = 0; i < invalid.length; i++) {
        Path file = directory.resolve("invalid-" + i + ".properties");
        final byte[] bytes = invalid[i].getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        final ProfileStore store = new ProfileStore(file);
        expectException(java.io.UncheckedIOException.class, new CheckedAction() {
            public void run() {
                try { store.load(); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            }
        }, reason[i] + " is rejected");
        RecordsService blocked = new RecordsService(file);
        assertTrue(blocked.isWritesBlocked(), reason[i] + " blocks writes");
        blocked.close();
        assertTrue(java.util.Arrays.equals(bytes, Files.readAllBytes(file)), reason[i] + " file stays untouched");
    }
}
	private static void assertTrue(final boolean actual,
			final String message) {
		checks++;
		if (!actual)
			throw new AssertionError(message);
	}
	private static void assertFalse(final boolean actual,
			final String message) {
		assertTrue(!actual, message);
	}
	private static void assertEquals(final int expected, final int actual,
			final String message) {
		checks++;
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected
					+ " but was " + actual);
	}
	private static void assertEquals(final long expected, final long actual,
			final String message) {
		checks++;
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected
					+ " but was " + actual);
	}
	private static void assertEquals(final String expected,
			final String actual, final String message) {
		checks++;
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected
					+ " but was " + actual);
	}
	private static void assertSame(final Object expected, final Object actual,
			final String message) {
		checks++;
		if (expected != actual)
			throw new AssertionError(message);
	}
	private static void assertContains(final String actual,
			final String expectedPart, final String message) {
		checks++;
		if (!actual.contains(expectedPart))
			throw new AssertionError(message + ": missing " + expectedPart);
	}
	private static void expectException(
			final Class<? extends Throwable> expectedType,
			final CheckedAction action, final String message) {
		checks++;
		try {
			action.run();
		} catch (Throwable actual) {
			if (expectedType.isInstance(actual))
				return;
			throw new AssertionError(message + ": expected "
					+ expectedType.getSimpleName() + " but caught "
					+ actual.getClass().getSimpleName());
		}
		throw new AssertionError(message + ": expected "
				+ expectedType.getSimpleName());
	}
	private static Path testDirectory(final String prefix) throws IOException {
		Path root = Paths.get("out", "records-tests");
		Files.createDirectories(root);
		return Files.createTempDirectory(root, prefix);
	}
}
