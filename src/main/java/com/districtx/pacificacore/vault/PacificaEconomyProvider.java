package com.districtx.pacificacore.vault;

import com.districtx.pacificacore.api.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class PacificaEconomyProvider implements Economy {
    private final EconomyService service;

    public PacificaEconomyProvider(EconomyService service) {
        this.service = service;
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "Pacifica-Core"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return String.format("$%,.2f", amount); }
    @Override public String currencyNamePlural() { return "Dollars"; }
    @Override public String currencyNameSingular() { return "Dollar"; }
    @Override public boolean hasAccount(OfflinePlayer player) { return player != null && player.getUniqueId() != null; }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return hasAccount(player); }
    @Override public boolean hasAccount(String playerName) { return playerName != null && hasAccount(org.bukkit.Bukkit.getOfflinePlayer(playerName)); }
    @Override public boolean hasAccount(String playerName, String world) { return hasAccount(playerName); }
    @Override public double getBalance(OfflinePlayer player) { return amount(service.getBalance(id(player))); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public double getBalance(String playerName) { return getBalance(org.bukkit.Bukkit.getOfflinePlayer(playerName)); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public boolean has(OfflinePlayer player, double amount) {
        return valid(amount) && service.has(id(player), BigDecimal.valueOf(amount));
    }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }
    @Override public boolean has(String playerName, double amount) { return has(org.bukkit.Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public boolean has(String playerName, String world, double amount) { return has(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!valid(amount)) return failure(amount, "Amount must be finite and non-negative.");
        boolean success = service.withdraw(id(player), BigDecimal.valueOf(amount));
        return response(player, amount, success, "Insufficient funds or storage failure.");
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(org.bukkit.Bukkit.getOfflinePlayer(playerName), amount);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        return withdrawPlayer(playerName, amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!valid(amount)) return failure(amount, "Amount must be finite and non-negative.");
        boolean success = service.deposit(id(player), BigDecimal.valueOf(amount));
        return response(player, amount, success, "Could not save the deposit.");
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(org.bukkit.Bukkit.getOfflinePlayer(playerName), amount);
    }
    @Override public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        return depositPlayer(playerName, amount);
    }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return player != null; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return createPlayerAccount(player); }
    @Override public boolean createPlayerAccount(String playerName) {
        return playerName != null && createPlayerAccount(org.bukkit.Bukkit.getOfflinePlayer(playerName));
    }
    @Override public boolean createPlayerAccount(String playerName, String world) {
        return createPlayerAccount(playerName);
    }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse createBank(String name, String playerName) { return unsupported(); }
    @Override public EconomyResponse deleteBank(String name) { return unsupported(); }
    @Override public EconomyResponse bankBalance(String name) { return unsupported(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return unsupported(); }
    @Override public List<String> getBanks() { return java.util.Collections.emptyList(); }

    private UUID id(OfflinePlayer player) { return player == null ? null : player.getUniqueId(); }

    private double amount(BigDecimal value) { return value == null ? 0 : value.doubleValue(); }

    private boolean valid(double value) { return Double.isFinite(value) && value >= 0; }

    private EconomyResponse response(OfflinePlayer player, double amount, boolean success, String error) {
        return new EconomyResponse(amount, getBalance(player),
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                success ? null : error);
    }

    private EconomyResponse failure(double amount, String error) {
        return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, error);
    }

    private EconomyResponse unsupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }
}