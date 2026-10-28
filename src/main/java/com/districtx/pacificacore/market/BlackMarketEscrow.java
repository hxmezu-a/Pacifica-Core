package com.districtx.pacificacore.market;

import org.bukkit.inventory.ItemStack;

public final class BlackMarketEscrow {
    private final ItemStack item;
    private final int quantity;

    public BlackMarketEscrow(ItemStack item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public ItemStack getItem() { return item; }
    public int getQuantity() { return quantity; }
}