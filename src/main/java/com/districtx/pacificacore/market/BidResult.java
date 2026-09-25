package com.districtx.pacificacore.market;

import java.math.BigDecimal;

public final class BidResult {
    public enum Status {
        SUCCESS, NOT_FOUND, EXPIRED, INVALID_AMOUNT, INSUFFICIENT_FUNDS, NOT_HIGH_ENOUGH, OWNER_TRANSACTION, FAILED
    }

    private final Status status;
    private final BigDecimal amount;

    private BidResult(Status status, BigDecimal amount) {
        this.status = status;
        this.amount = amount;
    }

    public static BidResult of(Status status) { return new BidResult(status, null); }
    public static BidResult success(BigDecimal amount) { return new BidResult(Status.SUCCESS, amount); }
    public Status getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
}