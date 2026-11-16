package story;

/**
 * One of the story endings.
 *
 * There are four endings for now. More endings can be added here later;
 * each one needs its own unique number.
 */
public enum Ending {

    ENDING1(1),
    ENDING2(2),
    ENDING3(3),
    ENDING4(4);

    /** Ending number, starting at 1. */
    private final int number;

    Ending(final int number) {
        this.number = number;
    }

    /** @return Ending number, starting at 1. */
    public int getNumber() {
        return this.number;
    }

    /**
     * Finds an ending by its number.
     *
     * @param number
     *            Ending number.
     * @return Matching ending.
     */
    public static Ending fromNumber(final int number) {
        for (Ending ending : values())
            if (ending.number == number)
                return ending;
        throw new IllegalArgumentException("Unknown ending number: " + number);
    }
}
