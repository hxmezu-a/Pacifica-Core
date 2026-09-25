package com.districtx.pacificacore.level.reward;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.api.LevelReward;
import com.districtx.pacificacore.api.LevelRewardContext;
import com.districtx.pacificacore.api.LevelRewardType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Locale;

final class ConfiguredLevelReward implements LevelReward {
    private final PacificaCore plugin;
    private final EconomyService economy;
    private final DiamondCurrencyService diamonds;
    private final String id;
    private final LevelRewardType type;
    private final String command;
    private final BigDecimal amount;
    private final String material;
    private final int itemAmount;

    ConfiguredLevelReward(PacificaCore plugin, EconomyService economy, DiamondCurrencyService diamonds,
                          String id, LevelRewardType type, String command, BigDecimal amount,
                          String material, int itemAmount) {
        this.plugin = plugin;
        this.economy = economy;
        this.diamonds = diamonds;
        this.id = id;
        this.type = type;
        this.command = command;
        this.amount = amount;
        this.material = material;
        this.itemAmount = itemAmount;
    }

    @Override public String getId() { return id; }
    @Override public LevelRewardType getType() { return type; }

    @Override
    public boolean execute(Player player, LevelRewardContext context) {
        if (player == null || context == null) return false;
        return switch (type) {
            case COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expand(command, context));
            case MONEY -> economy.deposit(player.getUniqueId(), amount);
            case DIAMOND -> diamonds.deposit(player.getUniqueId(), amount);
            case ITEM -> giveItem(player);
        };
    }

    private boolean giveItem(Player player) {
        Material itemType = Material.matchMaterial(material);
        if (itemType == null || itemType.isAir() || itemAmount < 1) {
            plugin.getLogger().warning("Invalid item level reward '" + id + "': " + material);
            return false;
        }
        player.getInventory().addItem(new ItemStack(itemType, itemAmount)).values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        return true;
    }

    private String expand(String configured, LevelRewardContext context) {
        Player player = context.getPlayer();
        return configured.replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%level%", String.valueOf(context.getLevel()))
                .replace("%old_level%", String.valueOf(context.getPreviousLevel()))
                .replace("%new_level%", String.valueOf(context.getLevel()))
                .replace("%total_xp%", String.valueOf(context.getTotalExperience()));
    }
}