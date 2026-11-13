package screen;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.function.UnaryOperator;

import boss.*;
import campaign.CampaignStageData;
import engine.DifficultyAdjustWhilePlaying;
import entity.item.ItemManager;
import entity.item.ShieldItem;
import engine.Cooldown;
import engine.CampaignUpgrades;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.GameplayConfig;
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
import records.RunSession;
import records.RecordsService;
import records.Achievement;
import story.StoryProgress;
import story.StoryStage;
import story.StoryStageData;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import event.LevelCompletedEvent;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.UUID;
import entity.Grenade;
import entity.GrenadeFragment;
import entity.item.BuffHud;
import entity.item.ClusterGrenadeItem;

/**
 * Implements the game screen, where the action happens.
 * Plays sound effects for gameplay events.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class GameScreen extends Screen {

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
	private static final int BOSS_DAMAGE_PER_SHOT = 50;
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
	/** Stages where a boss appears (agreed with the team, may change). */
	static final int[] BOSS_LEVELS = { 3, 5, 7 };
    /** Chance for a destroyed enemy ship to drop a Cluster Grenade item. */
    private static final double GRENADE_DROP_CHANCE = 0.15;
    /** Fixed altitude (Y) where a grenade explodes if it hits nothing. */
    private static final int GRENADE_DETONATION_Y = 200;
    /** Maximum number of Cluster Grenade items dropped in one level. */
    private static final int MAX_GRENADE_DROPS_PER_LEVEL = 2;

	/** Time the defeated boss stays on screen as an explosion. */
	private Cooldown bossExplosionCooldown;
	/** Boss of the level, its attacks, events and links to other systems.
	 * The only source of truth for the boss. */
	private BossManager bossManager;
	/** Set of all bullets fired by the boss. */
	private Set<BossBullet> bossBullets;

    private ItemManager itemManager;
    private final Random itemDropRandom = new Random();
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
    private boolean grenadeKeyPressed = false;
    /** Number of Cluster Grenade items dropped in this level. */
    private int grenadeDropsThisLevel = 0;

	/** Player ship type selected before the stage started. */
	private ShipType shipType;
	private final CampaignUpgrades campaignUpgrades;
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
    /** Checks if the level is finished. */
    private boolean levelFinished;
    /** Checks if a bonus life is received. */
    private boolean bonusLife;
    /** Last countdown number that played a sound. */
    private int lastCountdown = -1;
	private RunSession runSession;
    private RecordsService recordsService;
    private Achievement visibleAchievement;
    private long achievementNoticeUntil;
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
        this(gameState, waves, bonusLife, width, height, fps, shipType, runSession,
                CampaignUpgrades.none());
    }

    public GameScreen(final GameState gameState, final GameSettings gameSettings,
            final boolean bonusLife, final int width, final int height, final int fps,
            final ShipType shipType, final RunSession runSession,
            final CampaignUpgrades campaignUpgrades) {
        this(gameState, Collections.singletonList(gameSettings), bonusLife,
                width, height, fps, shipType, runSession, campaignUpgrades);
    }

    /** Campaign-only entry point; legacy and custom-level constructors keep base stats. */
    public GameScreen(final GameState gameState, final List<GameSettings> waves,
            final boolean bonusLife, final int width, final int height, final int fps,
            final ShipType shipType, final RunSession runSession,
            final CampaignUpgrades campaignUpgrades) {
        super(width, height, fps);
        if (campaignUpgrades == null) {
            throw new IllegalArgumentException("Campaign upgrades cannot be null.");
        }
        this.campaignUpgrades = campaignUpgrades;

		this.runSession = runSession;
		this.recordsService = Core.getRecordsService();
		this.recordsEvents.subscribe(EnemyDefeatedEvent.class,
				new GameplayEventListener<EnemyDefeatedEvent>() {
			public void onEvent(final EnemyDefeatedEvent event) {
				if (recordsService != null)
					recordsService.enemyDefeated(event.getRunId(),
							event.getEventId());
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
		this.playerStatus = new PlayerStatus(
				Math.min(gameState.getLivesRemaining(),
						this.campaignUpgrades.getMaxLives(this.shipType)),
				this.campaignUpgrades.getMaxLives(this.shipType),
				this.shipType.isShieldCapable());
        if (this.bonusLife)
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
	 * @param campaignUpgrades
	 *            Permanent upgrades of the player.
	 * @return Screen of the story stage.
	 */
	public static GameScreen forStoryStage(final GameState gameState,
			final StoryProgress progress,
			final UnaryOperator<GameSettings> difficulty,
			final boolean bonusLife, final int width, final int height,
			final int fps, final ShipType shipType,
			final RunSession runSession,
			final CampaignUpgrades campaignUpgrades) {
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
				width, height, fps, shipType, runSession, campaignUpgrades);
		screen.customWaves = storyWaves;
		screen.storyStage = stage;
		screen.storyBoss = StoryProgress.isBossStage(stageNumber)
				? progress.bossForStage(stageNumber) : null;
		return screen;
	}

	private EnemyShipFormation createFormation() {
		EnemyShipFormation formation = this.customWaves == null
				? new EnemyShipFormation(this.gameSettings, this.level)
				: new EnemyShipFormation(this.gameSettings, this.level, this.customWaves.get(this.currentWave));
		formation.attach(this);
		return formation;
	}

	/**
	 * Initializes basic screen properties, and adds necessary elements.
	 */
	public final void initialize() {
		super.initialize();

		enemyShipFormation = createFormation();

		this.ship = new Ship(this.width / 2, this.height - 30,
				this.shipType);
        this.campaignUpgrades.initializeShip(this.ship);
		// The hangar colour has to go through setBaseColor: the ship resets
		// its colour to the base one every frame once the damage flash is
		// over, so a plain setColour is wiped back to the default green.
		this.ship.setBaseColor(ShipCustomization.getColor());
		this.ship.setBaseColor(ShipCustomization.getColor());
		// Story mode uses the normal weapon on every ship for now.
		if (this.storyStage != null)
			this.ship.setBulletType(BulletType.NORMAL);
        this.itemManager = new ItemManager(this.height);

        this.shieldDroppedThisLevel = false;

        this.grenades = new HashSet<Grenade>();
        this.fragments = new HashSet<GrenadeFragment>();
        this.grenadeKeyPressed = false;
        this.grenadeDropsThisLevel = 0;

		this.bonusEnemyManager = new BonusEnemyManager();
		this.screenFinishedCooldown = Core.getCooldown(SCREEN_CHANGE_INTERVAL);
        this.bullets = new HashSet<Bullet>();
		this.fallingObstacles = new HashSet<FallingObstacle>();
		this.fallingObstacleCooldown = Core.getVariableCooldown(
				fallingObstacleInterval(), FALLING_OBSTACLE_VARIANCE);
		this.fallingObstacleCooldown.reset();

		this.bossBullets = new HashSet<BossBullet>();
        this.summonedEnemies = new ArrayList<EnemyShip>();
		this.bossManager = new BossManager();
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
		// Same for the player's own status events.
		this.playerStatus.addListener(new LoggingPlayerStatusListener());
		// Forward them onto the shared gameplay bus as well, so a system that
		// already listens there, such as sound effects, does not have to know
		// about PlayerStatus to receive them.
		this.playerStatus.addListener(new PlayerStatusListener() {
			public void onPlayerStatusEvent(final PlayerStatusEvent event) {
				recordsEvents.publish(event);
                handlePlayerStatusSound(event);
			}
		});
		// The boss appears only on boss stages.
		Boss.BossType bossType = bossTypeForStage();
		if (bossType != null)
			this.bossManager.spawn(new BossSpawnRequest(
					bossType,
					this.width / 2 - Boss.WIDTH / 2, 78,
					this.level, 0));
		this.bossExplosionCooldown = Core.getCooldown(BOSS_EXPLOSION_TIME);

        // Special input delay / countdown.
        this.gameStartTime = System.currentTimeMillis();
        this.inputDelay = Core.getCooldown(INPUT_DELAY);
        this.inputDelay.reset();

		if (this.bonusLife) {
			AudioManager.playSFX("audio/sfx/bonus_life.wav");
		}

		// The last life is checked first, so the urgent track also plays on
		// boss stages.
		if (this.playerStatus.getLives() == 1) {
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

    /**
     * Starts the action.
     *
     * @return Next screen code.
     */
    public final int run() {
        try { super.run(); }
        finally { if (this.runSession != null) this.runSession.stopActiveSegment(System.nanoTime()); }

        // Life bonus is only awarded when the stage was cleared.
        if (this.stageCleared)
			this.score += LIFE_SCORE * (this.playerStatus.getLives() - 1);
        this.logger.info("Screen cleared with a score of " + this.score);

        return this.returnCode;
    }

	/**
	 * Updates the elements on screen, checks for events, and plays the bonus
	 * enemy appearance sound.
	 */
	protected final void update() {
		super.update();
		if (this.recordsService != null) {
			long now = System.currentTimeMillis();
			if (this.visibleAchievement == null || now >= this.achievementNoticeUntil) {
				this.visibleAchievement = this.recordsService.pollNotification();
				if (this.visibleAchievement != null) {
					AudioManager.playSFX("audio/sfx/achievement_unlock.wav");
				}
				this.achievementNoticeUntil = this.visibleAchievement == null ? 0 : now + 3000;
			}
		}

		// Outside the input-delay check: a shield that runs out has to be
		// announced even while the level is finishing.
		this.playerStatus.update();
		// Sends the current number of lives remaining to the DifficultyAdjustWhilePlaying class
		DifficultyAdjustWhilePlaying.getInstance().update(this.playerStatus.getLives());
		// Reconcile shields active outside the item-collision path.
		clearSlowIfEnergyShieldActive();

        if (this.inputDelay.checkFinished() && !this.levelFinished) {
            if (this.runSession != null) this.runSession.startActiveSegment(System.nanoTime());
            if (this.runSession != null && this.recordsService != null)
                this.recordsService.activeDuration(this.runSession.getRunId(),
                        this.runSession.getActiveDurationMillis(System.nanoTime()));

            if (!this.ship.isDestroyed()) {
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
					if (fired > 0)
						AudioManager.playSFX("audio/sfx/player_shoot.wav");
				}

                // Cluster Grenade launch with the G key. Ignored if G is
                // also bound to movement or firing, so one press cannot do
                // both and waste a grenade.
                boolean grenadeKeyDown = !isGrenadeKeyBoundElsewhere()
                        && inputManager.isKeyDown(KeyEvent.VK_G);
                if (grenadeKeyDown && !this.grenadeKeyPressed) {
                    if (this.ship.launchGrenade(this.grenades, GRENADE_DETONATION_Y))
                        AudioManager.playSFX("audio/sfx/item_grenade_launch.wav");
                }
                this.grenadeKeyPressed = grenadeKeyDown;

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

			this.bonusEnemyManager.update(this.width);

            Boss boss = getBossOnScreen();
            if (boss != null) {
                boss.update(1, this.width - 1);
                List<BossAttackSystem.SummonRequest> summons =
                        new ArrayList<>();
                this.bossManager.update(
                        this.ship.getPositionX() + this.ship.getWidth() / 2,
                        this.ship.getPositionY(), this.bossBullets, summons);

                for (BossAttackSystem.SummonRequest req : summons) {
                    EnemyShip minion = EnemyShip.createByType(
                            req.getEnemyType(), req.getPositionX(), req.getPositionY());
                    Cooldown shootCooldown = this.enemyShipFormation.createShooterCooldown();
                    shootCooldown.reset();
                    minion.setShootingCooldown(shootCooldown);
                    this.summonedEnemies.add(minion);
                }
            }

            for (Iterator<EnemyShip> it = this.summonedEnemies.iterator(); it.hasNext();) {
                EnemyShip minion = it.next();
                minion.update();
                minion.move(0, 2);

                if (!this.ship.isDestroyed() && minion.canShoot()) {
                    if (minion.getDefeatedType() == DefeatedEnemyType.B) {
                        // B: player 자리에 null을 넘겨 수직 일자 발사
                        this.enemyShipFormation.shootFrom(minion, this.bullets, null);
                    } else if (minion.getDefeatedType() == DefeatedEnemyType.C) {
                        // C: player 자리에 this.ship을 넘겨 플레이어 조준 발사
                        this.enemyShipFormation.shootFrom(minion, this.bullets, this.ship);
                    }
                }

                if (!this.ship.isDestroyed() && checkCollision(minion, this.ship)) {
                    minion.destroy();
                    this.particleSystem.spawnEnemyExplosion(
                            minion.getPositionX() + minion.getWidth() / 2,
                            minion.getPositionY() + minion.getHeight() / 2);
                    damagePlayer(1, "Collided with summoned enemy");
                    it.remove();
                } else if (minion.isDestroyed() || minion.getPositionY() > this.height) {
                    it.remove();
                }
            }

			if (!this.ship.isDestroyed())
				this.bulletsShot += this.ship.updateBurst(this.bullets);

            this.enemyShipFormation.update();
			this.enemyShipFormation.shoot(this.bullets, this.ship);
        }

        this.ship.update();

        if (this.inputDelay.checkFinished() && !this.levelFinished) {
            this.itemManager.update(this.ship);

            if (!this.ship.isDestroyed()) {
                this.itemManager.checkCollisions(this.ship);
				// Clear immediately in the frame an Energy Shield is collected.
				clearSlowIfEnergyShieldActive();
            }

            clampShipPosition();
        }

		if (!updateFallingObstacles())
			manageFallingObstacleCollisions();
        manageCollisions();
        manageGrenades();

        cleanBullets();
        this.particleSystem.update();
        draw();

        advanceStageIfReady();

        if (this.levelFinished && this.screenFinishedCooldown.checkFinished())
            this.isRunning = false;
    }

    /** Progression shared by official and custom stages. */
    void advanceStageIfReady() {
        if (!this.levelFinished) {
            if (!this.playerStatus.isAlive()) {
                this.levelFinished = true;
                this.stageCleared = false;
                AudioManager.playSFX("audio/sfx/game_over.wav");
                this.screenFinishedCooldown.reset();
                this.logger.info("Stage " + this.level + " failed.");
            } else if (this.enemyShipFormation.isEmpty()
                    && this.summonedEnemies.isEmpty() && isBossCleared()) {
                if (this.currentWave < this.waves.size() - 1) {
                    startNextWave();
                } else {
                    this.levelFinished = true;
                    this.stageCleared = true;
                    AudioManager.playSFX("audio/sfx/level_clear.wav");
                    this.screenFinishedCooldown.reset();
                    this.logger.info("Stage " + this.level + " cleared.");
                    publishLevelCompleted();

                    // Notify the difficulty adjustment system when a level is cleared.
                    DifficultyAdjustWhilePlaying.getInstance().onLevelCleared();
                }
            }
        }

        if (this.levelFinished && this.runSession != null)
            this.runSession.stopActiveSegment(System.nanoTime());
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
		AudioManager.playSFX("audio/sfx/wave_transition.wav");
		this.logger.info("Stage " + this.level + " wave " + (this.currentWave + 1) +
				" of " + this.waves.size() + " started.");
	}

	/**
     * Draws the elements associated with the screen.
     */
    private void draw() {
        drawManager.initDrawing(this);

		for (FallingObstacle obstacle : this.fallingObstacles)
			drawManager.drawEntity(obstacle, obstacle.getPositionX(),
					obstacle.getPositionY());

		drawManager.drawEntity(this.ship, this.ship.getPositionX(),
				this.ship.getPositionY());

        if (this.ship.isShieldActive() && !this.ship.isDestroyed()) {
            drawManager.drawShield(this, this.ship);
        }

		EnemyShip bonusShip = this.bonusEnemyManager.getShip();
		if (bonusShip != null)
			drawManager.drawEntity(bonusShip, bonusShip.getPositionX(),
					bonusShip.getPositionY());

		Boss boss = getBossOnScreen();
		if (boss != null)
			drawManager.drawEntity(boss, boss.getPositionX(),
					boss.getPositionY());

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
		drawManager.drawLives(this, this.playerStatus.getLives(),
				this.playerStatus.getMaxLives());
		drawManager.drawLevel(this, this.level, CampaignStageData.getTotalStages());
		try { // Includes rewards awaiting settlement; read every frame.
			drawManager.drawCredits(this, Core.getAssetHudSnapshot().getCredits());
		} catch (IllegalStateException e) {
			// Assets are not initialized (e.g. screen-only tests): skip credits.
		}

		// Item HUD: grenades left, active buffs and their time left.
		drawManager.drawGrenadeCount(this, this.ship.getGrenadeCount());
		drawManager.drawBuffs(this, BuffHud.buildLines(
				this.itemManager.getBuffManager().getActiveBuffsRemainingTime(),
				System.currentTimeMillis()));

        drawManager.drawHorizontalLine(this, SEPARATION_LINE_HEIGHT - 1);

        // Countdown to game start.
        if (!this.inputDelay.checkFinished()) {
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
        drawManager.drawParticles(this.particleSystem);

        if (this.visibleAchievement != null)
            drawManager.drawAchievementNotice(this, this.visibleAchievement);
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
            if (checkCollision(obstacle, this.ship)) {
                // Remove first so a single obstacle can trigger only one collision.
                iterator.remove();
                this.fallingObstacleCooldown.reset();
                if (!this.ship.isInvulnerable()) {
                    if (this.ship.isShieldActive()) {
                        AudioManager.playSFX("audio/sfx/item_shield_block.wav");
                    } else {
                        AudioManager.playSFX("audio/sfx/obstacle_hit.wav");
                        this.playerStatus.applySlow(this.ship);
                    }
                }
            }
        }
    }

    /** Clears an existing slow as soon as the Energy Shield item is active. */
    private void clearSlowIfEnergyShieldActive() {
        if (this.ship.isShieldActive())
            this.playerStatus.clearSlowDebuff();
    }

    /**
     * Manages collisions between bullets and ships.
     */
    private void manageCollisions() {
        if (this.levelFinished)
            return;
        Set<Bullet> recyclable = new HashSet<Bullet>();
        for (Bullet bullet : this.bullets) {
            if (bullet.isEnemyBullet()) {
                if (checkCollision(bullet, this.ship) && !this.levelFinished) {
                    recyclable.add(bullet);
                    damagePlayer(1, "Hit on player ship");
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
                    recordEnemyDefeat(bonusShip);
                    floatingScoreHud.show(bonusShip);
                    this.score += this.bonusEnemyManager.destroy();
                    this.shipsDestroyed++;
                    this.particleSystem.spawnEnemyExplosion(
                            bonusShip.getPositionX() + bonusShip.getWidth() / 2,
                            bonusShip.getPositionY() + bonusShip.getHeight() / 2);
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
            }
        }
        this.bullets.removeAll(recyclable);
        BulletPool.recycle(recyclable);

        Set<BossBullet> hitBossBullets = new HashSet<BossBullet>();
        for (BossBullet bossBullet : this.bossBullets) {
            if (checkCollision(bossBullet, this.ship) && !this.levelFinished) {
                hitBossBullets.add(bossBullet);
                damagePlayer(bossBullet.getDamage(),
                        "Hit on player ship by the boss");
            }
        }
        this.bossBullets.removeAll(hitBossBullets);
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
		// Still inside the recovery period from an earlier hit. This outlasts
		// the time the ship cannot move, so that the player is not killed by
		// the shots that were aimed at them while they were held in place.
		if (this.ship.isInvulnerable())
			return false;
		if (this.ship.isShieldActive()) {
			this.logger.info("Shot absorbed by Energy Shield.");
			AudioManager.playSFX("audio/sfx/item_shield_block.wav");
			// The Energy Shield belongs to Ship, so PlayerStatus cannot see
			// this hit by itself. Announce it anyway, or a listener would
			// hear a blocked hit only when the timed shield was the one that
			// blocked it.
			this.playerStatus.announceDamageBlocked(damage);
			return false;
		}
		// PlayerStatus owns the timed shield, so its answer decides whether
		// the hit lands at all; the effects below must not run otherwise.
        if (!this.playerStatus.takeDamage(damage)) {
            if (this.playerStatus.isShieldActive())
                AudioManager.playSFX("audio/sfx/item_shield_block.wav");
            return false;
        }
		AudioManager.playSFX("audio/sfx/player_hit.wav");

		this.ship.destroy();
		this.ship.triggerDamageFeedback();
		this.particleSystem.spawnPlayerExplosion(
				this.ship.getPositionX() + this.ship.getWidth() / 2,
				this.ship.getPositionY() + this.ship.getHeight() / 2);
		this.itemManager.getBuffManager().resetAllBuffs(this.ship);
		if (this.playerStatus.getLives() == 1 && !this.isLowHealthBGM) {
			AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
			this.isLowHealthBGM = true;
		}
		this.logger.info(logMessage + ", " + this.playerStatus.getLives()
				+ " lives remaining.");
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
				&& getBossOnScreen() == null;
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

	/**
     * Returns a GameState object representing the status of the game.
     *
     * @return Current game state.
     */
    public final GameState getGameState() {
		return new GameState(this.level, this.score,
				this.playerStatus.getLives(),
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
		case DEFEATED:
			AudioManager.playSFX(BOSS_EXPLOSION_SOUND);
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
	private static DefeatedEnemyType classifyBoss(final Boss.BossType bossType) {
		switch (bossType) {
		    case BOSS_A:
			    return DefeatedEnemyType.BOSS_A;
		    case BOSS_B:
			    return DefeatedEnemyType.BOSS_B;
            case BOSS_C:
                return DefeatedEnemyType.BOSS_C;
		    default:
			    return DefeatedEnemyType.UNKNOWN;
		}
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
		this.ship.setPositionX(clampShipPositionX(this.ship.getPositionX(),
				this.ship.getWidth(), this.width));
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
     * Checks whether the G key is also bound to movement or firing.
     *
     * @return True if G is used by another control.
     */
    private boolean isGrenadeKeyBoundElsewhere() {
        return ControlsConfig.getKey(ControlsConfig.LEFT) == KeyEvent.VK_G
                || ControlsConfig.getKey(ControlsConfig.RIGHT) == KeyEvent.VK_G
                || ControlsConfig.getKey(ControlsConfig.FIRE) == KeyEvent.VK_G;
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
                        used = true;
                        break;
                    }

                EnemyShip bonusShip = this.bonusEnemyManager.getShip();
                if (!used && this.bonusEnemyManager.isActive()
                        && checkCollision(fragment, bonusShip)) {
                    recordEnemyDefeat(bonusShip);
                    floatingScoreHud.show(bonusShip);
                    this.score += this.bonusEnemyManager.destroy();
                    this.shipsDestroyed++;
                    this.particleSystem.spawnEnemyExplosion(
                            bonusShip.getPositionX() + bonusShip.getWidth() / 2,
                            bonusShip.getPositionY() + bonusShip.getHeight() / 2);
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
