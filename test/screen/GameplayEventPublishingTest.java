package screen;

import audio.AudioManager;
import engine.ControlsConfig;
import engine.Core;
import engine.DrawManager.SpriteType;
import engine.GameSettings;
import engine.GameState;
import engine.InputManager;
import entity.Boss;
import entity.BossBullet;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.EnemyType;
import entity.Ship;
import entity.ShipType;
import entity.ZigzagEnemyShip;
import entity.item.ItemManager;
import entity.item.ShieldItem;
import event.BossDefeatedEvent;
import event.DeduplicatingListener;
import event.DefeatedEnemyType;
import event.GameplayEvent;
import event.GameplayEventPublisher;
import event.ItemUsedEvent;
import event.PlayerHitEvent;
import event.PlayerHitSource;
import event.ShotFiredEvent;
import event.UsedItemType;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import records.RunSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real Campaign and Infinite screens and checks that each of the
 * four gameplay events reaches the shared bus from the place it happens.
 */
class GameplayEventPublishingTest {

    private boolean wasMuted;
    private boolean[] keys;
    private boolean[] savedKeys;

    @BeforeEach
    void setUp() throws Exception {
        wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        // The key array exists only once the input manager has been created.
        Core.getInputManager();
        Field keyboard = InputManager.class.getDeclaredField("keys");
        keyboard.setAccessible(true);
        keys = (boolean[]) keyboard.get(null);
        savedKeys = keys.clone();
        Arrays.fill(keys, false);
    }

    @AfterEach
    void tearDown() {
        System.arraycopy(savedKeys, 0, keys, 0, keys.length);
        AudioManager.stopBGM();
        AudioManager.setMuted(wasMuted);
    }

    // ------------------------------------------------------------ Campaign

    @Test
    void campaignShotsAddUpToTheBulletsShot() throws Exception {
        GameScreen game = campaign(1, ShipType.STANDARD, "shots");
        Seen<ShotFiredEvent> shots = watch(game.getGameplayEvents(), ShotFiredEvent.class);
        set(game, "inputDelay", Core.getCooldown(0));

        keys[ControlsConfig.getKey(ControlsConfig.FIRE)] = true;
        game.update();

        assertTrue(shots.size() >= 1, "pressing fire publishes a shot");
        assertEquals(game.getGameState().getBulletsShot(), totalBullets(shots));
        assertEquals("shots", shots.get(0).getRunId());
    }

    @Test
    void campaignHitsNameTheirSourceAndShieldedHitsAreSkipped() throws Exception {
        GameScreen game = campaign(1, ShipType.TANK, "hits");
        Seen<PlayerHitEvent> hits = watch(game.getGameplayEvents(), PlayerHitEvent.class);
        Ship ship = (Ship) get(game, "ship");

        ship.setShieldActive(true);
        enemyBullets(game).add(BulletPool.getBullet(ship.getPositionX() + 2,
                ship.getPositionY() + 2, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");
        assertEquals(0, hits.size(), "a hit absorbed by the shield is not a player hit");

        ship.setShieldActive(false);
        enemyBullets(game).add(BulletPool.getBullet(ship.getPositionX() + 2,
                ship.getPositionY() + 2, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");

        assertEquals(1, hits.size());
        assertSame(PlayerHitSource.ENEMY_BULLET, hits.get(0).getSource());
        assertEquals(1, hits.get(0).getDamage());
        assertEquals(2, hits.get(0).getLivesRemaining());

        // The recovery period after a hit is not a second hit.
        enemyBullets(game).add(BulletPool.getBullet(ship.getPositionX() + 2,
                ship.getPositionY() + 2, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");
        assertEquals(1, hits.size());
    }

    @Test
    void campaignBossBulletHitsAreReportedAsBossBullets() throws Exception {
        GameScreen game = campaign(3, ShipType.STANDARD, "boss-hit");
        Seen<PlayerHitEvent> hits = watch(game.getGameplayEvents(), PlayerHitEvent.class);
        Ship ship = (Ship) get(game, "ship");

        bossBullets(game).add(new BossBullet(ship.getPositionX() + ship.getWidth() / 2.0,
                ship.getPositionY() + 2, 0, 0, 1, false));
        call(game, "manageCollisions");

        assertEquals(1, hits.size());
        assertSame(PlayerHitSource.BOSS_BULLET, hits.get(0).getSource());
    }

    @Test
    void campaignSummonedEnemyCollisionsAreReportedAsEnemyCollisions() throws Exception {
        GameScreen game = campaign(3, ShipType.STANDARD, "summon-hit");
        Seen<PlayerHitEvent> hits = watch(game.getGameplayEvents(), PlayerHitEvent.class);
        Ship ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0));

        summonedEnemies(game).add(EnemyShip.createByType(EnemyType.A,
                ship.getPositionX(), ship.getPositionY()));
        game.update();

        assertTrue(summonedEnemies(game).isEmpty(), "the summoned enemy hit the ship");
        assertEquals(1, hits.size());
        assertSame(PlayerHitSource.ENEMY_COLLISION, hits.get(0).getSource());
        assertEquals(2, hits.get(0).getLivesRemaining());
    }

    @Test
    void campaignGrenadeLaunchAndShieldPickupAreItemUses() throws Exception {
        GameScreen game = campaign(1, ShipType.TANK, "items");
        Seen<ItemUsedEvent> uses = watch(game.getGameplayEvents(), ItemUsedEvent.class);
        Ship ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0));

        ship.addGrenades(1);
        keys[KeyEvent.VK_G] = true;
        game.update();
        game.update();
        keys[KeyEvent.VK_G] = false;
        assertEquals(1, uses.size(), "holding G launches one grenade");
        assertSame(UsedItemType.CLUSTER_GRENADE, uses.get(0).getItemType());

        ItemManager items = (ItemManager) get(game, "itemManager");
        items.spawnItem(new ShieldItem(ship.getPositionX(), ship.getPositionY()));
        game.update();

        assertEquals(2, uses.size());
        assertSame(UsedItemType.ENERGY_SHIELD, uses.get(1).getItemType());
    }

    @Test
    void aShieldCaughtByAShipThatCannotHoldItIsNotUsed() throws Exception {
        GameScreen game = campaign(1, ShipType.STANDARD, "no-shield");
        Seen<ItemUsedEvent> uses = watch(game.getGameplayEvents(), ItemUsedEvent.class);
        Ship ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0));

        ItemManager items = (ItemManager) get(game, "itemManager");
        items.spawnItem(new ShieldItem(ship.getPositionX(), ship.getPositionY()));
        game.update();

        assertEquals(0, uses.size());
    }

    @Test
    void campaignBossDefeatIsPublishedOnce() throws Exception {
        GameScreen game = campaign(3, ShipType.STANDARD, "boss");
        Seen<BossDefeatedEvent> defeats =
                watch(game.getGameplayEvents(), BossDefeatedEvent.class);

        damageBoss(game, Boss.BossType.BOSS_A.getMaxHealth());
        damageBoss(game, Boss.BossType.BOSS_A.getMaxHealth());

        assertEquals(1, defeats.size());
        assertEquals("boss", defeats.get(0).getRunId());
        assertSame(DefeatedEnemyType.BOSS_A, defeats.get(0).getBossType());
        assertTrue(defeats.get(0).getBattleDurationMillis() >= 0);
    }

    @Test
    void nothingIsPublishedOutsideARecordedRun() throws Exception {
        GameScreen game = new GameScreen(new GameState(3, 0, 3, 0, 0),
                new GameSettings(3, 5, 1, 100000), false, 448, 520, 60, ShipType.STANDARD);
        game.initialize();
        Seen<GameplayEvent> seen = new Seen<GameplayEvent>();
        GameplayEventPublisher bus = game.getGameplayEvents();
        bus.subscribe(ShotFiredEvent.class, seen::add);
        bus.subscribe(PlayerHitEvent.class, seen::add);
        bus.subscribe(ItemUsedEvent.class, seen::add);
        bus.subscribe(BossDefeatedEvent.class, seen::add);
        set(game, "inputDelay", Core.getCooldown(0));

        keys[ControlsConfig.getKey(ControlsConfig.FIRE)] = true;
        game.update();
        game.damagePlayer(1, PlayerHitSource.ENEMY_BULLET, "test");
        damageBoss(game, Boss.BossType.BOSS_A.getMaxHealth());

        assertEquals(0, seen.size());
    }

    // ------------------------------------------------------------ Infinite

    @Test
    void infiniteShotsAddUpToTheBulletsShot() throws Exception {
        InfiniteGameScreen game = infinite("endless-shots");
        Seen<ShotFiredEvent> shots = watch(game.getGameplayEvents(), ShotFiredEvent.class);

        keys[ControlsConfig.getKey(ControlsConfig.FIRE)] = true;
        game.updatePlayerControls();

        assertEquals(1, shots.size());
        assertEquals(game.getGameState().getBulletsShot(), totalBullets(shots));
        assertEquals("endless-shots", shots.get(0).getRunId());
    }

    @Test
    void infiniteTwoPlayerShotsAreBothPublished() {
        InfiniteGameScreen game = new InfiniteGameScreen(
                new GameState(1, 0, 3, 0, 0), 448, 520, 60,
                new RunSession("endless-two-player-shots"), 2);
        game.initialize();
        Seen<ShotFiredEvent> shots = watch(game.getGameplayEvents(), ShotFiredEvent.class);

        keys[ControlsConfig.getPlayerKey(0, ControlsConfig.FIRE)] = true;
        keys[ControlsConfig.getPlayerKey(1, ControlsConfig.FIRE)] = true;
        game.updatePlayerControls();

        assertEquals(2, shots.size());
        assertEquals(2, totalBullets(shots));
        assertEquals(game.getGameState().getBulletsShot(), totalBullets(shots));
    }

    @Test
    void infiniteBulletHitIsPublished() throws Exception {
        InfiniteGameScreen game = infinite("endless-bullet");
        Seen<PlayerHitEvent> hits = watch(game.getGameplayEvents(), PlayerHitEvent.class);
        Ship ship = (Ship) get(game, "ship");

        enemyBulletsOf(game).add(BulletPool.getBullet(ship.getPositionX() + 2,
                ship.getPositionY() + 2, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");
        // The ship is down now, so a second bullet in the same spot is ignored.
        enemyBulletsOf(game).add(BulletPool.getBullet(ship.getPositionX() + 2,
                ship.getPositionY() + 2, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");

        assertEquals(1, hits.size());
        assertSame(PlayerHitSource.ENEMY_BULLET, hits.get(0).getSource());
        assertEquals(1, hits.get(0).getDamage());
        assertEquals(2, hits.get(0).getLivesRemaining());
    }

    @Test
    void infiniteMissedEnemyBulletDoesNotPublishAHit() throws Exception {
        InfiniteGameScreen game = infinite("endless-miss");
        Seen<PlayerHitEvent> hits = watch(game.getGameplayEvents(), PlayerHitEvent.class);
        enemyBulletsOf(game).add(BulletPool.getBullet(0, 0, 4, Bullet.Owner.ENEMY));

        call(game, "manageCollisions");

        assertEquals(0, hits.size());
    }

    @Test
    void infiniteCollisionHitIsPublished() throws Exception {
        InfiniteGameScreen game = infinite("endless-crash");
        Seen<PlayerHitEvent> hits = watch(game.getGameplayEvents(), PlayerHitEvent.class);
        Ship ship = (Ship) get(game, "ship");

        @SuppressWarnings("unchecked")
        List<ZigzagEnemyShip> enemies = (List<ZigzagEnemyShip>) get(game, "enemies");
        enemies.add(new ZigzagEnemyShip(ship.getPositionX(), ship.getPositionY(),
                SpriteType.EnemyShipA1, 1));
        call(game, "manageCollisions");

        assertEquals(1, hits.size());
        assertSame(PlayerHitSource.ENEMY_COLLISION, hits.get(0).getSource());
        assertEquals(2, hits.get(0).getLivesRemaining());
    }

    // ------------------------------------------------------------ event IDs

    @Test
    void campaignIdsAreGroupedByKindAndDistinctPerAction() throws Exception {
        GameScreen game = campaign(3, ShipType.STANDARD, "ids");
        GameplayEventPublisher bus = game.getGameplayEvents();
        Seen<PlayerHitEvent> hits = watch(bus, PlayerHitEvent.class);
        Seen<ShotFiredEvent> shots = watch(bus, ShotFiredEvent.class);
        Seen<BossDefeatedEvent> defeats = watch(bus, BossDefeatedEvent.class);
        Ship ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0));

        game.damagePlayer(1, PlayerHitSource.ENEMY_BULLET, "first");
        endCooldown(ship, "destructionCooldown");
        endCooldown(ship, "invulnerabilityCooldown");
        game.damagePlayer(1, PlayerHitSource.ENEMY_BULLET, "second");
        endCooldown(ship, "destructionCooldown");
        keys[ControlsConfig.getKey(ControlsConfig.FIRE)] = true;
        game.update();
        damageBoss(game, Boss.BossType.BOSS_A.getMaxHealth());

        assertEquals(2, hits.size());
        assertTrue(hits.get(0).getEventId().startsWith("ENEMY_BULLET:"));
        assertTrue(hits.get(1).getEventId().startsWith("ENEMY_BULLET:"));
        assertNotEquals(hits.get(0).getEventId(), hits.get(1).getEventId(),
                "two hits from the same source are two actions");
        assertTrue(shots.get(0).getEventId().startsWith(GameScreen.SHOT_ID_KIND + ":"));
        assertTrue(defeats.get(0).getEventId().startsWith("BOSS_A:"));
    }

    @Test
    void campaignItemIdsAreGroupedByItem() throws Exception {
        GameScreen game = campaign(1, ShipType.TANK, "item-ids");
        Seen<ItemUsedEvent> uses = watch(game.getGameplayEvents(), ItemUsedEvent.class);
        Ship ship = (Ship) get(game, "ship");
        set(game, "inputDelay", Core.getCooldown(0));

        ship.addGrenades(1);
        keys[KeyEvent.VK_G] = true;
        game.update();
        keys[KeyEvent.VK_G] = false;
        ((ItemManager) get(game, "itemManager"))
                .spawnItem(new ShieldItem(ship.getPositionX(), ship.getPositionY()));
        game.update();

        assertEquals(2, uses.size());
        assertTrue(uses.get(0).getEventId().startsWith("CLUSTER_GRENADE:"));
        assertTrue(uses.get(1).getEventId().startsWith("ENERGY_SHIELD:"));
    }

    @Test
    void infiniteIdsAreGroupedByKind() throws Exception {
        InfiniteGameScreen game = infinite("endless-ids");
        GameplayEventPublisher bus = game.getGameplayEvents();
        Seen<PlayerHitEvent> hits = watch(bus, PlayerHitEvent.class);
        Seen<ShotFiredEvent> shots = watch(bus, ShotFiredEvent.class);
        Ship ship = (Ship) get(game, "ship");

        keys[ControlsConfig.getKey(ControlsConfig.FIRE)] = true;
        game.updatePlayerControls();
        enemyBulletsOf(game).add(BulletPool.getBullet(ship.getPositionX() + 2,
                ship.getPositionY() + 2, 4, Bullet.Owner.ENEMY));
        call(game, "manageCollisions");

        assertTrue(shots.get(0).getEventId().startsWith(GameScreen.SHOT_ID_KIND + ":"));
        assertTrue(hits.get(0).getEventId().startsWith("ENEMY_BULLET:"));
    }

    @Test
    void aRedeliveredScreenEventIsHandledOnce() throws Exception {
        GameScreen game = campaign(3, ShipType.STANDARD, "redelivery");
        GameplayEventPublisher bus = game.getGameplayEvents();
        Seen<PlayerHitEvent> raw = watch(bus, PlayerHitEvent.class);
        Seen<PlayerHitEvent> handled = new Seen<PlayerHitEvent>();
        bus.subscribe(PlayerHitEvent.class,
                new DeduplicatingListener<PlayerHitEvent>(handled::add));
        Seen<BossDefeatedEvent> bossesHandled = new Seen<BossDefeatedEvent>();
        bus.subscribe(BossDefeatedEvent.class,
                new DeduplicatingListener<BossDefeatedEvent>(bossesHandled::add));

        game.damagePlayer(1, PlayerHitSource.ENEMY_BULLET, "hit");
        damageBoss(game, Boss.BossType.BOSS_A.getMaxHealth());
        // Deliver the same actions again, as a second forwarding path would.
        bus.publish(raw.get(0));
        bus.publish(new PlayerHitEvent(raw.get(0).getRunId(), raw.get(0).getEventId(),
                raw.get(0).getSource(), raw.get(0).getDamage(), raw.get(0).getLivesRemaining()));

        assertEquals(3, raw.size(), "the bus itself delivers every publish");
        assertEquals(1, handled.size(), "the hit is handled once");
        assertEquals(1, bossesHandled.size(), "the boss defeat is handled once");
    }

    // ------------------------------------------------------------ helpers

    /** Ends one of the ship's cooldowns without waiting for the clock. */
    private static void endCooldown(final Ship ship, final String name) throws Exception {
        Object cooldown = field(Ship.class, name).get(ship);
        Field time = cooldown.getClass().getDeclaredField("time");
        time.setAccessible(true);
        time.setLong(cooldown, 0L);
    }

    private static GameScreen campaign(final int stage, final ShipType shipType,
            final String runId) {
        GameScreen game = new GameScreen(new GameState(stage, 0, 3, 0, 0),
                Arrays.asList(new GameSettings(3, 5, 1, 100000)), false, 448, 520, 60,
                shipType, new RunSession(runId));
        game.initialize();
        return game;
    }

    private static InfiniteGameScreen infinite(final String runId) {
        InfiniteGameScreen game = new InfiniteGameScreen(new GameState(1, 0, 3, 0, 0),
                448, 520, 60, new RunSession(runId));
        game.initialize();
        return game;
    }

    private static final class Seen<T> {
        private final List<T> events = new ArrayList<T>();
        void add(final T event) { events.add(event); }
        int size() { return events.size(); }
        T get(final int index) { return events.get(index); }
    }

    private static <T extends GameplayEvent> Seen<T> watch(
            final GameplayEventPublisher bus, final Class<T> type) {
        Seen<T> seen = new Seen<T>();
        bus.subscribe(type, seen::add);
        return seen;
    }

    private static int totalBullets(final Seen<ShotFiredEvent> shots) {
        int total = 0;
        for (ShotFiredEvent shot : shots.events)
            total += shot.getBulletCount();
        return total;
    }

    @SuppressWarnings("unchecked")
    private static Set<Bullet> enemyBullets(final GameScreen game) throws Exception {
        return (Set<Bullet>) get(game, "bullets");
    }

    @SuppressWarnings("unchecked")
    private static Set<Bullet> enemyBulletsOf(final InfiniteGameScreen game) throws Exception {
        return (Set<Bullet>) get(game, "enemyBullets");
    }

    @SuppressWarnings("unchecked")
    private static Set<BossBullet> bossBullets(final GameScreen game) throws Exception {
        return (Set<BossBullet>) get(game, "bossBullets");
    }

    @SuppressWarnings("unchecked")
    private static List<EnemyShip> summonedEnemies(final GameScreen game) throws Exception {
        return (List<EnemyShip>) get(game, "summonedEnemies");
    }

    private static void damageBoss(final GameScreen game, final int damage) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod("damageBoss", int.class);
        method.setAccessible(true);
        method.invoke(game, damage);
    }

    private static void call(final Object target, final String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Object get(final Object target, final String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static void set(final Object target, final String name, final Object value)
            throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(final Class<?> owner, final String name) throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass())
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException ignored) {
                // Keep looking up the hierarchy.
            }
        throw new NoSuchFieldException(name);
    }
}
