package com.districtx.pacificacore.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

final class SpawnShopGuiHolder implements InventoryHolder {
    enum Type { MAIN, SHOP, CATEGORIES, CATEGORY_SHOP, QUANTITY }

    private final Type type;
    private final String shopId;
    private final String category;
    private final UUID itemId;
    private Inventory inventory;

    SpawnShopGuiHolder(Type type, String shopId, String category, UUID itemId) {
        this.type = type;
        this.shopId = shopId;
        this.category = category;
        this.itemId = itemId;
    }

    Type type() { return type; }
    String shopId() { return shopId; }
    String category() { return category; }
    UUID itemId() { return itemId; }
    void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}