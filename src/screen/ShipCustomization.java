package screen;

import engine.LanguageManager;
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

    private static final String[] COLOR_KEYS = {
        "color.green",
        "color.cyan",
        "color.magenta",
        "color.yellow",
        "color.orange",
        "color.blue"
    };

    private static int selectedColorIndex;
    private static float customHue;

    private ShipCustomization() {
    }

    public static Color getColor() {
        return isCustomColor() ? Color.getHSBColor(customHue, 1f, 1f)
                : COLORS[selectedColorIndex];
    }

    public static String getColorName() {
        return isCustomColor() ? LanguageManager.get("color.custom")
                : LanguageManager.get(COLOR_KEYS[selectedColorIndex]);
    }

    public static boolean isCustomColor() {
        return selectedColorIndex == COLORS.length;
    }

    public static float getCustomHue() {
        return customHue;
    }

    /** Sets the custom hue along the full spectrum, from 0 to 1. */
    public static void setCustomHue(final float hue) {
        if (!Float.isFinite(hue) || hue < 0f || hue > 1f) {
            throw new IllegalArgumentException("Hue must be between 0 and 1.");
        }
        customHue = hue;
    }

    public static void changeColor(final int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Direction must be -1 or 1.");
        }

        int optionCount = COLORS.length + 1;
        selectedColorIndex = (selectedColorIndex + direction + optionCount)
                % optionCount;
    }
}
