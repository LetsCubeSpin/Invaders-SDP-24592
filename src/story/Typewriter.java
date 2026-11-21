// 글자를 시간에 맞춰 하나씩 드러내는 타자기 효과의 진행 상태를 계산하는 클래스
package story;

/**
 * Reveals a text one character at a time, like a typewriter.
 *
 * The class only counts characters. The caller passes the current time, so
 * the behaviour is the same on every frame rate and easy to test.
 */
public final class Typewriter {

    private final String text;
    private final double charsPerSecond;
    /** Time the typing began, or -1 before {@link #start(long)}. */
    private long startMillis = -1;
    /** True once everything was shown at once. */
    private boolean finished;

    /**
     * @param text
     *            Text to reveal.
     * @param charsPerSecond
     *            Typing speed, greater than zero.
     */
    public Typewriter(final String text, final double charsPerSecond) {
        if (text == null)
            throw new IllegalArgumentException("text must not be null.");
        if (charsPerSecond <= 0)
            throw new IllegalArgumentException(
                    "charsPerSecond must be greater than zero.");
        this.text = text;
        this.charsPerSecond = charsPerSecond;
    }

    /**
     * Starts the typing. Later calls are ignored.
     *
     * @param nowMillis
     *            Current time in milliseconds.
     */
    public void start(final long nowMillis) {
        if (this.startMillis < 0)
            this.startMillis = nowMillis;
    }

    /**
     * @param nowMillis
     *            Current time in milliseconds.
     * @return Number of characters shown so far.
     */
    public int visibleLength(final long nowMillis) {
        if (this.finished)
            return this.text.length();
        if (this.startMillis < 0)
            return 0;
        long elapsed = Math.max(0, nowMillis - this.startMillis);
        double typed = elapsed * this.charsPerSecond / 1000.0;
        return (int) Math.min(this.text.length(), typed);
    }

    /**
     * @param nowMillis
     *            Current time in milliseconds.
     * @return Part of the text shown so far.
     */
    public String visibleText(final long nowMillis) {
        return this.text.substring(0, visibleLength(nowMillis));
    }

    /**
     * @param nowMillis
     *            Current time in milliseconds.
     * @return Whether the whole text is shown.
     */
    public boolean isDone(final long nowMillis) {
        return visibleLength(nowMillis) >= this.text.length();
    }

    /** Shows the whole text at once. */
    public void finish() {
        this.finished = true;
    }

    /** @return The full text. */
    public String getText() {
        return this.text;
    }
}
