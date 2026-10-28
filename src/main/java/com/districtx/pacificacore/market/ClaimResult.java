package com.districtx.pacificacore.market;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public final class ClaimResult {
    public enum Status { SUCCESS, NOT_FOUND, NOT_CLAIMABLE, INVENTORY_FULL, FAILED }

    private final Status status;
    private final ItemStack item;
    private final int quantity;
    private final BigDecimal funds;

    private ClaimResult(Status status, ItemStack item, int quantity, BigDecimal funds) {
        this.status = status;
        this.item = item;
        this.quantity = quantity;
        this.funds = funds;
    }

    public static ClaimResult of(Status status) { return new ClaimResult(status, null, 0, BigDecimal.ZERO); }
    public static ClaimResult success(ItemStack item, int quantity, BigDecimal funds) {
        return new ClaimResult(Status.SUCCESS, item, quantity, funds);
    }
    public Status getStatus() { return status; }
    public ItemStack getItem() { return item; }
    public int getQuantity() { return quantity; }
    public BigDecimal getFunds() { return funds; }
}