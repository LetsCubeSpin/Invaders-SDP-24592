package screen;

import audio.AudioManager;
import engine.ControlsConfig;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.InputManager;
import entity.Bullet;
import entity.EnemyShip;
import entity.Grenade;
import entity.Ship;
import entity.ShipType;
import entity.item.BuffManager;
import entity.item.ClusterGrenadeItem;
import entity.item.Item;
import entity.item.ItemManager;
import entity.item.ShieldItem;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import level.EnemyCell;
import level.LevelDefinition;
import level.WaveDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static screen.CustomLevelGameScreenTest.formation;
import static screen.CustomLevelGameScreenTest.get;

class CombatItemAvailabilityTest {
    private boolean wasMuted;

    @BeforeEach
    void muteAudio() {
        wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
    }

    @AfterEach
    void restoreAudio() {
        AudioManager.stopBGM();
        AudioManager.setMuted(wasMuted);
    }

    @Test
    void stageSevenTankGetsOnlyOneShieldAndTwoGrenadesAfterTenKillsAndAUfo() throws Exception {
        GameScreen game = new GameScreen(new GameState(7, 0, 3, 0, 0),
                new GameSettings(4, 3, 60, 2000), false, 448, 520, 60, ShipType.TANK);
        game.initialize();
        Random drops = (Random) get(game, "itemDropRandom");
        List<Item> items = fallingItems(game);
        for (EnemyShip enemy : formation(game)) {
            // First draw from this seed is below the 15% grenade threshold.
            drops.setSeed(4096);
            shoot(game, enemy);
        }
        assertEquals(12, game.getGameState().getShipsDestroyed());
        assertEquals(2, items.stream().filter(ClusterGrenadeItem.class::isInstance).count());
        assertEquals(1, items.stream().filter(ShieldItem.class::isInstance).count());
        assertEquals(3, items.size());
        EnemyShip ufo = new EnemyShip();
        ufo.setPositionX(50);
        ufo.setPositionY(70);
        Object bonusManager = get(game, "bonusEnemyManager");
        field(bonusManager, "bonusShip").set(bonusManager, ufo);
        shoot(game, ufo);
        assertEquals(13, game.getGameState().getShipsDestroyed());
        assertEquals(3, items.size(), "Defeating the UFO does not spawn a weapon buff");

        Ship ship = (Ship) get(game, "ship");
        ItemManager manager = (ItemManager) get(game, "itemManager");
        // Exercise the old shoot-to-collect entry point after crossing ten kills.
        field(game, "inputDelay").set(game, Core.getCooldown(0));
        game.update();
        @SuppressWarnings("unchecked")
        Set<Bullet> bullets = (Set<Bullet>) get(game, "bullets");
        for (int x = 0; x < game.getWidth(); x += 6)
            bullets.add(new Bullet(x, 42, -6));
        resolveBulletHits(game);
        assertTrue(manager.getBuffManager().getActiveBuffsRemainingTime().isEmpty());
        for (Item item : items) {
            item.setPositionX(ship.getPositionX());
            item.setPositionY(ship.getPositionY());
        }
        manager.checkCollisions(ship);
        assertTrue(items.isEmpty());
        assertTrue(ship.isShieldActive());
        assertEquals(1, ship.getGrenadeCount());
        assertEquals(Set.of("Energy Shield"), manager.getBuffManager().getActiveBuffsRemainingTime().keySet());
        assertFalse(game.damagePlayer(1, "shielded"));
        game.initialize();
        Ship restarted = (Ship) get(game, "ship");
        assertFalse(restarted.isShieldActive());
        assertEquals(0, restarted.getGrenadeCount());
        assertTrue(fallingItems(game).isEmpty());
    }

    @Test
    void shieldExpiresAndResetsWithItsHudEntry() throws Exception {
        Ship tank = new Ship(100, 100, ShipType.TANK);
        ItemManager items = new ItemManager(520);
        BuffManager buffs = items.getBuffManager();
        ShieldItem shield = new ShieldItem(100, 100);
        items.spawnItem(shield);
        items.checkCollisions(tank);
        assertTrue(tank.isShieldActive());
        assertEquals(15_000, shield.getDuration());
        @SuppressWarnings("unchecked")
        Map<Item, Long> deadlines = (Map<Item, Long>) field(buffs, "activeBuffs").get(buffs);
        assertNotNull(deadlines.replace(shield, 0L));
        items.update(tank);
        assertFalse(tank.isShieldActive());
        assertTrue(buffs.getActiveBuffsRemainingTime().isEmpty());
        items.spawnItem(new ShieldItem(100, 100));
        items.checkCollisions(tank);
        assertTrue(tank.isShieldActive());
        buffs.resetAllBuffs(tank);
        assertFalse(tank.isShieldActive());
        assertTrue(buffs.getActiveBuffsRemainingTime().isEmpty());
    }

    @Test
    void customGrenadeDropsIsCollectedAndLaunchesWithG() throws Exception {
        WaveDefinition wave = new WaveDefinition("first", new EnemyCell[][] {
                {new EnemyCell("A", 1), new EnemyCell("B", 1)}}, 20, 100, 60, 2000);
        GameScreen game = GameScreen.forCustomLevel(new GameState(1, 0, 3, 0, 0),
                new LevelDefinition(1, "grenades", "Grenades", List.of(wave)), 1, 448, 520, 60);
        game.initialize();
        ((Random) get(game, "itemDropRandom")).setSeed(4096);
        shoot(game, formation(game).iterator().next());
        List<Item> items = fallingItems(game);
        assertEquals(1, items.size());
        assertInstanceOf(ClusterGrenadeItem.class, items.get(0));
        Ship player = (Ship) get(game, "ship");
        items.get(0).setPositionX(player.getPositionX());
        items.get(0).setPositionY(player.getPositionY());
        ((ItemManager) get(game, "itemManager")).checkCollisions(player);
        assertEquals(1, player.getGrenadeCount());
        field(game, "inputDelay").set(game, Core.getCooldown(0));
        Field keyboard = InputManager.class.getDeclaredField("keys");
        keyboard.setAccessible(true);
        boolean[] keys = (boolean[]) keyboard.get(null);
        boolean[] saved = keys.clone();
        int[] actions = {ControlsConfig.LEFT, ControlsConfig.RIGHT, ControlsConfig.FIRE};
        int[] defaults = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_SPACE};
        int[] bindings = Arrays.stream(actions).map(ControlsConfig::getKey).toArray();
        try {
            for (int i = 0; i < actions.length; i++)
                if (bindings[i] == KeyEvent.VK_G)
                    ControlsConfig.setKey(actions[i], defaults[i]);
            Arrays.fill(keys, false);
            keys[KeyEvent.VK_G] = true;
            keys[KeyEvent.VK_H] = true;
            keys[KeyEvent.VK_UP] = true;
            int originalY = player.getPositionY();
            game.update();
            assertEquals(originalY, player.getPositionY());
            assertEquals(0, player.getGrenadeCount());
            @SuppressWarnings("unchecked")
            Set<Grenade> launched = (Set<Grenade>) get(game, "grenades");
            assertEquals(1, launched.size());
            assertTrue(((ItemManager) get(game, "itemManager")).getBuffManager()
                    .getActiveBuffsRemainingTime().isEmpty());
            player.addGrenades(1);
            game.update();
            assertEquals(1, player.getGrenadeCount(), "Holding G does not spend the next grenade");
            assertEquals(1, launched.size());
        } finally {
            System.arraycopy(saved, 0, keys, 0, keys.length);
            for (int i = 0; i < actions.length; i++)
                if (bindings[i] == KeyEvent.VK_G)
                    ControlsConfig.setKey(actions[i], bindings[i]);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Item> fallingItems(GameScreen game) throws Exception {
        ItemManager manager = (ItemManager) get(game, "itemManager");
        return (List<Item>) field(manager, "activeItems").get(manager);
    }

    @SuppressWarnings("unchecked")
    private static void shoot(GameScreen game, EnemyShip target) throws Exception {
        Set<Bullet> bullets = (Set<Bullet>) get(game, "bullets");
        bullets.add(new Bullet(target.getPositionX() + 4, target.getPositionY(), -6));
        resolveBulletHits(game);
    }

    private static void resolveBulletHits(GameScreen game) throws Exception {
        Method collisions = GameScreen.class.getDeclaredMethod("manageCollisions");
        collisions.setAccessible(true);
        collisions.invoke(game);
    }

    private static Field field(Object obj, String name) throws NoSuchFieldException {
        for (Class<?> type = obj.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException missing) {
                // Screen declares the common input delay.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
