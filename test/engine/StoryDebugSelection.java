package engine;

import java.util.Locale;

import entity.BulletType;
import entity.ShipType;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryStage;
import story.StoryStageData;

/** One developer-selected stage. Each attempt reconstructs its own story progress. */
record StoryDebugSelection(StoryRoute route, int stage, Difficulty difficulty, ShipType ship) {
    enum Difficulty {
        EASY(GameplayConfig.EASY);

        final int setting;

        Difficulty(int setting) {
            this.setting = setting;
        }
    }

    StoryDebugSelection {
        if (route == null || difficulty == null || ship == null) {
            throw new IllegalArgumentException("Route, difficulty and ship are required.");
        }
        if (stage < 1 || stage > StoryProgress.TOTAL_STAGES) {
            throw new IllegalArgumentException("Stage must be between 1 and " + StoryProgress.TOTAL_STAGES);
        }
    }

    static StoryDebugSelection parse(String[] fields) {
        if (fields.length != 4) {
            throw new IllegalArgumentException("Expected ROUTE STAGE DIFFICULTY SHIP.");
        }
        StoryRoute route = StoryRoute.valueOf(token(fields[0]));
        int stage = Integer.parseInt(token(fields[1]));
        if (!Difficulty.EASY.name().equals(token(fields[2]))) {
            throw new IllegalArgumentException("Unsupported difficulty '" + fields[2] + "'. Expected EASY.");
        }
        if (!ShipType.STANDARD.name().equals(token(fields[3]))) {
            throw new IllegalArgumentException("Unsupported ship '" + fields[3] + "'. Expected STANDARD.");
        }
        return new StoryDebugSelection(route, stage, Difficulty.EASY, ShipType.STANDARD);
    }

    private static String token(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing story debug selection.");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    StoryProgress progress() {
        StoryProgress progress = new StoryProgress();
        if (stage > StoryProgress.FIRST_QUESTION_STAGE) {
            progress.chooseFirst(route.getFirstChoice());
        }
        if (stage > StoryProgress.MID_BOSS_STAGE) {
            progress.chooseSecond(route.getSecondChoice());
        }
        return progress;
    }

    String describe() {
        StoryProgress progress = progress();
        StoryStage data = StoryStageData.get(stage, progress);
        BulletType weapon = progress.weaponForStage(stage, ship.getBulletType());
        return "Route " + route + "   |   Stage " + stage + "   |   " + difficulty + "   |   " + ship
                + "\nWaves: " + data.getWaveCount() + "   Boss: "
                + (StoryProgress.isBossStage(stage) ? progress.bossForStage(stage) : "none")
                + "\nStory loadout: " + weapon + "   Starting grenades: "
                + (progress.givesGrenadesOnStage(stage) ? "granted" : "none")
                + "\nCurrent starting equipment: NORMAL bullets and zero grenades.";
    }
}
