package entity.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order and blinking of the active buffs on the HUD: the buff that ends
 * first comes first, and a buff about to end blinks.
 */
public final class BuffHud {

    private static final long BLINK_THRESHOLD_SECONDS = 2;
    private static final long BLINK_PERIOD_MS = 250;

    private BuffHud() {
    }

    /**
     * @param remaining
     *            Whole seconds left of each buff.
     * @return the buffs, the one that ends first first.
     */
    public static <K> List<K> byTimeLeft(final Map<K, Long> remaining) {
        List<Map.Entry<K, Long>> entries =
                new ArrayList<Map.Entry<K, Long>>(remaining.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        List<K> keys = new ArrayList<K>();
        for (Map.Entry<K, Long> entry : entries)
            keys.add(entry.getKey());
        return keys;
    }

    /**
     * @param remainingSeconds
     *            Whole seconds left of a buff.
     * @param nowMs
     *            Current time, which sets the blink phase.
     * @return whether the buff is drawn now: always, until its last seconds,
     *         when it blinks.
     */
    public static boolean isShown(final long remainingSeconds, final long nowMs) {
        return remainingSeconds > BLINK_THRESHOLD_SECONDS
                || (nowMs / BLINK_PERIOD_MS) % 2 == 0;
    }
}
