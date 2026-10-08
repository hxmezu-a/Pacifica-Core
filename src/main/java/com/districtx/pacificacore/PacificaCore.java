package com.districtx.pacificacore;

import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.api.AdvancementNotificationService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.api.PacificaCoreAPI;
import com.districtx.pacificacore.api.PacificaCoreAPIImpl;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.LevelRewardService;
import com.districtx.pacificacore.api.DailyExperienceService;
import com.districtx.pacificacore.api.PrestigeService;
import com.districtx.pacificacore.api.RankExperienceBonusService;
import com.districtx.pacificacore.api.LevelMenuService;
import com.districtx.pacificacore.api.TransactionService;
import com.districtx.pacificacore.api.ShopAccessService;
import com.districtx.pacificacore.api.SpawnShopService;
import com.districtx.pacificacore.api.SpawnShopNpcLinkService;
import com.districtx.pacificacore.api.NpcLinkService;
import com.districtx.pacificacore.api.SpawnShopSlotService;
import com.districtx.pacificacore.command.SpawnShopCommand;
import com.districtx.pacificacore.command.SpawnShopAdminCommand;
import com.districtx.pacificacore.command.AdminCommand;
import com.districtx.pacificacore.command.CoreAdminCommand;
import com.districtx.pacificacore.command.CurrencyAdminSubCommand;
import com.districtx.pacificacore.shop.SpawnShopManager;
import com.districtx.pacificacore.shop.SpawnShopGuiManager;
import com.districtx.pacificacore.command.PlayerLevelCommand;
import com.districtx.pacificacore.economy.DiamondCurrencyServiceImpl;
import com.districtx.pacificacore.economy.EconomyServiceImpl;
import com.districtx.pacificacore.economy.TransactionServiceImpl;
import com.districtx.pacificacore.storage.CurrencyStorage;
import com.districtx.pacificacore.storage.DatabaseManager;
import com.districtx.pacificacore.storage.PlayerLevelManager;
import com.districtx.pacificacore.storage.DailyExperienceRepository;
import com.districtx.pacificacore.storage.PrestigeRepository;
import com.districtx.pacificacore.storage.NpcLinkManager;
import com.districtx.pacificacore.storage.SpawnShopNpcLinkManager;
import com.districtx.pacificacore.storage.SpawnShopRepository;
import com.districtx.pacificacore.level.reward.LevelRewardManager;
import com.districtx.pacificacore.level.DailyExperienceManager;
import com.districtx.pacificacore.level.PrestigeManager;
import com.districtx.pacificacore.level.gui.LevelMenuManager;
import com.districtx.pacificacore.integration.LuckPermsRankProvider;
import com.districtx.pacificacore.integration.RankExperienceBonusManager;
import com.districtx.pacificacore.integration.LootSystemIntegration;
import com.districtx.pacificacore.integration.PacificaCombatTagIntegration;
import com.districtx.pacificacore.level.PlayerLevelListener;
import com.districtx.pacificacore.level.AdvancementNotificationManager;
import com.districtx.pacificacore.market.BlackMarketManager;
import com.districtx.pacificacore.market.BlackMarketService;
import com.districtx.pacificacore.vault.PacificaEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
import java.util.List;
import java.util.Locale;

public final class PacificaCore extends JavaPlugin implements CommandExecutor {
    private static PacificaCore instance;
    private PacificaCoreAPI api;
    private DatabaseManager database;
    private CurrencyStorage storage;
    private FileConfigurationBridge messages;
    private FileConfiguration levelConfig;
    private FileConfiguration levelRewardsConfig;
    private BlackMarketManager blackMarket;
    private PlayerLevelManager playerLevels;
    private LevelRewardManager levelRewards;
    private DailyExperienceManager dailyExperience;
    private PrestigeManager prestige;
    private RankExperienceBonusManager rankBonuses;
    private LevelMenuManager levelMenus;
    private AdvancementNotificationManager advancementNotifications;
    private LootSystemIntegration lootSystemIntegration;
    private Object playerLevelPlaceholderExpansion;
    private NpcLinkManager npcLinks;
    private SpawnShopNpcLinkManager spawnShopNpcLinks;
    private SpawnShopManager spawnShops;

    public static PacificaCore getInstance() { return instance; }
    public static PacificaCoreAPI getAPI() { return instance == null ? null : instance.api; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResourceIfMissing("level.yml");
        saveResourceIfMissing("level-rewards.yml");
        reloadLevelConfigurations();
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
        playerLevels = new PlayerLevelManager(this, database);
        DiamondCurrencyService diamonds = new DiamondCurrencyServiceImpl(storage);
        EconomyService economy = new EconomyServiceImpl(storage);
        spawnShops = new SpawnShopManager(this, new SpawnShopRepository(this, database), playerLevels,
                new PacificaEconomyProvider(economy));
        SpawnShopGuiManager spawnShopGuis = new SpawnShopGuiManager(this, spawnShops);
        spawnShops.setGuiManager(spawnShopGuis);
        getServer().getPluginManager().registerEvents(spawnShopGuis, this);
        npcLinks = new NpcLinkManager(this, database);
        spawnShopNpcLinks = new SpawnShopNpcLinkManager(this, database, npcLinks);
        TransactionService transactions = new TransactionServiceImpl(storage);
        levelRewards = new LevelRewardManager(this, database, playerLevels, economy, diamonds);
        playerLevels.setRewardService(levelRewards);
        rankBonuses = new RankExperienceBonusManager(this, new LuckPermsRankProvider(this));
        playerLevels.setRankExperienceBonusService(rankBonuses);
        dailyExperience = new DailyExperienceManager(this, playerLevels, economy,
                new DailyExperienceRepository(this, database));
        prestige = new PrestigeManager(this, playerLevels, new PrestigeRepository(this, database));
        levelMenus = new LevelMenuManager(this, playerLevels, levelRewards, dailyExperience, prestige, rankBonuses);
        playerLevels.setLevelMenuService(levelMenus);
        advancementNotifications = new AdvancementNotificationManager(this);
        getServer().getPluginManager().registerEvents(advancementNotifications, this);
        api = new PacificaCoreAPIImpl(diamonds, economy, transactions, playerLevels, levelRewards,
                dailyExperience, prestige, rankBonuses, levelMenus, advancementNotifications, npcLinks);
        blackMarket = new BlackMarketManager(this, database, economy);
        getServer().getPluginManager().registerEvents(blackMarket, this);
        getServer().getPluginManager().registerEvents(levelMenus, this);
        getServer().getPluginManager().registerEvents(levelMenus.getPrestigeMenu(), this);
        getServer().getServicesManager().register(LevelRewardService.class, levelRewards, this, ServicePriority.Normal);
        getServer().getServicesManager().register(DailyExperienceService.class, dailyExperience, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PrestigeService.class, prestige, this, ServicePriority.Normal);
        getServer().getServicesManager().register(RankExperienceBonusService.class, rankBonuses, this, ServicePriority.Normal);
        getServer().getServicesManager().register(LevelMenuService.class, levelMenus, this, ServicePriority.Normal);
        getServer().getServicesManager().register(AdvancementNotificationService.class, advancementNotifications,
                this, ServicePriority.Normal);
        getServer().getServicesManager().register(DiamondCurrencyService.class, diamonds, this, ServicePriority.Normal);
        getServer().getServicesManager().register(EconomyService.class, economy, this, ServicePriority.Normal);
        getServer().getServicesManager().register(TransactionService.class, transactions, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PlayerLevelService.class, playerLevels, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PacificaCoreAPI.class, api, this, ServicePriority.Normal);
        getServer().getServicesManager().register(SpawnShopService.class, spawnShops, this, ServicePriority.Normal);
        getServer().getServicesManager().register(NpcLinkService.class, npcLinks, this, ServicePriority.Normal);
        getServer().getServicesManager().register(SpawnShopNpcLinkService.class, spawnShopNpcLinks, this, ServicePriority.Normal);
        getServer().getServicesManager().register(SpawnShopSlotService.class, spawnShops, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ShopAccessService.class, spawnShops, this, ServicePriority.Normal);
        getServer().getServicesManager().register(BlackMarketService.class, blackMarket.getService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(Economy.class, new PacificaEconomyProvider(economy), this,
                ServicePriority.Highest);
        getCommand("diamond").setExecutor(this);
        getCommand("bal").setExecutor(this);
        getCommand("bm").setExecutor(blackMarket);
        getCommand("spawnshop").setExecutor(new SpawnShopCommand(this, spawnShopGuis));
        SpawnShopAdminCommand spawnShopAdminCommand = new SpawnShopAdminCommand(this, spawnShops,
                npcLinks, spawnShopNpcLinks);
        PlayerLevelCommand levelCommand = new PlayerLevelCommand(this);
        getCommand("level").setExecutor(levelCommand);
        AdminCommand adminCommand = new AdminCommand(List.of(blackMarket, spawnShopAdminCommand,
                new CoreAdminCommand(this), new CurrencyAdminSubCommand(this, "diamonds", true),
                new CurrencyAdminSubCommand(this, "balance", false), levelCommand));
        getCommand("admin").setExecutor(adminCommand);
        getCommand("admin").setTabCompleter(adminCommand);
        blackMarket.scheduleExpiredOfferCleanup();
        PacificaCombatTagIntegration combatTagIntegration = new PacificaCombatTagIntegration(this);
        getServer().getPluginManager().registerEvents(new PlayerLevelListener(this, playerLevels, combatTagIntegration), this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            playerLevels.initializePlayer(online.getUniqueId());
            playerLevels.refreshPlayer(online);
            playerLevels.processPendingLevelRewards(online);
        }
        lootSystemIntegration = new LootSystemIntegration(this);
        lootSystemIntegration.refresh();
        refreshPlayerLevelPlaceholderExpansion();
        getLogger().info("Pacifica-Core is ready with Diamond Currency and Vault economy services.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(this);
        if (lootSystemIntegration != null) lootSystemIntegration.disable();
        if (npcLinks != null) npcLinks.shutdown();
        if (spawnShopNpcLinks != null) spawnShopNpcLinks.shutdown();
        unregisterPlayerLevelPlaceholderExpansion();
        if (blackMarket != null) blackMarket.close();
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
        return false;
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

    public OfflinePlayer findOfflinePlayer(String name) {
        return findPlayer(name);
    }

    public boolean sendAdminMessage(CommandSender sender, String key, String... values) {
        return send(sender, key, values);
    }

    public String formatAmount(BigDecimal amount) {
        return format(amount);
    }

    public void reloadAdministrativeConfiguration() {
        reloadConfig();
        messages.reload();
        reloadPlayerLevelConfiguration();
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

    private void reloadPlayerLevelConfiguration() {
        reloadLevelConfigurations();
        if (playerLevels != null) playerLevels.reload();
        if (advancementNotifications != null) advancementNotifications.reload();
        if (lootSystemIntegration != null) lootSystemIntegration.refresh();
        refreshPlayerLevelPlaceholderExpansion();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerLevels != null) playerLevels.processPendingLevelRewards(player);
            if (levelMenus != null) levelMenus.refresh(player);
        }
    }

    public FileConfiguration getLevelConfig() {
        return levelConfig;
    }

    public FileConfiguration getLevelRewardsConfig() {
        return levelRewardsConfig;
    }

    private void reloadLevelConfigurations() {
        File levelFile = new File(getDataFolder(), "level.yml");
        File rewardsFile = new File(getDataFolder(), "level-rewards.yml");
        levelConfig = YamlConfiguration.loadConfiguration(levelFile);
        levelRewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
    }

    private void saveResourceIfMissing(String resource) {
        File file = new File(getDataFolder(), resource);
        if (!file.exists()) saveResource(resource, false);
    }

    private void refreshPlayerLevelPlaceholderExpansion() {
        unregisterPlayerLevelPlaceholderExpansion();
        if (!getLevelConfig().getBoolean("leveling.integrations.placeholder-api", true)) return;
        org.bukkit.plugin.Plugin placeholderApi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) return;
        try {
            Class<?> expansionClass = Class.forName(
                    "com.districtx.pacificacore.placeholder.PlayerLevelPlaceholderExpansion",
                    true, getClass().getClassLoader());
            Object expansion = expansionClass.getConstructor(PacificaCore.class).newInstance(this);
            Object registered = expansionClass.getMethod("register").invoke(expansion);
            if (Boolean.TRUE.equals(registered)) playerLevelPlaceholderExpansion = expansion;
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().warning("Could not register Pacifica Player Level placeholders: " + exception.getMessage());
        }
    }

    private void unregisterPlayerLevelPlaceholderExpansion() {
        if (playerLevelPlaceholderExpansion == null) return;
        try {
            playerLevelPlaceholderExpansion.getClass().getMethod("unregister")
                    .invoke(playerLevelPlaceholderExpansion);
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().warning("Could not unregister Pacifica Player Level placeholders: " + exception.getMessage());
        } finally {
            playerLevelPlaceholderExpansion = null;
        }
    }

    private static final class FileConfigurationBridge {
        private final PacificaCore plugin;
        private org.bukkit.configuration.file.FileConfiguration config;

        private FileConfigurationBridge(PacificaCore plugin) { this.plugin = plugin; reload(); }
        private void reload() { config = plugin.getConfig(); }
        private String get(String key) { return config.getString("messages." + key, key); }
    }
}