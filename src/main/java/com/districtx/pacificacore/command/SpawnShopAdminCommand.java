package com.districtx.pacificacore.command;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.NpcLinkService;
import com.districtx.pacificacore.api.SpawnShopItem;
import com.districtx.pacificacore.api.SpawnShopNpcLinkService;
import com.districtx.pacificacore.shop.SpawnShopManager;
import com.districtx.pacificacore.shop.SpawnShopNumberFormatter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class SpawnShopAdminCommand implements AdminSubCommand {
    private static final String PERMISSION = "pacifica.spawnshop.admin";
    private static final List<String> SHOPS = List.of("medic", "food", "vehicle", "armor", "weapon");
    private static final List<String> CATEGORIES = List.of("shotgun", "assault", "sniper", "pistol", "smg",
            "lmg", "throwable", "melee", "launcher", "special");

    private final PacificaCore plugin;
    private final SpawnShopManager shops;
    private final NpcLinkService npcLinks;
    private final SpawnShopNpcLinkService shopNpcLinks;

    public SpawnShopAdminCommand(PacificaCore plugin, SpawnShopManager shops,
                                 NpcLinkService npcLinks, SpawnShopNpcLinkService shopNpcLinks) {
        this.plugin = plugin;
        this.shops = shops;
        this.npcLinks = npcLinks;
        this.shopNpcLinks = shopNpcLinks;
    }

    @Override public String getName() { return "sshop"; }
    @Override public List<String> getAliases() { return List.of("spawnshop"); }
    @Override public String getPermission() { return "pacifica.admin.sshop"; }

    @Override
    public boolean canAccess(CommandSender sender) {
        return AdminSubCommand.super.canAccess(sender) || sender.hasPermission(PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return send(sender,
                plugin.getMessage("no-permission", "&cYou do not have permission."));
        if (args.length == 3 && args[0].equalsIgnoreCase("link")) {
            return linkNpc(sender, args[1], args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unlink")) return unlinkNpc(sender, args[1]);
        if (!(sender instanceof Player player)) return send(sender,
                plugin.getMessage("players-only", "&cOnly players can use this command."));
        if (args.length == 3 && !args[0].equalsIgnoreCase("weapon")) {
            return addStandardItem(player, args[0], args[1], args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("weapon")) {
            return addWeaponItem(player, args[1], args[2], args[3]);
        }
        return send(sender, "&eUsage: /adminpc sshop <shop> addhand <price> or /adminpc sshop weapon addhand <category> <price>");
    }

    private boolean linkNpc(CommandSender sender, String shopId, String npcId) {
        String shop = shopId.toLowerCase(Locale.ROOT);
        String externalNpcId = npcId.trim();
        if (shops.getShop(shop).isEmpty()) return send(sender, "&cShop does not exist.");
        if (externalNpcId.isEmpty()) return send(sender, "&cEnter an external NPC ID.");
        for (String oldNpcId : shopNpcLinks.getNpcsLinkedToShop(shop)) shopNpcLinks.unlinkNpc(oldNpcId);
        npcLinks.link("spawnshop:" + shop, externalNpcId);
        shopNpcLinks.linkNpcToShop(externalNpcId, shop);
        return send(sender, "&aLinked external NPC '&f" + externalNpcId + "&a' to shop '&f" + shop + "&a'.");
    }

    private boolean unlinkNpc(CommandSender sender, String shopId) {
        String shop = shopId.toLowerCase(Locale.ROOT);
        if (shops.getShop(shop).isEmpty()) return send(sender, "&cShop does not exist.");
        String npcId = npcLinks.getLinkedNpcId("spawnshop:" + shop).orElse(null);
        if (npcId == null && shopNpcLinks.getNpcsLinkedToShop(shop).isEmpty()) {
            return send(sender, "&cThis shop is not linked to an external NPC.");
        }
        npcLinks.unlink("spawnshop:" + shop);
        for (String linkedNpcId : shopNpcLinks.getNpcsLinkedToShop(shop)) shopNpcLinks.unlinkNpc(linkedNpcId);
        return send(sender, "&aUnlinked external NPC '&f" + (npcId == null ? "" : npcId)
                + "&a' from shop '&f" + shop + "&a'.");
    }

    private boolean addStandardItem(Player player, String shopId, String action, String priceText) {
        String shop = shopId.toLowerCase(Locale.ROOT);
        if (!List.of("medic", "food", "vehicle", "armor").contains(shop) || shops.getShop(shop).isEmpty()) {
            return send(player, "&cShop does not exist.");
        }
        if (!action.equalsIgnoreCase("addhand")) return send(player,
                "&eUsage: /adminpc sshop <shop> addhand <price>");
        return addHeldItem(player, shop, null, priceText);
    }

    private boolean addWeaponItem(Player player, String action, String category, String priceText) {
        String weaponCategory = category.toLowerCase(Locale.ROOT);
        if (!action.equalsIgnoreCase("addhand")) return send(player,
                "&eUsage: /adminpc sshop weapon addhand <category> <price>");
        if (!CATEGORIES.contains(weaponCategory)) return send(player, "&cUnknown weapon category.");
        return addHeldItem(player, "weapon", weaponCategory, priceText);
    }

    private boolean addHeldItem(Player player, String shopId, String category, String priceText) {
        double price;
        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException exception) {
            return send(player, "&cEnter a valid price greater than zero.");
        }
        if (!Double.isFinite(price) || price <= 0) return send(player, "&cEnter a valid price greater than zero.");
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return send(player, "&cYou must hold an item.");
        SpawnShopItem added = shops.addItem(shopId, category, held.clone(), price).orElse(null);
        if (added == null) return send(player, "&cThe shop is full.");
        return send(player, "&aAdded the held item to " + shopId + (category == null ? "" : "/" + category)
                + " for $" + SpawnShopNumberFormatter.formatPrice(added.unitPrice()) + ".");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return matches(java.util.stream.Stream.concat(SHOPS.stream(),
                java.util.stream.Stream.of("link", "unlink")).toList(), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("link") || args[0].equalsIgnoreCase("unlink"))) {
            return matches(SHOPS, args[1]);
        }
        if (args.length == 2 && SHOPS.contains(args[0].toLowerCase(Locale.ROOT))) return matches(List.of("addhand"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("weapon") && args[1].equalsIgnoreCase("addhand")) {
            return matches(CATEGORIES, args[2]);
        }
        return List.of();
    }

    private List<String> matches(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }

    private boolean send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        return true;
    }
}