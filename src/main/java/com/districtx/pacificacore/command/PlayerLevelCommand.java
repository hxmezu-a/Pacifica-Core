package com.districtx.pacificacore.command;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.ExperienceGainResult;
import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.api.PlayerLevelService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Implements the player-facing and administrative Player Level commands. */
public final class PlayerLevelCommand implements CommandExecutor, TabCompleter {
    private final PacificaCore plugin;

    /**
     * Creates the Player Level command handler.
     *
     * @param plugin owning Pacifica-Core plugin
     */
    public PlayerLevelCommand(PacificaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("level")) return showLevel(sender, args);
        return admin(sender, args);
    }

    private boolean showLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pacifica.level.command")) return send(sender, "no-permission", Map.of());
        if (!(sender instanceof Player player)) return send(sender, "players-only", Map.of());
        if (args.length != 0) return send(sender, "usage", Map.of());
        PlayerLevelService levels = plugin.getAPI().getPlayerLevelService();
        return sendTemplate(sender, "self", levelValues(levels, player.getUniqueId(), null));
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (args.length == 0) return send(sender, "usage", Map.of());
        String action = args[0].toLowerCase(Locale.ROOT);
        String permission = switch (action) {
            case "set" -> "pacifica.level.set";
            case "addxp" -> "pacifica.level.addxp";
            case "setxp" -> "pacifica.level.setxp";
            case "removexp" -> "pacifica.level.removexp";
            case "info" -> "pacifica.level.info";
            default -> null;
        };
        if (permission == null) return send(sender, "usage", Map.of());
        if (!sender.hasPermission("pacifica.level.admin") && !sender.hasPermission(permission)) {
            return send(sender, "no-permission", Map.of());
        }
        int expectedArgs = action.equals("info") ? 2 : 3;
        if (args.length != expectedArgs) return send(sender, "usage", Map.of());
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) return send(sender, "player-not-found", Map.of());
        PlayerLevelService levels = plugin.getAPI().getPlayerLevelService();
        String targetName = target.getName() == null ? args[1] : target.getName();
        if (action.equals("info")) {
            return sendTemplate(sender, "info", levelValues(levels, target.getUniqueId(), targetName));
        }
        if (action.equals("set")) {
            int level;
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                return send(sender, "invalid-level", Map.of());
            }
            if (!levels.setLevel(target.getUniqueId(), level)) return send(sender, "invalid-level", Map.of());
            return send(sender, "level-up", Map.of("player", targetName, "level", String.valueOf(level)));
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException exception) {
            return send(sender, "invalid-experience", Map.of());
        }
        if (!Double.isFinite(amount) || amount < 0.0) return send(sender, "invalid-experience", Map.of());
        if (action.equals("addxp")) {
            ExperienceGainResult result = levels.addExperience(target.getUniqueId(), amount, ExperienceSource.ADMIN);
            if (result.getExperienceAdded() <= 0.0) return send(sender, "invalid-experience", Map.of());
            return send(sender, "added", Map.of("player", targetName, "amount", format(result.getExperienceAdded())));
        }
        if (action.equals("setxp")) {
            if (!levels.setExperience(target.getUniqueId(), amount)) return send(sender, "invalid-experience", Map.of());
            return send(sender, "set-experience", Map.of("player", targetName, "amount", format(amount)));
        }
        if (!levels.removeExperience(target.getUniqueId(), amount)) return send(sender, "invalid-experience", Map.of());
        return send(sender, "removed", Map.of("player", targetName, "amount", format(amount)));
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private Map<String, String> levelValues(PlayerLevelService levels, java.util.UUID playerId, String playerName) {
        double experience = levels.getExperience(playerId);
        double progress = levels.getProgressToNextLevel(playerId);
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("level", String.valueOf(levels.getLevel(playerId)));
        values.put("exp", format(experience));
        values.put("progress", String.valueOf(Math.round(progress * 100.0)));
        values.put("exp_to_next_level", format(levels.getExperienceToNextLevel(playerId)));
        if (playerName != null) values.put("player", playerName);
        return values;
    }

    private boolean sendTemplate(CommandSender sender, String key, Map<String, String> values) {
        String message = plugin.getConfig().getString("player-level.messages.commands." + key, "");
        return sendMessage(sender, message, values);
    }

    private boolean send(CommandSender sender, String key, Map<String, String> values) {
        String message = plugin.getConfig().getString("messages." + key, key);
        if (key.equals("no-permission") || key.equals("players-only") || key.equals("player-not-found")) {
            return sendMessage(sender, message, values);
        }
        message = plugin.getConfig().getString("player-level.messages.commands." + key, message);
        return sendMessage(sender, message, values);
    }

    private boolean sendMessage(CommandSender sender, String message, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        String[] lines = message.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) lines[i] = ChatColor.translateAlternateColorCodes('&', lines[i]);
        sender.sendMessage(lines);
        return true;
    }

    private String format(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("leveladmin")) return Collections.emptyList();
        if (args.length == 1) return partial(args[0], Arrays.asList("set", "addxp", "setxp", "removexp", "info"));
        if (args.length == 2 && Arrays.asList("set", "addxp", "setxp", "removexp", "info")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return partial(args[1], names);
        }
        return Collections.emptyList();
    }

    private List<String> partial(String input, List<String> values) {
        List<String> result = new ArrayList<>();
        String prefix = input.toLowerCase(Locale.ROOT);
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value);
        return result;
    }
}