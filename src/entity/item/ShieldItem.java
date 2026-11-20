package entity.item;

import engine.Core;
import engine.DrawManager.SpriteType;
import entity.Ship;
import audio.AudioManager;

/**
 * Item qui octroie un bouclier d'energie sans limite de temps.
 * Il protege le vaisseau contre un seul coup, puis il se brise.
 */
public class ShieldItem extends Item {

    private static final int ITEM_WIDTH = 13;
    private static final int ITEM_HEIGHT = 8;

    public ShieldItem(final int positionX, final int positionY) {
        // On utilise temporairement un sprite existant pour l'affichage
        super(positionX, positionY, ITEM_WIDTH, ITEM_HEIGHT, SpriteType.EnemyShipSpecial);
    }

    @Override
    public void applyEffect(final Ship ship) {
        // Ship.setShieldActive also refuses, but checking here keeps the
        // log line below from claiming a shield that never came up.
        if (ship != null && ship.getShipType().isShieldCapable()) {
            ship.setShieldActive(true);
            AudioManager.playSFX("audio/sfx/status_shield_on.wav");
            Core.getLogger().info("Energy Shield activated until it takes a hit.");
        }
    }

    @Override
    public void removeEffect(final Ship ship) {
        if (ship != null) {
            ship.setShieldActive(false);
            Core.getLogger().info("Energy Shield removed.");
        }
    }

    @Override
    public long getDuration() {
        // No timer: the shield stays up until a hit breaks it.
        return 0;
    }

    @Override
    public String getName() {
        return "Energy Shield";
    }
}
