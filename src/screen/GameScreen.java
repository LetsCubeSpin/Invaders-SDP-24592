package screen;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

import boss.*;
import campaign.CampaignStageData;
import engine.DifficultyAdjustWhilePlaying;
import entity.item.ItemManager;
import entity.item.ShieldItem;
import engine.Cooldown;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.GameplayConfig;
import engine.StoryDebugModifiers;
import engine.ControlsConfig;
import engine.LevelGameplayAdapter;
import level.LevelDefinition;
import level.WaveDefinition;
import entity.Bullet;
import entity.BonusEnemyManager;
import entity.Boss;
import entity.BossBullet;
import entity.BulletPool;
import entity.BulletType;
import vfx.ParticleSystem;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.Entity;
import entity.FallingObstacle;
import entity.LoggingPlayerStatusListener;
import entity.PlayerStatusListener;
import entity.PlayerStatusEvent;
import entity.PlayerStatus;
import entity.Ship;
import entity.ShipType;
import audio.AudioManager;
import hud.FloatingScoreHud;
import hud.AchievementToast;
import hud.ItemStatus;
import records.RunSession;
import records.StageClear;
import records.RecordsService;
import story.StoryProgress;
import story.StoryQuestion;
import story.StoryStage;
import story.StoryScript;
import story.StoryStageData;
import event.BulletHitEvent;
import story.Typewriter;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventIds;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import event.ItemUsedEvent;
import event.LevelCompletedEvent;
import event.PlayerHitEvent;
import event.PlayerHitSource;
import event.ShotFiredEvent;
import event.UsedItemType;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.UUID;
import entity.Grenade;
import entity.GrenadeFragment;
import entity.item.ClusterGrenadeItem;
import entity.item.Item;

/**
 * Implements the game screen, where the action happens.
 * Plays sound effects for gameplay events.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class GameScreen extends Screen {

	/** Ends the isolated Story battle without a clear, defeat or score bonus. */
	public static final int RETURN_TO_STORY_DEBUG = 73;

    /** Milliseconds until the screen accepts user input. */
    private static final int INPUT_DELAY = 6000;
    /** Bonus score for each life remaining at the end of the level. */
    private static final int LIFE_SCORE = 100;
    /** Time from finishing the level to screen change. */
    private static final int SCREEN_CHANGE_INTERVAL = 1500;
	/** Average time between falling obstacle spawns, in milliseconds. */
	private static final int FALLING_OBSTACLE_INTERVAL = 17500;
	/** Spawn interval varies from fifteen to twenty seconds. */
	private static final int FALLING_OBSTACLE_VARIANCE = 2500;
	/** Average time between falling obstacles in a story asteroid field. */
	private static final int ASTEROID_FIELD_INTERVAL = 6000;
	/**
	 * Enables the debug keys used to exercise player status by hand. Off
	 * unless the game is started with -Dinvaders.debugKeys=true, so a normal
	 * play session on main never has them.
	 */
	private static final boolean DEBUG_KEYS =
			Boolean.getBoolean("invaders.debugKeys");
    /** Height of the interface separation line. */
    private static final int SEPARATION_LINE_HEIGHT = 40;
	/** Damage dealt to the boss by one player shot (to confirm with the
	 * weapon developer). */
	static final int BOSS_DAMAGE_PER_SHOT = 50;
	/** Time the boss explosion stays on screen. */
	private static final int BOSS_EXPLOSION_TIME = 500;
	/** Sound played as the warning before a boss special attack. */
	private static final String BOSS_WARNING_SOUND =
			"audio/sfx/item_laser_charge.wav";
	/** Sound played when a boss special attack fires. */
	private static final String BOSS_SPECIAL_SOUND =
			"audio/sfx/item_laser_beam.wav";
	/** Sound played when the boss appears. */
	private static final String BOSS_SPAWN_SOUND =
			"audio/sfx/boss_spawn.wav";
	/** Sound played when the boss enters a new phase. */
	private static final String BOSS_PHASE_SOUND =
			"audio/sfx/boss_phase.wav";
	/** Sound played when the boss fires a normal attack. */
	private static final String BOSS_SHOT_SOUND =
			"audio/sfx/boss_shoot.wav";
	/** Sound played when the boss is defeated. */
	private static final String BOSS_EXPLOSION_SOUND =
			"audio/sfx/boss_explosion.wav";
	/** Sound played when the boss activates its shield. */
	private static final String BOSS_SHIELD_ON_SOUND =
			"audio/sfx/status_shield_on.wav";
	/** Sound played when a shield blocks an attack. */
	private static final String SHIELD_BLOCK_SOUND =
			"audio/sfx/item_shield_block.wav";
	/** Minimum interval between shield block sounds inside a laser beam. */
	private static final long LASER_SHIELD_BLOCK_SFX_INTERVAL_MS = 250L;
	/** Timestamp of the last shield block sound played against a boss laser. */
	private long lastLaserShieldBlockSfxMillis;
	/** Stages where a boss appears (agreed with the team, may change). */
	static final int[] BOSS_LEVELS = { 3, 5, 7 };
	/**
	 * Top of the boss: under the bonus ship's path, and with its bottom no
	 * lower than the top of the enemy formation, which starts at y 100.
	 */
	static final int BOSS_SPAWN_Y = 86;
    /** Chance for a destroyed enemy ship to drop a Cluster Grenade item. */
    private static final double GRENADE_DROP_CHANCE = 0.15;
    /** Fixed altitude (Y) where a grenade explodes if it hits nothing. */
    private static final int GRENADE_DETONATION_Y = 200;
    /** Maximum number of Cluster Grenade items dropped in one level. */
    private static final int MAX_GRENADE_DROPS_PER_LEVEL = 2;

	/** Time the defeated boss stays on screen as an explosion. */
	private Cooldown bossExplosionCooldown;
	private static final int TWO_PLAYER_MAX_LIVES = 2;
	/** Boss of the level, its attacks, events and links to other systems.
	 * The only source of truth for the boss. */
	private BossManager bossManager;
	/** Set of all bullets fired by the boss. */
	private Set<BossBullet> bossBullets;

    private ItemManager itemManager;
    private final Random itemDropRandom = new Random();
	private final Random playerTargetRandom = new Random();
	private final Random obstacleRandom = new Random();
	/** Active hazards; a set permits future expansion beyond one obstacle. */
	private Set<FallingObstacle> fallingObstacles;
	/** Time until the next falling hazard appears. */
	private Cooldown fallingObstacleCooldown;
    private boolean shieldDroppedThisLevel = false;

    /** Grenades launched by the player and not exploded yet. */
    private Set<Grenade> grenades;
    /** Fragments of exploded grenades. */
    private Set<GrenadeFragment> fragments;
    /** Prevents launching several grenades while holding G. */
    /** Number of Cluster Grenade items dropped in this level. */
    private int grenadeDropsThisLevel = 0;

	/** Player ship type selected before the stage started. */
	private ShipType shipType;
    /** Current game difficulty settings. */
    private GameSettings gameSettings;
	/** Settings of every wave in this stage, in play order. */
	private List<GameSettings> waves;
	/** Original custom grids; null for the unchanged official campaign path. */
	private List<WaveDefinition> customWaves;
	/** Index of the wave currently being played. */
	private int currentWave;
	/** Story stage being played; null outside story mode. */
	private StoryStage storyStage;
	private final StoryDebugModifiers modifiers;
	/** Only the Story debug launcher can supply this battle's return control. */
	private BooleanSupplier debugReturnRequested;
	/** Boss of the story stage; null when the stage has no boss. */
	private Boss.BossType storyBoss;
    /** Current difficulty level number. */
    private int level;
    /** Difficulty selected when this stage was started. */
    private int difficulty;
    /** Formation of enemy ships. */
    private EnemyShipFormation enemyShipFormation;
    /** Player's ship. */
    private Ship ship;
    /** Number of players selected for this run. */
    private final int playerCount;
	private List<PlayerSlot> players;
	private int sharedLives;
	private int maxSharedLives;
    /** Bonus enemy ship that appears sometimes. */
	private BonusEnemyManager bonusEnemyManager;
    /** Time from finishing the level to screen change. */
    private Cooldown screenFinishedCooldown;
    /** Set of all bullets fired by on screen ships. */
    private Set<Bullet> bullets;
    /** Explosion particles. */
    private ParticleSystem particleSystem = new ParticleSystem();
    /** Current score. */
    private int score;
	private final FloatingScoreHud floatingScoreHud = new FloatingScoreHud();
    /** Player lives left. */
	private PlayerStatus playerStatus;
	/** State of the debug shield key on the previous frame. */
	private boolean shieldDebugKeyWasDown;
    /** Total bullets shot by the player. */
    private int bulletsShot;
    /** Total ships destroyed by the player. */
    private int shipsDestroyed;
    /** Moment the game starts. */
    private long gameStartTime;
    /** Checks if the stage was cleared. */
    private boolean stageCleared;
    private TimeAttackClock timeAttack;
    private int timeAttackClearSeconds;
    /** Checks if the level is finished. */
    private boolean levelFinished;
    /** Checks if a bonus life is received. */
    private boolean bonusLife;
    /** Last countdown number that played a sound. */
    private int lastCountdown = -1;

    /** Parts of a story stage start, which replaces the countdown. */
    private enum StoryIntro { NONE, DIALOGUE, TITLE, GO }

    /** How long the level title shows when a story stage has no line. */
    private static final int STORY_TITLE_MILLIS = 1500;
    /** How long "GO!" stays on screen while the stage already runs. */
    private static final int STORY_GO_MILLIS = 900;
    /** Typing speed of the pilot's line in characters per second. */
    private static final double STORY_CHARS_PER_SECOND = 30;

    private StoryIntro storyIntro = StoryIntro.NONE;

    /** Parts of the question asked after a cleared story stage. */
    private enum StoryQuestionPhase { NONE, TYPING, CHOOSING, DONE }

    /** Pixels per frame the ship flies forward while a choice is made. */
    private static final int STORY_FORWARD_SPEED = 3;
    /** How long the chosen half stays shown before the screen closes. */
    private static final int STORY_CHOICE_HOLD_MILLIS = 800;

    private boolean storyChoiceEnabled;
    private StoryProgress storyProgress;
    private StoryQuestion storyQuestion;
    private StoryQuestionPhase questionPhase = StoryQuestionPhase.NONE;
    private Typewriter questionTypewriter;
    /** Starts true so a Space held while the last enemy fell is ignored. */
    private boolean questionSpaceWasDown = true;
    private int storyChoice = -1;
    private long choiceMadeMillis;
    private Typewriter introTypewriter;
    private long introStartMillis;
    private long goStartMillis;
    /** Starts true so a Space held from the previous screen is ignored. */
    private boolean introSpaceWasDown = true;
	private RunSession runSession;
    private RecordsService recordsService;
    /** Where the status strip is free after the item icons, for the achievement notice. */
    private int statusStripFreeFrom;
    private final GameplayEventPublisher recordsEvents = new GameplayEventPublisher();
    private final Map<EnemyShip,String> defeatIds = new IdentityHashMap<EnemyShip,String>();
    /** Separates the enemy type from the random part of a defeat ID. */
    static final String DEFEAT_ID_SEPARATOR = ":";
    /** Flag to track if the urgent low-health BGM is currently playing. */
    private boolean isLowHealthBGM = false;
    private ArrayList<EnemyShip> summonedEnemies;

    /**
     * Constructor, establishes the properties of the screen.
     *
     * @param gameState
     *            Current game state.
     * @param gameSettings
     *            Current game settings.
     * @param bonusLife
     *            Checks if a bonus life is awarded this level.
     * @param width
     *            Screen width.
     * @param height
     *            Screen height.
     * @param fps
     *            Frames per second, frame rate at which the game is run.
     */
    public GameScreen(final GameState gameState,
                      final GameSettings gameSettings, final boolean bonusLife,
                      final int width, final int height, final int fps) {
		this(gameState, Collections.singletonList(gameSettings), bonusLife,
				width, height, fps, ShipType.STANDARD);
	}

	/**
	 * Constructor for a stage played with a selected player ship.
	 *
	 * @param gameState
	 *            Current game state.
	 * @param gameSettings
	 *            Current game settings.
	 * @param bonusLife
	 *            Checks if a bonus life is awarded this level.
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 * @param shipType
	 *            Selected player ship type.
	 */
	public GameScreen(final GameState gameState,
					  final GameSettings gameSettings, final boolean bonusLife,
					  final int width, final int height, final int fps,
					  final ShipType shipType) {
		this(gameState, Collections.singletonList(gameSettings), bonusLife,
				width, height, fps, shipType);
	}

	/**
	 * Constructor for a stage with one or more waves.
	 *
	 * @param gameState
	 * 			Current game state.
	 * @param waves
	 * 			Settings of each wave, in play order.
	 * @param bonusLife
	 * 			Checks if a bonus life is awarded this level.
	 * @param width
	 * 			Screen width.
	 * @param height
	 * 			Screen height.
	 * @param fps
	 * 			Frames per second, frame rate at which the game is run.
	 */
	public GameScreen(final GameState gameState,
					  final List<GameSettings> waves, final boolean bonusLife,
					  final int width, final int height, final int fps) {
		this(gameState, waves, bonusLife, width, height, fps,
				ShipType.STANDARD, null);
	}

	/**
	 * Constructor for a stage with one or more waves, played with a selected
	 * player ship.
	 *
	 * @param gameState
	 * 			Current game state.
	 * @param waves
	 * 			Settings of each wave, in play order.
	 * @param bonusLife
	 * 			Checks if a bonus life is awarded this level.
	 * @param width
	 * 			Screen width.
	 * @param height
	 * 			Screen height.
	 * @param fps
	 * 			Frames per second, frame rate at which the game is run.
	 * @param shipType
	 * 			Selected player ship type.
	 */
	public GameScreen(final GameState gameState,
					  final List<GameSettings> waves, final boolean bonusLife,
					  final int width, final int height, final int fps,
					  final ShipType shipType) {
		this(gameState, waves, bonusLife, width, height, fps, shipType, null);
	}

	/**
	 * Constructor for a single-wave stage that records its run.
	 *
	 * @param runSession
	 * 			Session the run is recorded under, or null for no records.
	 */
	public GameScreen(final GameState gameState,
					  final GameSettings gameSettings, final boolean bonusLife,
					  final int width, final int height, final int fps,
					  final RunSession runSession) {
		this(gameState, Collections.singletonList(gameSettings), bonusLife,
				width, height, fps, ShipType.STANDARD, runSession);
	}

	/**
	 * Constructor for a multi-wave stage that records its run.
	 *
	 * @param runSession
	 * 			Session the run is recorded under, or null for no records.
	 */
	public GameScreen(final GameState gameState,
					  final List<GameSettings> waves, final boolean bonusLife,
					  final int width, final int height, final int fps,
					  final RunSession runSession) {
		this(gameState, waves, bonusLife, width, height, fps,
				ShipType.STANDARD, runSession);
	}

	/**
	 * Constructor for a single-wave stage played with a selected ship and
	 * recorded under a run session.
	 */
	public GameScreen(final GameState gameState,
					  final GameSettings gameSettings, final boolean bonusLife,
					  final int width, final int height, final int fps,
					  final ShipType shipType, final RunSession runSession) {
		this(gameState, Collections.singletonList(gameSettings), bonusLife,
				width, height, fps, shipType, runSession);
	}

	/**
	 * Constructor for a stage with one or more waves, played with a selected
	 * player ship and recorded under a run session. Every other constructor
	 * reaches this one.
	 *
	 * @param gameState
	 * 			Current game state.
	 * @param waves
	 * 			Settings of each wave, in play order.
	 * @param bonusLife
	 * 			Checks if a bonus life is awarded this level.
	 * @param width
	 * 			Screen width.
	 * @param height
	 * 			Screen height.
	 * @param fps
	 * 			Frames per second, frame rate at which the game is run.
	 * @param shipType
	 * 			Selected player ship type.
	 * @param runSession
	 * 			Session the run is recorded under, or null for no records.
	 */
	public GameScreen(final GameState gameState,
					  final List<GameSettings> waves, final boolean bonusLife,
					  final int width, final int height, final int fps,
					  final ShipType shipType, final RunSession runSession) {
        this(gameState, waves, bonusLife, width, height, fps, shipType, runSession, 1);
    }

    /* add player count */
    public GameScreen(final GameState gameState, final GameSettings gameSettings,
            final boolean bonusLife, final int width, final int height, final int fps,
            final ShipType shipType, final RunSession runSession,
            final int playerCount) {
        this(gameState, Collections.singletonList(gameSettings), bonusLife,
                width, height, fps, shipType, runSession, playerCount);
    }

    /** Campaign stage entry point with the selected player count. */
    public GameScreen(final GameState gameState, final List<GameSettings> waves,
            final boolean bonusLife, final int width, final int height, final int fps,
            final ShipType shipType, final RunSession runSession,
            final int playerCount) {
        this(gameState, waves, bonusLife, width, height, fps, shipType, runSession, playerCount, null);
    }

    private GameScreen(final GameState gameState, final List<GameSettings> waves,
            final boolean bonusLife, final int width, final int height, final int fps,
            final ShipType shipType, final RunSession runSession, final int playerCount,
            final StoryDebugModifiers debug) {
        super(width, height, fps);
        this.modifiers = debug == null ? StoryDebugModifiers.defaults() : debug;
		if (playerCount != 1 && playerCount != 2)
			throw new IllegalArgumentException("Player count must be 1 or 2.");
		this.playerCount = playerCount;

		this.runSession = runSession;
		if (runSession != null)
			runSession.listenTo(this.recordsEvents);
		this.recordsService = Core.getRecordsService();
		this.recordsEvents.subscribe(EnemyDefeatedEvent.class,
				new GameplayEventListener<EnemyDefeatedEvent>() {
			public void onEvent(final EnemyDefeatedEvent event) {
				if (recordsService != null)
					recordsService.enemyDefeated(event.getRunId(),
							event.getEventId());
			}
		});
		// The session keeps the cleared stage with the run, and stage
		// achievements are checked against it at once.
		this.recordsEvents.subscribe(LevelCompletedEvent.class,
				new GameplayEventListener<LevelCompletedEvent>() {
			public void onEvent(final LevelCompletedEvent event) {
				RunSession session = GameScreen.this.runSession;
				if (session == null)
					return;
				StageClear clear = session.stageCleared(System.nanoTime());
				if (clear != null && recordsService != null)
					recordsService.stageCleared(session.getRunId(), clear);
			}
		});

		if (shipType == null)
			throw new IllegalArgumentException("Ship type cannot be null.");

		this.shipType = shipType;
		this.waves = copyWaves(waves);
		this.currentWave = 0;
		this.gameSettings = this.waves.get(0);
        this.bonusLife = bonusLife;
        this.level = gameState.getLevel();
        this.difficulty = GameplayConfig.getDifficulty();
        this.score = gameState.getScore();
		int maxLives = this.playerCount == 2 ? 1
				: this.shipType.getMaxLives();
		int initialLives = this.playerCount == 2 ? 1
				: Math.min(gameState.getLivesRemaining(), maxLives);
		if (debug != null) {
			maxLives = debug.startingLives();
			initialLives = maxLives;
		}
		this.playerStatus = new PlayerStatus(initialLives, maxLives,
				this.shipType.isShieldCapable());
		this.sharedLives = this.playerCount == 2
				? Math.min(TWO_PLAYER_MAX_LIVES, gameState.getLivesRemaining()
						+ (this.bonusLife ? 1 : 0))
				: this.playerStatus.getLives();
		this.maxSharedLives = this.playerCount == 2 ? TWO_PLAYER_MAX_LIVES : maxLives;
        if (this.bonusLife && this.playerCount == 1)
			this.playerStatus.gainLife();
        this.bulletsShot = gameState.getBulletsShot();
        this.shipsDestroyed = gameState.getShipsDestroyed();
    }

/** Validates every wave before allocating a screen or changing active gameplay. */
	public static GameScreen forCustomLevel(final GameState state,
			final LevelDefinition definition, final double difficultyMultiplier,
			final int width, final int height, final int fps) {
		List<GameSettings> settings = LevelGameplayAdapter.prepare(
				definition, width, height, difficultyMultiplier);
		GameScreen screen = new GameScreen(state, settings, false, width, height, fps);
		screen.customWaves = definition.getWaves();
		return screen;
	}
	/**
	 * Builds one story mode stage. The stage number is taken from the game
	 * state. Waves, boss and asteroid field come from the story data, and
	 * the difficulty is applied to the formation timing.
	 *
	 * @param gameState
	 *            State carried over from the previous stage.
	 * @param progress
	 *            Answers given so far in this story run.
	 * @param difficulty
	 *            Applies the selected difficulty to the stage settings.
	 * @param bonusLife
	 *            Whether the player gains a life at the start.
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second.
	 * @param shipType
	 *            Ship picked by the player.
	 * @param runSession
	 *            Records session of this run.
	 * @return Screen of the story stage.
	 */
	public static GameScreen forStoryStage(final GameState gameState,
			final StoryProgress progress,
			final UnaryOperator<GameSettings> difficulty,
			final boolean bonusLife, final int width, final int height,
			final int fps, final ShipType shipType,
			final RunSession runSession) {
		return createStoryStage(gameState, progress, difficulty, bonusLife,
				width, height, fps, shipType, runSession, null);
	}

	/** Starts one STANDARD Story battle with a fresh life pool and no run session. */
	public static GameScreen forStoryDebugStage(final GameState gameState,
			final StoryProgress progress, final UnaryOperator<GameSettings> difficulty,
			final int width, final int height, final int fps, final StoryDebugModifiers modifiers) {
		return forStoryDebugStage(gameState, progress, difficulty, width, height, fps, modifiers, null);
	}

	/** Grants optional launcher controls only to this isolated debug battle. */
	public static GameScreen forStoryDebugStage(final GameState gameState,
			final StoryProgress progress, final UnaryOperator<GameSettings> difficulty,
			final int width, final int height, final int fps, final StoryDebugModifiers modifiers,
			final BooleanSupplier returnRequested) {
		if (modifiers == null)
			throw new IllegalArgumentException("Debug modifiers must not be null.");
		GameScreen screen = createStoryStage(gameState, progress, difficulty, false,
				width, height, fps, ShipType.STANDARD, null, modifiers);
		screen.debugReturnRequested = returnRequested;
		return screen;
	}

	private static GameScreen createStoryStage(final GameState gameState, final StoryProgress progress,
			final UnaryOperator<GameSettings> difficulty, final boolean bonusLife, final int width,
			final int height, final int fps, final ShipType shipType, final RunSession runSession,
			final StoryDebugModifiers debug) {
		if (difficulty == null)
			throw new IllegalArgumentException("difficulty must not be null.");
		int stageNumber = gameState.getLevel();
		StoryStage stage = StoryStageData.get(stageNumber, progress);
		GameSettings settings = difficulty.apply(stage.getSettings());
		List<WaveDefinition> storyWaves = new ArrayList<WaveDefinition>();
		for (int wave = 1; wave <= stage.getWaveCount(); wave++)
			storyWaves.add(stage.createWave(stageNumber, wave));

		GameScreen screen = new GameScreen(gameState,
				Collections.nCopies(stage.getWaveCount(), settings), bonusLife,
				width, height, fps, shipType, runSession, 1, debug);
		screen.customWaves = storyWaves;
		screen.storyStage = stage;
		screen.storyBoss = StoryProgress.isBossStage(stageNumber)
				? progress.bossForStage(stageNumber) : null;
		screen.startStoryIntro(stageNumber);
		screen.storyProgress = progress;
		return screen;
	}

	/**
	 * Replaces the countdown of a story stage. The stage waits while the pilot
	 * says the stage's line, or for a moment on the level title when there is
	 * no line, and then starts at once with "GO!".
	 */
	private void startStoryIntro(final int stage) {
		String line = StoryScript.stageIntroLine(stage);
		if (line != null) {
			this.storyIntro = StoryIntro.DIALOGUE;
			this.introTypewriter = new Typewriter(line,
					STORY_CHARS_PER_SECOND);
		} else {
			this.storyIntro = StoryIntro.TITLE;
		}
	}

	/**
	 * Holds the stage, like the countdown does, until the story intro ends.
	 * It runs after the screen's own input delay is set.
	 */
	private void holdForStoryIntro() {
		if (!isStoryIntroHolding())
			return;
		this.inputDelay = Core.getCooldown(Integer.MAX_VALUE);
		this.inputDelay.reset();
		this.introStartMillis = System.currentTimeMillis();
	}

	/**
	 * Advances the story stage start.
	 *
	 * @param spaceDown
	 *            Whether Space is held down.
	 * @param now
	 *            Current time in milliseconds.
	 */
	void updateStoryIntro(final boolean spaceDown, final long now) {
		if (this.storyIntro == StoryIntro.NONE)
			return;
		boolean spacePressed = spaceDown && !this.introSpaceWasDown;
		this.introSpaceWasDown = spaceDown;
		switch (this.storyIntro) {
		case DIALOGUE:
			this.introTypewriter.start(now);
			if (spacePressed) {
				if (this.introTypewriter.isDone(now))
					beginStoryStage(now);
				else
					this.introTypewriter.finish();
			}
			break;
		case TITLE:
			if (now - this.introStartMillis >= STORY_TITLE_MILLIS)
				beginStoryStage(now);
			break;
		case GO:
			if (now - this.goStartMillis >= STORY_GO_MILLIS)
				this.storyIntro = StoryIntro.NONE;
			break;
		default:
			break;
		}
	}

	/** Starts the stage right away and shows "GO!" for a moment. */
	private void beginStoryStage(final long now) {
		this.inputDelay = Core.getCooldown(0);
		this.storyIntro = StoryIntro.GO;
		this.goStartMillis = now;
		AudioManager.playSFX("audio/sfx/countdown_go.wav");
	}

	/**
	 * Lets the player answer the story question that follows the stage by
	 * flying the ship into one half of the banner line. Off by default, in
	 * which case the stage simply ends and the story scenes ask the question.
	 *
	 * @param enabled
	 *            Whether the question is asked on this screen.
	 */
	public void setStoryChoiceEnabled(final boolean enabled) {
		this.storyChoiceEnabled = enabled;
	}

	/** @return Choice made on this screen, 0 or 1, or -1 when none was made. */
	public int getStoryChoice() {
		return this.storyChoice;
	}

	/**
	 * Runs the story question after the stage is cleared: the text is typed
	 * out, Space starts the choosing, and flying the ship up into the left or
	 * right half of the banner line picks choice 0 or 1.
	 *
	 * @return Whether the question still needs the screen.
	 */
	boolean updateStoryQuestion(final long now, final boolean spaceDown,
			final boolean forwardDown, final boolean leftDown,
			final boolean rightDown) {
		boolean spacePressed = spaceDown && !this.questionSpaceWasDown;
		this.questionSpaceWasDown = spaceDown;
		if (!this.storyChoiceEnabled || !this.stageCleared
				|| this.storyProgress == null)
			return false;

		if (this.questionPhase == StoryQuestionPhase.NONE) {
			int firstChoice = this.level == StoryProgress.FIRST_QUESTION_STAGE
					? 0 : this.storyProgress.getFirstChoice();
			this.storyQuestion = StoryScript.question(this.level, firstChoice);
			if (this.storyQuestion == null)
				return false;
			this.questionPhase = StoryQuestionPhase.TYPING;
			this.questionTypewriter = new Typewriter(
					this.storyQuestion.getText(), STORY_CHARS_PER_SECOND);
			this.questionTypewriter.start(now);
			return true;
		}

		switch (this.questionPhase) {
		case TYPING:
			if (spacePressed) {
				if (this.questionTypewriter.isDone(now)) {
					this.questionPhase = StoryQuestionPhase.CHOOSING;
					AudioManager.playSFX("audio/sfx/menu_select.wav");
				} else
					this.questionTypewriter.finish();
			}
			return true;
		case CHOOSING:
			flyShipToChoose(now, forwardDown, leftDown, rightDown);
			return true;
		case DONE:
			return now - this.choiceMadeMillis < STORY_CHOICE_HOLD_MILLIS;
		default:
			return false;
		}
	}

	/** Moves the ship while choosing, and picks the half it flies into. */
	private void flyShipToChoose(final long now, final boolean forwardDown,
			final boolean leftDown, final boolean rightDown) {
		boolean isRightBorder = this.ship.getPositionX()
				+ this.ship.getWidth() + this.ship.getSpeed() > this.width - 1;
		boolean isLeftBorder = this.ship.getPositionX()
				- this.ship.getSpeed() < 1;
		if (rightDown && !isRightBorder)
			this.ship.moveRight();
		if (leftDown && !isLeftBorder)
			this.ship.moveLeft();
		clampShipPosition();
		// Forward only works while choosing.
		if (forwardDown)
			this.ship.setPositionY(
					this.ship.getPositionY() - STORY_FORWARD_SPEED);
		if (this.ship.getPositionY() <= storyChoiceLineY()) {
			this.storyChoice = shipSide();
			this.questionPhase = StoryQuestionPhase.DONE;
			this.choiceMadeMillis = now;
			AudioManager.playSFX("audio/sfx/menu_select.wav");
		}
	}

	/** Height of the lower line of the banner, the line that is split. */
	private int storyChoiceLineY() {
		return this.height / 2 + this.height / 12;
	}

	/** @return 0 when the ship is over the left half, 1 over the right. */
	private int shipSide() {
		int center = this.ship.getPositionX() + this.ship.getWidth() / 2;
		return center < this.width / 2 ? 0 : 1;
	}

	private void drawStoryQuestion() {
		long now = System.currentTimeMillis();
		if (this.questionPhase == StoryQuestionPhase.TYPING) {
			drawManager.drawStoryQuestionBox(this,
					this.questionTypewriter.getText(),
					this.questionTypewriter.visibleLength(now),
					this.questionTypewriter.isDone(now), now);
		} else if (this.questionPhase == StoryQuestionPhase.CHOOSING
				|| this.questionPhase == StoryQuestionPhase.DONE) {
			drawManager.drawStoryChoices(this,
					new String[] { this.storyQuestion.getTitle(0),
							this.storyQuestion.getTitle(1) },
					new String[] { this.storyQuestion.getDetail(0),
							this.storyQuestion.getDetail(1) },
					shipSide(), this.storyChoice, now);
		}
	}

	/** @return Whether a story stage is still in its dialogue or title part. */
	boolean isStoryIntroHolding() {
		return this.storyIntro == StoryIntro.DIALOGUE
				|| this.storyIntro == StoryIntro.TITLE;
	}

	private void drawStoryIntro() {
		long now = System.currentTimeMillis();
		drawManager.drawCountDown(this, this.level,
				this.storyIntro == StoryIntro.GO ? 0 : 4, this.bonusLife);
		drawManager.drawHorizontalLine(this, this.height / 2 - this.height
				/ 12);
		drawManager.drawHorizontalLine(this, this.height / 2 + this.height
				/ 12);
		if (this.storyIntro == StoryIntro.DIALOGUE)
			drawManager.drawStoryDialogue(this, StoryScript.PILOT_NAME,
					this.introTypewriter.getText(),
					this.introTypewriter.visibleLength(now),
					this.introTypewriter.isDone(now), now);
	}

	private EnemyShipFormation createFormation() {
		EnemyShipFormation formation = new EnemyShipFormation(this.gameSettings, this.level,
				this.customWaves == null ? null : this.customWaves.get(this.currentWave), this.modifiers);
		formation.attach(this);
		return formation;
	}

	private void initializePlayerSlots() {
		this.players = new ArrayList<PlayerSlot>();
		for (int index = 0; index < this.playerCount; index++) {
			Ship playerShip = createPlayerShip(index);
			PlayerStatus status = index == 0 ? this.playerStatus
					: new PlayerStatus(1, 1, this.shipType.isShieldCapable());
			this.players.add(new PlayerSlot(index, playerShip, status));
			this.itemManager.getBuffManager(playerShip);
		}
		syncPrimaryPlayer();
	}

	private Ship createPlayerShip(final int index) {
		int positionX = this.width / 2;
		if (this.playerCount == 2)
			positionX += index == 0 ? -20 : 20;
		Ship playerShip = new Ship(positionX, this.height - 30, this.shipType);
		playerShip.setBaseColor(ShipCustomization.getColor(index));
		if (this.storyStage != null)
			playerShip.setBulletType(BulletType.NORMAL);
		return playerShip;
	}

	private void registerPlayerStatusListeners(final PlayerStatus status) {
		status.addListener(new LoggingPlayerStatusListener());
		status.addListener(new PlayerStatusListener() {
			public void onPlayerStatusEvent(final PlayerStatusEvent event) {
				recordsEvents.publish(event);
				handlePlayerStatusSound(event);
			}
		});
	}

	private void syncPrimaryPlayer() {
		PlayerSlot primary = this.players.get(0);
		this.ship = primary.getShip();
		this.playerStatus = primary.getStatus();
	}

	private void respawnPlayers() {
		for (int index = 0; index < this.players.size(); index++) {
			PlayerSlot player = this.players.get(index);
			this.itemManager.releaseShip(player.getShip());
			Ship playerShip = createPlayerShip(index);
			PlayerStatus status = new PlayerStatus(1, 1,
					this.shipType.isShieldCapable());
			player.respawn(playerShip, status);
			this.itemManager.getBuffManager(playerShip);
			registerPlayerStatusListeners(status);
		}
		syncPrimaryPlayer();
	}

	private int getLivesRemaining() {
		return this.playerCount == 2 ? this.sharedLives
				: this.playerStatus.getLives();
	}

	private boolean hasLivingPlayer() {
		return getLivingPlayerCount() > 0;
	}

	private int getLivingPlayerCount() {
		int livingPlayers = 0;
		for (PlayerSlot player : this.players)
			if (player.isAlive())
				livingPlayers++;
		return livingPlayers;
	}

	/** Selects an active player uniformly for an enemy attack. */
	private Ship chooseRandomLivingTargetShip() {
		List<Ship> targets = new ArrayList<Ship>();
		for (PlayerSlot player : this.players) {
			Ship candidate = player.getShip();
			if (player.isAlive() && !candidate.isDestroyed())
				targets.add(candidate);
		}
		if (targets.isEmpty())
			return null;
		return targets.get(this.playerTargetRandom.nextInt(targets.size()));
	}

	/** Spends one team life only after every player's personal ship is down. */
	private void resolveSharedPlayerDefeat() {
		if (this.playerCount != 2)
			return;

		if (!hasLivingPlayer()) {
			this.sharedLives = Math.max(0, this.sharedLives - 1);
			if (this.sharedLives > 0)
				respawnPlayers();
		}

		if (this.sharedLives == 1 && getLivingPlayerCount() == 1
				&& !this.isLowHealthBGM) {
			AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
			this.isLowHealthBGM = true;
		}
	}

	/**
	 * Initializes basic screen properties, and adds necessary elements.
	 */
	public final void initialize() {
		super.initialize();
		// The time between the previous screen and this one does not count
		// toward the achievement notice.
		if (this.recordsService != null)
			AchievementToast.of(this.recordsService).screenStarted();

		enemyShipFormation = createFormation();
		if (this.runSession != null) {
			this.runSession.stageStarted(this.level);
			this.runSession.waveStarted();
		}

        this.itemManager = new ItemManager(this.height);
		initializePlayerSlots();

        this.shieldDroppedThisLevel = false;

        this.grenades = new HashSet<Grenade>();
        this.fragments = new HashSet<GrenadeFragment>();
        this.grenadeDropsThisLevel = 0;

		this.bonusEnemyManager = new BonusEnemyManager(this.modifiers);
		this.screenFinishedCooldown = Core.getCooldown(SCREEN_CHANGE_INTERVAL);
        this.bullets = new HashSet<Bullet>();
		this.fallingObstacles = new HashSet<FallingObstacle>();
		this.fallingObstacleCooldown = Core.getVariableCooldown(
				fallingObstacleInterval(), FALLING_OBSTACLE_VARIANCE);
		this.fallingObstacleCooldown.reset();

		this.bossBullets = new HashSet<BossBullet>();
        this.summonedEnemies = new ArrayList<EnemyShip>();
		this.bossManager = new BossManager();
		this.bossManager.connectGameplayEvents(this.recordsEvents, getActiveRunId());
		// Logs boss events for debugging.
		this.bossManager.getEventManager().addListener(
				new LoggingBossEventListener());
		// Boss events also go onto the shared gameplay bus, so Sound,
		// Currency and Records can subscribe to BossEvent.class there, and
		// drive the effects, sounds and records handled on this screen.
		this.bossManager.getEventManager().addListener(
				new BossEventListener() {
			public void onBossEvent(final BossEvent event) {
				recordsEvents.publish(event);
				handleBossEvent(event);
			}
		});
		for (PlayerSlot player : this.players)
			registerPlayerStatusListeners(player.getStatus());
		// The boss appears only on boss stages.
		Boss.BossType bossType = bossTypeForStage();
		if (bossType != null)
			this.bossManager.spawn(new BossSpawnRequest(
					bossType,
					this.width / 2 - Boss.WIDTH / 2, BOSS_SPAWN_Y,
					this.level, 0), this.modifiers, bossAttackIntervalScale());
		this.bossExplosionCooldown = Core.getCooldown(BOSS_EXPLOSION_TIME);

        // Special input delay / countdown.
        this.gameStartTime = System.currentTimeMillis();
        this.inputDelay = Core.getCooldown(INPUT_DELAY);
        this.inputDelay.reset();
        holdForStoryIntro();

		if (this.bonusLife) {
			AudioManager.playSFX("audio/sfx/bonus_life.wav");
		}

		// The last life is checked first, so the urgent track also plays on
		// boss stages.
		if (getLivesRemaining() == 1
				&& (this.playerCount == 1 || getLivingPlayerCount() == 1)) {
			AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
			this.isLowHealthBGM = true;
		} else if (bossTypeForStage() != null) {
			// Boss stages start the boss music right away, so it already plays
			// during the countdown instead of switching when the boss appears.
			AudioManager.playBGM("audio/bgm/music_boss.wav");
			this.isLowHealthBGM = false;
		} else {
			AudioManager.playBGM("audio/bgm/music_game.wav");
			this.isLowHealthBGM = false;
		}
	}

    public static GameScreen forTimeAttack(final GameState state, final GameSettings settings,
            final int width, final int height, final int fps) {
        return forTimeAttack(state, settings, width, height, fps, new TimeAttackClock(System::nanoTime));
    }

    static GameScreen forTimeAttack(final GameState state, final GameSettings settings,
            final int width, final int height, final int fps, final TimeAttackClock clock) {
        GameScreen screen = new GameScreen(state, settings, false, width, height, fps);
        screen.timeAttack = clock;
        return screen;
    }

    /**
     * Starts the action.
     *
     * @return Next screen code.
     */
    public final int run() {
        try { super.run(); }
        finally { if (this.runSession != null) this.runSession.stopActiveSegment(System.nanoTime()); }

		if (this.returnCode == RETURN_TO_STORY_DEBUG)
			return this.returnCode;

        // Life bonus is only awarded when the stage was cleared.
        if (this.stageCleared && this.timeAttack == null)
			this.score += LIFE_SCORE * (getLivesRemaining() - 1);
        this.logger.info("Screen cleared with a score of " + this.score);

        return this.returnCode;
    }

	/**
	 * Updates the elements on screen, checks for events, and plays the bonus
	 * enemy appearance sound.
	 */
	protected final void update() {
		if (this.debugReturnRequested != null && !this.levelFinished
				&& this.debugReturnRequested.getAsBoolean()) {
			this.returnCode = RETURN_TO_STORY_DEBUG;
			this.isRunning = false;
			return;
		}
		super.update();
		updateStoryIntro(inputManager.isKeyDown(KeyEvent.VK_SPACE),
				System.currentTimeMillis());
		if (this.timeAttack == null && this.recordsService != null
				&& AchievementToast.of(this.recordsService)
						.update(System.currentTimeMillis()) != null)
			AudioManager.playSFX("audio/sfx/achievement_unlock.wav");

		// Outside the input-delay check: a shield that runs out has to be
		// announced even while the level is finishing.
		for (PlayerSlot player : this.players)
			player.getStatus().update();
		// Sends the current number of lives remaining to the DifficultyAdjustWhilePlaying class
		if (this.timeAttack == null)
			DifficultyAdjustWhilePlaying.getInstance().update(getLivesRemaining());
		// Reconcile shields active outside the item-collision path.
		for (PlayerSlot player : this.players)
			clearSlowIfEnergyShieldActive(player);

        if (this.timeAttack != null && !this.levelFinished && this.inputDelay.checkFinished()) {
            this.timeAttack.start();
            if (this.timeAttack.remainingNanos() == 0) failStage();
        }

        if (this.inputDelay.checkFinished() && !this.levelFinished) {
            if (this.runSession != null) this.runSession.startActiveSegment(System.nanoTime());
            if (this.runSession != null && this.recordsService != null)
                this.recordsService.activeDuration(this.runSession.getRunId(),
                        this.runSession.getActiveDurationMillis(System.nanoTime()));

            if (this.playerStatus.isAlive() && !this.ship.isDestroyed()) {
                boolean moveRight = inputManager.isKeyDown(
                        ControlsConfig.getKey(ControlsConfig.RIGHT));
                boolean moveLeft = inputManager.isKeyDown(
                        ControlsConfig.getKey(ControlsConfig.LEFT));

                boolean isRightBorder = this.ship.getPositionX()
						+ this.ship.getWidth() + this.ship.getSpeed() > this.width - 1;
                boolean isLeftBorder = this.ship.getPositionX()
                        - this.ship.getSpeed() < 1;

                if (moveRight && !isRightBorder) {
                    this.ship.moveRight();
                    clampShipPosition();
                }
                if (moveLeft && !isLeftBorder) {
                    this.ship.moveLeft();
                    clampShipPosition();
                }

				// Normal shooting using configured controls.
				if (inputManager.isKeyDown(
						ControlsConfig.getKey(ControlsConfig.FIRE))) {
					int fired = this.ship.shoot(this.bullets);
					this.bulletsShot += fired;
					publishShotFired(fired);
					if (fired > 0)
						AudioManager.playSFX("audio/sfx/player_shoot.wav");
				}

				// Temporary: the shield has no in-game source until B5 adds
				// the buff item, so it cannot be verified by playing without
				// this key. Removed in that PR.
				if (DEBUG_KEYS) {
					boolean shieldKeyDown = inputManager
							.isKeyDown(KeyEvent.VK_F2);
					// Fires on the press, not every frame the key is held,
					// so holding F2 cannot chain shields over the expiry and
					// hide the very timing this key exists to show.
					if (shieldKeyDown && !this.shieldDebugKeyWasDown) {
						if (this.playerStatus.activateShield())
							this.logger.info("Debug shield granted.");
						else
							this.logger.info("Debug shield refused (ship not "
									+ "shield-capable).");
					}
					this.shieldDebugKeyWasDown = shieldKeyDown;
				}
            }

			// Track the physical key even while a ship is down. Requiring a
			// release before the next throw prevents a held key from firing
			// automatically when a shared-life respawn replaces the ship.
			PlayerSlot primaryPlayer = this.players.get(0);
			boolean grenadeKeyDown = inputManager.isKeyDown(
					primaryPlayer.getKey(ControlsConfig.GRENADE));
			if (this.playerStatus.isAlive() && !this.ship.isDestroyed()
					&& grenadeKeyDown
					&& !primaryPlayer.isGrenadeKeyPressed()
					&& !isGrenadeKeyBoundElsewhere(primaryPlayer)
					&& this.ship.launchGrenade(this.grenades,
							GRENADE_DETONATION_Y)) {
				AudioManager.playSFX("audio/sfx/item_grenade_launch.wav");
				publishItemUsed(UsedItemType.CLUSTER_GRENADE);
			}
			primaryPlayer.setGrenadeKeyPressed(grenadeKeyDown);

			updateSecondaryPlayerControls();
			this.bonusEnemyManager.update(this.width);

            Boss boss = getBossOnScreen();
            if (boss != null) {
                boss.update(1, this.width - 1);
                List<BossAttackSystem.SummonRequest> summons =
                        new ArrayList<>();
				Ship target = chooseRandomLivingTargetShip();
				if (target != null) {
					this.bossManager.update(
							target.getPositionX() + target.getWidth() / 2,
							target.getPositionY(), this.bossBullets, summons);
				}

                for (BossAttackSystem.SummonRequest req : summons) {
                    EnemyShip minion = EnemyShip.createByType(
                            req.getEnemyType(), req.getPositionX(), req.getPositionY(), this.modifiers.scaleHp(1));
                    Cooldown shootCooldown = this.enemyShipFormation.createShooterCooldown();
                    shootCooldown.reset();
                    minion.setShootingCooldown(shootCooldown);
                    this.summonedEnemies.add(minion);
                }
            }

            for (Iterator<EnemyShip> it = this.summonedEnemies.iterator(); it.hasNext();) {
                EnemyShip minion = it.next();
                minion.update();
                minion.moveScaled(0, 2, this.modifiers.enemySpeedMultiplier());

                Ship target = chooseRandomLivingTargetShip();
                if (target != null && minion.canShoot()) {
                    if (minion.getDefeatedType() == DefeatedEnemyType.B) {
                        // B: player 자리에 null을 넘겨 수직 일자 발사
                        this.enemyShipFormation.shootFrom(minion, this.bullets, null);
                    } else if (minion.getDefeatedType() == DefeatedEnemyType.C) {
                        this.enemyShipFormation.shootFrom(minion, this.bullets, target);
                    }
                }

                boolean hitPlayer = false;
                for (PlayerSlot player : this.players) {
                    Ship playerShip = player.getShip();
                    if (!player.isAlive() || playerShip.isDestroyed()
                    		|| !checkCollision(minion, playerShip))
                    	continue;
                    minion.destroy();
                    this.particleSystem.spawnEnemyExplosion(
                    		minion.getPositionX() + minion.getWidth() / 2,
                    		minion.getPositionY() + minion.getHeight() / 2);
                    damagePlayer(player, 1, PlayerHitSource.ENEMY_COLLISION,
                    		"Collided with player ship");
                    it.remove();
                    hitPlayer = true;
                    break;
                }
                if (!hitPlayer
                    	&& (minion.isDestroyed() || minion.getPositionY() > this.height))
                    it.remove();
            }

			// Each living ship advances its own burst timer while sharing the bullet field.
			for (PlayerSlot player : this.players) {
				if (player.isAlive() && !player.getShip().isDestroyed()) {
					int burstFired = player.getShip().updateBurst(this.bullets);
					this.bulletsShot += burstFired;
					publishShotFired(burstFired);
				}
			}

            this.enemyShipFormation.update();
			this.enemyShipFormation.shoot(this.bullets,
					chooseRandomLivingTargetShip());
        }

		for (PlayerSlot player : this.players) {
			player.getShip().update();
			if (this.inputDelay.checkFinished() && !this.levelFinished)
				this.itemManager.updateBuffs(player.getShip());
		}

        if (this.inputDelay.checkFinished() && !this.levelFinished) {
			this.itemManager.updateItems();
			for (PlayerSlot player : this.players) {
				Ship playerShip = player.getShip();
				if (player.isAlive() && !playerShip.isDestroyed()) {
					List<Item> collected = this.itemManager.checkCollisions(
							playerShip, this.itemManager.getBuffManager(playerShip));
					publishItemsUsed(collected, playerShip);
					clearSlowIfEnergyShieldActive(player);
				}
			}
            clampShipPosition();
        }

		if (!updateFallingObstacles())
			manageFallingObstacleCollisions();
        manageCollisions();
		resolveSharedPlayerDefeat();
        manageGrenades();

        cleanBullets();
        this.particleSystem.update();
        draw();

        advanceStageIfReady();

        if (this.levelFinished && this.screenFinishedCooldown.checkFinished()) {
            boolean waiting = updateStoryQuestion(System.currentTimeMillis(),
                    inputManager.isKeyDown(KeyEvent.VK_SPACE),
                    inputManager.isKeyDown(KeyEvent.VK_UP),
                    inputManager.isKeyDown(
                            ControlsConfig.getKey(ControlsConfig.LEFT)),
                    inputManager.isKeyDown(
                            ControlsConfig.getKey(ControlsConfig.RIGHT)));
            if (!waiting)
                this.isRunning = false;
        }
    }

    /**
     * Reads Player 2's separate bindings without changing the existing Player 1
     * input path. Both ships add shots and grenades to the same team world.
     */
    private void updateSecondaryPlayerControls() {
        for (int index = 1; index < this.players.size(); index++) {
            PlayerSlot player = this.players.get(index);
            Ship playerShip = player.getShip();
            boolean grenadeKeyDown = inputManager.isKeyDown(
                    player.getKey(ControlsConfig.GRENADE));
            if (!player.isAlive() || playerShip.isDestroyed()) {
                // Remember keys held while this ship is down, so respawning
                // does not turn a held key into a new press.
                player.setGrenadeKeyPressed(grenadeKeyDown);
                continue;
            }

            boolean moveLeft = inputManager.isKeyDown(
                    player.getKey(ControlsConfig.LEFT));
            boolean moveRight = inputManager.isKeyDown(
                    player.getKey(ControlsConfig.RIGHT));
            boolean isLeftBorder = playerShip.getPositionX()
                    - playerShip.getSpeed() < 1;
            boolean isRightBorder = playerShip.getPositionX()
                    + playerShip.getWidth() + playerShip.getSpeed()
                    > this.width - 1;

            if (moveLeft && !isLeftBorder)
                playerShip.moveLeft();
            if (moveRight && !isRightBorder)
                playerShip.moveRight();

            // P2 fires into the shared bullet set, but the bullet counter and
            // fire sound still follow the existing team-wide behavior.
            if (inputManager.isKeyDown(player.getKey(ControlsConfig.FIRE))) {
                int fired = playerShip.shoot(this.bullets);
                this.bulletsShot += fired;
                publishShotFired(fired);
                if (fired > 0)
                    AudioManager.playSFX("audio/sfx/player_shoot.wav");
            }

            boolean grenadeKeyIsUsable = !isGrenadeKeyBoundElsewhere(player);
            if (grenadeKeyDown && grenadeKeyIsUsable
                    && !player.isGrenadeKeyPressed()
                    && playerShip.launchGrenade(this.grenades,
                            GRENADE_DETONATION_Y)) {
                AudioManager.playSFX("audio/sfx/item_grenade_launch.wav");
                publishItemUsed(UsedItemType.CLUSTER_GRENADE);
            }
            player.setGrenadeKeyPressed(grenadeKeyDown);
        }
    }

    /** Progression shared by official and custom stages. */
    void advanceStageIfReady() {
        if (!this.levelFinished) {
            int remainingSeconds = this.timeAttack == null ? 0 : this.timeAttack.remainingSeconds();
            if (this.timeAttack == null ? getLivesRemaining() <= 0 || !hasLivingPlayer()
                    : remainingSeconds == 0) {
                failStage();
            } else if (this.enemyShipFormation.isEmpty()
                    && this.summonedEnemies.isEmpty() && isBossCleared()) {
                if (this.currentWave < this.waves.size() - 1) {
                    startNextWave();
                } else {
                    this.levelFinished = true;
                    this.stageCleared = true;
                    if (this.timeAttack != null)
                        this.timeAttackClearSeconds = remainingSeconds;
                    AudioManager.playSFX("audio/sfx/level_clear.wav");
                    this.screenFinishedCooldown.reset();
                    this.logger.info("Stage " + this.level + " cleared.");
                    if (this.timeAttack == null) {
                        publishLevelCompleted();
                        DifficultyAdjustWhilePlaying.getInstance().onLevelCleared();
                    }
                }
            }
        }

        if (this.levelFinished && this.runSession != null)
            this.runSession.stopActiveSegment(System.nanoTime());
    }

    private void failStage() {
        this.levelFinished = true;
        this.stageCleared = false;
        AudioManager.playSFX("audio/sfx/game_over.wav");
        this.screenFinishedCooldown.reset();
        this.logger.info("Stage " + this.level + " failed.");
    }

	/**
	 * Publishes the completion of this campaign stage.
	 * Custom levels are not included until their identifiers are agreed.
	 */
	private void publishLevelCompleted() {
		if (this.customWaves != null && this.storyStage == null)
			return;
		this.recordsEvents.publish(
				new LevelCompletedEvent(this.level, this.difficulty));
	}

    /**
	 * Copies the wave list after checking that it can be played.
	 *
	 * @param waves
	 *            Settings of each wave, in play order.
	 * @return A copy of the wave list.
	 * @throws IllegalArgumentException
	 *             If the list is null or empty, or contains a null wave.
	 */
	static List<GameSettings> copyWaves(final List<GameSettings> waves) {
		if (waves == null || waves.isEmpty())
			throw new IllegalArgumentException(
					"A stage must contain at least one wave.");
		List<GameSettings> copy = new ArrayList<GameSettings>(waves);
		for (int i = 0; i < copy.size(); i++)
			if (copy.get(i) == null)
				throw new IllegalArgumentException(
						"Wave " + (i + 1) + " must not be null.");
		return copy;
	}

	/**
	 * Replaces the empty formation with the next wave's formation.
	 */
	private void startNextWave() {
		this.fallingObstacles.clear();
		this.fallingObstacleCooldown.reset();
        this.summonedEnemies.clear();
		this.logger.info("Stage " + this.level + " wave " +
				(this.currentWave + 1) + " cleared.");
		this.currentWave++;
		this.gameSettings = this.waves.get(this.currentWave);
		this.enemyShipFormation = createFormation();
		if (this.runSession != null)
			this.runSession.waveStarted();
		AudioManager.playSFX("audio/sfx/wave_transition.wav");
		this.logger.info("Stage " + this.level + " wave " + (this.currentWave + 1) +
				" of " + this.waves.size() + " started.");
	}

	/**
     * Draws the elements associated with the screen.
     */
    private void draw() {
        drawManager.initDrawing(this);
        if (this.timeAttack != null && !this.levelFinished && this.inputDelay.checkFinished()) {
            int seconds = this.timeAttack.countdownSeconds();
            if (seconds > 0)
                drawManager.drawTimeAttackCountdown(this, seconds, this.timeAttack.countdownOpacity(seconds));
        }

		for (FallingObstacle obstacle : this.fallingObstacles)
			drawManager.drawEntity(obstacle, obstacle.getPositionX(),
					obstacle.getPositionY());

		for (PlayerSlot player : this.players) {
			if (!player.isAlive())
				continue;
			Ship playerShip = player.getShip();
			drawManager.drawEntity(playerShip, playerShip.getPositionX(),
					playerShip.getPositionY());
			if (playerShip.isShieldActive() && !playerShip.isDestroyed())
				drawManager.drawShield(this, playerShip);
		}

		EnemyShip bonusShip = this.bonusEnemyManager.getShip();
		if (bonusShip != null)
			drawManager.drawEntity(bonusShip, bonusShip.getPositionX(),
					bonusShip.getPositionY());

		Boss boss = getBossOnScreen();
		if (boss != null)
			drawManager.drawBoss(this, boss, this.bossManager.getAttackSystem());

		for (BossBullet bossBullet : this.bossBullets)
			drawManager.drawEntity(bossBullet, bossBullet.getPositionX(),
					bossBullet.getPositionY());

        enemyShipFormation.draw();
        for (EnemyShip minion : this.summonedEnemies) {
            drawManager.drawEntity(minion, minion.getPositionX(), minion.getPositionY());
        }

        for (Bullet bullet : this.bullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

        for (Grenade grenade : this.grenades)
            drawManager.drawGrenade(grenade);
        for (GrenadeFragment fragment : this.fragments)
            drawManager.drawGrenadeFragment(fragment);

        this.itemManager.draw(this.drawManager);

        drawManager.drawFloatingScores(floatingScoreHud, this);

		// Interface.
		drawManager.drawScore(this, this.score);
        if (this.timeAttack != null) {
            drawManager.drawLevel(this, this.level, TimeAttackResultScreen.STAGE_COUNT);
            int seconds = this.stageCleared ? this.timeAttackClearSeconds : this.timeAttack.remainingSeconds();
            boolean timerRed = this.ship.isDamageFeedbackActive()
                    ? this.ship.isDamageFlashRed() : seconds <= 10;
            drawManager.drawTimeAttackHud(seconds, timerRed);
        } else {
            if (this.maxSharedLives > this.shipType.getMaxLives())
                drawManager.drawLifeCount(getLivesRemaining(), this.maxSharedLives);
            else
                drawManager.drawLives(this, getLivesRemaining(), this.maxSharedLives);
            drawManager.drawLevel(this, this.level, CampaignStageData.getTotalStages());
        }

		drawManager.drawBossHealthBar(this, this.bossManager.getHudInfo());

		// Status strip under the HUD: each player's grenades and active buffs.
		long now = System.currentTimeMillis();
		List<ItemStatus> items = new ArrayList<ItemStatus>();
		for (PlayerSlot player : this.players) {
			Ship playerShip = player.getShip();
			items.add(new ItemStatus(playerShip.getGrenadeCount(),
					this.itemManager.getBuffManager(playerShip).getActiveBuffIcons(), now));
		}
		this.statusStripFreeFrom = drawManager.drawItemStrip(this, items);

        drawManager.drawHorizontalLine(this, SEPARATION_LINE_HEIGHT - 1);

        // Countdown to game start, or the story stage start instead.
        if (this.storyIntro != StoryIntro.NONE) {
            drawStoryIntro();
        } else if (!this.inputDelay.checkFinished()) {
			int countdown = (int) ((INPUT_DELAY
					- (System.currentTimeMillis()
					- this.gameStartTime)) / 1000);
            playCountdownSound(countdown);
			drawManager.drawCountDown(this, this.level, countdown,
					this.bonusLife);
			drawManager.drawHorizontalLine(this, this.height / 2 - this.height
					/ 12);
			drawManager.drawHorizontalLine(this, this.height / 2 + this.height
					/ 12);
        }
        if (this.questionPhase != StoryQuestionPhase.NONE)
            drawStoryQuestion();
        drawManager.drawParticles(this.particleSystem);

        if (this.timeAttack == null && this.recordsService != null)
            drawManager.drawAchievementToast(this,
                    AchievementToast.of(this.recordsService), this.statusStripFreeFrom);
        drawManager.completeDrawing(this);
    }

    /**
     * Cleans bullets that go off screen.
     */
    private void cleanBullets() {
        Set<Bullet> recyclable = new HashSet<Bullet>();
        for (Bullet bullet : this.bullets) {
            bullet.update();
			// An aimed shot can travel almost horizontally, so a bullet that
			// leaves sideways has to be retired as well. Checking only the Y
			// axis left a bullet with no vertical speed on screen for the
			// rest of the level, drawn and collision-checked every frame and
			// never returned to the pool.
            if (bullet.getPositionY() < SEPARATION_LINE_HEIGHT
					|| bullet.getPositionY() > this.height
					|| bullet.getPositionX() + bullet.getWidth() < 0
					|| bullet.getPositionX() > this.width)
                recyclable.add(bullet);
        }
        this.bullets.removeAll(recyclable);
        BulletPool.recycle(recyclable);

		Set<BossBullet> outside = new HashSet<BossBullet>();
		for (BossBullet bossBullet : this.bossBullets) {
			bossBullet.update();
			if (bossBullet.isOutside(SEPARATION_LINE_HEIGHT, this.width,
					this.height))
				outside.add(bossBullet);
		}
		this.bossBullets.removeAll(outside);
    }

	/** Creates, advances, and retires the non-damaging falling hazards. */
	private boolean updateFallingObstacles() {
		if (this.levelFinished) {
			this.fallingObstacles.clear();
			return false;
		}
		if (!this.inputDelay.checkFinished())
			return false;

		if (this.fallingObstacles.isEmpty()
				&& this.fallingObstacleCooldown.checkFinished()) {
			int rightmostX = this.width - FallingObstacle.WIDTH - 1;
			int positionX = 1 + this.obstacleRandom.nextInt(rightmostX);
			this.fallingObstacles.add(new FallingObstacle(positionX,
					SEPARATION_LINE_HEIGHT));
			return true;
		}

		for (Iterator<FallingObstacle> iterator =
				this.fallingObstacles.iterator(); iterator.hasNext();) {
			FallingObstacle obstacle = iterator.next();
			obstacle.update();
			if (obstacle.hasLeftScreen(this.height)) {
				iterator.remove();
				this.fallingObstacleCooldown.reset();
			}
		}
		return false;
	}

    /** Consumes a colliding hazard, applying only the existing slow effect. */
    private void manageFallingObstacleCollisions() {
        if (!this.inputDelay.checkFinished() || this.levelFinished)
            return;

        for (Iterator<FallingObstacle> iterator =
             this.fallingObstacles.iterator(); iterator.hasNext();) {
            FallingObstacle obstacle = iterator.next();
			for (PlayerSlot player : this.players) {
				Ship playerShip = player.getShip();
				if (!player.isAlive() || !checkCollision(obstacle, playerShip))
					continue;
				// One falling obstacle can affect only the first ship it reaches.
				iterator.remove();
				this.fallingObstacleCooldown.reset();
				if (!playerShip.isInvulnerable()) {
					if (playerShip.isShieldActive()) {
						AudioManager.playSFX("audio/sfx/item_shield_block.wav");
					} else {
						AudioManager.playSFX("audio/sfx/obstacle_hit.wav");
						player.getStatus().applySlow(playerShip);
					}
				}
				break;
			}
        }
    }

    /** Clears an existing slow as soon as the Energy Shield item is active. */
    private void clearSlowIfEnergyShieldActive(final PlayerSlot player) {
        if (player.getShip().isShieldActive())
            player.getStatus().clearSlowDebuff();
    }

    /**
     * Manages collisions between bullets and ships.
     */
    private void manageCollisions() {
        if (this.levelFinished)
            return;
        Set<Bullet> recyclable = new HashSet<Bullet>();
        for (Bullet bullet : this.bullets) {
            if (this.levelFinished) break;
            if (bullet.isEnemyBullet()) {
				for (PlayerSlot player : this.players) {
					if (player.isAlive()
							&& checkCollision(bullet, player.getShip())) {
						recyclable.add(bullet);
						damagePlayer(player, 1, PlayerHitSource.ENEMY_BULLET,
								"Hit on player ship");
						break;
					}
				}
            } else {
                for (EnemyShip enemyShip : this.enemyShipFormation) {
                    if (!enemyShip.isDestroyed()
                            && checkCollision(bullet, enemyShip)) {
                        recyclable.add(bullet);
                        if (!this.enemyShipFormation.hit(enemyShip))
                            break;

                        AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
                        this.score += enemyShip.getPointValue();
                        floatingScoreHud.show(enemyShip);
                        this.shipsDestroyed++;
                        recordEnemyDefeat(enemyShip);
                        this.particleSystem.spawnEnemyExplosion(
                                enemyShip.getPositionX()
                                        + enemyShip.getWidth() / 2,
                                enemyShip.getPositionY()
                                        + enemyShip.getHeight() / 2);
                        tryDropGrenade(enemyShip);

                        // --- SHIELD ITEM DROP ---
                        if (!this.shieldDroppedThisLevel
                                && this.shipType.isShieldCapable()) {
                            boolean shouldDrop = (this.itemDropRandom.nextDouble() < 0.15)
                                    || (this.enemyShipFormation.isEmpty());
                            if (shouldDrop) {
                                this.itemManager.spawnItem(
                                        new ShieldItem(
                                                enemyShip.getPositionX(),
                                                enemyShip.getPositionY()
                                        )
                                );
                                this.shieldDroppedThisLevel = true;
                                this.logger.info("Shield item dropped from enemy ship at ("
                                        + enemyShip.getPositionX() + ", "
                                        + enemyShip.getPositionY() + ").");
                            }
                        }
                        break;
                    }
                }

                EnemyShip bonusShip = this.bonusEnemyManager.getShip();
                if (!recyclable.contains(bullet) && this.bonusEnemyManager.isActive()
                        && checkCollision(bullet, bonusShip)) {
                    hitBonusShip(bonusShip);
                    recyclable.add(bullet);
                }

                if (!recyclable.contains(bullet)) {
                    for (Iterator<EnemyShip> it = this.summonedEnemies.iterator(); it.hasNext();) {
                        EnemyShip minion = it.next();
                        if (!minion.isDestroyed() && checkCollision(bullet, minion)) {
                            recyclable.add(bullet);
                            if (minion.hit()) {
                                AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
                                this.particleSystem.spawnEnemyExplosion(
                                        minion.getPositionX() + minion.getWidth() / 2,
                                        minion.getPositionY() + minion.getHeight() / 2);
                                it.remove();
                            }
                            break;
                        }
                    }
                }

                // A bullet that already hit a ship cannot also hit the boss.
                Boss boss = getBossOnScreen();
                if (boss != null && !boss.isDefeated()
                        && !recyclable.contains(bullet)
                        && checkCollision(bullet, boss)) {
                    damageBoss(getBossDamagePerBullet());
                    recyclable.add(bullet);
                }

                // Every target above recycles the bullet that hit it, and a
                // bullet hits at most one target.
                if (recyclable.contains(bullet))
                    publishBulletHit();
            }
        }
        this.bullets.removeAll(recyclable);
        BulletPool.recycle(recyclable);
        if (this.levelFinished) return;

        Set<BossBullet> hitBossBullets = new HashSet<BossBullet>();
        for (BossBullet bossBullet : this.bossBullets) {
			for (PlayerSlot player : this.players) {
				if (player.isAlive()
						&& checkCollision(bossBullet, player.getShip())) {
					hitBossBullets.add(bossBullet);
					damagePlayer(player, bossBullet.getDamage(),
							PlayerHitSource.BOSS_BULLET,
							"Hit on player ship by the boss");
					break;
				}
			}
        }
        this.bossBullets.removeAll(hitBossBullets);

		Boss activeBoss = getBossOnScreen();
		BossAttackSystem bossAttackSystem = this.bossManager.getAttackSystem();
		if (activeBoss != null && !activeBoss.isDefeated()
				&& bossAttackSystem != null
				&& bossAttackSystem.isLaserFiring()) {
			for (PlayerSlot player : this.players) {
				if (player.isAlive() && bossAttackSystem.laserHits(activeBoss, player.getShip())) {
					damagePlayer(player, 1,
							PlayerHitSource.BOSS_BULLET,
							"Hit on player ship by OBELISK laser",
							shouldPlayLaserBlockSound());
				}
			}
		}
    }

	private boolean shouldPlayLaserBlockSound() {
		long now = System.currentTimeMillis();
		if (now - this.lastLaserShieldBlockSfxMillis
				< LASER_SHIELD_BLOCK_SFX_INTERVAL_MS)
			return false;
		this.lastLaserShieldBlockSfxMillis = now;
		return true;
	}
    private void hitBonusShip(final EnemyShip bonusShip) {
        if (!this.bonusEnemyManager.hit())
            return;
        recordEnemyDefeat(bonusShip);
        floatingScoreHud.show(bonusShip);
        this.score += bonusShip.getPointValue();
        this.shipsDestroyed++;
        this.particleSystem.spawnEnemyExplosion(
                bonusShip.getPositionX() + bonusShip.getWidth() / 2,
                bonusShip.getPositionY() + bonusShip.getHeight() / 2);
    }

    /**
	 * Applies one hit to the player. Every source of damage goes through this
	 * method, so that the protections and the effects of a hit cannot differ
	 * between an enemy bullet and a boss bullet.
	 *
	 * <p>The order matters. A protection returns before any life is taken,
	 * and the ship is destroyed only once the damage was actually accepted,
	 * which also stops several bullets in the same frame from each costing a
	 * life: the first accepted hit starts the recovery and the rest are
	 * turned away by {@link Ship#isInvulnerable()}.</p>
	 *
	 * @param damage
	 *            Amount of damage the source deals.
	 * @param logMessage
	 *            Description of the source, for the log line.
	 * @return Whether a life was lost.
	 */
	// The damage path is accessible to tests in the screen package.
	boolean damagePlayer(final int damage, final String logMessage) {
		return damagePlayer(this.players.get(0), damage,
				PlayerHitSource.UNKNOWN, logMessage);
	}

	/**
	 * Same as {@link #damagePlayer(int, String)}, naming what hit the player
	 * for the {@link PlayerHitEvent} sent when a life is lost.
	 */
	boolean damagePlayer(final int damage, final PlayerHitSource source,
			final String logMessage) {
		return damagePlayer(this.players.get(0), damage, source, logMessage);
	}

	private boolean damagePlayer(final PlayerSlot player, final int damage,
								 final PlayerHitSource source, final String logMessage) {
		return damagePlayer(player, damage, source, logMessage, true);
	}

	private boolean damagePlayer(final PlayerSlot player, final int damage,
			final PlayerHitSource source, final String logMessage, final boolean playBlockSound) {
        if (this.timeAttack != null && (this.levelFinished || !this.inputDelay.checkFinished()))
            return false;

		Ship playerShip = player.getShip();
		PlayerStatus status = player.getStatus();
		// Still inside the recovery period from an earlier hit. This outlasts
		// the time the ship cannot move, so that the player is not killed by
		// the shots that were aimed at them while they were held in place.
		if (!status.isAlive() || playerShip.isInvulnerable())
			return false;
		if (playerShip.isShieldActive()) {
			this.logger.info("Shot absorbed by Energy Shield.");
			if (playBlockSound)
				AudioManager.playSFX(SHIELD_BLOCK_SOUND);
			// The Energy Shield belongs to Ship, so PlayerStatus cannot see
			// this hit by itself. Announce it anyway, or a listener would
			// hear a blocked hit only when the timed shield was the one that
			// blocked it.
			status.announceDamageBlocked(damage);
			return false;
		}
		if (this.modifiers.invincible()) {
			if (status.isShieldActive()) {
				status.announceDamageBlocked(damage);
				if (playBlockSound)
					AudioManager.playSFX(SHIELD_BLOCK_SOUND);
			}
			return false;
		}
        if (this.timeAttack != null) {
            if (status.isShieldActive()) {
                status.announceDamageBlocked(damage);
                if (playBlockSound) AudioManager.playSFX(SHIELD_BLOCK_SOUND);
                return false;
            }
            this.timeAttack.deductHit();
            this.floatingScoreHud.showTimePenalty(playerShip, TimeAttackClock.HIT_PENALTY_SECONDS);
            AudioManager.playSFX("audio/sfx/player_hit.wav");
            playerShip.triggerDamageFeedback();
            if (this.timeAttack.remainingNanos() == 0) failStage();
            return true;
        }
		// PlayerStatus owns the timed shield, so its answer decides whether
		// the hit lands at all; the effects below must not run otherwise.
		int livesBefore = status.getLives();
		if (!status.takeDamage(damage)) {
			if (status.isShieldActive() && playBlockSound)
				AudioManager.playSFX(SHIELD_BLOCK_SOUND);
            return false;
        }
		AudioManager.playSFX("audio/sfx/player_hit.wav");

		playerShip.destroy();
		playerShip.triggerDamageFeedback();
		this.particleSystem.spawnPlayerExplosion(
				playerShip.getPositionX() + playerShip.getWidth() / 2,
				playerShip.getPositionY() + playerShip.getHeight() / 2);
		this.itemManager.releaseShip(playerShip);
		if (this.playerCount == 1 && status.getLives() == 1
				&& !this.isLowHealthBGM) {
			AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
			this.isLowHealthBGM = true;
		}
		this.logger.info(logMessage + ", " + getLivesRemaining()
				+ " lives remaining.");
		publishPlayerHit(source, livesBefore - status.getLives(),
				status.getLives());
		return true;
	}

	/**
     * Checks if two entities are colliding.
     *
     * @param a
     *            First entity, the bullet.
     * @param b
     *            Second entity, the ship.
     * @return Result of the collision test.
     */
    private boolean checkCollision(final Entity a, final Entity b) {
        // Calculate center point of the entities in both axis.
        int centerAX = a.getPositionX() + a.getWidth() / 2;
        int centerAY = a.getPositionY() + a.getHeight() / 2;
        int centerBX = b.getPositionX() + b.getWidth() / 2;
        int centerBY = b.getPositionY() + b.getHeight() / 2;
        // Calculate maximum distance without collision.
        int maxDistanceX = a.getWidth() / 2 + b.getWidth() / 2;
        int maxDistanceY = a.getHeight() / 2 + b.getHeight() / 2;
        // Calculates distance.
        int distanceX = Math.abs(centerAX - centerBX);
        int distanceY = Math.abs(centerAY - centerBY);

        return distanceX < maxDistanceX && distanceY < maxDistanceY;
    }

    /**
     * Checks if the stage was cleared.
     *
     * @return True if all enemies were defeated with the ship alive.
     */
	/**
	 * Applies damage to the boss. Every attack that hurts the boss must go
	 * through this method, so that the boss events are sent and the defeat
	 * explosion starts.
	 *
	 * @param damage
	 *            Amount of health to remove.
	 */
	private void damageBoss(final int damage) {
		Boss boss = this.bossManager.getBoss();
		if (boss == null || boss.isDefeated())
			return;
		if (boss.isShieldActive()) {
			AudioManager.playSFX(SHIELD_BLOCK_SOUND);
			return;
		}
		boss.takeDamage(storyBossDamage(damage));
		this.bossManager.sync();
		AudioManager.playSFX("audio/sfx/boss_hit.wav");

		// The check above stops a defeated boss from being hit again, so the
		// score is given only once per boss.
		if (boss.isDefeated()) {
			this.bossExplosionCooldown.reset();
			this.score += boss.getType().getPointValue();
			this.logger.info("Boss " + boss.getType() + " defeated, +"
					+ boss.getType().getPointValue() + " points.");
			if (this.storyStage != null)
				clearStoryEnemies();

            for (EnemyShip minion : this.summonedEnemies) {
                this.particleSystem.spawnEnemyExplosion(
                        minion.getPositionX() + minion.getWidth() / 2,
                        minion.getPositionY() + minion.getHeight() / 2);
            }

            this.summonedEnemies.clear();
		}
	}

	/**
	 * @return How much longer the boss waits between normal attacks: longer
	 *         in story mode, which is easier, and unchanged elsewhere.
	 */
	double bossAttackIntervalScale() {
		return this.storyStage == null ? 1.0
				: GameplayConfig.getStoryBossIntervalScale();
	}

	/**
	 * Lowers the damage a stronger story boss takes, so it lasts as long as
	 * a boss with more health. Other bosses take the full damage.
	 *
	 * @param damage
	 *            Damage of the hit.
	 * @return Damage the boss takes, at least 1.
	 */
	private int storyBossDamage(final int damage) {
		if (this.storyStage == null)
			return damage;
		return Math.max(1, (int) Math.round(
				damage / this.storyStage.getBossStrength()));
	}

	/**
	 * Removes every enemy left when the story boss is defeated, without
	 * score or rewards, and skips the waves that have not started.
	 */
	private void clearStoryEnemies() {
		List<EnemyShip> remaining = new ArrayList<EnemyShip>();
		for (EnemyShip enemy : this.enemyShipFormation)
			if (!enemy.isDestroyed())
				remaining.add(enemy);
		for (EnemyShip enemy : remaining)
			this.enemyShipFormation.destroy(enemy);
		this.currentWave = this.waves.size() - 1;
		this.logger.info("Story boss defeated, " + remaining.size()
				+ " remaining enemies removed.");
	}

	/**
	 * @return Boss of this stage, or null when no boss appears. Story stages
	 *         use the story data; other stages use the campaign boss stages.
	 */
	private Boss.BossType bossTypeForStage() {
		if (this.storyStage != null)
			return this.storyBoss;
		return isBossLevel(this.level) ? bossTypeForLevel(this.level) : null;
	}

	/**
	 * @return Average time between falling obstacles on this stage.
	 */
	private int fallingObstacleInterval() {
		if (this.storyStage != null && this.storyStage.hasAsteroidField())
			return ASTEROID_FIELD_INTERVAL;
		return FALLING_OBSTACLE_INTERVAL;
	}
	/**
	 * Returns the damage one player bullet deals to the boss. A RAPID shot
	 * fires its bullets one after another on the same line, so they share
	 * the damage of one shot. MULTI bullets spread apart and at most one of
	 * them can reach the boss, so each keeps the full damage.
	 *
	 * @return Damage per bullet.
	 */
	private int getBossDamagePerBullet() {
		// The current type, which can change during the game.
		if (this.ship.getCurrentBulletType() == BulletType.RAPID)
			return BOSS_DAMAGE_PER_SHOT / BulletType.RAPID.getShotCount();
		return BOSS_DAMAGE_PER_SHOT;
	}

	/**
	 * Returns the boss to update and draw. A defeated boss stays on screen
	 * until its explosion has been shown.
	 *
	 * @return Boss on screen, or null.
	 */
	private Boss getBossOnScreen() {
		Boss boss = this.bossManager.getBoss();
		if (boss == null)
			return null;
		if (boss.isDefeated() && this.bossExplosionCooldown.checkFinished())
			return null;
		return boss;
	}

	/**
	 * Checks whether the stage may end as far as the boss is concerned. The
	 * battle state comes from the BossManager, and a defeated boss leaves the
	 * screen only after its explosion has been shown.
	 *
	 * @return True when no boss is holding the stage open.
	 */
	private boolean isBossCleared() {
		return this.bossManager.getStatus().getBattleState()
				!= BossStatus.BattleState.IN_BATTLE
				&& (getBossOnScreen() == null
                        || (this.timeAttack != null && this.bossManager.getBoss().isDefeated()));
	}

	/**
	 * Picks the boss for a stage. The slow boss opens the run; the faster
	 * one, which reaches its stronger phases earlier, takes the later boss
	 * stages, so the two differ in more than their health bar.
	 *
	 * @param level
	 *            Stage number, starting at 1.
	 * @return Boss to spawn on that stage.
	 */

    /**
	static Boss.BossType bossTypeForLevel(final int level) {
		return level <= firstBossLevel() ? Boss.BossType.BOSS_A
				: Boss.BossType.BOSS_B;
	}
     */
    static Boss.BossType bossTypeForLevel(final int level) {
        if (level <= firstBossLevel())
            return Boss.BossType.BOSS_A;
        if (level == 5)
            return Boss.BossType.BOSS_B;
        return Boss.BossType.BOSS_C;
    }

	/**
	 * Returns the earliest boss stage, without relying on BOSS_LEVELS being
	 * written in ascending order.
	 *
	 * @return Smallest stage number in BOSS_LEVELS.
	 */
	static int firstBossLevel() {
		int first = Integer.MAX_VALUE;
		for (int bossLevel : BOSS_LEVELS)
			first = Math.min(first, bossLevel);
		return first;
	}

	/**
	 * Checks if a boss appears in the given stage.
	 *
	 * @param level
	 *            Stage number, starting at 1.
	 * @return True if the stage is a boss stage.
	 */
	private static boolean isBossLevel(final int level) {
		for (int bossLevel : BOSS_LEVELS)
			if (bossLevel == level)
				return true;
		return false;
	}

    public final boolean isStageCleared() {
        return this.stageCleared;
    }

    /**
	 * Gives the other systems the player's status, to read lives and shield
	 * state and to register a {@link entity.PlayerStatusListener}.
	 *
	 * @return Player status of the current game.
	 */
	public final PlayerStatus getPlayerStatus() {
		return this.playerStatus;
	}

	public final Ship getPlayerShip(final int index) {
		return this.players.get(index).getShip();
	}

	public final PlayerStatus getPlayerStatus(final int index) {
		return this.players.get(index).getStatus();
	}

	public final int getPlayerCount() {
		return this.playerCount;
	}

	/**
     * Returns a GameState object representing the status of the game.
     *
     * @return Current game state.
     */
    public final GameState getGameState() {
		return new GameState(this.level, this.score,
				getLivesRemaining(),
				this.bulletsShot, this.shipsDestroyed);
    }

    /**
     * Plays the countdown sound once each time the countdown number changes.
     *
     * @param countdown
     *            Number currently shown in the countdown.
     */
    private void playCountdownSound(final int countdown) {
        if (countdown == this.lastCountdown)
            return;
        this.lastCountdown = countdown;
        if (countdown == 0)
            AudioManager.playSFX("audio/sfx/countdown_go.wav");
        else if (countdown <= 3)
            AudioManager.playSFX("audio/sfx/countdown_beep.wav");
    }

	/**
	 * Plays the effects and sounds of a boss event and records the boss
	 * defeat (PRD 5.3, 5.4).
	 *
	 * @param event
	 *            Boss event.
	 */
	private void handleBossEvent(final BossEvent event) {
		playBossSound(event);
		if (event.getType() == BossEvent.Type.DEFEATED) {
			// Three explosions across the boss, as it is bigger than an enemy.
			for (int i = -1; i <= 1; i++)
				this.particleSystem.spawnEnemyExplosion(
						event.getPositionX() + i * Boss.WIDTH / 3,
						event.getPositionY());
			// The boss manager sends this once per boss, so the count and
			// the record are both taken from here. The score screen and the
			// saved run record read shipsDestroyed, not the defeat events,
			// so a boss went uncounted in both until this.
			this.shipsDestroyed++;
			recordBossDefeat(event.getBossType());
		}
	}

	static void playBossSound(final BossEvent event) {
		switch (event.getType()) {
		case SPAWNED:
			AudioManager.playSFX(BOSS_SPAWN_SOUND);
			break;
		case PHASE_CHANGED:
			AudioManager.playSFX(BOSS_PHASE_SOUND);
			break;
		case ATTACK:
			if (event.isSpecial())
				AudioManager.playSFX(event.isWindup()
						? BOSS_WARNING_SOUND : BOSS_SPECIAL_SOUND);
			else
				AudioManager.playSFX(BOSS_SHOT_SOUND);
			break;
		case SHIELD_ACTIVATED:
				AudioManager.playSFX(BOSS_SHIELD_ON_SOUND);
				break;
		case DEFEATED:
			AudioManager.playSFX(BOSS_EXPLOSION_SOUND);
			break;
		default:
			break;
		}
	}

    /**
     * Plays the sound that matches a player status event, such as the timed
     * shield or the slow debuff starting and ending.
     *
     * @param event
     *            Player status event.
     */
    private void handlePlayerStatusSound(final PlayerStatusEvent event) {
        switch (event.getType()) {
            case SHIELD_STARTED:
            case SHIELD_REFRESHED:
                AudioManager.playSFX("audio/sfx/status_shield_on.wav");
                break;
            case SHIELD_ENDED:
                AudioManager.playSFX("audio/sfx/status_shield_off.wav");
                break;
            case SLOW_STARTED:
            case SLOW_REFRESHED:
                AudioManager.playSFX("audio/sfx/status_slow_start.wav");
                break;
            case SLOW_ENDED:
                AudioManager.playSFX("audio/sfx/status_slow_end.wav");
                break;
            default:
                break;
        }
    }

	/**
	 * Counts the boss as a defeated enemy in the run records. The boss
	 * manager sends BossDefeated only once, so one id per boss is enough.
	 * The boss type travels on this event too, so a listener needs only one
	 * subscription for every kind of defeat.
	 *
	 * @param bossType
	 *            Kind of boss that was defeated.
	 */
	private void recordBossDefeat(final Boss.BossType bossType) {
		if (this.runSession == null)
			return;
		DefeatedEnemyType type = classifyBoss(bossType);
		this.recordsEvents.publish(new EnemyDefeatedEvent(
				this.runSession.getRunId(), newDefeatId(type), type));
	}

	/**
	 * Builds the ID of one defeat, grouped by the type of enemy defeated. The
	 * random part keeps every defeat distinct, so two enemies of the same
	 * type still count as two defeats and the ID stays usable on its own for
	 * duplicate detection. The type is handed out here rather than derived by
	 * a listener; {@link EnemyDefeatedEvent#getEnemyType()} remains the value
	 * to read, and this prefix only groups the IDs it accompanies.
	 *
	 * @param type
	 *            Classification of the defeated enemy.
	 * @return Defeat ID beginning with its enemy type.
	 */
	private static String newDefeatId(final DefeatedEnemyType type) {
		return type.name() + DEFEAT_ID_SEPARATOR + UUID.randomUUID();
	}

	/**
	 * Classifies a defeated boss for its defeat event.
	 *
	 * @param bossType
	 *            Kind of boss that was defeated.
	 * @return Classification of the boss, UNKNOWN for an unlisted kind.
	 */
	static DefeatedEnemyType classifyBoss(final Boss.BossType bossType) {
		switch (bossType) {
		    case BOSS_A:
			    return DefeatedEnemyType.BOSS_A;
		    case BOSS_B:
			    return DefeatedEnemyType.BOSS_B;
            case BOSS_C:
                return DefeatedEnemyType.BOSS_C;
			case BOSS_D:
				return DefeatedEnemyType.BOSS_D;
		    default:
			    return DefeatedEnemyType.UNKNOWN;
		}
	}

    /*
     * Each of the events below has a single place that accepts the action
     * (one volley, one hit past every protection, one launch or pickup), so
     * the ID is given right there, once per action, grouped by its kind like
     * the defeat IDs. Bullets are pooled and reused, so an ID cannot be kept
     * per bullet the way defeat IDs are kept per enemy.
     */

    /** Kind that groups the shot IDs. */
    static final String SHOT_ID_KIND = "SHOT";

    /** Sends the bullets fired this frame, if any, for accuracy statistics. */
    private void publishShotFired(final int bulletCount) {
        if (bulletCount <= 0 || this.runSession == null) return;
        this.recordsEvents.publish(new ShotFiredEvent(this.runSession.getRunId(),
                GameplayEventIds.newId(SHOT_ID_KIND), bulletCount));
    }

    /** Kind that groups the bullet hit IDs. */
    static final String BULLET_HIT_ID_KIND = "BULLET_HIT";

    /** Sends one player bullet hitting an enemy ship, for accuracy statistics. */
    private void publishBulletHit() {
        if (this.runSession == null) return;
        this.recordsEvents.publish(new BulletHitEvent(this.runSession.getRunId(),
                GameplayEventIds.newId(BULLET_HIT_ID_KIND)));
    }

    /** Sends a hit that cost the player lives; shielded hits never get here. */
    private void publishPlayerHit(final PlayerHitSource source,
            final int livesLost, final int livesRemaining) {
        if (livesLost <= 0 || this.runSession == null) return;
        this.recordsEvents.publish(new PlayerHitEvent(this.runSession.getRunId(),
                GameplayEventIds.newId(source.name()), source, livesLost,
                livesRemaining));
    }

    /** Sends one use of an item. */
    private void publishItemUsed(final UsedItemType itemType) {
        if (this.runSession == null) return;
        this.recordsEvents.publish(new ItemUsedEvent(this.runSession.getRunId(),
                GameplayEventIds.newId(itemType.name()), itemType));
    }

    /**
     * Sends the items used by being collected. An Energy Shield is used the
     * moment it is caught, but only by a ship that can hold it; a Cluster
     * Grenade is only stocked here and counts as used when launched.
     */
    private void publishItemsUsed(final List<Item> collected, final Ship ship) {
        for (Item item : collected)
            if (item instanceof ShieldItem && ship.isShieldActive())
                publishItemUsed(UsedItemType.ENERGY_SHIELD);
    }

	private void recordEnemyDefeat(final EnemyShip enemy) {
		if ((this.summonedEnemies != null && this.summonedEnemies.contains(enemy))
				|| this.runSession == null) {
			return;
		}
		String id = this.defeatIds.get(enemy);
		if (id == null) { id = newDefeatId(enemy.getDefeatedType()); this.defeatIds.put(enemy, id); }
		this.recordsEvents.publish(new EnemyDefeatedEvent(this.runSession.getRunId(), id,
				enemy.getDefeatedType()));
	}

    /**
     * Gives other systems the shared gameplay event bus. Player status events
     * are published here too, so a listener can subscribe with
     * {@code subscribe(PlayerStatusEvent.class, listener)} and read the type
     * from {@link entity.PlayerStatusEvent#getType()}.
     *
     * @return Shared gameplay event publisher.
     */
    public GameplayEventPublisher getGameplayEvents() { return this.recordsEvents; }

    public String getActiveRunId() { return this.runSession == null ? null : this.runSession.getRunId(); }

    /** Keep the extra movement inside the existing playfield borders. */
    private void clampShipPosition() {
        for (PlayerSlot player : this.players) {
            Ship playerShip = player.getShip();
            playerShip.setPositionX(clampShipPositionX(playerShip.getPositionX(),
                    playerShip.getWidth(), this.width));
        }
	}

	/**
	 * Keeps a ship position inside the playfield borders.
	 *
	 * @param positionX
	 *            Position to clamp, on the X axis.
	 * @param shipWidth
	 *            Width of the ship being moved.
	 * @param screenWidth
	 *            Width of the playfield.
	 * @return Clamped position on the X axis.
	 */
	static int clampShipPositionX(final int positionX, final int shipWidth,
			final int screenWidth) {
		final int leftBoundary = 1;
		final int rightBoundary = Math.max(leftBoundary,
				screenWidth - shipWidth - 1);
		return Math.max(leftBoundary, Math.min(positionX, rightBoundary));
    }

    /**
     * Prevents one physical key from both moving/firing a player and launching
     * a grenade. The check spans both players because the keyboard is shared.
     */
    private boolean isGrenadeKeyBoundElsewhere(
            final PlayerSlot grenadePlayer) {
        int grenadeKey = grenadePlayer.getKey(ControlsConfig.GRENADE);
        for (PlayerSlot player : this.players) {
            if (player.getKey(ControlsConfig.LEFT) == grenadeKey
                    || player.getKey(ControlsConfig.RIGHT) == grenadeKey
                    || player.getKey(ControlsConfig.FIRE) == grenadeKey)
                return true;
        }
        return false;
    }

    /**
     * Each destroyed enemy ship has a chance to drop a Cluster Grenade item.
     *
     * @param enemyShip
     *            Destroyed enemy ship.
     */
    private void tryDropGrenade(final EnemyShip enemyShip) {
        if (!this.levelFinished
                && this.grenadeDropsThisLevel < MAX_GRENADE_DROPS_PER_LEVEL
                && this.itemDropRandom.nextDouble() < GRENADE_DROP_CHANCE) {
            this.grenadeDropsThisLevel++;
            this.itemManager.spawnItem(new ClusterGrenadeItem(
                    enemyShip.getPositionX(), enemyShip.getPositionY()));
            this.logger.info("Grenade item dropped from enemy ship.");
        }
    }

    /**
     * Moves the grenades and their fragments. Once the stage has ended,
     * they keep moving but no longer hit anything, so the score, kill count
     * and defeat events cannot change after the end.
     */
    private void manageGrenades() {
        boolean canHit = !this.levelFinished;

        Set<Grenade> exploded = new HashSet<Grenade>();
        for (Grenade grenade : this.grenades) {
            grenade.update();

            boolean impact = false;
            if (canHit) {
                for (EnemyShip enemyShip : this.enemyShipFormation)
                    if (!enemyShip.isDestroyed()
                            && checkCollision(grenade, enemyShip)) {
                        impact = true;
                        break;
                    }
                if (!impact && this.bonusEnemyManager.isActive()
                        && checkCollision(grenade,
                                this.bonusEnemyManager.getShip()))
                    impact = true;
            }

            if (impact || grenade.hasReachedAltitude()) {
                exploded.add(grenade);
                this.fragments.addAll(GrenadeFragment.explode(
                        grenade.getCenterX(), grenade.getCenterY()));
				AudioManager.playSFX("audio/sfx/item_grenade_explode.wav");
            }
        }
        this.grenades.removeAll(exploded);

        boolean enemyDestroyed = false;
        Set<GrenadeFragment> expired = new HashSet<GrenadeFragment>();
        for (GrenadeFragment fragment : this.fragments) {
            fragment.update();

            boolean used = false;
            if (canHit) {
                for (EnemyShip enemyShip : this.enemyShipFormation)
                    if (!enemyShip.isDestroyed()
                            && checkCollision(fragment, enemyShip)) {
                        used = true;
                        // Story grunts normally have one HP. A scaled grunt may survive the fragment.
                        if (this.modifiers.enemyHpMultiplier() > 1.0 && enemyShip.getHealth() > 1) {
                            enemyShip.hit();
                            break;
                        }
                        this.score += enemyShip.getPointValue();
                        floatingScoreHud.show(enemyShip);
                        this.shipsDestroyed++;
                        recordEnemyDefeat(enemyShip);
                        this.enemyShipFormation.destroy(enemyShip);
                        enemyDestroyed = true;
                        this.particleSystem.spawnEnemyExplosion(
                                enemyShip.getPositionX()
                                        + enemyShip.getWidth() / 2,
                                enemyShip.getPositionY()
                                        + enemyShip.getHeight() / 2);
                        tryDropGrenade(enemyShip);
                        break;
                    }

                EnemyShip bonusShip = this.bonusEnemyManager.getShip();
                if (!used && this.bonusEnemyManager.isActive()
                        && checkCollision(fragment, bonusShip)) {
                    hitBonusShip(bonusShip);
                    used = true;
                }
            }

            if (used || fragment.isExpired())
                expired.add(fragment);
        }
        this.fragments.removeAll(expired);

        if (enemyDestroyed) {
            AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
        }
    }
}
