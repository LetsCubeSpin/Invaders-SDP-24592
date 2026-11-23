package hud;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import records.AchievementUnlock;
import records.RecordsService;

/**
 * The achievement notice that slides in on the status strip under the HUD,
 * one unlock at a time in the order they happened.
 *
 * <p>There is one per {@link RecordsService}, shared by every game screen,
 * so a notice carries on when the next stage opens a new screen. Its time
 * only runs while a screen updates it: the gap while no game screen is
 * shown, such as between stages, does not count.</p>
 */
public final class AchievementToast {
    /** Time the notice takes to slide in, and again to slide out. */
    public static final long SLIDE_MILLIS = 250;
    /** How long a notice stays when no other is waiting. */
    public static final long SHOW_MILLIS = 3000;
    /** How long it stays when one more is waiting. */
    public static final long SHORT_SHOW_MILLIS = 2000;
    /** How long it stays when two or more are waiting. */
    public static final long SHORTEST_SHOW_MILLIS = 1400;
    /**
     * Longest time between two updates that counts; a longer gap means no
     * game screen was shown in between.
     */
    static final long MAX_FRAME_GAP_MILLIS = 100;

    private static final Map<RecordsService, AchievementToast> TOASTS =
            new WeakHashMap<RecordsService, AchievementToast>();

    private final Supplier<AchievementUnlock> next;
    private final IntSupplier waiting;
    private AchievementUnlock current;
    private long elapsed;
    private long duration;
    private long lastUpdate = -1;

    /**
     * @param next
     *            Takes the next unlock to show, or null when none waits.
     * @param waiting
     *            How many unlocks wait to be shown.
     */
    AchievementToast(final Supplier<AchievementUnlock> next, final IntSupplier waiting) {
        this.next = next;
        this.waiting = waiting;
    }

    /** @return the notice showing the unlocks of the service. */
    public static synchronized AchievementToast of(final RecordsService service) {
        AchievementToast toast = TOASTS.get(service);
        if (toast == null) {
            toast = new AchievementToast(service::pollNotification,
                    service::getPendingNotificationCount);
            TOASTS.put(service, toast);
        }
        return toast;
    }

    /**
     * Advances the notice to the given time: ends the one shown once its
     * time is up and starts the next waiting one.
     *
     * @param nowMillis
     *            Current time.
     * @return the unlock that started showing, for its sound, or null.
     */
    public AchievementUnlock update(final long nowMillis) {
        if (this.lastUpdate >= 0) {
            long gap = nowMillis - this.lastUpdate;
            if (gap > 0 && gap <= MAX_FRAME_GAP_MILLIS)
                this.elapsed += gap;
        }
        this.lastUpdate = nowMillis;
        if (this.current != null && this.elapsed < this.duration)
            return null;
        this.current = this.next.get();
        this.elapsed = 0;
        if (this.current == null)
            return null;
        int left = this.waiting.getAsInt();
        this.duration = left == 0 ? SHOW_MILLIS
                : left == 1 ? SHORT_SHOW_MILLIS : SHORTEST_SHOW_MILLIS;
        return this.current;
    }

    /** @return the unlock shown, or null. */
    public AchievementUnlock getCurrent() {
        return this.current;
    }

    /** @return how many unlocks wait after the one shown. */
    public int getWaiting() {
        return this.current == null ? 0 : this.waiting.getAsInt();
    }

    /**
     * @return how far the notice has slid in, from 0 (off the right of the
     *         screen) to 1 (fully shown).
     */
    public double getShown() {
        if (this.current == null)
            return 0;
        long in = Math.min(this.elapsed, this.duration - this.elapsed);
        return Math.max(0, Math.min(1, (double) in / SLIDE_MILLIS));
    }

    /** Hides the notice shown, when the run it belongs to ends. */
    public void clear() {
        this.current = null;
        this.elapsed = 0;
    }
}
