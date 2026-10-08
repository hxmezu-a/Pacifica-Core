package com.districtx.pacificacore.shop;

import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.ShopAccessService;
import com.districtx.pacificacore.api.SpawnShop;
import com.districtx.pacificacore.api.SpawnShopItem;
import com.districtx.pacificacore.api.SpawnShopSlotService;
import com.districtx.pacificacore.api.SpawnShopService;
import com.districtx.pacificacore.storage.SpawnShopRepository;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnShopManager implements SpawnShopService, ShopAccessService, SpawnShopSlotService {
    private static final Set<String> SHOP_IDS = Set.of("weapon", "armor", "food", "vehicle", "medic");
    private static final Set<String> WEAPON_CATEGORIES = Set.of("shotgun", "assault", "sniper", "pistol", "smg",
            "lmg", "throwable", "melee", "launcher", "special");
    static final List<Integer> ITEM_SLOTS = List.of(11, 12, 13, 14, 15, 20, 21, 22, 23, 24,
            29, 30, 31, 32, 33, 38, 39, 40, 41, 42);

    private final JavaPlugin plugin;
    private final SpawnShopRepository repository;
    private final PlayerLevelService playerLevels;
    private final Economy economy;
    private final Map<UUID, SpawnShopItem> itemCache = new ConcurrentHashMap<>();
    private SpawnShopGuiManager guiManager;

    public SpawnShopManager(JavaPlugin plugin, SpawnShopRepository repository, PlayerLevelService playerLevels,
                            Economy economy) {
        this.plugin = plugin;
        this.repository = repository;
        this.playerLevels = playerLevels;
        this.economy = economy;
        for (SpawnShopItem item : repository.findAll()) itemCache.put(item.id(), item);
    }

    @Override
    public Optional<SpawnShop> getShop(String shopId) {
        String id = normalize(shopId);
        if (!SHOP_IDS.contains(id)) return Optional.empty();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("spawn-shop.shops." + id);
        String title = section == null ? defaultTitle(id) : section.getString("title", defaultTitle(id));
        int defaultLevel = switch (id) {
            case "armor" -> 10;
            case "vehicle" -> 25;
            case "medic" -> 50;
            default -> 0;
        };
        int requiredLevel = section == null ? defaultLevel : section.getInt("required-level", defaultLevel);
        boolean enabled = section == null || section.getBoolean("enabled", true);
        return Optional.of(new SpawnShop(id, title, Math.max(0, requiredLevel), enabled));
    }

    @Override
    public Collection<SpawnShop> getShops() {
        return SHOP_IDS.stream().map(this::getShop).flatMap(Optional::stream).toList();
    }

    @Override
    public boolean canAccess(Player player, String shopId) {
        if (player == null) return false;
        Optional<SpawnShop> shop = getShop(shopId);
        return shop.isPresent() && shop.get().enabled()
                && playerLevels.getLevel(player.getUniqueId()) >= shop.get().requiredLevel();
    }

    @Override
    public int getRequiredLevel(String shopId) {
        return getShop(shopId).map(SpawnShop::requiredLevel).orElse(Integer.MAX_VALUE);
    }

    @Override
    public void openShop(Player player, String shopId) {
        if (guiManager != null && canAccess(player, shopId)) guiManager.openShop(player, shopId);
    }

    public void setGuiManager(SpawnShopGuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public List<Integer> getAvailableItemSlots(String shopId) {
        return getShop(shopId).isPresent() ? ITEM_SLOTS : List.of();
    }

    @Override
    public java.util.OptionalInt getFirstAvailableItemSlot(String shopId) {
        return getAvailableItemSlots(shopId).stream().mapToInt(Integer::intValue).findFirst();
    }

    @Override
    public boolean isReservedSlot(int slot) {
        return slot < 0 || slot >= 54 || !ITEM_SLOTS.contains(slot);
    }

    @Override
    public boolean isShopItemSlot(int slot) {
        return slot >= 0 && slot < 54 && ITEM_SLOTS.contains(slot);
    }

    @Override
    public synchronized Optional<SpawnShopItem> addItem(String shopId, String category, ItemStack item, double price) {
        String id = normalize(shopId);
        String normalizedCategory = category == null || category.isBlank() ? null : normalize(category);
        if (getShop(id).isEmpty() || item == null || item.getType().isAir() || !Double.isFinite(price) || price <= 0) {
            return Optional.empty();
        }
        if (id.equals("weapon")) {
            if (normalizedCategory == null || !WEAPON_CATEGORIES.contains(normalizedCategory)) return Optional.empty();
        } else if (!Set.of("medic", "food", "vehicle", "armor").contains(id) || normalizedCategory != null) {
            return Optional.empty();
        }
        List<SpawnShopItem> current = getItems(id, normalizedCategory).stream().toList();
        int slot = getAvailableItemSlots(id).stream()
                .filter(candidate -> current.stream().noneMatch(existing -> existing.slot() == candidate))
                .findFirst().orElse(-1);
        if (slot < 0) return Optional.empty();
        double normalizedPrice = BigDecimal.valueOf(price).setScale(1, RoundingMode.HALF_UP).doubleValue();
        if (normalizedPrice <= 0) return Optional.empty();
        ItemStack stored = item.clone();
        stored.setAmount(1);
        SpawnShopItem entry = new SpawnShopItem(UUID.randomUUID(), id, normalizedCategory, stored, normalizedPrice, slot);
        if (!repository.save(entry)) return Optional.empty();
        itemCache.put(entry.id(), entry);
        if (guiManager != null) {
            Runnable refresh = () -> guiManager.refreshOpenInventories(id, normalizedCategory);
            if (Bukkit.isPrimaryThread()) refresh.run();
            else Bukkit.getScheduler().runTask(plugin, refresh);
        }
        return Optional.of(entry);
    }

    @Override
    public boolean removeItem(UUID shopItemId) {
        if (shopItemId == null || !repository.delete(shopItemId)) return false;
        itemCache.remove(shopItemId);
        return true;
    }

    @Override
    public Collection<SpawnShopItem> getItems(String shopId, String category) {
        String id = normalize(shopId);
        String normalizedCategory = category == null || category.isBlank() ? null : normalize(category);
        return itemCache.values().stream().filter(item -> item.shopId().equals(id)
                && java.util.Objects.equals(item.category(), normalizedCategory))
                .sorted(java.util.Comparator.comparingInt(SpawnShopItem::slot)).toList();
    }

    public Optional<SpawnShopItem> getItem(UUID itemId) {
        return Optional.ofNullable(itemId == null ? null : itemCache.get(itemId));
    }

    @Override
    public boolean purchase(Player player, UUID shopItemId, int quantity) {
        return purchaseDetailed(player, shopItemId, quantity) == com.districtx.pacificacore.api.SpawnShopPurchaseResult.SUCCESS;
    }

    @Override
    public com.districtx.pacificacore.api.SpawnShopPurchaseResult purchaseDetailed(Player player, UUID shopItemId,
                                                                                    int quantity) {
        if (player == null || !player.isOnline() || shopItemId == null || !Set.of(1, 8, 16, 32).contains(quantity)) {
            return com.districtx.pacificacore.api.SpawnShopPurchaseResult.INVALID_REQUEST;
        }
        Optional<SpawnShopItem> stored = getItem(shopItemId);
        if (stored.isEmpty()) return com.districtx.pacificacore.api.SpawnShopPurchaseResult.ITEM_UNAVAILABLE;
        SpawnShopItem item = stored.get();
        if (!canAccess(player, item.shopId())) return com.districtx.pacificacore.api.SpawnShopPurchaseResult.SHOP_LOCKED;
        if (!Double.isFinite(item.unitPrice()) || item.unitPrice() <= 0) {
            return com.districtx.pacificacore.api.SpawnShopPurchaseResult.INVALID_PRICE;
        }
        BigDecimal totalCost = BigDecimal.valueOf(item.unitPrice()).multiply(BigDecimal.valueOf(quantity));
        double total = totalCost.doubleValue();
        double balance = economy.getBalance(player);
        if (!Double.isFinite(total) || total <= 0) return com.districtx.pacificacore.api.SpawnShopPurchaseResult.INVALID_PRICE;
        if (!Double.isFinite(balance) || balance < total) {
            return com.districtx.pacificacore.api.SpawnShopPurchaseResult.INSUFFICIENT_FUNDS;
        }
        ItemStack template = item.itemStack();
        PlayerInventory inventory = player.getInventory();
        if (!hasSpace(inventory, template, quantity)) {
            return com.districtx.pacificacore.api.SpawnShopPurchaseResult.INVENTORY_FULL;
        }
        ItemStack[] before = cloneContents(inventory.getStorageContents());
        EconomyResponse withdrawal = economy.withdrawPlayer(player, total);
        if (!withdrawal.transactionSuccess()) return com.districtx.pacificacore.api.SpawnShopPurchaseResult.ECONOMY_ERROR;
        List<ItemStack> purchases = new ArrayList<>();
        int remaining = quantity;
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int amount = Math.min(remaining, maxStack);
            stack.setAmount(amount);
            purchases.add(stack);
            remaining -= amount;
        }
        boolean delivered;
        try {
            delivered = inventory.addItem(purchases.toArray(ItemStack[]::new)).isEmpty();
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not deliver Spawn Shop purchase to " + player.getUniqueId()
                    + ": " + exception.getMessage());
            delivered = false;
        }
        if (delivered) {
            return com.districtx.pacificacore.api.SpawnShopPurchaseResult.SUCCESS;
        }
        inventory.setStorageContents(before);
        EconomyResponse refund = economy.depositPlayer(player, total);
        if (!refund.transactionSuccess()) {
            plugin.getLogger().severe("Could not refund failed Spawn Shop purchase for " + player.getUniqueId());
        }
        return com.districtx.pacificacore.api.SpawnShopPurchaseResult.ECONOMY_ERROR;
    }

    private boolean hasSpace(PlayerInventory inventory, ItemStack template, int quantity) {
        long capacity = 0;
        int maxStack = Math.max(1, template.getMaxStackSize());
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) capacity += maxStack;
            else if (current.isSimilar(template)) capacity += Math.max(0, maxStack - current.getAmount());
            if (capacity >= quantity) return true;
        }
        return false;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        return Arrays.stream(contents).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String defaultTitle(String id) {
        return switch (id) {
            case "weapon" -> "&c&lWeapon Shop";
            case "armor" -> "&e&lArmor Shop";
            case "food" -> "&e&lFood Shop";
            case "vehicle" -> "&e&lVehicle Shop";
            case "medic" -> "&d&lMedic Shop";
            default -> "&6&lSpawn Shop";
        };
    }
}