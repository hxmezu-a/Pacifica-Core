package com.districtx.pacificacore.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface AdminSubCommand {
    String getName();
    List<String> getAliases();
    String getPermission();
    boolean execute(CommandSender sender, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);

    default boolean canAccess(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }
}