package hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import entity.item.BuffHud;
import vfx.Icon;

/**
 * What one player's part of the status strip under the HUD shows: the
 * grenades left and an icon for each active buff, the one that ends first
 * first. A buff in its last seconds blinks, so it keeps its place but is
 * not always shown.
 */
public final class ItemStatus {
    private final int grenades;
    private final List<Icon> buffs;
    private final List<Boolean> shown = new ArrayList<Boolean>();

    /**
     * @param grenades
     *            Grenades left; none are shown at 0.
     * @param buffSeconds
     *            Whole seconds left of each active buff, by its icon.
     * @param nowMs
     *            Current time, which sets the blink phase.
     */
    public ItemStatus(final int grenades, final Map<Icon, Long> buffSeconds,
            final long nowMs) {
        this.grenades = Math.max(0, grenades);
        this.buffs = Collections.unmodifiableList(BuffHud.byTimeLeft(buffSeconds));
        for (Icon buff : this.buffs)
            this.shown.add(BuffHud.isShown(buffSeconds.get(buff), nowMs));
    }

    /** @return grenades left. */
    public int getGrenades() {
        return this.grenades;
    }

    /** @return the icon of each active buff, the one that ends first first. */
    public List<Icon> getBuffs() {
        return this.buffs;
    }

    /** @return whether the buff at that place is drawn now, or blinked off. */
    public boolean isShown(final int buff) {
        return this.shown.get(buff);
    }

    /** @return whether there are no grenades and no buffs to show. */
    public boolean isEmpty() {
        return this.grenades == 0 && this.buffs.isEmpty();
    }
}
