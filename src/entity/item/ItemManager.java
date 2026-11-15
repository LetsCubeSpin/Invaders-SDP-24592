package entity.item;

import engine.DrawManager;
import entity.Ship;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import audio.AudioManager;

public class ItemManager {

    private final List<Item> activeItems;
    private final BuffManager buffManager;
    private final Map<Ship, BuffManager> playerBuffManagers;
    private Ship primaryShip;
    private final int screenHeight;

    public ItemManager(final int screenHeight) {
        this.activeItems = new ArrayList<>();
        this.buffManager = new BuffManager();
        this.playerBuffManagers = new IdentityHashMap<>();
        this.screenHeight = screenHeight;
    }

    public void spawnItem(final Item item) {
        this.activeItems.add(item);
    }

    public void update(final Ship ship) {
        updateItems();
        updateBuffs(ship);
    }

    public void updateItems() {
        Iterator<Item> iterator = this.activeItems.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            item.update();

            if (item.getPositionY() > this.screenHeight) {
                iterator.remove();
            }
        }
    }

    public void updateBuffs(final Ship ship) {
        getBuffManager(ship).update(ship);
    }

    public void checkCollisions(final Ship ship) {
        checkCollisions(ship, getBuffManager(ship));
    }

    public void checkCollisions(final Ship ship, final BuffManager buffs) {
        Iterator<Item> iterator = this.activeItems.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (checkCollision(item, ship)) {
                item.setCollected(true);
                AudioManager.playSFX("audio/sfx/item_pickup.wav");
                buffs.addBuff(item, ship);
                iterator.remove();
            }
        }
    }

    private boolean checkCollision(final Item item, final Ship ship) {
        return item.getPositionX() < ship.getPositionX() + ship.getWidth()
                && item.getPositionX() + item.getWidth() > ship.getPositionX()
                && item.getPositionY() < ship.getPositionY() + ship.getHeight()
                && item.getPositionY() + item.getHeight() > ship.getPositionY();
    }

    public void draw(final DrawManager drawManager) {
        for (Item item : this.activeItems) {
            if (item instanceof ClusterGrenadeItem) {
                drawManager.drawGrenadeItem(item.getPositionX(), item.getPositionY());
            } else {
                drawManager.drawEntity(item, item.getPositionX(), item.getPositionY());
            }
        }
    }

    public BuffManager getBuffManager() {
        return this.buffManager;
    }

    public BuffManager getBuffManager(final Ship ship) {
        BuffManager buffs = this.playerBuffManagers.get(ship);
        if (buffs != null)
            return buffs;

        if (this.primaryShip == null) {
            this.primaryShip = ship;
            buffs = this.buffManager;
        } else {
            buffs = new BuffManager();
        }
        this.playerBuffManagers.put(ship, buffs);
        return buffs;
    }

    public void releaseShip(final Ship ship) {
        BuffManager buffs = this.playerBuffManagers.remove(ship);
        if (buffs != null)
            buffs.resetAllBuffs(ship);
        if (this.primaryShip == ship)
            this.primaryShip = null;
    }
}
