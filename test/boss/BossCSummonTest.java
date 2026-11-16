package boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import entity.Boss;
import entity.BossBullet;
import entity.EnemyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossCSummonTest {

    @Test
    void bossCConfigEnablesSummonsPerPhaseAndOtherBossesDoNot() {
        assertFalse(BossAttackConfig.forType(Boss.BossType.BOSS_A)
                .getPhase(1).isSummonEnabled());
        assertFalse(BossAttackConfig.forType(Boss.BossType.BOSS_B)
                .getPhase(1).isSummonEnabled());

        BossAttackConfig bossC = BossAttackConfig.forType(Boss.BossType.BOSS_C);
        assertTrue(bossC.getPhase(1).isSummonEnabled());
        assertEquals(EnemyType.A, bossC.getPhase(1).getSummonEnemyType());

        assertTrue(bossC.getPhase(2).isSummonEnabled());
        assertEquals(EnemyType.B, bossC.getPhase(2).getSummonEnemyType());

        assertTrue(bossC.getPhase(3).isSummonEnabled());
        assertEquals(EnemyType.C, bossC.getPhase(3).getSummonEnemyType());
    }

    @Test
    void summonsPeriodicallyAfterCooldownAndResetsTimer() throws InterruptedException {
        BossAttackSystem system = new BossAttackSystem(
                fastSummonConfig(25, 2), new Random(0));

        // 첫 update()는 전투 시작 처리만 수행하고 즉시 소환하지 않음
        assertNull(system.update(180, 100, 180, 400));
        // 쿨타임 경과 전에는 소환되지 않음
        assertNull(system.update(180, 100, 180, 400));

        Thread.sleep(35);

        BossAttackSystem.AttackResult first =
                system.update(180, 100, 180, 400);
        assertNotNull(first);
        assertEquals(2, first.getSummons().size());
        for (BossAttackSystem.SummonRequest req : first.getSummons()) {
            assertEquals(EnemyType.A, req.getEnemyType());
            assertEquals(104, req.getPositionY());
        }

        // 소환 직후 쿨타임이 초기화되어 다음 프레임에 중복 소환되지 않음
        assertNull(system.update(180, 100, 180, 400));
    }

    @Test
    void changesSummonedEnemyTypeAcrossPhases() throws InterruptedException {
        BossAttackSystem system = new BossAttackSystem(
                fastSummonConfig(20, 1), new Random(0));
        system.update(180, 100, 180, 400); // 전투 시작

        // Phase 1 -> EnemyType.A
        Thread.sleep(30);
        BossAttackSystem.AttackResult phase1Result =
                system.update(180, 100, 180, 400);
        assertNotNull(phase1Result);
        assertEquals(EnemyType.A,
                phase1Result.getSummons().get(0).getEnemyType());

        // Phase 2 -> EnemyType.B
        system.setPhase(2);
        Thread.sleep(30);
        BossAttackSystem.AttackResult phase2Result =
                system.update(180, 100, 180, 400);
        assertNotNull(phase2Result);
        assertEquals(EnemyType.B,
                phase2Result.getSummons().get(0).getEnemyType());

        // Phase 3 -> EnemyType.C
        system.setPhase(3);
        Thread.sleep(30);
        BossAttackSystem.AttackResult phase3Result =
                system.update(180, 100, 180, 400);
        assertNotNull(phase3Result);
        assertEquals(EnemyType.C,
                phase3Result.getSummons().get(0).getEnemyType());
    }

    @Test
    void bossManagerSyncsPhaseChangesAndStopsSummoningWhenDefeated()
            throws InterruptedException {
        BossManager manager = new BossManager(new Random(0));
        Boss boss = manager.spawn(new BossSpawnRequest(
                Boss.BossType.BOSS_C, 180, 78, 7, 0));
        assertNotNull(boss);
        assertEquals(1, boss.getPhase());

        List<BossBullet> bullets = new ArrayList<BossBullet>();
        List<BossAttackSystem.SummonRequest> summons =
                new ArrayList<BossAttackSystem.SummonRequest>();

        // 3인자 오버로드 호출 시에도 예외 없이 동작하는지 확인
        manager.update(180, 400, bullets);

        // BOSS_C 체력 800 기준: Phase 2 임계값 0.7 (560 미만), Phase 3 임계값 0.4 (320 미만)
        boss.takeDamage(250); // HP 550 -> Phase 2
        manager.sync();
        assertEquals(2, boss.getPhase());
        assertEquals(2, manager.getAttackSystem().getPhase());

        boss.takeDamage(250); // HP 300 -> Phase 3
        manager.sync();
        assertEquals(3, boss.getPhase());
        assertEquals(3, manager.getAttackSystem().getPhase());

        // 보스 격파 후에는 쿨타임이 지나도 소환되지 않음
        boss.takeDamage(300); // HP 0 -> DEFEATED
        manager.sync();
        assertTrue(boss.isDefeated());
        assertFalse(manager.getAttackSystem().isActive());

        Thread.sleep(30);
        manager.update(180, 400, bullets, summons);
        assertTrue(summons.isEmpty());
    }

    /**
     * 일반 탄막 간격을 길게(10초) 두고 소환 주기만 짧게 설정해
     * 탄막 발사와 섞이지 않고 소환 동작만 독립적으로 검증하기 위한 설정.
     */
    private static BossAttackConfig fastSummonConfig(
            final int summonIntervalMs, final int summonCount) {
        BossAttackConfig.PhaseConfig p1 =
                new BossAttackConfig.PhaseConfig(1.0, false)
                        .add(new AttackPattern.SingleShot(3), 1)
                        .withSummon(EnemyType.A, summonIntervalMs, summonCount);
        BossAttackConfig.PhaseConfig p2 =
                new BossAttackConfig.PhaseConfig(1.0, false)
                        .add(new AttackPattern.SingleShot(3), 1)
                        .withSummon(EnemyType.B, summonIntervalMs, summonCount);
        BossAttackConfig.PhaseConfig p3 =
                new BossAttackConfig.PhaseConfig(1.0, false)
                        .add(new AttackPattern.SingleShot(3), 1)
                        .withSummon(EnemyType.C, summonIntervalMs, summonCount);
        return new BossAttackConfig(10_000, 1,
                new BossAttackConfig.PhaseConfig[] { p1, p2, p3 }, null);
    }
}
