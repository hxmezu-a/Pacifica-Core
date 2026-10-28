package com.districtx.pacificacore.auth;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.AuthSystemService;
import com.districtx.pacificacore.api.AuthenticationMethod;
import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.storage.AuthRepository;
import com.districtx.pacificacore.storage.DatabaseManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import javax.crypto.SecretKeyFactory;
import javax.crypto.Mac;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class AuthSystem implements Listener, AuthSystemService, CommandExecutor {
    private static final int HASH_ITERATIONS = 600_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int MAX_ATTEMPTS = 5;
    private static final long ATTEMPT_INTERVAL_MILLIS = 1_000L;
    private static final String BYPASS_PERMISSION = "pacifica.auth.bypass";

    private final PacificaCore plugin;
    private final AuthRepository repository;
    private final DiscordSRVAuthBridge discordBridge;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Session> authenticated = new ConcurrentHashMap<>();
    private final Map<UUID, FailureState> failures = new ConcurrentHashMap<>();
    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<UUID, RecoveryTicket> recoveryTickets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> discordResendLimits = new ConcurrentHashMap<>();
    private final Map<UUID, PendingJoinTeleport> pendingJoinTeleports = new ConcurrentHashMap<>();
    private volatile FileConfiguration configuration;
    private volatile byte[] ipHashKey;
    private volatile EconomyService linkRewardEconomy;
    private volatile DiamondCurrencyService linkRewardDiamonds;

    public AuthSystem(PacificaCore plugin, DatabaseManager database, FileConfiguration configuration) {
        this.plugin = plugin;
        this.repository = new AuthRepository(database);
        this.discordBridge = new DiscordSRVAuthBridge();
        this.configuration = configuration;
        discordBridge.setAccountLinkHandlers(this::onDiscordAccountLinked, this::onDiscordAccountUnlinked);
        discordBridge.setDailyClaimHandler(this::handleDailyRewardClaim);
        scheduleDailyClaimCommandRegistration();
        if (discordLinkingEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    int conflicts = repository.countDiscordLinkConflicts(discordLinkLimit());
                    if (conflicts > 0) {
                        plugin.getLogger().warning("AuthSystem found " + conflicts
                                + " Discord identities already above the configured account-link limit. Existing links were preserved; new links to those identities will be rejected.");
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "AuthSystem could not inspect existing Discord account-link conflicts.", exception);
                }
            });
        }
    }

    public void reloadConfiguration(FileConfiguration configuration) {
        this.configuration = configuration;
        scheduleDailyClaimCommandRegistration();
    }

    public boolean setAuthenticationJoinLocation(Location location) {
        if (!isUsableLocation(location)) return false;

        FileConfiguration activeConfiguration = configuration;
        String prefix = "auth-system.join-location.";
        String[] keys = {"enabled", "world", "x", "y", "z", "yaw", "pitch"};
        Object[] previousValues = new Object[keys.length];
        boolean[] previouslySet = new boolean[keys.length];
        for (int index = 0; index < keys.length; index++) {
            String key = prefix + keys[index];
            previouslySet[index] = activeConfiguration.isSet(key);
            previousValues[index] = activeConfiguration.get(key);
        }

        activeConfiguration.set(prefix + "enabled", true);
        activeConfiguration.set(prefix + "world", location.getWorld().getName());
        activeConfiguration.set(prefix + "x", location.getX());
        activeConfiguration.set(prefix + "y", location.getY());
        activeConfiguration.set(prefix + "z", location.getZ());
        activeConfiguration.set(prefix + "yaw", location.getYaw());
        activeConfiguration.set(prefix + "pitch", location.getPitch());

        File configFile = new File(plugin.getDataFolder(), "AuthSystem/config.yml");
        try {
            Files.createDirectories(configFile.toPath().getParent());
            activeConfiguration.save(configFile);
            return true;
        } catch (IOException exception) {
            for (int index = 0; index < keys.length; index++) {
                activeConfiguration.set(prefix + keys[index], previouslySet[index] ? previousValues[index] : null);
            }
            plugin.getLogger().log(Level.SEVERE, "AuthSystem could not save the authentication join location.", exception);
            return false;
        }
    }

    private void scheduleDailyClaimCommandRegistration() {
        String guildId = dailyRewardsGuildId();
        String channelId = configuration.getString("discord-daily-rewards.channel-id", "");
        if (!dailyRewardsEnabled()) {
            discordBridge.registerDailyClaimCommand("", "");
            return;
        }
        if (guildId == null || !guildId.matches("[0-9]{17,20}")
                || channelId == null || !channelId.matches("[0-9]{17,20}")) {
            plugin.getLogger().warning("AuthSystem disabled Discord /claim: configure valid discord-daily-rewards.guild-id and channel-id values.");
            discordBridge.registerDailyClaimCommand(guildId, channelId);
            return;
        }
        new BukkitRunnable() {
            private boolean warningLogged;

            @Override
            public void run() {
                if (discordBridge.registerDailyClaimCommand(guildId, channelId)) {
                    cancel();
                } else if (!warningLogged) {
                    plugin.getLogger().warning("AuthSystem could not register Discord /claim. Check DiscordSRV, the configured guild, and the configured claim channel.");
                    warningLogged = true;
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 1200L);
    }

    public void setLinkRewardServices(EconomyService economy, DiamondCurrencyService diamonds) {
        this.linkRewardEconomy = economy;
        this.linkRewardDiamonds = diamonds;
    }

    public void shutdown() {
        discordBridge.close();
        sessions.clear();
        authenticated.clear();
        challenges.clear();
        recoveryTickets.clear();
        discordResendLimits.clear();
    }

    @Override
    public boolean isAuthenticated(UUID playerId) {
        return authenticated.containsKey(playerId);
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.isRegistered(playerId);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read authentication account.", exception);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public CompletableFuture<Optional<AuthenticationMethod>> getAuthenticationMethod(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.getMethod(playerId);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read authentication method.", exception);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public void invalidateSession(UUID playerId) {
        authenticated.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && !player.hasPermission(BYPASS_PERMISSION)) {
            beginSession(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("logout")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /logout.");
            return true;
        }
        UUID playerId = player.getUniqueId();
        if (!isAuthenticated(playerId)) {
            player.sendMessage(message("logout-already-logged-out", "&eYou are already logged out."));
            return true;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            player.sendMessage(message("logout-bypass", "&cAuthentication bypass is enabled for your account."));
            return true;
        }
        invalidateSession(playerId);
        player.sendMessage(message("logout-confirmation", "&aYou have been logged out."));
        return true;
    }

    public CompletableFuture<Boolean> resetAccount(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.deleteAccount(playerId);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not reset authentication account.", exception);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task)).thenApply(deleted -> {
            runOnMain(() -> {
                if (deleted) invalidateSession(playerId);
            });
            return deleted;
        });
    }

    public CompletableFuture<Boolean> unlinkDiscordAccount(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean removed = repository.unlinkDiscordAccount(playerId);
                if (removed && !configuration.getBoolean("discord-linking.rewards.one-time", true)) {
                    repository.resetDiscordLinkReward(playerId);
                }
                return removed;
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not remove Discord account link.", exception);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    public CompletableFuture<Optional<String>> getDiscordLinkRewardStatus(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.discordLinkRewardStatus(playerId);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read Discord-link reward status.", exception);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    public CompletableFuture<Boolean> retryFailedDiscordLinkReward(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.requeueFailedDiscordLinkReward(playerId);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not requeue the failed Discord-link reward.", exception);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task)).thenApply(requeued -> {
            if (requeued) runOnMain(() -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && isAuthenticated(playerId)) {
                    processPendingDiscordLinkReward(player);
                }
            });
            return requeued;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location destination = authenticationJoinLocation();
        if (destination == null) {
            plugin.getLogger().warning("No valid Pacifica-Core authentication join location is configured. Set one with /adminpc auth setjoin before unauthenticated players join.");
            if (!player.hasPermission(BYPASS_PERMISSION)) {
                player.kickPlayer(ChatColor.RED + "Authentication is unavailable because the login location is not configured. Contact an administrator.");
            }
            return;
        }

        UUID playerId = player.getUniqueId();
        PendingJoinTeleport pendingTeleport = new PendingJoinTeleport(destination.clone());
        if (pendingJoinTeleports.putIfAbsent(playerId, pendingTeleport) != null) return;
        try {
            player.teleportAsync(pendingTeleport.destination).whenComplete((teleported, failure) -> runOnMain(() -> {
                if (!pendingJoinTeleports.remove(playerId, pendingTeleport) || !player.isOnline()) return;
                if (failure != null || !Boolean.TRUE.equals(teleported)) {
                    if (failure != null) {
                        plugin.getLogger().log(Level.WARNING, "AuthSystem could not teleport UUID " + playerId
                                + " to the authentication join location.", failure);
                    } else {
                        plugin.getLogger().warning("AuthSystem teleport to the authentication join location failed for UUID "
                                + playerId + ".");
                    }
                    player.kickPlayer(ChatColor.RED + "Could not reach the authentication location. Please reconnect or contact an administrator.");
                    return;
                }
                if (!player.hasPermission(BYPASS_PERMISSION)) beginSession(player);
            }));
        } catch (RuntimeException | LinkageError exception) {
            pendingJoinTeleports.remove(playerId, pendingTeleport);
            plugin.getLogger().log(Level.WARNING, "AuthSystem could not start the authentication join teleport for UUID "
                    + playerId + ".", exception);
            if (player.isOnline()) {
                player.kickPlayer(ChatColor.RED + "Could not reach the authentication location. Please reconnect or contact an administrator.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingJoinTeleports.remove(playerId);
        sessions.remove(playerId);
        authenticated.remove(playerId);
        Challenge challenge = challenges.remove(playerId);
        recoveryTickets.remove(playerId);
        if (configuration.getBoolean("discord-linking.verification.delete-channel-on-logout", false)) {
            String guildId = configuration.getString("discord-linking.guild-id", "");
            String categoryId = configuration.getString("discord-linking.verification-category-id", "");
            String challengeDiscordId = challenge == null ? null : challenge.discordId;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    discordBridge.deleteVerificationChannel(playerId, guildId, categoryId, challengeDiscordId).join();
                } catch (Exception | LinkageError exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "AuthSystem could not apply the configured verification-channel cleanup for player UUID "
                                    + playerId + ".", exception);
                }
            });
        }
    }

    private void beginSession(Player player) {
        UUID playerId = player.getUniqueId();
        authenticated.remove(playerId);
        challenges.remove(playerId);
        recoveryTickets.remove(playerId);
        Session session = new Session(UUID.randomUUID());
        sessions.put(playerId, session);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                session.registered = repository.isRegistered(playerId);
                if (session.registered) {
                    session.method = repository.getMethod(playerId).orElse(null);
                    if (session.method == AuthenticationMethod.PASSWORD) {
                        session.passwordHash = repository.getPasswordHash(playerId);
                    }
                }
                runOnMain(() -> {
                    if (!isCurrent(playerId, session) || !player.isOnline()) return;
                    showAuthentication(player, session);
                });
            } catch (SQLException exception) {
                plugin.getLogger().severe("AuthSystem could not load an account record for UUID "
                        + playerId + "; authentication remains blocked.");
                runOnMain(() -> {
                    if (player.isOnline()) player.kickPlayer(ChatColor.RED + "Authentication is temporarily unavailable. Please reconnect later.");
                });
            }
        });
    }

    private void showAuthentication(Player player, Session session) {
        if (!isCurrent(player.getUniqueId(), session) || isAuthenticated(player.getUniqueId())) return;
        boolean passwordEnabled = configuration.getBoolean("authentication.password-enabled", true);
        boolean discordEnabled = configuration.getBoolean("authentication.discord-enabled", false);
        boolean discordRequired = configuration.getBoolean("authentication.require-discord-on-first-join", false);
        boolean discordLoginRequired = configuration.getBoolean("authentication.require-discord-on-login", false);
        if (!session.registered) {
            if (discordRequired && !discordEnabled) {
                showUnavailable(player, "Discord is required but Discord authentication is not enabled. Contact an administrator.");
            } else if (discordRequired || (discordEnabled && !passwordEnabled)) {
                startDiscordChallenge(player, session, ChallengePurpose.REGISTER);
            } else if (passwordEnabled && discordEnabled) {
                showRegistrationChoices(player, session);
            } else if (passwordEnabled) {
                showRegistration(player, session);
            } else if (discordEnabled) {
                startDiscordChallenge(player, session, ChallengePurpose.REGISTER);
            } else {
                showUnavailable(player, "No authentication method is currently available. Contact an administrator.");
            }
            return;
        }
        if (session.method == AuthenticationMethod.PASSWORD && session.passwordHash != null) {
            if (discordLoginRequired && discordEnabled) startDiscordChallenge(player, session, ChallengePurpose.LOGIN);
            else if (discordLoginRequired) {
                showUnavailable(player, "Discord login verification is required but Discord authentication is not enabled. Contact an administrator.");
            } else if (passwordEnabled) showPasswordLogin(player, session, null);
            else if (discordEnabled) startDiscordChallenge(player, session, ChallengePurpose.LOGIN);
            else showUnavailable(player, "Password authentication is disabled by server policy. Contact an administrator.");
            return;
        }
        if (session.method == AuthenticationMethod.DISCORD && discordEnabled) {
            startDiscordChallenge(player, session, ChallengePurpose.LOGIN);
            return;
        }
        showUnavailable(player, "This account uses an unavailable authentication method. Contact an administrator.");
    }

    private void showRegistrationChoices(Player player, Session session) {
        displayDialog(player, "registration method selection", () -> dialog("Account Registration",
                "Choose how to register your account.", List.of(),
                List.of(button("Register with Password", (view, clicker) -> showRegistration(clicker, session)),
                        button("Link with Discord", (view, clicker) ->
                                startDiscordChallenge(clicker, session, ChallengePurpose.REGISTER)))));
    }

    private void showRegistration(Player player, Session session) {
        displayDialog(player, "registration", () -> dialog("Account Registration",
                "Create a password to protect your account.",
                List.of(DialogInput.text("password", Component.text("Password"))
                                .maxLength(maximumPasswordLength()).width(240).build(),
                        DialogInput.text("confirm", Component.text("Confirm password"))
                                .maxLength(maximumPasswordLength()).width(240).build()),
                List.of(button("Register", (view, clicker) -> registerPassword(clicker, session, view)),
                        button("Back", (view, clicker) -> showRegistrationChoices(clicker, session)))));
    }

    private void registerPassword(Player player, Session session, DialogResponseView view) {
        if (view == null || !isCurrent(player.getUniqueId(), session)) return;
        if (!configuration.getBoolean("authentication.password-enabled", true)) {
            showAuthentication(player, session);
            return;
        }
        String password = view.getText("password");
        String confirmation = view.getText("confirm");
        int minimumLength = Math.min(128, Math.max(8, configuration.getInt("password.minimum-length", 10)));
        int maximumLength = maximumPasswordLength();
        if (password == null || confirmation == null || password.length() < minimumLength || password.length() > maximumLength) {
            player.sendMessage(ChatColor.RED + "Choose a password between " + minimumLength + " and " + maximumLength + " characters.");
            showRegistration(player, session);
            return;
        }
        if (!password.equals(confirmation)) {
            player.sendMessage(ChatColor.RED + "Those passwords do not match.");
            showRegistration(player, session);
            return;
        }
        char[] secret = password.toCharArray();
        char[] confirmSecret = confirmation.toCharArray();
        java.util.Arrays.fill(secret, '\0');
        java.util.Arrays.fill(confirmSecret, '\0');
        UUID playerId = player.getUniqueId();
        int ipLimit = ipLimit(player);
        byte[] clientAddress = ipLimit > 0 ? clientIpAddress(player) : null;
        String pendingDiscordId = session.pendingDiscordId;
        int discordLimit = pendingDiscordId == null ? 0 : discordLinkLimit();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthRepository.RegistrationResult result;
            try {
                if (pendingDiscordId != null
                        && !pendingDiscordId.equals(discordBridge.resolveLinkedDiscordId(playerId))) {
                    runOnMain(() -> {
                        if (player.isOnline() && isCurrent(playerId, session)) {
                            session.pendingDiscordId = null;
                            player.sendMessage(ChatColor.RED + "DiscordSRV no longer confirms that account link. Link Discord again before registering it.");
                            showRegistration(player, session);
                        }
                    });
                    return;
                }
                String ipHash = ipLimit > 0 ? hashIp(clientAddress) : null;
                result = repository.registerPassword(playerId, hashPassword(password), ipHash, ipLimit,
                        pendingDiscordId, discordLimit);
            } catch (Exception exception) {
                plugin.getLogger().severe("AuthSystem could not save an account for UUID " + playerId + ".");
                runOnMain(() -> {
                    if (player.isOnline() && isCurrent(playerId, session)) {
                        player.sendMessage(ChatColor.RED + "Registration could not be saved. Try again later.");
                        showRegistration(player, session);
                    }
                });
                return;
            }
            runOnMain(() -> {
                if (!player.isOnline() || !isCurrent(playerId, session)) return;
                switch (result) {
                    case CREATED -> {
                        session.registered = true;
                        session.method = AuthenticationMethod.PASSWORD;
                        session.pendingDiscordId = null;
                        authenticate(player, session);
                        player.sendMessage(ChatColor.GREEN + "Your account is registered and authenticated.");
                        if (pendingDiscordId != null) {
                            player.sendMessage(linkRewardMessage("discord-linked",
                                    "&aYour Minecraft account has been linked successfully!"));
                            queueDiscordLinkReward(playerId);
                        }
                    }
                    case ALREADY_REGISTERED -> {
                        player.sendMessage(ChatColor.RED + "An account already exists. Please reconnect to log in.");
                        beginSession(player);
                    }
                    case IP_LIMIT_REACHED -> {
                        plugin.getLogger().warning("AuthSystem denied registration at the IP account limit for player UUID "
                                + playerId + ".");
                        player.sendMessage(message("ip-limit-reached",
                                "&cThe maximum number of Minecraft accounts for this network has been reached."));
                        showRegistration(player, session);
                    }
                    case IP_ADDRESS_UNAVAILABLE -> {
                        player.sendMessage(message("ip-limit-unavailable",
                                "&cYour connection could not be checked for the account limit. Try again later."));
                        showRegistration(player, session);
                    }
                    case DISCORD_LIMIT_REACHED -> {
                        player.sendMessage(message("discord-limit-reached",
                                "&cThis Discord account has reached its Minecraft account-link limit."));
                        showRegistration(player, session);
                    }
                }
            });
        });
    }

    private void showPasswordLogin(Player player, Session session, String error) {
        List<DialogBody> body = error == null ? List.of(
                DialogBody.plainMessage(Component.text("Enter your account password to continue."))) : List.of(
                DialogBody.plainMessage(Component.text(error, NamedTextColor.RED)),
                DialogBody.plainMessage(Component.text("Enter your account password to continue.")));
        List<ActionButton> buttons = new java.util.ArrayList<>();
        buttons.add(button("Log in", (view, clicker) -> verifyPassword(clicker, session, view)));
        if (configuration.getBoolean("authentication.discord-enabled", false)) {
            buttons.add(button("Login with Discord", (view, clicker) -> beginDiscordLogin(clicker, session)));
            buttons.add(button("Forgot Password", (view, clicker) ->
                    startDiscordChallenge(clicker, session, ChallengePurpose.RECOVERY)));
        }
        displayDialog(player, "password login", () -> dialog("Account Login", body,
                List.of(DialogInput.text("password", Component.text("Password"))
                        .maxLength(maximumPasswordLength()).width(240).build()),
                buttons));
    }

    private void beginDiscordLogin(Player player, Session session) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || isAuthenticated(playerId)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String discordId = discordBridge.resolveLinkedDiscordId(playerId);
                runOnMain(() -> {
                    if (!player.isOnline() || !isCurrent(playerId, session) || isAuthenticated(playerId)) return;
                    startDiscordChallenge(player, session, discordId == null
                            ? ChallengePurpose.LINK_LOGIN : ChallengePurpose.LOGIN);
                });
            } catch (Exception | LinkageError exception) {
                plugin.getLogger().log(Level.WARNING,
                        "AuthSystem could not check DiscordSRV link status for player UUID " + playerId + ".", exception);
                runOnMain(() -> {
                    if (player.isOnline() && isCurrent(playerId, session) && !isAuthenticated(playerId)) {
                        showPasswordLogin(player, session,
                                "Discord login is temporarily unavailable. Try again or use your password.");
                    }
                });
            }
        });
    }

    private void startDiscordChallenge(Player player, Session session, ChallengePurpose purpose) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || (!isDiscordSrvLinkPurpose(purpose) && isAuthenticated(playerId))) return;
        if (purpose == ChallengePurpose.REGISTER_LINK && session.registered) return;
        if (purpose == ChallengePurpose.LINK_LOGIN && !session.registered) return;
        if (isDiscordSrvLinkPurpose(purpose)) {
            if (!discordLinkingEnabled()) {
                player.sendMessage(message("discord-linking-disabled", "&cDiscord account linking is currently disabled."));
                return;
            }
            startDiscordSrvLinkChallenge(player, session, purpose);
            return;
        }
        if (purpose != ChallengePurpose.LOGIN && purpose != ChallengePurpose.RECOVERY) return;
        long now = System.currentTimeMillis();
        Long retryAt = discordResendLimits.get(playerId);
        if (retryAt != null && retryAt > now) {
            showDiscordDeliveryFailure(player, session, purpose, new IllegalStateException("Try again in "
                    + Math.max(1, (retryAt - now + 999) / 1000) + " seconds."));
            return;
        }
        discordResendLimits.remove(playerId);
        int codeLength = Math.min(9, Math.max(6, configuration.getInt("discord-linking.verification.code-length",
                configuration.getInt("authentication.discord-code-length", 6))));
        int expirationSeconds = Math.min(1800, Math.max(30,
                configuration.getInt("discord-linking.verification.expiration-seconds",
                        configuration.getInt("authentication.discord-code-expiration-seconds", 300))));
        int cooldownSeconds = Math.min(3600, Math.max(10,
                configuration.getInt("discord-linking.verification.resend-cooldown-seconds",
                        configuration.getInt("authentication.discord-code-resend-cooldown-seconds", 60))));
        long resendAt = now + cooldownSeconds * 1000L;
        discordResendLimits.put(playerId, resendAt);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> discordResendLimits.remove(playerId, resendAt), cooldownSeconds * 20L);
        StringBuilder code = new StringBuilder(codeLength);
        for (int index = 0; index < codeLength; index++) code.append(secureRandom.nextInt(10));
        Challenge challenge = new Challenge(session.id, code.toString(), purpose,
                now + expirationSeconds * 1000L, resendAt, null, 0);
        challenges.put(playerId, challenge);
        showDiscordVerification(player, session, challenge,
                "Preparing a private Discord server channel for your login verification code.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (challenges.remove(playerId, challenge) && player.isOnline() && isCurrent(playerId, session)) {
                showExpiredChallenge(player, session, purpose);
            }
        }, expirationSeconds * 20L);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (challenges.get(playerId) != challenge) return;
                String discordId = discordBridge.resolveLinkedDiscordId(playerId);
                if (discordId == null) {
                    if (purpose == ChallengePurpose.LOGIN) {
                        runOnMain(() -> {
                            if (player.isOnline() && isCurrent(playerId, session)
                                    && challenges.remove(playerId, challenge)) {
                                startDiscordChallenge(player, session, ChallengePurpose.LINK_LOGIN);
                            }
                        });
                        return;
                    }
                    throw new IllegalStateException("This Minecraft account is not linked in DiscordSRV. Complete "
                            + "DiscordSRV account linking, then try Discord verification again.");
                }
                challenge.discordId = discordId;
                discordBridge.deliverLoginCode(playerId, player.getName(),
                        configuration.getString("discord-linking.guild-id", ""),
                        configuration.getString("discord-linking.verification-category-id", ""),
                        discordId, challenge.code,
                        configuration.getBoolean("discord-linking.verification.create-channel-if-missing", true),
                        configuration.getBoolean("discord-linking.verification.reuse-existing-channel", true),
                        () -> isCurrent(playerId, session) && challenges.get(playerId) == challenge
                                && System.currentTimeMillis() < challenge.expiresAt)
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                failDiscordDelivery(player, session, purpose, challenge, failure);
                                return;
                            }
                            challenge.delivered = true;
                            runOnMain(() -> {
                                if (player.isOnline() && isCurrent(playerId, session)
                                        && challenges.get(playerId) == challenge) {
                                    player.sendMessage(ChatColor.GREEN
                                            + "A login verification code was sent to your private Discord server channel.");
                                    showDiscordVerification(player, session, challenge,
                                            "Enter the fresh code sent to your private Discord server channel.");
                                }
                            });
                        });
            } catch (Exception | LinkageError exception) {
                failDiscordDelivery(player, session, purpose, challenge, exception);
            }
        });
    }

    private void startDiscordSrvLinkChallenge(Player player, Session session, ChallengePurpose purpose) {
        UUID playerId = player.getUniqueId();
        Challenge active = challenges.get(playerId);
        if (active != null && active.purpose == purpose && active.sessionId.equals(session.id)
                && System.currentTimeMillis() < active.expiresAt) {
            showDiscordVerification(player, session, active,
                    "Complete account linking through DiscordSRV, then check the link status here.");
            return;
        }
        long now = System.currentTimeMillis();
        int expirationSeconds = Math.min(1800, Math.max(30,
                configuration.getInt("discord-linking.verification.expiration-seconds",
                        configuration.getInt("authentication.discord-code-expiration-seconds", 300))));
        Challenge challenge = new Challenge(session.id, null, purpose, now + expirationSeconds * 1000L,
                now, purpose == ChallengePurpose.REGISTER ? clientIpAddress(player) : null,
                purpose == ChallengePurpose.REGISTER ? ipLimit(player) : 0);
        challenges.put(playerId, challenge);
        showDiscordVerification(player, session, challenge,
                "Checking DiscordSRV and requesting your native account-linking code...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (challenges.remove(playerId, challenge) && player.isOnline() && isCurrent(playerId, session)) {
                showExpiredChallenge(player, session, purpose);
            }
        }, expirationSeconds * 20L);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!isCurrent(playerId, session) || challenges.get(playerId) != challenge) return;
                String linkedId = discordBridge.resolveLinkedDiscordId(playerId);
                if (linkedId != null) {
                    runOnMain(() -> {
                        if (player.isOnline() && isCurrent(playerId, session)
                                && challenges.get(playerId) == challenge) {
                            completeDiscordSrvLink(player, session, challenge, linkedId);
                        }
                    });
                    return;
                }
                String linkCode = discordBridge.getOrCreateNativeLinkCode(playerId);
                runOnMain(() -> {
                    if (!player.isOnline() || !isCurrent(playerId, session)
                            || challenges.get(playerId) != challenge
                            || System.currentTimeMillis() >= challenge.expiresAt) return;
                    challenge.discordSrvCode = linkCode;
                    showDiscordVerification(player, session, challenge,
                            "Send this code to DiscordSRV using its native account-linking flow, then verify here.");
                });
            } catch (Exception | LinkageError exception) {
                failDiscordDelivery(player, session, purpose, challenge, exception);
            }
        });
    }

    private void onDiscordAccountLinked(UUID playerId, String discordId) {
        if (discordId == null || !discordId.matches("[0-9]{17,20}")) return;
        Session session = sessions.get(playerId);
        Challenge challenge = challenges.get(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && session != null && challenge != null
                && isDiscordSrvLinkPurpose(challenge.purpose) && challenge.sessionId.equals(session.id)) {
            runOnMain(() -> completeDiscordSrvLink(player, session, challenge, discordId));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!discordId.equals(discordBridge.resolveLinkedDiscordId(playerId))) return;
                AuthRepository.LinkResult result = repository.linkDiscordAccount(playerId, discordId, discordLinkLimit());
                if (result == AuthRepository.LinkResult.LINKED) {
                    queueDiscordLinkReward(playerId);
                    runOnMain(() -> {
                        Player linkedPlayer = Bukkit.getPlayer(playerId);
                        Session current = authenticated.get(playerId);
                        if (linkedPlayer != null && current != null && isCurrent(playerId, current)) {
                            linkedPlayer.sendMessage(linkRewardMessage("discord-linked",
                                    "&aYour Minecraft account has been linked successfully!"));
                        }
                    });
                } else if (result != AuthRepository.LinkResult.ALREADY_LINKED) {
                    plugin.getLogger().warning("DiscordSRV linked UUID " + playerId
                            + ", but AuthSystem could not synchronize its auxiliary link record (" + result + ").");
                }
            } catch (Exception | LinkageError exception) {
                plugin.getLogger().log(Level.WARNING,
                        "DiscordSRV linked UUID " + playerId + ", but AuthSystem could not synchronize its auxiliary link record.",
                        exception);
            }
        });
    }

    private void onDiscordAccountUnlinked(UUID playerId, String discordId) {
        Session session = sessions.get(playerId);
        if (session != null) {
            runOnMain(() -> {
                if (!isCurrent(playerId, session)) return;
                if (discordId != null && discordId.equals(session.pendingDiscordId)) {
                    session.pendingDiscordId = null;
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Your pending DiscordSRV link was removed. Link Discord again before registering it.");
                        showRegistration(player, session);
                    }
                }
            });
        }
        if (discordId != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    repository.unlinkDiscordAccount(playerId, discordId);
                    if (!configuration.getBoolean("discord-linking.rewards.one-time", true)) {
                        repository.resetDiscordLinkReward(playerId);
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "DiscordSRV unlinked UUID " + playerId + ", but AuthSystem could not remove its matching auxiliary link record.",
                            exception);
                }
            });
        }
    }

    private boolean linkRewardsEnabled() {
        return configuration.getBoolean("discord-linking.rewards.enabled", true);
    }

    private boolean dailyRewardsEnabled() {
        return configuration.getBoolean("discord-daily-rewards.enabled", false);
    }

    private String dailyRewardsGuildId() {
        String guildId = configuration.getString("discord-daily-rewards.guild-id", "");
        return guildId == null || guildId.isBlank()
                ? configuration.getString("discord-linking.guild-id", "") : guildId;
    }

    private void handleDailyRewardClaim(String discordId, Consumer<String> response) {
        if (!dailyRewardsEnabled()) {
            response.accept(configuration.getString("discord-daily-rewards.messages.error",
                    "Daily rewards are disabled."));
            return;
        }
        List<String> rewards = configuration.getStringList("discord-daily-rewards.rewards").stream()
                .filter(command -> command != null && !command.isBlank()).toList();
        if (rewards.isEmpty()) {
            response.accept(configuration.getString("discord-daily-rewards.messages.error",
                    "Your reward could not be processed. Please try again later."));
            return;
        }
        int cooldownHours = Math.min(8760, Math.max(1,
                configuration.getInt("discord-daily-rewards.cooldown-hours", 24)));
        long cooldownMillis = cooldownHours * 3_600_000L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID playerId = repository.findMinecraftUuidForDiscord(discordId);
                if (playerId == null) {
                    response.accept(configuration.getString("discord-daily-rewards.messages.unlinked",
                            "Link your Minecraft account before claiming rewards."));
                    return;
                }
                AuthRepository.DailyClaimReservation reservation = repository.reserveDailyClaim(playerId,
                        System.currentTimeMillis(), cooldownMillis);
                if (reservation.status() == AuthRepository.DailyClaimStatus.COOLDOWN) {
                    long remaining = Math.max(0, reservation.nextClaimAt() - System.currentTimeMillis());
                    long minutes = Math.max(1, (remaining + 59_999L) / 60_000L);
                    String remainingText = minutes >= 60
                            ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
                    response.accept(configuration.getString("discord-daily-rewards.messages.cooldown",
                                    "You have already claimed today's reward. Try again in %time%.")
                            .replace("%time%", remainingText));
                    return;
                }
                if (reservation.status() == AuthRepository.DailyClaimStatus.ALREADY_PENDING) {
                    response.accept(configuration.getString("discord-daily-rewards.messages.pending",
                            "Your reward is queued. Log in to the Minecraft server to receive it."));
                    return;
                }
                runOnMain(() -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline() && isAuthenticated(playerId)) {
                        processPendingDailyReward(player, response);
                    } else {
                        response.accept(configuration.getString("discord-daily-rewards.messages.pending",
                                "Your reward is queued. Log in to the Minecraft server to receive it."));
                    }
                });
            } catch (SQLException | RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "AuthSystem could not process a Discord daily reward claim.", exception);
                response.accept(configuration.getString("discord-daily-rewards.messages.error",
                        "Your reward could not be processed. Please try again later."));
            }
        });
    }

    private void processPendingDailyReward(Player player, Consumer<String> response) {
        UUID playerId = player.getUniqueId();
        if (!dailyRewardsEnabled() || !player.isOnline() || !isAuthenticated(playerId)) {
            if (response != null) response.accept(configuration.getString("discord-daily-rewards.messages.pending",
                    "Your reward is queued. Log in to the Minecraft server to receive it."));
            return;
        }
        List<String> rewards = configuration.getStringList("discord-daily-rewards.rewards").stream()
                .filter(command -> command != null && !command.isBlank()).toList();
        if (rewards.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!repository.claimPendingDailyReward(playerId)) {
                    if (response != null) response.accept(configuration.getString("discord-daily-rewards.messages.pending",
                            "Your reward is queued. Log in to the Minecraft server to receive it."));
                    return;
                }
                runOnMain(() -> {
                    if (!player.isOnline() || !isAuthenticated(playerId)) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                repository.requeueDailyRewardClaim(playerId);
                                if (response != null) response.accept(configuration.getString(
                                        "discord-daily-rewards.messages.pending",
                                        "Your reward is queued. Log in to the Minecraft server to receive it."));
                            } catch (SQLException exception) {
                                plugin.getLogger().log(Level.WARNING,
                                        "AuthSystem could not restore a pending daily reward for UUID " + playerId + ".",
                                        exception);
                            }
                        });
                        return;
                    }
                    boolean delivered = true;
                    for (String reward : rewards) {
                        String command = reward.replace("%player%", player.getName())
                                .replace("%uuid%", playerId.toString());
                        try {
                            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                                delivered = false;
                                break;
                            }
                        } catch (RuntimeException exception) {
                            delivered = false;
                            plugin.getLogger().log(Level.WARNING,
                                    "AuthSystem could not execute a Discord daily reward for UUID " + playerId + ".",
                                    exception);
                            break;
                        }
                    }
                    boolean rewardDelivered = delivered;
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            if (rewardDelivered) {
                                repository.completeDailyRewardClaim(playerId, System.currentTimeMillis());
                            } else {
                                repository.requeueDailyRewardClaim(playerId);
                            }
                            if (response != null) {
                                response.accept(configuration.getString(rewardDelivered
                                        ? "discord-daily-rewards.messages.success"
                                        : "discord-daily-rewards.messages.error", rewardDelivered
                                        ? "Your daily Minecraft reward has been claimed!"
                                        : "Your reward could not be processed. Please try again later."));
                            }
                        } catch (SQLException exception) {
                            plugin.getLogger().log(Level.WARNING,
                                    "AuthSystem could not finalize a Discord daily reward for UUID " + playerId + ".",
                                    exception);
                            if (response != null) response.accept(configuration.getString(
                                    "discord-daily-rewards.messages.error",
                                    "Your reward could not be processed. Please try again later."));
                        }
                    });
                });
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "AuthSystem could not claim a queued Discord daily reward for UUID " + playerId + ".",
                        exception);
                if (response != null) response.accept(configuration.getString("discord-daily-rewards.messages.error",
                        "Your reward could not be processed. Please try again later."));
            }
        });
    }

    private void queueDiscordLinkReward(UUID playerId) {
        if (!linkRewardsEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.queueDiscordLinkReward(playerId);
                runOnMain(() -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline() && isAuthenticated(playerId)) {
                        processPendingDiscordLinkReward(player);
                    }
                });
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "AuthSystem could not queue the Discord-link reward for UUID " + playerId + ".", exception);
            }
        });
    }

    private void processPendingDiscordLinkReward(Player player) {
        UUID playerId = player.getUniqueId();
        if (!linkRewardsEnabled() || !player.isOnline() || !isAuthenticated(playerId)) return;
        List<String> commands = configuration.getStringList("discord-linking.rewards.commands");
        boolean balanceEnabled = configuration.getBoolean(
                "discord-linking.rewards.server-balance.enabled", false);
        BigDecimal balanceAmount = configuredRewardAmount("discord-linking.rewards.server-balance.amount");
        boolean diamondsEnabled = configuration.getBoolean(
                "discord-linking.rewards.diamond-currency.enabled", false);
        BigDecimal diamondAmount = configuredRewardAmount("discord-linking.rewards.diamond-currency.amount");
        boolean grantBalance = balanceEnabled && balanceAmount.signum() > 0;
        boolean grantDiamonds = diamondsEnabled && diamondAmount.signum() > 0;
        boolean hasCommands = commands.stream().anyMatch(command -> command != null && !command.isBlank());
        if (!hasCommands && !grantBalance && !grantDiamonds) return;
        if ((grantBalance && linkRewardEconomy == null) || (grantDiamonds && linkRewardDiamonds == null)) {
            plugin.getLogger().warning("AuthSystem could not deliver a configured Discord-link currency reward; "
                    + "the reward remains queued for UUID " + playerId + ".");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!repository.claimPendingDiscordLinkReward(playerId)) return;
                runOnMain(() -> {
                    if (!player.isOnline() || !isAuthenticated(playerId)) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                repository.requeueDiscordLinkReward(playerId);
                            } catch (SQLException exception) {
                                plugin.getLogger().log(Level.WARNING,
                                        "AuthSystem could not restore the queued Discord-link reward for UUID "
                                                + playerId + ".", exception);
                            }
                        });
                        return;
                    }
                    boolean delivered = true;
                    for (String command : commands) {
                        if (command == null || command.isBlank()) continue;
                        String expanded = command.replace("%player%", player.getName())
                                .replace("%uuid%", playerId.toString());
                        try {
                            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded)) {
                                delivered = false;
                                plugin.getLogger().warning("AuthSystem's configured Discord-link reward command "
                                        + "was not accepted for UUID " + playerId + ".");
                            }
                        } catch (RuntimeException exception) {
                            delivered = false;
                            plugin.getLogger().log(Level.WARNING,
                                    "AuthSystem could not execute a configured Discord-link reward command for UUID "
                                            + playerId + ".", exception);
                        }
                    }
                    if (grantBalance && !linkRewardEconomy.deposit(playerId, balanceAmount)) {
                        delivered = false;
                        plugin.getLogger().warning("AuthSystem could not deposit the Discord-link server-balance "
                                + "reward for UUID " + playerId + ".");
                    }
                    if (grantDiamonds && !linkRewardDiamonds.deposit(playerId, diamondAmount)) {
                        delivered = false;
                        plugin.getLogger().warning("AuthSystem could not deposit the Discord-link diamond-currency "
                                + "reward for UUID " + playerId + ".");
                    }
                    final boolean rewardDelivered = delivered;
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            if (rewardDelivered) {
                                repository.completeDiscordLinkReward(playerId);
                                runOnMain(() -> {
                                    if (player.isOnline() && isAuthenticated(playerId)) {
                                        player.sendMessage(linkRewardMessage("reward-received",
                                                "&aYou received your account-linking reward!"));
                                    }
                                });
                            } else {
                                repository.failDiscordLinkReward(playerId);
                                plugin.getLogger().warning("AuthSystem marked the Discord-link reward as failed for UUID "
                                        + playerId + ". Use the admin reward retry action after reviewing delivery.");
                            }
                        } catch (SQLException exception) {
                            plugin.getLogger().log(Level.WARNING,
                                    "AuthSystem could not finalize the Discord-link reward for UUID " + playerId + ".",
                                    exception);
                        }
                    });
                });
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "AuthSystem could not claim the queued Discord-link reward for UUID " + playerId + ".",
                        exception);
            }
        });
    }

    private BigDecimal configuredRewardAmount(String path) {
        double amount = configuration.getDouble(path, 0.0);
        return Double.isFinite(amount) && amount > 0 ? BigDecimal.valueOf(amount) : BigDecimal.ZERO;
    }

    private void completeDiscordSrvLink(Player player, Session session, Challenge challenge, String expectedDiscordId) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || challenges.get(playerId) != challenge
                || !isDiscordSrvLinkPurpose(challenge.purpose) || expectedDiscordId == null
                || !expectedDiscordId.matches("[0-9]{17,20}")
                || (challenge.discordId != null && !expectedDiscordId.equals(challenge.discordId))
                || System.currentTimeMillis() >= challenge.expiresAt) return;
        synchronized (challenge) {
            if (challenge.processing) return;
            challenge.processing = true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean discordSrvConfirmed = false;
            try {
                String authoritativeDiscordId = discordBridge.resolveLinkedDiscordId(playerId);
                if (!expectedDiscordId.equals(authoritativeDiscordId)) {
                    throw new IllegalStateException("DiscordSRV has not confirmed the expected Minecraft-to-Discord link yet.");
                }
                discordSrvConfirmed = true;
                challenge.discordId = authoritativeDiscordId;
                if (challenge.purpose == ChallengePurpose.REGISTER_LINK) {
                    runOnMain(() -> {
                        if (!player.isOnline() || !isCurrent(playerId, session)
                                || challenges.get(playerId) != challenge || session.registered) return;
                        challenges.remove(playerId, challenge);
                        session.pendingDiscordId = authoritativeDiscordId;
                        player.sendMessage(message("discord-link-verified",
                                "&aDiscordSRV confirmed the link. Complete registration to save it."));
                        showRegistration(player, session);
                    });
                    return;
                }
                if (challenge.purpose == ChallengePurpose.LINK_LOGIN) {
                    AuthRepository.LinkResult linkResult = repository.linkDiscordAccount(
                            playerId, authoritativeDiscordId, discordLinkLimit());
                    runOnMain(() -> {
                        if (!player.isOnline() || !isCurrent(playerId, session)
                                || challenges.get(playerId) != challenge) return;
                        challenges.remove(playerId, challenge);
                        if (linkResult == AuthRepository.LinkResult.LINKED) queueDiscordLinkReward(playerId);
                        if (linkResult == AuthRepository.LinkResult.LINKED
                                || linkResult == AuthRepository.LinkResult.ALREADY_LINKED) {
                            startDiscordChallenge(player, session, ChallengePurpose.LOGIN);
                        } else {
                            showDiscordLoginLinkFailure(player, session);
                        }
                    });
                    return;
                }
                String ipHash = challenge.ipLimit > 0 ? hashIp(challenge.clientAddress) : null;
                AuthRepository.RegistrationResult result = repository.registerDiscord(playerId, authoritativeDiscordId,
                        ipHash, discordLinkLimit(), challenge.ipLimit);
                runOnMain(() -> {
                    if (!player.isOnline() || !isCurrent(playerId, session)
                            || challenges.get(playerId) != challenge) return;
                    challenges.remove(playerId, challenge);
                    switch (result) {
                        case CREATED -> {
                            session.registered = true;
                            session.method = AuthenticationMethod.DISCORD;
                            authenticate(player, session);
                            queueDiscordLinkReward(playerId);
                            player.sendMessage(ChatColor.GREEN + "Your account is registered and authenticated.");
                        }
                        case ALREADY_REGISTERED -> {
                            player.sendMessage(ChatColor.YELLOW + "An account already exists. Authenticate again.");
                            beginSession(player);
                        }
                        case IP_LIMIT_REACHED -> {
                            showDiscordRegistrationFailure(player, session, message("ip-limit-reached",
                                    "&cThe maximum number of Minecraft accounts for this network has been reached."));
                        }
                        case DISCORD_LIMIT_REACHED -> {
                            showDiscordRegistrationFailure(player, session, message("discord-limit-reached",
                                    "&cThis Discord account has reached its Minecraft account-link limit."));
                        }
                        case IP_ADDRESS_UNAVAILABLE -> {
                            showDiscordRegistrationFailure(player, session, message("ip-limit-unavailable",
                                    "&cYour connection could not be checked for the account limit. Try again later."));
                        }
                    }
                });
            } catch (Exception | LinkageError exception) {
                challenge.processing = false;
                plugin.getLogger().log(Level.WARNING,
                        "AuthSystem could not complete DiscordSRV registration/link verification for player UUID "
                                + playerId + ".",
                        exception);
                String status = discordSrvConfirmed
                        ? "DiscordSRV confirmed your link, but Pacifica-Core could not finish registration. Retry or contact an administrator."
                        : "DiscordSRV has not confirmed the link yet. Wait for it to finish, then check again.";
                runOnMain(() -> {
                    if (player.isOnline() && isCurrent(playerId, session)
                            && challenges.get(playerId) == challenge) {
                        showDiscordVerification(player, session, challenge, status);
                    }
                });
            }
        });
    }

    private void showDiscordRegistrationFailure(Player player, Session session, String error) {
        List<ActionButton> buttons = new java.util.ArrayList<>();
        if (passwordFallbackAvailable(session)) {
            buttons.add(button("Register with Password", (view, clicker) -> showRegistration(clicker, session)));
        }
        buttons.add(button("Try DiscordSRV Again", (view, clicker) ->
                startDiscordChallenge(clicker, session, ChallengePurpose.REGISTER)));
        buttons.add(button("Back", (view, clicker) -> {
            challenges.remove(clicker.getUniqueId());
            showRegistrationChoices(clicker, session);
        }));
        displayDialog(player, "Discord registration incomplete", () -> dialog("Registration Not Completed",
                List.of(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED))), List.of(), buttons));
    }

    private void showDiscordLoginLinkFailure(Player player, Session session) {
        List<ActionButton> buttons = List.of(
                button("Try DiscordSRV Again", (view, clicker) ->
                        startDiscordChallenge(clicker, session, ChallengePurpose.LINK_LOGIN)),
                button("Back to Password Login", (view, clicker) -> showPasswordLogin(clicker, session, null)));
        displayDialog(player, "Discord account link unavailable", () -> dialog("Discord Account Link Unavailable",
                "This Discord account could not be linked to your Minecraft account. Check your account-link limit or try again.",
                List.of(), buttons));
    }

    private void checkDiscordSrvLink(Player player, Session session, Challenge challenge) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || challenges.get(playerId) != challenge
                || !isDiscordSrvLinkPurpose(challenge.purpose)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String linkedId = discordBridge.resolveLinkedDiscordId(playerId);
                if (linkedId == null) {
                    runOnMain(() -> {
                        if (player.isOnline() && isCurrent(playerId, session)
                                && challenges.get(playerId) == challenge) {
                            showDiscordVerification(player, session, challenge,
                                    "DiscordSRV has not linked this Minecraft account yet. Complete the Discord step and check again.");
                        }
                    });
                    return;
                }
                completeDiscordSrvLink(player, session, challenge, linkedId);
            } catch (Exception | LinkageError exception) {
                failDiscordDelivery(player, session, challenge.purpose, challenge, exception);
            }
        });
    }

    private void failDiscordDelivery(Player player, Session session, ChallengePurpose purpose,
                                     Challenge challenge, Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        UUID playerId = player.getUniqueId();
        plugin.getLogger().log(Level.WARNING, "AuthSystem Discord delivery failed for player UUID " + playerId
                + " (DiscordSRV=" + discordBridge.diagnosticState() + "). Authentication remains blocked.", cause);
        Throwable finalCause = cause;
        runOnMain(() -> {
            if (player.isOnline() && isCurrent(playerId, session) && challenges.get(playerId) == challenge) {
                challenges.remove(playerId, challenge);
                showDiscordDeliveryFailure(player, session, purpose, finalCause);
            }
        });
    }

    private void showDiscordVerification(Player player, Session session, Challenge challenge, String status) {
        if (!isCurrent(player.getUniqueId(), session) || challenges.get(player.getUniqueId()) != challenge) return;
        boolean accountLinkFlow = isDiscordSrvLinkPurpose(challenge.purpose);
        String title = challenge.purpose == ChallengePurpose.REGISTER_LINK
                ? "Discord Registration Link" : challenge.purpose == ChallengePurpose.REGISTER
                ? "Discord Account Registration" : challenge.purpose == ChallengePurpose.LINK_LOGIN
                ? "Link Discord for Login" : "Discord Login Verification";
        String instructions = challenge.purpose == ChallengePurpose.REGISTER_LINK
                ? dialogMessage("link-code-register-prompt",
                        "Link this Minecraft account using DiscordSRV. The link is saved with your password registration.")
                : challenge.purpose == ChallengePurpose.REGISTER
                ? "Complete DiscordSRV account linking to register and authenticate this Minecraft account."
                : challenge.purpose == ChallengePurpose.LINK_LOGIN
                ? "This Minecraft account is not linked to DiscordSRV. Complete account linking to continue with Discord login."
                : "Check your private Discord server channel for your fresh login verification code. It expires soon and can be used once.";
        List<ActionButton> buttons = new java.util.ArrayList<>();
        List<DialogInput> inputs = List.of();
        List<DialogBody> body = new java.util.ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(instructions)));
        if (accountLinkFlow) {
            if (challenge.discordSrvCode == null) {
                body.add(DialogBody.plainMessage(Component.text(
                        "Waiting for DiscordSRV to provide your native link code...")));
            } else {
                body.add(DialogBody.plainMessage(Component.text("Your DiscordSRV link code: ")
                        .append(Component.text(challenge.discordSrvCode, NamedTextColor.AQUA))));
                body.add(DialogBody.plainMessage(Component.text(
                        "Send this code to the DiscordSRV bot using its account-linking workflow, then return here.")));
                buttons.add(button("Verify Link / Continue", (view, clicker) ->
                        checkDiscordSrvLink(clicker, session, challenge)));
            }
            buttons.add(button("Back", (view, clicker) -> {
                if (challenge.processing) {
                    showDiscordVerification(clicker, session, challenge,
                            "Link verification is still processing. Please wait before going back.");
                    return;
                }
                challenges.remove(clicker.getUniqueId(), challenge);
                if (challenge.purpose == ChallengePurpose.LINK_LOGIN) {
                    showPasswordLogin(clicker, session, null);
                } else if (challenge.purpose == ChallengePurpose.REGISTER_LINK) {
                    showRegistration(clicker, session);
                } else {
                    showRegistrationChoices(clicker, session);
                }
            }));
        } else {
            inputs = List.of(DialogInput.text("code", Component.text("Verification code"))
                    .maxLength(9).width(220).build());
            buttons.add(button("Submit Code", (view, clicker) -> submitDiscordCode(clicker, session, challenge, view)));
        }
        if (!accountLinkFlow && session.method == AuthenticationMethod.PASSWORD) {
            buttons.add(button("Back", (view, clicker) -> {
                challenges.remove(clicker.getUniqueId(), challenge);
                showPasswordLogin(clicker, session, null);
            }));
        }
        if (passwordFallbackAvailable(session)) {
            buttons.add(button("Use Password", (view, clicker) -> usePasswordFallback(clicker, session, challenge)));
        }
        if (!accountLinkFlow) {
            buttons.add(button("Resend Code", (view, clicker) -> resendDiscordCode(clicker, session, challenge)));
        }
        if (status != null && !status.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(status, NamedTextColor.YELLOW)));
        }
        List<DialogInput> dialogInputs = inputs;
        displayDialog(player, "Discord verification", () -> dialog(title, body, dialogInputs, buttons));
    }

    private boolean passwordFallbackAvailable(Session session) {
        if (!configuration.getBoolean("authentication.password-enabled", true)) return false;
        if (!session.registered) return !configuration.getBoolean("authentication.require-discord-on-first-join", false);
        return session.method == AuthenticationMethod.PASSWORD
                && !configuration.getBoolean("authentication.require-discord-on-login", false);
    }

    private void showExpiredChallenge(Player player, Session session, ChallengePurpose purpose) {
        List<ActionButton> buttons = new java.util.ArrayList<>();
        if (passwordFallbackAvailable(session)) {
            buttons.add(button("Use Password", (view, clicker) -> {
                if (!session.registered) showRegistration(clicker, session);
                else showPasswordLogin(clicker, session, null);
            }));
        }
        buttons.add(button(isDiscordSrvLinkPurpose(purpose) ? "Check DiscordSRV Link Again" : "Request New Code",
                (view, clicker) -> startDiscordChallenge(clicker, session, purpose)));
        if (isDiscordSrvLinkPurpose(purpose)) {
            buttons.add(button("Back", (view, clicker) -> {
                challenges.remove(clicker.getUniqueId());
                if (purpose == ChallengePurpose.LINK_LOGIN) showPasswordLogin(clicker, session, null);
                else if (purpose == ChallengePurpose.REGISTER_LINK) showRegistration(clicker, session);
                else showRegistrationChoices(clicker, session);
            }));
        }
        String status = isDiscordSrvLinkPurpose(purpose)
                ? "This Pacifica-Core verification session expired. Complete linking through DiscordSRV, then check again."
                : "The verification code expired. Your account remains unauthenticated; request a new code to continue.";
        displayDialog(player, "expired Discord verification", () -> dialog("Verification Code Expired",
                status, List.of(), buttons));
    }

    private void usePasswordFallback(Player player, Session session, Challenge challenge) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || challenges.get(playerId) != challenge) return;
        boolean passwordEnabled = configuration.getBoolean("authentication.password-enabled", true);
        boolean discordRequired = configuration.getBoolean("authentication.require-discord-on-first-join", false);
        boolean discordLoginRequired = configuration.getBoolean("authentication.require-discord-on-login", false);
        if (!passwordEnabled || (discordRequired && !session.registered)
                || (discordLoginRequired && session.registered)) {
            player.sendMessage(ChatColor.RED + "Password authentication is not allowed for this account by server policy.");
            return;
        }
        if (!session.registered) {
            challenges.remove(playerId, challenge);
            showRegistration(player, session);
        } else if (session.method == AuthenticationMethod.PASSWORD) {
            challenges.remove(playerId, challenge);
            showPasswordLogin(player, session, null);
        } else {
            player.sendMessage(ChatColor.RED + "This account has no password credential. Continue with Discord verification.");
        }
    }

    private void resendDiscordCode(Player player, Session session, Challenge challenge) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || challenges.get(playerId) != challenge) return;
        long waitMillis = challenge.resendAt - System.currentTimeMillis();
        if (waitMillis > 0) {
            showDiscordVerification(player, session, challenge, dialogMessage("link-code-cooldown",
                    "Wait %seconds% seconds before requesting another code.")
                    .replace("%seconds%", String.valueOf(Math.max(1, (waitMillis + 999) / 1000))));
            return;
        }
        startDiscordChallenge(player, session, challenge.purpose);
    }

    private void submitDiscordCode(Player player, Session session, Challenge challenge, DialogResponseView view) {
        UUID playerId = player.getUniqueId();
        if (view == null || !isCurrent(playerId, session) || challenges.get(playerId) != challenge
                || !challenge.sessionId.equals(session.id) || challenge.processing
                || isDiscordSrvLinkPurpose(challenge.purpose)
                || (challenge.purpose != ChallengePurpose.LOGIN && challenge.purpose != ChallengePurpose.RECOVERY)) return;
        if (!challenge.delivered) {
            showDiscordVerification(player, session, challenge,
                    "Your private Discord server channel is still being prepared. Try again in a moment.");
            return;
        }
        if (System.currentTimeMillis() >= challenge.expiresAt) {
            showDiscordVerification(player, session, challenge,
                    "That verification code has expired. Request a fresh code.");
            return;
        }
        String suppliedCode = view.getText("code");
        if (suppliedCode == null || challenge.code == null || !challenge.code.equals(suppliedCode)) {
            int maxAttempts = Math.min(10, Math.max(1,
                    configuration.getInt("discord-linking.verification.max-attempts",
                            configuration.getInt("authentication.discord-code-max-attempts", 5))));
            if (++challenge.attempts >= maxAttempts) {
                challenges.remove(playerId, challenge);
                showDiscordDeliveryFailure(player, session, challenge.purpose,
                        new IllegalStateException("The verification attempt limit was reached. Request a new code after the cooldown."));
            } else {
                showDiscordVerification(player, session, challenge,
                        dialogMessage("invalid-link-code", "Incorrect code. Attempts remaining: %attempts%.")
                                .replace("%attempts%", String.valueOf(maxAttempts - challenge.attempts)));
            }
            return;
        }
        challenge.processing = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentLinkedId = discordBridge.resolveLinkedDiscordId(playerId);
                if (currentLinkedId == null || !currentLinkedId.equals(challenge.discordId)) {
                    throw new IllegalStateException("DiscordSRV no longer confirms the linked Discord identity for this Minecraft account.");
                }
                if (System.currentTimeMillis() >= challenge.expiresAt) {
                    throw new IllegalStateException("The verification challenge has expired.");
                }
                runOnMain(() -> {
                    if (!player.isOnline() || !isCurrent(playerId, session)
                            || challenges.get(playerId) != challenge
                            || System.currentTimeMillis() >= challenge.expiresAt) return;
                    challenges.remove(playerId, challenge);
                    if (challenge.purpose == ChallengePurpose.RECOVERY) {
                        RecoveryTicket ticket = new RecoveryTicket(session.id, System.currentTimeMillis() + 120_000L);
                        recoveryTickets.put(playerId, ticket);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (recoveryTickets.remove(playerId, ticket) && player.isOnline()
                                    && isCurrent(playerId, session)) {
                                showPasswordLogin(player, session,
                                        "Password recovery expired. Start recovery again to continue.");
                            }
                        }, 2400L);
                        showPasswordReset(player, session, ticket, null);
                    } else {
                        authenticate(player, session);
                        player.sendMessage(ChatColor.GREEN + "You are now authenticated.");
                    }
                });
            } catch (Exception | LinkageError exception) {
                plugin.getLogger().log(Level.WARNING, "AuthSystem rejected or could not complete Discord verification for player UUID "
                        + playerId + ". Authentication remains blocked.", exception);
                runOnMain(() -> {
                    if (player.isOnline() && isCurrent(playerId, session) && challenges.get(playerId) == challenge) {
                        challenge.processing = false;
                        showDiscordVerification(player, session, challenge,
                                "Verification could not be completed. Check your connection and submit again.");
                    }
                });
            }
        });
    }

    private void showDiscordDeliveryFailure(Player player, Session session, ChallengePurpose purpose, Throwable cause) {
        if (!isCurrent(player.getUniqueId(), session)) return;
        List<ActionButton> buttons = new java.util.ArrayList<>();
        if (passwordFallbackAvailable(session)) {
            buttons.add(button("Use Password", (view, clicker) -> {
                if (!session.registered) showRegistration(clicker, session);
                else if (session.method == AuthenticationMethod.PASSWORD) showPasswordLogin(clicker, session, null);
                else clicker.sendMessage(ChatColor.RED + "This account has no password credential.");
            }));
        }
        buttons.add(button("Try Discord Again", (view, clicker) -> startDiscordChallenge(clicker, session, purpose)));
        if (isDiscordSrvLinkPurpose(purpose)) {
            buttons.add(button("Back", (view, clicker) -> {
                if (purpose == ChallengePurpose.LINK_LOGIN) showPasswordLogin(clicker, session, null);
                else showRegistrationChoices(clicker, session);
            }));
        }
        String status = isDiscordSrvLinkPurpose(purpose)
                ? "The DiscordSRV account-linking workflow could not be started or confirmed."
                : "A fresh login verification code could not be delivered. Your account remains unauthenticated.";
        displayDialog(player, "Discord delivery failure", () -> dialog("Discord Verification Unavailable",
                List.of(DialogBody.plainMessage(Component.text(status)),
                        DialogBody.plainMessage(Component.text(cause.getMessage() == null
                                ? "Check your DiscordSRV configuration and try again." : cause.getMessage(), NamedTextColor.RED))),
                List.of(), buttons));
    }

    private void showPasswordReset(Player player, Session session, RecoveryTicket ticket, String error) {
        UUID playerId = player.getUniqueId();
        if (!isCurrent(playerId, session) || recoveryTickets.get(playerId) != ticket) return;
        if (System.currentTimeMillis() >= ticket.expiresAt) {
            recoveryTickets.remove(playerId, ticket);
            showPasswordLogin(player, session, "Password recovery expired. Start recovery again to continue.");
            return;
        }
        List<DialogBody> body = new java.util.ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Enter and confirm your new account password.")));
        if (error != null) body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED)));
        displayDialog(player, "password recovery", () -> dialog("Reset Password", body,
                List.of(DialogInput.text("password", Component.text("New password"))
                                .maxLength(maximumPasswordLength()).width(240).build(),
                        DialogInput.text("confirm", Component.text("Confirm new password"))
                                .maxLength(maximumPasswordLength()).width(240).build()),
                List.of(button("Save New Password", (view, clicker) ->
                        resetPassword(clicker, session, ticket, view)))));
    }

    private void resetPassword(Player player, Session session, RecoveryTicket ticket, DialogResponseView view) {
        UUID playerId = player.getUniqueId();
        if (view == null || !isCurrent(playerId, session) || recoveryTickets.get(playerId) != ticket
                || !ticket.sessionId.equals(session.id)) return;
        if (System.currentTimeMillis() >= ticket.expiresAt) {
            showPasswordReset(player, session, ticket, null);
            return;
        }
        String password = view.getText("password");
        String confirmation = view.getText("confirm");
        int minimumLength = Math.min(128, Math.max(8, configuration.getInt("password.minimum-length", 10)));
        int maximumLength = maximumPasswordLength();
        if (password == null || confirmation == null || password.length() < minimumLength || password.length() > maximumLength) {
            showPasswordReset(player, session, ticket, "Choose a password between " + minimumLength + " and " + maximumLength + " characters.");
            return;
        }
        if (!password.equals(confirmation)) {
            showPasswordReset(player, session, ticket, "Those passwords do not match.");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String encodedHash = hashPassword(password);
                if (!repository.updatePassword(playerId, encodedHash)) {
                    throw new IllegalStateException("The password account could not be updated.");
                }
                runOnMain(() -> {
                    if (!player.isOnline() || !isCurrent(playerId, session)
                            || recoveryTickets.get(playerId) != ticket) return;
                    recoveryTickets.remove(playerId, ticket);
                    session.passwordHash = encodedHash;
                    authenticate(player, session);
                    player.sendMessage(ChatColor.GREEN + "Your password was reset and you are now authenticated.");
                });
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "AuthSystem could not complete password recovery for player UUID "
                        + playerId + ".", exception);
                runOnMain(() -> {
                    if (player.isOnline() && isCurrent(playerId, session)
                            && recoveryTickets.get(playerId) == ticket) {
                        showPasswordReset(player, session, ticket, "The password could not be saved. Try again.");
                    }
                });
            }
        });
    }

    private void verifyPassword(Player player, Session session, DialogResponseView view) {
        if (view == null || !isCurrent(player.getUniqueId(), session)) return;
        if (!configuration.getBoolean("authentication.password-enabled", true)) {
            showAuthentication(player, session);
            return;
        }
        FailureState failureState = failures.computeIfAbsent(player.getUniqueId(), ignored -> new FailureState());
        long now = System.currentTimeMillis();
        synchronized (failureState) {
            if (failureState.lockedUntil > now) {
                showPasswordLogin(player, session, "Too many attempts. Try again in "
                        + Math.max(1, (failureState.lockedUntil - now + 999) / 1000) + " seconds.");
                return;
            }
            if (now - failureState.lastAttempt < ATTEMPT_INTERVAL_MILLIS) {
                showPasswordLogin(player, session, "Please wait before trying again.");
                return;
            }
            failureState.lastAttempt = now;
        }
        String supplied = view.getText("password");
        if (supplied == null || supplied.length() > maximumPasswordLength()) supplied = "";
        String password = supplied;
        char[] secret = password.toCharArray();
        java.util.Arrays.fill(secret, '\0');
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean valid = verifyPassword(password, session.passwordHash);
            runOnMain(() -> {
                if (!player.isOnline() || !isCurrent(player.getUniqueId(), session)) return;
                if (valid) {
                    failures.remove(player.getUniqueId());
                    authenticate(player, session);
                    player.sendMessage(ChatColor.GREEN + "You are now authenticated.");
                    return;
                }
                int attempts;
                synchronized (failureState) {
                    attempts = ++failureState.attempts;
                    if (attempts >= MAX_ATTEMPTS) {
                        failureState.attempts = 0;
                        failureState.lockedUntil = System.currentTimeMillis() + 300_000L;
                    }
                }
                if (attempts >= MAX_ATTEMPTS) {
                    player.kickPlayer(ChatColor.RED + "Too many incorrect password attempts.");
                } else {
                    showPasswordLogin(player, session, "Incorrect password. Attempts remaining: "
                            + (MAX_ATTEMPTS - attempts) + ".");
                }
            });
        });
    }

    private void authenticate(Player player, Session session) {
        if (!isCurrent(player.getUniqueId(), session)) return;
        sessions.remove(player.getUniqueId(), session);
        challenges.remove(player.getUniqueId());
        recoveryTickets.remove(player.getUniqueId());
        discordResendLimits.remove(player.getUniqueId());
        authenticated.put(player.getUniqueId(), session);
        player.closeInventory();
        processPendingDiscordLinkReward(player);
        processPendingDailyReward(player, null);
    }

    private Location authenticationJoinLocation() {
        String prefix = "auth-system.join-location.";
        if (!configuration.getBoolean(prefix + "enabled", false)) return null;

        String worldName = configuration.getString(prefix + "world", "");
        if (worldName == null || worldName.isBlank()) return null;
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = configuration.getDouble(prefix + "x", Double.NaN);
        double y = configuration.getDouble(prefix + "y", Double.NaN);
        double z = configuration.getDouble(prefix + "z", Double.NaN);
        double yaw = configuration.getDouble(prefix + "yaw", Double.NaN);
        double pitch = configuration.getDouble(prefix + "pitch", Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(yaw) || !Double.isFinite(pitch)
                || !Float.isFinite((float) yaw) || !Float.isFinite((float) pitch)) return null;
        return new Location(world, x, y, z, (float) yaw, (float) pitch);
    }

    private boolean isUsableLocation(Location location) {
        if (location == null || location.getWorld() == null
                || Bukkit.getWorld(location.getWorld().getName()) == null) return false;
        return Double.isFinite(location.getX()) && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ()) && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }

    private void showUnavailable(Player player, String message) {
        displayDialog(player, "unavailable-authentication", () -> dialog("Authentication Unavailable", message, List.of(),
                List.of(button("Try again", (view, clicker) -> beginSession(clicker)))));
    }

    private void displayDialog(Player player, String operation, Supplier<Dialog> dialogFactory) {
        Dialog dialog;
        try {
            dialog = dialogFactory.get();
        } catch (RuntimeException | LinkageError exception) {
            logDialogFailure(player, operation, "construction", exception);
            return;
        }
        try {
            player.showDialog(dialog);
        } catch (RuntimeException | LinkageError exception) {
            logDialogFailure(player, operation, "display", exception);
        }
    }

    private void logDialogFailure(Player player, String operation, String stage, Throwable exception) {
        boolean dialogApiAvailable;
        try {
            Class.forName("io.papermc.paper.dialog.Dialog", false, plugin.getClass().getClassLoader());
            dialogApiAvailable = true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            dialogApiAvailable = false;
        }
        plugin.getLogger().log(Level.SEVERE, "AuthSystem failed during Dialog " + stage + " for " + operation
                + " (Paper Dialog API available=" + dialogApiAvailable + ", server=" + Bukkit.getVersion()
                + "). Authentication remains blocked.", exception);
        if (player.isOnline()) {
            player.sendMessage(ChatColor.RED + "Authentication could not be displayed. Contact an administrator.");
        }
    }

    private Dialog dialog(String title, String message, List<io.papermc.paper.registry.data.dialog.input.DialogInput> inputs,
                          List<ActionButton> buttons) {
        return dialog(title, List.of(DialogBody.plainMessage(Component.text(message))), inputs, buttons);
    }

    private Dialog dialog(String title, List<DialogBody> body,
                          List<io.papermc.paper.registry.data.dialog.input.DialogInput> inputs,
                          List<ActionButton> buttons) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.AQUA))
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .canCloseWithEscape(false)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
    }

    private ActionButton button(String label, DialogCallback callback) {
        return ActionButton.builder(Component.text(label))
                .width(180)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player player)) return;
                    runOnMain(() -> {
                        try {
                            callback.accept(view, player);
                        } catch (RuntimeException | LinkageError exception) {
                            plugin.getLogger().log(Level.SEVERE, "AuthSystem failed during response processing for button '"
                                    + label + "'. Authentication remains blocked.", exception);
                            if (player.isOnline()) {
                                player.sendMessage(ChatColor.RED + "Authentication could not be completed. Contact an administrator.");
                            }
                        }
                    });
                }, ClickCallback.Options.builder().uses(1).build()))
                .build();
    }

    private String hashPassword(String password) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, HASH_ITERATIONS, HASH_BITS);
        java.util.Arrays.fill(chars, '\0');
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return "pbkdf2-sha256$" + HASH_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    private boolean verifyPassword(String password, String encoded) {
        if (encoded == null) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != SALT_BYTES || expected.length != HASH_BITS / 8) return false;
            char[] chars = password.toCharArray();
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, HASH_BITS);
            java.util.Arrays.fill(chars, '\0');
            try {
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } finally {
                spec.clearPassword();
            }
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean discordLinkingEnabled() {
        return configuration.getBoolean("discord-linking.enabled", true);
    }

    private int maximumPasswordLength() {
        int minimumLength = Math.min(128, Math.max(8, configuration.getInt("password.minimum-length", 10)));
        return Math.min(128, Math.max(minimumLength, configuration.getInt("password.maximum-length", 128)));
    }

    private int discordLinkLimit() {
        if (!discordLinkingEnabled()) return 0;
        return Math.min(1000, Math.max(1,
                configuration.getInt("discord-linking.max-minecraft-accounts-per-discord", 1)));
    }

    private int ipLimit(Player player) {
        if (!configuration.getBoolean("ip-limits.enabled", true)) return 0;
        String bypassPermission = configuration.getString("ip-limits.bypass-permission",
                "pacifica.auth.iplimit.bypass");
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) return 0;
        return Math.min(1000, Math.max(1, configuration.getInt("ip-limits.max-accounts-per-ip", 3)));
    }

    private String message(String path, String fallback) {
        String configured = configuration.getString("messages." + path, fallback);
        return ChatColor.translateAlternateColorCodes('&', configured == null ? fallback : configured);
    }

    private String linkRewardMessage(String path, String fallback) {
        String configured = configuration.getString("discord-linking.rewards.messages." + path, fallback);
        return ChatColor.translateAlternateColorCodes('&', configured == null ? fallback : configured);
    }

    private String dialogMessage(String path, String fallback) {
        String configured = configuration.getString("messages." + path, fallback);
        return configured == null ? fallback : configured;
    }

    private byte[] clientIpAddress(Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        if (socketAddress == null || socketAddress.getAddress() == null) return null;
        byte[] address = socketAddress.getAddress().getAddress();
        if (address.length == 16) {
            boolean mappedIpv4 = true;
            for (int index = 0; index < 10; index++) mappedIpv4 &= address[index] == 0;
            mappedIpv4 &= address[10] == (byte) 0xff && address[11] == (byte) 0xff;
            if (mappedIpv4) return Arrays.copyOfRange(address, 12, 16);
        }
        return address;
    }

    private String hashIp(byte[] address) throws Exception {
        if (address == null) return null;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(loadIpHashKey(), "HmacSHA256"));
        mac.update("pacifica-core-auth-ip-v1".getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac.doFinal(address));
    }

    private synchronized byte[] loadIpHashKey() throws IOException {
        if (ipHashKey != null) return ipHashKey.clone();
        Path keyPath = plugin.getDataFolder().toPath().resolve("AuthSystem/ip-hash.key");
        Files.createDirectories(keyPath.getParent());
        byte[] key;
        if (Files.exists(keyPath)) {
            key = Files.readAllBytes(keyPath);
        } else {
            key = new byte[32];
            secureRandom.nextBytes(key);
            Files.write(keyPath, key);
        }
        if (key.length != 32) throw new IOException("The stored AuthSystem IP hashing key is invalid.");
        ipHashKey = key.clone();
        return ipHashKey.clone();
    }

    private boolean isCurrent(UUID playerId, Session session) {
        return sessions.get(playerId) == session || authenticated.get(playerId) == session;
    }

    private boolean isDiscordSrvLinkPurpose(ChallengePurpose purpose) {
        return purpose == ChallengePurpose.REGISTER || purpose == ChallengePurpose.REGISTER_LINK
                || purpose == ChallengePurpose.LINK_LOGIN;
    }

    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    private boolean restricted(Player player) {
        return !player.hasPermission(BYPASS_PERMISSION) && !isAuthenticated(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (restricted(event.getPlayer()) && event.getTo() != null
                && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) event.setTo(event.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PendingJoinTeleport pendingTeleport = pendingJoinTeleports.get(event.getPlayer().getUniqueId());
        if (restricted(event.getPlayer()) && (pendingTeleport == null
                || !pendingTeleport.destination.equals(event.getTo()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) { if (restricted(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!restricted(event.getPlayer())) return;
        String command = event.getMessage().split(" ", 2)[0].substring(1);
        if (command.equalsIgnoreCase("logout") || command.equalsIgnoreCase("pacifica-core:logout")) return;
        event.setCancelled(true);
    }

    private interface DialogCallback {
        void accept(DialogResponseView view, Player player);
    }

    private enum ChallengePurpose {
        REGISTER,
        REGISTER_LINK,
        LINK_LOGIN,
        LOGIN,
        RECOVERY
    }

    private static final class Challenge {
        private final UUID sessionId;
        private volatile String code;
        private volatile String discordSrvCode;
        private final ChallengePurpose purpose;
        private final long expiresAt;
        private volatile long resendAt;
        private volatile String discordId;
        private volatile boolean delivered;
        private int attempts;
        private volatile boolean processing;
        private final byte[] clientAddress;
        private final int ipLimit;

        private Challenge(UUID sessionId, String code, ChallengePurpose purpose, long expiresAt, long resendAt,
                          byte[] clientAddress, int ipLimit) {
            this.sessionId = sessionId;
            this.code = code;
            this.purpose = purpose;
            this.expiresAt = expiresAt;
            this.resendAt = resendAt;
            this.clientAddress = clientAddress;
            this.ipLimit = ipLimit;
        }
    }

    private static final class RecoveryTicket {
        private final UUID sessionId;
        private final long expiresAt;

        private RecoveryTicket(UUID sessionId, long expiresAt) {
            this.sessionId = sessionId;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Session {
        private final UUID id;
        private volatile boolean registered;
        private volatile AuthenticationMethod method;
        private volatile String passwordHash;
        private String pendingDiscordId;
        private Session(UUID id) { this.id = id; }
    }

    private static final class PendingJoinTeleport {
        private final Location destination;

        private PendingJoinTeleport(Location destination) {
            this.destination = destination;
        }
    }

    private static final class FailureState {
        private int attempts;
        private long lastAttempt;
        private long lockedUntil;
    }
}