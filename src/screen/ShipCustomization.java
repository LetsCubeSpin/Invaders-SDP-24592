package screen;

import java.awt.Color;

/** Stores the player's visual ship customization for the current session. */
public final class ShipCustomization {

    private static final Color[] COLORS = {
        Color.GREEN,
        Color.CYAN,
        Color.MAGENTA,
        Color.YELLOW,
        Color.ORANGE,
        Color.BLUE
    };

    private static final String[] COLOR_NAMES = {
        "Green",
        "Cyan",
        "Magenta",
        "Yellow",
        "Orange",
        "Blue"
    };

    private static final int[] SELECTED_COLOR_INDICES = {0, 5};
    private static final float[] CUSTOM_HUES = {0f, 0f};

    private ShipCustomization() {
    }

    public static Color getColor() {
        return getColor(0);
    }

    public static Color getColor(final int playerIndex) {
        int colorIndex = getSelectedColorIndex(playerIndex);
        return colorIndex == COLORS.length
                ? Color.getHSBColor(CUSTOM_HUES[playerIndex], 1f, 1f)
                : COLORS[colorIndex];
    }

    public static String getColorName() {
        return getColorName(0);
    }

    public static String getColorName(final int playerIndex) {
        int colorIndex = getSelectedColorIndex(playerIndex);
        return colorIndex == COLORS.length ? "Custom" : COLOR_NAMES[colorIndex];
    }

    public static boolean isCustomColor() {
        return isCustomColor(0);
    }

    public static boolean isCustomColor(final int playerIndex) {
        return getSelectedColorIndex(playerIndex) == COLORS.length;
    }

    public static float getCustomHue() {
        return getCustomHue(0);
    }

    public static float getCustomHue(final int playerIndex) {
        validatePlayerIndex(playerIndex);
        return CUSTOM_HUES[playerIndex];
    }

    /** Sets the custom hue along the full spectrum, from 0 to 1. */
    public static void setCustomHue(final float hue) {
        setCustomHue(0, hue);
    }

    /** Sets one player's custom hue along the full spectrum, from 0 to 1. */
    public static void setCustomHue(final int playerIndex, final float hue) {
        if (!Float.isFinite(hue) || hue < 0f || hue > 1f) {
            throw new IllegalArgumentException("Hue must be between 0 and 1.");
        }
        validatePlayerIndex(playerIndex);
        CUSTOM_HUES[playerIndex] = hue;
    }

    public static void changeColor(final int direction) {
        changeColor(0, direction);
    }

    public static void changeColor(final int playerIndex, final int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Direction must be -1 or 1.");
        }

        validatePlayerIndex(playerIndex);
        int optionCount = COLORS.length + 1;
        SELECTED_COLOR_INDICES[playerIndex] =
                (SELECTED_COLOR_INDICES[playerIndex] + direction + optionCount)
                        % optionCount;
    }

    private static int getSelectedColorIndex(final int playerIndex) {
        validatePlayerIndex(playerIndex);
        return SELECTED_COLOR_INDICES[playerIndex];
    }

    private static void validatePlayerIndex(final int playerIndex) {
        if (playerIndex < 0 || playerIndex >= SELECTED_COLOR_INDICES.length) {
            throw new IllegalArgumentException("Player index must be 0 or 1.");
        }
    }
}
