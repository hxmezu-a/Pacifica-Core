package com.districtx.pacificacore.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlackMarketCategoryDetector {
    private final org.bukkit.NamespacedKey categoryKey;

    public BlackMarketCategoryDetector(JavaPlugin plugin) {
        this.categoryKey = new org.bukkit.NamespacedKey(plugin, "blackmarket_category");
    }

    public BlackMarketCategory detect(ItemStack item) {
        if (item == null) return BlackMarketCategory.MISC;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String value = meta.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
            if (value != null) {
                try {
                    return BlackMarketCategory.valueOf(value);
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the configured automatic detection rules.
                }
            }
        }
        Material material = item.getType();
        if (material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD) {
            return BlackMarketCategory.PLAYER_HEADS;
        }
        if (material.name().endsWith("_SWORD") || material.name().endsWith("_AXE")
                || material.name().endsWith("_BOW") || material.name().endsWith("_CROSSBOW")
                || material == Material.TRIDENT) return BlackMarketCategory.WEAPONS;
        if (material.name().endsWith("_HELMET") || material.name().endsWith("_CHESTPLATE")
                || material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS")) {
            return BlackMarketCategory.ARMOR;
        }
        return BlackMarketCategory.MISC;
    }
}