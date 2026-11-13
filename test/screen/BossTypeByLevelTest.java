package screen;

import java.util.EnumSet;
import java.util.Set;

import entity.Boss;

/**
 * Standalone checks that every boss type is reachable across the boss
 * stages and that boss tiers differ in gameplay attributes.
 */
public final class BossTypeByLevelTest {

	private BossTypeByLevelTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) {
		checkEveryBossTypeIsReachable();
		checkFirstBossStageOpensWithTheSlowBoss();
		checkBossesDifferInMoreThanHealth();

		System.out.println("Boss type by level checks passed.");
	}

	/**
	 * Reads from GameScreen.BOSS_LEVELS rather than hardcoding stage numbers,
	 * ensuring no implemented boss type is left unreachable.
	 */
	private static void checkEveryBossTypeIsReachable() {
		Set<Boss.BossType> reachable = EnumSet.noneOf(Boss.BossType.class);
		for (int level : GameScreen.BOSS_LEVELS) {
			Boss.BossType type = GameScreen.bossTypeForLevel(level);
			check(type != null, "Boss stage " + level + " returns a boss");
			reachable.add(type);
		}
		for (Boss.BossType type : Boss.BossType.values())
			check(reachable.contains(type),
					type + " is reachable in GameScreen.BOSS_LEVELS");
	}

	private static void checkFirstBossStageOpensWithTheSlowBoss() {
		int first = GameScreen.firstBossLevel();
		check(GameScreen.bossTypeForLevel(first) == Boss.BossType.BOSS_A,
				"The first boss stage (" + first + ") uses the slow boss");
	}

	/**
	 * R4 asks for bosses with difficulty levels, so the two must differ in
	 * how they play and not only in the size of their health bar.
	 */
	private static void checkBossesDifferInMoreThanHealth() {
		Boss.BossType slow = Boss.BossType.BOSS_A;
		Boss.BossType fast = Boss.BossType.BOSS_B;

		check(slow.getMaxHealth() != fast.getMaxHealth(),
				"The two bosses differ in health");
		check(slow.getSpeed() != fast.getSpeed(),
				"The two bosses differ in speed");
	}
}
