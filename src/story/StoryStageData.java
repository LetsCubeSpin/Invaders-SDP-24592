package story;

import java.util.EnumMap;
import java.util.Map;

import engine.GameSettings;
import entity.EnemyType;

import static entity.EnemyType.A;
import static entity.EnemyType.B;
import static entity.EnemyType.C;

/**
 * Stage settings of story mode.
 *
 * Stage 1 is the same for everyone. Stages 2 and 3 depend on the first
 * answer. Stages 4 to 7 depend on the route.
 *
 * GameSettings values: formation width, formation height, base speed,
 * shooting frequency, start X, start Y. These are first values and will be
 * tuned during balancing.
 */
public final class StoryStageData {

    /** Stage 1: Swarm scouts. */
    private static final StoryStage STAGE_ONE = new StoryStage(
            new GameSettings(5, 4, 60, 2000, 20, 100),
            new EnemyType[] {A, A, A, A}, 1, false, 1.0);

    /** Stages 2 and 3, by first answer (0: assault, 1: rescue). */
    private static final StoryStage[][] FIRST_BRANCH = {
        {
            // 1.1 Assault: open space, mostly B.
            new StoryStage(new GameSettings(5, 5, 50, 2500, 60, 100),
                    new EnemyType[] {B, B, B, A, A}, 1, false, 1.0),
            // Stage 3 boss: ARGUS.
            new StoryStage(new GameSettings(6, 5, 40, 1500, 40, 120),
                    new EnemyType[] {C, B, B, A, A}, 1, false, 1.0)
        },
        {
            // 1.2 Rescue: asteroid field around the moon.
            new StoryStage(new GameSettings(5, 5, 50, 2500, 60, 100),
                    new EnemyType[] {B, A, A, A, A}, 1, true, 1.0),
            // Stage 3 boss: NOX.
            new StoryStage(new GameSettings(6, 5, 40, 1500, 40, 120),
                    new EnemyType[] {C, B, B, A, A}, 1, false, 1.0)
        }
    };

    /** Stages 4 to 7 of each route. */
    private static final Map<StoryRoute, StoryStage[]> ROUTE_STAGES =
            new EnumMap<StoryRoute, StoryStage[]>(StoryRoute.class);

    static {
        // 1.1.1 Absorb the core: mostly C, fast enemies.
        ROUTE_STAGES.put(StoryRoute.HYBRID, new StoryStage[] {
            new StoryStage(new GameSettings(6, 5, 25, 1500, 80, 100),
                    new EnemyType[] {C, C, B, B, A}, 2, false, 1.0),
            new StoryStage(new GameSettings(6, 6, 15, 1000, 40, 120),
                    new EnemyType[] {C, C, C, B, B, A}, 2, false, 1.0),
            new StoryStage(new GameSettings(7, 6, 8, 1000, 40, 100),
                    new EnemyType[] {C, C, C, C, B, B}, 2, false, 1.0),
            new StoryStage(new GameSettings(7, 6, 5, 800, 20, 100),
                    new EnemyType[] {C, C, B, B, A, A}, 1, false, 1.0)
        });
        // 1.1.2 Destroy the core: large B and C fleets.
        ROUTE_STAGES.put(StoryRoute.HUMAN_VICTORY, new StoryStage[] {
            new StoryStage(new GameSettings(7, 6, 30, 1500, 40, 100),
                    new EnemyType[] {C, B, B, B, A, A}, 2, false, 1.0),
            new StoryStage(new GameSettings(8, 6, 20, 1000, 20, 120),
                    new EnemyType[] {C, C, B, B, B, A}, 2, false, 1.0),
            new StoryStage(new GameSettings(8, 7, 10, 1000, 20, 100),
                    new EnemyType[] {C, C, B, B, B, A, A}, 2, false, 1.0),
            // ARGUS MK-II: stronger ARGUS.
            new StoryStage(new GameSettings(8, 7, 2, 500, 20, 100),
                    new EnemyType[] {C, C, B, B, B, A, A}, 1, false, 1.5)
        });
        // 1.2.1 Escort home: defending Earth orbit with obstacles.
        ROUTE_STAGES.put(StoryRoute.HOMECOMING, new StoryStage[] {
            new StoryStage(new GameSettings(6, 6, 30, 1500, 80, 100),
                    new EnemyType[] {B, B, A, A, A, A}, 2, true, 1.0),
            new StoryStage(new GameSettings(7, 6, 20, 1000, 40, 120),
                    new EnemyType[] {B, B, B, A, A, A}, 2, true, 1.0),
            new StoryStage(new GameSettings(7, 7, 10, 1000, 40, 100),
                    new EnemyType[] {C, B, B, B, A, A, A}, 2, true, 1.0),
            // Upgraded NOX: stronger NOX.
            new StoryStage(new GameSettings(7, 7, 5, 800, 40, 100),
                    new EnemyType[] {C, B, B, B, A, A, A}, 1, false, 1.5)
        });
        // 1.2.2 Infiltrate the mothership: small elite C formations.
        ROUTE_STAGES.put(StoryRoute.TRUCE, new StoryStage[] {
            new StoryStage(new GameSettings(5, 5, 30, 1500, 100, 100),
                    new EnemyType[] {C, C, C, B, B}, 2, false, 1.0),
            new StoryStage(new GameSettings(5, 6, 20, 1000, 100, 120),
                    new EnemyType[] {C, C, C, C, B, B}, 2, false, 1.0),
            new StoryStage(new GameSettings(6, 6, 10, 1000, 80, 100),
                    new EnemyType[] {C, C, C, C, C, B}, 2, false, 1.0),
            new StoryStage(new GameSettings(6, 6, 5, 800, 80, 100),
                    new EnemyType[] {C, C, C, B, B, B}, 1, false, 1.0)
        });
    }

    private StoryStageData() {
    }

    /**
     * Returns the settings of a story stage.
     *
     * @param stage
     *            Stage number, 1 to 7.
     * @param progress
     *            Answers given so far. Stages 2 and 3 need the first answer,
     *            stages 4 to 7 need both answers.
     * @return Settings of the stage.
     */
    public static StoryStage get(final int stage,
            final StoryProgress progress) {
        if (stage < 1 || stage > StoryProgress.TOTAL_STAGES)
            throw new IllegalArgumentException("stage must be between 1 and "
                    + StoryProgress.TOTAL_STAGES + ".");
        if (progress == null)
            throw new IllegalArgumentException("progress must not be null.");
        if (stage == 1)
            return STAGE_ONE;
        if (stage <= StoryProgress.MID_BOSS_STAGE)
            return FIRST_BRANCH[progress.getFirstChoice()][stage - 2];
        return ROUTE_STAGES.get(progress.getRoute())[stage - 4];
    }
}
