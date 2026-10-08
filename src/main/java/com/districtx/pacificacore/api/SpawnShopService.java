package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SpawnShopService {
    Optional<SpawnShop> getShop(String shopId);
    Collection<SpawnShop> getShops();
    boolean canAccess(Player player, String shopId);
    void openShop(Player player, String shopId);
    boolean purchase(Player player, UUID shopItemId, int quantity);
    SpawnShopPurchaseResult purchaseDetailed(Player player, UUID shopItemId, int quantity);
    Optional<SpawnShopItem> addItem(String shopId, String category, ItemStack item, double price);
    boolean removeItem(UUID shopItemId);
    Collection<SpawnShopItem> getItems(String shopId, String category);
}