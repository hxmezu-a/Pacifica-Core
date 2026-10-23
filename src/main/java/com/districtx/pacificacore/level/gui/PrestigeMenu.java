package com.districtx.pacificacore.level.gui;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.PrestigeService;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.level.LevelDisplayFormatter;
import com.cryptomorin.xseries.XMaterial;
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
    private static final int[] LEVEL_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24,
            29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private final PacificaCore plugin;
    private final PlayerLevelService levels;
    private final PrestigeService prestige;

    public PrestigeMenu(PacificaCore plugin, PlayerLevelService levels, PrestigeService prestige) {
        this.plugin = plugin;
        this.levels = levels;
        this.prestige = prestige;
    }

    public void open(Player player) {
        open(player, 1);
    }

    public void open(Player player, int page) {
        if (player == null || !prestige.isUnlocked(player.getUniqueId())) return;
        int rows = Math.max(1, Math.min(6, plugin.getLevelConfig().getInt("gui.prestige.rows", 6)));
        int pages = maximumPage();
        int safePage = Math.max(1, Math.min(pages, page));
        LevelMenuHolder holder = new LevelMenuHolder("PRESTIGE", safePage);
        String title = plugin.getLevelConfig().getString("gui.prestige.title", "&6&lPrestige &7- &e%page%/%pages%")
                .replace("%page%", String.valueOf(safePage)).replace("%pages%", String.valueOf(pages));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, color(title));
        holder.setInventory(inventory);
        fill(inventory, "gui.items.empty");
        fillSlots(inventory, "gui.progression.border.white", new int[]{0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53});
        fillSlots(inventory, "gui.progression.border.black", new int[]{1, 2, 3, 4, 5, 6, 7, 10, 19, 28, 37, 46, 16, 25, 34, 43, 52});
        for (int index = 0; index < LEVEL_SLOTS.length; index++) {
            int milestone = 105 + ((safePage - 1) * LEVEL_SLOTS.length + index) * 5;
            if (milestone <= maximumMilestone()) setMilestone(inventory, player, LEVEL_SLOTS[index], milestone);
        }
        int currentLevel = levels.getLevel(player.getUniqueId());
        int infoSlot = slot("gui.prestige.info-slot", 4, inventory.getSize());
        set(inventory, infoSlot,
                plugin.getLevelConfig().getString("gui.prestige.info.material", "NETHER_STAR"),
                plugin.getLevelConfig().getString("gui.prestige.info.name", "&e&lPrestige %prestige%"),
                plugin.getLevelConfig().getStringList("gui.prestige.info.lore"),
                Map.of("prestige", String.valueOf(prestige.getPrestige(player.getUniqueId())),
                        "level", LevelDisplayFormatter.format(plugin, currentLevel),
                        "page", String.valueOf(safePage), "pages", String.valueOf(pages)));
        int backSlot = slot("gui.prestige.back.slot", 47, inventory.getSize());
        set(inventory, backSlot, plugin.getLevelConfig().getString("gui.prestige.back.material", "REDSTONE"),
                plugin.getLevelConfig().getString("gui.prestige.back.name", "&c&lBack"),
                plugin.getLevelConfig().getStringList("gui.prestige.back.lore"), Map.of());
        int previousSlot = slot("gui.prestige.navigation.previous-slot", 48, inventory.getSize());
        int nextSlot = slot("gui.prestige.navigation.next-slot", 51, inventory.getSize());
        if (safePage > 1) set(inventory, previousSlot,
                plugin.getLevelConfig().getString("gui.prestige.navigation.material", "ARROW"),
                plugin.getLevelConfig().getString("gui.prestige.navigation.previous-name", "&e&lPrevious Page"),
                List.of("&7Page " + (safePage - 1)), Map.of());
        if (safePage < pages) set(inventory, nextSlot,
                plugin.getLevelConfig().getString("gui.prestige.navigation.material", "ARROW"),
                plugin.getLevelConfig().getString("gui.prestige.navigation.next-name", "&e&lNext Page"),
                List.of("&7Page " + (safePage + 1)), Map.of());
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
        } else if (event.getRawSlot() == slot("gui.prestige.navigation.previous-slot", 48,
                holder.getInventory().getSize()) && holder.getPage() > 1) {
            open(player, holder.getPage() - 1);
        } else if (event.getRawSlot() == slot("gui.prestige.navigation.next-slot", 51,
                holder.getInventory().getSize()) && holder.getPage() < maximumPage()) {
            open(player, holder.getPage() + 1);
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

    private void fillSlots(Inventory inventory, String path, int[] slots) {
        for (int slot : slots) set(inventory, slot,
                plugin.getLevelConfig().getString(path + ".material", "GRAY_STAINED_GLASS_PANE"),
                plugin.getLevelConfig().getString(path + ".name", "&7"), List.of(), Map.of());
    }

    private void setMilestone(Inventory inventory, Player player, int slot, int milestone) {
        int currentLevel = levels.getLevel(player.getUniqueId());
        int currentMilestone = currentMilestone(currentLevel);
        String state = currentMilestone == 0 || milestone > currentMilestone ? "upcoming"
                : milestone == currentMilestone ? "current" : "previous";
        int displayLevel = state.equals("current") ? currentLevel : milestone;
        var progression = levels.getProgression();
        double totalExperience = levels.getExperience(player.getUniqueId());
        int xpTargetLevel = state.equals("current") ? currentLevel : milestone;
        String rankUpStatus = state.equals("previous") ? "Complete"
                : state.equals("current")
                ? format(levels.getCurrentLevelExperience(player.getUniqueId()).doubleValue()) + " / "
                + format(progression.getExperienceRequiredForNextLevel(currentLevel))
                : "0.0 / " + format(progression.getExperienceRequiredForNextLevel(milestone));
        Map<String, String> values = Map.of(
                "milestone", String.valueOf(milestone),
                "level", LevelDisplayFormatter.format(plugin, displayLevel),
                "total_xp_needed", format(progression.getTotalExperienceRequiredForLevel(xpTargetLevel)),
                "rankup_xp_status", rankUpStatus,
                "xp_progression", state.equals("current")
                        ? format(levels.getCurrentLevelExperience(player.getUniqueId()).doubleValue()) : "0.0",
                "level_xp_needed", format(progression.getExperienceRequiredForNextLevel(xpTargetLevel)),
                "player_level", LevelDisplayFormatter.format(plugin, currentLevel));
        String name = plugin.getLevelConfig().getString("gui.prestige.progression."
                + (state.equals("current") ? "current-name" : "level-name"),
                state.equals("current") ? "&8[&4&lLevel: %level%&8]" : "&8[&4&lLevel: %milestone%&8]");
        List<String> lore = plugin.getLevelConfig().getStringList("gui.prestige.progression." + state + "-lore")
                .stream().map(line -> apply(line, values)).toList();
        String material = plugin.getLevelConfig().getString("gui.prestige.progression.states." + state + ".material",
                state.equals("previous") ? "NETHER_STAR" : state.equals("current") ? "WRITABLE_BOOK" : "END_CRYSTAL");
        set(inventory, slot, material, name, lore, values);
    }

    private int currentMilestone(int level) {
        if (level < 101 || maximumMilestone() < 105) return 0;
        int milestone = level < 105 ? 105 : 105 + ((level - 105) / 5) * 5;
        return Math.min(maximumMilestone(), milestone);
    }

    private int maximumMilestone() {
        int maximumLevel = levels.getMaximumLevel();
        return maximumLevel < 105 ? 0 : maximumLevel - (maximumLevel % 5);
    }

    private int maximumPage() {
        int maximumMilestone = maximumMilestone();
        int milestoneCount = maximumMilestone < 105 ? 0 : (maximumMilestone - 105) / 5 + 1;
        return Math.max(1, (milestoneCount + LEVEL_SLOTS.length - 1) / LEVEL_SLOTS.length);
    }

    private String format(double amount) {
        return com.districtx.pacificacore.level.ExperienceFormatter.formatXp(amount);
    }

    private void set(Inventory inventory, int slot, String material, String name, List<String> lore,
                     Map<String, String> values) {
        if (slot >= 0) inventory.setItem(slot, item(material, name, lore, values));
    }

    private ItemStack item(String materialName, String name, List<String> lore, Map<String, String> values) {
        Material material = XMaterial.matchXMaterial(materialName == null ? "" : materialName)
                .map(XMaterial::parseMaterial)
                .orElseGet(() -> XMaterial.matchXMaterial("STONE").map(XMaterial::parseMaterial).orElseThrow());
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