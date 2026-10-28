package com.districtx.pacificacore.auth;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class DiscordSRVAuthBridge {
    private volatile Plugin discordPlugin;
    private volatile DiscordSRVPublicApi api;
    private volatile Throwable apiLoadFailure;
    private volatile BiConsumer<UUID, String> linkedHandler;
    private volatile BiConsumer<UUID, String> unlinkedHandler;
    private volatile BiConsumer<String, Consumer<String>> dailyClaimHandler;

    void setAccountLinkHandlers(BiConsumer<UUID, String> linkedHandler,
                                BiConsumer<UUID, String> unlinkedHandler) {
        this.linkedHandler = linkedHandler;
        this.unlinkedHandler = unlinkedHandler;
        DiscordSRVPublicApi current = api;
        if (current != null) current.setAccountLinkHandlers(linkedHandler, unlinkedHandler);
    }

    void setDailyClaimHandler(BiConsumer<String, Consumer<String>> handler) {
        dailyClaimHandler = handler;
        DiscordSRVPublicApi current = api;
        if (current != null) current.setDailyClaimHandler(handler);
    }

    boolean registerDailyClaimCommand(String guildId, String channelId) {
        try {
            return readyApi().registerDailyClaimCommand(guildId, channelId);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    String resolveLinkedDiscordId(UUID playerId) throws Exception {
        return readyApi().resolveLinkedDiscordId(playerId);
    }

    String getOrCreateNativeLinkCode(UUID playerId) throws Exception {
        return readyApi().getOrCreateNativeLinkCode(playerId);
    }

    String resolveDiscordUsername(String discordId) throws Exception {
        return readyApi().resolveDiscordUsername(discordId);
    }

    CompletableFuture<Void> deliverLoginCode(UUID playerId, String playerName, String guildId,
                                             String categoryId, String discordId, String code,
                                             boolean createChannelIfMissing, boolean reuseExistingChannel,
                                             BooleanSupplier challengeIsCurrent) {
        try {
            return readyApi().deliverLoginCode(playerId, playerName, guildId, categoryId, discordId, code,
                    createChannelIfMissing, reuseExistingChannel, challengeIsCurrent);
        } catch (Exception | LinkageError exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    CompletableFuture<Void> deleteVerificationChannel(UUID playerId, String guildId, String categoryId,
                                                      String discordId) {
        try {
            return readyApi().deleteVerificationChannel(playerId, guildId, categoryId, discordId);
        } catch (Exception | LinkageError exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    void close() {
        DiscordSRVPublicApi current = api;
        if (current == null) return;
        try {
            current.close();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    String diagnosticState() {
        discordPlugin = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordPlugin == null) return "not installed";
        if (!discordPlugin.isEnabled()) return "installed but disabled";
        try {
            return "installed, version=" + discordPlugin.getDescription().getVersion() + ", connection="
                    + readyApi().connectionStatus();
        } catch (RuntimeException | LinkageError exception) {
            return "installed, API unavailable (" + exception.getClass().getSimpleName() + ")";
        }
    }

    private synchronized DiscordSRVPublicApi readyApi() {
        discordPlugin = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordPlugin == null || !discordPlugin.isEnabled()) {
            throw new IllegalStateException("DiscordSRV is not installed or enabled.");
        }
        if (api == null) {
            try {
                Class<?> adapterClass = Class.forName(
                        "com.districtx.pacificacore.auth.DiscordSRVAccountLinkAdapter", true, getClass().getClassLoader());
                api = (DiscordSRVPublicApi) adapterClass.getDeclaredConstructor().newInstance();
                api.setAccountLinkHandlers(linkedHandler, unlinkedHandler);
                api.setDailyClaimHandler(dailyClaimHandler);
                apiLoadFailure = null;
            } catch (ReflectiveOperationException | LinkageError exception) {
                apiLoadFailure = exception;
                throw new IllegalStateException(
                        "DiscordSRV's public account-linking API could not be loaded. Verify the installed DiscordSRV version.",
                        exception);
            }
        }
        return api;
    }

    interface DiscordSRVPublicApi {
        String resolveLinkedDiscordId(UUID playerId) throws Exception;

        String getOrCreateNativeLinkCode(UUID playerId) throws Exception;

        String resolveDiscordUsername(String discordId) throws Exception;

        CompletableFuture<Void> deliverLoginCode(UUID playerId, String playerName, String guildId,
                                                 String categoryId, String discordId, String code,
                                                 boolean createChannelIfMissing, boolean reuseExistingChannel,
                                                 BooleanSupplier challengeIsCurrent);

        CompletableFuture<Void> deleteVerificationChannel(UUID playerId, String guildId, String categoryId,
                                                          String discordId);

        void setAccountLinkHandlers(BiConsumer<UUID, String> linkedHandler,
                                    BiConsumer<UUID, String> unlinkedHandler);

        void setDailyClaimHandler(BiConsumer<String, Consumer<String>> handler);

        boolean registerDailyClaimCommand(String guildId, String channelId);

        void close();

        String connectionStatus();
    }
}