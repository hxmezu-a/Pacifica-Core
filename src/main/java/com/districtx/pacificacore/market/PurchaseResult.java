package com.districtx.pacificacore.market;

public final class PurchaseResult {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        EXPIRED,
        INVALID_QUANTITY,
        INSUFFICIENT_STOCK,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        OWNER_TRANSACTION,
        FAILED
    }

    private final Status status;
    private final BlackMarketOffer offer;
    private final int quantity;

    private PurchaseResult(Status status, BlackMarketOffer offer, int quantity) {
        this.status = status;
        this.offer = offer;
        this.quantity = quantity;
    }

    public static PurchaseResult of(Status status) { return new PurchaseResult(status, null, 0); }
    public static PurchaseResult success(BlackMarketOffer offer, int quantity) {
        return new PurchaseResult(Status.SUCCESS, offer, quantity);
    }
    public Status getStatus() { return status; }
    public BlackMarketOffer getOffer() { return offer; }
    public int getQuantity() { return quantity; }
}