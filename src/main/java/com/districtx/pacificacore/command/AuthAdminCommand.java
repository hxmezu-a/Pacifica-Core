package com.districtx.pacificacore.command;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.AuthenticationMethod;
import com.districtx.pacificacore.auth.AuthSystem;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AuthAdminCommand implements AdminSubCommand {
    private final PacificaCore plugin;
    private final AuthSystem authSystem;

    public AuthAdminCommand(PacificaCore plugin, AuthSystem authSystem) {
        this.plugin = plugin;
        this.authSystem = authSystem;
    }

    @Override public String getName() { return "auth"; }
    @Override public List<String> getAliases() { return List.of(); }
    @Override public String getPermission() { return "pacifica.core.admin.auth"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("setjoin")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command must be run in-game because it saves your current position.");
                return true;
            }
            if (!authSystem.setAuthenticationJoinLocation(player.getLocation())) {
                sender.sendMessage(ChatColor.RED + "Could not save the authentication join location. Check the current world and server logs.");
                return true;
            }
            var location = player.getLocation();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&lAuthSystem &7> &aLogin/register join location has been set successfully. &7(World: &f"
                    + location.getWorld().getName() + " &7X: &f" + String.format(Locale.ROOT, "%.2f", location.getX())
                    + " &7Y: &f" + String.format(Locale.ROOT, "%.2f", location.getY())
                    + " &7Z: &f" + String.format(Locale.ROOT, "%.2f", location.getZ()) + "&7)"));
            return true;
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("status")
                && !args[0].equalsIgnoreCase("reset") && !args[0].equalsIgnoreCase("unlink")
                && !args[0].equalsIgnoreCase("reward-status")
                && !args[0].equalsIgnoreCase("reward-retry"))) {
            sender.sendMessage("Usage: /adminpc auth <status|reset|unlink|reward-status|reward-retry> <player> or /adminpc auth setjoin");
            return true;
        }
        OfflinePlayer target = plugin.findOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            authSystem.getAuthenticationMethod(target.getUniqueId()).whenComplete((method, failure) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (failure != null) {
                            sender.sendMessage("Could not read authentication status.");
                            plugin.getLogger().warning("Auth status lookup failed for UUID " + target.getUniqueId() + ".");
                            return;
                        }
                        Optional<AuthenticationMethod> selected = method;
                        sender.sendMessage("Auth status for " + (target.getName() == null ? args[1] : target.getName())
                                + ": " + selected.map(value -> "registered (" + value.name().toLowerCase(Locale.ROOT) + ")")
                                .orElse("not registered") + "; authenticated now: "
                                + authSystem.isAuthenticated(target.getUniqueId()) + ".");
                    }));
            return true;
        }
        if (args[0].equalsIgnoreCase("reward-status")) {
            authSystem.getDiscordLinkRewardStatus(target.getUniqueId()).whenComplete((status, failure) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (failure != null) {
                            sender.sendMessage("Could not read the Discord-link reward status.");
                            plugin.getLogger().warning("Discord-link reward status lookup failed for UUID "
                                    + target.getUniqueId() + ".");
                            return;
                        }
                        sender.sendMessage("Discord-link reward for "
                                + (target.getName() == null ? args[1] : target.getName()) + ": "
                                + status.orElse("NOT_QUEUED") + ".");
                    }));
            return true;
        }
        if (args[0].equalsIgnoreCase("reward-retry")) {
            authSystem.retryFailedDiscordLinkReward(target.getUniqueId()).whenComplete((requeued, failure) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (failure != null) {
                            sender.sendMessage("Could not retry the Discord-link reward.");
                            plugin.getLogger().warning("Discord-link reward retry failed for UUID "
                                    + target.getUniqueId() + ".");
                        } else if (requeued) {
                            sender.sendMessage("Failed Discord-link reward requeued for "
                                    + (target.getName() == null ? args[1] : target.getName()) + ". Review prior delivery first; already-applied commands may repeat.");
                            plugin.getLogger().info(sender.getName() + " requeued the failed Discord-link reward for UUID "
                                    + target.getUniqueId() + ".");
                        } else {
                            sender.sendMessage("That player has no failed Discord-link reward to retry.");
                        }
                    }));
            return true;
        }
        if (args[0].equalsIgnoreCase("unlink")) {
            authSystem.unlinkDiscordAccount(target.getUniqueId()).whenComplete((unlinked, failure) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (failure != null) {
                            sender.sendMessage("Could not remove the Discord account link.");
                            plugin.getLogger().warning("Auth link removal failed for UUID " + target.getUniqueId() + ".");
                        } else if (unlinked) {
                            sender.sendMessage("Discord account link removed for "
                                    + (target.getName() == null ? args[1] : target.getName()) + ".");
                            plugin.getLogger().info(sender.getName() + " removed the AuthSystem Discord link for UUID "
                                    + target.getUniqueId() + ".");
                        } else {
                            sender.sendMessage("That player has no AuthSystem Discord account link.");
                        }
                    }));
            return true;
        }
        authSystem.resetAccount(target.getUniqueId()).whenComplete((reset, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        sender.sendMessage("Could not reset the authentication account.");
                        plugin.getLogger().warning("Auth account reset failed for UUID " + target.getUniqueId() + ".");
                    } else if (reset) {
                        sender.sendMessage("Authentication account reset for "
                                + (target.getName() == null ? args[1] : target.getName()) + ".");
                        plugin.getLogger().info(sender.getName() + " reset the AuthSystem account for UUID "
                                + target.getUniqueId() + ".");
                    } else {
                        sender.sendMessage("That player has no authentication account.");
                    }
                }));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("status", "reset", "unlink", "reward-status", "reward-retry", "setjoin").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2) return plugin.getServer().getOnlinePlayers().stream()
                .map(player -> player.getName())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}