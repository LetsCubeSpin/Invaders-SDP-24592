package screen;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import vfx.ParticleSystem;

import audio.AudioManager;
import records.RunSession;
import records.Achievement;
import records.RecordsService;
import event.EnemyDefeatedEvent;
import event.GameplayEventIds;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import event.PlayerHitEvent;
import event.PlayerHitSource;
import event.ShotFiredEvent;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.UUID;

import engine.Cooldown;
import engine.ControlsConfig;
import engine.Core;
import engine.DrawManager.SpriteType;
import engine.GameState;
import entity.Bullet;
import entity.BulletPool;
import entity.Entity;
import entity.PlayerStatus;
import entity.Ship;
import entity.ZigzagEnemyShip;
import engine.DifficultyAdjustWhilePlaying;

/**
 * Implements the infinite mode game screen: zigzagging enemies keep spawning
 * from the top of the screen until the player runs out of lives or quits.
 */
public class InfiniteGameScreen extends Screen {

	private static final int SEPARATION_LINE_HEIGHT = 40;
	private static final int SIDE_MARGIN = 20;
	private static final int INPUT_DELAY = 6000;
	private static final int ENEMY_WIDTH = 12 * 2;
	private static final int INITIAL_SPAWN_INTERVAL = 1000;
	private static final int MINIMUM_SPAWN_INTERVAL = 300;
	private static final int SPAWN_INTERVAL_STEP = 10;
	private static final int KILLS_PER_SPEED_UP = 20;
	private static final int MAXIMUM_FALL_SPEED = 3;
	private static final int SCREEN_CHANGE_INTERVAL = 1500;
	private static final int ENEMY_BULLET_SPEED = 4;
	private static final double ENEMY_FIRE_CHANCE = 0.003;

	private int getEnemyBulletSpeed() {
		return (int) DifficultyAdjustWhilePlaying.getInstance().adjustEnemyBulletSpeed(ENEMY_BULLET_SPEED);
	}

	private Ship ship;
	private PlayerStatus playerStatus;
	private List<PlayerSlot> players;
	private List<ZigzagEnemyShip> enemies;
	private Set<Bullet> bullets;
	private Set<Bullet> enemyBullets;
	private Cooldown spawnCooldown;
	private Cooldown screenFinishedCooldown;
	private int score;
	private int lives;
	/** Lives the player started with. */
	private final int maxLives;
	/** Total bullets shot by the player. */
	private int bulletsShot;
	private int shipsDestroyed;
	/** Number of players selected for this run. */
	private final int playerCount;
	private boolean gameOver;
	/** Prevents replaying the urgent track while the team remains on one shared life. */
	private boolean lowLivesMusicStarted;
	private RunSession runSession;
	private boolean abandoned;
	private RecordsService recordsService;
	private Achievement visibleAchievement;
	private long achievementNoticeUntil;
	private final GameplayEventPublisher recordsEvents = new GameplayEventPublisher();
	private final Map<Entity,String> defeatIds = new IdentityHashMap<Entity,String>();
	private ParticleSystem particleSystem;
	private long gameStartTime;
	private int lastCountdown = -1;

	public InfiniteGameScreen(final GameState gameState, final int width,
			final int height, final int fps) {
		this(gameState, width, height, fps, null);
	}

	public InfiniteGameScreen(final GameState gameState, final int width,
			final int height, final int fps, final RunSession runSession) {
		this(gameState, width, height, fps, runSession, 1);
	}

	public InfiniteGameScreen(final GameState gameState, final int width,
			final int height, final int fps, final RunSession runSession,
			final int playerCount) {
		super(width, height, fps);
		if (playerCount != 1 && playerCount != 2)
			throw new IllegalArgumentException("Player count must be 1 or 2.");
		this.runSession = runSession;
		this.playerCount = playerCount;
		this.recordsService = Core.getRecordsService();
		this.recordsEvents.subscribe(EnemyDefeatedEvent.class,
				new GameplayEventListener<EnemyDefeatedEvent>() {
			public void onEvent(final EnemyDefeatedEvent event) {
				if (recordsService != null)
					recordsService.enemyDefeated(event.getRunId(), event.getEventId());
			}
		});

		this.score = gameState.getScore();
		// 2P runs share at most two lives; 1P keeps the existing run value.
		this.lives = playerCount == 2
				? Math.min(2, gameState.getLivesRemaining())
				: gameState.getLivesRemaining();
		this.maxLives = playerCount == 2 ? 2 : this.lives;
		// In 2P, each status tracks one ship's life, not the team's life pool.
		int personalLives = playerCount == 2 ? 1 : this.lives;
		this.playerStatus = new PlayerStatus(personalLives,
				Math.max(1, personalLives), false);
		this.bulletsShot = gameState.getBulletsShot();
		this.shipsDestroyed = gameState.getShipsDestroyed();
	}

	public final void initialize() {
		super.initialize();

		initializePlayerSlots();
		this.enemies = new ArrayList<ZigzagEnemyShip>();
		this.bullets = new HashSet<Bullet>();
		this.enemyBullets = new HashSet<Bullet>();
		this.spawnCooldown = Core.getCooldown(INITIAL_SPAWN_INTERVAL);
		this.spawnCooldown.reset();
		this.screenFinishedCooldown = Core.getCooldown(SCREEN_CHANGE_INTERVAL);
		this.particleSystem = new ParticleSystem();
		this.gameStartTime = System.currentTimeMillis();
		this.lastCountdown = -1;
		this.inputDelay = Core.getCooldown(INPUT_DELAY);
		this.inputDelay.reset();

		AudioManager.playBGM("audio/bgm/music_game.wav");
	}

	/** Creates one slot per selected player while preserving the original center spawn in 1P. */
	private void initializePlayerSlots() {
		this.players = new ArrayList<PlayerSlot>();
		for (int index = 0; index < this.playerCount; index++) {
			Ship playerShip = createPlayerShip(index);
			PlayerStatus status = index == 0 ? this.playerStatus
					: new PlayerStatus(1, 1, false);
			this.players.add(new PlayerSlot(index, playerShip, status));
		}
		syncPrimaryPlayer();
	}

	private Ship createPlayerShip(final int index) {
		int positionX = this.width / 2;
		if (this.playerCount == 2)
			positionX += index == 0 ? -20 : 20;
		return new Ship(positionX, this.height - 30);
	}

	private void syncPrimaryPlayer() {
		PlayerSlot primary = this.players.get(0);
		this.ship = primary.getShip();
		this.playerStatus = primary.getStatus();
	}

	/** A shared life is spent only after all player ships have been eliminated. */
	private void resolveSharedPlayerDefeat() {
		if (this.playerCount != 2)
			return;

		if (!hasLivingPlayer()) {
			this.lives = Math.max(0, this.lives - 1);
			// Remaining team lives respawn both players; zero ends the run.
			if (this.lives > 0) {
				for (int index = 0; index < this.players.size(); index++) {
					this.players.get(index).respawn(createPlayerShip(index),
							new PlayerStatus(1, 1, false));
				}
				syncPrimaryPlayer();
			}
		}

		if (this.lives == 1 && getLivingPlayerCount() == 1
				&& !this.lowLivesMusicStarted) {
			AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
			this.lowLivesMusicStarted = true;
		}
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

	public final int run() {
		try { super.run(); }
		finally { if (runSession != null) runSession.stopActiveSegment(System.nanoTime()); }

		this.logger.info("Infinite mode ended with a score of " + this.score);
		return this.returnCode;
	}

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


		// Difficulty adjustment follows the shared team pool in 2P mode.
		DifficultyAdjustWhilePlaying.getInstance().update(this.lives);
		// Personal statuses update independently; they never own the shared team lives.
		for (PlayerSlot player : this.players)
			player.getStatus().update();

		if (this.inputDelay.checkFinished() && !this.gameOver) {
			if (runSession != null) runSession.startActiveSegment(System.nanoTime());
			if (runSession != null && recordsService != null)
				recordsService.activeDuration(runSession.getRunId(), runSession.getActiveDurationMillis(System.nanoTime()));
			if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				this.abandoned = true;
				if (recordsService != null && runSession != null) recordsService.abandonRun(runSession.getRunId());
				if (runSession != null) runSession.stopActiveSegment(System.nanoTime());
				// Leaving with Esc plays the same back sound as the menu screens.
				AudioManager.playSFX("audio/sfx/menu_back.wav");
				this.isRunning = false;
				return;
			}

			updatePlayerControls();
			spawnEnemy();
			moveEnemies();
			shootEnemies();
		}

		// Every ship keeps its own damage cooldown and visual state, including a ship that is out.
		for (PlayerSlot player : this.players)
			player.getShip().update();
		manageCollisions();
		resolveSharedPlayerDefeat();
		cleanBullets();
		this.particleSystem.update();
		draw();

		if (this.lives == 0 && !this.gameOver) {
			this.gameOver = true;
			AudioManager.playSFX("audio/sfx/game_over.wav");
			if (runSession != null) runSession.stopActiveSegment(System.nanoTime());
			this.screenFinishedCooldown.reset();
		}

		if (this.gameOver && this.screenFinishedCooldown.checkFinished())
			this.isRunning = false;
	}

	void updatePlayerControls() {
		// Read controls from each slot so P1 keeps its saved bindings and P2
		// uses A/D/W without creating a separate input manager.
		for (PlayerSlot player : this.players) {
			Ship playerShip = player.getShip();
			// An eliminated player cannot control a ship that is only showing
			// its destruction animation.
			if (!player.isAlive() || playerShip.isDestroyed())
				continue;

			boolean moveRight = inputManager.isKeyDown(
					player.getKey(ControlsConfig.RIGHT));
			boolean moveLeft = inputManager.isKeyDown(
					player.getKey(ControlsConfig.LEFT));
			boolean isRightBorder = playerShip.getPositionX()
					+ playerShip.getWidth() + playerShip.getSpeed()
					> this.width - 1;
			boolean isLeftBorder = playerShip.getPositionX()
					- playerShip.getSpeed() < 1;

			if (moveRight && !isRightBorder)
				playerShip.moveRight();
			if (moveLeft && !isLeftBorder)
				playerShip.moveLeft();
			if (inputManager.isKeyDown(player.getKey(ControlsConfig.FIRE))) {
				int fired = playerShip.shoot(this.bullets);
				this.bulletsShot += fired;
				publishShotFired(fired);
				// Play the shot sound only when a bullet was actually fired.
				if (fired > 0)
					AudioManager.playSFX("audio/sfx/player_shoot.wav");
			}

			// Each ship owns its burst timer; its follow-up bullets still join
			// the shared bullet set and team-wide shot counter.
			int burstFired = playerShip.updateBurst(this.bullets);
			this.bulletsShot += burstFired;
			publishShotFired(burstFired);
			if (burstFired > 0)
				AudioManager.playSFX("audio/sfx/player_shoot.wav");
		}
	}

	private void spawnEnemy() {
		if (!this.spawnCooldown.checkFinished())
			return;

		int amplitude = ZigzagEnemyShip.getZigzagAmplitude();
		int minX = SIDE_MARGIN + amplitude;
		int maxX = this.width - SIDE_MARGIN - amplitude - ENEMY_WIDTH;
		int positionX = minX + (int) (Math.random() * (maxX - minX));

		SpriteType spriteType;
		double roll = Math.random();
		if (roll < 0.2)
			spriteType = SpriteType.EnemyShipC1;
		else if (roll < 0.6)
			spriteType = SpriteType.EnemyShipB1;
		else
			spriteType = SpriteType.EnemyShipA1;

		int fallSpeed = Math.min(MAXIMUM_FALL_SPEED,
				1 + this.shipsDestroyed / KILLS_PER_SPEED_UP);

		this.enemies.add(new ZigzagEnemyShip(positionX,
				SEPARATION_LINE_HEIGHT, spriteType, fallSpeed));

		int interval = Math.max(MINIMUM_SPAWN_INTERVAL, INITIAL_SPAWN_INTERVAL
				- this.shipsDestroyed * SPAWN_INTERVAL_STEP);
		this.spawnCooldown = Core.getCooldown(interval);
		this.spawnCooldown.reset();
	}

	private void moveEnemies() {
		Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
		while (iterator.hasNext()) {
			ZigzagEnemyShip enemy = iterator.next();
			enemy.zigzagMove();
			enemy.update();
			if (enemy.getPositionY() > this.height)
				iterator.remove();
		}
	}

	private void shootEnemies() {
		for (ZigzagEnemyShip enemy : this.enemies) {
			if (Math.random() < ENEMY_FIRE_CHANCE) {
				this.enemyBullets.add(BulletPool.getBullet(
						enemy.getPositionX() + enemy.getWidth() / 2,
						enemy.getPositionY() + enemy.getHeight(),
						getEnemyBulletSpeed()));
				// Same enemy shot sound as the Campaign formation.
				AudioManager.playSFX("audio/sfx/enemy_shoot.wav");
			}
		}
	}

	private void draw() {
		drawManager.initDrawing(this);

		for (PlayerSlot player : this.players) {
			if (!player.isAlive())
				continue;
			Ship playerShip = player.getShip();
			drawManager.drawEntity(playerShip, playerShip.getPositionX(),
					playerShip.getPositionY());
		}

		for (ZigzagEnemyShip enemy : this.enemies)
			drawManager.drawEntity(enemy, enemy.getPositionX(),
					enemy.getPositionY());

		for (Bullet bullet : this.bullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

		for (Bullet bullet : this.enemyBullets)
			drawManager.drawEntity(bullet, bullet.getPositionX(),
					bullet.getPositionY());

		drawManager.drawScore(this, this.score);
		drawManager.drawLives(this, this.lives, this.maxLives);
		try {
			drawManager.drawCredits(this, Core.getAssetHudSnapshot().getCredits());
		} catch (IllegalStateException e) {
			// Assets are not initialized (e.g. screen-only tests): skip credits.
		}
		drawManager.drawHorizontalLine(this, SEPARATION_LINE_HEIGHT - 1);

		if (this.visibleAchievement != null)
			drawManager.drawAchievementNotice(this, this.visibleAchievement);
		drawManager.drawParticles(this.particleSystem);

		if (!this.inputDelay.checkFinished()) {
			int countdown = (int) ((INPUT_DELAY
					- (System.currentTimeMillis() - this.gameStartTime)) / 1000);
			playCountdownSound(countdown);
			drawManager.drawCountDown(this, "Infinite Mode", countdown);
			drawManager.drawHorizontalLine(this,
					this.height / 2 - this.height / 12);
			drawManager.drawHorizontalLine(this,
					this.height / 2 + this.height / 12);
		}
		drawManager.completeDrawing(this);
	}

	private void playCountdownSound(final int countdown) {
		if (countdown == this.lastCountdown)
			return;

		this.lastCountdown = countdown;
		if (countdown == 0)
			AudioManager.playSFX("audio/sfx/countdown_go.wav");
		else if (countdown <= 3)
			AudioManager.playSFX("audio/sfx/countdown_beep.wav");
	}

	private void cleanBullets() {
		Set<Bullet> recyclable = new HashSet<Bullet>();
		for (Bullet bullet : this.bullets) {
			bullet.update();
			if (bullet.getPositionY() < SEPARATION_LINE_HEIGHT
					|| bullet.getPositionY() > this.height)
				recyclable.add(bullet);
		}
		this.bullets.removeAll(recyclable);
		BulletPool.recycle(recyclable);

		Set<Bullet> recyclableEnemy = new HashSet<Bullet>();
		for (Bullet bullet : this.enemyBullets) {
			bullet.update();
			if (bullet.getPositionY() > this.height)
				recyclableEnemy.add(bullet);
		}
		this.enemyBullets.removeAll(recyclableEnemy);
		BulletPool.recycle(recyclableEnemy);
	}

	private void manageCollisions() {
		if (this.gameOver) return;
		Set<Bullet> recyclable = new HashSet<Bullet>();

		for (Bullet bullet : this.bullets) {
			Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
			while (iterator.hasNext()) {
				ZigzagEnemyShip enemy = iterator.next();
				if (checkCollision(bullet, enemy)) {
					this.score += enemy.getPointValue();
					this.shipsDestroyed++;
                    recordEnemyDefeat(enemy);
                    AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
					this.particleSystem.spawnEnemyExplosion(
							enemy.getPositionX() + enemy.getWidth() / 2,
							enemy.getPositionY() + enemy.getHeight() / 2);
					iterator.remove();
					recyclable.add(bullet);
					break;
				}
			}
		}
		this.bullets.removeAll(recyclable);
		BulletPool.recycle(recyclable);

		if (this.gameOver)
			return;

		Iterator<Bullet> bulletIterator = this.enemyBullets.iterator();
		while (bulletIterator.hasNext()) {
			Bullet bullet = bulletIterator.next();
			for (PlayerSlot player : this.players) {
				Ship target = player.getShip();
				if (player.isAlive() && !target.isDestroyed()
						&& checkCollision(bullet, target)) {
					bulletIterator.remove();
					BulletPool.recycle(Collections.singleton(bullet));
					damagePlayer(player, PlayerHitSource.ENEMY_BULLET,
							"Hit by enemy bullet");
					break;
				}
			}
		}

		Iterator<ZigzagEnemyShip> iterator = this.enemies.iterator();
		while (iterator.hasNext()) {
			ZigzagEnemyShip rammed = iterator.next();
			for (PlayerSlot player : this.players) {
				Ship target = player.getShip();
				if (player.isAlive() && !target.isDestroyed()
						&& checkCollision(rammed, target)) {
					iterator.remove();
					// The crashing enemy explodes like an enemy that is shot down.
					AudioManager.playSFX("audio/sfx/enemy_explosion.wav");
					this.particleSystem.spawnEnemyExplosion(
							rammed.getPositionX() + rammed.getWidth() / 2,
							rammed.getPositionY() + rammed.getHeight() / 2);
					damagePlayer(player, PlayerHitSource.ENEMY_COLLISION,
							"Hit on player ship");
					break;
				}
			}
		}
	}

	/**
	 * Applies damage to the selected ship. In 2P, the personal status marks
	 * that ship out; the shared team pool is changed only when nobody remains.
	 */
	private void damagePlayer(final PlayerSlot player,
			final PlayerHitSource source, final String reason) {
		PlayerStatus status = player.getStatus();
		Ship target = player.getShip();
		if (!status.takeDamage(1))
			return;

		target.destroy();
		target.triggerDamageFeedback();
		AudioManager.playSFX("audio/sfx/player_hit.wav");
		this.particleSystem.spawnPlayerExplosion(
				target.getPositionX() + target.getWidth() / 2,
				target.getPositionY() + target.getHeight() / 2);

		// In solo play the status is the life pool; in 2P it only marks this ship eliminated.
		if (this.playerCount == 1)
			this.lives = status.getLives();

		// Urgent music starts when the solo player has one life, or when the 2P team
		// has one shared life and this hit has eliminated the last active ship.
		// In solo play this hit can leave one life; the 2P shared pool is checked
		// after all ships are out, when resolveSharedPlayerDefeat spends a life.
		if (this.playerCount == 1 && this.lives == 1
				&& !this.lowLivesMusicStarted) {
			AudioManager.playBGM("audio/bgm/music_game_urgent.wav");
			this.lowLivesMusicStarted = true;
		}

		this.logger.info(reason + ", " + this.lives + " team lives remaining.");
		publishPlayerHit(source);
	}

	private boolean checkCollision(final Entity a, final Entity b) {
		int centerAX = a.getPositionX() + a.getWidth() / 2;
		int centerAY = a.getPositionY() + a.getHeight() / 2;
		int centerBX = b.getPositionX() + b.getWidth() / 2;
		int centerBY = b.getPositionY() + b.getHeight() / 2;

		int maxDistanceX = a.getWidth() / 2 + b.getWidth() / 2;
		int maxDistanceY = a.getHeight() / 2 + b.getHeight() / 2;

		int distanceX = Math.abs(centerAX - centerBX);
		int distanceY = Math.abs(centerAY - centerBY);

		return distanceX < maxDistanceX && distanceY < maxDistanceY;
	}

	public boolean isAbandoned() { return this.abandoned; }

	public final int getPlayerCount() {
		return this.playerCount;
	}

	/** Returns the selected player's current ship for screen-level tests and integrations. */
	public final Ship getPlayerShip(final int index) {
		return this.players.get(index).getShip();
	}

	/** Returns the selected player's personal status, separate from shared team lives. */
	public final PlayerStatus getPlayerStatus(final int index) {
		return this.players.get(index).getStatus();
	}

	public final GameState getGameState() {
		// The screen's life value is the solo pool or the 2P shared team pool.
		return new GameState(1, this.score, this.lives, this.bulletsShot,
				this.shipsDestroyed);
	}

	/** Sends the bullets fired this frame, if any, for accuracy statistics. */
	private void publishShotFired(final int bulletCount) {
		if (bulletCount <= 0 || this.runSession == null) return;
		this.recordsEvents.publish(new ShotFiredEvent(this.runSession.getRunId(),
				GameplayEventIds.newId(GameScreen.SHOT_ID_KIND), bulletCount));
	}

	/**
	 * Sends a hit that cost the player one life. As in Campaign, the ID is
	 * given once, at the one place each hit is accepted.
	 */
	private void publishPlayerHit(final PlayerHitSource source) {
		if (this.runSession == null) return;
		this.recordsEvents.publish(new PlayerHitEvent(this.runSession.getRunId(),
				GameplayEventIds.newId(source.name()), source, 1, Math.max(0, this.lives)));
	}

	private void recordEnemyDefeat(final Entity enemy) {
		if (this.runSession == null) return;
		String id = this.defeatIds.get(enemy);
		if (id == null) { id=UUID.randomUUID().toString(); this.defeatIds.put(enemy,id); }
		this.recordsEvents.publish(new EnemyDefeatedEvent(this.runSession.getRunId(),id));
	}

	public GameplayEventPublisher getGameplayEvents() { return this.recordsEvents; }

	public String getActiveRunId() { return this.runSession == null ? null : this.runSession.getRunId(); }
}
