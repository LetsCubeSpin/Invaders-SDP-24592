package engine;

import boss.BossHudInfo;
import boss.BossAttackSystem;
import entity.item.OrbitalAnnihilator;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import screen.AudioControlScreen;
import screen.CustomLevelListScreen;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import screen.PlayerRecordsScreen;
import screen.Screen;
import screen.ShipCustomization;
import screen.ShipColorScreen;
import hud.FloatingScoreHud;
import hud.AchievementToast;
import hud.ItemStatus;
import records.AchievementStatus;
import records.AchievementUnlock;
import records.Achievement;
import records.RecordsService;
import records.RunRecord;
import records.RunRecordFormatter;
import records.CumulativeStats;
import records.RunFilter;
import records.RunOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import entity.Entity;
import entity.Boss;
import entity.Grenade;
import entity.GrenadeFragment;
import entity.item.ClusterGrenadeItem;
import entity.Ship;
import entity.Bullet;
import vfx.Icon;
import vfx.RankBadge;
import vfx.ShipSprites;
import vfx.BossSprites;
import screen.GameModeScreen;
import screen.MenuScreen;

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
    /** Language currently represented by the loaded UI fonts. */
    private static LanguageManager.Language fontLanguage;

    /** Options of the settings menu, in order (used for hit detection). */
    private static final int SETTINGS_MENU_OPTION_COUNT = 5;
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
            refreshFontsForCurrentLanguage();
            logger.info("Finished loading the fonts.");

        } catch (IOException e) {
            logger.warning("Loading failed.");
        }
    }

    /** Reloads the UI fonts when the selected language changes. */
    private void refreshFontsForCurrentLanguage() {
        LanguageManager.Language selectedLanguage =
                LanguageManager.getCurrentLanguage();
        if (fontLanguage == selectedLanguage && fontRegular != null
                && fontBig != null) {
            return;
        }

        try {
            if(LanguageManager.getCurrentLanguage() == LanguageManager.Language.KOREAN) {
                Font regular = fileManager.loadFont(20f);
                Font big = fileManager.loadFont(30f);
                fontRegular = regular;
                fontBig = big;
                fontLanguage = selectedLanguage;
                fontToast = null;
                fontSmall = null;
                fontKeyHelp = null;
            }else{
                Font regular = fileManager.loadFont(14f);
                Font big = fileManager.loadFont(24f);
                fontRegular = regular;
                fontBig = big;
                fontLanguage = selectedLanguage;
                fontToast = null;
                fontSmall = null;
                fontKeyHelp = null;
            }
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

        if (entity instanceof Boss && !((Boss) entity).isDefeated()) {
            drawPixels(BossSprites.forType(((Boss) entity).getType()), positionX, positionY);
            return;
        }

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

    /** Draws the boss and the shield or laser currently visible in its attack pattern. */
    public void drawBoss(final Screen screen, final Boss boss, final BossAttackSystem attacks) {
        drawEntity(boss, boss.getPositionX(), boss.getPositionY());
        if (boss.isDefeated())
            return;
        if (boss.isShieldActive())
            drawShield(screen, boss);
        if (attacks != null && (attacks.isLaserCharging() || attacks.isLaserFiring())) {
            boolean firing = attacks.isLaserFiring();
            int width = firing ? attacks.getActiveBeamWidth() : OrbitalAnnihilator.BEAM_WIDTH;
            drawBossLaser(boss, width, screen.getHeight(), firing, attacks.getLaserChargeProgress());
        }
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
            hud.setColor(boss.getBossType().getColor());
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
        drawCenteredBigString(screen, LanguageManager.get("screen.paused"),
                screen.getHeight() / 2 + 20);
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

        String levelString = LanguageManager.get("hud.level") + " " + level
                + "/" + totalLevels;
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
        String instructionsString = LanguageManager.get("title.instructions");

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
        String scoreString = String.format(LanguageManager.get("result.score"), score);
        String livesRemainingString = String.format(
                LanguageManager.get("result.lives"), livesRemaining);
        String shipsDestroyedString = String.format(
                LanguageManager.get("result.enemies"), shipsDestroyed);
        String accuracyString = String.format(
                LanguageManager.get("result.accuracy"), accuracy * 100);

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
        String gameOverString = LanguageManager.get(gameCleared
                ? "screen.gameClear" : "screen.gameOver");
        String continueOrExitString = LanguageManager.get("screen.continueExit");

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
        drawResultActions(screen, screen.getHeight() / 2 + fontRegularMetrics.getHeight() * 10, acceptsInput);
        drawMenuHint(screen, continueOrExitString);
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
                String.format(LanguageManager.get("screen.stageFailed"), stage),
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
        String highScoreString = LanguageManager.get("screen.highScores");
        String instructionsString = LanguageManager.get("screen.highScoresHelp");

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, highScoreString, screen.getHeight() / 8);

        backBufferGraphics.setColor(Color.GRAY);
        drawMenuAction(screen, MenuScreen.BACK, instructionsString, screen.getWidth() / 2,
                screen.getHeight() / 5, false, true);
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
        String title = LanguageManager.get("hud.level") + " " + level;
        if (bonusLife)
            title += " - " + LanguageManager.get("screen.bonusLife");

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
            drawCenteredBigString(screen, LanguageManager.get("screen.go"),
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
        if (screen instanceof MenuScreen menu) menu.menuTarget(MenuScreen.NEXT, 0, 0, screen.getWidth(), screen.getHeight());
        backBufferGraphics.setColor(done ? Color.WHITE : Color.GRAY);
        drawCenteredRegularString(screen, done ? "Space or click to continue" : "Space or click to skip text",
                screen.getHeight() - 30);
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

        drawDialogueTextBox(screen, speaker, text, visibleChars, done,
                portraitWidth, boxY, boxHeight, millis);
    }

    /**
     * Draws the text box right next to a dialogue portrait: the speaker's
     * name, the typed line and, once it is complete, a blinking prompt.
     */
    private void drawDialogueTextBox(final Screen screen, final String speaker,
            final String text, final int visibleChars, final boolean prompt,
            final int portraitWidth, final int boxY, final int boxHeight,
            final long millis) {
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

        if (prompt && (millis / 500) % 2 == 0) {
            backBufferGraphics.setColor(Color.GRAY);
            String hint = LanguageManager.get("key.space").toUpperCase(Locale.ROOT);
            backBufferGraphics.drawString(hint, boxX
                    + textWidth - DIALOGUE_TEXT_PADDING
                    - fontRegularMetrics.stringWidth(hint),
                    boxY + boxHeight - 10);
        }
    }

    /**
     * Draws a boss's line when it appears, at the top of the game board: its
     * portrait on the left and the typed line to its right, between the line
     * under the HUD and the banner, like the woman at the control tower. The
     * portrait is square, so the text box is narrower than the pilot's.
     *
     * @param screen
     *            Screen to draw on.
     * @param boss
     *            Boss of stage 3, the first answer 0 or 1 that leads to it.
     * @param speaker
     *            Name shown above the line.
     * @param text
     *            Full line.
     * @param visibleChars
     *            Number of characters shown so far.
     * @param done
     *            Whether the whole line is shown, which shows the prompt.
     * @param top
     *            Height of the line under the HUD.
     * @param seconds
     *            Time since the dialogue started, so the boss moves from the
     *            start of its loop.
     * @param millis
     *            Current time, drives the prompt.
     */
    public void drawStoryBossDialogue(final Screen screen, final int boss,
            final String speaker, final String text, final int visibleChars,
            final boolean done, final int top, final double seconds,
            final long millis) {
        int boxY = topDialogueY(top);
        int boxHeight = topDialogueHeight(screen, top);
        int portraitWidth = boxHeight;
        story.BossPortrait.draw((Graphics2D) backBufferGraphics, boss,
                DIALOGUE_PORTRAIT_X, boxY, portraitWidth, boxHeight, seconds);
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawRect(DIALOGUE_PORTRAIT_X, boxY,
                portraitWidth - 1, boxHeight - 1);
        drawDialogueTextBox(screen, speaker, text, visibleChars, done,
                portraitWidth, boxY, boxHeight, millis);
    }

    /**
     * @param top
     *            Height of the line under the HUD.
     * @return the width of a boss's portrait in the dialogue at the top of
     *         the board: it is square.
     */
    public int bossPortraitWidth(final Screen screen, final int top) {
        return topDialogueHeight(screen, top);
    }

    /** Top of a dialogue at the top of the board, just under the HUD line. */
    private int topDialogueY(final int top) {
        return top + QUESTION_TOP_GAP;
    }

    /** Height of a dialogue at the top of the board, above the banner. */
    private int topDialogueHeight(final Screen screen, final int top) {
        int bannerTop = screen.getHeight() / 2 - screen.getHeight() / 12;
        return Math.min(DIALOGUE_BOX_HEIGHT,
                bannerTop - QUESTION_BANNER_GAP - topDialogueY(top));
    }

    /**
     * How many lines a line of a dialogue takes in the text box next to a
     * portrait, so a line can be checked to fit in two. It needs the fonts,
     * which are set up when a screen starts drawing.
     *
     * @param screen
     *            Screen the dialogue is drawn on.
     * @param text
     *            Full line of the dialogue.
     * @param portraitWidth
     *            Width of the portrait next to the text box.
     * @return the number of lines the text is drawn in.
     */
    public int countDialogueLines(final Screen screen, final String text,
            final int portraitWidth) {
        int boxX = DIALOGUE_PORTRAIT_X + portraitWidth + DIALOGUE_BOX_SPACING;
        int textWidth = screen.getWidth() - boxX - DIALOGUE_PORTRAIT_X;
        return wrapRanges(text, textWidth - 2 * DIALOGUE_TEXT_PADDING).size();
    }

    /** Gap between the line under the HUD and the question dialogue. */
    private static final int QUESTION_TOP_GAP = 4;
    /** Gap kept free above the banner, under the question dialogue. */
    private static final int QUESTION_BANNER_GAP = 8;

    /**
     * Draws a story question as a conversation at the top of the game board:
     * the speaker's portrait on the left and her typed line to its right, the
     * same layout as the pilot's dialogue, between the line under the HUD and
     * the banner. Her mouth moves while the line is typed.
     *
     * @param screen
     *            Screen to draw on.
     * @param speaker
     *            Who speaks: her portrait is drawn and her name is shown
     *            above the line.
     * @param text
     *            Full line.
     * @param visibleChars
     *            Number of characters shown so far.
     * @param speaking
     *            Whether she is still talking, which moves her mouth.
     * @param prompt
     *            Whether to show the prompt for the next step.
     * @param top
     *            Height of the line under the HUD.
     * @param millis
     *            Current time, drives the portrait and the prompt.
     */
    public void drawStoryQuestionDialogue(final Screen screen,
            final story.StorySpeaker speaker, final String text,
            final int visibleChars,
            final boolean speaking, final boolean prompt, final int top,
            final long millis) {
        int boxY = topDialogueY(top);
        int boxHeight = topDialogueHeight(screen, top);
        // Both women are drawn in the same source size, so the frame is too.
        int portraitWidth = (int) Math.round(boxHeight
                * story.ControllerPortrait.SRC_W
                / story.ControllerPortrait.SRC_H);
        if (speaker == story.StorySpeaker.SERA) {
            story.SeraPortrait.draw((Graphics2D) backBufferGraphics,
                    DIALOGUE_PORTRAIT_X, boxY, portraitWidth, boxHeight,
                    millis / 1000.0, speaking);
        } else {
            story.ControllerPortrait.draw((Graphics2D) backBufferGraphics,
                    DIALOGUE_PORTRAIT_X, boxY, portraitWidth, boxHeight,
                    millis / 1000.0, speaking);
        }
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawRect(DIALOGUE_PORTRAIT_X, boxY,
                portraitWidth - 1, boxHeight - 1);
        drawDialogueTextBox(screen, speaker.displayName(), text, visibleChars,
                prompt, portraitWidth, boxY, boxHeight, millis);
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
     * Draws a notice, a line of narration, typed out in one wide box at the
     * top of the game board: the place where the character speaks next, so
     * her portrait and line replace the box.
     *
     * @param screen
     *            Screen to draw on.
     * @param text
     *            Full text of the notice.
     * @param visibleChars
     *            Number of characters shown so far.
     * @param done
     *            Whether the whole text is shown, which shows the prompt.
     * @param top
     *            Height of the line under the HUD.
     * @param millis
     *            Current time, for the blinking prompt.
     */
    public void drawStoryNotice(final Screen screen, final String text,
            final int visibleChars, final boolean done, final int top,
            final long millis) {
        int boxX = DIALOGUE_PORTRAIT_X;
        int boxY = topDialogueY(top);
        int boxWidth = screen.getWidth() - 2 * DIALOGUE_PORTRAIT_X;
        int boxHeight = topDialogueHeight(screen, top);
        backBufferGraphics.setColor(Color.BLACK);
        backBufferGraphics.fillRect(boxX, boxY, boxWidth, boxHeight);
        backBufferGraphics.setColor(Color.GREEN);
        backBufferGraphics.drawRect(boxX, boxY, boxWidth - 1, boxHeight - 1);
        backBufferGraphics.setColor(Color.WHITE);
        drawTypedText(text, visibleChars, boxX + DIALOGUE_TEXT_PADDING,
                boxY + 30, boxWidth - 2 * DIALOGUE_TEXT_PADDING);
        if (done && (millis / 500) % 2 == 0) {
            backBufferGraphics.setColor(Color.GRAY);
            String hint = LanguageManager.get("key.space").toUpperCase(Locale.ROOT);
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
            drawCenteredRegularString(screen, LanguageManager.get("story.chooseSide"),
                    lowerY + 24);
        }
    }

    /** Blinking "press space" prompt shown when the text is complete. */
    private void drawPrompt(final Screen screen, final boolean show,
            final long millis, final int y) {
        if (show && (millis / 500) % 2 == 0) {
            backBufferGraphics.setColor(Color.GRAY);
            drawCenteredRegularString(screen, LanguageManager.get("story.pressSpace"), y);
        }
    }

    public void drawSettingsMenu(final Screen screen, final int selectedOption) {
        String[] options = getSettingsMenuOptions();
        String settingsTitle = LanguageManager.get("settings.title");

        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, settingsTitle, screen.getHeight() / 5);
        drawIcon(Icon.SETTINGS, screen.getWidth() / 2
                - fontBigMetrics.stringWidth(settingsTitle) / 2
                - Icon.SETTINGS.getWidth() * 3 - 10,
                screen.getHeight() / 5 - 22, 3, Color.GREEN);

        for (int i = 0; i < options.length; i++) {
            boolean selected = i == selectedOption;
            int labelY = screen.getHeight() / 2
                    + i * fontRegularMetrics.getHeight() * 2;
            backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, options[i], labelY);
            drawMenuIcon(screen, SETTINGS_ICONS[i], options[i], labelY, selected);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, LanguageManager.get("settings.instruction"),
                screen.getHeight() / 8 * 7);
    }

    private String[] getSettingsMenuOptions() {
        return new String[] {
            LanguageManager.get("settings.audio"),
            LanguageManager.get("settings.controls"),
            LanguageManager.get("settings.color"),
            LanguageManager.get("settings.changeLanguageLabel"),
            LanguageManager.get("settings.back")
        };
    }

    /** Draws the ship preview and the custom color editing controls. */
    public void drawShipColorSelection(final Screen screen,
            final Ship previewShip, final Ship secondPlayerPreviewShip,
            final Rectangle colorBar,
            final boolean editingCustomColor, final int selectedPlayerIndex) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, LanguageManager.get("settings.color.title"), screen.getHeight() / 5);
        drawEntity(previewShip, previewShip.getPositionX(), previewShip.getPositionY());
        drawEntity(secondPlayerPreviewShip,
                secondPlayerPreviewShip.getPositionX(),
                secondPlayerPreviewShip.getPositionY());

        Ship selectedShip = selectedPlayerIndex == 0
                ? previewShip : secondPlayerPreviewShip;
        if (screen instanceof MenuScreen menu) {
            menu.menuTarget(0, previewShip.getPositionX() - 6, previewShip.getPositionY() - 12,
                    previewShip.getWidth() + 12, previewShip.getHeight() + 18);
            menu.menuTarget(1, secondPlayerPreviewShip.getPositionX() - 6,
                    secondPlayerPreviewShip.getPositionY() - 12, secondPlayerPreviewShip.getWidth() + 12,
                    secondPlayerPreviewShip.getHeight() + 18);
        }
        int markerCenterX = selectedShip.getPositionX()
                + selectedShip.getWidth() / 2;
        int markerTopY = selectedShip.getPositionY() - 12;
        backBufferGraphics.setColor(GameColors.HIGHLIGHT);
        backBufferGraphics.fillPolygon(
                new int[] {markerCenterX - 5, markerCenterX + 5, markerCenterX},
                new int[] {markerTopY, markerTopY, markerTopY + 6}, 3);

        boolean custom = ShipCustomization.isCustomColor(selectedPlayerIndex);
        backBufferGraphics.setColor(Color.WHITE);
        String colorName = ShipCustomization.getColorName(selectedPlayerIndex);
        String colorKey = "color." + colorName.toLowerCase(Locale.ROOT);
        String label = LanguageManager.get("settings.colorLabel")
                + LanguageManager.get(colorKey);
        if (custom) {
            if (screen instanceof MenuScreen menu)
                menu.menuTarget(ShipColorScreen.HUE_BAR, colorBar.x, colorBar.y - 6,
                        colorBar.width, colorBar.height + 12);
            drawShipColorBar(colorBar,
                    ShipCustomization.getCustomHue(selectedPlayerIndex),
                    editingCustomColor);
            label += String.format(" #%06X",
                    selectedShip.getColor().getRGB() & 0xFFFFFF);
            drawCenteredRegularString(screen, LanguageManager.get(editingCustomColor
                    ? "settings.colorEdit" : "settings.colorEnter"),
                    screen.getHeight() - 110);
        }
        drawCenteredRegularString(screen, label,
                screen.getHeight() / 2 + (custom ? 75 : 45));
        String playerName = LanguageManager.get(selectedPlayerIndex == 0
                ? "settings.colorPlayerOne" : "settings.colorPlayerTwo");
        drawCenteredRegularString(screen, String.format(
                LanguageManager.get("settings.colorPlayer"), playerName),
                screen.getHeight() - 85);
        drawCenteredRegularString(screen, LanguageManager.get(editingCustomColor
                ? "settings.colorDone" : "settings.colorChoose"),
                screen.getHeight() - 60);
        int colorY = screen.getHeight() / 2 + (custom ? 75 : 45);
        drawMenuAction(screen, MenuScreen.PREVIOUS, "<", 35, colorY, false, true);
        drawMenuAction(screen, MenuScreen.NEXT, ">", screen.getWidth() - 35,
                colorY, false, true);
        drawMenuAction(screen, MenuScreen.BACK,
                LanguageManager.get("settings.color.instruction.3rdline"),
                screen.getWidth() / 2, screen.getHeight() - 35, false, true);
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
        drawCenteredBigString(screen, LanguageManager.get("settings.controlsTitle"),
                screen.getHeight() / 5);

        for (int i = 0; i < labels.length; i++) {
            backBufferGraphics.setColor(
                    i == selectedOption ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i],
                    screen.getHeight() / 2
                            + i * fontRegularMetrics.getHeight() * 2);
        }

        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen,
                LanguageManager.get("settings.controlsInstruction"),
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
        String titleString = LanguageManager.get("settings.audioTitle");
        String selectString = LanguageManager.get("settings.audioSelect");
        String adjustString = LanguageManager.get("settings.audioAdjust");
        String returnString = LanguageManager.get("settings.audioReturn");
        // In the order of the options: BGM volume, SFX volume, mute.
        String[] optionStrings = { LanguageManager.get("settings.bgmVolume"),
                LanguageManager.get("settings.sfxVolume"),
                LanguageManager.get("settings.mute") };
        // Values shown on the right side of each option.
        String[] valueStrings = { Integer.toString(bgmVolume),
                Integer.toString(sfxVolume), LanguageManager.get(
                        muted ? "settings.on" : "settings.off") };
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
            int baseline = firstOptionY + optionSpacing * i;
            if (i == muteIndex) {
                menuTextTarget(screen, i, columnLeft, baseline, columnWidth, fontRegularMetrics);
            } else {
                menuTextTarget(screen, AudioControlScreen.VOLUME_DOWN + i, valueLeft, baseline,
                        fontRegularMetrics.stringWidth("<"), fontRegularMetrics);
                int arrowWidth = fontRegularMetrics.stringWidth(">");
                menuTextTarget(screen, AudioControlScreen.VOLUME_UP + i, valueRight - arrowWidth,
                        baseline, arrowWidth, fontRegularMetrics);
                if (screen instanceof MenuScreen menu)
                    menu.menuTarget(AudioControlScreen.VOLUME_BAR + i, columnLeft, baseline + barGap,
                            columnWidth, barHeight + 2);
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
        drawMenuAction(screen, MenuScreen.BACK, returnString, screen.getWidth() / 2,
                screen.getHeight() / 8 * 7, false, true);
    }

    public int volumeAt(Screen screen, int x) {
        int left = (screen.getWidth() - AUDIO_COLUMN_WIDTH) / 2;
        return Math.max(0, Math.min(100, Math.round(100f * (x - left) / (AUDIO_COLUMN_WIDTH - 1))));
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
                return LanguageManager.get("key.leftArrow");
            case java.awt.event.KeyEvent.VK_RIGHT:
                return LanguageManager.get("key.rightArrow");
            case java.awt.event.KeyEvent.VK_UP:
                return LanguageManager.get("key.upArrow");
            case java.awt.event.KeyEvent.VK_DOWN:
                return LanguageManager.get("key.downArrow");
            case java.awt.event.KeyEvent.VK_SPACE:
                return LanguageManager.get("key.space");
            default:
                if (keyCode >= java.awt.event.KeyEvent.VK_A
                        && keyCode <= java.awt.event.KeyEvent.VK_Z) {
                    return String.valueOf((char) keyCode);
                }
                return java.awt.event.KeyEvent.getKeyText(keyCode);
        }
    }

    public void initDrawing(final Screen screen) {
        refreshFontsForCurrentLanguage();
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
                showBackBuffer();
        } finally {
            backBufferGraphics.dispose();
            if (graphics != null)
                graphics.dispose();
        }
    }

    /** The back buffer made as big as the window, when the display is scaled. */
    private static BufferedImage scaledBackBuffer;

    /**
     * Shows the back buffer in the window. When the display is scaled by a
     * fraction (125 % is common on Windows), Java scales the picture itself
     * as it copies it to the window, and a thin line, such as the border of a
     * dialogue box, steps up or down by a pixel here and there. So then the
     * picture is scaled here, evenly over its whole size, and copied to the
     * window as it is.
     */
    private static void showBackBuffer() {
        Insets insets = frame.getInsets();
        if (!(graphics instanceof Graphics2D window)) {
            graphics.drawImage(backBuffer, insets.left, insets.top, frame);
            return;
        }
        double scaleX = window.getTransform().getScaleX();
        double scaleY = window.getTransform().getScaleY();
        if (isWholeScale(scaleX) && isWholeScale(scaleY)) {
            graphics.drawImage(backBuffer, insets.left, insets.top, frame);
            return;
        }
        scaledBackBuffer = scaleNearest(backBuffer,
                (int) Math.round(backBuffer.getWidth() * scaleX),
                (int) Math.round(backBuffer.getHeight() * scaleY),
                scaledBackBuffer);
        Graphics2D device = (Graphics2D) window.create();
        // Pixels of the display, with no scaling of Java's.
        device.setTransform(new AffineTransform());
        device.drawImage(scaledBackBuffer, (int) Math.round(insets.left * scaleX),
                (int) Math.round(insets.top * scaleY), null);
        device.dispose();
    }

    /** @return whether a display scale is a whole number, such as 1 or 2. */
    static boolean isWholeScale(final double scale) {
        return Math.abs(scale - Math.rint(scale)) < 1e-6;
    }

    /**
     * Scales a picture to a size, each pixel becoming the same number of
     * pixels all along a line, so the lines of the picture stay straight.
     *
     * @param source
     *            Picture to scale.
     * @param width
     *            Width of the result.
     * @param height
     *            Height of the result.
     * @param reuse
     *            An earlier result to draw into when it has the same size, or
     *            null.
     * @return the scaled picture.
     */
    static BufferedImage scaleNearest(final BufferedImage source,
            final int width, final int height, final BufferedImage reuse) {
        BufferedImage target = reuse != null && reuse.getWidth() == width
                && reuse.getHeight() == height ? reuse
                : new BufferedImage(width, height,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return target;
    }

    public void drawNameInput(final Screen screen, final char[] name,
            final int nameCharSelected) {
        drawNameInputAt(screen, name, nameCharSelected, screen.getHeight()
                / 4 + fontRegularMetrics.getHeight() * 10,
                fontRegularMetrics.getHeight() * 2);
    }

    private void drawNameInputAt(final Screen screen, final char[] name,
            final int nameCharSelected, final int y, final int spacing) {
        String newRecordString = LanguageManager.get("screen.newRecord");
        String introduceNameString = LanguageManager.get("screen.enterName");

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

            menuTextTarget(screen, i, positionX, y + spacing * 2,
                    fontRegularMetrics.charWidth(name[i]), fontRegularMetrics);
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
        String title = fitText(getAchievementTitle(unlock.getAchievement(),
                unlock.getTier()), metrics, room);
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
        drawCenteredBigString(screen, LanguageManager.get(gameCleared
                ? "screen.gameCleared" : "screen.gameOver"), y);
        if (failedStage > 0) {
            y += spacing;
            backBufferGraphics.setColor(Color.RED);
            drawCenteredRegularString(screen, String.format(
                    LanguageManager.get("screen.stageFailed"), failedStage), y);
        }
        y += spacing * 2;
        backBufferGraphics.setColor(Color.WHITE);
        String[] results = {
            String.format(LanguageManager.get("result.score"), score),
            String.format(LanguageManager.get("result.lives"), lives),
            String.format(LanguageManager.get("result.enemies"), enemies),
            String.format(LanguageManager.get("result.accuracy"), accuracy * 100)
        };
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
                ? String.format(LanguageManager.get("result.achievementPage"),
                        page + 1, pages)
                : LanguageManager.get("result.newAchievements"), y);
        int first = page * MAX_RESULT_ACHIEVEMENTS;
        int last = Math.min(achievements.size(), first + MAX_RESULT_ACHIEVEMENTS);
        for (int i = first; i < last; i++) {
            y += spacing;
            drawResultAchievement(screen, achievements.get(i), y);
        }
        backBufferGraphics.setColor(acceptsInput ? Color.GREEN : Color.GRAY);
        drawResultActions(screen, screen.getHeight() - spacing, acceptsInput);
        backBufferGraphics.setColor(Color.GRAY);
        drawCenteredRegularString(screen, LanguageManager.get("result.controls"),
                screen.getHeight() - spacing * 2);
    }

    /**
     * Draws one achievement row of the results screen, centered: its medal
     * and its name in the tier's color, or a trophy and its name in white.
     */
    private void drawResultAchievement(final Screen screen,
            final AchievementUnlock unlock, final int baseline) {
        boolean tiered = unlock.getAchievement().getTierCount() > 1;
        String title = getAchievementTitle(unlock.getAchievement(), unlock.getTier());
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
        String summary = String.format(Locale.ROOT,
                LanguageManager.get("records.achievementSummary"),
                unlocked, statuses.size());
        if (statuses.size() > perPage)
            summary += "   " + String.format(Locale.ROOT,
                    LanguageManager.get("records.achievementScroll"), first + 1,
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
        backBufferGraphics.drawString(getAchievementStatusTitle(status), left, y);
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
        backBufferGraphics.drawString(getAchievementCondition(
                status.getAchievement(), Math.min(status.getUnlockedTiers() + 1,
                        status.getAchievement().getTierCount())), left,
                y + achievementConditionOffset());
    }

    private String getAchievementStatusTitle(final AchievementStatus status) {
        if (!status.isRevealed())
            return status.getTitle();
        return getAchievementTitle(status.getAchievement(),
                status.getUnlockedTiers());
    }

    private String getAchievementTitle(final Achievement achievement,
            final int tier) {
        String key = "achievement." + achievement.getId() + ".name";
        String name = LanguageManager.get(key);
        if (key.equals(name))
            name = achievement.getName();
        if (achievement.getTierCount() == 1 || tier <= 0)
            return name;
        String tierName = achievement.getTierName(tier);
        String tierKey = "achievement.tier." + tierName.toLowerCase(Locale.ROOT);
        String localizedTier = LanguageManager.get(tierKey);
        return name + " (" + (tierKey.equals(localizedTier)
                ? tierName : localizedTier) + ")";
    }

    private String getAchievementCondition(final Achievement achievement,
            final int tier) {
        String key = "achievement." + achievement.getId() + ".condition";
        String condition = LanguageManager.get(key);
        if (key.equals(condition))
            return achievement.getCondition(tier);
        return condition.replace("{target}",
                Long.toString(achievement.getTarget(tier)));
    }

    private static final int[] TITLE_CODES = { 2, 3, 9, 4, 0 };
    private static final Icon[] TITLE_ICONS = {
        Icon.PLAY, Icon.HIGH_SCORES, Icon.RECORDS, Icon.SETTINGS, Icon.EXIT
    };
    private static final Icon[] SETTINGS_ICONS = {
        Icon.AUDIO, Icon.CONTROLS, Icon.COLOR, Icon.LANGUAGE, Icon.BACK
    };

    public void drawMenu(final Screen screen, final int option) {
        String[] labels = getMainMenuLabels();
        int spacing = fontRegularMetrics.getHeight() * 3 / 2;
        int firstY = screen.getHeight() / 2 + fontRegularMetrics.getHeight() * 2;
        for (int i = 0; i < labels.length; i++) {
            boolean selected = option == TITLE_CODES[i];
            int labelY = firstY + i * spacing;
            backBufferGraphics.setColor(selected ? Color.GREEN : Color.WHITE);
            drawCenteredRegularString(screen, labels[i], labelY);
            if (TITLE_ICONS[i] != null)
                drawMenuIcon(screen, TITLE_ICONS[i], labels[i], labelY, selected);
        }
    }

    public int getMenuOptionAt(final Screen screen,
            final int mouseX, final int mouseY) {
        if (fontRegularMetrics == null)
            return -1;

        int fontHeight = fontRegularMetrics.getHeight();
        int row = getCenteredOptionAt(screen, getMainMenuLabels(), TITLE_ICONS,
                screen.getHeight() / 2 + fontHeight * 2, fontHeight * 3 / 2,
                mouseX, mouseY);
        return row < 0 ? -1 : TITLE_CODES[row];
    }

    private String[] getMainMenuLabels() {
        return new String[] {
            LanguageManager.get("menu.play"),
            LanguageManager.get("menu.highScores"),
            LanguageManager.get("menu.records"),
            LanguageManager.get("menu.settings"),
            LanguageManager.get("menu.exit")
        };
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

        return getCenteredOptionAt(screen, getSettingsMenuOptions(),
                SETTINGS_ICONS, screen.getHeight() / 2, fontRegularMetrics.getHeight() * 2,
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
                getControlsSettingsLabels(selectedOption, waitingForKey), null,
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
    private int getCenteredOptionAt(final Screen screen, final String[] labels, final Icon[] icons,
            final int firstY, final int spacing,
            final int mouseX, final int mouseY) {
        for (int i = 0; i < labels.length; i++) {
            int textWidth = fontRegularMetrics.stringWidth(labels[i]);
            int left = screen.getWidth() / 2 - textWidth / 2;
            int baseline = firstY + i * spacing;
            int top = baseline - fontRegularMetrics.getAscent();
            int bottom = baseline + fontRegularMetrics.getDescent();

            int iconSpace = icons == null ? 0 : icons[i].getWidth() * 2 + 8;
            if (mouseX >= left - iconSpace && mouseX <= left + textWidth
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
        String[] labels = {
            LanguageManager.get("settings.moveLeft"),
            LanguageManager.get("settings.moveRight"),
            LanguageManager.get("settings.fire"),
            LanguageManager.get("ui.back")
        };
        String[] keys = {
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.LEFT)),
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.RIGHT)),
                getKeyDisplayText(ControlsConfig.getKey(ControlsConfig.FIRE))
        };

        for (int i = 0; i < keys.length; i++) {
            labels[i] += ": ";
            labels[i] += waitingForKey && i == selectedOption
                    ? LanguageManager.get("settings.pressKey")
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
    private static final String[] RECORDS_TAB_KEYS = {
        "records.tabRuns", "records.tabAchievements", "records.tabStats"
    };

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
                new int[] { MenuScreen.NONE, MenuScreen.BACK },
                "Left/Right", LanguageManager.get("records.changeTab"),
                "Esc/Space", LanguageManager.get("records.back"));
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
                ? LanguageManager.get(service.getLoadWarning() == null
                        ? "records.profileReady" : "records.profileRestored")
                : LanguageManager.get(status.startsWith("Saving")
                        ? "records.profileSaving" : "records.profileError");
        drawCenteredRegularString(screen, message,
                screen.getHeight() - fontRegularMetrics.getHeight());
    }

    /**
     * Draws every tab name in a row, the current one green and underlined,
     * in the big font when the row fits the screen.
     */
    private void drawRecordsTabBar(final Screen screen, final int tab) {
        String[] tabs = getRecordsTabLabels();
        boolean big = tabBarWidth(fontBigMetrics, tabs) <= screen.getWidth() - 16;
        FontMetrics metrics = big ? fontBigMetrics : fontRegularMetrics;
        backBufferGraphics.setFont(big ? fontBig : fontRegular);
        int gap = metrics.stringWidth("  ");
        int x = (screen.getWidth() - tabBarWidth(metrics, tabs)) / 2;
        int y = screen.getHeight() / 9;
        for (int i = 0; i < tabs.length; i++) {
            int nameWidth = metrics.stringWidth(tabs[i]);
            menuTextTarget(screen, i, x, y, nameWidth, metrics);
            backBufferGraphics.setColor(i == tab ? Color.GREEN : Color.GRAY);
            backBufferGraphics.drawString(tabs[i], x, y);
            if (i == tab)
                backBufferGraphics.fillRect(x, y + 4, nameWidth, 2);
            x += nameWidth + gap;
        }
    }

    private String[] getRecordsTabLabels() {
        String[] labels = new String[RECORDS_TAB_KEYS.length];
        for (int i = 0; i < RECORDS_TAB_KEYS.length; i++)
            labels[i] = LanguageManager.get(RECORDS_TAB_KEYS[i]);
        return labels;
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
    private void drawKeyHelp(final Screen screen, final int y, final int[] targets,
            final String... keysAndActions) {
        Font font = keyHelpFont();
        backBufferGraphics.setFont(font);
        FontMetrics metrics = backBufferGraphics.getFontMetrics(font);
        String[] labels = new String[targets.length];
        int gap = metrics.stringWidth("   ");
        int width = gap * (targets.length - 1);
        for (int i = 0; i < targets.length; i++) {
            labels[i] = "[" + keysAndActions[2 * i] + "] " + keysAndActions[2 * i + 1];
            width += metrics.stringWidth(labels[i]);
        }
        int x = (screen.getWidth() - width) / 2;
        for (int i = 0; i < targets.length; i++) {
            int labelWidth = metrics.stringWidth(labels[i]);
            boolean pointed = targets[i] != MenuScreen.NONE
                    && menuTextTarget(screen, targets[i], x, y, labelWidth, metrics);
            backBufferGraphics.setColor(pointed ? Color.GREEN : Color.LIGHT_GRAY);
            backBufferGraphics.drawString(labels[i], x, y);
            x += labelWidth + gap;
        }
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
    private static int tabBarWidth(final FontMetrics metrics, final String[] tabs) {
        int width = metrics.stringWidth("  ") * (tabs.length - 1);
        for (String name : tabs)
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
        String sortAndMode = LanguageManager.get("records.sort") + ": "
                + getRunOrderName(order) + "   "
                + LanguageManager.get("records.mode") + ": "
                + getRunFilterName(filter);
        if (runs.isEmpty())
            drawCenteredSegments(screen, y, Color.WHITE, sortAndMode);
        else
            drawCenteredSegments(screen, y, Color.WHITE, sortAndMode,
                    Color.GRAY, "   " + (selected + 1) + "/" + runs.size());
        drawKeyHelp(screen, y + line, new int[] { PlayerRecordsScreen.SORT,
                PlayerRecordsScreen.FILTER, MenuScreen.NONE },
                "J", LanguageManager.get("records.sortHelp"),
                "K", LanguageManager.get("records.modeHelp"),
                "Up/Down", LanguageManager.get("records.select"));
        if (runs.isEmpty()) {
            backBufferGraphics.setColor(Color.GRAY);
            String message = filter == RunFilter.ALL
                    ? LanguageManager.get("records.noRuns")
                    : String.format(LanguageManager.get("records.noFilteredRuns"),
                            getRunFilterName(filter));
            drawCenteredRegularString(screen, message, y + line * 3);
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
        drawRunRow(screen, "#", LanguageManager.get("records.column.score"),
                LanguageManager.get("records.column.mode"),
                LanguageManager.get("records.column.date"), y);
        DateFormat date = new SimpleDateFormat("yy-MM-dd HH:mm");
        for (int i = first; i < Math.min(runs.size(), first + perPage); i++) {
            RunRecord run = runs.get(i);
            y += line;
            backBufferGraphics.setColor(i == selected ? Color.GREEN : Color.WHITE);
            if (screen instanceof MenuScreen menu)
                menu.menuTarget(PlayerRecordsScreen.TAB_COUNT + i, 8, y - fontRegularMetrics.getAscent(),
                        screen.getWidth() - 16, line);
            drawRunRow(screen, Integer.toString(i + 1), Integer.toString(run.getScore()),
                    getRunModeName(run.getMode()),
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
        drawCenteredRegularString(screen, String.format(
                LanguageManager.get("records.runSummary"),
                run.getScore(), run.getEnemiesDefeated(),
                formatPlayTime(run.getActiveDurationMillis())), y);
        backBufferGraphics.setColor(Color.WHITE);
        List<String> details = RunRecordFormatter.detailLines(run);
        int rows = RUN_DETAIL_LINES - 1;
        int width = screen.getWidth();
        for (int i = 0; i < details.size(); i++) {
            // Each detail line is "Label: value".
            String[] parts = details.get(i).split(": ", 2);
            boolean left = i < rows;
            String value = parts.length > 1 ? parts[1] : "";
            if ("Mode".equals(parts[0]))
                value = getRunModeName(run.getMode());
            drawLabelValue(getRunDetailLabel(parts[0]), value,
                    width * (left ? 4 : 52) / 100, width * (left ? 45 : 96) / 100,
                    y + line * (i % rows + 1));
        }
    }

    private String getRunModeName(final records.RunMode mode) {
        return LanguageManager.get("records.filter." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getRunFilterName(final RunFilter filter) {
        return LanguageManager.get("records.filter." + filter.name().toLowerCase(Locale.ROOT));
    }

    private String getRunOrderName(final RunOrder order) {
        return LanguageManager.get("records.order." + order.name().toLowerCase(Locale.ROOT));
    }

    private String getRunDetailLabel(final String label) {
        switch (label) {
        case "Mode": return LanguageManager.get("records.detail.mode");
        case "Stage reached": return LanguageManager.get("records.detail.stage");
        case "Waves reached": return LanguageManager.get("records.detail.waves");
        case "Shots fired": return LanguageManager.get("records.detail.shots");
        case "Hits": return LanguageManager.get("records.detail.hits");
        case "Accuracy": return LanguageManager.get("records.detail.accuracy");
        case "Times hit": return LanguageManager.get("records.detail.timesHit");
        case "Bosses defeated": return LanguageManager.get("records.detail.bosses");
        case "Items used": return LanguageManager.get("records.detail.items");
        default: return label;
        }
    }

    /** Draws the personal bests and the cumulative stats. */
    private void drawRecordsStats(final Screen screen, final RecordsService service) {
        int line = fontRegularMetrics.getHeight();
        int y = getRecordsPageTop(screen);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, LanguageManager.get("records.personalBests"), y);
        // Labels start and values end at the same place on every line.
        int left = screen.getWidth() * 16 / 100;
        int right = screen.getWidth() * 84 / 100;
        backBufferGraphics.setColor(Color.WHITE);
        if (service.getRecordCount() == 0) {
            drawCenteredRegularString(screen, LanguageManager.get("records.noRuns"), y + line);
            y += line * 2;
        } else {
            drawLabelValue(LanguageManager.get("records.highestScore"),
                    Integer.toString(service.getHighestScore()),
                    left, right, y + line);
            drawLabelValue(LanguageManager.get("records.mostEnemies"),
                    Integer.toString(service.getMostEnemiesDefeated()),
                    left, right, y + line * 2);
            drawLabelValue(LanguageManager.get("records.longestSurvival"),
                    formatPlayTime(service.getLongestSurvivalMillis()), left, right, y + line * 3);
            y += line * 4;
        }
        CumulativeStats stats = service.getCumulativeStats();
        String[][] totals = {
                { LanguageManager.get("records.completedRuns"),
                        Integer.toString(stats.getRunsCompleted()) },
                { LanguageManager.get("records.enemiesDefeated"),
                        Long.toString(stats.getEnemiesDefeated()) },
                { LanguageManager.get("records.playTime"),
                        formatPlayTime(stats.getActiveDurationMillis()) },
                { LanguageManager.get("records.stagesCleared"),
                        Long.toString(stats.getStagesCleared()) },
                { LanguageManager.get("records.endingsSeen"),
                        stats.getEndingsSeen() + "/" + stats.getEndingCount() },
                { LanguageManager.get("records.shotsFired"),
                        Long.toString(stats.getShotsFired()) },
                { LanguageManager.get("records.hits"), stats.getHits() + String.format(java.util.Locale.ROOT,
                        " (%.1f%%)", stats.getAccuracy() * 100) },
                { LanguageManager.get("records.timesHit"),
                        Long.toString(stats.getTimesHit()) },
                { LanguageManager.get("records.bossesDefeated"),
                        Long.toString(stats.getBossesDefeated()) },
                { LanguageManager.get("records.itemsUsed"),
                        Long.toString(stats.getItemsUsed()) } };
        y += line;
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredRegularString(screen, LanguageManager.get("records.totals"), y);
        backBufferGraphics.setColor(Color.WHITE);
        for (String[] total : totals) {
            y += line;
            drawLabelValue(total[0], total[1], left, right, y);
        }
    }

    /**
     * @return play time such as 42.5s, 3m 07s or 2h 05m; shorter values
     *         keep the tenths of a second.
     */
    private static String formatPlayTime(final long millis) {
        long seconds = millis / 1000;
        if (seconds < 60)
            return String.format(Locale.ROOT, LanguageManager.get("time.seconds"),
                    millis / 1000.0);
        if (seconds < 3600)
            return String.format(Locale.ROOT, LanguageManager.get("time.minutesSeconds"),
                    seconds / 60, seconds % 60);
        return String.format(Locale.ROOT, LanguageManager.get("time.hoursMinutes"),
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

    /** The drawing coordinates also define the active area, including a small click margin. */
    private boolean menuTextTarget(Screen screen, int id, int x, int baseline,
            int width, FontMetrics metrics) {
        return screen instanceof MenuScreen menu && menu.menuTarget(id, x - 4,
                baseline - metrics.getAscent() - 2, width + 8, metrics.getHeight() + 4);
    }

    private void drawMenuAction(Screen screen, int id, String label, int centerX, int y,
            boolean selected, boolean enabled) {
        int width = fontRegularMetrics.stringWidth(label);
        int x = centerX - width / 2;
        boolean underPointer = enabled && menuTextTarget(screen, id, x, y, width, fontRegularMetrics);
        backBufferGraphics.setFont(fontRegular);
        backBufferGraphics.setColor(!enabled ? Color.GRAY : selected ? Color.GREEN : Color.WHITE);
        backBufferGraphics.drawString(label, x, y);
        if (underPointer && (selected || id < MenuScreen.NONE))
            backBufferGraphics.drawLine(x, y + 3, x + width, y + 3);
    }

    private void drawMenuHint(Screen screen, String text) {
        backBufferGraphics.setColor(Color.LIGHT_GRAY);
        drawCenteredRegularString(screen, text, screen.getHeight() - 24);
    }

    private void drawResultActions(Screen screen, int y, boolean enabled) {
        drawMenuAction(screen, MenuScreen.NEXT, LanguageManager.get("menu.retry"),
                screen.getWidth() / 4, y, false, enabled);
        drawMenuAction(screen, MenuScreen.BACK, LanguageManager.get("menu.backAction"),
                screen.getWidth() * 3 / 4, y, false, enabled);
    }

    public void drawCustomLevelMenu(final Screen screen, final int option,
            final int levelCount) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, LanguageManager.get("custom.title"), 90);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, String.format(
                LanguageManager.get("custom.importedLevels"), levelCount), 140);
        for (int i = 0; i < CustomLevelMenu.MENU_ACTIONS.size(); i++) {
            CustomLevelMenu.Action action = CustomLevelMenu.MENU_ACTIONS.get(i);
            boolean enabled = action.isEnabled(levelCount);
            drawMenuAction(screen, i, action.getLabel(), screen.getWidth() / 2,
                    220 + i * 45, i == option, enabled);
        }
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, LanguageManager.get("custom.controls"),
                screen.getHeight() - 45);
    }

    private void drawFileLabel(final Screen screen, final String text, final int y, final int target) {
        // Use a system font for Unicode filenames, independently of the game's pixel font.
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        backBufferGraphics.setFont(font);
        FontMetrics metrics = backBufferGraphics.getFontMetrics(font);
        String label = FileNameDisplay.fit(text, screen.getWidth() - 48, metrics::stringWidth);
        int width = metrics.stringWidth(label);
        int x = (screen.getWidth() - width) / 2;
        if (target != MenuScreen.NONE)
            menuTextTarget(screen, target, x, y, width, metrics);
        backBufferGraphics.drawString(label, x, y);
    }

    public void drawCustomLevelList(final Screen screen,
            final java.util.List<CustomLevelMenu.Entry> entries, final int selected,
            final CustomLevelMenu.Action action) {
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, action.getLabel(), 65);
        backBufferGraphics.setColor(Color.WHITE);
        drawCenteredRegularString(screen, (selected + 1) + " / " + entries.size(), 105);
        int pageSize = CustomLevelListScreen.PAGE_SIZE;
        int start = selected / pageSize * pageSize;
        for (int i = start; i < Math.min(start + pageSize, entries.size()); i++) {
            backBufferGraphics.setColor(i == selected ? Color.GREEN : Color.WHITE);
            drawFileLabel(screen, (i + 1) + ". " + FileNameDisplay.label(entries.get(i).getFilename()), 155 + (i - start) * 32, i);
        }
        backBufferGraphics.setColor(Color.LIGHT_GRAY);
        drawFileLabel(screen, entries.get(selected).getSource().getParent().toString(),
                screen.getHeight() - 95, MenuScreen.NONE);
        int actionY = screen.getHeight() - 56;
        if (entries.size() > pageSize) {
            drawMenuAction(screen, MenuScreen.PREVIOUS, "< Prev",
                    screen.getWidth() / 4, actionY, false, start > 0);
            drawMenuAction(screen, MenuScreen.NEXT, "Next >",
                    screen.getWidth() * 3 / 4, actionY, false,
                    start + pageSize < entries.size());
        }
        drawMenuAction(screen, MenuScreen.BACK, LanguageManager.get("ui.back"),
                screen.getWidth() / 2, actionY, false, true);
        drawMenuHint(screen, LanguageManager.get("custom.controls"));
    }

    /** Draw the countdown behind gameplay sprites using the game's pixel font. */
    public void drawTimeAttackCountdown(final Screen screen, final int seconds, final float opacity) {
        boolean finalCountdown = seconds <= 10;
        String label = String.format(LanguageManager.get(finalCountdown
                ? "timeAttack.seconds" : "timeAttack.secondsLeft"), seconds);
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
        String label = String.format(LanguageManager.get("timeAttack.hud"), seconds);
        backBufferGraphics.drawString(label, 10, 25);
    }

    public void drawTimeAttackResult(final Screen screen, final int clearedStages,
            final int totalStages, final int score) {
        boolean cleared = clearedStages == totalStages;
        backBufferGraphics.setColor(cleared ? Color.GREEN : Color.RED);
        drawCenteredBigString(screen, LanguageManager.get(cleared
                ? "timeAttack.clear" : "timeAttack.gameOver"), 125);
        backBufferGraphics.setColor(Color.WHITE);
        if (!cleared)
            drawCenteredRegularString(screen, LanguageManager.get("timeAttack.timeUp"), 175);
        drawCenteredRegularString(screen, String.format(Locale.ROOT,
                LanguageManager.get("timeAttack.stagesCleared"),
                clearedStages, totalStages), 230);
        drawCenteredRegularString(screen, String.format(Locale.ROOT,
                LanguageManager.get("timeAttack.score"), score), 270);
        drawResultActions(screen, screen.getHeight() - 70, true);
        drawMenuHint(screen, LanguageManager.get("timeAttack.controls"));
    }

    public void drawGameModeMenu(final Screen screen, final int option) {
        String[] labels = {
            LanguageManager.get("mode.endless"),
            LanguageManager.get("mode.campaign"),
            LanguageManager.get("mode.story"),
            LanguageManager.get("mode.custom"),
            LanguageManager.get("mode.timeAttack"),
            LanguageManager.get("ui.back")
        };

        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, LanguageManager.get("mode.title"),
                screen.getHeight() / 3);

        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < labels.length; i++) {
            drawMenuAction(screen, i, labels[i], screen.getWidth() / 2,
                    screen.getHeight() / 3 + 60 + i * 40, i == option, true);
        }
        drawMenuHint(screen, "Arrows + Enter / Point + Click");
    }

    // 2P 선택 화면을 위한 메인 메서드 추가
    public void drawPlayerSelectMenu(final Screen screen, final int option) {
        String[] labels = {
            LanguageManager.get("players.one"),
            LanguageManager.get("players.two"),
            LanguageManager.get("ui.back")
        };

        backBufferGraphics.setFont(fontBig);
        backBufferGraphics.setColor(Color.GREEN);
        drawCenteredBigString(screen, LanguageManager.get("players.title"),
                screen.getHeight() / 3);

        backBufferGraphics.setFont(fontRegular);
        for (int i = 0; i < labels.length; i++) {
            drawMenuAction(screen, i, labels[i], screen.getWidth() / 2,
                    screen.getHeight() / 3 + 60 + i * 40, i == option, true);
        }
        drawMenuHint(screen, "Arrows + Enter / Point + Click");
    }
}
