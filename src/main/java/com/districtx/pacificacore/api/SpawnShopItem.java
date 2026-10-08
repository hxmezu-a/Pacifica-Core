package com.districtx.pacificacore.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record SpawnShopItem(UUID id, String shopId, String category, ItemStack itemStack, double unitPrice, int slot) {
    public SpawnShopItem {
        itemStack = itemStack.clone();
    }

    @Override
    public ItemStack itemStack() {
        return itemStack.clone();
    }
}