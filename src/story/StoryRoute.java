package story;

import entity.Boss;
import entity.BulletType;

/**
 * One of the four story routes.
 *
 * The route is decided by the two story questions. The first answer picks
 * the boss of stage 3. The second answer picks the final boss of stage 7
 * and the weapon used from stage 4.
 *
 * Choices are 0 (first option) or 1 (second option).
 */
public enum StoryRoute {

    /** 1.1.1 Absorb the core. */
    // TODO: final boss becomes BOSS_C (VESPERA) when it is added.
    HYBRID(1, 0, 0, Boss.BossType.BOSS_A, BulletType.MULTI, false),
    /** 1.1.2 Destroy the core. */
    HUMAN_VICTORY(2, 0, 1, Boss.BossType.BOSS_A, BulletType.RAPID, false),
    /** 1.2.1 Escort the survivors home. */
    HOMECOMING(3, 1, 0, Boss.BossType.BOSS_B, BulletType.NORMAL, true),
    /** 1.2.2 Infiltrate the mothership. */
    // TODO: final boss becomes BOSS_D (OBELISK) and weapon becomes PIERCE.
    TRUCE(4, 1, 1, Boss.BossType.BOSS_B, BulletType.MULTI, false);

    /** Ending number shown to the records system (1 to 4). */
    private final int endingNumber;
    /** Answer to the first question. */
    private final int firstChoice;
    /** Answer to the second question. */
    private final int secondChoice;
    /** Boss of stage 7. */
    private final Boss.BossType finalBoss;
    /** Weapon used from stage 4. */
    private final BulletType weapon;
    /** Whether grenades are given from stage 4. */
    private final boolean givesGrenades;

    StoryRoute(final int endingNumber, final int firstChoice,
            final int secondChoice, final Boss.BossType finalBoss,
            final BulletType weapon, final boolean givesGrenades) {
        this.endingNumber = endingNumber;
        this.firstChoice = firstChoice;
        this.secondChoice = secondChoice;
        this.finalBoss = finalBoss;
        this.weapon = weapon;
        this.givesGrenades = givesGrenades;
    }

    /**
     * Finds the route for the two answers.
     *
     * @param firstChoice
     *            Answer to the first question, 0 or 1.
     * @param secondChoice
     *            Answer to the second question, 0 or 1.
     * @return Matching route.
     */
    public static StoryRoute of(final int firstChoice,
            final int secondChoice) {
        checkChoice(firstChoice);
        checkChoice(secondChoice);
        for (StoryRoute route : values())
            if (route.firstChoice == firstChoice
                    && route.secondChoice == secondChoice)
                return route;
        throw new IllegalStateException("No route for the given choices.");
    }

    /**
     * Returns the boss of stage 3. It only depends on the first answer,
     * because the second question is asked after that boss.
     *
     * @param firstChoice
     *            Answer to the first question, 0 or 1.
     * @return Boss of stage 3.
     */
    public static Boss.BossType stageThreeBoss(final int firstChoice) {
        checkChoice(firstChoice);
        return firstChoice == 0 ? Boss.BossType.BOSS_A
                : Boss.BossType.BOSS_B;
    }

    private static void checkChoice(final int choice) {
        if (choice != 0 && choice != 1)
            throw new IllegalArgumentException("choice must be 0 or 1.");
    }

    public int getEndingNumber() {
        return this.endingNumber;
    }

    public int getFirstChoice() {
        return this.firstChoice;
    }

    public int getSecondChoice() {
        return this.secondChoice;
    }

    public Boss.BossType getStageThreeBoss() {
        return stageThreeBoss(this.firstChoice);
    }

    public Boss.BossType getFinalBoss() {
        return this.finalBoss;
    }

    public BulletType getWeapon() {
        return this.weapon;
    }

    public boolean givesGrenades() {
        return this.givesGrenades;
    }
}
