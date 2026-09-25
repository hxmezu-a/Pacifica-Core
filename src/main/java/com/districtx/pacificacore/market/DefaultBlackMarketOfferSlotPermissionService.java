package com.districtx.pacificacore.market;

import org.bukkit.entity.Player;

public final class DefaultBlackMarketOfferSlotPermissionService implements BlackMarketOfferSlotPermissionService {
    private final BlackMarketOfferSlotRegistry slotRegistry;

    public DefaultBlackMarketOfferSlotPermissionService(BlackMarketOfferSlotRegistry slotRegistry) {
        this.slotRegistry = slotRegistry;
    }

    @Override
    public int getHighestUnlockedSequentialSlot(Player player) {
        if (hasAllSlotsPermission(player)) return slotRegistry.getMaximumLogicalSlot();

        int highestUnlocked = 0;
        for (int logicalSlot = 1; logicalSlot <= slotRegistry.getMaximumLogicalSlot(); logicalSlot++) {
            if (!player.hasPermission("bm.myoffer.slot." + logicalSlot)) break;
            highestUnlocked = logicalSlot;
        }
        return highestUnlocked;
    }

    @Override
    public boolean isSlotUnlocked(Player player, int logicalSlot) {
        if (logicalSlot < 1 || logicalSlot > slotRegistry.getMaximumLogicalSlot()) return false;
        return hasAllSlotsPermission(player) || logicalSlot <= getHighestUnlockedSequentialSlot(player);
    }

    @Override
    public int getRequiredCheatCodeLevel(Player player) {
        return getHighestUnlockedSequentialSlot(player) + 1;
    }

    @Override
    public boolean hasAllSlotsPermission(Player player) {
        return player.hasPermission("bm.myoffer.slot.all");
    }
}