package com.districtx.pacificacore.shop;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.event.PlayerLevelChangeEvent;
import com.districtx.pacificacore.api.SpawnShop;
import com.districtx.pacificacore.api.SpawnShopItem;
import com.districtx.pacificacore.api.SpawnShopPurchaseResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SpawnShopGuiManager implements Listener {
    private static final List<Integer> LIGHT_PANE_SLOTS = List.of(0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53);
    private static final List<Integer> BLACK_PANE_SLOTS = List.of(1, 10, 19, 28, 37, 46, 2, 3, 4, 5, 6, 7,
            16, 25, 34, 43, 52);
    private static final List<Integer> CATEGORY_SLOTS = List.of(20, 21, 22, 23, 24, 29, 30, 31, 32, 33);
    private static final List<Category> CATEGORIES = List.of(
            new Category("THROWABLE", "GUNPOWDER", List.of("&7Remember: Don't miss!")),
            new Category("MELEE", "DIAMOND_SWORD", List.of("&7For when you need to get close and personal.")),
            new Category("PISTOL", "IRON_AXE", List.of("&7A basic weapon; point, shoot, damage.")),
            new Category("LMG", "FLINT", List.of("&7Slow and steady wins the race... so do a lot of bullets.")),
            new Category("SMG", "POPPY", List.of("&7For when you need to get close and personal.")),
            new Category("SHOTGUN", "WOODEN_SHOVEL", List.of("&7Damage? Check!", "&7Spread? Check!",
                    "&7Overall coolness factor? What more could you want!?")),
            new Category("ASSAULT", "DIAMOND_PICKAXE", List.of("&7Now we're talking!")),
            new Category("LAUNCHER", "DIAMOND_HORSE_ARMOR", List.of("&7When you're too lazy to throw...")),
            new Category("SNIPER", "DIAMOND_HOE", List.of("&7Not all battles are fought at close range.")),
            new Category("SPECIAL", "LEAD", List.of("&7Respect comes in many forms...",
                    "&7Especially that of a damage death machine!")));

    private final PacificaCore plugin;
    private final SpawnShopManager shops;

    public SpawnShopGuiManager(PacificaCore plugin, SpawnShopManager shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = create(SpawnShopGuiHolder.Type.MAIN, null, null, null, 54, "&6&lSpawn Shop");
        decorate(inventory, Set.of());
        addShopButton(player, inventory, 29, "weapon", "DIAMOND_AXE", "&c&lWeapon Shop");
        addShopButton(player, inventory, 30, "vehicle", "MINECART", "&e&lVehicle Shop");
        addShopButton(player, inventory, 31, "armor", "DIAMOND_CHESTPLATE", "&e&lArmor Shop");
        addShopButton(player, inventory, 32, "food", "COOKED_BEEF", "&e&lFood Shop");
        addShopButton(player, inventory, 33, "medic", "POTION", "&d&lMedic Shop");
        player.openInventory(inventory);
    }

    public void openShop(Player player, String shopId) {
        if (!shops.canAccess(player, shopId)) {
            sendLocked(player, shopId);
            return;
        }
        if (shopId.equalsIgnoreCase("weapon")) {
            openWeaponCategories(player);
            return;
        }
        SpawnShop shop = shops.getShop(shopId).orElseThrow();
        openItems(player, shop.id(), null, shop.displayName());
    }

    private void openWeaponCategories(Player player) {
        Inventory inventory = create(SpawnShopGuiHolder.Type.CATEGORIES, "weapon", null, null, 54,
                "&c&lChoose Category");
        decorate(inventory, Set.copyOf(CATEGORY_SLOTS));
        for (int index = 0; index < CATEGORIES.size(); index++) {
            Category category = CATEGORIES.get(index);
            inventory.setItem(CATEGORY_SLOTS.get(index), item(category.material(), "&a&l" + category.name(),
                    category.lore()));
        }
        player.openInventory(inventory);
    }

    private void openItems(Player player, String shopId, String category, String title) {
        SpawnShopGuiHolder.Type type = category == null
                ? SpawnShopGuiHolder.Type.SHOP : SpawnShopGuiHolder.Type.CATEGORY_SHOP;
        String inventoryTitle = category == null ? title : "&9&lPurchase " + category.toUpperCase(Locale.ROOT);
        Inventory inventory = create(type, shopId, category, null, 54, inventoryTitle);
        decorate(inventory, Set.copyOf(SpawnShopManager.ITEM_SLOTS));
        ItemStack placeholder = item("GRAY_STAINED_GLASS_PANE", "&7");
        for (int slot : SpawnShopManager.ITEM_SLOTS) inventory.setItem(slot, placeholder.clone());
        for (SpawnShopItem entry : shops.getItems(shopId, category)) {
            if (!SpawnShopManager.ITEM_SLOTS.contains(entry.slot())) continue;
            inventory.setItem(entry.slot(), displayItem(entry));
        }
        player.openInventory(inventory);
    }

    private void openQuantity(Player player, SpawnShopItem entry) {
        Inventory inventory = create(SpawnShopGuiHolder.Type.QUANTITY, entry.shopId(), entry.category(),
                entry.id(), 9, "&6&lChoose Quantity");
        for (int slot : List.of(0, 2, 6, 8)) inventory.setItem(slot, item("GRAY_STAINED_GLASS_PANE", "&7"));
        inventory.setItem(4, displayItem(entry));
        for (int[] button : new int[][]{{1, 1}, {3, 8}, {5, 16}, {7, 32}}) {
            int quantity = button[1];
            inventory.setItem(button[0], item("GREEN_STAINED_GLASS_PANE", "&a&lx" + quantity));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof SpawnShopGuiHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        switch (holder.type()) {
            case MAIN -> handleMainClick(player, slot);
            case CATEGORIES -> handleCategoryClick(player, slot);
            case SHOP, CATEGORY_SHOP -> handleShopClick(player, holder, slot);
            case QUANTITY -> handleQuantityClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpawnShopGuiHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof SpawnShopGuiHolder holder)) return;
        if (holder.type() == SpawnShopGuiHolder.Type.MAIN) openMainMenu(player);
        else if (holder.shopId() != null && !shops.canAccess(player, holder.shopId())) player.closeInventory();
    }

    private void handleMainClick(Player player, int slot) {
        String shopId = switch (slot) {
            case 29 -> "weapon";
            case 30 -> "vehicle";
            case 31 -> "armor";
            case 32 -> "food";
            case 33 -> "medic";
            default -> null;
        };
        if (shopId == null) return;
        if (shops.canAccess(player, shopId)) openShop(player, shopId);
    }

    private void handleCategoryClick(Player player, int slot) {
        int index = CATEGORY_SLOTS.indexOf(slot);
        if (index < 0 || !shops.canAccess(player, "weapon")) return;
        Category category = CATEGORIES.get(index);
        openItems(player, "weapon", category.name().toLowerCase(Locale.ROOT),
                "&9&lPurchase " + category.name());
    }

    private void handleShopClick(Player player, SpawnShopGuiHolder holder, int slot) {
        if (!shops.canAccess(player, holder.shopId())) {
            sendLocked(player, holder.shopId());
            player.closeInventory();
            return;
        }
        for (SpawnShopItem entry : shops.getItems(holder.shopId(), holder.category())) {
            if (entry.slot() == slot) {
                openQuantity(player, entry);
                return;
            }
        }
    }

    private void handleQuantityClick(Player player, SpawnShopGuiHolder holder, int slot) {
        int quantity = switch (slot) {
            case 1 -> 1;
            case 3 -> 8;
            case 5 -> 16;
            case 7 -> 32;
            default -> 0;
        };
        if (quantity == 0) return;
        SpawnShopPurchaseResult result = shops.purchaseDetailed(player, holder.itemId(), quantity);
        String key = switch (result) {
            case SUCCESS -> "purchase-success";
            case INVENTORY_FULL -> "inventory-full";
            case INSUFFICIENT_FUNDS -> "insufficient-funds";
            case SHOP_LOCKED -> "shop-locked";
            default -> "purchase-failed";
        };
        String message = plugin.getConfig().getString("spawn-shop.messages." + key, defaultMessage(key));
        SpawnShopItem item = shops.getItem(holder.itemId()).orElse(null);
        double total = item == null ? 0 : BigDecimal.valueOf(item.unitPrice()).multiply(BigDecimal.valueOf(quantity)).doubleValue();
        message = message.replace("%quantity%", String.valueOf(quantity))
                .replace("%price%", SpawnShopNumberFormatter.formatPrice(total));
        player.sendMessage(color(message));
        if (result == SpawnShopPurchaseResult.SUCCESS) player.closeInventory();
        else if (result == SpawnShopPurchaseResult.SHOP_LOCKED) player.closeInventory();
    }

    private void addShopButton(Player player, Inventory inventory, int slot, String shopId, String material,
                               String name) {
        if (shops.canAccess(player, shopId)) inventory.setItem(slot, item(material, name));
    }

    public void refreshOpenInventories(String shopId, String category) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof SpawnShopGuiHolder holder)) {
                continue;
            }
            if ((holder.type() != SpawnShopGuiHolder.Type.SHOP
                    && holder.type() != SpawnShopGuiHolder.Type.CATEGORY_SHOP)
                    || !shopId.equalsIgnoreCase(holder.shopId())
                    || !java.util.Objects.equals(category, holder.category())) continue;
            if (!shops.canAccess(player, shopId)) {
                player.closeInventory();
                continue;
            }
            String title = category == null
                    ? shops.getShop(shopId).map(SpawnShop::displayName).orElse("&6&lShop") : null;
            openItems(player, shopId, category, title);
        }
    }

    private Inventory create(SpawnShopGuiHolder.Type type, String shopId, String category, UUID itemId,
                            int size, String title) {
        SpawnShopGuiHolder holder = new SpawnShopGuiHolder(type, shopId, category, itemId);
        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.setInventory(inventory);
        return inventory;
    }

    private void decorate(Inventory inventory, Set<Integer> contentSlots) {
        ItemStack light = item("LIGHT_GRAY_STAINED_GLASS_PANE", "&7");
        ItemStack black = item("BLACK_STAINED_GLASS_PANE", "&7");
        ItemStack gray = item("GRAY_STAINED_GLASS_PANE", "&7");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (contentSlots.contains(slot)) continue;
            if (LIGHT_PANE_SLOTS.contains(slot)) inventory.setItem(slot, light.clone());
            else if (BLACK_PANE_SLOTS.contains(slot)) inventory.setItem(slot, black.clone());
            else inventory.setItem(slot, gray.clone());
        }
    }

    private ItemStack displayItem(SpawnShopItem entry) {
        ItemStack display = entry.itemStack();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> {
            String plain = ChatColor.stripColor(line);
            return plain != null && plain.matches("- \\$\\d+(?:\\.\\d+)?");
        });
        lore.add(color("&7- &a$" + SpawnShopNumberFormatter.formatPrice(entry.unitPrice())));
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack item(String materialName, String name, String... lore) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.matchMaterial("BARRIER");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack item(String materialName, String name, List<String> lore) {
        return item(materialName, name, lore.toArray(String[]::new));
    }

    private void sendLocked(Player player, String shopId) {
        int required = shops.getRequiredLevel(shopId);
        String message = plugin.getConfig().getString("spawn-shop.messages.shop-locked",
                "&cYou need Pacifica Level %required-level% to access this shop.");
        player.sendMessage(color(message.replace("%required-level%", String.valueOf(required))));
    }

    private String defaultMessage(String key) {
        return switch (key) {
            case "purchase-success" -> "&aPurchased x%quantity% for $%price%.";
            case "inventory-full" -> "&cYou do not have enough inventory space.";
            case "insufficient-funds" -> "&cYou do not have enough money.";
            case "shop-locked" -> "&cYou do not meet this shop's level requirement.";
            default -> "&cThe purchase could not be completed.";
        };
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record Category(String name, String material, List<String> lore) {
    }
}