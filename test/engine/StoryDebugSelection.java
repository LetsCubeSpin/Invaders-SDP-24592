package engine;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import entity.BulletType;
import entity.ShipType;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryStage;
import story.StoryStageData;

/** One developer-selected stage. Each attempt reconstructs its own story progress. */
record StoryDebugSelection(StoryRoute route, int stage, Difficulty difficulty, ShipType ship,
        StoryDebugModifiers modifiers) {
    StoryDebugSelection(StoryRoute route, int stage, Difficulty difficulty, ShipType ship) {
        this(route, stage, difficulty, ship, StoryDebugModifiers.defaults());
    }

    enum Difficulty {
        EASY(GameplayConfig.EASY);

        final int setting;

        Difficulty(int setting) {
            this.setting = setting;
        }
    }

    StoryDebugSelection {
        if (route == null || difficulty == null || ship == null || modifiers == null) {
            throw new IllegalArgumentException("Route, difficulty, ship and modifiers are required.");
        }
        if (stage < 1 || stage > StoryProgress.TOTAL_STAGES) {
            throw new IllegalArgumentException("Stage must be between 1 and " + StoryProgress.TOTAL_STAGES);
        }
    }

    static StoryDebugSelection parse(String[] fields) {
        if (fields.length < 4) {
            throw new IllegalArgumentException("Expected ROUTE STAGE DIFFICULTY SHIP followed by optional modifier pairs.");
        }
        StoryRoute route = StoryRoute.valueOf(token(fields[0]));
        int stage = Integer.parseInt(token(fields[1]));
        if (!Difficulty.EASY.name().equals(token(fields[2]))) {
            throw new IllegalArgumentException("Unsupported difficulty '" + fields[2] + "'. Expected EASY.");
        }
        if (!ShipType.STANDARD.name().equals(token(fields[3]))) {
            throw new IllegalArgumentException("Unsupported ship '" + fields[3] + "'. Expected STANDARD.");
        }
        StoryDebugModifiers defaults = StoryDebugModifiers.defaults();
        boolean invincible = defaults.invincible();
        int lives = defaults.startingLives();
        double hp = defaults.enemyHpMultiplier();
        double speed = defaults.enemySpeedMultiplier();
        Set<String> seen = new HashSet<>();
        for (int i = 4; i < fields.length; i += 2) {
            String option = fields[i];
            if (option == null || i + 1 >= fields.length) throw new IllegalArgumentException("Missing value for " + option);
            if (!seen.add(option)) throw new IllegalArgumentException("Duplicate option: " + option);
            String value = token(fields[i + 1]);
            switch (option) {
                case "--invincible" -> invincible = switch (value) {
                    case "ON" -> true;
                    case "OFF" -> false;
                    default -> throw new IllegalArgumentException("Invincible must be ON or OFF.");
                };
                case "--starting-lives" -> lives = Integer.parseInt(value);
                case "--enemy-hp-multiplier" -> hp = multiplier(value);
                case "--enemy-speed-multiplier" -> speed = multiplier(value);
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        return new StoryDebugSelection(route, stage, Difficulty.EASY, ShipType.STANDARD,
                new StoryDebugModifiers(invincible, lives, hp, speed));
    }

    private static double multiplier(String value) {
        if (!value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) {
            throw new IllegalArgumentException("Expected a decimal multiplier between 0.25 and 4.0.");
        }
        return Double.parseDouble(value);
    }

    List<String> arguments() {
        return List.of(route.name(), Integer.toString(stage), difficulty.name(), ship.name(),
                "--invincible", modifiers.invincible() ? "ON" : "OFF",
                "--starting-lives", Integer.toString(modifiers.startingLives()),
                "--enemy-hp-multiplier", Double.toString(modifiers.enemyHpMultiplier()),
                "--enemy-speed-multiplier", Double.toString(modifiers.enemySpeedMultiplier()));
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
                + "\nInvincible: " + (modifiers.invincible() ? "ON" : "OFF") + "   Starting lives: " + modifiers.startingLives()
                + "\nEnemy HP: " + modifiers.enemyHpMultiplier() + "x   Enemy speed: " + modifiers.enemySpeedMultiplier() + "x"
                + "\nWaves: " + data.getWaveCount() + "   Boss: "
                + (StoryProgress.isBossStage(stage) ? progress.bossForStage(stage) : "none")
                + "\nStory loadout: " + weapon + "   Starting grenades: "
                + (progress.givesGrenadesOnStage(stage) ? "granted" : "none")
                + "\nCurrent starting equipment: NORMAL bullets and zero grenades.";
    }
}
