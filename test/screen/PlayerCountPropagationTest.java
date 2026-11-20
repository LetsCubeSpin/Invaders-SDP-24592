package screen;

import engine.GameSettings;
import engine.GameState;
import entity.ShipType;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerCountPropagationTest {

    @Test
    void campaignScreenRetainsSelectedPlayerCount() {
        GameState state = new GameState(1, 0, 3, 0, 0);
        GameSettings settings = new GameSettings(1, 1, 60, 2000);

        GameScreen onePlayer = new GameScreen(state,
                Collections.singletonList(settings), false, 448, 520, 60,
                ShipType.STANDARD, null);
        GameScreen twoPlayers = new GameScreen(state,
                Collections.singletonList(settings), false, 448, 520, 60,
                ShipType.STANDARD, null, 2);

        assertEquals(1, onePlayer.getPlayerCount());
        assertEquals(2, twoPlayers.getPlayerCount());
        assertThrows(IllegalArgumentException.class, () -> new GameScreen(state,
                Collections.singletonList(settings), false, 448, 520, 60,
                ShipType.STANDARD, null, 3));
    }

    @Test
    void infiniteScreenRetainsSelectedPlayerCount() {
        GameState state = new GameState(1, 0, 3, 0, 0);

        InfiniteGameScreen onePlayer =
                new InfiniteGameScreen(state, 448, 520, 60);
        InfiniteGameScreen twoPlayers =
                new InfiniteGameScreen(state, 448, 520, 60, null, 2);

        assertEquals(1, onePlayer.getPlayerCount());
        assertEquals(2, twoPlayers.getPlayerCount());
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteGameScreen(state, 448, 520, 60, null, 3));
    }
}
