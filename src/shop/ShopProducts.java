package shop;

import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import currency.ShopCatalog.Category;
import entity.ShipType;
import engine.LanguageManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopProducts {
    private ShopProducts() { }

    public static List<ShopProduct> availableProducts() {
        List<ShopProduct> products = new ArrayList<ShopProduct>();
        for (PermanentUpgrade upgrade : PermanentUpgrade.values()) {
            for (int level = 1; level <= upgrade.getMaxLevel(); level++) {
                int bonus = upgrade.getBonus(level);
                String key = descriptionKey(upgrade);
                products.add(new ShopProduct(upgrade.getProductId(level),
                        upgrade.getName() + " " + level + "/" + upgrade.getMaxLevel(),
                        Category.UPGRADE, upgrade.getPrice(level),
                        key, new Object[] { bonus }, upgrade.getRequiredUnlockId(level)));
            }
        }
        for (ShipType ship : ShipType.values()) {
            boolean available = ship != ShipType.SPEEDSTER;
            String key = available ? "shop.ship.selectHangar" : "shop.ship.unlockHangar";
            products.add(ShopProduct.shipWithKey(ship, available, available ? 0 : 200, key));
        }
        return Collections.unmodifiableList(products);
    }

    /** Keep one card per upgrade, advancing it only after a completed purchase. */
    static List<ShopProduct> visibleProducts(List<ShopProduct> products, PlayerAssetsState assets) {
        List<ShopProduct> visible = new ArrayList<ShopProduct>();
        for (ShopProduct product : products) {
            PermanentUpgrade upgrade = PermanentUpgrade.forProduct(product.getId());
            if (upgrade == null) {
                visible.add(product);
                continue;
            }
            int owned = upgrade.getLevel(assets);
            int next = Math.min(owned + 1, upgrade.getMaxLevel());
            if (!product.getId().equals(upgrade.getProductId(next))) continue;
            if (owned == upgrade.getMaxLevel()) {
                String name = upgrade.getName() + " " + LanguageManager.get("upgrade.max");
                visible.add(new ShopProduct(product.getId(), name,
                        product.getCategory(), product.getPrice(),
                        product.getDescription(), product.getRequiredUnlockId()));
            } else {
                visible.add(product);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    private static String descriptionKey(PermanentUpgrade upgrade) {
        switch (upgrade) {
        case HULL: return "upgrade.hull.description";
        case ENGINE: return "upgrade.engine.description";
        case RELOADER: return "upgrade.reloader.description";
        default: throw new IllegalArgumentException("Unknown upgrade.");
        }
    }
}
