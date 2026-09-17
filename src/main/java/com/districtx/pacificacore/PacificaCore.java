package com.districtx.pacificacore;

import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.api.PacificaCoreAPI;
import com.districtx.pacificacore.api.PacificaCoreAPIImpl;
import com.districtx.pacificacore.api.TransactionService;
import com.districtx.pacificacore.economy.DiamondCurrencyServiceImpl;
import com.districtx.pacificacore.economy.EconomyServiceImpl;
import com.districtx.pacificacore.economy.TransactionServiceImpl;
import com.districtx.pacificacore.storage.CurrencyStorage;
import com.districtx.pacificacore.storage.DatabaseManager;
import com.districtx.pacificacore.vault.PacificaEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PacificaCore extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static PacificaCore instance;
    private PacificaCoreAPI api;
    private DatabaseManager database;
    private CurrencyStorage storage;
    private FileConfigurationBridge messages;

    public static PacificaCore getInstance() { return instance; }
    public static PacificaCoreAPI getAPI() { return instance == null ? null : instance.api; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new FileConfigurationBridge(this);
        database = new DatabaseManager(this);
        try {
            database.initialize();
        } catch (java.sql.SQLException exception) {
            getLogger().severe("Could not initialize Pacifica-Core databases: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        storage = new CurrencyStorage(this, database);
        storage.load();
        migrateYamlCurrency();
        DiamondCurrencyService diamonds = new DiamondCurrencyServiceImpl(storage);
        EconomyService economy = new EconomyServiceImpl(storage);
        TransactionService transactions = new TransactionServiceImpl(storage);
        api = new PacificaCoreAPIImpl(diamonds, economy, transactions);
        getServer().getServicesManager().register(DiamondCurrencyService.class, diamonds, this, ServicePriority.Normal);
        getServer().getServicesManager().register(EconomyService.class, economy, this, ServicePriority.Normal);
        getServer().getServicesManager().register(TransactionService.class, transactions, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PacificaCoreAPI.class, api, this, ServicePriority.Normal);
        getServer().getServicesManager().register(Economy.class, new PacificaEconomyProvider(economy), this,
                ServicePriority.Highest);
        getCommand("diamond").setExecutor(this);
        getCommand("bal").setExecutor(this);
        getCommand("core").setExecutor(this);
        getCommand("core").setTabCompleter(this);
        getLogger().info("Pacifica-Core is ready with Diamond Currency and Vault economy services.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(this);
        if (database != null) database.close();
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("diamond")) {
            if (!(sender instanceof Player player)) return tell(sender, "players-only");
            if (!sender.hasPermission("pacifica.core.command.diamond")) return tell(sender, "no-permission");
            return send(sender, "balance-diamond", "amount", format(api.getDiamondCurrencyService().getBalance(player.getUniqueId())));
        }
        if (command.getName().equalsIgnoreCase("bal")) return balance(sender, args);
        if (label.equalsIgnoreCase("dmdadmin")) return diamondAdminCommand(sender, args);
        return coreCommand(sender, args);
    }

    private boolean diamondAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pacifica.core.admin.reload")) return tell(sender, "no-permission");
            reloadConfig();
            messages.reload();
            return tell(sender, "reloaded");
        }
        if (args.length < 2 || args.length > 3) return tell(sender, "usage");
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!Arrays.asList("give", "remove", "set", "reset").contains(action)) return tell(sender, "usage");
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) return tell(sender, "player-not-found");
        BigDecimal amount = BigDecimal.ZERO;
        if (!action.equals("reset")) {
            if (args.length != 3) return tell(sender, "usage");
            try {
                amount = new BigDecimal(args[2]);
            } catch (NumberFormatException exception) {
                return tell(sender, "invalid-amount");
            }
            if (amount.signum() < 0 || amount.scale() > 8) return tell(sender, "invalid-amount");
        } else if (args.length != 2) return tell(sender, "usage");
        String permission = "pacifica.core.admin." + (action.equals("remove") ? "get" : action.equals("give") ? "dgive" : action);
        if (!sender.hasPermission(permission)) return tell(sender, "no-permission");
        boolean success = action.equals("give")
                ? api.getDiamondCurrencyService().deposit(target.getUniqueId(), amount)
                : action.equals("remove")
                ? api.getDiamondCurrencyService().withdraw(target.getUniqueId(), amount)
                : action.equals("set")
                ? api.getDiamondCurrencyService().setBalance(target.getUniqueId(), amount)
                : api.getDiamondCurrencyService().resetBalance(target.getUniqueId());
        if (!success) return tell(sender, "invalid-amount");
        return send(sender, action.equals("reset") ? "reset" : action.equals("set") ? "set"
                        : action.equals("remove") ? "removed" : "updated",
                "player", target.getName() == null ? args[1] : target.getName(), "currency", "diamond",
                "amount", format(amount));
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pacifica.core.command.balance")) return tell(sender, "no-permission");
        OfflinePlayer target = sender instanceof Player player ? player : null;
        if (args.length == 1) {
            if (!sender.hasPermission("pacifica.core.command.balance.others")) return tell(sender, "no-permission");
            target = findPlayer(args[0]);
            if (target == null) return tell(sender, "player-not-found");
        }
        if (target == null) return tell(sender, "players-only");
        String key = args.length == 1 ? "balance-other" : "balance-money";
        return send(sender, key, "player", target.getName() == null ? args[0] : target.getName(),
                "amount", format(api.getEconomyService().getBalance(target.getUniqueId())));
    }

    private boolean coreCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pacifica.core.admin.reload")) return tell(sender, "no-permission");
            reloadConfig();
            messages.reload();
            return tell(sender, "reloaded");
        }
        if (args.length != 4 && args.length != 3) return tell(sender, "usage");
        String action = args[0].toLowerCase(Locale.ROOT);
        String type;
        String playerName;
        String amountText = null;
        String permission;
        if (action.equals("dgive") || action.equals("bgive")) {
            if (args.length != 3) return tell(sender, "usage");
            type = action.equals("dgive") ? "diamond" : "balance";
            playerName = args[1]; amountText = args[2]; permission = "pacifica.core.admin." + action;
            action = "give";
        } else {
            if (action.equals("reset")) {
                if (args.length != 3) return tell(sender, "usage");
                type = args[1].toLowerCase(Locale.ROOT); playerName = args[2];
            } else {
                if (args.length != 4) return tell(sender, "usage");
                type = args[1].toLowerCase(Locale.ROOT); playerName = args[2]; amountText = args[3];
            }
            permission = "pacifica.core.admin." + (args[0].equalsIgnoreCase("get") ? "get" : args[0].toLowerCase(Locale.ROOT));
            action = args[0].toLowerCase(Locale.ROOT);
        }
        if (!sender.hasPermission(permission)) return tell(sender, "no-permission");
        if (!type.equals("diamond") && !type.equals("balance")) return tell(sender, "invalid-currency");
        OfflinePlayer target = findPlayer(playerName);
        if (target == null) return tell(sender, "player-not-found");
        BigDecimal amount = BigDecimal.ZERO;
        if (!action.equals("reset")) {
            try { amount = new BigDecimal(amountText); } catch (NumberFormatException exception) { return tell(sender, "invalid-amount"); }
            if (amount.signum() < 0 || amount.scale() > 8) return tell(sender, "invalid-amount");
        }
        boolean success;
        if (action.equals("give")) success = mutate(type, target, amount, true, false, false);
        else if (action.equals("get")) success = mutate(type, target, amount, false, true, false);
        else if (action.equals("set")) success = mutate(type, target, amount, false, false, true);
        else if (action.equals("reset")) success = reset(type, target);
        else return tell(sender, "usage");
        if (!success) return tell(sender, "invalid-amount");
        return send(sender, action.equals("reset") ? "reset" : action.equals("set") ? "set" : action.equals("get") ? "removed" : "updated",
                "player", playerName, "currency", type, "amount", format(amount));
    }

    private boolean mutate(String type, OfflinePlayer player, BigDecimal amount,
                           boolean deposit, boolean withdraw, boolean set) {
        if (type.equals("diamond")) {
            if (deposit) return api.getDiamondCurrencyService().deposit(player.getUniqueId(), amount);
            if (withdraw) return api.getDiamondCurrencyService().withdraw(player.getUniqueId(), amount);
            return set && api.getDiamondCurrencyService().setBalance(player.getUniqueId(), amount);
        }
        if (deposit) return api.getEconomyService().deposit(player.getUniqueId(), amount);
        if (withdraw) return api.getEconomyService().withdraw(player.getUniqueId(), amount);
        return set && api.getEconomyService().setBalance(player.getUniqueId(), amount);
    }

    private boolean reset(String type, OfflinePlayer player) {
        return type.equals("diamond")
                ? api.getDiamondCurrencyService().resetBalance(player.getUniqueId())
                : api.getEconomyService().resetBalance(player.getUniqueId());
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private boolean tell(CommandSender sender, String key) { return send(sender, key); }

    private boolean send(CommandSender sender, String key, String... values) {
        String message = messages.get(key);
        for (int i = 0; i + 1 < values.length; i += 2) message = message.replace("%" + values[i] + "%", values[i + 1]);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        return true;
    }

    private String format(BigDecimal amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    private void migrateYamlCurrency() {
        migrateYamlFile(new File(getDataFolder(), "balance.yml"), new File(getDataFolder(), ".balance-yaml-migrated"));
        migrateYamlFile(new File(getDataFolder(), "balances.yml"), new File(getDataFolder(), ".balances-yaml-migrated"));
        File legacy = new File(getDataFolder().getParentFile(), "Pacifica-Currency/balances.yml");
        migrateYamlFile(legacy, new File(getDataFolder(), ".legacy-diamond-migrated"));
    }

    private void migrateYamlFile(File file, File marker) {
        if (marker.isFile() || !file.isFile()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean successful = migrateSection(yaml.getConfigurationSection("diamonds"), true);
        successful &= migrateSection(yaml.getConfigurationSection("balances"), false);
        if (!successful) {
            getLogger().warning("Database migration from " + file.getName() + " was not completed; it will be retried.");
            return;
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) throw new IOException("directory unavailable");
            if (!marker.createNewFile() && !marker.isFile()) throw new IOException("marker unavailable");
            getLogger().info("Migrated currency data from " + file.getName() + ".");
        } catch (IOException exception) {
            getLogger().warning("Could not mark " + file.getName() + " as migrated: " + exception.getMessage());
        }
    }

    private boolean migrateSection(org.bukkit.configuration.ConfigurationSection section, boolean diamonds) {
        if (section == null) return true;
        boolean successful = true;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                BigDecimal amount = new BigDecimal(section.getString(key, "0"));
                if (amount.signum() < 0 || amount.scale() > 8) throw new IllegalArgumentException("invalid amount");
                boolean imported = diamonds ? storage.importDiamondIfAbsent(uuid, amount)
                        : storage.importBalanceIfAbsent(uuid, amount);
                if (!imported) successful = false;
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Ignoring invalid " + (diamonds ? "Diamond Currency" : "balance")
                        + " entry: " + key);
            }
        }
        return successful;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], Arrays.asList("reload", "dgive", "bgive", "reset", "get", "set"));
        if (args.length == 2 && !args[0].equalsIgnoreCase("dgive") && !args[0].equalsIgnoreCase("bgive")) {
            return partial(args[1], Arrays.asList("diamond", "balance"));
        }
        return Collections.emptyList();
    }

    private List<String> partial(String input, List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.startsWith(input.toLowerCase(Locale.ROOT))) result.add(value);
        return result;
    }

    private static final class FileConfigurationBridge {
        private final PacificaCore plugin;
        private org.bukkit.configuration.file.FileConfiguration config;

        private FileConfigurationBridge(PacificaCore plugin) { this.plugin = plugin; reload(); }
        private void reload() { config = plugin.getConfig(); }
        private String get(String key) { return config.getString("messages." + key, key); }
    }
}