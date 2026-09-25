package com.districtx.pacificacore.market;

import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.storage.BlackMarketRepository;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BlackMarketServiceImpl implements BlackMarketService {
    private final JavaPlugin plugin;
    private final BlackMarketRepository repository;
    private final EconomyService economy;
    private final ItemMatcher itemMatcher;
    private final BlackMarketCategoryDetector categoryDetector;
    private final BlackMarketOfferSlotRegistry offerSlotRegistry = new BlackMarketOfferSlotRegistry();

    public BlackMarketServiceImpl(JavaPlugin plugin, BlackMarketRepository repository, EconomyService economy) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.itemMatcher = new ItemMatcher(plugin);
        this.categoryDetector = new BlackMarketCategoryDetector(plugin);
    }

    @Override
    public synchronized BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                                       BlackMarketCategory category, BlackMarketOfferType type,
                                                       Duration duration) {
        return createOffer(seller, item, quantity, price, category, type, duration, (Integer) null);
    }

    @Override
    public synchronized BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                                       BlackMarketCategory category, BlackMarketOfferType type,
                                                       Duration duration, int logicalOfferSlot) {
        return createOffer(seller, item, quantity, price, category, type, duration, Integer.valueOf(logicalOfferSlot));
    }

    private BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                         BlackMarketCategory category, BlackMarketOfferType type,
                                         Duration duration, Integer logicalOfferSlot) {
        if (seller == null || item == null || item.getType().isAir() || quantity <= 0 || price == null
                || price.signum() <= 0 || category == null || type == null || duration == null
                || duration.isZero() || duration.isNegative()
                || (logicalOfferSlot != null && (logicalOfferSlot < 1
                || logicalOfferSlot > offerSlotRegistry.getMaximumLogicalSlot()))) return null;
        if (logicalOfferSlot != null && isLogicalSlotOccupied(seller, repository.findSeller(seller), logicalOfferSlot)) return null;
        ItemStack stored = item.clone();
        long created = System.currentTimeMillis();
        BigDecimal reserved = type == BlackMarketOfferType.BUY
                ? price.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        if (type == BlackMarketOfferType.BUY && !economy.withdraw(seller, reserved)) return null;
        BlackMarketOffer offer = new BlackMarketOffer(UUID.randomUUID(), seller, category, type, stored,
                quantity, quantity, price, created, created + duration.toMillis(), 0, reserved, price, null,
                "ACTIVE", 0, logicalOfferSlot);
        if (repository.create(offer)) return offer;
        if (type == BlackMarketOfferType.BUY && !economy.deposit(seller, reserved)) {
            plugin.getLogger().severe("Could not refund failed Black Market buy-offer escrow for " + seller + ".");
        }
        return null;
    }

    @Override
    public synchronized BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                                       BlackMarketCategory category, BlackMarketOfferType type,
                                                       Duration duration, Inventory escrowInventory) {
        if (type == BlackMarketOfferType.BUY) {
            return createOffer(seller, item, quantity, price, category, type, duration);
        }
        if (escrowInventory == null || itemMatcher.countMatchingItems(escrowInventory, item) < quantity) return null;
        ItemStack[] before = cloneContents(escrowInventory.getContents());
        if (itemMatcher.removeMatchingItems(escrowInventory, item, quantity) != quantity) {
            escrowInventory.setContents(before);
            return null;
        }
        BlackMarketOffer offer = createOffer(seller, item, quantity, price, category, type, duration);
        if (offer == null) escrowInventory.setContents(before);
        return offer;
    }

    @Override
    public synchronized BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                                       BlackMarketCategory category, BlackMarketOfferType type,
                                                       Duration duration, int logicalOfferSlot, Inventory escrowInventory) {
        if (type == BlackMarketOfferType.BUY) {
            return createOffer(seller, item, quantity, price, category, type, duration, logicalOfferSlot);
        }
        if (escrowInventory == null || itemMatcher.countMatchingItems(escrowInventory, item) < quantity) return null;
        ItemStack[] before = cloneContents(escrowInventory.getContents());
        if (itemMatcher.removeMatchingItems(escrowInventory, item, quantity) != quantity) {
            escrowInventory.setContents(before);
            return null;
        }
        BlackMarketOffer offer = createOffer(seller, item, quantity, price, category, type, duration, logicalOfferSlot);
        if (offer == null) escrowInventory.setContents(before);
        return offer;
    }

    public synchronized BlackMarketOffer createOffer(UUID owner, ItemStack item, int quantity, BigDecimal price,
                                                       BlackMarketOfferType type, Duration duration) {
        return createOffer(owner, item, quantity, price, categoryDetector.detect(item), type, duration);
    }

    @Override
    public synchronized Optional<BlackMarketOffer> getOffer(UUID offerId) {
        return Optional.ofNullable(offerId == null ? null : repository.find(offerId));
    }

    @Override
    public synchronized List<BlackMarketOffer> getOffers(BlackMarketCategory category, BlackMarketFilter filter,
                                                          BlackMarketSort sort) {
        return repository.findActive(category, filter, sort, System.currentTimeMillis());
    }

    @Override
    public synchronized List<BlackMarketOffer> getOffers(UUID seller) {
        return repository.findSeller(seller);
    }

    @Override
    public synchronized PurchaseResult purchase(UUID buyer, UUID offerId, int quantity, Inventory inventory) {
        if (buyer == null || offerId == null || inventory == null || quantity <= 0) {
            return PurchaseResult.of(PurchaseResult.Status.INVALID_QUANTITY);
        }
        BlackMarketOffer offer = repository.find(offerId);
        if (offer == null) return PurchaseResult.of(PurchaseResult.Status.NOT_FOUND);
        if (buyer.equals(offer.getSeller())) return PurchaseResult.of(PurchaseResult.Status.OWNER_TRANSACTION);
        if (offer.getType() != BlackMarketOfferType.SELL) {
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        if (offer.getExpiresAt() <= System.currentTimeMillis()) {
            repository.expire(System.currentTimeMillis());
            return PurchaseResult.of(PurchaseResult.Status.EXPIRED);
        }
        if (quantity > offer.getRemainingQuantity()) {
            return PurchaseResult.of(PurchaseResult.Status.INSUFFICIENT_STOCK);
        }
        BigDecimal total = offer.getPrice().multiply(BigDecimal.valueOf(quantity));
        if (!economy.has(buyer, total)) return PurchaseResult.of(PurchaseResult.Status.INSUFFICIENT_FUNDS);
        ItemStack item = offer.getItem().clone();
        item.setAmount(quantity);
        if (!canReceive(inventory, item)) return PurchaseResult.of(PurchaseResult.Status.INVENTORY_FULL);
        ItemStack[] before = cloneContents(inventory.getContents());
        if (!economy.withdraw(buyer, total)) return PurchaseResult.of(PurchaseResult.Status.INSUFFICIENT_FUNDS);

        int updated = offer.getRemainingQuantity() - quantity;
        if (!repository.changeRemaining(offer.getId(), offer.getRemainingQuantity(), updated)) {
            economy.deposit(buyer, total);
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        if (!inventory.addItem(item).isEmpty()) {
            inventory.setContents(before);
            repository.changeRemaining(offer.getId(), updated, offer.getRemainingQuantity());
            if (!economy.deposit(buyer, total)) {
                plugin.getLogger().severe("Could not refund failed Black Market purchase for " + buyer + ".");
            }
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        if (!economy.deposit(offer.getSeller(), total)) {
            plugin.getLogger().severe("Could not credit Black Market seller " + offer.getSeller() + ".");
            inventory.setContents(before);
            repository.changeRemaining(offer.getId(), updated, offer.getRemainingQuantity());
            if (!economy.deposit(buyer, total)) {
                plugin.getLogger().severe("Could not refund Black Market buyer " + buyer + ".");
            }
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        return PurchaseResult.success(offer, quantity);
    }

    @Override
    public synchronized PurchaseResult fulfillBuyOffer(UUID seller, UUID offerId, int quantity, Inventory inventory) {
        if (seller == null || offerId == null || inventory == null || quantity <= 0) {
            return PurchaseResult.of(PurchaseResult.Status.INVALID_QUANTITY);
        }
        BlackMarketOffer offer = repository.find(offerId);
        if (offer == null) return PurchaseResult.of(PurchaseResult.Status.NOT_FOUND);
        if (seller.equals(offer.getSeller())) return PurchaseResult.of(PurchaseResult.Status.OWNER_TRANSACTION);
        if (offer.getType() != BlackMarketOfferType.BUY) return PurchaseResult.of(PurchaseResult.Status.FAILED);
        if (offer.getExpiresAt() <= System.currentTimeMillis()) {
            repository.expire(System.currentTimeMillis());
            return PurchaseResult.of(PurchaseResult.Status.EXPIRED);
        }
        int available = itemMatcher.countMatchingItems(inventory, offer.getItem());
        int actual = Math.min(quantity, Math.min(available, offer.getRemainingQuantity()));
        if (actual <= 0) return PurchaseResult.of(PurchaseResult.Status.INSUFFICIENT_STOCK);
        BigDecimal total = offer.getPrice().multiply(BigDecimal.valueOf(actual));
        ItemStack[] before = cloneContents(inventory.getContents());
        ItemStack fulfilledItem = itemMatcher.firstMatchingItem(inventory, offer.getItem());
        if (fulfilledItem == null) return PurchaseResult.of(PurchaseResult.Status.FAILED);
        fulfilledItem.setAmount(actual);
        int removed = itemMatcher.removeMatchingItems(inventory, offer.getItem(), actual);
        if (removed != actual) {
            inventory.setContents(before);
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        int updated = offer.getRemainingQuantity() - actual;
        BigDecimal funds = offer.getReservedFunds().subtract(total);
        if (!repository.changeBuyState(offer.getId(), offer.getRemainingQuantity(), updated,
                offer.getReservedFunds(), funds)) {
            inventory.setContents(before);
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        if (!repository.addBuyEscrow(offer.getId(), fulfilledItem, actual)) {
            repository.changeBuyState(offer.getId(), updated, offer.getRemainingQuantity(), funds, offer.getReservedFunds());
            inventory.setContents(before);
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        if (!economy.deposit(seller, total)) {
            repository.clearBuyEscrow(offer.getId());
            repository.changeBuyState(offer.getId(), updated, offer.getRemainingQuantity(), funds, offer.getReservedFunds());
            inventory.setContents(before);
            return PurchaseResult.of(PurchaseResult.Status.FAILED);
        }
        return PurchaseResult.success(offer, actual);
    }

    @Override
    public synchronized BidResult placeBid(UUID bidder, UUID offerId, BigDecimal amount) {
        if (bidder == null || offerId == null || amount == null || amount.signum() <= 0) {
            return BidResult.of(BidResult.Status.INVALID_AMOUNT);
        }
        BlackMarketOffer offer = repository.find(offerId);
        if (offer == null || offer.getType() != BlackMarketOfferType.AUCTION) {
            return BidResult.of(BidResult.Status.NOT_FOUND);
        }
        if (bidder.equals(offer.getSeller())) return BidResult.of(BidResult.Status.OWNER_TRANSACTION);
        if (offer.getExpiresAt() <= System.currentTimeMillis()) {
            cleanupExpiredOffers();
            return BidResult.of(BidResult.Status.EXPIRED);
        }
        BigDecimal minimum = offer.getHighestBidder() == null ? offer.getMinimumBid() : offer.getReservedFunds();
        if (amount.compareTo(minimum) < 0 || (offer.getHighestBidder() != null && amount.compareTo(minimum) <= 0)) {
            return BidResult.of(BidResult.Status.NOT_HIGH_ENOUGH);
        }
        boolean sameBidder = bidder.equals(offer.getHighestBidder());
        BigDecimal required = sameBidder ? amount.subtract(offer.getReservedFunds()) : amount;
        if (!economy.withdraw(bidder, required)) return BidResult.of(BidResult.Status.INSUFFICIENT_FUNDS);
        if (!repository.placeBid(offerId, offer.getRemainingQuantity(), offer.getReservedFunds(),
                offer.getHighestBidder(), bidder, amount)) {
            economy.deposit(bidder, required);
            return BidResult.of(BidResult.Status.FAILED);
        }
        if (offer.getHighestBidder() != null && offer.getReservedFunds().signum() > 0
                && !sameBidder) {
            refundBid(offer.getHighestBidder(), offer.getReservedFunds());
        }
        return BidResult.success(amount);
    }

    @Override
    public synchronized List<BlackMarketBid> getBidHistory(UUID offerId) {
        return offerId == null ? java.util.Collections.emptyList() : repository.findBids(offerId);
    }

    @Override
    public synchronized ClaimResult claimOffer(UUID claimant, UUID offerId, Inventory inventory) {
        if (claimant == null || offerId == null || inventory == null) return ClaimResult.of(ClaimResult.Status.FAILED);
        BlackMarketOffer offer = repository.findAny(offerId);
        if (offer == null || !("EXPIRED".equals(offer.getStatus()) || "CLAIMABLE".equals(offer.getStatus())
                || "CANCELLED".equals(offer.getStatus()))) {
            return ClaimResult.of(ClaimResult.Status.NOT_CLAIMABLE);
        }
        boolean auctionHasWinner = offer.getType() == BlackMarketOfferType.AUCTION && offer.getHighestBidder() != null;
        boolean owner = claimant.equals(offer.getSeller())
                && (!auctionHasWinner || "CANCELLED".equals(offer.getStatus()));
        boolean auctionWinner = offer.getType() == BlackMarketOfferType.AUCTION
                && offer.getHighestBidder() != null && claimant.equals(offer.getHighestBidder())
                && !"CANCELLED".equals(offer.getStatus());
        if (!owner && !auctionWinner) return ClaimResult.of(ClaimResult.Status.NOT_CLAIMABLE);
        int quantity;
        BlackMarketEscrow buyEscrow = offer.getType() == BlackMarketOfferType.BUY
                ? repository.findBuyEscrow(offer.getId()) : null;
        if (offer.getType() == BlackMarketOfferType.BUY) quantity = buyEscrow == null ? 0 : buyEscrow.getQuantity();
        else if (offer.getType() == BlackMarketOfferType.SELL) quantity = offer.getRemainingQuantity();
        else quantity = offer.getHighestBidder() == null ? offer.getOriginalQuantity() : offer.getOriginalQuantity();
        quantity -= offer.getClaimedQuantity();
        if (quantity <= 0) return ClaimResult.of(ClaimResult.Status.NOT_CLAIMABLE);
        ItemStack item = copy(buyEscrow == null ? offer.getItem() : buyEscrow.getItem(), quantity);
        if (!canReceive(inventory, item)) return ClaimResult.of(ClaimResult.Status.INVENTORY_FULL);
        ItemStack[] before = cloneContents(inventory.getContents());
        if (!inventory.addItem(item).isEmpty()) return ClaimResult.of(ClaimResult.Status.INVENTORY_FULL);
        if (!repository.claim(offer.getId(), offer.getClaimedQuantity(), offer.getClaimedQuantity() + quantity)) {
            inventory.setContents(before);
            return ClaimResult.of(ClaimResult.Status.FAILED);
        }
        if (offer.getType() == BlackMarketOfferType.BUY) repository.clearBuyEscrow(offer.getId());
        return ClaimResult.success(item, quantity, BigDecimal.ZERO);
    }

    @Override
    public synchronized boolean cancelOffer(UUID seller, UUID offerId) {
        BlackMarketOffer offer = repository.find(offerId);
        if (offer == null || !seller.equals(offer.getSeller())) return false;
        if (!repository.setStatus(offerId, seller, "CANCELLED")) return false;
        if (offer.getType() == BlackMarketOfferType.BUY && offer.getReservedFunds().signum() > 0) {
            if (!economy.deposit(seller, offer.getReservedFunds())) {
                plugin.getLogger().severe("Could not return cancelled Black Market escrow for " + seller + ".");
            }
        } else if (offer.getType() == BlackMarketOfferType.AUCTION && offer.getHighestBidder() != null) {
            refundBid(offer.getHighestBidder(), offer.getReservedFunds());
        }
        return true;
    }

    @Override
    public synchronized int cleanupExpiredOffers() {
        long now = System.currentTimeMillis();
        List<BlackMarketOffer> expiredBuys = repository.expireBuyOffers(now);
        for (BlackMarketOffer offer : expiredBuys) {
            if (offer.getReservedFunds().signum() > 0 && !economy.deposit(offer.getSeller(), offer.getReservedFunds())) {
                plugin.getLogger().severe("Could not return expired Black Market escrow for " + offer.getSeller() + ".");
            }
        }
        List<BlackMarketOffer> expired = repository.expireAuctions(now);
        for (BlackMarketOffer offer : expired) settleAuction(offer);
        return expiredBuys.size() + expired.size() + repository.expire(now);
    }

    private void settleAuction(BlackMarketOffer offer) {
        if (offer.getHighestBidder() != null) {
            if (!economy.deposit(offer.getSeller(), offer.getReservedFunds())) {
                plugin.getLogger().severe("Could not pay auction seller " + offer.getSeller() + ".");
            }
            BigDecimal refund = offer.getReservedFunds().multiply(BigDecimal.valueOf(
                    plugin.getConfig().getDouble("black-market.auction.losing-bid-refund-percent", 70.0D)))
                    .divide(BigDecimal.valueOf(100));
            for (BlackMarketBid bid : repository.findBids(offer.getId())) {
                if (!bid.getBidder().equals(offer.getHighestBidder()) && !bid.isRefunded()
                        && repository.markBidRefunded(bid.getId())) {
                    refundBid(bid.getBidder(), bid.getAmount());
                }
            }
        }
    }

    private void refundBid(UUID bidder, BigDecimal amount) {
        BigDecimal refund = amount.multiply(BigDecimal.valueOf(
                plugin.getConfig().getDouble("black-market.auction.losing-bid-refund-percent", 70.0D)))
                .divide(BigDecimal.valueOf(100));
        if (!economy.deposit(bidder, refund)) {
            plugin.getLogger().severe("Could not refund Black Market bid for " + bidder + ".");
        }
    }

    @Override
    public synchronized int getActiveOfferCount() {
        return repository.countActive(System.currentTimeMillis());
    }

    private boolean canReceive(Inventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= item.getMaxStackSize();
            } else if (existing.isSimilar(item)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    private ItemStack copy(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    private boolean isLogicalSlotOccupied(UUID seller, List<BlackMarketOffer> offers, int requestedSlot) {
        Set<Integer> occupied = new HashSet<>();
        for (BlackMarketOffer offer : offers) {
            if (!seller.equals(offer.getSeller())) continue;
            Integer logicalSlot = offer.getLogicalOfferSlot();
            if (logicalSlot != null && logicalSlot >= 1
                    && logicalSlot <= offerSlotRegistry.getMaximumLogicalSlot()) occupied.add(logicalSlot);
        }
        for (BlackMarketOffer offer : offers) {
            if (!seller.equals(offer.getSeller())) continue;
            if (offer.getLogicalOfferSlot() != null) continue;
            for (int logicalSlot = 1; logicalSlot <= offerSlotRegistry.getMaximumLogicalSlot(); logicalSlot++) {
                if (!occupied.contains(logicalSlot)) {
                    occupied.add(logicalSlot);
                    break;
                }
            }
        }
        return occupied.contains(requestedSlot);
    }
}