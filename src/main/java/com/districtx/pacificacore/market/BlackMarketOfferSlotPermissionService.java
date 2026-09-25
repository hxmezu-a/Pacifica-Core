package com.districtx.pacificacore.market;

import org.bukkit.entity.Player;

public interface BlackMarketOfferSlotPermissionService {
    int getHighestUnlockedSequentialSlot(Player player);

    boolean isSlotUnlocked(Player player, int logicalSlot);

    int getRequiredCheatCodeLevel(Player player);

    boolean hasAllSlotsPermission(Player player);
}