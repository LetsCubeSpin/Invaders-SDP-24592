package screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import engine.Core;
import engine.DrawManager;
import engine.GameColors;
import records.Achievement;
import records.AchievementUnlock;
import vfx.RankBadge;

/** The new achievements on the results screen: icons, tier colors, pages and room. */
class ScoreScreenAchievementsTest {

    /** Game screen size inside the window, as the records checks draw it. */
    private static final int WIDTH = 432;
    private static final int HEIGHT = 490;

    /** Every tier of every achievement, more than one page holds. */
    private static List<AchievementUnlock> manyUnlocks(final int count) {
        List<AchievementUnlock> unlocks = new ArrayList<AchievementUnlock>();
        for (Achievement achievement : Achievement.values())
            for (int tier = 1; tier <= achievement.getTierCount(); tier++)
                unlocks.add(new AchievementUnlock(achievement, tier));
        assertTrue(unlocks.size() >= count, "enough unlocks to list");
        return unlocks.subList(0, count);
    }

    @Test
    void thePagesTakeTurnsOnlyWhenTheyDoNotFitOnOne() {
        assertEquals(1, DrawManager.getResultAchievementPageCount(0));
        assertEquals(1, DrawManager.getResultAchievementPageCount(6));
        assertEquals(2, DrawManager.getResultAchievementPageCount(7));
        assertEquals(3, DrawManager.getResultAchievementPageCount(13));
        long page = ScoreScreen.ACHIEVEMENT_PAGE_MILLIS;
        assertEquals(0, ScoreScreen.achievementPage(6, page * 5), "one page never turns");
        assertEquals(0, ScoreScreen.achievementPage(8, 0));
        assertEquals(0, ScoreScreen.achievementPage(8, page - 1));
        assertEquals(1, ScoreScreen.achievementPage(8, page));
        assertEquals(0, ScoreScreen.achievementPage(8, page * 2), "back to the first page");
        assertEquals(2, ScoreScreen.achievementPage(13, page * 2));
    }

    /** What drawing the results changed: each changed pixel's color, or 0. */
    private static int[][] drawResults(final List<AchievementUnlock> unlocks,
            final boolean newRecord, final int failedStage, final int page,
            final String imageName) throws Exception {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(WIDTH, HEIGHT, 60);
        Field buffer = DrawManager.class.getDeclaredField("backBuffer");
        buffer.setAccessible(true);
        draw.initDrawing(screen);
        BufferedImage image = (BufferedImage) buffer.get(null);
        BufferedImage before = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        before.getGraphics().drawImage(image, 0, 0, null);
        draw.drawAchievementResults(screen, 10000, 0, 20, 0.5f, newRecord,
                new char[] { 'A', 'A', 'A' }, 0, unlocks, true, failedStage,
                false, "mode: Easy", page);
        Path directory = Paths.get("out", "results-tests");
        Files.createDirectories(directory);
        javax.imageio.ImageIO.write(image, "png", directory.resolve(imageName + ".png").toFile());
        int[][] changed = new int[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++)
            for (int y = 0; y < HEIGHT; y++)
                if (image.getRGB(x, y) != before.getRGB(x, y))
                    changed[x][y] = image.getRGB(x, y);
        draw.completeDrawing(screen);
        return changed;
    }

    private static boolean rowChanged(final int[][] changed, final int y) {
        for (int x = 0; x < WIDTH; x++)
            if (changed[x][y] != 0)
                return true;
        return false;
    }

    private static boolean hasColor(final int[][] changed, final Color color) {
        for (int[] column : changed)
            for (int rgb : column)
                if (rgb != 0 && (rgb & 0xFFFFFF) == (color.getRGB() & 0xFFFFFF))
                    return true;
        return false;
    }

    @Test
    void theFullestResultsKeepAGapAboveThePrompt() throws Exception {
        // A failed stage, a new record and a full page of achievements.
        int[][] changed = drawResults(manyUnlocks(6), true, 3, 0, "fullest");
        int y = HEIGHT - 1;
        while (!rowChanged(changed, y))
            y--;
        while (rowChanged(changed, y))
            y--; // the prompt
        int gap = 0;
        while (y >= 0 && !rowChanged(changed, y)) {
            gap++;
            y--;
        }
        assertTrue(gap >= 8, "the last achievement is " + gap + " px above the prompt");
        assertTrue(rowChanged(changed, y), "the achievements are drawn above the gap");
        int top = 0;
        while (!rowChanged(changed, top))
            top++;
        assertTrue(top >= 8, "the title stays on screen, at y " + top);
    }

    @Test
    void tieredNamesTakeTheirTierColorAndOthersATrophy() throws Exception {
        int[][] silver = drawResults(Arrays.asList(
                new AchievementUnlock(Achievement.EXTERMINATOR, 2)), false, 0, 0, "silver");
        assertTrue(hasColor(silver, RankBadge.tierColor(2)), "the silver medal and name");
        assertFalse(hasColor(silver, RankBadge.tierColor(1)), "no bronze");
        int[][] single = drawResults(Arrays.asList(
                new AchievementUnlock(Achievement.FIRST_RUN, 1)), false, 0, 0, "single");
        assertTrue(hasColor(single, GameColors.ICON_COIN), "the trophy");
        assertTrue(hasColor(single, Color.WHITE), "the name in white");
    }

    @Test
    void theSecondPageListsTheRest() throws Exception {
        List<AchievementUnlock> unlocks = manyUnlocks(8);
        int[][] first = drawResults(unlocks, false, 0, 0, "page-1");
        int[][] second = drawResults(unlocks, false, 0, 1, "page-2");
        int[][] wrapped = drawResults(unlocks, false, 0, 2, "page-3-wraps");
        assertTrue(countRows(second) < countRows(first), "two rows on the second page, six on the first");
        assertFalse(Arrays.deepEquals(first, second), "the pages differ");
        assertTrue(Arrays.deepEquals(first, wrapped), "the third turn shows the first page again");
    }

    /** @return how many pixel rows have something drawn. */
    private static int countRows(final int[][] changed) {
        int rows = 0;
        for (int y = 0; y < HEIGHT; y++)
            if (rowChanged(changed, y))
                rows++;
        return rows;
    }
}
