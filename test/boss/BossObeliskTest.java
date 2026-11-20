package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;

import org.junit.jupiter.api.Test;

import entity.Boss;
import entity.BossBullet;
import entity.item.OrbitalAnnihilator;
import event.BossDefeatedEvent;
import event.DefeatedEnemyType;

public class BossObeliskTest {

	@Test
	public void testObeliskStatsAndPhaseTransitions() {
		Boss boss = new Boss(100, 78, Boss.BossType.BOSS_D);

		assertEquals(Boss.BossType.BOSS_D, boss.getType());
		assertEquals(1000, boss.getMaxHealth());
		assertEquals(1, boss.getPhase());

		// 체력 70% 미만 진입 -> Phase 2
		boss.takeDamage(301);
		assertEquals(2, boss.getPhase());

		// 체력 40% 미만 진입 -> Phase 3
		boss.takeDamage(300);
		assertEquals(3, boss.getPhase());
	}

	@Test
	public void testPhase2ShieldConfigAndDamageImmunity() {
		BossAttackConfig config = BossAttackConfig.forType(Boss.BossType.BOSS_D);

		assertFalse(config.getPhase(1).isShieldEnabled());
		assertTrue(config.getPhase(2).isShieldEnabled());
		assertEquals(9000, config.getPhase(2).getShieldIntervalMilliseconds());
		assertEquals(3500, config.getPhase(2).getShieldDurationMilliseconds());
		assertFalse(config.getPhase(3).isShieldEnabled());

		Boss boss = new Boss(100, 78, Boss.BossType.BOSS_D);
		boss.setShieldActive(true);
		assertTrue(boss.isShieldActive());

		// 쉴드 활성 중에는 데미지 무효화
		boss.takeDamage(250);
		assertEquals(1000, boss.getCurrentHealth());

		// 쉴드 해제 후 정상 피격
		boss.setShieldActive(false);
		boss.takeDamage(250);
		assertEquals(750, boss.getCurrentHealth());
	}

	@Test
	public void testPhase3OrbitalLaserConfigAndWindup() {
		BossAttackConfig config = BossAttackConfig.forType(Boss.BossType.BOSS_D);

		assertFalse(config.getPhase(1).isSpecialEnabled());
		assertFalse(config.getPhase(2).isSpecialEnabled());
		assertTrue(config.getPhase(3).isSpecialEnabled());
		assertTrue(config.getSpecialAttack() instanceof SpecialAttack.OrbitalLaser);

		SpecialAttack special = config.getSpecialAttack();
		assertEquals("orbital_laser", special.getName());
		assertEquals(OrbitalAnnihilator.CHARGE_DURATION, special.getWindupMilliseconds());
		assertEquals(OrbitalAnnihilator.BEAM_DURATION, special.getBeamMilliseconds());
		assertEquals(OrbitalAnnihilator.BEAM_WIDTH, special.getBeamWidth());

		// Phase 3 진입 직후 첫 업데이트에서 레이저 충전(windup) 시작 검증
		BossAttackSystem system = new BossAttackSystem(config, new Random(42));
		system.update(116, 92, 116, 400); // 전투 시작 초기화
		system.setPhase(3);

		BossAttackSystem.AttackResult result = system.update(116, 92, 116, 400);
		assertNotNull(result);
		assertTrue(result.isSpecial());
		assertTrue(result.isWindup());
		assertTrue(system.isLaserCharging());
		assertFalse(system.isLaserFiring());

		// 전투 종료 시 상태 초기화
		system.stop();
		assertFalse(system.isLaserCharging());
		assertFalse(system.isShieldActive());
	}

	@Test
	public void testBossManagerLifecycleAndDefeatEvent() {
		BossManager manager = new BossManager(new Random(42));
		Boss spawned = manager.spawn(
				new BossSpawnRequest(Boss.BossType.BOSS_D, 100, 78, 7, 0));

		assertNotNull(spawned);
		manager.update(116, 400, new HashSet<BossBullet>());

		// 1. 쉴드 활성 중에는 치명타 데미지도 방어됨
		spawned.setShieldActive(true);
		spawned.takeDamage(spawned.getMaxHealth());
		assertFalse(spawned.isDefeated());
		assertEquals(spawned.getMaxHealth(), spawned.getCurrentHealth());

		// 2. 쉴드 해제 후 격파 및 BossDefeatedEvent 분류 검증
		spawned.setShieldActive(false);
		spawned.takeDamage(spawned.getMaxHealth());
		manager.sync();

		assertTrue(spawned.isDefeated());
		assertFalse(spawned.isShieldActive());
		assertEquals(BossStatus.BattleState.ENDED,
				manager.getStatus().getBattleState());

		BossDefeatedEvent event = new BossDefeatedEvent(
				"run-1", "evt-1", DefeatedEnemyType.BOSS_D, 5000L);
		assertNotNull(event);
	}
}
