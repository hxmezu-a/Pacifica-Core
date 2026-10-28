package com.districtx.pacificacore.auth;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.ApiManager;
import github.scarsz.discordsrv.api.events.AccountLinkedEvent;
import github.scarsz.discordsrv.api.events.AccountUnlinkedEvent;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Category;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.PermissionOverride;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscordSRVAccountLinkAdapter implements DiscordSRVAuthBridge.DiscordSRVPublicApi {
    private static final String MESSAGE_PREFIX = "DistrictX Auth > ";
    private static final Pattern DISCORD_ERROR = Pattern.compile("\\b([0-9]{5}):\\s*([^\\r\\n]+)");
    private volatile BiConsumer<UUID, String> linkedHandler;
    private volatile BiConsumer<UUID, String> unlinkedHandler;
    private volatile BiConsumer<String, Consumer<String>> dailyClaimHandler;
    private volatile String dailyClaimGuildId;
    private volatile String dailyClaimChannelId;
    private volatile JDA dailyClaimJda;
    private ApiManager subscribedApi;
    private final ListenerAdapter dailyClaimListener = new ListenerAdapter() {
        @Override
        public void onSlashCommand(SlashCommandEvent event) {
            if (!"claim".equals(event.getName())) return;
            String guildId = dailyClaimGuildId;
            String channelId = dailyClaimChannelId;
            if (!event.isFromGuild()) {
                event.reply("Use /claim in the configured server channel.").queue();
                return;
            }
            if (guildId == null || channelId == null) {
                event.deferReply(true).queue(hook -> hook.editOriginal("Daily rewards are unavailable right now.").queue());
                return;
            }
            if (!guildId.equals(event.getGuild().getId()) || !channelId.equals(event.getTextChannel().getId())) {
                event.deferReply(true).queue(hook -> hook.editOriginal(
                        "Use /claim in the designated daily-reward channel.").queue());
                return;
            }
            event.deferReply(true).queue(hook -> {
                BiConsumer<String, Consumer<String>> handler = dailyClaimHandler;
                if (handler == null) {
                    hook.editOriginal("Daily rewards are unavailable right now.").queue();
                    return;
                }
                try {
                    handler.accept(event.getUser().getId(), response -> hook.editOriginal(response).queue());
                } catch (RuntimeException exception) {
                    hook.editOriginal("Your reward could not be processed. Please try again later.").queue();
                }
            });
        }
    };

    @Override
    public String resolveLinkedDiscordId(UUID playerId) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DiscordSRV account-link status must be resolved asynchronously.");
        }
        ensureSubscribed();
        String discordId = readyDiscordSrv().getAccountLinkManager().getDiscordIdBypassCache(playerId);
        if (discordId == null) return null;
        if (!discordId.matches("[0-9]{17,20}")) {
            throw new IllegalStateException("DiscordSRV returned an invalid linked Discord user ID.");
        }
        return discordId;
    }

    @Override
    public String getOrCreateNativeLinkCode(UUID playerId) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DiscordSRV link codes must be obtained asynchronously.");
        }
        ensureSubscribed();
        AccountLinkManager accountLinks = readyDiscordSrv().getAccountLinkManager();
        if (accountLinks.getDiscordIdBypassCache(playerId) != null) {
            throw new IllegalStateException("This Minecraft account is already linked through DiscordSRV.");
        }
        synchronized (accountLinks) {
            for (java.util.Map.Entry<String, UUID> entry : accountLinks.getLinkingCodes().entrySet()) {
                if (playerId.equals(entry.getValue())) return entry.getKey();
            }
            String code = accountLinks.generateCode(playerId);
            if (code == null || !code.matches("[0-9]{4}")
                    || !playerId.equals(accountLinks.getLinkingCodes().get(code))) {
                throw new IllegalStateException("DiscordSRV did not return a link code associated with this Minecraft account.");
            }
            return code;
        }
    }

    @Override
    public String resolveDiscordUsername(String discordId) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Discord account names must be resolved asynchronously.");
        }
        if (discordId == null || !discordId.matches("[0-9]{17,20}")) {
            throw new IllegalArgumentException("The Discord user ID is invalid.");
        }
        ensureSubscribed();
        return readyDiscordSrv().getJda().retrieveUserById(discordId).submit().get().getName();
    }

    @Override
    public synchronized boolean registerDailyClaimCommand(String guildId, String channelId) {
        if (guildId == null || !guildId.matches("[0-9]{17,20}")
                || channelId == null || !channelId.matches("[0-9]{17,20}")) {
            dailyClaimGuildId = null;
            dailyClaimChannelId = null;
            return false;
        }
        try {
            DiscordSRV discordSrv = readyDiscordSrv();
            JDA jda = discordSrv.getJda();
            Guild guild = jda.getGuildById(guildId);
            if (guild == null || guild.getTextChannelById(channelId) == null) {
                dailyClaimGuildId = guildId;
                dailyClaimChannelId = null;
                return false;
            }
            if (guildId.equals(dailyClaimGuildId) && channelId.equals(dailyClaimChannelId)
                    && jda == dailyClaimJda) return true;
            if (dailyClaimJda != null) dailyClaimJda.removeEventListener(dailyClaimListener);
            dailyClaimJda = jda;
            dailyClaimGuildId = guildId;
            dailyClaimChannelId = channelId;
            jda.addEventListener(dailyClaimListener);
            guild.upsertCommand("claim", "Claim your daily Minecraft reward").queue();
            return true;
        } catch (RuntimeException | LinkageError exception) {
            dailyClaimGuildId = null;
            dailyClaimChannelId = null;
            dailyClaimJda = null;
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> deliverLoginCode(UUID playerId, String playerName, String guildId,
                                                     String categoryId, String discordId, String code,
                                                     boolean createChannelIfMissing, boolean reuseExistingChannel,
                                                     BooleanSupplier challengeIsCurrent) {
        final DiscordSRV discordSrv;
        try {
            if (playerId == null || playerName == null || playerName.isBlank()) {
                throw new IllegalStateException("The Minecraft player identity is unavailable.");
            }
            if (discordId == null || !discordId.matches("[0-9]{17,20}")) {
                throw new IllegalStateException("The linked Discord account ID is invalid.");
            }
            discordSrv = readyDiscordSrv();
            ensureSubscribed();
            if (!challengeIsCurrent.getAsBoolean()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("The verification challenge is no longer active."));
            }
        } catch (RuntimeException | LinkageError exception) {
            return CompletableFuture.failedFuture(exception);
        }

        return privateVerificationChannel(discordSrv, playerId, playerName, guildId, categoryId, discordId,
                createChannelIfMissing, reuseExistingChannel, challengeIsCurrent)
                .thenCompose(verificationChannel -> {
                    if (!challengeIsCurrent.getAsBoolean()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("The verification challenge is no longer active."));
                    }
                    return named("SEND_PRIVATE_CHANNEL_LOGIN_CODE",
                            verificationChannel.channel().sendMessage(message("Login verification code: `" + code
                                    + "`\nThis code expires shortly and can only be used once.")).submit())
                            .thenApply(ignored -> (Void) null);
                });
    }

    private CompletableFuture<VerificationChannel> privateVerificationChannel(DiscordSRV discordSrv, UUID playerId,
                                                                              String playerName, String guildId,
                                                                              String categoryId, String discordId,
                                                                              boolean createChannelIfMissing,
                                                                              boolean reuseExistingChannel,
                                                                              BooleanSupplier challengeIsCurrent) {
        if (guildId == null || !guildId.matches("[0-9]{17,20}")
                || categoryId == null || !categoryId.matches("[0-9]{17,20}")) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Configure valid discord-linking.guild-id and discord-linking.verification-category-id values."));
        }
        JDA jda = discordSrv.getJda();
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "DiscordSRV's bot is not connected to the configured verification guild."));
        }
        Category category = guild.getCategoryById(categoryId);
        if (category == null || !category.getGuild().getId().equals(guild.getId())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "The configured verification category was not found in the configured Discord guild."));
        }
        if (!reuseExistingChannel && !createChannelIfMissing) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Private verification channels are configured for neither reuse nor creation."));
        }
        return guild.retrieveMemberById(discordId).submit().thenCompose(member -> {
            if (member.getUser().isBot() || member.getId().equals(guild.getSelfMember().getId())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Verification channels can only be assigned to a human Discord account."));
            }
            if (!challengeIsCurrent.getAsBoolean()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("The verification challenge is no longer active."));
            }
            String ownerMarker = ownerMarker(playerId, discordId);
            if (reuseExistingChannel) {
                for (TextChannel channel : category.getTextChannels()) {
                    if (!ownerMarker.equals(channel.getTopic())) continue;
                    PermissionOverride currentMemberOverride = channel.getPermissionOverride(member);
                    boolean currentlyCanSend = currentMemberOverride != null
                            && currentMemberOverride.getAllowed().contains(Permission.MESSAGE_WRITE);
                    if (!hasRequiredPrivatePermissions(channel, category, guild, member, currentlyCanSend)) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "The existing verification channel has unsafe ownership or permissions; no code was sent."));
                    }
                    return configureTargetPermissions(channel, member, category, guild)
                            .thenApply(ignored -> new VerificationChannel(channel, member));
                }
            }
            if (!createChannelIfMissing) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "No eligible private verification channel exists for this account."));
            }
            if (guild.getRoles().size() + 2 > 100) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "The verification guild has too many roles to create a channel with safe permission overwrites."));
            }
            Member botMember = guild.getSelfMember();
            Set<Permission> denied = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_WRITE);
            var action = guild.createTextChannel(availableChannelName(category, playerName, playerId))
                    .setParent(category)
                    .setTopic(ownerMarker);
            for (Role role : guild.getRoles()) {
                action.addPermissionOverride(role, Collections.emptySet(), denied);
            }
            action.addPermissionOverride(member,
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                    EnumSet.of(Permission.MESSAGE_WRITE));
            action.addPermissionOverride(botMember,
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, Permission.MESSAGE_WRITE),
                    Collections.emptySet());
            return named("CREATE_PRIVATE_VERIFICATION_CHANNEL", action.submit()).thenApply(channel -> {
                if (!challengeIsCurrent.getAsBoolean()
                        || !hasRequiredPrivatePermissions(channel, category, guild, member, false)) {
                    throw new IllegalStateException(
                            "The new verification channel's permissions could not be verified; no code was sent.");
                }
                return new VerificationChannel(channel, member);
            });
        });
    }

    private static String ownerMarker(UUID playerId, String discordId) {
        return "pacifica-auth:" + playerId + ":" + discordId;
    }

    private static String message(String content) {
        return MESSAGE_PREFIX + content;
    }

    private static String availableChannelName(Category category, String playerName, UUID playerId) {
        String normalized = playerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (normalized.isBlank()) normalized = "player";
        String base = normalized + "-verification";
        if (!channelNameInUse(category, base)) return base;
        String suffix = playerId.toString().substring(0, 8);
        String unique = base + "-" + suffix;
        int attempt = 2;
        while (channelNameInUse(category, unique)) unique = base + "-" + suffix + "-" + attempt++;
        return unique;
    }

    private static boolean channelNameInUse(Category category, String name) {
        for (TextChannel channel : category.getTextChannels()) {
            if (channel.getName().equals(name)) return true;
        }
        return false;
    }

    private static boolean hasRequiredPrivatePermissions(TextChannel channel, Category category, Guild guild,
                                                         Member member, boolean allowTargetToSend) {
        if (channel.getParent() == null || !channel.getParent().getId().equals(category.getId())) return false;
        PermissionOverride publicOverride = channel.getPermissionOverride(guild.getPublicRole());
        PermissionOverride memberOverride = channel.getPermissionOverride(member);
        PermissionOverride botOverride = channel.getPermissionOverride(guild.getSelfMember());
        Set<Permission> memberAllowed = allowTargetToSend
                ? EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, Permission.MESSAGE_WRITE)
                : EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY);
        Set<Permission> memberDenied = allowTargetToSend
                ? Set.of() : EnumSet.of(Permission.MESSAGE_WRITE);
        if (!has(publicOverride, Set.of(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_WRITE))
                || !has(memberOverride, memberAllowed, memberDenied)
                || !has(botOverride, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_WRITE), Set.of())) return false;
        for (Role role : guild.getRoles()) {
            PermissionOverride override = channel.getPermissionOverride(role);
            if (override == null || !override.getDenied().contains(Permission.VIEW_CHANNEL)) return false;
        }
        for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
            Member overriddenMember = override.getMember();
            if (overriddenMember != null && !overriddenMember.getId().equals(member.getId())
                    && !overriddenMember.getId().equals(guild.getSelfMember().getId())
                    && override.getAllowed().contains(Permission.VIEW_CHANNEL)) return false;
        }
        return true;
    }

    private static CompletableFuture<Void> configureTargetPermissions(TextChannel channel, Member member,
                                                                       Category category, Guild guild) {
        var action = channel.upsertPermissionOverride(member)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)
                .deny(Permission.MESSAGE_WRITE);
        return named("CONFIGURE_PRIVATE_CHANNEL_PERMISSIONS", action.submit()).thenAccept(ignored -> {
            if (!hasRequiredPrivatePermissions(channel, category, guild, member, false)) {
                throw new IllegalStateException("The private channel's player permissions could not be verified.");
            }
        });
    }

    private static boolean has(PermissionOverride override, Set<Permission> allowed, Set<Permission> denied) {
        return override != null && override.getAllowed().containsAll(allowed)
                && override.getDenied().containsAll(denied);
    }

    @Override
    public CompletableFuture<Void> deleteVerificationChannel(UUID playerId, String guildId, String categoryId,
                                                             String discordId) {
        try {
            if (playerId == null || (discordId != null && !discordId.matches("[0-9]{17,20}"))) {
                throw new IllegalStateException("The verification channel owner could not be resolved.");
            }
            DiscordSRV discordSrv = readyDiscordSrv();
            if (guildId == null || !guildId.matches("[0-9]{17,20}")
                    || categoryId == null || !categoryId.matches("[0-9]{17,20}")) {
                throw new IllegalStateException("The configured verification guild or category ID is invalid.");
            }
            Guild guild = discordSrv.getJda().getGuildById(guildId);
            Category category = guild == null ? null : guild.getCategoryById(categoryId);
            if (category == null || !category.getGuild().getId().equals(guildId)) {
                throw new IllegalStateException("The configured verification category is unavailable.");
            }
            String marker = discordId == null ? null : ownerMarker(playerId, discordId);
            String ownerPrefix = "pacifica-auth:" + playerId + ":";
            for (TextChannel channel : category.getTextChannels()) {
                String topic = channel.getTopic();
                if (topic == null || (marker == null ? !topic.startsWith(ownerPrefix) : !marker.equals(topic))) continue;
                return named("DELETE_PRIVATE_VERIFICATION_CHANNEL", channel.delete().submit())
                        .thenApply(ignored -> (Void) null);
            }
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException | LinkageError exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public void setAccountLinkHandlers(BiConsumer<UUID, String> linkedHandler,
                                       BiConsumer<UUID, String> unlinkedHandler) {
        this.linkedHandler = linkedHandler;
        this.unlinkedHandler = unlinkedHandler;
    }

    @Override
    public void setDailyClaimHandler(BiConsumer<String, Consumer<String>> handler) {
        this.dailyClaimHandler = handler;
    }

    @Subscribe
    public void onAccountLinked(AccountLinkedEvent event) {
        BiConsumer<UUID, String> handler = linkedHandler;
        if (handler != null && event.getPlayer().getUniqueId() != null && event.getUser() != null) {
            handler.accept(event.getPlayer().getUniqueId(), event.getUser().getId());
        }
    }

    @Subscribe
    public void onAccountUnlinked(AccountUnlinkedEvent event) {
        BiConsumer<UUID, String> handler = unlinkedHandler;
        if (handler != null && event.getPlayer().getUniqueId() != null) {
            handler.accept(event.getPlayer().getUniqueId(), event.getDiscordId());
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (dailyClaimJda != null) {
                dailyClaimJda.removeEventListener(dailyClaimListener);
                dailyClaimJda = null;
                dailyClaimGuildId = null;
                dailyClaimChannelId = null;
            }
            if (subscribedApi != null) {
                try {
                    subscribedApi.unsubscribe(this);
                } finally {
                    subscribedApi = null;
                }
            }
        }
    }

    @Override
    public String connectionStatus() {
        DiscordSRV discordSrv = readyDiscordSrv();
        JDA jda = discordSrv.getJda();
        return jda == null ? "unavailable" : jda.getStatus().name();
    }

    private synchronized void ensureSubscribed() {
        readyDiscordSrv();
        ApiManager currentApi = DiscordSRV.api;
        if (currentApi == null) throw new IllegalStateException("DiscordSRV's public event API is not ready.");
        if (subscribedApi != currentApi) {
            if (subscribedApi != null) subscribedApi.unsubscribe(this);
            currentApi.subscribe(this);
            subscribedApi = currentApi;
        }
    }

    private DiscordSRV readyDiscordSrv() {
        DiscordSRV discordSrv = DiscordSRV.getPlugin();
        if (discordSrv == null || !discordSrv.isEnabled()) {
            throw new IllegalStateException("DiscordSRV is not installed or enabled.");
        }
        if (!DiscordSRV.isReady || discordSrv.getJda() == null
                || discordSrv.getJda().getStatus() != JDA.Status.CONNECTED
                || discordSrv.getAccountLinkManager() == null) {
            throw new IllegalStateException("DiscordSRV's account-linking service is not ready.");
        }
        return discordSrv;
    }

    private static <T> CompletableFuture<T> named(String operation, CompletableFuture<T> action) {
        return action.handle((value, failure) -> {
            if (failure != null) throw new CompletionException(operationFailure(operation, failure));
            return value;
        });
    }

    private static IllegalStateException operationFailure(String operation, Throwable failure) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        Matcher matcher = DISCORD_ERROR.matcher(message);
        String detail = message;
        String errorCode = null;
        if (matcher.find()) {
            errorCode = matcher.group(1);
            detail = matcher.group(2).trim();
        }
        String advice = "";
        if ("50013".equals(errorCode)) {
            advice = " Check that DiscordSRV's bot can manage channels and send messages in the configured category.";
        }
        String description = "Discord operation " + operation + " failed"
                + (errorCode == null ? ": " + detail : " (Discord error " + errorCode + ": " + detail + ")")
                + "." + advice;
        return new IllegalStateException(description, cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record VerificationChannel(TextChannel channel, Member member) {
    }
}