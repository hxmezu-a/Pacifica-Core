package com.districtx.pacificacore.command;

import com.districtx.pacificacore.api.NpcLinkService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class BankingAdminSubCommand implements AdminSubCommand {
    private final NpcLinkService npcLinks;

    public BankingAdminSubCommand(NpcLinkService npcLinks) {
        this.npcLinks = npcLinks;
    }

    @Override public String getName() { return "bank"; }
    @Override public List<String> getAliases() { return List.of(); }
    @Override public String getPermission() { return "pacifica.admin.bank"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) return send(sender, "&cYou do not have permission.");
        if (args.length == 2 && args[0].equalsIgnoreCase("link")) {
            String npcId = args[1].trim();
            if (npcId.isEmpty()) return usage(sender);
            npcLinks.link("bank", npcId);
            return send(sender, "&aLinked the bank feature to external NPC '&f" + npcId + "&a'.");
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("unlink")) {
            if (!npcLinks.isLinked("bank")) return send(sender, "&cNo bank NPC link is saved.");
            npcLinks.unlink("bank");
            return send(sender, "&aRemoved the bank NPC link.");
        }
        return usage(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission()) || args.length != 1) return List.of();
        return List.of("link", "unlink").stream().filter(value -> value.startsWith(args[0].toLowerCase()))
                .toList();
    }

    private boolean usage(CommandSender sender) {
        return send(sender, "&eUsage: /adminpc bank link <npc-id> | /adminpc bank unlink");
    }

    private boolean send(CommandSender sender, String text) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
        return true;
    }
}