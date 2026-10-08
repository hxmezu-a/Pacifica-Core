package com.districtx.pacificacore.command;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.shop.SpawnShopGuiManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnShopCommand implements CommandExecutor {
    private final PacificaCore plugin;
    private final SpawnShopGuiManager guiManager;

    public SpawnShopCommand(PacificaCore plugin, SpawnShopGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pacifica.spawnshop")) {
            return send(sender, plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission."));
        }
        if (!(sender instanceof Player player)) {
            return send(sender, plugin.getConfig().getString("messages.players-only", "&cOnly players can use this command."));
        }
        guiManager.openMainMenu(player);
        return true;
    }

    private boolean send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        return true;
    }
}