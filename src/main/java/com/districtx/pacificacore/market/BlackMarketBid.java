package com.districtx.pacificacore.market;

import java.math.BigDecimal;
import java.util.UUID;

public final class BlackMarketBid {
    private final UUID id;
    private final UUID offerId;
    private final UUID bidder;
    private final BigDecimal amount;
    private final long createdAt;
    private final boolean refunded;

    public BlackMarketBid(UUID id, UUID offerId, UUID bidder, BigDecimal amount, long createdAt, boolean refunded) {
        this.id = id;
        this.offerId = offerId;
        this.bidder = bidder;
        this.amount = amount;
        this.createdAt = createdAt;
        this.refunded = refunded;
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public UUID getBidder() { return bidder; }
    public BigDecimal getAmount() { return amount; }
    public long getCreatedAt() { return createdAt; }
    public boolean isRefunded() { return refunded; }
}