package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class CurrencyStorage {
    private final JavaPlugin plugin;
    private final PlayerBalanceRepository repository;
    private final Map<UUID, BigDecimal> diamonds = new HashMap<>();
    private final Map<UUID, BigDecimal> balances = new HashMap<>();

    public CurrencyStorage(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.repository = new PlayerBalanceRepository(plugin, database);
    }

    public synchronized void load() {
        diamonds.clear();
        balances.clear();
        diamonds.putAll(repository.loadDiamonds());
        balances.putAll(repository.loadBalances());
    }

    public synchronized BigDecimal getDiamond(UUID player) {
        return diamonds.getOrDefault(player, BigDecimal.ZERO);
    }

    public synchronized BigDecimal getBalance(UUID player) {
        return balances.getOrDefault(player, BigDecimal.ZERO);
    }

    public synchronized boolean hasDiamond(UUID player, BigDecimal amount) {
        return validAmount(amount) && getDiamond(player).compareTo(amount) >= 0;
    }

    public synchronized boolean hasBalance(UUID player, BigDecimal amount) {
        return validAmount(amount) && getBalance(player).compareTo(amount) >= 0;
    }

    public synchronized boolean depositDiamond(UUID player, BigDecimal amount) {
        return change(diamonds, player, amount, true);
    }

    public synchronized boolean depositBalance(UUID player, BigDecimal amount) {
        return change(balances, player, amount, true);
    }

    public synchronized boolean withdrawDiamond(UUID player, BigDecimal amount) {
        return change(diamonds, player, amount, false);
    }

    public synchronized boolean withdrawBalance(UUID player, BigDecimal amount) {
        return change(balances, player, amount, false);
    }

    public synchronized boolean setDiamond(UUID player, BigDecimal amount) {
        if (!validPlayer(player) || !validAmount(amount) || !repository.setDiamond(player, amount)) return false;
        diamonds.put(player, amount);
        return true;
    }

    public synchronized boolean setBalance(UUID player, BigDecimal amount) {
        if (!validPlayer(player) || !validAmount(amount) || !repository.setBalance(player, amount)) return false;
        balances.put(player, amount);
        return true;
    }

    public synchronized boolean resetDiamond(UUID player) {
        return setDiamond(player, BigDecimal.ZERO);
    }

    public synchronized boolean resetBalance(UUID player) {
        return setBalance(player, BigDecimal.ZERO);
    }

    public synchronized boolean transfer(UUID from, UUID to, BigDecimal diamondAmount, BigDecimal balanceAmount) {
        if (!validPlayer(from) || !validPlayer(to) || !validAmount(diamondAmount)
                || !validAmount(balanceAmount) || !hasDiamond(from, diamondAmount)
                || !hasBalance(from, balanceAmount)
                || !repository.transfer(from, to, diamondAmount, balanceAmount)) return false;
        if (!from.equals(to)) {
            diamonds.put(from, getDiamond(from).subtract(diamondAmount));
            diamonds.put(to, getDiamond(to).add(diamondAmount));
            balances.put(from, getBalance(from).subtract(balanceAmount));
            balances.put(to, getBalance(to).add(balanceAmount));
        }
        return true;
    }

    public synchronized boolean process(UUID player, BigDecimal diamondAmount, BigDecimal balanceAmount,
                                        BooleanSupplier action) {
        if (!validPlayer(player) || !validAmount(diamondAmount) || !validAmount(balanceAmount)
                || action == null || !hasDiamond(player, diamondAmount) || !hasBalance(player, balanceAmount)) {
            return false;
        }
        BigDecimal oldDiamonds = getDiamond(player);
        BigDecimal oldBalance = getBalance(player);
        if (!repository.setBoth(player, oldDiamonds.subtract(diamondAmount), oldBalance.subtract(balanceAmount))) {
            return false;
        }
        diamonds.put(player, oldDiamonds.subtract(diamondAmount));
        balances.put(player, oldBalance.subtract(balanceAmount));
        boolean committed;
        try {
            committed = action.getAsBoolean();
        } catch (RuntimeException exception) {
            committed = false;
        }
        if (committed) return true;
        if (!repository.setBoth(player, oldDiamonds, oldBalance)) {
            plugin.getLogger().severe("Could not roll back a failed multi-currency transaction for " + player + ".");
        }
        diamonds.put(player, oldDiamonds);
        balances.put(player, oldBalance);
        return false;
    }

    public synchronized boolean importDiamondIfAbsent(UUID player, BigDecimal amount) {
        if (!validPlayer(player) || !validAmount(amount) || diamonds.containsKey(player)) return true;
        if (!repository.importDiamondIfAbsent(player, amount)) return false;
        diamonds.put(player, repository.getDiamond(player));
        return true;
    }

    public synchronized boolean importBalanceIfAbsent(UUID player, BigDecimal amount) {
        if (!validPlayer(player) || !validAmount(amount) || balances.containsKey(player)) return true;
        if (!repository.importBalanceIfAbsent(player, amount)) return false;
        balances.put(player, repository.getBalance(player));
        return true;
    }

    private boolean change(Map<UUID, BigDecimal> currency, UUID player, BigDecimal amount, boolean deposit) {
        if (!validPlayer(player) || !validAmount(amount)) return false;
        BigDecimal previous = currency.getOrDefault(player, BigDecimal.ZERO);
        if (!deposit && previous.compareTo(amount) < 0) return false;
        BigDecimal updated = deposit ? previous.add(amount) : previous.subtract(amount);
        boolean saved = currency == diamonds ? repository.setDiamond(player, updated) : repository.setBalance(player, updated);
        if (saved) currency.put(player, updated);
        return saved;
    }

    private boolean validAmount(BigDecimal amount) {
        return amount != null && amount.signum() >= 0 && amount.scale() <= 8;
    }

    private boolean validPlayer(UUID player) {
        return player != null;
    }
}