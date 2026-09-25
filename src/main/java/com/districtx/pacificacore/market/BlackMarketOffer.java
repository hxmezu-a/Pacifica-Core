package com.districtx.pacificacore.market;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;

public final class BlackMarketOffer {
    private final UUID id;
    private final UUID seller;
    private final BlackMarketCategory category;
    private final BlackMarketOfferType type;
    private final ItemStack item;
    private final int originalQuantity;
    private final int remainingQuantity;
    private final BigDecimal price;
    private final long createdAt;
    private final long expiresAt;
    private final int fulfilledQuantity;
    private final BigDecimal reservedFunds;
    private final BigDecimal minimumBid;
    private final UUID highestBidder;
    private final String status;
    private final int claimedQuantity;
    private final Integer logicalOfferSlot;

    public BlackMarketOffer(UUID id, UUID seller, BlackMarketCategory category, BlackMarketOfferType type,
                            ItemStack item, int originalQuantity, int remainingQuantity, BigDecimal price,
                            long createdAt, long expiresAt) {
        this(id, seller, category, type, item, originalQuantity, remainingQuantity, price, createdAt, expiresAt,
                originalQuantity - remainingQuantity, BigDecimal.ZERO, price, null, "ACTIVE", 0, null);
    }

    public BlackMarketOffer(UUID id, UUID seller, BlackMarketCategory category, BlackMarketOfferType type,
                            ItemStack item, int originalQuantity, int remainingQuantity, BigDecimal price,
                            long createdAt, long expiresAt, int fulfilledQuantity, BigDecimal reservedFunds,
                            BigDecimal minimumBid, UUID highestBidder, String status) {
        this(id, seller, category, type, item, originalQuantity, remainingQuantity, price, createdAt, expiresAt,
                fulfilledQuantity, reservedFunds, minimumBid, highestBidder, status, 0, null);
    }

    public BlackMarketOffer(UUID id, UUID seller, BlackMarketCategory category, BlackMarketOfferType type,
                            ItemStack item, int originalQuantity, int remainingQuantity, BigDecimal price,
                            long createdAt, long expiresAt, int fulfilledQuantity, BigDecimal reservedFunds,
                            BigDecimal minimumBid, UUID highestBidder, String status, int claimedQuantity) {
        this(id, seller, category, type, item, originalQuantity, remainingQuantity, price, createdAt, expiresAt,
                fulfilledQuantity, reservedFunds, minimumBid, highestBidder, status, claimedQuantity, null);
    }

    public BlackMarketOffer(UUID id, UUID seller, BlackMarketCategory category, BlackMarketOfferType type,
                            ItemStack item, int originalQuantity, int remainingQuantity, BigDecimal price,
                            long createdAt, long expiresAt, int fulfilledQuantity, BigDecimal reservedFunds,
                            BigDecimal minimumBid, UUID highestBidder, String status, int claimedQuantity,
                            Integer logicalOfferSlot) {
        this.id = id;
        this.seller = seller;
        this.category = category;
        this.type = type;
        this.item = item;
        this.originalQuantity = originalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.fulfilledQuantity = fulfilledQuantity;
        this.reservedFunds = reservedFunds;
        this.minimumBid = minimumBid;
        this.highestBidder = highestBidder;
        this.status = status;
        this.claimedQuantity = claimedQuantity;
        this.logicalOfferSlot = logicalOfferSlot;
    }

    public UUID getId() { return id; }
    public UUID getSeller() { return seller; }
    public BlackMarketCategory getCategory() { return category; }
    public BlackMarketOfferType getType() { return type; }
    public ItemStack getItem() { return item; }
    public int getOriginalQuantity() { return originalQuantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public BigDecimal getPrice() { return price; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public int getFulfilledQuantity() { return fulfilledQuantity; }
    public BigDecimal getReservedFunds() { return reservedFunds; }
    public BigDecimal getMinimumBid() { return minimumBid; }
    public UUID getHighestBidder() { return highestBidder; }
    public String getStatus() { return status; }
    public int getClaimedQuantity() { return claimedQuantity; }
    public Integer getLogicalOfferSlot() { return logicalOfferSlot; }
}