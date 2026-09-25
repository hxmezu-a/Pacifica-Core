package com.districtx.pacificacore.level.gui;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.PrestigeService;
import com.districtx.pacificacore.api.PlayerLevelService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Independent, expandable Prestige GUI controller. */
public final class PrestigeMenu implements Listener {
    private final PacificaCore plugin;
    private final PlayerLevelService levels;
    private final PrestigeService prestige;

    public PrestigeMenu(PacificaCore plugin, PlayerLevelService levels, PrestigeService prestige) {
        this.plugin = plugin;
        this.levels = levels;
        this.prestige = prestige;
    }

    public void open(Player player) {
        if (player == null || !prestige.isUnlocked(player.getUniqueId())) return;
        int rows = Math.max(1, Math.min(6, plugin.getLevelConfig().getInt("gui.prestige.rows", 6)));
        LevelMenuHolder holder = new LevelMenuHolder("PRESTIGE", 0);
        String title = color(plugin.getLevelConfig().getString("gui.prestige.title", "&6&lPrestige"));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inventory);
        fill(inventory, "gui.items.empty");
        int infoSlot = slot("gui.prestige.info-slot", 22, inventory.getSize());
        set(inventory, infoSlot,
                plugin.getLevelConfig().getString("gui.prestige.info.material", "NETHER_STAR"),
                plugin.getLevelConfig().getString("gui.prestige.info.name", "&e&lPrestige %prestige%"),
                plugin.getLevelConfig().getStringList("gui.prestige.info.lore"),
                Map.of("prestige", String.valueOf(prestige.getPrestige(player.getUniqueId())),
                        "level", String.valueOf(levels.getLevel(player.getUniqueId()))));
        int backSlot = slot("gui.prestige.back.slot", 47, inventory.getSize());
        set(inventory, backSlot, plugin.getLevelConfig().getString("gui.prestige.back.material", "REDSTONE"),
                plugin.getLevelConfig().getString("gui.prestige.back.name", "&c&lBack"),
                plugin.getLevelConfig().getStringList("gui.prestige.back.lore"), Map.of());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof LevelMenuHolder holder)
                || !holder.getMenu().equals("PRESTIGE")) return;
        event.setCancelled(true);
        if (event.getRawSlot() == slot("gui.prestige.back.slot", 47, holder.getInventory().getSize())) {
            plugin.getAPI().getLevelMenuService().openMainMenu(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LevelMenuHolder holder
                && holder.getMenu().equals("PRESTIGE")) event.setCancelled(true);
    }

    private void fill(Inventory inventory, String path) {
        ItemStack item = item(plugin.getLevelConfig().getString(path + ".material", "GRAY_STAINED_GLASS_PANE"),
                plugin.getLevelConfig().getString(path + ".name", "&7"), List.of(), Map.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, item.clone());
    }

    private void set(Inventory inventory, int slot, String material, String name, List<String> lore,
                     Map<String, String> values) {
        if (slot >= 0) inventory.setItem(slot, item(material, name, lore, values));
    }

    private ItemStack item(String materialName, String name, List<String> lore, Map<String, String> values) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null) material = Material.matchMaterial("STONE");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(apply(name, values)));
        meta.setLore(lore.stream().map(line -> color(apply(line, values))).toList());
        item.setItemMeta(meta);
        return item;
    }

    private int slot(String path, int fallback, int size) {
        int slot = plugin.getLevelConfig().getInt(path, fallback);
        return slot >= 0 && slot < size ? slot : -1;
    }

    private String apply(String text, Map<String, String> values) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}