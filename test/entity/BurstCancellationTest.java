package entity;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Standalone checks that the bullets a Rapid burst still owes are dropped,
 * not merely held back, when the ship stops being allowed to shoot.
 */
public final class BurstCancellationTest {

	private BurstCancellationTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	public static void main(final String[] args) throws Exception {
		checkBurstFiresWhenNothingBlocksIt();
		checkWeaponChangeCancelsTheBurst();
		checkCancelledBurstDoesNotResume();

		System.out.println("Burst cancellation checks passed.");
	}

	/** The baseline: a Rapid shot owes a second bullet and fires it. */
	private static void checkBurstFiresWhenNothingBlocksIt() throws Exception {
		Ship ship = rapidShip();
		Set<Bullet> bullets = new HashSet<Bullet>();

		check(ship.shoot(bullets) == 1, "A Rapid shot fires its first bullet");
		check(owed(ship) == 1, "A Rapid shot owes one more bullet");

		allowNextShot(ship);
		check(ship.updateBurst(bullets) == 1, "The owed bullet is fired");
		check(owed(ship) == 0, "Nothing is owed once the burst is done");
	}

	/** Switching weapons drops any outstanding RAPID follow-up. */
	private static void checkWeaponChangeCancelsTheBurst() throws Exception {
		Ship ship = rapidShip();
		Set<Bullet> bullets = new HashSet<Bullet>();

		ship.shoot(bullets);
		check(owed(ship) == 1, "RAPID owes its second shot");
		ship.setBulletType(BulletType.NORMAL);
		check(owed(ship) == 0, "Switching weapons clears the pending shot");

		allowNextShot(ship);
		check(ship.updateBurst(bullets) == 0,
				"No old shot is emitted by the new weapon");
	}

	/** Explicit cancellation must survive subsequent updates. */
	private static void checkCancelledBurstDoesNotResume() throws Exception {
		Ship ship = rapidShip();
		Set<Bullet> bullets = new HashSet<Bullet>();

		ship.shoot(bullets);
		ship.cancelBurst();

		allowNextShot(ship);
		check(ship.updateBurst(bullets) == 0,
				"A cancelled burst does not resume");
		check(owed(ship) == 0, "No follow-up remains");
	}

	private static Ship rapidShip() {
		// The Speedster fires the Rapid bullet type, which owes a second
		// bullet after a 100 ms delay.
		Ship ship = new Ship(100, 100, ShipType.SPEEDSTER);
		check(ship.getCurrentBulletType() == BulletType.RAPID,
				"The Speedster fires Rapid bullets");
		return ship;
	}

	/** Brings the burst's next shot forward, instead of waiting for it. */
	private static void allowNextShot(final Ship ship) throws Exception {
		Field field = Ship.class.getDeclaredField("nextShotTime");
		field.setAccessible(true);
		field.setLong(ship, 0L);
	}

	private static int owed(final Ship ship) throws Exception {
		Field field = Ship.class.getDeclaredField("remainingShots");
		field.setAccessible(true);
		return field.getInt(ship);
	}
}
