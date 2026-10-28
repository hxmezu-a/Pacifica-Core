package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.math.BigDecimal;

@FunctionalInterface
public interface SpawnShopPriceModifier {
    BigDecimal modifyPrice(Player player, SpawnShopItem item, int quantity, BigDecimal baseTotal);
}