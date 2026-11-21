package screen;

import static org.junit.jupiter.api.Assertions.*;

import audio.AudioBus;
import audio.AudioCache;
import audio.AudioManager;
import audio.AudioResource;
import boss.BossAttackSystem;
import boss.BossManager;
import engine.Cooldown;
import engine.GameState;
import engine.StoryDebugModifiers;
import entity.Boss;
import entity.BossBullet;
import entity.Bullet;
import entity.BulletPool;
import entity.PlayerStatus;
import entity.PlayerStatusEvent;
import entity.Ship;
import entity.item.OrbitalAnnihilator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import story.StoryProgress;
import story.StoryRoute;

/** Exercises the real TRUCE beam and projectile collisions, observing playSFX's cache requests. */
@Isolated("Temporarily installs a recording cache in the shared AudioManager")
class ObeliskShieldSoundTest {
    private static final String BLOCK_SOUND = "audio/sfx/item_shield_block.wav";
    private final List<Integer> blockedDamage = new ArrayList<>();
    private int blockSounds;
    private AudioCache previousCache;
    private boolean audioWasInitialized;
    private boolean recording;
    private GameScreen game;
    private Method collisions;

    @ParameterizedTest(name = "invincible={0}, shield={1}")
    @CsvSource({"true, TIMED", "false, TIMED", "true, ENERGY", "false, ENERGY"})
    void onlyLaserSoundsAreThrottled(boolean invincible, Shield shield) throws Exception {
        startTruceBattle(invincible);
        Ship ship = game.getPlayerShip(0);
        PlayerStatus status = game.getPlayerStatus();
        if (shield == Shield.TIMED) {
            assertTrue(status.activateShield());
        } else {
            ship.setShieldActive(true);
        }
        int lives = status.getLives();
        Field lastSound = field(GameScreen.class, "lastLaserShieldBlockSfxMillis");
        assertEquals(250L, field(GameScreen.class, "LASER_SHIELD_BLOCK_SFX_INTERVAL_MS").getLong(null));

        collide();
        assertEquals(1, blockSounds);
        long firstSoundTime = lastSound.getLong(game);
        assertTrue(firstSoundTime > 0, "First beam contact starts the cooldown");
        // Usually these are adjacent frames. Reopen the same test window if setup was suspended.
        long now = System.currentTimeMillis();
        if (now - firstSoundTime >= 250L) {
            lastSound.setLong(game, now - 1);
        }
        collide();
        int soundsAfterTwoHits = blockSounds;
        assertEquals(List.of(1, 1), blockedDamage);
        assertEquals(lives, status.getLives());

        lastSound.setLong(game, System.currentTimeMillis() - 250L);
        collide();
        assertEquals(List.of(1, 1, 1), blockedDamage);
        assertEquals(lives, status.getLives());
        assertAll(
                () -> assertEquals(1, soundsAfterTwoHits, "Two laser hits inside one window"),
                () -> assertEquals(2, blockSounds, "Third laser hit after the 250 ms window"));

        // Two different projectiles in one frame still sound even while the laser is throttled.
        ship.setPositionX(0);
        lastSound.setLong(game, System.currentTimeMillis());
        addOverlappingProjectiles(ship);
        collide();
        assertEquals(List.of(1, 1, 1, 1, 2), blockedDamage);
        assertEquals(4, blockSounds);
        assertEquals(lives, status.getLives());
    }

    @Test
    void invincibilityWithoutAShieldIsSilent() throws Exception {
        startTruceBattle(true);
        PlayerStatus status = game.getPlayerStatus();
        int lives = status.getLives();
        collide();
        collide();
        assertTrue(blockedDamage.isEmpty());
        assertEquals(0, blockSounds);
        assertEquals(lives, status.getLives());
        assertFalse(game.getPlayerShip(0).isDestroyed());
    }

    private enum Shield { TIMED, ENERGY }

    private void startTruceBattle(boolean invincible) throws Exception {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(StoryRoute.TRUCE.getFirstChoice());
        progress.chooseSecond(StoryRoute.TRUCE.getSecondChoice());
        game = GameScreen.forStoryDebugStage(new GameState(7, 0, 4, 0, 0), progress,
                settings -> settings, 448, 520, 60, new StoryDebugModifiers(invincible, 4, 1, 1));
        game.initialize();
        BossManager manager = (BossManager) field(GameScreen.class, "bossManager").get(game);
        Boss boss = manager.getBoss();
        assertEquals(Boss.BossType.BOSS_D, boss.getType());

        // Freeze just the firing phase. BossObeliskTest exercises its charge, fire and expiry.
        Cooldown beam = new Cooldown(OrbitalAnnihilator.BEAM_DURATION, () -> 10_000L);
        beam.reset();
        field(BossAttackSystem.class, "beamCooldown").set(manager.getAttackSystem(), beam);
        field(BossAttackSystem.class, "activeBeamWidth").setInt(
                manager.getAttackSystem(), OrbitalAnnihilator.BEAM_WIDTH);
        Ship ship = game.getPlayerShip(0);
        ship.setPositionX(boss.getPositionX() + (boss.getWidth() - ship.getWidth()) / 2);
        game.getPlayerStatus().addListener(event -> {
            if (event.getType() == PlayerStatusEvent.Type.DAMAGE_BLOCKED) {
                blockedDamage.add(event.getDamageAmount());
            }
        });
        collisions = GameScreen.class.getDeclaredMethod("manageCollisions");
        collisions.setAccessible(true);
        recordBlockSounds();
    }

    @SuppressWarnings("unchecked")
    private void addOverlappingProjectiles(Ship ship) throws Exception {
        int centerX = ship.getPositionX() + ship.getWidth() / 2;
        int y = ship.getPositionY() + 2;
        Set<Bullet> bullets = (Set<Bullet>) field(GameScreen.class, "bullets").get(game);
        Set<BossBullet> bossBullets = (Set<BossBullet>) field(GameScreen.class, "bossBullets").get(game);
        bullets.add(BulletPool.getBullet(centerX, y, 4, Bullet.Owner.ENEMY));
        bossBullets.add(new BossBullet(centerX, y, 0, 0, 2, false));
    }

    private void collide() throws Exception {
        collisions.invoke(game);
    }

    private void recordBlockSounds() throws Exception {
        Field cache = field(AudioManager.class, "audioCache");
        Field initialized = field(AudioManager.class, "initialized");
        previousCache = (AudioCache) cache.get(null);
        audioWasInitialized = initialized.getBoolean(null);
        AudioCache resources = previousCache != null ? previousCache : new AudioCache();
        assertNotNull(resources.getAudioData(BLOCK_SOUND)); // Decode before measuring adjacent hits.
        cache.set(null, new AudioCache() {
            @Override
            public AudioResource getAudioData(String path) {
                if (BLOCK_SOUND.equals(path)) {
                    blockSounds++;
                }
                return resources.getAudioData(path);
            }
        });
        initialized.setBoolean(null, true); // Observe SFX requests even without an output device.
        recording = true;
    }

    @AfterEach
    void restoreAudio() throws Exception {
        AudioManager.stopBGM();
        if (recording) {
            if (!audioWasInitialized) {
                // No mixer was available to drain the test's queued effects.
                ((AudioBus) field(AudioManager.class, "sfxBus").get(null)).stopAll();
            }
            field(AudioManager.class, "audioCache").set(null, previousCache);
            field(AudioManager.class, "initialized").setBoolean(null, audioWasInitialized);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
