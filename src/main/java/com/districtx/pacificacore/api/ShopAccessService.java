package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

public interface ShopAccessService {
    boolean canAccess(Player player, String shopId);
    int getRequiredLevel(String shopId);
}