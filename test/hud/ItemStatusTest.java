package hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import engine.Core;
import engine.DrawManager;
import entity.Ship;
import entity.ShipType;
import entity.item.BuffHud;
import entity.item.ItemManager;
import entity.item.ShieldItem;
import screen.Screen;
import vfx.Icon;

/** The grenades and buffs of the status strip under the HUD. */
class ItemStatusTest {

    private static Map<Icon, Long> buffs(final Object... iconsAndSeconds) {
        Map<Icon, Long> buffs = new LinkedHashMap<Icon, Long>();
        for (int i = 0; i < iconsAndSeconds.length; i += 2)
            buffs.put((Icon) iconsAndSeconds[i], ((Number) iconsAndSeconds[i + 1]).longValue());
        return buffs;
    }

    @Test
    void theBuffThatEndsFirstComesFirst() {
        ItemStatus status = new ItemStatus(0, buffs(Icon.BUFF, 9, Icon.SHIELD, 4), 0);
        assertEquals(Arrays.asList(Icon.SHIELD, Icon.BUFF), status.getBuffs());
    }

    @Test
    void aBuffInItsLastSecondsBlinksButKeepsItsPlace() {
        assertTrue(BuffHud.isShown(5, 250), "a buff with time left never blinks");
        assertTrue(BuffHud.isShown(2, 0));
        assertFalse(BuffHud.isShown(2, 250), "in its last seconds it is off every other 250 ms");
        ItemStatus status = new ItemStatus(0, buffs(Icon.SHIELD, 1, Icon.BUFF, 8), 250);
        assertEquals(2, status.getBuffs().size());
        assertFalse(status.isShown(0));
        assertTrue(status.isShown(1));
    }

    @Test
    void nothingToShowWithoutGrenadesOrBuffs() {
        assertTrue(new ItemStatus(0, Collections.<Icon, Long>emptyMap(), 0).isEmpty());
        assertTrue(new ItemStatus(-2, Collections.<Icon, Long>emptyMap(), 0).isEmpty());
        assertFalse(new ItemStatus(1, Collections.<Icon, Long>emptyMap(), 0).isEmpty());
        assertFalse(new ItemStatus(0, buffs(Icon.SHIELD, 3), 0).isEmpty());
    }

    @Test
    void anActiveShieldShowsTheShieldIcon() {
        Ship ship = new Ship(100, 100, ShipType.STANDARD);
        ItemManager items = new ItemManager(520);
        items.spawnItem(new ShieldItem(100, 100));
        items.checkCollisions(ship);
        Map<Icon, Long> icons = items.getBuffManager().getActiveBuffIcons();
        assertEquals(Collections.singleton(Icon.SHIELD), icons.keySet());
        assertTrue(icons.get(Icon.SHIELD) >= 14, "a fresh shield lasts 15 seconds");
    }

    @Test
    void theStripDrawsOnlyWhatThereIsAndSeparatesTwoPlayers() throws Exception {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(448, 520, 60);
        Field buffer = DrawManager.class.getDeclaredField("backBuffer");
        buffer.setAccessible(true);
        Path directory = Paths.get("out", "hud-tests");
        Files.createDirectories(directory);

        ItemStatus none = new ItemStatus(0, Collections.<Icon, Long>emptyMap(), 0);
        ItemStatus one = new ItemStatus(3, buffs(Icon.SHIELD, 9), 0);
        ItemStatus other = new ItemStatus(1, buffs(Icon.SHIELD, 5, Icon.BUFF, 7), 0);

        draw.initDrawing(screen);
        assertEquals(0, draw.drawItemStrip(screen, Arrays.asList(none)),
                "an empty strip leaves all of it free");
        assertEquals(0, draw.drawItemStrip(screen, Arrays.asList(none, none)));
        int single = draw.drawItemStrip(screen, Arrays.asList(one));
        draw.completeDrawing(screen);

        draw.initDrawing(screen);
        int both = draw.drawItemStrip(screen, Arrays.asList(one, other));
        javax.imageio.ImageIO.write((BufferedImage) buffer.get(null), "png",
                directory.resolve("strip-two-players.png").toFile());
        draw.completeDrawing(screen);

        assertTrue(single > 0 && single < screen.getWidth() / 3,
                "one player's grenades and shield take little room: " + single);
        assertTrue(both > single, "the second player's icons follow the bar");
        assertTrue(both < screen.getWidth() / 2,
                "two players still leave room for the notice: " + both);
    }
}
