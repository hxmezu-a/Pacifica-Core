package com.districtx.pacificacore.market;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemMatcher {
    private final boolean ignoreDamage;

    public ItemMatcher(JavaPlugin plugin) {
        this.ignoreDamage = plugin.getConfig().getBoolean("black-market.item-matching.ignore-damage", true);
    }

    public boolean matches(ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null || expected.getType() != actual.getType()) return false;
        ItemStack normalizedExpected = normalize(expected);
        ItemStack normalizedActual = normalize(actual);
        return normalizedExpected.isSimilar(normalizedActual);
    }

    public boolean matchesIgnoringAmount(ItemStack expected, ItemStack actual) {
        return matches(expected, actual);
    }

    public int countMatchingItems(Player player, ItemStack target) {
        return player == null ? 0 : countMatchingItems(player.getInventory(), target);
    }

    public int countMatchingItems(Inventory inventory, ItemStack target) {
        if (inventory == null || target == null) return 0;
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (matches(target, item)) total += item.getAmount();
        }
        return total;
    }

    public ItemStack firstMatchingItem(Inventory inventory, ItemStack target) {
        if (inventory == null || target == null) return null;
        for (ItemStack item : inventory.getStorageContents()) {
            if (matches(target, item)) return item.clone();
        }
        return null;
    }

    public int removeMatchingItems(Player player, ItemStack target, int amount) {
        return player == null ? 0 : removeMatchingItems(player.getInventory(), target, amount);
    }

    public int removeMatchingItems(Inventory inventory, ItemStack target, int amount) {
        if (inventory == null || target == null || amount <= 0) return 0;
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!matches(target, item)) continue;
            int removed = Math.min(remaining, item.getAmount());
            if (removed == item.getAmount()) contents[slot] = null;
            else item.setAmount(item.getAmount() - removed);
            remaining -= removed;
        }
        inventory.setStorageContents(contents);
        return amount - remaining;
    }

    private ItemStack normalize(ItemStack item) {
        ItemStack normalized = item.clone();
        ItemMeta meta = normalized.getItemMeta();
        if (meta == null) return normalized;
        meta.setDisplayName(null);
        meta.setLore(null);
        if (ignoreDamage && meta instanceof Damageable damageable) damageable.setDamage(0);
        normalized.setItemMeta(meta);
        return normalized;
    }
}