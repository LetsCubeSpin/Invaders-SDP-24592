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
        testRecordsPageNavigationAndRender();
        testLatestMainMenuOrder();
        testRunRecordModeAndStats();
        testRunSessionStatistics();
        testProfileVersionTwoRoundTrip();
        testVersionOneProfileMigration();
        testVersionOneBackupRecovery();
        testInvalidVersionTwoProfiles();
        testRunDetailLines();
        System.out.println("RecordsSmokeTest passed: "+checks+" checks.");
    }
private static void testRunRecordValidation() {
		RunRecord record = new RunRecord("run-a", -100, 4, 2500, 123456);
		assertEquals("run-a", record.getRunId(), "run id");
		assertEquals(-100, record.getScore(), "score can follow game state");
		assertEquals(4, record.getEnemiesDefeated(), "enemies defeated");
		assertEquals(2500L, record.getActiveDurationMillis(),
				"active duration");
		assertEquals(123456L, record.getCompletionTimeMillis(),
				"completion time");

		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord(null, 0, 0, 0, 1);
			}
		}, "null run id");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("  ", 0, 0, 0, 1);
			}
		}, "blank run id");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("bad-enemies", 0, -1, 0, 1);
			}
		}, "negative enemies");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("bad-duration", 0, 0, -1, 1);
			}
		}, "negative duration");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("bad-time", 0, 0, 0, 0);
			}
		}, "non-positive completion time");
	}

private static void testRepositoryDeduplication() {
		final RunRecordRepository repository =
				new InMemoryRunRecordRepository();
		RunRecord first = new RunRecord("run-a", 100, 3, 2000, 10);
		RunRecord duplicate = new RunRecord("run-a", 999, 9, 9000, 99);
		RunRecord sameScore = new RunRecord("run-b", 100, 3, 2000, 20);

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
		RunSession session = new RunSession("run-timed");
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

		final RunSession activeSession = new RunSession("run-active");
		activeSession.startActiveSegment(10);
		expectException(IllegalStateException.class, new CheckedAction() {
			public void run() {
				activeSession.finish(0, 0, 1);
			}
		}, "active session must stop before finish");
		activeSession.stopActiveSegment(20);
		assertEquals(0L, activeSession.finish(0, 0, 1)
				.getActiveDurationMillis(), "sub-millisecond time rounds down");

		final RunSession invalidClock = new RunSession("run-clock");
		invalidClock.startActiveSegment(20);
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				invalidClock.stopActiveSegment(19);
			}
		}, "clock cannot move backwards");
		invalidClock.stopActiveSegment(30);

		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunSession("");
			}
		}, "session run id is required");
	}

private static void testFormatterOutput() {
		RunRecord record = new RunRecord("run-output", 800, 15, 12000,
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
		assertContains(output, "Mode: Unknown", "formatted unknown mode");
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
    service.completeRun(new RunRecord("a", -50, 4, 9000, 1));
    service.completeRun(new RunRecord("b", -60, 7, 8000, 2));
    service.completeRun(new RunRecord("c", -50, 7, 9000, 3));
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
			service.completeRun(new RunRecord("z", 100, 0, 0, 20));
			service.completeRun(new RunRecord("a", 100, 0, 0, 10));
			service.completeRun(new RunRecord("b", 100, 0, 0, 10));
			for (int i = 0; i < 9; i++)
				service.completeRun(new RunRecord("low-" + i, i, 0, 0, 30 + i));
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
			records.add(new RunRecord("recover-me", 42, 1, 2000, 77));
			ProfileSnapshot first = new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>());
			store.write(first);
			records.add(new RunRecord("newer", 43, 1, 2000, 78));
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
			blocked.completeRun(new RunRecord("ephemeral", 9, 0, 0, 100));
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
    service.completeRun(new RunRecord("persist", 100, 3, 12000, 1));
    service.close(); service.close();
    assertTrue(service.getSaveError() == null, "profile writes successfully");
    RecordsService restored = new RecordsService(profile);
    assertEquals(1, restored.getRecordCount(), "records restore");
    assertEquals(100, restored.getHighestScore(), "best restores from records");
    restored.close();
    Path protectedProfile = testDirectory("protected-primary").resolve("profile.properties");
    new ProfileStore(protectedProfile).write(new ProfileSnapshot(
            java.util.Arrays.asList(new RunRecord("original",100,0,0,1)), new LinkedHashMap<String,Long>()));
    byte[] original = Files.readAllBytes(protectedProfile);
    Path backup = protectedProfile.resolveSibling("profile.properties.bak");
    Files.createDirectory(backup);
    Files.write(backup.resolve("keep"), new byte[] {42});
    RecordsService blocked = new RecordsService(protectedProfile);
    blocked.completeRun(new RunRecord("blocked", 200, 4, 20000, 2));
    blocked.close();
    assertTrue(blocked.getSaveError() != null, "backup failure is reported");
    assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(protectedProfile)), "backup failure preserves primary");
    assertFalse(blocked.completeRun(new RunRecord("late", 0, 0, 0, 3)), "closed service rejects completions");
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
			service.completeRun(new RunRecord("shutdown-" + i, i, 0, 0, i + 1));
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
			assertTrue(service.completeRun(new RunRecord("first", 500, 20,
					60000L, 1000L)), "completed run is recorded");
			assertTrue(service.getUnlockTime(Achievement.FIRST_RUN) != null,
					"first completed run unlocks first-run achievement");
			assertEquals(4, service.getCurrentRunUnlocks().size(),
					"new unlocks are kept for the run summary");
			assertFalse(service.enemyDefeated("first", "late-defeat"),
					"finished runs reject late defeat events");
			assertFalse(service.completeRun(new RunRecord("first", 900, 30,
					90000L, 2000L)), "duplicate completed run is ignored");
			service.startRun("same-score");
			assertTrue(service.pollNotification() != null,
					"unshown notices remain queued when a new run starts");
			service.completeRun(new RunRecord("same-score", 500, 0, 1000,
					3000L));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) == null,
					"equal score does not beat the previous best");
			service.startRun("new-best");
			service.completeRun(new RunRecord("new-best", 501, 0, 1000,
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
			negative.completeRun(new RunRecord("negative", -50, 0, 0, 101));
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
			records.add(new RunRecord("minimum", Integer.MIN_VALUE, 0, 0, 1));
			new ProfileStore(profile).write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			RecordsService service = new RecordsService(profile);
			service.startRun("greater");
			service.completeRun(new RunRecord("greater", Integer.MIN_VALUE + 1,
					0, 0, 2));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"minimum integer score remains a valid previous best");
			service.close();
			service.close();
			assertTrue(service.getSaveError() == null, "repeated close flushes safely");
			assertFalse(service.completeRun(new RunRecord("after-close", 0, 0, 0, 3)),
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
			failing.completeRun(new RunRecord("cannot-save", 10, 0, 0, 4));
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
			service.completeRun(new RunRecord("last-frame", 50, 20, 60000, 1));
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
                new engine.GameSettings(2,2,40,1000),false,432,490,60,new RunSession("adapter"));
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
				service.completeRun(new RunRecord("render-" + i, 10000 + i * 100,
						20 + i, 60000 + i * 1000, 1791850000000L + i));
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
			String[] tabImages = { "records.png", "details-story.png", "achievements.png" };
			for (int tab = 0; tab < tabImages.length; tab++) {
				draw.initDrawing(page);
				draw.drawRecordsPage(page, service, tab);
				javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
						"png", directory.resolve(tabImages[tab]).toFile());
				draw.completeDrawing(page);
			}
			// Rank 2 is the Infinite run and rank 3 a version 1 run.
			String[] detailImages = { "details-infinite.png", "details-v1.png" };
			for (int i = 0; i < detailImages.length; i++) {
				draw.initDrawing(page);
				draw.drawRecordsPage(page, service, screen.PlayerRecordsScreen.TAB_RUN_DETAILS, i + 1);
				javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
						"png", directory.resolve(detailImages[i]).toFile());
				draw.completeDrawing(page);
			}
			draw.initDrawing(page);
			draw.drawRecordsPage(page, service, screen.PlayerRecordsScreen.TAB_RUN_DETAILS, 99);
			draw.completeDrawing(page);

			screen.PlayerRecordsScreen browser = new screen.PlayerRecordsScreen(432, 490, 60, service);
			java.lang.reflect.Method changeTab = screen.PlayerRecordsScreen.class.getDeclaredMethod("changeTab", int.class);
			java.lang.reflect.Method changeRun = screen.PlayerRecordsScreen.class.getDeclaredMethod("changeRun", int.class);
			java.lang.reflect.Field tabField = screen.PlayerRecordsScreen.class.getDeclaredField("tab");
			java.lang.reflect.Field runField = screen.PlayerRecordsScreen.class.getDeclaredField("selectedRun");
			for (java.lang.reflect.AccessibleObject member : new java.lang.reflect.AccessibleObject[] {
					changeTab, changeRun, tabField, runField })
				member.setAccessible(true);
			changeTab.invoke(browser, 1);
			assertEquals(screen.PlayerRecordsScreen.TAB_RUN_DETAILS, tabField.getInt(browser), "Right opens run details");
			changeTab.invoke(browser, 1);
			assertEquals(screen.PlayerRecordsScreen.TAB_ACHIEVEMENTS, tabField.getInt(browser), "Right then opens achievements");
			changeTab.invoke(browser, 1);
			assertEquals(screen.PlayerRecordsScreen.TAB_RUNS, tabField.getInt(browser), "tabs wrap around to the right");
			changeTab.invoke(browser, -1);
			assertEquals(screen.PlayerRecordsScreen.TAB_ACHIEVEMENTS, tabField.getInt(browser), "tabs wrap around to the left");
			changeRun.invoke(browser, -1);
			assertEquals(9, runField.getInt(browser), "Up from the best run wraps to the last of the top ten");
			changeRun.invoke(browser, 1);
			assertEquals(0, runField.getInt(browser), "Down from the last run wraps to the best");
			// More achievements than fit: Up/Down scrolls and stops at either end.
			List<Achievement> many = new ArrayList<Achievement>();
			for (int i = 0; i < 14; i++)
				many.add(new Achievement("many_" + i, "Achievement " + (i + 1),
						"Defeat {target} enemies in total.", Achievement.Scope.TOTAL,
						Stat.ENEMIES_DEFEATED, i % 4 == 3, 10 + i, 100 + i, 1000 + i));
			RecordsService crowded = new RecordsService(
					testDirectory("crowded-profile").resolve("profile.properties"), many);
			try {
				crowded.completeRun(new RunRecord("crowded", 1, 105, 1, 1));
				screen.PlayerRecordsScreen list = new screen.PlayerRecordsScreen(432, 490, 60, crowded);
				java.lang.reflect.Method scroll = screen.PlayerRecordsScreen.class
						.getDeclaredMethod("scrollAchievements", int.class);
				java.lang.reflect.Field firstField = screen.PlayerRecordsScreen.class
						.getDeclaredField("firstAchievement");
				scroll.setAccessible(true);
				firstField.setAccessible(true);
				int perPage = draw.getAchievementsPerPage(page);
				assertTrue(perPage >= 6 && perPage < many.size(),
						"the six game achievements fit, fourteen do not: " + perPage);
				scroll.invoke(list, -1);
				assertEquals(0, firstField.getInt(list), "Up at the top stays at the top");
				for (int i = 0; i < many.size(); i++)
					scroll.invoke(list, 1);
				assertEquals(many.size() - perPage, firstField.getInt(list),
						"Down stops once the last achievement is shown");
				draw.initDrawing(page);
				draw.drawRecordsPage(page, crowded, screen.PlayerRecordsScreen.TAB_ACHIEVEMENTS,
						0, firstField.getInt(list));
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
    RunRecord legacy = new RunRecord("legacy", 10, 2, 300, 4);
    assertSame(RunMode.UNKNOWN, legacy.getMode(), "record without a mode is unknown");
    assertSame(RunStats.EMPTY, legacy.getStats(), "record without stats has empty stats");
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
    assertSame(RunMode.UNKNOWN, new RunSession("plain").getMode(), "session without a mode");
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
    final RunSession invalid = new RunSession("invalid");
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
private static final String VERSION_ONE_PROFILE = "version=1\n"
        + "record.count=2\n"
        + "record.0.id=old-a\nrecord.0.score=1500\nrecord.0.enemies=30\n"
        + "record.0.duration=65000\nrecord.0.completed=1700000000000\n"
        + "record.1.id=old-b\nrecord.1.score=-20\nrecord.1.enemies=0\n"
        + "record.1.duration=0\nrecord.1.completed=1700000000001\n"
        + "achievement.count=2\n"
        + "achievement.0.id=first_run\nachievement.0.unlocked=1700000000000\n"
        + "achievement.1.id=sixty_seconds\nachievement.1.unlocked=1700000000002\n"
        + "ending.count=1\nending.0=ENDING2\n";
private static void checkVersionOneContent(final ProfileSnapshot snapshot, final String message) {
    assertEquals(2, snapshot.getRecords().size(), message + ": every v1 record is kept");
    assertSameRecord(new RunRecord("old-a", 1500, 30, 65000, 1700000000000L),
            snapshot.getRecords().get(0), message + ": old-a");
    assertSameRecord(new RunRecord("old-b", -20, 0, 0, 1700000000001L),
            snapshot.getRecords().get(1), message + ": old-b");
    assertEquals(1700000000000L, snapshot.getUnlockTimes().get("first_run").longValue(),
            message + ": achievements are kept");
    assertEquals(1700000000002L, snapshot.getUnlockTimes().get("sixty_seconds").longValue(),
            message + ": unlock time is kept");
    assertTrue(snapshot.getSeenEndings().contains(story.Ending.ENDING2), message + ": endings are kept");
}
private static void testVersionOneProfileMigration() throws Exception {
    Path profile = testDirectory("profile-v1").resolve("profile.properties");
    byte[] versionOne = VERSION_ONE_PROFILE.getBytes(StandardCharsets.UTF_8);
    Files.write(profile, versionOne);
    ProfileStore store = new ProfileStore(profile);
    checkVersionOneContent(store.load(), "v1 load");
    assertTrue(store.getLoadWarning() == null, "a v1 profile is not treated as a recovery");
    assertTrue(java.util.Arrays.equals(versionOne, Files.readAllBytes(profile)),
            "loading alone leaves the v1 file untouched");

    // One write converts the file; the v1 file it replaces becomes the backup.
    ProfileSnapshot loaded = store.load();
    store.write(loaded);
    assertEquals("2", profileVersion(profile), "the first write converts the profile to version 2");
    Path backup = profile.resolveSibling("profile.properties.bak");
    assertTrue(java.util.Arrays.equals(versionOne, Files.readAllBytes(backup)),
            "the v1 file is backed up, not quarantined");
    try (java.nio.file.DirectoryStream<Path> corrupt =
            Files.newDirectoryStream(profile.getParent(), "profile.properties.corrupt-*")) {
        assertFalse(corrupt.iterator().hasNext(), "a valid v1 file is never quarantined");
    }
    checkVersionOneContent(new ProfileStore(backup).load(), "v1 backup");
    checkVersionOneContent(new ProfileStore(profile).load(), "converted v2");

    // The game itself, started on an unconverted v1 profile.
    Path game = testDirectory("profile-v1-game").resolve("profile.properties");
    Files.write(game, versionOne);
    RecordsService service = new RecordsService(game);
    assertFalse(service.isWritesBlocked(), "a v1 profile does not block writes");
    assertEquals(2, service.getRecordCount(), "service keeps every v1 record");
    assertEquals(1500, service.getHighestScore(), "v1 personal best still counts");
    service.completeRun(new RunRecord("new-run", RunMode.STORY, 1600, 31, 70000, 1700000000003L,
            new RunStats(2, 5, 60, 31, 1, 1, 2)));
    service.close();
    assertTrue(service.getSaveError() == null, "saving after a v1 load succeeds");
    RecordsService restarted = new RecordsService(game);
    assertEquals(3, restarted.getRecordCount(), "old and new runs survive a restart");
    assertEquals(1600, restarted.getHighestScore(), "the new run counts after a restart");
    restarted.close();
    ProfileSnapshot converted = new ProfileStore(game).load();
    checkVersionOneContent(new ProfileSnapshot(converted.getRecords().subList(0, 2),
            converted.getUnlockTimes(), converted.getSeenEndings()), "game-converted v2");
    RunRecord added = converted.getRecords().get(2);
    assertSame(RunMode.STORY, added.getMode(), "new run keeps its mode beside v1 runs");
    assertEquals(60, added.getStats().getShotsFired(), "new run keeps its stats beside v1 runs");
}
private static void testVersionOneBackupRecovery() throws Exception {
    Path profile = testDirectory("profile-v1-backup").resolve("profile.properties");
    Files.write(profile, VERSION_ONE_PROFILE.getBytes(StandardCharsets.UTF_8));
    ProfileStore store = new ProfileStore(profile);
    ProfileSnapshot loaded = store.load();
    List<RunRecord> records = new ArrayList<RunRecord>(loaded.getRecords());
    records.add(new RunRecord("newer", RunMode.CAMPAIGN, 5, 1, 100, 1700000000009L,
            new RunStats(1, 1, 3, 1, 0, 0, 0)));
    store.write(new ProfileSnapshot(records, loaded.getUnlockTimes(), loaded.getSeenEndings()));
    Files.write(profile, "version=2\nrecord.count=oops\n".getBytes(StandardCharsets.UTF_8));

    ProfileStore reopened = new ProfileStore(profile);
    checkVersionOneContent(reopened.load(), "recovered from v1 backup");
    assertTrue(reopened.getLoadWarning() != null, "recovery from a v1 backup is reported");
    assertEquals("2", profileVersion(profile), "the recovered profile is rewritten as version 2");
    checkVersionOneContent(new ProfileStore(profile).load(), "rewritten recovered profile");
}
private static void testRunDetailLines() {
    List<String> story = RunRecordFormatter.detailLines(new RunRecord("d-story", RunMode.STORY, 1, 1, 1, 1,
            new RunStats(3, 7, 40, 10, 2, 1, 4)));
    assertEquals("Mode: Story|Stage reached: 3|Waves reached: 7|Shots fired: 40|Hits: 10|Accuracy: 25.0%"
            + "|Times hit: 2|Bosses defeated: 1|Items used: 4", String.join("|", story), "story run details");
    List<String> infinite = RunRecordFormatter.detailLines(new RunRecord("d-inf", RunMode.INFINITE, 1, 1, 1, 1,
            new RunStats(0, 0, 8, 2, 1, 0, 0)));
    assertEquals("Stage reached: -", infinite.get(1), "infinite has no stage");
    assertEquals("Waves reached: -", infinite.get(2), "infinite has no waves");
    assertEquals("Accuracy: 25.0%", infinite.get(5), "infinite accuracy");
    List<String> legacy = RunRecordFormatter.detailLines(new RunRecord("d-v1", 1, 1, 1, 1));
    assertEquals("Mode: Unknown", legacy.get(0), "v1 run mode");
    assertContains(String.join(" ", legacy), "not recorded", "v1 run says its details were not recorded");
    assertFalse(String.join(" ", legacy).contains("Shots"), "v1 run does not pretend to have zero statistics");
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
        VERSION_ONE_PROFILE.replace("version=1", "version=3"),
    };
    String[] reason = {"missing mode", "unknown mode", "missing statistic", "negative statistic", "future version"};
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
    Path valid = directory.resolve("valid.properties");
    Files.write(valid, (header + "record.0.mode=UNKNOWN\n" + stats).getBytes(StandardCharsets.UTF_8));
    assertSame(RunMode.UNKNOWN, new ProfileStore(valid).load().getRecords().get(0).getMode(),
            "v2 accepts converted unknown-mode records");
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
