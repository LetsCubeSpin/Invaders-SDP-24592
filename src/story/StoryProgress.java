package story;

import entity.Boss;
import entity.BulletType;

/**
 * Remembers the answers given during one story run and tells, for each
 * stage, which boss appears and which weapon is used.
 *
 * Stages are 1-based, from 1 to {@link #TOTAL_STAGES}.
 */
public final class StoryProgress {

    /** Number of stages in story mode. */
    public static final int TOTAL_STAGES = 7;
    /** The first question is asked after this stage. */
    public static final int FIRST_QUESTION_STAGE = 1;
    /** Stage of the first boss. The second question is asked after it. */
    public static final int MID_BOSS_STAGE = 3;
    /** Stage of the final boss. */
    public static final int FINAL_BOSS_STAGE = 7;

    /** Value used while a question has not been answered. */
    private static final int NO_ANSWER = -1;

    private int firstChoice = NO_ANSWER;
    private int secondChoice = NO_ANSWER;

    /**
     * Saves the answer to the first question.
     *
     * @param choice
     *            0 (first option) or 1 (second option).
     */
    public void chooseFirst(final int choice) {
        if (hasFirstChoice())
            throw new IllegalStateException("First question already answered.");
        StoryRoute.stageThreeBoss(choice); // Rejects values other than 0 or 1.
        this.firstChoice = choice;
    }

    /**
     * Saves the answer to the second question.
     *
     * @param choice
     *            0 (first option) or 1 (second option).
     */
    public void chooseSecond(final int choice) {
        if (!hasFirstChoice())
            throw new IllegalStateException("First question not answered yet.");
        if (hasSecondChoice())
            throw new IllegalStateException("Second question already answered.");
        StoryRoute.of(this.firstChoice, choice); // Rejects values other than 0 or 1.
        this.secondChoice = choice;
    }

    public boolean hasFirstChoice() {
        return this.firstChoice != NO_ANSWER;
    }

    public boolean hasSecondChoice() {
        return this.secondChoice != NO_ANSWER;
    }

    public int getFirstChoice() {
        if (!hasFirstChoice())
            throw new IllegalStateException("First question not answered yet.");
        return this.firstChoice;
    }

    /**
     * @return Route decided by both answers.
     */
    public StoryRoute getRoute() {
        if (!hasSecondChoice())
            throw new IllegalStateException("Route is not decided yet.");
        return StoryRoute.of(this.firstChoice, this.secondChoice);
    }

    /**
     * @param stage
     *            Stage number, 1 to 7.
     * @return Whether a boss appears on the stage.
     */
    public static boolean isBossStage(final int stage) {
        checkStage(stage);
        return stage == MID_BOSS_STAGE || stage == FINAL_BOSS_STAGE;
    }

    /**
     * Returns the boss of a stage.
     *
     * @param stage
     *            Stage number, 1 to 7.
     * @return Boss of the stage, or null when the stage has no boss.
     */
    public Boss.BossType bossForStage(final int stage) {
        checkStage(stage);
        if (stage == MID_BOSS_STAGE)
            return StoryRoute.stageThreeBoss(getFirstChoice());
        if (stage == FINAL_BOSS_STAGE)
            return getRoute().getFinalBoss();
        return null;
    }

    /**
     * Returns the weapon of a stage. Stages before the second question use
     * the ship's own weapon. Later stages use the weapon of the route.
     *
     * @param stage
     *            Stage number, 1 to 7.
     * @param shipWeapon
     *            Weapon of the ship the player picked.
     * @return Weapon to use on the stage.
     */
    public BulletType weaponForStage(final int stage,
            final BulletType shipWeapon) {
        checkStage(stage);
        if (shipWeapon == null)
            throw new IllegalArgumentException("shipWeapon must not be null.");
        if (stage <= MID_BOSS_STAGE)
            return shipWeapon;
        return getRoute().getWeapon();
    }

    /**
     * @param stage
     *            Stage number, 1 to 7.
     * @return Whether grenades are given on the stage.
     */
    public boolean givesGrenadesOnStage(final int stage) {
        checkStage(stage);
        return stage > MID_BOSS_STAGE && getRoute().givesGrenades();
    }

    private static void checkStage(final int stage) {
        if (stage < 1 || stage > TOTAL_STAGES)
            throw new IllegalArgumentException(
                    "stage must be between 1 and " + TOTAL_STAGES + ".");
    }
}
