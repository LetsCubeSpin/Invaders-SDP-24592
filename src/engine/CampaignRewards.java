package engine;

import currency.PlayerAssets;
import currency.RewardSystem;
import event.DefeatedEnemyType;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import event.LevelCompletedEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Campaign rewards for one application session; use on the gameplay thread. */
final class CampaignRewards {
    /** Stage of the final boss; any boss defeated there pays the final-boss reward. */
    static final int FINAL_BOSS_LEVEL = 7;

    private final RewardSystem rewards;
    private final RewardSettlementCoordinator settlement;
    private final Deque<Integer> waitingCompletions = new ArrayDeque<Integer>();
    private final Deque<String> waitingDefeats = new ArrayDeque<String>();
    private final Set<String> seenDefeats = new HashSet<String>();
    private String activeRunId;
    private SettlementResult lastResult = SettlementResult.SUCCESS;

    CampaignRewards(PlayerAssets assets, FileManager files) {
        this(assets, files, null);
    }

    CampaignRewards(PlayerAssets assets, FileManager files, Path savePath) {
        rewards = new RewardSystem(assets);
        settlement = new RewardSettlementCoordinator(rewards, assets, files, savePath);
    }

    /** Starts deduplication for a Campaign run without discarding unpaid rewards. */
    void startRun(String runId) {
        if (runId == null || runId.trim().isEmpty()) {
            throw new IllegalArgumentException("Campaign run ID must not be blank.");
        }
        if (!runId.equals(activeRunId)) {
            activeRunId = runId;
            seenDefeats.clear();
        }
    }

    StageSubscription subscribe(GameplayEventPublisher events, int level) {
        Objects.requireNonNull(events, "events");
        if (level < 1) throw new IllegalArgumentException("Campaign level must be positive.");
        return new StageSubscription(events, level);
    }

    SettlementResult getLastResult() {
        return lastResult;
    }

    /** Includes current defeats; a clear bonus is included only after its screen exits. */
    long getPendingCurrency() {
        long pending = rewards.getPendingCurrency();
        for (String rewardKey : waitingDefeats) {
            pending += rewards.getEnemyReward(rewardKey);
        }
        for (int difficulty : waitingCompletions) {
            pending += rewards.getLevelReward(difficulty);
        }
        return pending;
    }

    /** Retries unpaid rewards and saves the latest assets without repeating paid bonuses. */
    SettlementResult retryPendingRewards() {
        if (rewards.getPendingCurrency() > 0) {
            lastResult = settlement.processSettlement();
            if (lastResult != SettlementResult.SUCCESS) return lastResult;
        }
        // Retain defeats that did not fit the pending amount until settlement makes room.
        while (!waitingDefeats.isEmpty()) {
            if (!rewards.rewardEnemy(waitingDefeats.peekFirst())) {
                lastResult = SettlementResult.SETTLEMENT_FAILED;
                return lastResult;
            }
            waitingDefeats.removeFirst();
            lastResult = settlement.processSettlement();
            if (lastResult != SettlementResult.SUCCESS) return lastResult;
        }
        // Keep later completions queued if the balance is full, including across new runs.
        while (!waitingCompletions.isEmpty()) {
            if (!rewards.rewardLevel(waitingCompletions.peekFirst())) {
                lastResult = SettlementResult.SETTLEMENT_FAILED;
                return lastResult;
            }
            waitingCompletions.removeFirst();
            lastResult = settlement.processSettlement();
            if (lastResult != SettlementResult.SUCCESS) return lastResult;
        }
        if (lastResult == SettlementResult.SAVE_FAILED) {
            lastResult = settlement.retrySave();
        }
        return lastResult;
    }

    private static boolean isBoss(DefeatedEnemyType type) {
        switch (type) {
            case BOSS_A:
            case BOSS_B:
            case BOSS_C:
            case BOSS_D:
                return true;
            default:
                return false;
        }
    }

    /** Each screen is a separate attempt, so a new run can earn the same stage again. */
    final class StageSubscription implements AutoCloseable, GameplayEventListener<LevelCompletedEvent> {
        private final GameplayEventPublisher events;
        private final int level;
        private final String runId;
        private final GameplayEventListener<EnemyDefeatedEvent> defeatListener = this::onEnemyDefeated;
        private Integer completedDifficulty;
        private boolean closed;

        private StageSubscription(GameplayEventPublisher events, int level) {
            this.events = events;
            this.level = level;
            this.runId = activeRunId;
            events.subscribe(LevelCompletedEvent.class, this);
            events.subscribe(EnemyDefeatedEvent.class, defeatListener);
        }

        private void onEnemyDefeated(EnemyDefeatedEvent event) {
            if (closed || completedDifficulty != null || runId == null
                    || !runId.equals(activeRunId) || !runId.equals(event.getRunId())) return;
            String rewardKey = rewardKey(event.getEnemyType());
            if (rewards.getEnemyReward(rewardKey) == 0
                    || !seenDefeats.add(event.getEventId())) return;
            if (!rewards.rewardEnemy(rewardKey)) {
                waitingDefeats.addLast(rewardKey);
            }
        }

        /** A boss on the final stage pays the final-boss reward; other defeats pay by type. */
        private String rewardKey(DefeatedEnemyType type) {
            return level == FINAL_BOSS_LEVEL && isBoss(type) ? RewardSystem.FINAL_BOSS : type.name();
        }

        @Override
        public void onEvent(LevelCompletedEvent event) {
            if (closed || completedDifficulty != null || event.getLevelId() != level) return;
            completedDifficulty = event.getDifficulty();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            events.unsubscribe(LevelCompletedEvent.class, this);
            events.unsubscribe(EnemyDefeatedEvent.class, defeatListener);
            // Apply the clear bonus after the game screen, including its end delay, has finished.
            if (completedDifficulty != null) waitingCompletions.addLast(completedDifficulty);
            SettlementResult result = retryPendingRewards();
            if (result != SettlementResult.SUCCESS) {
                Core.getLogger().warning("Campaign reward needs retry: " + result);
            }
        }
    }
}
