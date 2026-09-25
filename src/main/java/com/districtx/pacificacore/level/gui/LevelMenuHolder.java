package com.districtx.pacificacore.level.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class LevelMenuHolder implements InventoryHolder {
    private final String menu;
    private final int page;
    private Inventory inventory;

    LevelMenuHolder(String menu, int page) {
        this.menu = menu;
        this.page = page;
    }

    String getMenu() { return menu; }
    int getPage() { return page; }
    void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}