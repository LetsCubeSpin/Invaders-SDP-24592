package engine;

import boss.BossHudInfo;

import java.awt.Color;
import java.awt.Font;
import screen.CustomLevelListScreen;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import screen.PlayerRecordsScreen;
import screen.Screen;
import screen.ShipCustomization;
import hud.FloatingScoreHud;
import hud.AchievementToast;
import hud.ItemStatus;
import records.AchievementStatus;
import records.AchievementUnlock;
import records.RecordsService;
import records.RunRecord;
import records.RunRecordFormatter;
import records.CumulativeStats;
import records.RunFilter;
import records.RunMode;
import records.RunOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import entity.Entity;
import entity.Grenade;
import entity.GrenadeFragment;
import entity.item.ClusterGrenadeItem;
import entity.Ship;
import entity.Bullet;
import vfx.Icon;
import vfx.RankBadge;
import vfx.ShipSprites;
import screen.GameModeScreen;

/**
 * Manages screen drawing.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class DrawManager {
    private int getNormalGameOverTitleY(final Screen screen) {
        int regularHeight = fontRegularMetrics.getHeight();
        int bigHeight = fontBigMetrics.getHeight();

        int promptY = screen.getHeight() / 2 + regularHeight * 10;
        int titleToAccuracy = bigHeight + regularHeight * 8;

        return (promptY
                - fontRegularMetrics.getAscent()
                - titleToAccuracy
                - fontRegularMetrics.getDescent()
                + fontBigMetrics.getAscent()) / 2;
    }

    /** Most rows of newly unlocked achievements on one results page. */
    private static final int MAX_RESULT_ACHIEVEMENTS = 6;
    /** Gap between an achievement's icon and its name on the results screen. */
    private static final int RESULT_ICON_GAP = 6;

    /**
     * @param achievementCount
     *            Number of achievements the run unlocked.
     * @return how many pages the results screen needs to list them all.
     */
    public static int getResultAchievementPageCount(final int achievementCount) {
        return Math.max(1, (achievementCount + MAX_RESULT_ACHIEVEMENTS - 1)
                / MAX_RESULT_ACHIEVEMENTS);
    }

    private static final DateTimeFormatter ACHIEVEMENT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a", Locale.US);

    /**
     * Centers the complete achievement result block above the input prompt.
     *
     * @param screen
     *                    Screen to draw on.
     * @param isNewRecord
     *                    If the score is a new high score.
     * @param failedStage
     *                    Failed stage number, or zero when no stage failed.
     * @param resultRows
     *                    Number of result rows, the mode included.
     * @param achievementCount
     *                    Number of achievement rows on a page.
     * @return Baseline for the game-over title.
     */
    private int getAchievementResultsTitleY(final Screen screen,
            final boolean isNewRecord, final int failedStage,
            final int resultRows, final int achievementCount) {
        int spacing = fontRegularMetrics.getHeight();
        int lastLineOffset = 0;

        if (failedStage > 0)
            lastLineOffset += spacing;

        // Gap before the result rows, including the increment after the
        // last row used by drawAchievementResults.
        lastLineOffset += spacing * 2;
        lastLineOffset += spacing * resultRows;

        if (isNewRecord) {
            // Gap before the three name-input rows and the increment after
            // that block.
            lastLineOffset += spacing;
            lastLineOffset += spacing * 3;
        }

        // Achievement heading followed by one row per unlocked achievement.
        lastLineOffset += spacing;
        lastLineOffset += spacing * achievementCount;

        int promptY = screen.getHeight() - spacing;
        int availableTop = spacing;
        int availableBottom = promptY - spacing * 2;
        int blockHeight = fontBigMetrics.getAscent() + lastLineOffset
                + fontRegularMetrics.getDescent();
        int availableHeight = availableBottom - availableTop;
        int blockTop = availableTop
                + Math.max(0, (availableHeight - blockHeight) / 2);

        return blockTop + fontBigMetrics.getAscent();
    }

    /** Singleton instance of the class. */
    private static DrawManager instance;
    /** Current frame. */
    private static Frame frame;
    /** FileManager instance. */
    private static FileManager fileManager;
    /** Application logger. */
    private static Logger logger;
    /** Graphics context. */
    private static Graphics graphics;
    /** Buffer Graphics. */
    private static Graphics backBufferGraphics;
    /** Buffer image. */
    private static BufferedImage backBuffer;
    /** Normal sized font. */
    private static Font fontRegular;
    /** Normal sized font properties. */
    private static FontMetrics fontRegularMetrics;
    /** Big sized font. */
    private static Font fontBig;
    /** Big sized font properties. */
    private static FontMetrics fontBigMetrics;

    /** Options of the settings menu, in order. */
    private static final String[] SETTINGS_MENU_OPTIONS = { "Audio", "Controls", "Color", "Back" };
    /** Width of the column holding the audio settings. */
    private static final int AUDIO_COLUMN_WIDTH = 200;
    /** Space between an audio option name and its volume bar. */
    private static final int AUDIO_BAR_GAP = 6;
    /** Height of the audio volume bars. */
    private static final int AUDIO_BAR_HEIGHT = 8;

    /** Sprite types mapped to their images. */
    private static Map<SpriteType, boolean[][]> spriteMap;

    /** Sprite types. */
    public static enum SpriteType {
        /** Player ship. */
        Ship,
        /** Destroyed player ship. */
        ShipDestroyed,
        /** Player bullet. */
        Bullet,
        /** Enemy bullet. */
        EnemyBullet,
        /** First enemy ship - first form. */
        EnemyShipA1,
        /** First enemy ship - second form. */
        EnemyShipA2,
        /** Second enemy ship - first form. */
        EnemyShipB1,
        /** Second enemy ship - second form. */
        EnemyShipB2,
        /** Third enemy ship - first form. */
        EnemyShipC1,
        /** Third enemy ship - second form. */
        EnemyShipC2,
        /** Bonus ship. */
        EnemyShipSpecial,
        /** Destroyed enemy ship. */
        Explosion,
        /** Falling hazard. */
        FallingObstacle
    };

    /**
     * Private constructor.
     */
    private DrawManager() {
        fileManager = Core.getFileManager();
        logger = Core.getLogger();
        logger.info("Started loading resources.");

        try {
            spriteMap = new LinkedHashMap<SpriteType, boolean[][]>();

            spriteMap.put(SpriteType.Ship, new boolean[13][8]);
            spriteMap.put(SpriteType.ShipDestroyed, new boolean[13][8]);
            spriteMap.put(SpriteType.Bullet, new boolean[3][5]);
            spriteMap.put(SpriteType.EnemyBullet, new boolean[3][5]);
            spriteMap.put(SpriteType.EnemyShipA1, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipA2, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipB1, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipB2, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipC1, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipC2, new boolean[12][8]);
            spriteMap.put(SpriteType.EnemyShipSpecial, new boolean[16][7]);
            spriteMap.put(SpriteType.Explosion, new boolean[13][7]);
            spriteMap.put(SpriteType.FallingObstacle, new boolean[13][7]);

            fileManager.loadSprite(spriteMap);
            logger.info("Finished loading the sprites.");

            // Font loading.
            fontRegular = fileManager.loadFont(14f);
            fontBig = fileManager.loadFont(24f);
            logger.info("Finished loading the fonts.");

        } catch (IOException e) {
            logger.warning("Loading failed.");
        } catch (FontFormatException e) {
            logger.warning("Font formating failed.");
        }
    }

    /**
     * Returns shared instance of DrawManager.
     *
     * @return Shared instance of DrawManager.
     */
    protected static DrawManager getInstance() {
        if (instance == null)
            instance = new DrawManager();
        return instance;
    }

    /**
     * Sets the frame to draw the image on.
     *
     * @param currentFrame
     *                     Frame to draw on.
     */
    public void setFrame(final Frame currentFrame) {
        frame = currentFrame;
    }

    /**
     * Draws an entity, using the apropiate image.
     *
     * @param entity
     *                  Entity to be drawn.
     * @param positionX
     *                  Coordinates for the left side of the image.
     * @param positionY
     *                  Coordinates for the upper side of the image.
     */
    public void drawEntity(final Entity entity, final int positionX,
            final int positionY) {
        boolean[][] image = spriteMap.get(entity.getSpriteType());

        if (entity instanceof Ship
                && entity.getSpriteType() == SpriteType.Ship) {
            image = ShipSprites.forType(((Ship) entity).getShipType());
        }

        if (entity instanceof Bullet
                && entity.getSpriteType() == SpriteType.Bullet) {
            drawPixels(ShipSprites.playerBullet(), positionX, positionY);
            return;
        }

        if (entity instanceof Bullet
                && entity.getSpriteType() == SpriteType.EnemyBullet) {
            drawPixels(ShipSprites.enemyBullet(), positionX, positionY);
            return;
        }

        backBufferGraphics.setColor(entity.getColor());
        for (int i = 0; i < image.length; i++)
            for (int j = 0; j < image[i].length; j++)
                if (image[i][j])
                    backBufferGraphics.drawRect(positionX + i * 2, positionY
                            + j * 2, 1, 1);
    }

    /**
     * Draws multi-colored sprite pixels at the gameplay sprite scale.
     *
     * @param pixels
     *                  Pixel colors indexed [x][y]; null where empty.
     * @param positionX
     *                  Coordinates for the left side of the image.
     * @param positionY
     *                  Coordinates for the upper side of the image.
     */
    private void drawPixels(final Color[][] pixels, final int positionX,
            final int positionY) {
        for (int i = 0; i < pixels.length; i++)
            for (int j = 0; j < pixels[i].length; j++)
                if (pixels[i][j] != null) {
                    backBufferGraphics.setColor(pixels[i][j]);
                    backBufferGraphics.drawRect(positionX + i * 2,
                            positionY + j * 2, 1, 1);
                }
    }

    /**
     * For debugging purpouses, draws the canvas borders.
     *
     * @param screen
     *               Screen to draw in.
     */
    @SuppressWarnings("unused")
    private void drawBorders(final Screen screen) {
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawLine(0, 0, screen.getWidth() - 1, 0);
        backBufferGraphics.drawLine(0, 0, 0, screen.getHeight() - 1);
        backBufferGraphics.drawLine(screen.getWidth() - 1, 0,
                screen.getWidth() - 1, screen.getHeight() - 1);
        backBufferGraphics.drawLine(0, screen.getHeight() - 1,
                screen.getWidth() - 1, screen.getHeight() - 1);
    }

    /**
     * For debugging purpouses, draws a grid over the canvas.
     *
     * @param screen
     *               Screen to draw in.
     */
    @SuppressWarnings("unused")
    private void drawGrid(final Screen screen) {
        backBufferGraphics.setColor(Color.DARK_GRAY);
        for (int i = 0; i < screen.getHeight() - 1; i += 2)
            backBufferGraphics.drawLine(0, i, screen.getWidth() - 1, i);
        for (int j = 0; j < screen.getWidth() - 1; j += 2)
            backBufferGraphics.drawLine(j, 0, j, screen.getHeight() - 1);
    }

    /**
     * Draws current score on screen.
     *
     * @param screen
     *               Screen to draw on.
     * @param score
     *               Current score.
     */
    public void drawScore(final Screen screen, final int score) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.WHITE);
        String scoreString = String.format("%04d", score);

        int scoreX = screen.getWidth() - 60;
        drawIcon(Icon.SCORE, scoreX - Icon.SCORE.getWidth() * 2 - 6, 12, 2);
        backBufferGraphics.drawString(scoreString, scoreX, 25);
    }

    /** Draws boss health in the free row between the score and separator. */
    public void drawBossHealthBar(final Screen screen, final BossHudInfo boss) {
        if (boss == null || !boss.isVisible() || boss.getMaxHealth() <= 0
                || boss.getCurrentHealth() <= 0) {
            return;
        }

        Graphics hud = backBufferGraphics.create();
        try {
            hud.setFont(fontRegular.deriveFont(10f));
            FontMetrics metrics = hud.getFontMetrics();
            String name = boss.getBossType().name().replace('_', ' ');
            int maximum = boss.getMaxHealth();
            int current = Math.min(boss.getCurrentHealth(), maximum);
            String health = current + " / " + maximum;
            int right = screen.getWidth() - 20;
            int barX = 20 + metrics.stringWidth(name) + 8;
            int barWidth = right - metrics.stringWidth(maximum + " / " + maximum)
                    - 8 - barX;
            // Keep the full bar width fixed as the current HP loses digits.
            int filledWidth = Math.max(1, (int) ((long) barWidth * current / maximum));

            hud.setColor(Color.BLACK);
            hud.fillRect(18, 29, screen.getWidth() - 36, 10);
            hud.setColor(Color.WHITE);
            hud.drawString(name, 20, 37);
            hud.drawString(health, right - metrics.stringWidth(health), 37);
            hud.setColor(Color.DARK_GRAY);
            hud.fillRect(barX, 31, barWidth, 6);
            hud.setColor(Color.MAGENTA);
            hud.fillRect(barX, 31, filledWidth, 6);
            hud.setColor(Color.GRAY);
            hud.drawRect(barX - 1, 30, barWidth + 1, 7);
        } finally {
            hud.dispose();
        }
    }

    /**
     * Draws remaining lives as heart icons on screen.
     *
     * @param screen
     *               Screen to draw on.
     * @param lives
     *               Current lives.
     */
    public void drawLives(final Screen screen, final int lives) {
        drawLives(screen, lives, lives);
    }

    /**
     * Draws remaining lives as heart icons and lost lives as broken hearts.
     *
     * @param screen
     *                 Screen to draw on.
     * @param lives
     *                 Current lives.
     * @param maxLives
     *                 Lives the player started with.
     */
    public void drawLives(final Screen screen, final int lives,
            final int maxLives) {
        for (int i = 0; i < Math.max(lives, maxLives); i++)
            drawIcon(i < lives ? Icon.LIFE : Icon.LIFE_LOST,
                    20 + 22 * i, 12, 2);
    }

    /** Shows a life pool that would overrun the space reserved for heart icons. */
    public void drawLifeCount(final int lives, final int maxLives) {
        drawIcon(lives > 0 ? Icon.LIFE : Icon.LIFE_LOST, 20, 12, 2);
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.WHITE);
        backBufferGraphics.drawString(lives + "/" + maxLives, 42, 25);
    }

    /**
     * Draws a UI icon in its own colors.
     *
     * @param icon
     *                  Icon to draw.
     * @param positionX
     *                  Coordinates for the left side of the icon.
     * @param positionY
     *                  Coordinates for the upper side of the icon.
     * @param scale
     *                  Size in screen pixels of each icon pixel.
     */
    public void drawIcon(final Icon icon, final int positionX,
            final int positionY, final int scale) {
        drawIconPixels(icon, positionX, positionY, scale, null, false);
    }

    /**
     * Draws a UI icon in its own colors, darkened to show it is inactive.
     *
     * @param icon
     *                  Icon to draw.
     * @param positionX
     *                  Coordinates for the left side of the icon.
     * @param positionY
     *                  Coordinates for the upper side of the icon.
     * @param scale
     *                  Size in screen pixels of each icon pixel.
     */
    public void drawDimmedIcon(final Icon icon, final int positionX,
            final int positionY, final int scale) {
        drawIconPixels(icon, positionX, positionY, scale, null, true);
    }

    /**
     * Draws a UI icon in the given color.
     *
     * @param icon
     *                  Icon to draw.
     * @param positionX
     *                  Coordinates for the left side of the icon.
     * @param positionY
     *                  Coordinates for the upper side of the icon.
     * @param scale
     *                  Size in screen pixels of each icon pixel.
     * @param color
     *                  Color of the icon.
     */
    public void drawIcon(final Icon icon, final int positionX,
            final int positionY, final int scale, final Color color) {
        drawIconPixels(icon, positionX, positionY, scale, color, false);
    }

    private void drawIconPixels(final Icon icon, final int positionX,
            final int positionY, final int scale, final Color override,
            final boolean dimmed) {
        Color previous = backBufferGraphics.getColor();
        for (int i = 0; i < icon.getWidth(); i++)
            for (int j = 0; j < icon.getHeight(); j++) {
                Color color = icon.getColor(i, j);
                if (color == null)
                    continue;
                if (override != null)
                    color = override;
                if (dimmed)
                    color = new Color(color.getRed() / 2,
                            color.getGreen() / 2, color.getBlue() / 2);
                backBufferGraphics.setColor(color);
                backBufferGraphics.fillRect(positionX + i * scale,
                        positionY + j * scale, scale, scale);
            }
        backBufferGraphics.setColor(previous);
    }

    /**
     * Draws the pause overlay: a pause icon with a "Paused" label.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawPaused(final Screen screen) {
        int scale = 4;
        drawIcon(Icon.PAUSE, screen.getWidth() / 2 - Icon.PAUSE.getWidth()
                * scale / 2, screen.getHeight() / 2 - 50, scale);
        backBufferGraphics.setColor(GameColors.TEXT);
        drawCenteredBigString(screen, "Paused", screen.getHeight() / 2 + 20);
    }

    /**
     * Draws an icon to the left of a centered menu label. The selected
     * option's icon is in full color and bobs; the others are dimmed.
     *
     * @param screen
     *                 Screen to draw on.
     * @param icon
     *                 Icon to draw.
     * @param label
     *                 Centered label the icon sits next to.
     * @param labelY
     *                 Baseline of the label.
     * @param selected
     *                 Whether the option is selected.
     */
    private void drawMenuIcon(final Screen screen, final Icon icon,
            final String label, final int labelY, final boolean selected) {
        int iconX = screen.getWidth() / 2
                - fontRegularMetrics.stringWidth(label) / 2
                - icon.getWidth() * 2 - 8;
        int iconY = labelY - 5 - icon.getHeight();
        if (selected)
            drawIcon(icon, iconX, iconY + (int) Math.round(
                    Math.sin(System.currentTimeMillis() / 150.0) * 2), 2);
        else
            drawDimmedIcon(icon, iconX, iconY, 2);
    }

    /**
     * Draws a grid of pixel colors.
     *
     * @param pixels
     *                  Pixel colors indexed [x][y]; null where empty.
     * @param positionX
     *                  Coordinates for the left side of the grid.
     * @param positionY
     *                  Coordinates for the upper side of the grid.
     * @param scale
     *                  Size in screen pixels of each grid pixel.
     */
    private void drawColorGrid(final Color[][] pixels, final int positionX,
            final int positionY, final int scale) {
        Color previous = backBufferGraphics.getColor();
        for (int i = 0; i < pixels.length; i++)
            for (int j = 0; j < pixels[i].length; j++)
                if (pixels[i][j] != null) {
                    backBufferGraphics.setColor(pixels[i][j]);
                    backBufferGraphics.fillRect(positionX + i * scale,
                            positionY + j * scale, scale, scale);
                }
        backBufferGraphics.setColor(previous);
    }

    /**
     * Draws current level progress on screen.
     *
     * @param screen
     *                    Screen to draw on.
     * @param level
     *                    Current level.
     * @param totalLevels
     *                    Total number of levels.
     */
    public void drawLevel(final Screen screen, final int level,
            final int totalLevels) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(Color.WHITE);

        String levelString = "LEVEL " + level + "/" + totalLevels;
        int textWidth = backBufferGraphics.getFontMetrics().stringWidth(levelString);
        int levelX = (screen.getWidth() - textWidth) / 2;

        backBufferGraphics.drawString(levelString, levelX, 25);
    }

    /**
     * Draws a thick line from side to side of the screen.
     *
     * @param screen
     *                  Screen to draw on.
     * @param positionY
     *                  Y coordinate of the line.
     */
    public void drawHorizontalLine(final Screen screen, final int positionY) {
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawLine(0, positionY, screen.getWidth(), positionY);
        backBufferGraphics.drawLine(0, positionY + 1, screen.getWidth(),
                positionY + 1);
    }

    /**
     * Draws the Orbital Annihilator: a growing energy charge on the ship's
     * nose while charging, then a full vertical beam going up from the ship.
     */
    public void drawOrbitalAnnihilator(final Ship ship, final int beamWidth,
            final int topY, final boolean firing, final float chargeProgress) {
        int centerX = ship.getPositionX() + ship.getWidth() / 2;
        int startY = ship.getPositionY();
        int beamX = centerX - beamWidth / 2;

        if (firing) {
            backBufferGraphics.setColor(new Color(255, 0, 0, 120));
            backBufferGraphics.fillRect(beamX - 4, topY, beamWidth + 8, startY - topY);

            backBufferGraphics.setColor(Color.RED);
            backBufferGraphics.fillRect(beamX, topY, beamWidth, startY - topY);

            backBufferGraphics.setColor(Color.WHITE);
            backBufferGraphics.fillRect(centerX - beamWidth / 4, topY, beamWidth / 2, startY - topY);
        } else {
            backBufferGraphics.setColor(new Color(255, 0, 0, 90));
            backBufferGraphics.fillRect(centerX - 1, topY, 2, startY - topY);

            int radius = 2 + (int) (chargeProgress * beamWidth / 2);
            boolean flicker = ((int) (chargeProgress * 20)) % 2 == 0;

            backBufferGraphics.setColor(flicker ? Color.WHITE : Color.RED);
            backBufferGraphics.fillOval(centerX - radius, startY - radius, radius * 2, radius * 2);
        }
    }

    /**
     * Draws game title.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawTitle(final Screen screen) {
        String titleString = "Invaders";
        String instructionsString = "select with w+s / arrows, confirm with space";

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, instructionsString,
                screen.getHeight() / 2);

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, titleString, screen.getHeight() / 3);

        long time = System.currentTimeMillis();
        int scale = 4;
        int shipX = screen.getWidth() / 2 - Icon.TITLE_SHIP.getWidth()
                * scale / 2;
        int shipY = screen.getHeight() / 3 - 40
                - (Icon.TITLE_SHIP.getHeight() + 3) * scale
                + (int) Math.round(Math.sin(time / 400.0) * 3);
        drawIcon(Icon.TITLE_SHIP, shipX, shipY, scale);
        drawIcon((time / 90) % 2 == 0 ? Icon.TITLE_FLAME_LONG
                : Icon.TITLE_FLAME_SHORT, shipX,
                shipY + Icon.TITLE_SHIP.getHeight() * scale, scale);
    }

    /**
     * Draws game results.
     *
     * @param screen
     *                       Screen to draw on.
     * @param score
     *                       Score obtained.
     * @param livesRemaining
     *                       Lives remaining when finished.
     * @param shipsDestroyed
     *                       Total ships destroyed.
     * @param accuracy
     *                       Total accuracy.
     * @param isNewRecord
     *                       If the score is a new high score.
     */
    public void drawResults(final Screen screen, final int score,
            final int livesRemaining, final int shipsDestroyed,
            final float accuracy, final boolean isNewRecord) {
        String scoreString = String.format("score %04d", score);
        String livesRemainingString = "lives remaining " + livesRemaining;
        String shipsDestroyedString = "enemies destroyed " + shipsDestroyed;
        String accuracyString = String
                .format("accuracy %.2f%%", accuracy * 100);

        int resultsY;

        if (isNewRecord)
            resultsY = screen.getHeight() / 4;
        else
            resultsY = getNormalGameOverTitleY(screen)
                    + fontBigMetrics.getHeight()
                    + fontRegularMetrics.getHeight() * 2;

        backBufferGraphics.setColor(Color.WHITE);

        drawCenteredRegularString(screen, scoreString, resultsY);

        drawCenteredRegularString(
                screen,
                livesRemainingString,
                resultsY + fontRegularMetrics.getHeight() * 2);

        drawCenteredRegularString(
                screen,
                shipsDestroyedString,
                resultsY + fontRegularMetrics.getHeight() * 4);

        drawCenteredRegularString(
                screen,
                accuracyString,
                resultsY + fontRegularMetrics.getHeight() * 6);
    }

    /**
     * Draws basic content of game over screen.
     *
     * @param screen
     *                     Screen to draw on.
     * @param acceptsInput
     *                     If the screen accepts input.
     * @param isNewRecord
     *                     If the score is a new high score.
     * @param gameCleared
     *                     If the game was cleared successfully.
     */
    public void drawGameOver(final Screen screen, final boolean acceptsInput,
            final boolean isNewRecord, final boolean gameCleared) {
        String gameOverString = gameCleared ? "Game Clear" : "Game Over";
        String continueOrExitString = "Press Space to play again, Escape to exit";

        int gameOverY;

        if (isNewRecord)
            gameOverY = screen.getHeight() / 4
                    - fontBigMetrics.getHeight() * 2;
        else
            gameOverY = getNormalGameOverTitleY(screen);

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, gameOverString, gameOverY);

        if (acceptsInput)
            backBufferGraphics.setColor(Color.GREEN);
        else
            backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, continueOrExitString,
                screen.getHeight() / 2 + fontRegularMetrics.getHeight() * 10);
    }

    /**
     * Draws which stage was failed, right below the game over title.
     *
     * @param screen
     *                    Screen to draw on.
     * @param stage
     *                    Number of the stage that was failed.
     * @param isNewRecord
     *                    If the score is a new high score (changes the layout).
     */
    public void drawStageFailed(final Screen screen, final int stage,
            final boolean isNewRecord) {
        int stageFailedY;

        if (isNewRecord)
            stageFailedY = screen.getHeight() / 4
                    - fontBigMetrics.getHeight();
        else
            stageFailedY = getNormalGameOverTitleY(screen)
                    + fontBigMetrics.getHeight();

        backBufferGraphics.setColor(Color.RED);

        drawCenteredRegularString(
                screen,
                "Stage " + stage + " Failed",
                stageFailedY);
    }

    /** Draws the game mode as one more row of the results list. */
    public void drawModeLabel(final Screen screen, final String modeLabel,
            final boolean isNewRecord) {
        int height = isNewRecord ? 4 : 2;
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, modeLabel, screen.getHeight()
                / height + fontRegularMetrics.getHeight() * 8);
    }

    /**
     * Draws high score screen title and instructions.
     *
     * @param screen
     *               Screen to draw on.
     */
    public void drawHighScoreMenu(final Screen screen) {
        String highScoreString = "High Scores";
        String instructionsString = "Press Space or Esc to return";

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, highScoreString, screen.getHeight() / 8);

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, instructionsString,
                screen.getHeight() / 5);
    }

    /**
     * Draws high scores.
     *
     * @param screen
     *                   Screen to draw on.
     * @param highScores
     *                   List of high scores.
     */
    public void drawHighScores(final Screen screen,
            final List<Score> highScores) {
        backBufferGraphics.setColor(Color.WHITE);
        int i = 0;
        String scoreString = "";

        for (Score score : highScores) {
            scoreString = String.format("%s        %04d", score.getName(),
                    score.getScore());
            int rowY = screen.getHeight() / 4
                    + fontRegularMetrics.getHeight() * (i + 1) * 2;
            drawCenteredRegularString(screen, scoreString, rowY);
            drawColorGrid(RankBadge.forRank(i + 1), screen.getWidth() / 2
                    - fontRegularMetrics.stringWidth(scoreString) / 2
                    - RankBadge.WIDTH * 2 - 12,
                    rowY - 5 - RankBadge.HEIGHT - 4, 2);
            i++;
        }
    }

    /**
     * Draws a centered string on regular font.
     *
     * @param screen
     *               Screen to draw on.
     * @param string
     *               String to draw.
     * @param height
     *               Height of the drawing.
     */
    public void drawCenteredRegularString(final Screen screen,
            final String string, final int height) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.drawString(string, screen.getWidth() / 2
                - fontRegularMetrics.stringWidth(string) / 2, height);
    }

    /**
     * Draws a centered string on big font.
     *
     * @param screen
     *               Screen to draw on.
     * @param string
     *               String to draw.
     * @param height
     *               Height of the drawing.
     */
    public void drawCenteredBigString(final Screen screen, final String string,
            final int height) {
        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.drawString(string, screen.getWidth() / 2
                - fontBigMetrics.stringWidth(string) / 2, height);
    }

    /**
     * Countdown to game start.
     *
     * @param screen
     *                  Screen to draw on.
     * @param level
     *                  Level number.
     * @param number
     *                  Countdown number.
     * @param bonusLife
     *                  Checks if a bonus life is received.
     */
    public void drawCountDown(final Screen screen, final int level,
            final int number, final boolean bonusLife) {
        String title = "Level " + level;
        if (bonusLife)
            title += " - Bonus life!";

        drawCountDown(screen, title, number);
    }

    /**
     * Draws the countdown with the given title.
     *
     * @param screen
     *               Screen to draw on.
     * @param title
     *               Title shown before the countdown numbers.
     * @param number
     *               Countdown number.
     */
    public void drawCountDown(final Screen screen, final String title,
            final int number) {
        int rectWidth = screen.getWidth();
        int rectHeight = screen.getHeight() / 6;

        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics.fillRect(0,
                screen.getHeight() / 2 - rectHeight / 2,
                rectWidth, rectHeight);
        backBufferGraphics.setColor(Color.GREEN);

        if (number >= 4)
            drawCenteredBigString(screen, title,
                    screen.getHeight() / 2 + fontBigMetrics.getHeight() / 3);
        else if (number != 0)
            drawCenteredBigString(screen, Integer.toString(number),
                    screen.getHeight() / 2 + fontBigMetrics.getHeight() / 3);
        else
            drawCenteredBigString(screen, "GO!",
                    screen.getHeight() / 2 + fontBigMetrics.getHeight() / 3);
    }

    /** Left edge of the pilot portrait in story dialogue. */
    private static final int DIALOGUE_PORTRAIT_X = 5;
    /** Height of the boxes, and their gap below the lower line. */
    private static final int DIALOGUE_BOX_HEIGHT = 160;
    private static final int DIALOGUE_BOX_GAP = 19;
    /** Space kept free below the boxes. */
    private static final int DIALOGUE_BOTTOM_MARGIN = 30;
    /** Gap between the portrait and the text box. */
    private static final int DIALOGUE_BOX_SPACING = 14;
    private static final int DIALOGUE_TEXT_PADDING = 10;

    /**
     * Splits a text into lines that fit the width. A line is a range of the
     * text, so a typewriter can reveal the text without the words jumping
     * from one line to the next.
     *
     * @param text
     *            Text to wrap, a newline always starts a new line.
     * @param maxWidth
     *            Width of a line in pixels.
     * @return Start (inclusive) and end (exclusive) of every line.
     */
    private List<int[]> wrapRanges(final String text, final int maxWidth) {
        List<int[]> ranges = new java.util.ArrayList<int[]>();
        int start = 0;
        int length = text.length();
        while (start <= length) {
            int end = start;
            int lastSpace = -1;
            int next = -1;
            while (end < length && text.charAt(end) != '\n') {
                if (text.charAt(end) == ' ')
                    lastSpace = end;
                if (fontRegularMetrics.stringWidth(
                        text.substring(start, end + 1)) > maxWidth
                        && lastSpace > start) {
                    end = lastSpace;
                    next = lastSpace + 1;
                    break;
                }
                end++;
            }
            if (next < 0)
                next = end + 1;
            ranges.add(new int[] { start, end });
            start = next;
        }
        return ranges;
    }

    /**
     * Draws the typed-out lines of a text.
     *
     * @param text
     *            Full text.
     * @param visibleChars
     *            Number of characters of the text that are shown.
     * @param x
     *            Left edge of the lines.
     * @param y
     *            Baseline of the first line.
     * @param maxWidth
     *            Width available for a line.
     */
    private void drawTypedText(final String text, final int visibleChars,
            final int x, final int y, final int maxWidth) {
        backBufferGraphics.setFont(fontRegular);
        int lineHeight = fontRegularMetrics.getHeight() + 4;
        int row = 0;
        for (int[] range : wrapRanges(text, maxWidth)) {
            int shownEnd = Math.min(range[1], visibleChars);
            if (shownEnd > range[0])
                backBufferGraphics.drawString(
                        text.substring(range[0], shownEnd), x,
                        y + row * lineHeight);
            row++;
        }
    }

    /**
     * Draws the story narration typed out on a plain screen.
     *
     * @param screen
     *            Screen to draw on.
     * @param text
     *            Full narration.
     * @param visibleChars
     *            Number of characters shown so far.
     * @param done
     *            Whether the whole text is shown, which shows the prompt.
     * @param millis
     *            Current time, for the blinking prompt.
     */
    public void drawStoryNarration(final Screen screen, final String text,
            final int visibleChars, final boolean done, final long millis) {
        int x = 30;
        int width = screen.getWidth() - 2 * x;
        backBufferGraphics.setColor(Color.GREEN);
        drawTypedText(text, visibleChars, x, screen.getHeight() / 5, width);
        drawPrompt(screen, done, millis, screen.getHeight() - 30);
    }

    /**
     * Draws the portrait on the lower left and the typed line to its right,
     * over the game screen.
     *
     * @param screen
     *            Screen to draw on.
     * @param speaker
     *            Name shown above the line.
     * @param text
     *            Full line.
     * @param visibleChars
     *            Number of characters shown so far.
     * @param done
     *            Whether the whole line is shown, which shows the prompt.
     * @param millis
     *            Current time, drives the portrait and the prompt.
     */
    public void drawStoryDialogue(final Screen screen, final String speaker,
            final String text, final int visibleChars, final boolean done,
            final long millis) {
        // The lines sit at a fraction of the height, so the boxes start just
        // below the lower one and stay inside the part of the window that
        // is really visible.
        int boxY = dialogueBoxY(screen);
        int boxHeight = dialogueBoxHeight(screen);
        // Portrait, with one green border right around it.
        int portraitWidth = (int) Math.round(boxHeight
                * story.PilotPortraitTraced.SRC_W
                / story.PilotPortraitTraced.SRC_H);
        story.PilotPortraitTraced.draw((Graphics2D) backBufferGraphics,
                DIALOGUE_PORTRAIT_X, boxY, portraitWidth, boxHeight,
                millis / 1000.0);
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawRect(DIALOGUE_PORTRAIT_X, boxY,
                portraitWidth - 1, boxHeight - 1);

        // Text box right next to the portrait.
        int boxX = DIALOGUE_PORTRAIT_X + portraitWidth + DIALOGUE_BOX_SPACING;
        int textWidth = screen.getWidth() - boxX - DIALOGUE_PORTRAIT_X;
        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics.fillRect(boxX, boxY, textWidth, boxHeight);
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawRect(boxX, boxY, textWidth - 1, boxHeight - 1);
        backBufferGraphics.setFont(fontRegular);
        int textX = boxX + DIALOGUE_TEXT_PADDING;
        backBufferGraphics.drawString(speaker, textX, boxY + 24);
        backBufferGraphics.setColor(Color.WHITE);
        drawTypedText(text, visibleChars, textX, boxY + 54,
                textWidth - 2 * DIALOGUE_TEXT_PADDING);

        if (done && (millis / 500) % 2 == 0) {
            backBufferGraphics.setColor(Color.GRAY);
            String hint = "SPACE";
            backBufferGraphics.drawString(hint, boxX
                    + textWidth - DIALOGUE_TEXT_PADDING
                    - fontRegularMetrics.stringWidth(hint),
                    boxY + boxHeight - 10);
        }
    }

    /** Top of the dialogue boxes, just below the lower line of the banner. */
    private int dialogueBoxY(final Screen screen) {
        return screen.getHeight() / 2 + screen.getHeight() / 12
                + DIALOGUE_BOX_GAP;
    }

    /** Height of the dialogue boxes, kept inside the visible window. */
    private int dialogueBoxHeight(final Screen screen) {
        return Math.min(DIALOGUE_BOX_HEIGHT, screen.getHeight()
                - DIALOGUE_BOTTOM_MARGIN - dialogueBoxY(screen));
    }

    /**
     * Draws a story question typed out in one wide box over the game screen.
     *
     * @param screen
     *            Screen to draw on.
     * @param text
     *            Full text of the question.
     * @param visibleChars
     *            Number of characters shown so far.
     * @param done
     *            Whether the whole text is shown, which shows the prompt.
     * @param millis
     *            Current time, for the blinking prompt.
     */
    public void drawStoryQuestionBox(final Screen screen, final String text,
            final int visibleChars, final boolean done, final long millis) {
        int boxX = DIALOGUE_PORTRAIT_X;
        int boxY = dialogueBoxY(screen);
        int boxWidth = screen.getWidth() - 2 * DIALOGUE_PORTRAIT_X;
        int boxHeight = dialogueBoxHeight(screen);
        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics.fillRect(boxX, boxY, boxWidth, boxHeight);
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawRect(boxX, boxY, boxWidth - 1, boxHeight - 1);
        backBufferGraphics.setColor(Color.WHITE);
        drawTypedText(text, visibleChars, boxX + DIALOGUE_TEXT_PADDING,
                boxY + 30, boxWidth - 2 * DIALOGUE_TEXT_PADDING);
        if (done && (millis / 500) % 2 == 0) {
            backBufferGraphics.setColor(Color.GRAY);
            String hint = "SPACE";
            backBufferGraphics.drawString(hint, boxX + boxWidth
                    - DIALOGUE_TEXT_PADDING
                    - fontRegularMetrics.stringWidth(hint),
                    boxY + boxHeight - 10);
        }
    }

    /**
     * Draws the two choices of a story question in the banner bar. The lower
     * line of the bar is split in two halves, and flying the ship into one
     * half picks that choice.
     *
     * @param screen
     *            Screen to draw on.
     * @param titles
     *            Titles of choice 0 (left half) and choice 1 (right half).
     * @param details
     *            Descriptions of the two choices.
     * @param shipSide
     *            Half the ship is under, 0 or 1.
     * @param chosen
     *            Choice that was made, or -1 while the player is choosing.
     * @param millis
     *            Current time, for the flashing of the chosen half.
     */
    public void drawStoryChoices(final Screen screen, final String[] titles,
            final String[] details, final int shipSide, final int chosen,
            final long millis) {
        int width = screen.getWidth();
        int upperY = screen.getHeight() / 2 - screen.getHeight() / 12;
        int lowerY = screen.getHeight() / 2 + screen.getHeight() / 12;
        int middle = width / 2;
        Color dim = new Color(0, 120, 0);

        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics.fillRect(0, upperY, width, lowerY - upperY);
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawLine(0, upperY, width - 1, upperY);
        backBufferGraphics.setColor(dim);
        backBufferGraphics.drawLine(middle, upperY, middle, lowerY);

        backBufferGraphics.setFont(fontRegular);
        for (int side = 0; side < 2; side++) {
            int left = side == 0 ? 0 : middle + 2;
            int right = side == 0 ? middle - 2 : width - 1;
            boolean active = chosen < 0 ? side == shipSide : side == chosen;
            boolean flash = side == chosen && (millis / 150) % 2 == 0;
            Color color = flash ? Color.WHITE : active ? Color.GREEN : dim;

            // The half of the lower line, thicker under the ship.
            int thickness = active ? 3 : 1;
            backBufferGraphics.setColor(color);
            backBufferGraphics.fillRect(left, lowerY - thickness + 1,
                    right - left + 1, thickness);

            int centerX = (left + right) / 2;
            backBufferGraphics.drawString(titles[side], centerX
                    - fontRegularMetrics.stringWidth(titles[side]) / 2,
                    upperY + 24);
            backBufferGraphics.setColor(active ? Color.WHITE : Color.GRAY);
            int row = 0;
            for (int[] range : wrapRanges(details[side],
                    right - left - 2 * DIALOGUE_TEXT_PADDING)) {
                String line = details[side].substring(range[0], range[1]);
                backBufferGraphics.drawString(line, centerX
                        - fontRegularMetrics.stringWidth(line) / 2,
                        upperY + 44 + row * 18);
                row++;
            }
        }

        if (chosen < 0) {
            backBufferGraphics.setColor(Color.GRAY);
            drawCenteredRegularString(screen, "UP ARROW: FLY TO A SIDE",
                    lowerY + 24);
        }
    }

    /** Blinking "press space" prompt shown when the text is complete. */
    private void drawPrompt(final Screen screen, final boolean show,
            final long millis, final int y) {
        if (show && (millis / 500) % 2 == 0) {
            backBufferGraphics.setColor(Color.GRAY);
            drawCenteredRegularString(screen, "Press SPACE", y);
        }
    }

    public void drawSettingsMenu(final Screen screen, final int selectedOption) {
        String[] options = SETTINGS_MENU_OPTIONS;
        Icon[] icons = { Icon.AUDIO, Icon.CONTROLS, Icon.COLOR, Icon.BACK };

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Settings", screen.getHeight() / 5);
        drawIcon(Icon.SETTINGS, screen.getWidth() / 2
                - fontBigMetrics.stringWidth("Settings") / 2
                - Icon.SETTINGS.getWidth() * 3 - 10,
                screen.getHeight() / 5 - 22, 3, Color.GREEN);

        for (int i = 0; i < options.length; i++) {
            boolean selected = i == selectedOption;
            int labelY = screen.getHeight() / 2
                    + i * fontRegularMetrics.getHeight() * 2;
            backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, options[i], labelY);
            drawMenuIcon(screen, icons[i], options[i], labelY, selected);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, "Up/Down: select  Space: confirm",
                screen.getHeight() / 8 * 7);
    }

    /** Draws the ship preview and the custom color editing controls. */
    public void drawShipColorSelection(final Screen screen,
            final Ship previewShip, final Ship secondPlayerPreviewShip,
            final Rectangle colorBar,
            final boolean editingCustomColor, final int selectedPlayerIndex) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Ship Color", screen.getHeight() / 5);
        drawEntity(previewShip, previewShip.getPositionX(), previewShip.getPositionY());
        drawEntity(secondPlayerPreviewShip,
                secondPlayerPreviewShip.getPositionX(),
                secondPlayerPreviewShip.getPositionY());

        Ship selectedShip = selectedPlayerIndex == 0
                ? previewShip : secondPlayerPreviewShip;
        int markerCenterX = selectedShip.getPositionX()
                + selectedShip.getWidth() / 2;
        int markerTopY = selectedShip.getPositionY() - 12;
        backBufferGraphics.setColor(GameColors.HIGHLIGHT);
        backBufferGraphics.fillPolygon(
                new int[] {markerCenterX - 5, markerCenterX + 5, markerCenterX},
                new int[] {markerTopY, markerTopY, markerTopY + 6}, 3);

        boolean custom = ShipCustomization.isCustomColor(selectedPlayerIndex);
        backBufferGraphics.setColor(Color.WHITE);
        String label = "Color: "
                + ShipCustomization.getColorName(selectedPlayerIndex);
        if (custom) {
            drawShipColorBar(colorBar,
                    ShipCustomization.getCustomHue(selectedPlayerIndex),
                    editingCustomColor);
            label += String.format(" #%06X",
                    selectedShip.getColor().getRGB() & 0xFFFFFF);
            drawCenteredRegularString(screen, editingCustomColor
                    ? "Left/Right or drag: adjust color"
                    : "Enter or drag: edit custom color",
                    screen.getHeight() - 110);
        }
        drawCenteredRegularString(screen, label,
                screen.getHeight() / 2 + (custom ? 75 : 45));
        String playerOrdinal = selectedPlayerIndex == 0 ? "1st" : "2nd";
        drawCenteredRegularString(screen, "Change the " + playerOrdinal
                + " ship's color (Up/Down)", screen.getHeight() - 85);
        drawCenteredRegularString(screen, editingCustomColor
                ? "Enter: done" : "Left/Right: choose color", screen.getHeight() - 60);
        drawCenteredRegularString(screen, "Esc: back to settings", screen.getHeight() - 35);
    }

    /** Draws the full hue spectrum and a marker at the custom hue. */
    private void drawShipColorBar(final Rectangle bounds, final float hue,
            final boolean editing) {
        Color[] colors = {Color.RED, Color.YELLOW, Color.GREEN,
            Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        int width = bounds.width;
        int height = bounds.height;
        int x = bounds.x;
        int y = bounds.y;
        float[] stops = new float[colors.length];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = (float) i / (stops.length - 1);
        }

        Graphics2D barGraphics = (Graphics2D) backBufferGraphics.create();
        try {
            barGraphics.setPaint(new LinearGradientPaint(x, y,
                    x + width - 1, y, stops, colors));
            barGraphics.fillRect(x, y, width, height);
            barGraphics.setColor(editing ? Color.WHITE : Color.GRAY);
            barGraphics.drawRect(x - 1, y - 1, width + 1, height + 1);

            int selectedX = x + Math.round(hue * (width - 1));
            barGraphics.setColor(Color.WHITE);
            barGraphics.fillPolygon(
                    new int[] {selectedX - 4, selectedX + 4, selectedX},
                    new int[] {y - 9, y - 9, y - 4}, 3);
            barGraphics.drawRect(selectedX - 3, y - 2, 6, height + 3);
        } finally {
            barGraphics.dispose();
        }
    }

    public void drawControlsSettings(final Screen screen,
            final int selectedOption, final boolean waitingForKey) {
        String[] labels = getControlsSettingsLabels(selectedOption,
                waitingForKey);

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Controls", screen.getHeight() / 5);

        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(
                    i == selectedOption ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i],
                    screen.getHeight() / 2
                            + i * fontRegularMetrics.getHeight() * 2);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, "Up/Down: select  Space: change",
                screen.getHeight() / 8 * 7);
    }

    /**
     * Draws the audio settings screen.
     *
     * @param screen
     *                  Screen to draw on.
     * @param option
     *                  Option selected.
     * @param bgmVolume
     *                  Current BGM volume, from 0 to 100.
     * @param sfxVolume
     *                  Current SFX volume, from 0 to 100.
     * @param muted
     *                  If the audio is muted.
     */
    public void drawAudioSettings(final Screen screen, final int option,
            final int bgmVolume, final int sfxVolume, final boolean muted) {
        String titleString = "Audio Settings";
        String selectString = "Up/Down: select";
        String adjustString = "Left/Right: adjust  M: mute";
        String returnString = "Press Esc to return";
        // In the order of the options: BGM volume, SFX volume, mute.
        String[] optionStrings = { "BGM Volume", "SFX Volume", "Mute" };
        // Values shown on the right side of each option.
        String[] valueStrings = { Integer.toString(bgmVolume),
                Integer.toString(sfxVolume), muted ? "ON" : "OFF" };
        // Volumes shown as bars. Only the volume options have a bar.
        int[] barVolumes = { bgmVolume, sfxVolume };

        // Centered column that holds the option names, values, and bars.
        int columnWidth = AUDIO_COLUMN_WIDTH;
        int columnLeft = screen.getWidth() / 2 - columnWidth / 2;
        // Vertical position of the first option and the space between options.
        int firstOptionY = screen.getHeight() / 3;
        int optionSpacing = fontRegularMetrics.getHeight() * 4;
        // Mute is the last option.
        int muteIndex = optionStrings.length - 1;
        // Space taken by the widest value, so the brackets do not move.
        int valueRight = columnLeft + columnWidth;
        int valueLeft = valueRight - fontRegularMetrics.stringWidth("< 100 >");
        int valueCenter = (valueLeft + valueRight) / 2;
        // Volume bars are drawn right below the volume names.
        int barGap = AUDIO_BAR_GAP;
        int barHeight = AUDIO_BAR_HEIGHT;

        // Title.
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, titleString, screen.getHeight() / 8);

        // Control instructions.
        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, selectString,
                screen.getHeight() / 5);
        drawCenteredRegularString(screen, adjustString,
                screen.getHeight() / 5 + fontRegularMetrics.getHeight());

        // Options, one row each.
        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < optionStrings.length; i++) {
            // The selected option is green and the others are white.
            if (i == option)
                backBufferGraphics.setColor(Color.GREEN);
            else
                backBufferGraphics.setColor(Color.WHITE);
            // Option name on the left edge of the column.
            backBufferGraphics.drawString(optionStrings[i], columnLeft,
                    firstOptionY + optionSpacing * i);
            // Value centered in the value space.
            backBufferGraphics.drawString(valueStrings[i], valueCenter
                    - fontRegularMetrics.stringWidth(valueStrings[i]) / 2,
                    firstOptionY + optionSpacing * i);
            // Brackets at fixed positions around the volume values, to show
            // that they can be changed with Left and Right.
            if (i != muteIndex) {
                backBufferGraphics.drawString("<", valueLeft,
                        firstOptionY + optionSpacing * i);
                backBufferGraphics.drawString(">", valueRight
                        - fontRegularMetrics.stringWidth(">"),
                        firstOptionY + optionSpacing * i);
            }
            // Strike through the volume numbers while muted.
            if (muted && i != muteIndex) {
                int numberWidth = fontRegularMetrics
                        .stringWidth(valueStrings[i]);
                backBufferGraphics.fillRect(valueCenter - numberWidth / 2 - 2,
                        firstOptionY + optionSpacing * i
                                - fontRegularMetrics.getAscent() / 2,
                        numberWidth + 4, 2);
            }
            // Volume bar below the volume names, gray while muted.
            if (i < barVolumes.length) {
                Color barColor;
                if (muted)
                    barColor = Color.GRAY;
                else if (i == option)
                    barColor = Color.GREEN;
                else
                    barColor = Color.WHITE;
                drawVolumeBar(columnLeft,
                        firstOptionY + optionSpacing * i + barGap,
                        columnWidth, barHeight, barVolumes[i], barColor);
            }
        }

        // Return instruction near the bottom.
        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, returnString,
                screen.getHeight() / 8 * 7);
    }

    /**
     * Draws a horizontal volume bar.
     * The whole bar is drawn in dark gray, and the part up to the volume is
     * filled with the given color.
     *
     * @param positionX
     *                  Left edge of the bar.
     * @param positionY
     *                  Top edge of the bar.
     * @param width
     *                  Width of the bar at volume 100.
     * @param height
     *                  Height of the bar.
     * @param volume
     *                  Volume to show, from 0 to 100.
     * @param color
     *                  Color of the filled part.
     */
    private void drawVolumeBar(final int positionX, final int positionY,
            final int width, final int height, final int volume,
            final Color color) {
        backBufferGraphics.setColor(Color.DARK_GRAY);
        backBufferGraphics.fillRect(positionX, positionY, width, height);

        backBufferGraphics.setColor(color);
        backBufferGraphics.fillRect(positionX, positionY,
                width * volume / 100, height);
    }

    /**
     * Draws a pixel-art sprite (2x2 pixels per cell) in the current color,
     * the same way as the other entities of the game.
     */
    private void drawSprite(final boolean[][] image, final int x,
            final int y) {
        for (int i = 0; i < image.length; i++) {
            for (int j = 0; j < image[i].length; j++) {
                if (image[i][j]) {
                    backBufferGraphics.drawRect(x + i * 2, y + j * 2, 1, 1);
                }
            }
        }
    }

    /**
     * Draws the Cluster Grenade item with its pixel-art sprite, so that
     * players recognize it when it falls.
     *
     * @param x Coordinate for the left side of the item.
     * @param y Coordinate for the upper side of the item.
     */
    public void drawGrenadeItem(final int x, final int y) {
        backBufferGraphics.setColor(Color.WHITE);
        drawSprite(ClusterGrenadeItem.getSprite(), x, y);
    }

    /**
     * Draws a grenade launched by the player ship: a pixel-art grenade
     * with a halo.
     *
     * @param grenade Grenade to draw.
     */
    public void drawGrenade(final Grenade grenade) {
        Graphics g = backBufferGraphics;
        int x = grenade.getPositionX();
        int y = grenade.getPositionY();
        int w = grenade.getWidth();
        int h = grenade.getHeight();

        // Halo around the grenade.
        g.setColor(new Color(255, 140, 0, 50));
        g.fillOval(x - 10, y - 10, w + 20, h + 20);
        g.setColor(new Color(255, 170, 0, 100));
        g.fillOval(x - 5, y - 5, w + 10, h + 10);

        // Grenade.
        g.setColor(grenade.getColor());
        drawSprite(Grenade.getSprite(), x, y);
    }

    /**
     * Draws a fragment of an exploded grenade: a pixel-art spark with a halo
     * that fades out.
     *
     * @param fragment Fragment to draw.
     */
    public void drawGrenadeFragment(final GrenadeFragment fragment) {
        Graphics g = backBufferGraphics;
        int x = fragment.getPositionX();
        int y = fragment.getPositionY();
        int cx = x + fragment.getWidth() / 2;
        int cy = y + fragment.getHeight() / 2;

        // The halo fades out while the fragment flies away.
        double fade = 1.0 - 0.6 * fragment.getProgress();
        g.setColor(new Color(255, 140, 0, (int) (60 * fade)));
        g.fillOval(cx - 11, cy - 11, 22, 22);
        g.setColor(new Color(255, 190, 0, (int) (110 * fade)));
        g.fillOval(cx - 7, cy - 7, 14, 14);

        // Spark with a bright center.
        g.setColor(fragment.getColor());
        drawSprite(GrenadeFragment.getSprite(), x, y);
        g.setColor(Color.WHITE);
        g.fillRect(cx - 1, cy - 1, 2, 2);
    }

    /**
     * Top of the status strip: the band just under the HUD separator, above
     * the path of the bonus ship, where no gameplay object goes.
     */
    public static final int STATUS_STRIP_TOP = 41;
    /** Height of the status strip, down to the bonus ship's path. */
    public static final int STATUS_STRIP_HEIGHT =
            entity.EnemyShip.BONUS_SHIP_Y - 1 - STATUS_STRIP_TOP;
    /** Left edge of the item icons on the status strip. */
    private static final int STATUS_STRIP_MARGIN = 10;

    /** Baseline of text in the given font, centered on the status strip. */
    private static int statusStripBaseline(final FontMetrics metrics) {
        return STATUS_STRIP_TOP + (STATUS_STRIP_HEIGHT + metrics.getAscent()
                - metrics.getDescent()) / 2;
    }

    /** Baseline of regular text on the status strip. */
    private int statusStripBaseline() {
        return statusStripBaseline(fontRegularMetrics);
    }

    /**
     * Draws each player's grenades and active buffs as icons from the left
     * of the status strip; with two players, a bar separates them.
     *
     * @param screen Screen to draw on.
     * @param players Items of each player, Player 1 first.
     * @return the x where the strip is free again, for the achievement
     *         notice; the left edge when nothing is drawn.
     */
    public int drawItemStrip(final Screen screen, final List<ItemStatus> players) {
        boolean any = false;
        for (ItemStatus player : players)
            any |= !player.isEmpty();
        if (!any)
            return 0;
        backBufferGraphics.setFont(fontRegular);
        int iconSize = 7 * 2;
        int iconTop = STATUS_STRIP_TOP + (STATUS_STRIP_HEIGHT - iconSize) / 2;
        int x = STATUS_STRIP_MARGIN;
        for (int p = 0; p < players.size(); p++) {
            ItemStatus player = players.get(p);
            if (p > 0) {
                x += 4;
                backBufferGraphics.setFont(fontRegular);
                backBufferGraphics.setColor(Color.GRAY);
                backBufferGraphics.drawString("|", x, statusStripBaseline());
                x += fontRegularMetrics.stringWidth("|") + 8;
            }
            if (player.getGrenades() > 0) {
                drawIcon(Icon.GRENADE, x, iconTop, 2);
                x += iconSize + 2;
                String count = Integer.toString(player.getGrenades());
                backBufferGraphics.setFont(fontRegular);
                backBufferGraphics.setColor(GameColors.EXPLOSION_ORANGE);
                backBufferGraphics.drawString(count, x, statusStripBaseline());
                x += fontRegularMetrics.stringWidth(count) + 8;
            }
            List<Icon> buffs = player.getBuffs();
            for (int i = 0; i < buffs.size(); i++) {
                if (player.isShown(i))
                    drawIcon(buffs.get(i), x, iconTop, 2);
                x += iconSize + 4;
            }
        }
        return x + 4;
    }

    /**
     * Dessine un bouclier protecteur autour du vaisseau.
     *
     * @param screen Écran de jeu cible.
     * @param ship   Vaisseau à entourer.
     */
    public void drawShield(final Screen screen, final Entity ship) {
        backBufferGraphics.setColor(new Color(0, 200, 255, 120)); // Cyan semi-transparent

        int padding = 4;
        int x = ship.getPositionX() - padding;
        int y = ship.getPositionY() - padding;
        int width = ship.getWidth() + (padding * 2);
        int height = ship.getHeight() + (padding * 2);

        // Dessine l'intérieur lumineux du bouclier
        backBufferGraphics.fillOval(x, y, width, height);

        // Dessine le contour plus net du bouclier
        backBufferGraphics.setColor(Color.CYAN);
        backBufferGraphics.drawOval(x, y, width, height);
    }

	/**
	 * Draws OBELISK's downward Orbital Annihilator laser warning and beam.
	 *
	 * @param boss
	 *            Firing boss entity.
	 * @param beamWidth
	 *            Width of the active beam in pixels.
	 * @param bottomY
	 *            Bottom Y coordinate of the play area.
	 * @param firing
	 *            True while firing, false while charging.
	 * @param chargeProgress
	 *            Charge progress in [0, 1].
	 */
	public void drawBossLaser(final Entity boss, final int beamWidth,
							  final int bottomY, final boolean firing,
							  final float chargeProgress) {
		int centerX = boss.getPositionX() + boss.getWidth() / 2;
		int startY = boss.getPositionY() + boss.getHeight();
		int beamHeight = Math.max(0, bottomY - startY);
		int beamX = centerX - beamWidth / 2;

		if (firing) {
			backBufferGraphics.setColor(new Color(255, 0, 0, 120));
			backBufferGraphics.fillRect(beamX - 4, startY, beamWidth + 8,
					beamHeight);

			backBufferGraphics.setColor(Color.RED);
			backBufferGraphics.fillRect(beamX, startY, beamWidth, beamHeight);

			backBufferGraphics.setColor(Color.WHITE);
			backBufferGraphics.fillRect(centerX - beamWidth / 4, startY,
					beamWidth / 2, beamHeight);
		} else {
			backBufferGraphics.setColor(new Color(255, 0, 0, 90));
			backBufferGraphics.fillRect(centerX - 1, startY, 2, beamHeight);

			int radius = 2 + (int) (chargeProgress * beamWidth / 2);
			boolean flicker = ((int) (chargeProgress * 20)) % 2 == 0;

			backBufferGraphics.setColor(flicker ? Color.WHITE : Color.RED);
			backBufferGraphics.fillOval(centerX - radius, startY - radius,
					radius * 2, radius * 2);
		}
	}

    private String getKeyDisplayText(final int keyCode) {
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT:
                return "Left Arrow";
            case java.awt.event.KeyEvent.VK_RIGHT:
                return "Right Arrow";
            case java.awt.event.KeyEvent.VK_UP:
                return "Up Arrow";
            case java.awt.event.KeyEvent.VK_DOWN:
                return "Down Arrow";
            case java.awt.event.KeyEvent.VK_SPACE:
                return "Space";
            default:
                if (keyCode >= java.awt.event.KeyEvent.VK_A
                        && keyCode <= java.awt.event.KeyEvent.VK_Z) {
                    return String.valueOf((char) keyCode);
                }
                return java.awt.event.KeyEvent.getKeyText(keyCode);
        }
    }

    public void initDrawing(final Screen screen) {
        backBuffer = new BufferedImage(screen.getWidth(), screen.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        graphics = frame == null ? null : frame.getGraphics();
        backBufferGraphics = backBuffer.getGraphics();

        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics
                .fillRect(0, 0, screen.getWidth(), screen.getHeight());
        screen.drawBackground(backBufferGraphics);

        fontRegularMetrics = backBufferGraphics.getFontMetrics(fontRegular);
        fontBigMetrics = backBufferGraphics.getFontMetrics(fontBig);

        // drawBorders(screen);
        // drawGrid(screen);
    }

    public void completeDrawing(final Screen screen) {
        try {
            if (graphics != null)
                graphics.drawImage(backBuffer, frame.getInsets().left,
                        frame.getInsets().top, frame);
        } finally {
            backBufferGraphics.dispose();
            if (graphics != null)
                graphics.dispose();
        }
    }

    public void drawNameInput(final Screen screen, final char[] name,
            final int nameCharSelected) {
        drawNameInputAt(screen, name, nameCharSelected, screen.getHeight()
                / 4 + fontRegularMetrics.getHeight() * 10,
                fontRegularMetrics.getHeight() * 2);
    }

    private void drawNameInputAt(final Screen screen, final char[] name,
            final int nameCharSelected, final int y, final int spacing) {
        String newRecordString = "New Record!";
        String introduceNameString = "Introduce name:";

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, newRecordString, y);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, introduceNameString, y + spacing);

        // 3 letters name.
        int positionX = screen.getWidth()
                / 2
                - (fontRegularMetrics.getWidths()[name[0]]
                        + fontRegularMetrics.getWidths()[name[1]]
                        + fontRegularMetrics.getWidths()[name[2]]
                        + fontRegularMetrics.getWidths()[' ']) / 2;

        for (int i = 0; i < 3; i++) {
            if (i == nameCharSelected)
                backBufferGraphics.setColor(Color.GREEN);
            else
                backBufferGraphics.setColor(Color.WHITE);

            positionX += fontRegularMetrics.getWidths()[name[i]] / 2;
            positionX = i == 0 ? positionX
                    : positionX
                            + (fontRegularMetrics.getWidths()[name[i - 1]]
                                    + fontRegularMetrics.getWidths()[' ']) / 2;

            backBufferGraphics.drawString(Character.toString(name[i]),
                    positionX,
                    y + spacing * 2);
        }
    }

    /** Gap between the achievement notice and the right edge of the screen. */
    private static final int TOAST_MARGIN = 6;

    /** Font of the achievement notice; derived when first needed. */
    private static Font fontToast;
    /** Border of the achievement notice between its flashes. */
    private static final Color TOAST_BORDER = new Color(20, 110, 55);

    private Font toastFont() {
        if (fontToast == null)
            fontToast = fontRegular.deriveFont(16f);
        return fontToast;
    }

    /**
     * Draws the achievement notice on the status strip, right of the item
     * icons: a green box with the medal of the unlocked tier or a trophy,
     * the achievement's name and tier, and how many more notices wait. It
     * slides in from the right edge, flashes its border, and slides back
     * out, and never leaves the strip.
     *
     * @param screen Screen to draw on.
     * @param toast Notice to draw; nothing is drawn while it shows none.
     * @param freeFrom x where the strip is free, from
     *        {@link #drawItemStrip}; 0 when the whole strip is free.
     */
    public void drawAchievementToast(final Screen screen, final AchievementToast toast,
            final int freeFrom) {
        AchievementUnlock unlock = toast.getCurrent();
        if (unlock == null || toast.getShown() <= 0)
            return;
        Font font = toastFont();
        FontMetrics metrics = backBufferGraphics.getFontMetrics(font);
        int padding = 5;
        int scale = 2;
        int iconWidth = RankBadge.WIDTH * scale;
        int waiting = toast.getWaiting();
        String more = waiting > 0 ? "+" + waiting : "";
        int moreWidth = more.isEmpty() ? 0 : metrics.stringWidth(more) + padding * 2;
        int right = screen.getWidth() - TOAST_MARGIN;
        int left = Math.max(freeFrom, TOAST_MARGIN);
        int room = right - left - padding * 3 - iconWidth - moreWidth;
        String title = fitText(unlock.getTitle(), metrics, room);
        int width = Math.min(right - left,
                padding * 3 + iconWidth + metrics.stringWidth(title) + moreWidth);
        // Slides from off the right edge to its place, ending at the margin.
        int x = right - width + (int) Math.round((1 - toast.getShown())
                * (width + TOAST_MARGIN));
        int top = STATUS_STRIP_TOP + 1;
        int height = STATUS_STRIP_HEIGHT - 2;

        backBufferGraphics.setColor(GameColors.HIGHLIGHT);
        backBufferGraphics.fillRect(x, top, width, height);
        backBufferGraphics.setColor(toast.isFlashing() ? GameColors.EXPLOSION_YELLOW : TOAST_BORDER);
        backBufferGraphics.drawRect(x, top, width - 1, height - 1);
        backBufferGraphics.drawRect(x + 1, top + 1, width - 3, height - 3);

        int iconX = x + padding;
        if (unlock.getAchievement().getTierCount() > 1)
            drawColorGrid(RankBadge.forTier(unlock.getTier()), iconX,
                    top + (height - RankBadge.HEIGHT * scale) / 2, scale);
        else
            drawIcon(Icon.HIGH_SCORES, iconX,
                    top + (height - Icon.HIGH_SCORES.getHeight() * scale) / 2, scale);
        int baseline = top + (height + metrics.getAscent() - metrics.getDescent()) / 2;
        backBufferGraphics.setFont(font);
        backBufferGraphics.setColor(GameColors.BACKGROUND);
        backBufferGraphics.drawString(title, iconX + iconWidth + padding, baseline);
        if (!more.isEmpty())
            backBufferGraphics.drawString(more,
                    x + width - padding - metrics.stringWidth(more), baseline);
    }

    /** @return the text, cut short with ".." when it is wider than the room. */
    private static String fitText(final String text, final FontMetrics metrics,
            final int room) {
        if (metrics.stringWidth(text) <= room)
            return text;
        String cut = text;
        while (!cut.isEmpty() && metrics.stringWidth(cut + "..") > room)
            cut = cut.substring(0, cut.length() - 1);
        return cut.trim() + "..";
    }
    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<AchievementUnlock> achievements, final boolean acceptsInput, final int failedStage) {
        drawAchievementResults(screen, score, lives, enemies, accuracy, isNewRecord, name, selected,
                achievements, acceptsInput, failedStage, false);
    }

    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<AchievementUnlock> achievements, final boolean acceptsInput, final int failedStage,
            final boolean gameCleared) {
        drawAchievementResults(screen, score, lives, enemies, accuracy,
                isNewRecord, name, selected, achievements, acceptsInput,
                failedStage, gameCleared, null);
    }

    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<AchievementUnlock> achievements, final boolean acceptsInput,
            final int failedStage, final boolean gameCleared, final String modeLabel) {
        drawAchievementResults(screen, score, lives, enemies, accuracy,
                isNewRecord, name, selected, achievements, acceptsInput,
                failedStage, gameCleared, modeLabel, 0);
    }

    /**
     * Draws the results screen of a run that unlocked achievements. Each
     * achievement has its medal or trophy, and a tiered one's name is in its
     * tier's color; when they do not fit on one page, the given page of them
     * is shown and the heading says which.
     *
     * @param achievementPage
     *            Page of the achievements to list, from 0; see
     *            {@link #getResultAchievementPageCount}.
     */
    public void drawAchievementResults(final Screen screen, final int score,
            final int lives, final int enemies, final float accuracy,
            final boolean isNewRecord, final char[] name, final int selected,
            final List<AchievementUnlock> achievements, final boolean acceptsInput,
            final int failedStage, final boolean gameCleared, final String modeLabel,
            final int achievementPage) {
        int spacing = fontRegularMetrics.getHeight();
        int y = getAchievementResultsTitleY(screen, isNewRecord, failedStage,
                modeLabel == null ? 4 : 5,
                Math.min(achievements.size(), MAX_RESULT_ACHIEVEMENTS));
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, gameCleared ? "Game Cleared!" : "Game Over", y);
        if (failedStage > 0) {
            y += spacing;
            backBufferGraphics.setColor(Color.RED);
            drawCenteredRegularString(screen, "Stage " + failedStage + " Failed", y);
        }
        y += spacing * 2;
        backBufferGraphics.setColor(Color.WHITE);
        String[] results = { "Score " + score, "Lives remaining " + lives,
                "Enemies destroyed " + enemies,
                String.format("Accuracy %.2f%%", accuracy * 100) };
        if (modeLabel != null)
            results = new String[] { results[0], results[1], results[2], results[3], modeLabel };
        for (String result : results) {
            drawCenteredRegularString(screen, result, y);
            y += spacing;
        }
        if (isNewRecord) {
            y += spacing;
            drawNameInputAt(screen, name, selected, y, spacing);
            y += spacing * 3;
        }
        y += spacing;
        backBufferGraphics.setColor(Color.GREEN);
        int pages = getResultAchievementPageCount(achievements.size());
        int page = Math.floorMod(achievementPage, pages);
        drawCenteredRegularString(screen, pages > 1
                ? "New achievements: " + (page + 1) + "/" + pages
                : "New achievements:", y);
        int first = page * MAX_RESULT_ACHIEVEMENTS;
        int last = Math.min(achievements.size(), first + MAX_RESULT_ACHIEVEMENTS);
        for (int i = first; i < last; i++) {
            y += spacing;
            drawResultAchievement(screen, achievements.get(i), y);
        }
        backBufferGraphics.setColor(acceptsInput ? Color.GREEN : Color.GRAY);
        drawCenteredRegularString(screen, "Space: play again   Esc: menu",
                screen.getHeight() - spacing);
    }

    /**
     * Draws one achievement row of the results screen, centered: its medal
     * and its name in the tier's color, or a trophy and its name in white.
     */
    private void drawResultAchievement(final Screen screen,
            final AchievementUnlock unlock, final int baseline) {
        boolean tiered = unlock.getAchievement().getTierCount() > 1;
        String title = unlock.getTitle();
        int iconWidth = tiered ? RankBadge.WIDTH : Icon.HIGH_SCORES.getWidth();
        int iconHeight = tiered ? RankBadge.HEIGHT : Icon.HIGH_SCORES.getHeight();
        int x = screen.getWidth() / 2 - (iconWidth + RESULT_ICON_GAP
                + fontRegularMetrics.stringWidth(title)) / 2;
        int middle = baseline - (fontRegularMetrics.getAscent()
                - fontRegularMetrics.getDescent()) / 2;
        if (tiered)
            drawColorGrid(RankBadge.forTier(unlock.getTier()), x, middle - iconHeight / 2, 1);
        else
            drawIcon(Icon.HIGH_SCORES, x, middle - iconHeight / 2, 1);
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(tiered ? RankBadge.tierColor(unlock.getTier()) : Color.WHITE);
        backBufferGraphics.drawString(title, x + iconWidth + RESULT_ICON_GAP, baseline);
    }

    /** Side margin of the achievement rows on the records page. */
    private static final int ACHIEVEMENT_MARGIN = 12;
    /** Width of the icon column left of each achievement row. */
    private static final int ACHIEVEMENT_ICON_COLUMN = 26;
    /** Height of the progress bar between an achievement's name and condition. */
    private static final int ACHIEVEMENT_BAR_HEIGHT = 3;
    /** Smaller font for the achievement conditions; derived when first needed. */
    private static Font fontSmall;

    private Font smallFont() {
        if (fontSmall == null)
            fontSmall = fontRegular.deriveFont(12f);
        return fontSmall;
    }

    /** Baseline of an achievement's condition below the baseline of its name. */
    private int achievementConditionOffset() {
        return fontRegularMetrics.getHeight() + ACHIEVEMENT_BAR_HEIGHT + 3;
    }

    /**
     * Height of one achievement on the records page: its name, progress bar,
     * condition and a gap.
     */
    private int achievementRowHeight() {
        return achievementConditionOffset() + fontRegularMetrics.getHeight() + 6;
    }

    /** First baseline of the achievement rows, below the summary line. */
    private int achievementListTop(final Screen screen) {
        return getRecordsPageTop(screen) + fontRegularMetrics.getHeight() * 2;
    }

    /**
     * @return how many achievements the records page shows at once; Up/Down
     *         scrolls through the others.
     */
    public int getAchievementsPerPage(final Screen screen) {
        // Rows stop one line above the status line at the bottom.
        int bottom = screen.getHeight() - fontRegularMetrics.getHeight() * 2;
        // The condition of the last achievement must fit above it.
        return Math.max(1, (bottom - achievementListTop(screen)
                - achievementConditionOffset()) / achievementRowHeight() + 1);
    }

    /**
     * Draws the achievements of the records page from the first one shown:
     * each with its medal or trophy, name, progress bar and condition.
     */
    public void drawAchievementList(final Screen screen, final RecordsService service,
            final int firstShown) {
        List<AchievementStatus> statuses = service.getAchievementStatuses();
        int perPage = getAchievementsPerPage(screen);
        int first = Math.max(0, Math.min(firstShown, statuses.size() - perPage));
        int unlocked = 0;
        for (AchievementStatus status : statuses)
            if (status.isUnlocked())
                unlocked++;
        backBufferGraphics.setColor(Color.GRAY);
        String summary = unlocked + "/" + statuses.size() + " unlocked";
        if (statuses.size() > perPage)
            summary += String.format("   %d-%d   Up/Down: scroll", first + 1,
                    Math.min(statuses.size(), first + perPage));
        drawCenteredRegularString(screen, summary, getRecordsPageTop(screen));
        int y = achievementListTop(screen);
        for (int i = first; i < Math.min(statuses.size(), first + perPage); i++) {
            drawAchievementRow(screen, statuses.get(i), y);
            y += achievementRowHeight();
        }
    }

    /** Draws one achievement row whose name sits on the given baseline. */
    private void drawAchievementRow(final Screen screen, final AchievementStatus status,
            final int y) {
        int left = ACHIEVEMENT_MARGIN + ACHIEVEMENT_ICON_COLUMN;
        int right = screen.getWidth() - ACHIEVEMENT_MARGIN;
        // The icon is centered on the row's text, from the name to the condition.
        int middle = y + achievementConditionOffset() / 2 - 4;
        if (status.isUnlocked() && status.getAchievement().getTierCount() > 1)
            drawColorGrid(RankBadge.forTier(status.getUnlockedTiers()),
                    ACHIEVEMENT_MARGIN, middle - RankBadge.HEIGHT, 2);
        else if (status.isUnlocked())
            drawIcon(Icon.HIGH_SCORES, ACHIEVEMENT_MARGIN,
                    middle - Icon.HIGH_SCORES.getHeight(), 2);
        else
            drawDimmedIcon(Icon.HIGH_SCORES, ACHIEVEMENT_MARGIN,
                    middle - Icon.HIGH_SCORES.getHeight(), 2);

        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(status.isUnlocked() ? Color.GREEN : Color.WHITE);
        backBufferGraphics.drawString(status.getTitle(), left, y);
        // Complete achievements show when they were finished, the others
        // their progress towards the next tier.
        Long time = status.getLastUnlockTime();
        String corner = status.isComplete() && time != null
                ? ACHIEVEMENT_TIMESTAMP_FORMAT.format(
                        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
                : status.getProgressText();
        backBufferGraphics.setColor(status.isComplete() ? Color.GREEN : Color.LIGHT_GRAY);
        backBufferGraphics.drawString(corner,
                right - fontRegularMetrics.stringWidth(corner), y);

        if (status.isRevealed()) {
            int barTop = y + 4;
            backBufferGraphics.setColor(Color.DARK_GRAY);
            backBufferGraphics.fillRect(left, barTop, right - left, ACHIEVEMENT_BAR_HEIGHT);
            backBufferGraphics.setColor(status.isComplete() ? Color.GREEN : Color.WHITE);
            backBufferGraphics.fillRect(left, barTop,
                    (int) Math.round((right - left) * status.getProgressFraction()),
                    ACHIEVEMENT_BAR_HEIGHT);
        }

        backBufferGraphics.setFont(smallFont());
        backBufferGraphics.setColor(Color.GRAY);
        backBufferGraphics.drawString(status.getCondition(), left,
                y + achievementConditionOffset());
    }

    public void drawMenu(final Screen screen, final int option) {
        String[] labels = {
            "Play", "High scores",
            "Records & Achievements", "Settings", "Exit"
        };
        int[] codes = { 2, 3, 9, 4, 0 };
        Icon[] icons = {
                Icon.PLAY, Icon.HIGH_SCORES,
                Icon.RECORDS, Icon.SETTINGS, Icon.EXIT
        };
        int spacing = fontRegularMetrics.getHeight() * 3 / 2;
        int firstY = screen.getHeight() / 2 + fontRegularMetrics.getHeight() * 2;
        for (int i = 0; i < labels.length; i++) {
            boolean selected = option == codes[i];
            int labelY = firstY + i * spacing;
            backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i], labelY);
            if (icons[i] != null)
                drawMenuIcon(screen, icons[i], labels[i], labelY, selected);
        }
    }

    public int getMenuOptionAt(final Screen screen,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        String[] labels = {
                "Play", "High scores",
                "Records & Achievements", "Settings", "Exit"
        };
        int[] codes = { 2, 3, 9, 4, 0 };
        int fontHeight = fontRegularMetrics.getHeight();
        int spacing = fontHeight * 3 / 2;
        int firstY = screen.getHeight() / 2 + fontHeight * 2;

        for (int i = 0; i < labels.length; i++) {
            int textWidth = fontRegularMetrics.stringWidth(labels[i]);
            int left = screen.getWidth() / 2 - textWidth / 2;
            int baseline = firstY + i * spacing;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = baseline + fontRegularMetrics.getDescent();

            if (mouseX >= left && mouseX <= left + textWidth
                    && mouseY >= top && mouseY <= bottom)
                return codes[i];
        }

        return -1;
    }

    /**
     * Returns the settings menu option under the mouse.
     *
     * @return Index of the hovered option, or -1 if none.
     */
    public int getSettingsMenuOptionAt(final Screen screen,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        return getCenteredOptionAt(screen, SETTINGS_MENU_OPTIONS,
                screen.getHeight() / 2, fontRegularMetrics.getHeight() * 2,
                mouseX, mouseY);
    }

    /**
     * Returns the controls settings option under the mouse.
     *
     * @return Index of the hovered option, or -1 if none.
     */
    public int getControlsSettingsOptionAt(final Screen screen,
            final int selectedOption, final boolean waitingForKey,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        return getCenteredOptionAt(screen,
                getControlsSettingsLabels(selectedOption, waitingForKey),
                screen.getHeight() / 2, fontRegularMetrics.getHeight() * 2,
                mouseX, mouseY);
    }

    /**
     * Returns the audio settings option under the mouse. A volume option
     * covers its whole row, from its name down to its volume bar.
     *
     * @param optionCount
     *                    Number of options on the audio settings screen.
     * @return Index of the hovered option, or -1 if none.
     */
    public int getAudioSettingsOptionAt(final Screen screen,
            final int optionCount, final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        int columnLeft = screen.getWidth() / 2 - AUDIO_COLUMN_WIDTH / 2;
        int firstOptionY = screen.getHeight() / 3;
        int optionSpacing = fontRegularMetrics.getHeight() * 4;
        int muteIndex = optionCount - 1;

        if (mouseX < columnLeft || mouseX > columnLeft + AUDIO_COLUMN_WIDTH)
            return -1;

        for (int i = 0; i < optionCount; i++) {
            int baseline = firstOptionY + optionSpacing * i;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = i == muteIndex
                    ? baseline + fontRegularMetrics.getDescent()
                    : baseline + AUDIO_BAR_GAP + AUDIO_BAR_HEIGHT;

            if (mouseY >= top && mouseY <= bottom)
                return i;
        }

        return -1;
    }

    /**
     * Returns the index of the horizontally centered option under the mouse.
     *
     * @param labels
     *                Option texts, drawn one under the other.
     * @param firstY
     *                Baseline of the first option.
     * @param spacing
     *                Vertical space between two option baselines.
     * @return Index of the hovered option, or -1 if none.
     */
    private int getCenteredOptionAt(final Screen screen, final String[] labels,
            final int firstY, final int spacing,
            final int mouseX, final int mouseY) {
        for (int i = 0; i < labels.length; i++) {
            int textWidth = fontRegularMetrics.stringWidth(labels[i]);
            int left = screen.getWidth() / 2 - textWidth / 2;
            int baseline = firstY + i * spacing;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = baseline + fontRegularMetrics.getDescent();

            if (mouseX >= left && mouseX <= left + textWidth
                    && mouseY >= top && mouseY <= bottom)
                return i;
        }

        return -1;
    }

    /**
     * Builds the texts of the controls settings options, with their keys.
     *
     * @return One text per option: Move Left, Move Right, Fire, Back.
     */
    private String[] getControlsSettingsLabels(final int selectedOption,
            final boolean waitingForKey) {
        String[] labels = { "Move Left", "Move Right", "Fire", "Back" };
        String[] keys = {
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.LEFT)),
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.RIGHT)),
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.FIRE))
        };

        for (int i = 0; i < keys.length; i++) {
            labels[i] += ": ";
            labels[i] += waitingForKey && i == selectedOption
                    ? "Press a key (Esc to cancel)"
                    : keys[i];
        }

        return labels;
    }

    /** Draws a tab of the records page with every list at its start. */
    public void drawRecordsPage(final Screen screen,
            final RecordsService service, final int tab) {
        drawRecordsPage(screen, service, tab, RunOrder.SCORE, RunFilter.ALL,
                0, 0, 0);
    }

    /** First baseline below the tab bar and key help of the records page. */
    private int getRecordsPageTop(final Screen screen) {
        return screen.getHeight() / 6 + fontRegularMetrics.getHeight() * 2;
    }

    /** Tab names of the records page, in PlayerRecordsScreen TAB_ order. */
    private static final String[] RECORDS_TABS = { "Records", "Achievements", "Stats" };

    /**
     * Draws one tab of the records page below a bar naming every tab.
     *
     * @param tab
     *            One of the PlayerRecordsScreen TAB_ constants.
     * @param order
     *            Order of the run list on the records tab.
     * @param filter
     *            Modes the run list shows.
     * @param selectedRun
     *            Index, from 0, of the selected run in that list.
     * @param firstRun
     *            Index, from 0, of the first run shown in the list.
     * @param firstAchievement
     *            Index, from 0, of the first achievement shown on the
     *            achievements tab.
     */
    public void drawRecordsPage(final Screen screen,
            final RecordsService service, final int tab, final RunOrder order,
            final RunFilter filter, final int selectedRun, final int firstRun,
            final int firstAchievement) {
        drawRecordsTabBar(screen, tab);
        drawKeyHelp(screen, screen.getHeight() / 6,
                "Left/Right", "Tab", "Esc/Space", "Return");
        if (tab == PlayerRecordsScreen.TAB_RECORDS)
            drawRunList(screen, service.getRuns(order, filter), order, filter,
                    selectedRun, firstRun);
        else if (tab == PlayerRecordsScreen.TAB_ACHIEVEMENTS)
            drawAchievementList(screen, service, firstAchievement);
        else
            drawRecordsStats(screen, service);
        backBufferGraphics.setColor(Color.GRAY);
        String status = service.getSaveError();
        String message = status == null
                ? (service.getLoadWarning() == null ? "Profile ready" : "Profile restored from backup")
                : (status.startsWith("Saving") ? "Saving profile..."
                        : "Profile error - check game log");
        drawCenteredRegularString(screen, message,
                screen.getHeight() - fontRegularMetrics.getHeight());
    }

    /**
     * Draws every tab name in a row, the current one green and underlined,
     * in the big font when the row fits the screen.
     */
    private void drawRecordsTabBar(final Screen screen, final int tab) {
        boolean big = tabBarWidth(fontBigMetrics) <= screen.getWidth() - 16;
        FontMetrics metrics = big ? fontBigMetrics : fontRegularMetrics;
        backBufferGraphics.setFont(big ? fontBig : fontRegular);
        int gap = metrics.stringWidth("  ");
        int x = (screen.getWidth() - tabBarWidth(metrics)) / 2;
        int y = screen.getHeight() / 9;
        for (int i = 0; i < RECORDS_TABS.length; i++) {
            int nameWidth = metrics.stringWidth(RECORDS_TABS[i]);
            backBufferGraphics.setColor(i == tab ? Color.GREEN : Color.GRAY);
            backBufferGraphics.drawString(RECORDS_TABS[i], x, y);
            if (i == tab)
                backBufferGraphics.fillRect(x, y + 4, nameWidth, 2);
            x += nameWidth + gap;
        }
    }

    /** Font of the key help on the records page; derived when first needed. */
    private static Font fontKeyHelp;

    /** @return a step smaller than the regular font, so key help reads below the tabs. */
    private Font keyHelpFont() {
        if (fontKeyHelp == null)
            fontKeyHelp = fontRegular.deriveFont(12f);
        return fontKeyHelp;
    }

    /**
     * Draws key help centered on one line in a smaller font, each key in
     * brackets and light gray and its action in gray, such as
     * "[J] Sort   [K] Mode", so it stays quieter than the tab names.
     *
     * @param keysAndActions
     *            Each key followed by its action.
     */
    private void drawKeyHelp(final Screen screen, final int y,
            final String... keysAndActions) {
        Object[] segments = new Object[keysAndActions.length * 2];
        for (int i = 0; i < keysAndActions.length; i += 2) {
            segments[i * 2] = Color.LIGHT_GRAY;
            segments[i * 2 + 1] = "[" + keysAndActions[i] + "]";
            segments[i * 2 + 2] = Color.GRAY;
            segments[i * 2 + 3] = " " + keysAndActions[i + 1]
                    + (i + 2 < keysAndActions.length ? "   " : "");
        }
        drawCenteredSegments(screen, y, keyHelpFont(), segments);
    }

    /**
     * Draws texts of different colors one after another, centered together.
     *
     * @param colorsAndTexts
     *            Each Color followed by the String drawn in it.
     */
    private void drawCenteredSegments(final Screen screen, final int y,
            final Object... colorsAndTexts) {
        drawCenteredSegments(screen, y, fontRegular, colorsAndTexts);
    }

    /** Draws colored texts one after another in the given font, centered together. */
    private void drawCenteredSegments(final Screen screen, final int y,
            final Font font, final Object... colorsAndTexts) {
        backBufferGraphics.setFont(font);
        FontMetrics metrics = backBufferGraphics.getFontMetrics(font);
        int width = 0;
        for (int i = 1; i < colorsAndTexts.length; i += 2)
            width += metrics.stringWidth((String) colorsAndTexts[i]);
        int x = (screen.getWidth() - width) / 2;
        for (int i = 0; i < colorsAndTexts.length; i += 2) {
            String text = (String) colorsAndTexts[i + 1];
            backBufferGraphics.setColor((Color) colorsAndTexts[i]);
            backBufferGraphics.drawString(text, x, y);
            x += metrics.stringWidth(text);
        }
    }

    /**
     * Draws a label from the left edge and its value up to the right edge,
     * so the labels and values of a list line up.
     */
    private void drawLabelValue(final String label, final String value,
            final int left, final int right, final int y) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.drawString(label, left, y);
        backBufferGraphics.drawString(value,
                right - fontRegularMetrics.stringWidth(value), y);
    }

    /** @return width of the tab names in a row, two spaces apart. */
    private static int tabBarWidth(final FontMetrics metrics) {
        int width = metrics.stringWidth("  ") * (RECORDS_TABS.length - 1);
        for (String name : RECORDS_TABS)
            width += metrics.stringWidth(name);
        return width;
    }

    /** Lines of run details below the run list: a summary and five rows. */
    private static final int RUN_DETAIL_LINES = 6;

    /** Baseline of the column names above the run list. */
    private int runListTop(final Screen screen) {
        return getRecordsPageTop(screen) + fontRegularMetrics.getHeight() * 5 / 2;
    }

    /** Baseline of the first line of the selected run's details. */
    private int runDetailsTop(final Screen screen) {
        return screen.getHeight() - fontRegularMetrics.getHeight() * (2 + RUN_DETAIL_LINES);
    }

    /**
     * @return how many runs the records tab lists at once; Up/Down scrolls
     *         through the others.
     */
    public int getRunsPerPage(final Screen screen) {
        int line = fontRegularMetrics.getHeight();
        // Rows start one line below the column names and stop a line and a
        // half above the details.
        return Math.max(1, (runDetailsTop(screen) - line * 3 / 2
                - (runListTop(screen) + line)) / line + 1);
    }

    /**
     * Draws the sorted, filtered runs from the first one shown and the
     * details of the selected one.
     */
    private void drawRunList(final Screen screen, final List<RunRecord> runs,
            final RunOrder order, final RunFilter filter, final int selectedRun,
            final int firstRun) {
        int line = fontRegularMetrics.getHeight();
        int y = getRecordsPageTop(screen);
        int selected = Math.max(0, Math.min(selectedRun, runs.size() - 1));
        String sortAndMode = "Sort: " + order.getDisplayName()
                + "   Mode: " + filter.getDisplayName();
        if (runs.isEmpty())
            drawCenteredSegments(screen, y, Color.WHITE, sortAndMode);
        else
            drawCenteredSegments(screen, y, Color.WHITE, sortAndMode,
                    Color.GRAY, "   " + (selected + 1) + "/" + runs.size());
        drawKeyHelp(screen, y + line, "J", "Sort", "K", "Mode", "Up/Down", "Select");
        if (runs.isEmpty()) {
            backBufferGraphics.setColor(Color.GRAY);
            drawCenteredRegularString(screen, filter == RunFilter.ALL
                    ? "No completed runs yet."
                    : "No " + filter.getDisplayName() + " runs yet.", y + line * 3);
            return;
        }
        int perPage = getRunsPerPage(screen);
        int first = Math.max(0, Math.min(firstRun, runs.size() - perPage));
        if (selected < first)
            first = selected;
        else if (selected >= first + perPage)
            first = selected - perPage + 1;
        y = runListTop(screen);
        backBufferGraphics.setColor(Color.GRAY);
        drawRunRow(screen, "#", "Score", "Mode", "Date", y);
        DateFormat date = new SimpleDateFormat("yy-MM-dd HH:mm");
        for (int i = first; i < Math.min(runs.size(), first + perPage); i++) {
            RunRecord run = runs.get(i);
            y += line;
            backBufferGraphics.setColor(i == selected ? Color.GREEN : Color.WHITE);
            drawRunRow(screen, Integer.toString(i + 1), Integer.toString(run.getScore()),
                    run.getMode().getDisplayName(),
                    date.format(new Date(run.getCompletionTimeMillis())), y);
        }
        drawSelectedRun(screen, runs.get(selected));
    }

    /** Draws one row of the run list in its four columns. */
    private void drawRunRow(final Screen screen, final String number,
            final String score, final String mode, final String date, final int y) {
        int width = screen.getWidth();
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.drawString(number,
                width * 12 / 100 - fontRegularMetrics.stringWidth(number), y);
        backBufferGraphics.drawString(score,
                width * 33 / 100 - fontRegularMetrics.stringWidth(score), y);
        backBufferGraphics.drawString(mode, width * 38 / 100, y);
        backBufferGraphics.drawString(date,
                width * 94 / 100 - fontRegularMetrics.stringWidth(date), y);
    }

    /** Draws the details of the selected run in two columns. */
    private void drawSelectedRun(final Screen screen, final RunRecord run) {
        int line = fontRegularMetrics.getHeight();
        int y = runDetailsTop(screen);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, String.format("%d pts  %d enemies  %s",
                run.getScore(), run.getEnemiesDefeated(),
                formatPlayTime(run.getActiveDurationMillis())), y);
        backBufferGraphics.setColor(Color.WHITE);
        List<String> details = RunRecordFormatter.detailLines(run);
        if (run.getMode() == RunMode.UNKNOWN) {
            for (String detail : details) {
                y += line;
                drawCenteredRegularString(screen, detail, y);
            }
            return;
        }
        int rows = RUN_DETAIL_LINES - 1;
        int width = screen.getWidth();
        for (int i = 0; i < details.size(); i++) {
            // Each detail line is "Label: value".
            String[] parts = details.get(i).split(": ", 2);
            boolean left = i < rows;
            drawLabelValue(parts[0], parts.length > 1 ? parts[1] : "",
                    width * (left ? 4 : 52) / 100, width * (left ? 45 : 96) / 100,
                    y + line * (i % rows + 1));
        }
    }

    /** Draws the personal bests and the cumulative stats. */
    private void drawRecordsStats(final Screen screen, final RecordsService service) {
        int line = fontRegularMetrics.getHeight();
        int y = getRecordsPageTop(screen);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, "Personal bests", y);
        // Labels start and values end at the same place on every line.
        int left = screen.getWidth() * 16 / 100;
        int right = screen.getWidth() * 84 / 100;
        backBufferGraphics.setColor(Color.WHITE);
        if (service.getRecordCount() == 0) {
            drawCenteredRegularString(screen, "No completed runs yet.", y + line);
            y += line * 2;
        } else {
            drawLabelValue("Highest score", Integer.toString(service.getHighestScore()),
                    left, right, y + line);
            drawLabelValue("Most enemies", Integer.toString(service.getMostEnemiesDefeated()),
                    left, right, y + line * 2);
            drawLabelValue("Longest survival",
                    formatPlayTime(service.getLongestSurvivalMillis()), left, right, y + line * 3);
            y += line * 4;
        }
        CumulativeStats stats = service.getCumulativeStats();
        // Values version 1 runs did not record are marked when such runs exist.
        String mark = stats.getRunsWithoutStats() > 0 ? "*" : "";
        String[][] totals = {
                { "Completed runs", Integer.toString(stats.getRunsCompleted()) },
                { "Enemies defeated", Long.toString(stats.getEnemiesDefeated()) },
                { "Play time", formatPlayTime(stats.getActiveDurationMillis()) },
                { "Stages cleared", Long.toString(stats.getStagesCleared()) },
                { "Endings seen", stats.getEndingsSeen() + "/" + stats.getEndingCount() },
                { "Shots fired" + mark, Long.toString(stats.getShotsFired()) },
                { "Hits" + mark, stats.getHits() + String.format(java.util.Locale.ROOT,
                        " (%.1f%%)", stats.getAccuracy() * 100) },
                { "Times hit" + mark, Long.toString(stats.getTimesHit()) },
                { "Bosses defeated" + mark, Long.toString(stats.getBossesDefeated()) },
                { "Items used" + mark, Long.toString(stats.getItemsUsed()) } };
        y += line;
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, "Totals", y);
        backBufferGraphics.setColor(Color.WHITE);
        for (String[] total : totals) {
            y += line;
            drawLabelValue(total[0], total[1], left, right, y);
        }
        if (!mark.isEmpty()) {
            backBufferGraphics.setColor(Color.GRAY);
            int leftOut = stats.getRunsWithoutStats();
            drawCenteredRegularString(screen, "* Leaves out " + leftOut
                    + (leftOut == 1 ? " older run" : " older runs"), y + line * 3 / 2);
        }
    }

    /**
     * @return play time such as 42.5s, 3m 07s or 2h 05m; shorter values
     *         keep the tenths of a second.
     */
    private static String formatPlayTime(final long millis) {
        long seconds = millis / 1000;
        if (seconds < 60)
            return String.format(java.util.Locale.ROOT, "%.1fs", millis / 1000.0);
        if (seconds < 3600)
            return String.format(java.util.Locale.ROOT, "%dm %02ds",
                    seconds / 60, seconds % 60);
        return String.format(java.util.Locale.ROOT, "%dh %02dm",
                seconds / 3600, seconds / 60 % 60);
    }

    /**
     * Draws every live explosion particle.
     */
    public void drawParticles(final vfx.ParticleSystem particleSystem) {
        for (vfx.Particle p : particleSystem.getParticles()) {
            backBufferGraphics.setColor(p.getColor());
            backBufferGraphics.fillRect(p.getX(), p.getY(), p.getSize(),
                    p.getSize());
        }
    }

    /** Draws earned points without changing the existing score HUD. */
    public void drawFloatingScores(final FloatingScoreHud hud, final Screen screen) {
        hud.draw((java.awt.Graphics2D) backBufferGraphics,
                screen.getWidth(), screen.getHeight());
    }

    public void drawCustomLevelMenu(final Screen screen, final int option,
            final int levelCount) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Custom Level", 90);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, "Imported Levels: " + levelCount, 140);
        for (int i = 0; i < CustomLevelMenu.MENU_ACTIONS.size(); i++) {
            CustomLevelMenu.Action action = CustomLevelMenu.MENU_ACTIONS.get(i);
            boolean enabled = action.isEnabled(levelCount);
            backBufferGraphics.setColor(!enabled ? Color.GRAY : i == option ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, action.getLabel(), 220 + i * 45);
        }
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, "Arrows / Space / Esc", screen.getHeight() - 45);
    }

    private void drawFileLabel(final Screen screen, final String text, final int y) {
        // Use a system font for Unicode filenames, independently of the game's pixel font.
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        backBufferGraphics.setFont(font);
        FontMetrics metrics = backBufferGraphics.getFontMetrics(font);
        String label = FileNameDisplay.fit(text, screen.getWidth() - 48, metrics::stringWidth);
        backBufferGraphics.drawString(label, (screen.getWidth() - metrics.stringWidth(label)) / 2, y);
    }

    public void drawCustomLevelList(final Screen screen,
            final java.util.List<CustomLevelMenu.Entry> entries, final int selected,
            final CustomLevelMenu.Action action) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, action == CustomLevelMenu.Action.PLAY ? "Play Level" : "Export JSON", 65);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, (selected + 1) + " / " + entries.size(), 105);
        int pageSize = CustomLevelListScreen.PAGE_SIZE;
        int start = selected / pageSize * pageSize;
        for (int i = start; i < Math.min(start + pageSize, entries.size()); i++) {
            backBufferGraphics.setColor(i == selected ? Color.GREEN : Color.WHITE);
            drawFileLabel(screen, (i + 1) + ". " + FileNameDisplay.label(entries.get(i).getFilename()), 155 + (i - start) * 32);
        }
        backBufferGraphics.setColor(Color.LIGHT_GRAY);
        drawFileLabel(screen, entries.get(selected).getSource().getParent().toString(), screen.getHeight() - 95);
        drawCenteredRegularString(screen, "Arrows / Space / Esc", screen.getHeight() - 45);
    }

    /** Draw the countdown behind gameplay sprites using the game's pixel font. */
    public void drawTimeAttackCountdown(final Screen screen, final int seconds, final float opacity) {
        boolean finalCountdown = seconds <= 10;
        String label = finalCountdown ? seconds + "S" : seconds + "S LEFT!";
        Graphics2D overlay = (Graphics2D) backBufferGraphics.create();
        try {
            overlay.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            overlay.setFont(fontBig.deriveFont(finalCountdown ? 96f : 40f));
            overlay.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.32f * opacity));
            overlay.setColor(finalCountdown ? new Color(255, 105, 105) : Color.WHITE);
            FontMetrics metrics = overlay.getFontMetrics();
            int x = (screen.getWidth() - metrics.stringWidth(label)) / 2;
            int y = (screen.getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
            overlay.drawString(label, x, y);
        } finally {
            overlay.dispose();
        }
    }

    public void drawTimeAttackHud(final int seconds, final boolean red) {
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(red ? Color.RED : Color.WHITE);
        String label = "TIME " + seconds + "s";
        backBufferGraphics.drawString(label, 10, 25);
    }

    public void drawTimeAttackResult(final Screen screen, final int clearedStages,
            final int totalStages, final int score) {
        boolean cleared = clearedStages == totalStages;
        backBufferGraphics.setColor(cleared ? Color.GREEN : Color.RED);
        drawCenteredBigString(screen, cleared ? "TIME ATTACK CLEAR" : "GAME OVER", 125);
        backBufferGraphics.setColor(Color.WHITE);
        if (!cleared) drawCenteredRegularString(screen, "TIME UP", 175);
        drawCenteredRegularString(screen, "Stages cleared: " + clearedStages + "/" + totalStages, 230);
        drawCenteredRegularString(screen, "Score: " + score, 270);
        drawCenteredRegularString(screen, "SPACE: Retry   ESC: Back", screen.getHeight() - 70);
    }

    public void drawGameModeMenu(final Screen screen, final int option) {
        String[] labels = { "Endless Mode", "Campaign Mode", "Story Mode", "Custom Level", "Time Attack", "Back" };

        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Select Mode", screen.getHeight() / 3);

        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(i == option ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i],
                    screen.getHeight() / 3 + 60 + i * 40);
        }
    }

    // 2P 선택 화면을 위한 메인 메서드 추가
    public void drawPlayerSelectMenu(final Screen screen, final int option) {
        String[] labels = { "1 Player", "2 Players (Co-op)", "Back" };

        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, "Select Players", screen.getHeight() / 3);

        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(i == option ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i],
                    screen.getHeight() / 3 + 60 + i * 40);
        }
    }
}
