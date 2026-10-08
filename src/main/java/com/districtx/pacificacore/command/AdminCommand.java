package com.districtx.pacificacore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private static final String ROOT_PERMISSION = "pacifica.admin";
    private final Map<String, AdminSubCommand> commands = new LinkedHashMap<>();

    public AdminCommand(Collection<AdminSubCommand> subCommands) {
        for (AdminSubCommand subCommand : subCommands) {
            commands.put(subCommand.getName().toLowerCase(Locale.ROOT), subCommand);
            for (String alias : subCommand.getAliases()) {
                commands.put(alias.toLowerCase(Locale.ROOT), subCommand);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (accessibleCommands(sender).isEmpty()) return deny(sender);
            sender.sendMessage(ChatColor.YELLOW + "Usage: /admin <" + String.join("|", accessibleCommands(sender)) + "> ...");
            return true;
        }
        AdminSubCommand subCommand = commands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            if (accessibleCommands(sender).isEmpty()) return deny(sender);
            sender.sendMessage(ChatColor.RED + "Unknown or unavailable admin feature.");
            return true;
        }
        if (!canAccess(sender, subCommand)) return deny(sender);
        return subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matches(accessibleCommands(sender), args[0]);
        if (args.length < 1) return List.of();
        AdminSubCommand subCommand = commands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null || !canAccess(sender, subCommand)) return List.of();
        return subCommand.tabComplete(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    private boolean canAccess(CommandSender sender, AdminSubCommand subCommand) {
        return sender.hasPermission(ROOT_PERMISSION) || subCommand.canAccess(sender);
    }

    private List<String> accessibleCommands(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (AdminSubCommand subCommand : commands.values()) {
            if (canAccess(sender, subCommand) && !names.contains(subCommand.getName())) {
                names.add(subCommand.getName());
            }
        }
        return names;
    }

    private List<String> matches(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission.");
        return true;
    }
}