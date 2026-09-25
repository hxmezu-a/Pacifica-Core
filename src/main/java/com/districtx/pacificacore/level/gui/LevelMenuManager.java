package com.districtx.pacificacore.level.gui;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.DailyExperiencePurchaseResult;
import com.districtx.pacificacore.api.DailyExperienceService;
import com.districtx.pacificacore.api.LevelMenuService;
import com.districtx.pacificacore.api.LevelProgression;
import com.districtx.pacificacore.api.LevelRewardService;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.PrestigeService;
import com.districtx.pacificacore.api.RankExperienceBonusService;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/** Builds the main Level menu and the permanent 100-level progression pages. */
public final class LevelMenuManager implements LevelMenuService, Listener {
    private static final int[] LEVEL_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24,
            29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private final PacificaCore plugin;
    private final PlayerLevelService levels;
    private final LevelRewardService rewards;
    private final DailyExperienceService dailyExperience;
    private final PrestigeService prestige;
    private final RankExperienceBonusService rankBonuses;
    private final PrestigeMenu prestigeMenu;

    public LevelMenuManager(PacificaCore plugin, PlayerLevelService levels, LevelRewardService rewards,
                            DailyExperienceService dailyExperience, PrestigeService prestige,
                            RankExperienceBonusService rankBonuses) {
        this.plugin = plugin;
        this.levels = levels;
        this.rewards = rewards;
        this.dailyExperience = dailyExperience;
        this.prestige = prestige;
        this.rankBonuses = rankBonuses;
        this.prestigeMenu = new PrestigeMenu(plugin, levels, prestige);
    }

    public PrestigeMenu getPrestigeMenu() { return prestigeMenu; }

    @Override
    public void openMainMenu(Player player) {
        if (player == null) return;
        Inventory inventory = createInventory(player, "MAIN", 0,
                plugin.getLevelConfig().getString("gui.main.title", "&6&lLeveling Menu"),
                plugin.getLevelConfig().getInt("gui.main.rows", 6));
        fill(inventory, "gui.items.empty");
        fillSlots(inventory, parseSlots("gui.main.border.white.slots", 
                new int[]{0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53}), "gui.main.border.white");
        fillSlots(inventory, parseSlots("gui.main.border.black.slots",
                new int[]{1, 2, 3, 4, 5, 6, 7, 10, 19, 28, 37, 46, 16, 25, 34, 43, 52}),
                "gui.main.border.black");
        setConfigured(inventory, "gui.items.progression", Map.of());
        int prestigeSlot = configuredSlot("gui.items.prestige.slot", 23, inventory.getSize());
        boolean unlocked = prestige.isUnlocked(player.getUniqueId());
        List<String> prestigeLore = new ArrayList<>(plugin.getLevelConfig().getStringList("gui.items.prestige.lore"));
        prestigeLore.add(apply(plugin.getLevelConfig().getString(unlocked
                ? "gui.items.prestige.unlocked-line" : "gui.items.prestige.locked-line",
                unlocked ? "&b&lClick &fto open menu!" : "&4LOCKED (&fLvl.%unlock_level%&4)"),
                Map.of("unlock_level", String.valueOf(plugin.getLevelConfig().getInt("prestige.unlock-level", 100)))));
        set(inventory, prestigeSlot, material("gui.items.prestige.material", "GOLD_NUGGET"),
                text("gui.items.prestige.name", "&e&lPrestige Leveling"), prestigeLore, Map.of());
        setDailyButton(inventory, player);
        setCurrentLevel(inventory, player);
        player.openInventory(inventory);
    }

    @Override
    public void openProgressionMenu(Player player, int page) {
        if (player == null) return;
        int safePage = Math.max(1, Math.min(5, page));
        Inventory inventory = createInventory(player, "PROGRESSION", safePage,
                plugin.getLevelConfig().getString("gui.progression.title", "&6&lLevel Progression &7- &e%page%/5")
                        .replace("%page%", String.valueOf(safePage)),
                plugin.getLevelConfig().getInt("gui.progression.rows", 6));
        fill(inventory, "gui.items.empty");
        fillSlots(inventory, parseSlots("gui.progression.border.white.slots",
                new int[]{0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53}), "gui.progression.border.white");
        fillSlots(inventory, parseSlots("gui.progression.border.black.slots",
                new int[]{1, 2, 3, 4, 5, 6, 7, 10, 19, 28, 37, 46, 16, 25, 34, 43, 52}),
                "gui.progression.border.black");
        for (int slot : LEVEL_SLOTS) {
            OptionalInt mappedLevel = getLevelForSlot(safePage, slot);
            if (mappedLevel.isPresent()) setProgressionLevel(inventory, player, slot, mappedLevel.getAsInt());
        }
        int backSlot = configuredSlot("gui.progression.back.slot", 47, inventory.getSize());
        set(inventory, backSlot, material("gui.progression.back.material", "REDSTONE"),
                text("gui.progression.back.name", "&c&lBack"),
                plugin.getLevelConfig().getStringList("gui.progression.back.lore"), Map.of());
        int previousSlot = configuredSlot("gui.progression.navigation.previous-slot", 48, inventory.getSize());
        int nextSlot = configuredSlot("gui.progression.navigation.next-slot", 51, inventory.getSize());
        setEmpty(inventory, previousSlot);
        setEmpty(inventory, 49);
        setEmpty(inventory, 50);
        setEmpty(inventory, nextSlot);
        if (safePage > 1) set(inventory, previousSlot,
                material("gui.progression.navigation.material", "ARROW"),
                text("gui.progression.navigation.previous-name", "&e&lPrevious Page"),
                replace(plugin.getLevelConfig().getStringList("gui.progression.navigation.previous-lore"),
                        Map.of("page", String.valueOf(safePage - 1))), Map.of());
        if (safePage < 5) set(inventory, nextSlot,
                material("gui.progression.navigation.material", "ARROW"),
                text("gui.progression.navigation.next-name", "&e&lNext Page"),
                replace(plugin.getLevelConfig().getStringList("gui.progression.navigation.next-lore"),
                        Map.of("page", String.valueOf(safePage + 1))), Map.of());
        player.openInventory(inventory);
    }

    @Override
    public void openPrestigeMenu(Player player) {
        if (player == null) return;
        if (!prestige.isUnlocked(player.getUniqueId())) {
            player.sendMessage(color(plugin.getLevelConfig().getString("gui.messages.prestige-locked",
                    "&cPrestige unlocks at Level 100.")));
            return;
        }
        prestigeMenu.open(player);
    }

    @Override
    public void refresh(Player player) {
        if (player == null || !(player.getOpenInventory().getTopInventory().getHolder() instanceof LevelMenuHolder holder)) return;
        if (holder.getMenu().equals("MAIN")) openMainMenu(player);
        else if (holder.getMenu().equals("PROGRESSION")) openProgressionMenu(player, holder.getPage());
    }

    @Override
    public OptionalInt getLevelForSlot(int page, int slot) {
        if (page < 1 || page > 5) return OptionalInt.empty();
        for (int index = 0; index < LEVEL_SLOTS.length; index++) {
            if (LEVEL_SLOTS[index] == slot) return OptionalInt.of((page - 1) * 20 + index + 1);
        }
        return OptionalInt.empty();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof LevelMenuHolder holder)
                || holder.getMenu().equals("PRESTIGE")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (holder.getMenu().equals("MAIN")) handleMainClick(player, slot);
        else handleProgressionClick(player, holder.getPage(), slot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LevelMenuHolder holder
                && !holder.getMenu().equals("PRESTIGE")) event.setCancelled(true);
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == configuredSlot("gui.items.progression.slot", 21, 54)) {
            openProgressionMenu(player, 1);
        } else if (slot == configuredSlot("gui.items.prestige.slot", 23, 54)) {
            if (prestige.isUnlocked(player.getUniqueId())) openPrestigeMenu(player);
            else player.sendMessage(color(plugin.getLevelConfig().getString("gui.messages.prestige-locked",
                    "&cPrestige unlocks at Level 100.")));
        } else if (slot == configuredSlot("gui.items.daily-xp.slot", 31, 54)) {
            purchaseDailyExperience(player);
        }
    }

    private void handleProgressionClick(Player player, int page, int slot) {
        if (slot == configuredSlot("gui.progression.back.slot", 47, 54)) openMainMenu(player);
        else if (slot == configuredSlot("gui.progression.navigation.previous-slot", 48, 54) && page > 1) {
            openProgressionMenu(player, page - 1);
        } else if (slot == configuredSlot("gui.progression.navigation.next-slot", 51, 54) && page < 5) {
            openProgressionMenu(player, page + 1);
        }
    }

    private void purchaseDailyExperience(Player player) {
        DailyExperiencePurchaseResult result = dailyExperience.purchase(player);
        String messageKey = result.isSuccessful() ? "success" : result.getFailureReason();
        String message = plugin.getLevelConfig().getString("messages.daily-xp." + messageKey,
                result.isSuccessful() ? "&aPurchased %amount% XP for $%price%." : "&cDaily XP purchase failed.");
        player.sendMessage(color(apply(message, Map.of("amount", format(result.getExperience()),
                "price", format(result.getPrice()), "cooldown", formatDuration(result.getCooldown()),
                "currency_symbol", plugin.getLevelConfig().getString("currency.money-symbol", "$")))));
        refresh(player);
    }

    private void setProgressionLevel(Inventory inventory, Player player, int slot, int displayedLevel) {
        UUID playerId = player.getUniqueId();
        int currentLevel = levels.getLevel(playerId);
        String state = currentLevel == displayedLevel ? "current" : currentLevel > displayedLevel ? "unlocked" : "locked";
        LevelProgression progression = levels.getProgression();
        double currentXp = levels.getExperience(playerId);
        double xpIntoLevel = currentLevel == displayedLevel
                ? progression.getExperienceIntoCurrentLevel(currentXp) : 0.0;
        double levelRequirement = progression.getExperienceRequiredForNextLevel(displayedLevel);
        Map<String, String> values = new java.util.HashMap<>();
        values.put("level", String.valueOf(displayedLevel));
        values.put("player_level", String.valueOf(currentLevel));
        values.put("level_progression", format(levels.getProgressToNextLevel(playerId) * 100.0));
        values.put("total_xp_needed", format(progression.getTotalExperienceRequiredForLevel(displayedLevel)));
        values.put("xp_progression", format(xpIntoLevel));
        values.put("level_xp_needed", format(levelRequirement));
        values.put("color_level", levelColor(displayedLevel));
        values.put("page", String.valueOf((displayedLevel - 1) / 20 + 1));
        values.put("daily_xp", format(dailyExperience.getExperienceAmount(playerId)));
        values.put("price_xp", format(dailyExperience.getPrice(playerId)));
        String name = apply(text("gui.progression.level-name", "&8[%color_level%Level %level%&8]"), values);
        List<String> lore = new ArrayList<>();
        if (state.equals("current")) lore.add(text("gui.progression.current-prefix", "&6&lCURRENT LEVEL"));
        String stateLorePath = "gui.progression." + state + "-lore";
        lore.addAll(replace(plugin.getLevelConfig().getStringList(stateLorePath), values));
        lore.addAll(rewards.getDisplayLore(displayedLevel));
        set(inventory, slot, material("gui.progression.states." + state + ".material",
                state.equals("current") ? "WRITABLE_BOOK" : state.equals("unlocked") ? "GREEN_DYE" : "RED_DYE"),
                name, lore, Map.of());
    }

    private void setDailyButton(Inventory inventory, Player player) {
        UUID playerId = player.getUniqueId();
        String state;
        if (!dailyExperience.isUnlocked(playerId)) state = "locked";
        else if (!dailyExperience.getRemainingCooldown(playerId).isZero()) state = "cooldown";
        else state = "available";
        Map<String, String> values = new java.util.HashMap<>();
        values.put("daily_xp", format(dailyExperience.getExperienceAmount(playerId)));
        values.put("price_xp", format(dailyExperience.getPrice(playerId)));
        values.put("cooldown", formatDuration(dailyExperience.getRemainingCooldown(playerId)));
        values.put("currency_symbol", plugin.getLevelConfig().getString("currency.money-symbol", "$"));
        values.put("unlock_level", String.valueOf(plugin.getLevelConfig().getInt("daily-xp.unlock-level", 10)));
        set(inventory, configuredSlot("gui.items.daily-xp.slot", 31, inventory.getSize()),
                material("gui.items.daily-xp.material", "EXPERIENCE_BOTTLE"),
                text("gui.items.daily-xp.name", "&a&lDaily XP"),
                replace(plugin.getLevelConfig().getStringList("gui.items.daily-xp.lore." + state), values), Map.of());
    }

    private void setCurrentLevel(Inventory inventory, Player player) {
        UUID playerId = player.getUniqueId();
        int level = levels.getLevel(playerId);
        Map<String, String> values = new java.util.HashMap<>();
        values.put("level", String.valueOf(level));
        values.put("player_level", String.valueOf(level));
        values.put("level_progression", format(levels.getProgressToNextLevel(playerId) * 100.0));
        values.put("total_xp_needed", format(levels.getExperienceRequiredForLevel(level + 1)));
        values.put("xp_progression", format(levels.getProgression().getExperienceIntoCurrentLevel(
                levels.getExperience(playerId))));
        values.put("level_xp_needed", format(levels.getProgression().getExperienceRequiredForNextLevel(level)));
        values.put("color_level", levelColor(level));
        values.put("daily_xp", format(dailyExperience.getExperienceAmount(playerId)));
        values.put("price_xp", format(dailyExperience.getPrice(playerId)));
        values.put("kill_bonus", format(rankBonuses.getBonusPercentage(playerId,
                com.districtx.pacificacore.api.ExperienceSource.PLAYER_KILL)));
        values.put("death_bonus", format(rankBonuses.getBonusPercentage(playerId,
                com.districtx.pacificacore.api.ExperienceSource.PVP_DEATH)));
        values.put("loot_bonus", format(rankBonuses.getBonusPercentage(playerId,
                com.districtx.pacificacore.api.ExperienceSource.LOOT)));
        values.put("rank", rankBonuses.getPrimaryRank(playerId));
        setConfigured(inventory, "gui.items.current-level", values);
    }

    private Inventory createInventory(Player player, String menu, int page, String title, int requestedRows) {
        int rows = Math.max(1, Math.min(6, requestedRows));
        LevelMenuHolder holder = new LevelMenuHolder(menu, page);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, color(apply(title,
                Map.of("level", String.valueOf(levels.getLevel(player.getUniqueId())), "page", String.valueOf(page)))));
        holder.setInventory(inventory);
        return inventory;
    }

    private void fill(Inventory inventory, String configPath) {
        ItemStack item = createItem(material(configPath + ".material", "GRAY_STAINED_GLASS_PANE"),
                text(configPath + ".name", "&7"), List.of(), Map.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, item.clone());
    }

    private void fillSlots(Inventory inventory, int[] slots, String path) {
        for (int slot : slots) set(inventory, slot, material(path + ".material", "WHITE_STAINED_GLASS_PANE"),
                text(path + ".name", "&7"), plugin.getLevelConfig().getStringList(path + ".lore"), Map.of());
    }

    private int[] parseSlots(String path, int[] fallback) {
        List<Integer> configured = plugin.getLevelConfig().getIntegerList(path);
        return configured.isEmpty() ? fallback : configured.stream().mapToInt(Integer::intValue).toArray();
    }

    private void setConfigured(Inventory inventory, String path, Map<String, String> values) {
        set(inventory, configuredSlot(path + ".slot", 0, inventory.getSize()), material(path + ".material", "STONE"),
                apply(text(path + ".name", ""), values), replace(plugin.getLevelConfig().getStringList(path + ".lore"), values), Map.of());
    }

    private void setEmpty(Inventory inventory, int slot) {
        set(inventory, slot, material("gui.items.empty.material", "GRAY_STAINED_GLASS_PANE"),
                text("gui.items.empty.name", "&7"), List.of(), Map.of());
    }

    private void set(Inventory inventory, int slot, String material, String name, List<String> lore,
                     Map<String, String> values) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, createItem(material, name, lore, values));
    }

    private ItemStack createItem(String materialName, String name, List<String> lore, Map<String, String> values) {
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

    private String levelColor(int level) {
        for (Map<?, ?> range : plugin.getLevelConfig().getMapList("level-colors")) {
            Object minimum = range.get("min-level");
            Object maximum = range.get("max-level");
            if (minimum instanceof Number min && maximum instanceof Number max
                    && level >= min.intValue() && level <= max.intValue()) {
                Object color = range.get("color");
                return color == null ? "&7" : String.valueOf(color);
            }
        }
        return "&7";
    }

    private int configuredSlot(String path, int fallback, int size) {
        int slot = plugin.getLevelConfig().getInt(path, fallback);
        return slot >= 0 && slot < size ? slot : -1;
    }

    private String material(String path, String fallback) {
        return plugin.getLevelConfig().getString(path, fallback);
    }

    private String text(String path, String fallback) {
        return plugin.getLevelConfig().getString(path, fallback);
    }

    private List<String> replace(List<String> source, Map<String, String> values) {
        return source.stream().map(line -> apply(line, values)).toList();
    }

    private String apply(String text, Map<String, String> values) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private String format(double amount) {
        if (Double.isInfinite(amount)) return "∞";
        if (Double.isNaN(amount)) return "0";
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) return "0m";
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        String path = days > 0 ? "daily-xp.cooldown-format.days"
                : hours > 0 ? "daily-xp.cooldown-format.hours" : "daily-xp.cooldown-format.minutes";
        String fallback = days > 0 ? "%days%d %hours%h" : hours > 0 ? "%hours%h %minutes%m" : "%minutes%m";
        return plugin.getLevelConfig().getString(path, fallback)
                .replace("%days%", String.valueOf(days))
                .replace("%hours%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(Math.max(1, minutes)));
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}