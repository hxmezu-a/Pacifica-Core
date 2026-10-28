package com.districtx.pacificacore.command;

import com.districtx.pacificacore.PacificaCore;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class CoreAdminCommand implements AdminSubCommand {
    private final PacificaCore plugin;

    public CoreAdminCommand(PacificaCore plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "core"; }
    @Override public List<String> getAliases() { return List.of(); }
    @Override public String getPermission() { return "pacifica.admin.core"; }

    @Override
    public boolean canAccess(CommandSender sender) {
        return AdminSubCommand.super.canAccess(sender) || sender.hasPermission("pacifica.core.admin.reload");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            return plugin.sendAdminMessage(sender, "usage");
        }
        if (!sender.hasPermission("pacifica.core.admin.reload")) {
            return plugin.sendAdminMessage(sender, "no-permission");
        }
        plugin.reloadAdministrativeConfiguration();
        return plugin.sendAdminMessage(sender, "reloaded");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("pacifica.core.admin.reload")
                && "reload".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) return List.of("reload");
        return List.of();
    }
}