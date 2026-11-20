package engine;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.function.Consumer;

import screen.GameModeScreen;

import java.util.UUID;
import records.RunSession;
import records.RunRecord;
import records.RunRecordFormatter;
import records.RecordsService;
import level.LevelDefinition;
import audio.AudioControls;
import screen.AudioControlScreen;
import screen.GameScreen;
import screen.HighScoreScreen;
import screen.InfiniteGameScreen;
import screen.ScoreScreen;
import screen.Screen;
import screen.TitleScreen;
import entity.ShipType;
import screen.PlayerRecordsScreen;
import campaign.CampaignStageData;
import campaign.CampaignManager;
import screen.SettingsScreen;
import screen.ShipColorScreen;
import screen.ControlsSettingsScreen;
import story.PlaceholderStoryScenes;
import story.StoryProgress;
import story.StoryScenes;

/**
 * Implements core game logic.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class Core {
	/** Width of current screen. */
	private static final int WIDTH = 448;
	/** Height of current screen. */
	private static final int HEIGHT = 520;
	/** Max fps of current screen. */
	private static final int FPS = 60;

	/** Max lives. */
	private static final int MAX_LIVES = 3;
	/** Levels between extra life. */
	private static final int EXTRA_LIFE_FRECUENCY = 3;

	/** Frame to draw screens on. */
	private static Frame frame;
	/** Screen currently shown. */
	private static Screen currentScreen;
	private static RecordsService recordsService;
	/** Application logger. */
	private static final Logger LOGGER = Logger.getLogger(Core.class
			.getSimpleName());
	/** Logger handler for printing to disk. */
	private static Handler fileHandler;
	/** Logger handler for printing to console. */
	private static ConsoleHandler consoleHandler;

	/**
	 * Test implementation.
	 *
	 * @param args
	 *             Program args, ignored.
	 */
	public static void main(final String[] args) {
		run();
	}

	/** Starts the game. */
	public static void run() {
		try {
			LOGGER.setUseParentHandlers(false);

			fileHandler = new FileHandler("log");
			fileHandler.setFormatter(new MinimalFormatter());

			consoleHandler = new ConsoleHandler();
			consoleHandler.setFormatter(new MinimalFormatter());

			LOGGER.addHandler(fileHandler);
			LOGGER.addHandler(consoleHandler);
			LOGGER.setLevel(Level.ALL);

		} catch (Exception e) {
			// TODO handle exception
			e.printStackTrace();
		}
		AudioControls.applySettings();

		frame = new Frame(WIDTH, HEIGHT);
		DrawManager.getInstance().setFrame(frame);
		int width = frame.getWidth();
		int height = frame.getHeight();

		recordsService = new RecordsService(new java.io.File(System.getProperty("user.home"),
				".space-invaders/records-achievements.properties").toPath());
		registerRecordsShutdownHook(recordsService);
		if (recordsService.getSaveError() != null)
			LOGGER.warning(recordsService.getSaveError());
		if (recordsService.getLoadWarning() != null)
			LOGGER.warning(recordsService.getLoadWarning());

		GameState gameState;
		ShipType selectedShip = ShipType.STANDARD;
		int playerCount = 1;

		int returnCode = 1;
		do {
			gameState = new GameState(1, 0, MAX_LIVES, 0, 0);
			// When starting a new game, reset the difficulty adjustment conditions (player has 1 life or less && player has not cleared 3 levels while on low difficulty)
			DifficultyAdjustWhilePlaying.getInstance().reset();
			switch (returnCode) {
			case 1:
				// Main menu.
				currentScreen = new TitleScreen(width, height, FPS);
				LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
						+ " title screen at " + FPS + " fps.");
				returnCode = frame.setScreen(currentScreen);
				LOGGER.info("Closing title screen.");

				if (returnCode == 2)
					returnCode = 14;
				break;
			case 14:
				currentScreen = new GameModeScreen(width, height, FPS);
				LOGGER.info("Starting game mode selection screen.");
				returnCode = frame.setScreen(currentScreen);
				playerCount = ((GameModeScreen) currentScreen).getPlayerCount();
				LOGGER.info("Closing game mode selection screen.");
				break;
			case 2:
				// The selected ship decides how many lives the run starts with.
				LOGGER.info("Selected ship: " + selectedShip.getName());

					// Campaign lives belong to the run; in 2P, start at one and cap at two.
					int campaignMaxLives = playerCount == 2 ? 2
							: selectedShip.getMaxLives();
					int startingLives = playerCount == 2 ? 1 : campaignMaxLives;
					gameState = new GameState(1, 0, startingLives,
							0, 0);

					// Game & score.
					RunSession runSession = new RunSession(UUID.randomUUID().toString());
										recordsService.startRun(runSession.getRunId());
					do {
						// Award one team life at the usual milestones, up to this mode's cap.
						boolean bonusLife = gameState.getLevel()
								% EXTRA_LIFE_FRECUENCY == 0
								&& gameState.getLivesRemaining() < campaignMaxLives;

						currentScreen = new GameScreen(gameState,
								applyDifficulty(CampaignStageData
										.getStageSettings(gameState.getLevel())),
								bonusLife, width, height, FPS, selectedShip,
								runSession, playerCount);
						LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
								+ " game screen at " + FPS + " fps.");
						frame.setScreen(currentScreen);
						LOGGER.info("Closing game screen.");

						boolean stageCleared = ((GameScreen) currentScreen).isStageCleared();
						gameState = ((GameScreen) currentScreen).getGameState();

						if (!stageCleared) {
							LOGGER.info("Stage " + gameState.getLevel()
									+ " failed. Not advancing.");
							break;
						}

						CampaignManager.updateMaxCampaignStage(
								Math.min(gameState.getLevel() + 1,
										CampaignStageData.getTotalStages()));
						LOGGER.info("Highest Stage is: " + CampaignManager.getMaxCampaignStage());

						gameState = new GameState(gameState.getLevel() + 1,
								gameState.getScore(),
								gameState.getLivesRemaining(),
								gameState.getBulletsShot(),
								gameState.getShipsDestroyed());

					} while (gameState.getLivesRemaining() > 0
							&& gameState.getLevel() <= CampaignStageData.getTotalStages());

					RunRecord completedRun = runSession.finish(gameState.getScore(),
							gameState.getShipsDestroyed(), System.currentTimeMillis());
					if (recordsService.completeRun(completedRun))
						LOGGER.info("Completed run recorded:\n" + RunRecordFormatter.format(completedRun));

					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " score screen at " + FPS + " fps, with a score of "
							+ gameState.getScore() + ", "
							+ gameState.getLivesRemaining() + " lives remaining, "
							+ gameState.getBulletsShot() + " bullets shot and "
							+ gameState.getShipsDestroyed() + " ships destroyed.");
					currentScreen = new ScoreScreen(width, height, FPS, gameState,
							recordsService.getCurrentRunUnlocks());
					returnCode = frame.setScreen(currentScreen);
					LOGGER.info("Closing score screen.");
					break;
				case 3:
					// High scores.
					currentScreen = new HighScoreScreen(width, height, FPS);
					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " high score screen at " + FPS + " fps.");
					returnCode = frame.setScreen(currentScreen);
					LOGGER.info("Closing high score screen.");
					break;
				case 9:
					currentScreen = new PlayerRecordsScreen(width, height, FPS, recordsService);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 4:
					currentScreen = new SettingsScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 5:
					currentScreen = new AudioControlScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 6:
					currentScreen = new ControlsSettingsScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 7:
					currentScreen = new ShipColorScreen(width, height, FPS);
					returnCode = frame.setScreen(currentScreen);
					break;
				case 8:
					// Infinite mode & score.
					RunSession infiniteSession = new RunSession(UUID.randomUUID().toString());
					recordsService.startRun(infiniteSession.getRunId());
					currentScreen = new InfiniteGameScreen(gameState, width, height,
							FPS, infiniteSession, playerCount);
					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " infinite game screen at " + FPS + " fps.");
					frame.setScreen(currentScreen);
					LOGGER.info("Closing infinite game screen.");

					gameState = ((InfiniteGameScreen) currentScreen).getGameState();
					if (!((InfiniteGameScreen) currentScreen).isAbandoned()) {
						RunRecord infiniteRecord = infiniteSession.finish(gameState.getScore(),
								gameState.getShipsDestroyed(), System.currentTimeMillis());
						recordsService.completeRun(infiniteRecord);
					}

					LOGGER.info("Starting " + WIDTH + "x" + HEIGHT
							+ " score screen at " + FPS + " fps, with a score of "
							+ gameState.getScore() + ", "
							+ gameState.getBulletsShot() + " bullets shot and "
							+ gameState.getShipsDestroyed() + " ships destroyed.");
					// Infinite mode score screen, Space plays infinite mode again.
					currentScreen = new ScoreScreen(width, height, FPS, gameState,
							true, recordsService.getCurrentRunUnlocks());
					returnCode = frame.setScreen(currentScreen);
					LOGGER.info("Closing score screen.");
					break;
				case 15:
					// Story mode & score.
					returnCode = playStory(selectedShip, width, height,
							new PlaceholderStoryScenes());
					break;
			}

		} while (returnCode != 0);

		recordsService.close();
		if (recordsService.getSaveError() != null)
			LOGGER.warning(recordsService.getSaveError());
		fileHandler.flush();
		fileHandler.close();
		System.exit(0);
	}

	/**
	 * Plays story mode: the opening, the seven story stages with a question
	 * after stages 1 and 3, the ending and the score screen. Records work
	 * as in the campaign, but campaign progress is not saved.
	 *
	 * @param selectedShip
	 *            Ship picked by the player.
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param scenes
	 *            Story scenes shown between the stages.
	 * @return Return code of the score screen.
	 */
	private static int playStory(final ShipType selectedShip, final int width,
			final int height, final StoryScenes scenes) {
		LOGGER.info("Starting story mode with " + selectedShip.getName() + ".");
		int maxLives = selectedShip.getMaxLives();
		GameState gameState = new GameState(1, 0, maxLives, 0, 0);
		StoryProgress progress = new StoryProgress();

		RunSession runSession = new RunSession(UUID.randomUUID().toString());
		recordsService.startRun(runSession.getRunId());

		scenes.showOpening();
		while (gameState.getLivesRemaining() > 0
				&& gameState.getLevel() <= StoryProgress.TOTAL_STAGES) {
			int stage = gameState.getLevel();
			boolean bonusLife = stage % EXTRA_LIFE_FRECUENCY == 0
					&& gameState.getLivesRemaining() < maxLives;
			GameScreen screen = GameScreen.forStoryStage(gameState, progress,
					Core::applyDifficulty, bonusLife, width, height, FPS,
					selectedShip, runSession);
			currentScreen = screen;
			LOGGER.info("Starting story stage " + stage + ".");
			frame.setScreen(screen);
			LOGGER.info("Closing story stage " + stage + ".");

			gameState = screen.getGameState();
			if (!screen.isStageCleared()) {
				LOGGER.info("Story stage " + stage + " failed.");
				break;
			}
			askStoryQuestion(stage, progress, scenes);
			gameState = new GameState(stage + 1, gameState.getScore(),
					gameState.getLivesRemaining(), gameState.getBulletsShot(),
					gameState.getShipsDestroyed());
		}

		if (gameState.getLevel() > StoryProgress.TOTAL_STAGES)
			scenes.showEnding(progress.getRoute());

		RunRecord storyRecord = runSession.finish(gameState.getScore(),
				gameState.getShipsDestroyed(), System.currentTimeMillis());
		if (recordsService.completeRun(storyRecord))
			LOGGER.info("Completed story run recorded:\n"
					+ RunRecordFormatter.format(storyRecord));

		currentScreen = new ScoreScreen(width, height, FPS, gameState,
				recordsService.getCurrentRunUnlocks());
		int returnCode = frame.setScreen(currentScreen);
		LOGGER.info("Closing score screen.");
		// The score screen's "play again" code is the campaign's; story mode
		// replays itself instead.
		return returnCode == 2 ? 15 : returnCode;
	}

	/**
	 * Asks the story question that follows a cleared stage: the first one
	 * after stage 1 and the second one after the stage 3 boss.
	 *
	 * @param stage
	 *            Stage that was just cleared.
	 * @param progress
	 *            Answers given so far.
	 * @param scenes
	 *            Story scenes that ask the questions.
	 */
	static void askStoryQuestion(final int stage, final StoryProgress progress,
			final StoryScenes scenes) {
		if (stage == StoryProgress.FIRST_QUESTION_STAGE)
			progress.chooseFirst(scenes.askFirst());
		else if (stage == StoryProgress.MID_BOSS_STAGE)
			progress.chooseSecond(scenes.askSecond(progress.getFirstChoice()));
	}

	/**
	 * Runs one validated custom stage in the existing window and returns its result.
	 * Call on the game's screen-running thread after file selection/import completes.
	 * This path never enters the official campaign loop, unlocks stages, or submits
	 * a high score. The caller decides which menu/result screen to display next.
	 */
	public static GameState playCustomLevel(final LevelDefinition definition) {
		if (frame == null)
			throw new IllegalStateException("The game window must be initialized first");
		return runCustomLevel(definition, GameplayConfig.getMultiplier(),
				frame.getWidth(), frame.getHeight(), FPS, screen -> {
					currentScreen = screen;
					frame.setScreen(screen);
				});
	}

	/** Creates the menu/controller's persistent custom-level selection for this window. */
	public static CustomLevelSession createCustomLevelSession() {
		if (frame == null)
			throw new IllegalStateException("The game window must be initialized first");
		return new CustomLevelSession(frame.getWidth(), frame.getHeight());
	}

	// Runner is separated so handoff and unlock isolation can be checked headlessly.
	static GameState runCustomLevel(final LevelDefinition definition, final double multiplier,
			final int width, final int height, final int fps, final Consumer<GameScreen> runner) {
		Objects.requireNonNull(runner, "runner");
		GameScreen custom = GameScreen.forCustomLevel(
				new GameState(1, 0, MAX_LIVES, 0, 0), definition, multiplier, width, height, fps);
		runner.accept(custom);
		return custom.getGameState();
	}

	private static GameSettings applyDifficulty(
			final GameSettings stageSettings) {
		return applyDifficulty(stageSettings, GameplayConfig.getMultiplier());
	}

	// The calculation is accessible to tests in the engine package.
	static GameSettings applyDifficulty(
			final GameSettings stageSettings, final double multiplier) {
		int movementInterval = Math.max(1,
				(int) Math.round(stageSettings.getBaseSpeed() * multiplier));
		int shootingInterval = Math.max(250,
				(int) Math.round(
						stageSettings.getShootingFrecuency() * multiplier));

		return new GameSettings(stageSettings.getFormationWidth(),
				stageSettings.getFormationHeight(), movementInterval,
				shootingInterval, stageSettings.getInitialX(),
				stageSettings.getInitialY());
	}

	/**
	 * Constructor, not called.
	 */
	private Core() {

	}

	/**
	 * Controls access to the logger.
	 *
	 * @return Application logger.
	 */
	public static Logger getLogger() {
		return LOGGER;
	}

	/**
	 * Controls access to the drawing manager.
	 *
	 * @return Application draw manager.
	 */
	public static DrawManager getDrawManager() {
		return DrawManager.getInstance();
	}

	/**
	 * Controls access to the input manager.
	 *
	 * @return Application input manager.
	 */
	public static InputManager getInputManager() {
		return InputManager.getInstance();
	}

	/**
	 * Controls access to the file manager.
	 *
	 * @return Application file manager.
	 */
	public static FileManager getFileManager() {
		return FileManager.getInstance();
	}

	/**
	 * Controls creation of new cooldowns.
	 *
	 * @param milliseconds
	 *                     Duration of the cooldown.
	 * @return A new cooldown.
	 */
	public static Cooldown getCooldown(final int milliseconds) {
		return new Cooldown(milliseconds);
	}

	/**
	 * Controls creation of new cooldowns with variance.
	 *
	 * @param milliseconds
	 *                     Duration of the cooldown.
	 * @param variance
	 *                     Variation in the cooldown duration.
	 * @return A new cooldown with variance.
	 */
	public static Cooldown getVariableCooldown(final int milliseconds,
			final int variance) {
		return new Cooldown(milliseconds, variance);
	}

	public static RecordsService getRecordsService() {
		return recordsService;
	}

	private static void registerRecordsShutdownHook(final RecordsService service) {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				service.close();
				if (service.getSaveError() != null)
					LOGGER.warning(service.getSaveError());
			}
		}, "records-profile-shutdown"));
	}
}
