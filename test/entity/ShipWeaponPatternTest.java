package entity;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponPatternTest {

    @Test
    void normalAndRapidStartWithOneCenteredBulletAndRespectTheConfiguredInterval() {
        for (ShipType type : new ShipType[] {ShipType.STANDARD, ShipType.SPEEDSTER}) {
            Ship ship = new Ship(100, 100, type);
            ship.setShootingInterval(60_000);
            Set<Bullet> bullets = new HashSet<>();
            assertEquals(1, ship.shoot(bullets));
            assertEquals(0, ship.shoot(bullets));
            Bullet bullet = bullets.iterator().next();
            int x = bullet.getPositionX();
            assertFalse(bullet.isEnemyBullet());
            bullet.update();
            assertEquals(x, bullet.getPositionX());
            assertEquals(ship.getPositionX() + ship.getWidth() / 2, x + bullet.getWidth() / 2);
        }
    }

    @Test
    void multiFiresThreeBulletsThatSpreadFromOneOrigin() {
        Ship ship = new Ship(100, 100, ShipType.TANK);
        Set<Bullet> bullets = new HashSet<>();
        assertEquals(3, ship.shoot(bullets));
        Set<Integer> horizontalSteps = new HashSet<>();
        for (Bullet bullet : bullets) {
            int x = bullet.getPositionX();
            assertFalse(bullet.isEnemyBullet());
            bullet.update();
            horizontalSteps.add(bullet.getPositionX() - x);
        }
        assertEquals(Set.of(-2, 0, 2), horizontalSteps);
    }
}
