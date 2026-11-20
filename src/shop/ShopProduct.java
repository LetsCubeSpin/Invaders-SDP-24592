package shop;

import currency.ShopCatalog.Category;
import entity.ShipType;
import engine.LanguageManager;
import java.util.Locale;

/** Immutable product details. Purchase prices are registered in ShopCatalog. */
public final class ShopProduct {
    private final String id;
    private final String name;
    private final Category category;
    private final int price;
    private final String description;
    private final String descriptionKey;
    private final Object[] descriptionArgs;
    private final String requiredUnlockId;
    private final ShipType shipType;
    private final boolean availableByDefault;

    public ShopProduct(String id, String name, Category category, int price) {
        this(id, name, category, price,
                category == Category.UPGRADE ? "Permanent stat upgrade" : "Permanent unlock");
    }

    public ShopProduct(String id, String name, Category category, int price,
            String description) {
        this(id, name, category, price, description, null);
    }

    public ShopProduct(String id, String name, Category category, int price,
            String description, String requiredUnlockId) {
        this(id, name, category, price, description, requiredUnlockId, null, false);
    }

    private ShopProduct(String id, String name, Category category, int price,
            String description, String requiredUnlockId, ShipType shipType,
            boolean availableByDefault) {
        this(id, name, category, price, description, null, (Object[])null,
                requiredUnlockId, shipType, availableByDefault);
    }

    /** Constructor with a translation key for dynamic description resolution. */
    ShopProduct(String id, String name, Category category, int price,
            String descriptionKey, Object[] descriptionArgs, String requiredUnlockId) {
        this(id, name, category, price, null, descriptionKey, descriptionArgs,
                requiredUnlockId, null, false);
    }

    /** Constructor with a translation key, ship type, and availability for ship products. */
    ShopProduct(String id, String name, Category category, int price,
            String descriptionKey, Object[] descriptionArgs, String requiredUnlockId,
            ShipType shipType, boolean availableByDefault) {
        this(id, name, category, price, null, descriptionKey, descriptionArgs,
                requiredUnlockId, shipType, availableByDefault);
    }

    private ShopProduct(String id, String name, Category category, int price,
            String description, String descriptionKey, Object[] descriptionArgs,
            String requiredUnlockId, ShipType shipType, boolean availableByDefault) {
        if (requiredUnlockId != null && (requiredUnlockId.trim().isEmpty()
                || requiredUnlockId.equals(id))) {
            throw new IllegalArgumentException("Prerequisite must be a different, nonblank unlock ID.");
        }
        boolean hasDescription = description != null && !description.trim().isEmpty();
        boolean hasDescriptionKey = descriptionKey != null && !descriptionKey.trim().isEmpty();
        if (id == null || id.trim().isEmpty() || name == null
                || name.trim().isEmpty() || category == null
                || (availableByDefault ? price != 0 : price <= 0)
                || (!hasDescription && !hasDescriptionKey)) {
            throw new IllegalArgumentException("Product requires an ID, name, category, price and description.");
        }
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.descriptionKey = descriptionKey;
        this.descriptionArgs = descriptionArgs;
        this.requiredUnlockId = requiredUnlockId;
        this.shipType = shipType;
        this.availableByDefault = availableByDefault;
    }

    /** Default availability does not grant or persist permanent ownership. */
    public static ShopProduct ship(ShipType type, boolean availableByDefault, int price) {
        if (type == null) throw new IllegalArgumentException("Ship type is required.");
        String key = availableByDefault ? "shop.ship.selectHangar" : "shop.ship.unlockHangar";
        return new ShopProduct("ship." + type.name().toLowerCase(Locale.ROOT),
                type.getName(), Category.UNLOCKABLE, price,
                key, (Object[])null, null, type, availableByDefault);
    }

    /** Overload that accepts a custom description for the ship product. */
    public static ShopProduct ship(ShipType type, boolean availableByDefault, int price, String description) {
        if (type == null) throw new IllegalArgumentException("Ship type is required.");
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
        return new ShopProduct("ship." + type.name().toLowerCase(Locale.ROOT),
                type.getName(), Category.UNLOCKABLE, price,
                description, null, type, availableByDefault);
    }

    /** Factory for ship products with a translation key for the description. */
    static ShopProduct shipWithKey(ShipType type, boolean availableByDefault, int price,
            String descriptionKey) {
        if (type == null) throw new IllegalArgumentException("Ship type is required.");
        if (descriptionKey == null || descriptionKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Description key is required.");
        }
        return new ShopProduct("ship." + type.name().toLowerCase(Locale.ROOT),
                type.getName(), Category.UNLOCKABLE, price,
                descriptionKey, (Object[])null, null, type, availableByDefault);
    }

    public ShipType getShipType() {
        return shipType;
    }

    public boolean isAvailableByDefault() {
        return availableByDefault;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public int getPrice() {
        return price;
    }

    public String getDescription() {
        if (this.descriptionKey != null) {
            if (this.descriptionArgs != null && this.descriptionArgs.length > 0) {
                return LanguageManager.get(this.descriptionKey, this.descriptionArgs);
            }
            return LanguageManager.get(this.descriptionKey);
        }
        return description;
    }

    public String getRequiredUnlockId() {
        return requiredUnlockId;
    }
}
