package com.districtx.pacificacore.market;

import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.storage.BlackMarketRepository;
import com.districtx.pacificacore.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.wesjd.anvilgui.AnvilGUI;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.Collections;

public final class BlackMarketManager implements CommandExecutor, Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final BlackMarketService service;
    private final EconomyService economy;
    private final org.bukkit.NamespacedKey offerKey;
    private final org.bukkit.NamespacedKey categoryKey;
    private final BlackMarketCategoryDetector categoryDetector;
    private final BlackMarketOfferSlotRegistry offerSlotRegistry = new BlackMarketOfferSlotRegistry();
    private final BlackMarketOfferSlotPermissionService offerSlotPermissionService =
            new DefaultBlackMarketOfferSlotPermissionService(offerSlotRegistry);
    private final Map<UUID, BrowseState> browseStates = new HashMap<>();
    private int cleanupTask = -1;

    public BlackMarketManager(JavaPlugin plugin, DatabaseManager database, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.offerKey = new org.bukkit.NamespacedKey(plugin, "offer");
        this.categoryKey = new org.bukkit.NamespacedKey(plugin, "blackmarket_category");
        this.categoryDetector = new BlackMarketCategoryDetector(plugin);
        this.service = new BlackMarketServiceImpl(plugin, new BlackMarketRepository(plugin, database), economy);
        cleanupTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, service::cleanupExpiredOffers, 20L * 60L, 20L * 60L);
    }

    public BlackMarketService getService() { return service; }

    public int cleanupExpiredOffers() { return service.cleanupExpiredOffers(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bmadmin")) return categoryCommand(sender, args);
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the Black Market.");
            return true;
        }
        openMain((Player) sender);
        return true;
    }

    private boolean categoryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("blackmarket.admin.category")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to assign Black Market categories.");
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("category") || !args[1].equalsIgnoreCase("add")) {
            player.sendMessage(ChatColor.RED + "Usage: /bmadmin category add <weapon|armor|playerheads|rares|cheatcode|cosmetics>");
            return true;
        }
        BlackMarketCategory category;
        switch (args[2].toLowerCase(java.util.Locale.ROOT)) {
            case "weapon": category = BlackMarketCategory.WEAPONS; break;
            case "armor": category = BlackMarketCategory.ARMOR; break;
            case "playerheads": category = BlackMarketCategory.PLAYER_HEADS; break;
            case "rares": category = BlackMarketCategory.RARES; break;
            case "cheatcode": category = BlackMarketCategory.CHEAT_CODES; break;
            case "cosmetics": category = BlackMarketCategory.COSMETICS; break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown category.");
                return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Hold an item in your main hand first.");
            return true;
        }
        ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "That item cannot be categorized.");
            return true;
        }
        meta.getPersistentDataContainer().set(categoryKey, org.bukkit.persistence.PersistentDataType.STRING, category.name());
        held.setItemMeta(meta);
        player.sendMessage(ChatColor.GREEN + "Item category set to " + category.name() + ".");
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || !(event.getView().getTopInventory().getHolder() instanceof MarketHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Player player = (Player) event.getWhoClicked();
        MarketHolder holder = (MarketHolder) event.getView().getTopInventory().getHolder();
        if (holder.type == Screen.MAIN) {
            if (event.getRawSlot() == 11) openCategories(player);
            else if (event.getRawSlot() == 15) openMyOffers(player);
        } else if (holder.type == Screen.CATEGORIES) {
            if (event.getRawSlot() == 0) openMain(player);
            else if (event.getRawSlot() >= 10 && event.getRawSlot() <= 16) {
                openBrowse(player, BlackMarketCategory.values()[event.getRawSlot() - 10]);
            }
        } else if (holder.type == Screen.BROWSE) {
            BrowseState state = browseStates.get(player.getUniqueId());
            if (state == null) return;
            if (event.getRawSlot() == 0) openCategories(player);
            else if (event.getRawSlot() == 47) { state.priceDescending = !state.priceDescending; state.timeSort = 0; refreshBrowse(player); }
            else if (event.getRawSlot() == 49) { state.filter = nextFilter(state.filter); refreshBrowse(player); }
            else if (event.getRawSlot() == 51) { state.timeSort = state.timeSort == 1 ? 2 : 1; refreshBrowse(player); }
            else if (isOfferSlot(event.getRawSlot())) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.hasItemMeta()) {
                    String id = clicked.getItemMeta().getPersistentDataContainer().get(holder.key, org.bukkit.persistence.PersistentDataType.STRING);
                    if (id != null) {
                        UUID offerId = UUID.fromString(id);
                        if (service.getOffer(offerId).map(offer -> offer.getType() == BlackMarketOfferType.AUCTION).orElse(false)) {
                            openAuction(player, offerId);
                        } else {
                            openPurchase(player, offerId);
                        }
                    }
                }
            }
        } else if (holder.type == Screen.PURCHASE) {
            if (event.getRawSlot() == 0) {
                BrowseState state = browseStates.get(player.getUniqueId());
                if (state != null) openBrowse(player, state.category);
                else openCategories(player);
            } else if (event.getRawSlot() >= 11 && event.getRawSlot() <= 15) {
                int[] quantities = {1, 3, 5, 10, Integer.MAX_VALUE};
                if (service.getOffer(holder.offerId).map(offer -> offer.getType() == BlackMarketOfferType.AUCTION).orElse(false)) {
                    openAuction(player, holder.offerId);
                } else if (service.getOffer(holder.offerId).map(offer -> offer.getType() == BlackMarketOfferType.BUY).orElse(false)) {
                    fulfillBuyOffer(player, holder.offerId, quantities[event.getRawSlot() - 11]);
                } else {
                    purchase(player, holder.offerId, quantities[event.getRawSlot() - 11]);
                }
            }
        } else if (holder.type == Screen.AUCTION) {
            if (event.getRawSlot() == 0) {
                BrowseState state = browseStates.get(player.getUniqueId());
                if (state != null) openBrowse(player, state.category); else openCategories(player);
            } else if (event.getRawSlot() == 13) {
                openBidInput(player, holder);
            }
        } else if (holder.type == Screen.MY_OFFERS) {
            if (event.getRawSlot() == 0) openMain(player);
            else if (offerSlotRegistry.isOfferSlot(event.getRawSlot())) {
                ItemStack clicked = event.getCurrentItem();
                UUID offerId = clicked == null || !clicked.hasItemMeta() ? null
                        : getOfferId(clicked.getItemMeta());
                if (offerId != null) {
                    claimOffer(player, offerId);
                } else {
                    OptionalInt logicalSlot = offerSlotRegistry.getLogicalSlot(event.getRawSlot());
                    if (logicalSlot.isPresent() && unlocked(player, logicalSlot.getAsInt())
                            && !hasOffer(player, logicalSlot.getAsInt())) {
                        openCreate(player, event.getRawSlot(), logicalSlot.getAsInt(),
                                new MarketHolder(Screen.CREATE, null, null, offerKey));
                    }
                }
            }
        } else if (holder.type == Screen.CREATE) {
            if (event.getRawSlot() == 10) openItemSelection(player, holder);
            else if (event.getRawSlot() == 12) openOfferTypes(player, holder);
            else if (event.getRawSlot() == 14) openNumberInput(player, holder, true);
            else if (event.getRawSlot() == 16) openNumberInput(player, holder, false);
            else if (event.getRawSlot() == 31) openOfferLengths(player, holder);
        } else if (holder.type == Screen.ITEM_SELECTION) {
            if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                holder.selectedItem = event.getCurrentItem().clone();
                openCreate(player, holder.slot, holder);
            }
        } else if (holder.type == Screen.OFFER_TYPES) {
            if (event.getRawSlot() == 11) { holder.offerType = BlackMarketOfferType.BUY; openCreate(player, holder.slot, holder); }
            else if (event.getRawSlot() == 13) { holder.offerType = BlackMarketOfferType.AUCTION; openCreate(player, holder.slot, holder); }
            else if (event.getRawSlot() == 15) { holder.offerType = BlackMarketOfferType.SELL; openCreate(player, holder.slot, holder); }
        } else if (holder.type == Screen.LENGTHS) {
            if (event.getRawSlot() == 0) openCreate(player, holder.slot, holder);
            else if (event.getRawSlot() == 10 || event.getRawSlot() == 12 || event.getRawSlot() == 14 || event.getRawSlot() == 16) {
                int hours = event.getRawSlot() == 10 ? 8 : event.getRawSlot() == 12 ? 16 : event.getRawSlot() == 14 ? 24 : 48;
                createOffer(player, holder, hours);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player && event.getView().getTopInventory().getHolder() instanceof MarketHolder) {
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { browseStates.remove(event.getPlayer().getUniqueId()); }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new MarketHolder(Screen.MAIN, null, null, offerKey), 27, color("&7&lTHE BLACK MARKET"));
        fill(inventory);
        inventory.setItem(11, item(Material.BOOKSHELF, "&e&lBrowser", "&7", "&7Click to browse various", "&7offers from other players.", "&7", "&6&lBlack Market", "&7Number of items on market: &d&l" + service.getActiveOfferCount()));
        inventory.setItem(15, item(material("WRITABLE_BOOK"), "&d&lMy Offers", "&7", "&7Click to view the offers", "&7you have on the", "&eBlack Market."));
        player.openInventory(inventory);
    }

    private void openCategories(Player player) {
        Inventory inventory = Bukkit.createInventory(new MarketHolder(Screen.CATEGORIES, null, null, offerKey), 27, color("&7&lCATEGORIES"));
        fill(inventory);
        inventory.setItem(0, item(Material.REDSTONE, "&c&lBack"));
        BlackMarketCategory[] categories = BlackMarketCategory.values();
        for (int i = 0; i < categories.length; i++) {
            if (!categoryEnabled(categories[i])) continue;
            Material material = material(categories[i].getIcon());
            inventory.setItem(i + 10, item(material, "&" + (i == 1 ? "b" : i == 2 ? "e" : "6") + "&l" + categories[i].getDisplayName(),
                    "&7", "&7Click to browse", "&7various items"));
        }
        player.openInventory(inventory);
    }

    private void openBrowse(Player player, BlackMarketCategory category) {
        BrowseState state = browseStates.computeIfAbsent(player.getUniqueId(), id -> new BrowseState(category));
        state.category = category;
        refreshBrowse(player);
    }

    private void refreshBrowse(Player player) {
        BrowseState state = browseStates.get(player.getUniqueId());
        if (state == null) return;
        BlackMarketSort sort = state.priceDescending ? BlackMarketSort.HIGHEST_PRICE : BlackMarketSort.LOWEST_PRICE;
        if (state.timeSort == 1) sort = BlackMarketSort.LEAST_TIME;
        if (state.timeSort == 2) sort = BlackMarketSort.MOST_TIME;
        Inventory inventory = Bukkit.createInventory(new MarketHolder(Screen.BROWSE, null, state.category, offerKey), 54,
                color("&7&l" + state.category.getDisplayName()));
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, "&7");
        for (int glassSlot : BlackMarketBrowseLayout.BROWSE_GLASS_SLOTS) inventory.setItem(glassSlot, pane.clone());
        inventory.setItem(0, item(Material.REDSTONE, "&c&lBack"));
        inventory.setItem(47, item(state.priceDescending ? Material.GOLD_INGOT : Material.IRON_INGOT,
                state.priceDescending ? "&c&lHighest Price" : "&c&lLowest Price"));
        inventory.setItem(49, item(filterMaterial(state.filter), filterName(state.filter)));
        inventory.setItem(51, item(state.timeSort == 1 ? material("MANGROVE_PROPAGULE") : Material.RED_DYE,
                state.timeSort == 1 ? "&c&lLeast Time Left" : "&c&lMost Time Left"));
        int offerIndex = 0;
        for (BlackMarketOffer offer : service.getOffers(state.category, state.filter, sort)) {
            if (offerIndex >= BlackMarketBrowseLayout.LISTING_SLOTS.size()) break;
            inventory.setItem(BlackMarketBrowseLayout.LISTING_SLOTS.get(offerIndex++), displayOffer(offer));
        }
        player.openInventory(inventory);
    }

    private void openPurchase(Player player, UUID offerId) {
        BlackMarketOffer offer = service.getOffer(offerId).orElse(null);
        if (offer == null) { player.sendMessage(color("&cThis offer is no longer available.")); return; }
        MarketHolder holder = new MarketHolder(Screen.PURCHASE, offerId, null, offerKey);
        boolean buying = offer.getType() == BlackMarketOfferType.BUY;
        Inventory inventory = Bukkit.createInventory(holder, 27, color(buying ? "&7&lSelect Amount" : "&7&lPURCHASE"));
        fill(inventory);
        inventory.setItem(0, item(Material.REDSTONE, "&c&lBack"));
        inventory.setItem(4, displayOffer(offer));
        int[] quantities = {1, 3, 5, 10, offer.getRemainingQuantity()};
        for (int i = 0; i < quantities.length; i++) {
            int quantity = Math.min(quantities[i], offer.getRemainingQuantity());
            inventory.setItem(i + 11, item(i == 4 ? material("WRITABLE_BOOK") : Material.PAPER,
                    buying ? "&aSell &e" + (i == 4 ? "All" : quantity + "x") : "&aPurchase &e" + (i == 4 ? "All" : quantity + "x"),
                    "&7", "&7Cost per: &a$" + offer.getPrice(), "&7Total Cost: &a$" + offer.getPrice().multiply(BigDecimal.valueOf(quantity))));
        }
        player.openInventory(inventory);
    }

    private void openAuction(Player player, UUID offerId) {
        BlackMarketOffer offer = service.getOffer(offerId).orElse(null);
        if (offer == null) {
            player.sendMessage(color("&cThis auction is no longer available."));
            return;
        }
        MarketHolder holder = new MarketHolder(Screen.AUCTION, offerId, null, offerKey);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&7&lAuction"));
        fill(inventory);
        inventory.setItem(0, item(Material.REDSTONE, "&c&lBack"));
        inventory.setItem(4, displayOffer(offer));
        BigDecimal current = offer.getHighestBidder() == null ? offer.getMinimumBid() : offer.getReservedFunds();
        inventory.setItem(13, item(Material.GOLD_INGOT, "&6&lPlace Bid", "&7Minimum bid: &a$" + current,
                "&7Enter an amount greater than the current bid."));
        player.openInventory(inventory);
    }

    private void openBidInput(Player player, MarketHolder state) {
        new AnvilGUI.Builder().plugin(plugin).title("Set your bid").text("null")
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    try {
                        BigDecimal amount = new BigDecimal(snapshot.getText());
                        BidResult result = service.placeBid(player.getUniqueId(), state.offerId, amount);
                        if (result.getStatus() == BidResult.Status.SUCCESS) {
                            player.sendMessage(color("&aBid placed successfully."));
                            openAuction(player, state.offerId);
                            return Collections.singletonList(AnvilGUI.ResponseAction.close());
                        }
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(
                                result.getStatus() == BidResult.Status.INSUFFICIENT_FUNDS ? "Insufficient funds" : "Bid too low"));
                    } catch (NumberFormatException exception) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText("Enter a positive amount"));
                    }
                }).open(player);
    }

    private void purchase(Player player, UUID offerId, int requested) {
        BlackMarketOffer offer = service.getOffer(offerId).orElse(null);
        if (offer == null) return;
        int quantity = Math.min(requested, offer.getRemainingQuantity());
        PurchaseResult result = service.purchase(player.getUniqueId(), offerId, quantity, player.getInventory());
        if (result.getStatus() == PurchaseResult.Status.SUCCESS) {
            player.sendMessage(color("&aPurchase complete."));
            refreshBrowse(player);
        } else player.sendMessage(color("&cPurchase failed: " + result.getStatus().name().toLowerCase().replace('_', ' ') + "."));
    }

    private void fulfillBuyOffer(Player player, UUID offerId, int requested) {
        BlackMarketOffer offer = service.getOffer(offerId).orElse(null);
        if (offer == null) return;
        int quantity = Math.min(requested, offer.getRemainingQuantity());
        PurchaseResult result = service.fulfillBuyOffer(player.getUniqueId(), offerId, quantity, player.getInventory());
        if (result.getStatus() == PurchaseResult.Status.SUCCESS) {
            player.sendMessage(color("&aItems sold to the Buy offer."));
            refreshBrowse(player);
        } else {
            player.sendMessage(color("&cSale failed: " + result.getStatus().name().toLowerCase().replace('_', ' ') + "."));
        }
    }

    private void claimOffer(Player player, UUID offerId) {
        ClaimResult result = service.claimOffer(player.getUniqueId(), offerId, player.getInventory());
        if (result.getStatus() == ClaimResult.Status.SUCCESS) {
            player.sendMessage(color("&aBlack Market assets claimed."));
            openMyOffers(player);
        } else if (result.getStatus() == ClaimResult.Status.INVENTORY_FULL) {
            player.sendMessage(color("&cMake room in your inventory before claiming this offer."));
        } else {
            player.sendMessage(color("&cThis offer is not currently claimable."));
        }
    }

    private void openMyOffers(Player player) {
        Inventory inventory = Bukkit.createInventory(new MarketHolder(Screen.MY_OFFERS, null, null, offerKey), 36, color("&7&lMy Offers"));
        renderMyOffersDecorations(inventory);
        inventory.setItem(0, item(Material.REDSTONE, "&c&lBack"));
        renderMyOfferSlots(player, inventory);
        player.openInventory(inventory);
    }

    private void renderMyOffersDecorations(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, "&7");
        for (int slot : BlackMarketBrowseLayout.MY_OFFERS_GLASS_SLOTS) inventory.setItem(slot, pane.clone());
    }

    private void renderMyOfferSlots(Player player, Inventory inventory) {
        Map<Integer, BlackMarketOffer> offersBySlot = offersByLogicalSlot(player);
        for (int logicalOfferSlot = 1; logicalOfferSlot <= offerSlotRegistry.getMaximumLogicalSlot(); logicalOfferSlot++) {
            int physicalSlot = offerSlotRegistry.getPhysicalSlot(logicalOfferSlot).getAsInt();
            BlackMarketOffer offer = offersBySlot.get(logicalOfferSlot);
            if (offer != null) {
                inventory.setItem(physicalSlot, ("EXPIRED".equals(offer.getStatus()) || "CLAIMABLE".equals(offer.getStatus()))
                        ? displayClaim(offer) : displayOffer(offer));
            } else {
                if (unlocked(player, logicalOfferSlot)) inventory.setItem(physicalSlot, item(Material.GRAY_DYE, "&3&lEmpty Slot", "&7", "&7Slot Number: " + logicalOfferSlot, "&7", "&aClick to make a new offer!"));
                else inventory.setItem(physicalSlot, item(Material.BARRIER, "&c&lLocked Slot", "&7Unlock with Level "
                        + offerSlotPermissionService.getRequiredCheatCodeLevel(player)
                        + " of the Black Market Cheat Code"));
            }
        }
    }

    private void openCreate(Player player, int slot) {
        OptionalInt logicalSlot = offerSlotRegistry.getLogicalSlot(slot);
        if (logicalSlot.isPresent()) openCreate(player, slot, logicalSlot.getAsInt(), new MarketHolder(Screen.CREATE, null, null, offerKey));
    }

    private void openCreate(Player player, int slot, MarketHolder holder) {
        OptionalInt logicalSlot = offerSlotRegistry.getLogicalSlot(slot);
        if (logicalSlot.isPresent()) openCreate(player, slot, logicalSlot.getAsInt(), holder);
    }

    private void openCreate(Player player, int slot, int logicalOfferSlot, MarketHolder holder) {
        holder.type = Screen.CREATE;
        holder.slot = slot;
        holder.logicalOfferSlot = logicalOfferSlot;
        Inventory inventory = Bukkit.createInventory(holder, 45, color("&7&lCreate Offer"));
        inventory.setItem(10, holder.selectedItem == null
                ? item(Material.REDSTONE_BLOCK, "&cChange Item", "&7Choose Item for this offer!") : holder.selectedItem.clone());
        inventory.setItem(12, item(holder.offerType == null ? Material.REDSTONE_BLOCK
                        : holder.offerType == BlackMarketOfferType.BUY ? material("GREEN_BANNER")
                        : holder.offerType == BlackMarketOfferType.AUCTION ? Material.YELLOW_BANNER : Material.RED_BANNER,
                "&cChange Offer Type", "&7Types: BUY, SELL, or AUCTION"));
        inventory.setItem(14, item(holder.quantity > 0 ? material("WHEAT_SEEDS") : Material.REDSTONE_BLOCK, "&bChange Amount",
                holder.quantity > 0 ? "&fAmount to trade: " + holder.quantity : "&7Click to set the quantity."));
        inventory.setItem(16, item(holder.price.signum() > 0 ? Material.PAPER : Material.REDSTONE_BLOCK,
                "&eChange Price (Each)", holder.price.signum() > 0 ? "&fPrice: " + holder.price : "&7Click to set the price."));
        inventory.setItem(31, item(Material.CRAFTING_TABLE, "&a&lCreate Offer!", "&9Make stuff", "&7Create your offer!"));
        player.openInventory(inventory);
    }

    private void openItemSelection(Player player, MarketHolder state) {
        MarketHolder holder = new MarketHolder(Screen.ITEM_SELECTION, null, null, offerKey);
        holder.slot = state.slot;
        holder.logicalOfferSlot = state.logicalOfferSlot;
        holder.selectedItem = state.selectedItem;
        holder.offerType = state.offerType;
        holder.quantity = state.quantity;
        holder.price = state.price;
        Inventory inventory = Bukkit.createInventory(holder, 36, color("&7&lCreate Offer"));
        int slot = 1;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir() || isProtectedSelectionItem(stack) || slot >= inventory.getSize()) continue;
            inventory.setItem(slot++, stack.clone());
        }
        player.openInventory(inventory);
    }

    private void openOfferTypes(Player player, MarketHolder state) {
        MarketHolder holder = copyState(Screen.OFFER_TYPES, state);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&7&lSelect Auction Type"));
        inventory.setItem(11, item(Material.GREEN_WOOL, "&b&lBUY", "&7Click this if you want to buy!"));
        inventory.setItem(13, item(Material.YELLOW_WOOL, "&6&lAUCTION", "&7Players bid against each other."));
        inventory.setItem(15, item(Material.RED_WOOL, "&c&lSELL", "&7Click this if you want to sell!"));
        player.openInventory(inventory);
    }

    private void openNumberInput(Player player, MarketHolder state, boolean quantity) {
        new AnvilGUI.Builder().plugin(plugin).title(quantity ? "Set the quantity" : "Set the per price")
                .text(quantity ? String.valueOf(Math.max(1, state.quantity)) : state.price.toPlainString())
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    try {
                        if (quantity) {
                            int value = Integer.parseInt(snapshot.getText());
                            if (value <= 0) throw new NumberFormatException();
                            state.quantity = value;
                        } else {
                            BigDecimal value = new BigDecimal(snapshot.getText());
                            if (value.signum() <= 0) throw new NumberFormatException();
                            state.price = value;
                        }
                        openCreate(player, state.slot, state);
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    } catch (NumberFormatException exception) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText("Enter a positive number"));
                    }
                }).open(player);
    }

    private void openOfferLengths(Player player, MarketHolder state) {
        MarketHolder holder = copyState(Screen.LENGTHS, state);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&7&lChoose Offer Length"));
        int[] slots = {10, 12, 14, 16};
        List<Integer> durations = configuredDurations();
        for (int i = 0; i < slots.length; i++) {
            int hours = durations.get(i);
            inventory.setItem(slots[i], item(Material.BOOK, "&6&l" + hours + " Hours",
                    "&7Duration: &e" + hours + "h", "&7Total Tax: &a$" + taxFor(state, hours)));
        }
        inventory.setItem(0, item(Material.REDSTONE, "&c&lBack"));
        player.openInventory(inventory);
    }

    private void createOffer(Player player, MarketHolder state, int hours) {
        if (state.selectedItem == null || state.offerType == null || state.quantity <= 0 || state.price.signum() <= 0) {
            player.sendMessage(color("&cSelect an item, type, positive quantity, and positive price first."));
            openCreate(player, state.slot, state);
            return;
        }
        BigDecimal tax = taxFor(state, hours);
        if (!economy.has(player.getUniqueId(), tax) || !economy.withdraw(player.getUniqueId(), tax)) {
            player.sendMessage(color("&cYou cannot afford the marketplace tax."));
            return;
        }
        BlackMarketOffer offer = service.createOffer(player.getUniqueId(), state.selectedItem, state.quantity, state.price,
                categoryDetector.detect(state.selectedItem), state.offerType, Duration.ofHours(hours), state.logicalOfferSlot,
                player.getInventory());
        if (offer == null) {
            economy.deposit(player.getUniqueId(), tax);
            player.sendMessage(color("&cThe offer could not be created. Check your items and balance."));
            return;
        }
        player.sendMessage(color("&aBlack Market offer created."));
        openMyOffers(player);
    }

    private List<Integer> configuredDurations() {
        List<Integer> values = plugin.getConfig().getIntegerList("black-market.durations-hours");
        if (values.size() >= 4) return values.subList(0, 4);
        return Arrays.asList(8, 16, 24, 48);
    }

    private BigDecimal taxFor(MarketHolder state, int hours) {
        BigDecimal value = state.price.multiply(BigDecimal.valueOf(state.quantity));
        String path = "black-market.durations." + hours + "h.tax";
        if (plugin.getConfig().contains(path)) {
            double configured = plugin.getConfig().getDouble(path, 0.0D);
            String mode = plugin.getConfig().getString("black-market.durations." + hours + "h.tax-mode", "percent");
            if ("fixed".equalsIgnoreCase(mode)) return BigDecimal.valueOf(configured);
            return value.multiply(BigDecimal.valueOf(configured)).divide(BigDecimal.valueOf(100));
        }
        double percent = plugin.getConfig().getDouble("black-market.tax-percent", 0.0D);
        return value.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100));
    }

    private boolean categoryEnabled(BlackMarketCategory category) {
        String key = switch (category) {
            case WEAPONS -> "weapon";
            case ARMOR -> "armor";
            case PLAYER_HEADS -> "player-head";
            case RARES -> "rare";
            case CHEAT_CODES -> "cheat-code";
            case COSMETICS -> "cosmetics";
            case MISC -> "misc";
        };
        return plugin.getConfig().getBoolean("black-market.categories." + key, true);
    }

    private MarketHolder copyState(Screen type, MarketHolder source) {
        MarketHolder holder = new MarketHolder(type, null, null, offerKey);
        holder.slot = source.slot;
        holder.logicalOfferSlot = source.logicalOfferSlot;
        holder.selectedItem = source.selectedItem;
        holder.offerType = source.offerType;
        holder.quantity = source.quantity;
        holder.price = source.price;
        return holder;
    }

    private boolean unlocked(Player player, int slot) {
        return offerSlotPermissionService.isSlotUnlocked(player, slot);
    }

    private boolean hasOffer(Player player, int logicalOfferSlot) {
        return logicalOfferSlot > 0 && offersByLogicalSlot(player).containsKey(logicalOfferSlot);
    }

    private Map<Integer, BlackMarketOffer> offersByLogicalSlot(Player player) {
        List<BlackMarketOffer> offers = service.getOffers(player.getUniqueId());
        Map<Integer, BlackMarketOffer> result = new HashMap<>();
        List<BlackMarketOffer> legacyOffers = new ArrayList<>();
        for (BlackMarketOffer offer : offers) {
            Integer logicalSlot = offer.getLogicalOfferSlot();
            if (player.getUniqueId().equals(offer.getSeller()) && logicalSlot != null
                    && logicalSlot >= 1 && logicalSlot <= offerSlotRegistry.getMaximumLogicalSlot()) {
                result.put(logicalSlot, offer);
            } else {
                legacyOffers.add(offer);
            }
        }
        for (BlackMarketOffer offer : legacyOffers) {
            for (int logicalSlot = 1; logicalSlot <= offerSlotRegistry.getMaximumLogicalSlot(); logicalSlot++) {
                if (!result.containsKey(logicalSlot)) {
                    result.put(logicalSlot, offer);
                    break;
                }
            }
        }
        return result;
    }

    private UUID getOfferId(ItemMeta meta) {
        if (meta == null) return null;
        String value = meta.getPersistentDataContainer().get(offerKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isProtectedSelectionItem(ItemStack stack) {
        String materialName = stack.getType().name();
        return "CHEST".equals(materialName) || "CLOCK".equals(materialName) || "COMPASS".equals(materialName);
    }

    private ItemStack displayOffer(BlackMarketOffer offer) {
        ItemStack display = offer.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;
        String name = meta.hasDisplayName() ? meta.getDisplayName() : display.getType().name();
        boolean buying = offer.getType() == BlackMarketOfferType.BUY;
        boolean auction = offer.getType() == BlackMarketOfferType.AUCTION;
        meta.setDisplayName(color((buying ? "&a&lBuying " : auction ? "&6&lAuction " : "&c&lSelling ") + name));
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(color("&7"));
        lore.add(color(buying ? "&a&lBuying" : auction ? "&6&lAuction" : "&c&lSelling"));
        lore.add(color("&7Item #" + offer.getCategory().name() + "_" + offer.getId().toString().substring(0, 8)));
        lore.add(color("&7Quantity: &e" + offer.getRemainingQuantity()));
        lore.add(color("&7Price (each): &a$" + offer.getPrice()));
        if (auction) lore.add(color("&7Highest bid: &a$" + (offer.getHighestBidder() == null ? offer.getMinimumBid() : offer.getReservedFunds())));
        lore.add(color("&7Expires: &a" + duration(offer.getExpiresAt() - System.currentTimeMillis())));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(offerKey, org.bukkit.persistence.PersistentDataType.STRING, offer.getId().toString());
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack displayClaim(BlackMarketOffer offer) {
        ItemStack claim = item(Material.CHEST, "&a&lClaim", "&7Offer has finished.", "&aClick to claim available assets.");
        ItemMeta meta = claim.getItemMeta();
        meta.getPersistentDataContainer().set(offerKey, org.bukkit.persistence.PersistentDataType.STRING,
                offer.getId().toString());
        claim.setItemMeta(meta);
        return claim;
    }

    private void fill(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, "&7");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, pane);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Arrays.stream(lore).map(this::color).collect(java.util.stream.Collectors.toList()));
        stack.setItemMeta(meta);
        return stack;
    }

    private Material material(String value) {
        Material material = Material.matchMaterial(value);
        return material == null ? Material.STONE : material;
    }

    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private boolean isOfferSlot(int slot) {
        return BlackMarketBrowseLayout.LISTING_SLOTS.contains(slot);
    }
    private String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
    }
    private Material filterMaterial(BlackMarketFilter filter) { return filter == BlackMarketFilter.BUY ? Material.GREEN_WOOL : filter == BlackMarketFilter.SELL ? Material.RED_WOOL : filter == BlackMarketFilter.AUCTION ? Material.YELLOW_WOOL : Material.WHITE_WOOL; }
    private String filterName(BlackMarketFilter filter) { return filter == BlackMarketFilter.BUY ? "&c&lBuy Offers Only" : filter == BlackMarketFilter.SELL ? "&c&lSell Offers Only" : filter == BlackMarketFilter.AUCTION ? "&6&lAuction Only" : "&c&lNo Filter"; }
    private BlackMarketFilter nextFilter(BlackMarketFilter filter) { return BlackMarketFilter.values()[(filter.ordinal() + 1) % BlackMarketFilter.values().length]; }

    @Override
    public void close() { if (cleanupTask != -1) Bukkit.getScheduler().cancelTask(cleanupTask); }

    private enum Screen { MAIN, CATEGORIES, BROWSE, PURCHASE, AUCTION, MY_OFFERS, CREATE, ITEM_SELECTION, OFFER_TYPES, LENGTHS }
    private static final class BrowseState {
        private BlackMarketCategory category;
        private BlackMarketFilter filter = BlackMarketFilter.ALL;
        private boolean priceDescending;
        private int timeSort;
        private BrowseState(BlackMarketCategory category) { this.category = category; }
    }
    private static final class MarketHolder implements InventoryHolder {
        private Screen type;
        private final UUID offerId;
        private final BlackMarketCategory category;
        private final org.bukkit.NamespacedKey key;
        private int slot;
        private int logicalOfferSlot;
        private ItemStack selectedItem;
        private BlackMarketOfferType offerType;
        private int quantity;
        private BigDecimal price = BigDecimal.ZERO;
        private MarketHolder(Screen type, UUID offerId, BlackMarketCategory category, org.bukkit.NamespacedKey key) { this.type = type; this.offerId = offerId; this.category = category; this.key = key; }
        @Override public Inventory getInventory() { return null; }
    }
}