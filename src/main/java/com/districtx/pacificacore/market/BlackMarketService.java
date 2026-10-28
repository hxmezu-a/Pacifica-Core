package com.districtx.pacificacore.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlackMarketService {
    BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                 BlackMarketCategory category, BlackMarketOfferType type, Duration duration);
    BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                 BlackMarketCategory category, BlackMarketOfferType type, Duration duration,
                                 int logicalOfferSlot);
    BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                 BlackMarketCategory category, BlackMarketOfferType type, Duration duration,
                                 Inventory escrowInventory);
    BlackMarketOffer createOffer(UUID seller, ItemStack item, int quantity, BigDecimal price,
                                 BlackMarketCategory category, BlackMarketOfferType type, Duration duration,
                                 int logicalOfferSlot, Inventory escrowInventory);
    Optional<BlackMarketOffer> getOffer(UUID offerId);
    List<BlackMarketOffer> getOffers(BlackMarketCategory category, BlackMarketFilter filter, BlackMarketSort sort);
    List<BlackMarketOffer> getOffers(UUID seller);
    PurchaseResult purchase(UUID buyer, UUID offerId, int quantity, Inventory inventory);
    PurchaseResult fulfillBuyOffer(UUID seller, UUID offerId, int quantity, Inventory inventory);
    BidResult placeBid(UUID bidder, UUID offerId, BigDecimal amount);
    List<BlackMarketBid> getBidHistory(UUID offerId);
    ClaimResult claimOffer(UUID claimant, UUID offerId, Inventory inventory);
    boolean cancelOffer(UUID seller, UUID offerId);
    int cleanupExpiredOffers();
    int getActiveOfferCount();
}