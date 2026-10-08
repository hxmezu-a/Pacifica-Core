package com.districtx.pacificacore.command;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.api.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CurrencyAdminSubCommand implements AdminSubCommand {
    private final PacificaCore plugin;
    private final String feature;
    private final boolean diamonds;

    public CurrencyAdminSubCommand(PacificaCore plugin, String feature, boolean diamonds) {
        this.plugin = plugin;
        this.feature = feature;
        this.diamonds = diamonds;
    }

    @Override public String getName() { return feature; }
    @Override public List<String> getAliases() { return diamonds ? List.of("diamond") : List.of("bal"); }
    @Override public String getPermission() { return "pacifica.admin." + feature; }

    @Override
    public boolean canAccess(CommandSender sender) {
        if (AdminSubCommand.super.canAccess(sender)) return true;
        for (String action : List.of("give", "remove", "set", "reset", "reload")) {
            if (action.equals("reload") && !diamonds) continue;
            if (sender.hasPermission(actionPermission(action))) return true;
        }
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && diamonds) {
            if (!sender.hasPermission(actionPermission("reload"))) {
                return plugin.sendAdminMessage(sender, "no-permission");
            }
            plugin.reloadAdministrativeConfiguration();
            return plugin.sendAdminMessage(sender, "reloaded");
        }
        if (args.length == 0) return plugin.sendAdminMessage(sender, "usage");
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("give", "remove", "set", "reset").contains(action)) {
            return plugin.sendAdminMessage(sender, "usage");
        }
        if (!sender.hasPermission(actionPermission(action))) {
            return plugin.sendAdminMessage(sender, "no-permission");
        }
        if (args.length != (action.equals("reset") ? 2 : 3)) {
            return plugin.sendAdminMessage(sender, "usage");
        }
        OfflinePlayer target = plugin.findOfflinePlayer(args[1]);
        if (target == null) return plugin.sendAdminMessage(sender, "player-not-found");
        BigDecimal amount = BigDecimal.ZERO;
        if (!action.equals("reset")) {
            try {
                amount = new BigDecimal(args[2]);
            } catch (NumberFormatException exception) {
                return plugin.sendAdminMessage(sender, "invalid-amount");
            }
            if (amount.signum() < 0 || amount.scale() > 8) {
                return plugin.sendAdminMessage(sender, "invalid-amount");
            }
        }
        boolean success = switch (action) {
            case "give" -> deposit(target, amount);
            case "remove" -> withdraw(target, amount);
            case "set" -> set(target, amount);
            case "reset" -> reset(target);
            default -> false;
        };
        if (!success) return plugin.sendAdminMessage(sender, "invalid-amount");
        String key = action.equals("reset") ? "reset" : action.equals("set") ? "set"
                : action.equals("remove") ? "removed" : "updated";
        String targetName = target.getName() == null ? args[1] : target.getName();
        return plugin.sendAdminMessage(sender, key, "player", targetName,
                "currency", diamonds ? "diamond" : "balance", "amount", plugin.formatAmount(amount));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>();
            for (String action : List.of("give", "remove", "set", "reset")) {
                if (sender.hasPermission(actionPermission(action))) actions.add(action);
            }
            if (diamonds && sender.hasPermission(actionPermission("reload"))) actions.add("reload");
            return matches(actions, args[0]);
        }
        if (args.length == 2 && List.of("give", "remove", "set", "reset").contains(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission(actionPermission(args[0].toLowerCase(Locale.ROOT)))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return matches(names, args[1]);
        }
        return List.of();
    }

    private String actionPermission(String action) {
        String permission = switch (action) {
            case "give" -> diamonds ? "dgive" : "bgive";
            case "remove" -> "get";
            default -> action;
        };
        return "pacifica.core.admin." + permission;
    }

    private boolean deposit(OfflinePlayer player, BigDecimal amount) {
        return diamonds ? diamondService().deposit(player.getUniqueId(), amount)
                : economyService().deposit(player.getUniqueId(), amount);
    }

    private boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        return diamonds ? diamondService().withdraw(player.getUniqueId(), amount)
                : economyService().withdraw(player.getUniqueId(), amount);
    }

    private boolean set(OfflinePlayer player, BigDecimal amount) {
        return diamonds ? diamondService().setBalance(player.getUniqueId(), amount)
                : economyService().setBalance(player.getUniqueId(), amount);
    }

    private boolean reset(OfflinePlayer player) {
        return diamonds ? diamondService().resetBalance(player.getUniqueId())
                : economyService().resetBalance(player.getUniqueId());
    }

    private DiamondCurrencyService diamondService() {
        return plugin.getAPI().getDiamondCurrencyService();
    }

    private EconomyService economyService() {
        return plugin.getAPI().getEconomyService();
    }

    private List<String> matches(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}