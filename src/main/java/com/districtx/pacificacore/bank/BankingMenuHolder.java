package com.districtx.pacificacore.bank;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

final class BankingMenuHolder implements InventoryHolder {
    enum Screen { MAIN, AMOUNT, RECIPIENTS }

    final Screen screen;
    final UUID owner;
    final BankingGuiManager.Flow flow;
    final BigDecimal amount;
    final int page;
    final List<BankingGuiManager.PlayerEntry> players;
    private Inventory inventory;

    BankingMenuHolder(Screen screen, UUID owner, BankingGuiManager.Flow flow, BigDecimal amount,
                      int page, List<BankingGuiManager.PlayerEntry> players) {
        this.screen = screen;
        this.owner = owner;
        this.flow = flow;
        this.amount = amount;
        this.page = page;
        this.players = players;
    }

    void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}