package screen;

import audio.AudioManager;
import engine.CampaignUpgrades;
import engine.GameSettings;
import engine.GameState;
import entity.Ship;
import entity.ShipType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import records.RunSession;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameScreenTargetSelectionTest {

    private boolean wasMuted;

    @BeforeEach
    void setUp() {
        this.wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
    }

    @AfterEach
    void tearDown() {
        AudioManager.stopBGM();
        AudioManager.setMuted(this.wasMuted);
    }

    @Test
    void choosesBothLivingPlayersWithEqualOdds() throws Exception {
        GameScreen game = twoPlayerGame();
        Ship firstPlayer = game.getPlayerShip(0);
        Ship secondPlayer = game.getPlayerShip(1);
        Field randomField = GameScreen.class.getDeclaredField("playerTargetRandom");
        randomField.setAccessible(true);
        ((Random) randomField.get(game)).setSeed(42L);

        int firstSelections = 0;
        int secondSelections = 0;
        for (int attempt = 0; attempt < 1000; attempt++) {
            Ship target = chooseTarget(game);
            if (target == firstPlayer)
                firstSelections++;
            else if (target == secondPlayer)
                secondSelections++;
        }

        assertTrue(firstSelections > 400 && firstSelections < 600);
        assertTrue(secondSelections > 400 && secondSelections < 600);
    }

    @Test
    void targetsPlayerTwoWhenPlayerOneIsEliminated() throws Exception {
        GameScreen game = twoPlayerGame();
        assertTrue(game.getPlayerStatus(0).takeDamage(1));

        assertSame(game.getPlayerShip(1), chooseTarget(game));
    }

    private static GameScreen twoPlayerGame() {
        GameScreen game = new GameScreen(new GameState(3, 0, 3, 0, 0),
                new GameSettings(3, 5, 1, 100000), false, 448, 520, 60,
                ShipType.STANDARD, new RunSession("target-selection"),
                CampaignUpgrades.none(), 2);
        game.initialize();
        return game;
    }

    private static Ship chooseTarget(final GameScreen game) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(
                "chooseRandomLivingTargetShip");
        method.setAccessible(true);
        return (Ship) method.invoke(game);
    }
}
