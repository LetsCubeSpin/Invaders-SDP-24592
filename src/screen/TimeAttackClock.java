package screen;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** One stage's time budget, measured against a monotonic clock. */
final class TimeAttackClock {
    private static final long STAGE_BUDGET = TimeUnit.SECONDS.toNanos(90);
    static final int HIT_PENALTY_SECONDS = 5;
    private static final long HIT_PENALTY = TimeUnit.SECONDS.toNanos(HIT_PENALTY_SECONDS);

    private static final long WARNING_DURATION = TimeUnit.MILLISECONDS.toNanos(1600);
    private static final long FINAL_DIGIT_DURATION = TimeUnit.SECONDS.toNanos(1);

    private int previousCountdownSeconds = 90;
    private int warningSeconds;
    private long warningStartedAt;
    private long finalDigitStartedAt;

    private final LongSupplier nanoTime;
    private boolean started;
    private long start;
    private long penalties;

    TimeAttackClock(final LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    void start() {
        if (!started) {
            start = nanoTime.getAsLong();
            started = true;
        }
    }

    void deductHit() {
        penalties = Math.min(STAGE_BUDGET, penalties + HIT_PENALTY);
    }

    long remainingNanos() {
        long elapsed = started ? nanoTime.getAsLong() - start : 0;
        return Math.max(0, STAGE_BUDGET - elapsed - penalties);
    }

    int remainingSeconds() {
        long second = TimeUnit.SECONDS.toNanos(1);
        return (int) ((remainingNanos() + second - 1) / second);
    }

    // Keep the milestone visible even if a hit takes the timer past it in one frame.
    int countdownSeconds() {
        int seconds = remainingSeconds();
        long now = nanoTime.getAsLong();
        if (seconds <= 30 && previousCountdownSeconds > 30) {
            warningSeconds = 30;
            warningStartedAt = now;
        } else if (seconds <= 60 && previousCountdownSeconds > 60) {
            warningSeconds = 60;
            warningStartedAt = now;
        }
        if (seconds <= 10 && seconds != previousCountdownSeconds) finalDigitStartedAt = now;
        previousCountdownSeconds = seconds;
        if (seconds <= 10) return seconds;
        return now - warningStartedAt < WARNING_DURATION ? warningSeconds : 0;
    }

    float countdownOpacity(final int seconds) {
        boolean finalDigit = seconds <= 10;
        long duration = finalDigit ? FINAL_DIGIT_DURATION : WARNING_DURATION;
        long elapsed = nanoTime.getAsLong() - (finalDigit ? finalDigitStartedAt : warningStartedAt);
        long fadeStart = duration * 3 / 5;
        float progress = Math.max(0f, Math.min(1f, (elapsed - fadeStart) / (float) (duration - fadeStart)));
        float smoothFade = progress * progress * (3f - 2f * progress);
        return 1f - smoothFade;
    }
}
