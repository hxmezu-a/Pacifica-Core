package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.AuthenticationMethod;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class AuthRepository {
    private final DatabaseManager database;

    public AuthRepository(DatabaseManager database) {
        this.database = database;
    }

    public boolean isRegistered(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM auth_accounts WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    public Optional<AuthenticationMethod> getMethod(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT method FROM auth_accounts WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();
                    try {
                        return Optional.of(AuthenticationMethod.valueOf(result.getString("method")));
                    } catch (IllegalArgumentException exception) {
                        throw new SQLException("Stored authentication method is invalid.", exception);
                    }
                }
            }
        });
    }

    public String getPasswordHash(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT password_hash FROM auth_accounts WHERE uuid = ? AND method = 'PASSWORD'")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString("password_hash") : null;
                }
            }
        });
    }

    public RegistrationResult registerPassword(UUID playerId, String encodedHash, String ipHash, int ipLimit,
                                               String discordId, int discordLimit) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM auth_accounts WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return RegistrationResult.ALREADY_REGISTERED;
                }
            }
            if (ipLimit > 0) {
                if (ipHash == null) return RegistrationResult.IP_ADDRESS_UNAVAILABLE;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM auth_ip_accounts WHERE ip_hash = ?")) {
                    statement.setString(1, ipHash);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && result.getInt(1) >= ipLimit) return RegistrationResult.IP_LIMIT_REACHED;
                    }
                }
            }
            if (discordId != null && discordLimit > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM auth_discord_links WHERE discord_user_id = ?")) {
                    statement.setString(1, discordId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && result.getInt(1) >= discordLimit) {
                            return RegistrationResult.DISCORD_LIMIT_REACHED;
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO auth_accounts (uuid, method, password_hash, created_at) "
                            + "VALUES (?, 'PASSWORD', ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, encodedHash);
                statement.setLong(3, System.currentTimeMillis());
                if (statement.executeUpdate() != 1) return RegistrationResult.ALREADY_REGISTERED;
            }
            saveIpHash(connection, playerId, ipHash);
            if (discordId != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO auth_discord_links (uuid, discord_user_id, linked_at) VALUES (?, ?, ?)")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, discordId);
                    statement.setLong(3, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            }
            return RegistrationResult.CREATED;
        });
    }

    public RegistrationResult registerDiscord(UUID playerId, String discordId, String ipHash,
                                              int discordLimit, int ipLimit) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM auth_accounts WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return RegistrationResult.ALREADY_REGISTERED;
                }
            }
            if (ipLimit > 0) {
                if (ipHash == null) return RegistrationResult.IP_ADDRESS_UNAVAILABLE;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM auth_ip_accounts WHERE ip_hash = ?")) {
                    statement.setString(1, ipHash);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && result.getInt(1) >= ipLimit) return RegistrationResult.IP_LIMIT_REACHED;
                    }
                }
            }
            if (discordLimit > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM auth_discord_links WHERE discord_user_id = ?")) {
                    statement.setString(1, discordId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && result.getInt(1) >= discordLimit) {
                            return RegistrationResult.DISCORD_LIMIT_REACHED;
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO auth_accounts (uuid, method, password_hash, created_at) "
                            + "VALUES (?, 'DISCORD', NULL, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, System.currentTimeMillis());
                if (statement.executeUpdate() != 1) return RegistrationResult.ALREADY_REGISTERED;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO auth_discord_links (uuid, discord_user_id, linked_at) VALUES (?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, discordId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            saveIpHash(connection, playerId, ipHash);
            return RegistrationResult.CREATED;
        });
    }

    public String getDiscordLink(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT discord_user_id FROM auth_discord_links WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString("discord_user_id") : null;
                }
            }
        });
    }

    public UUID findMinecraftUuidForDiscord(String discordId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid FROM auth_discord_links WHERE discord_user_id = ? LIMIT 2")) {
                statement.setString(1, discordId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return null;
                    UUID playerId = UUID.fromString(result.getString("uuid"));
                    return result.next() ? null : playerId;
                }
            }
        });
    }

    public DailyClaimReservation reserveDailyClaim(UUID playerId, long now, long cooldownMillis)
            throws SQLException {
        return database.auth(connection -> {
            String status = null;
            long claimedAt = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status, claimed_at FROM auth_discord_daily_claims WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        status = result.getString("status");
                        claimedAt = result.getLong("claimed_at");
                    }
                }
            }
            if (status == null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO auth_discord_daily_claims (uuid, status, reserved_at, claimed_at) "
                                + "VALUES (?, 'PENDING', ?, 0)")) {
                    statement.setString(1, playerId.toString());
                    statement.setLong(2, now);
                    statement.executeUpdate();
                }
                return new DailyClaimReservation(DailyClaimStatus.ACCEPTED, now);
            }
            if ("PENDING".equals(status) || "PROCESSING".equals(status)) {
                return new DailyClaimReservation(DailyClaimStatus.ALREADY_PENDING, 0);
            }
            if ("DONE".equals(status) && claimedAt > 0 && now < claimedAt + cooldownMillis) {
                return new DailyClaimReservation(DailyClaimStatus.COOLDOWN, claimedAt + cooldownMillis);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_daily_claims SET status = 'PENDING', reserved_at = ?, claimed_at = 0 "
                            + "WHERE uuid = ? AND status = 'DONE' AND claimed_at = ?")) {
                statement.setLong(1, now);
                statement.setString(2, playerId.toString());
                statement.setLong(3, claimedAt);
                if (statement.executeUpdate() == 1) {
                    return new DailyClaimReservation(DailyClaimStatus.ACCEPTED, now);
                }
            }
            return new DailyClaimReservation(DailyClaimStatus.ALREADY_PENDING, 0);
        });
    }

    public boolean claimPendingDailyReward(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_daily_claims SET status = 'PROCESSING' "
                            + "WHERE uuid = ? AND status = 'PENDING'")) {
                statement.setString(1, playerId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void completeDailyRewardClaim(UUID playerId, long claimedAt) throws SQLException {
        database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_daily_claims SET status = 'DONE', claimed_at = ? "
                            + "WHERE uuid = ? AND status = 'PROCESSING'")) {
                statement.setLong(1, claimedAt);
                statement.setString(2, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void requeueDailyRewardClaim(UUID playerId) throws SQLException {
        database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_daily_claims SET status = 'PENDING' "
                            + "WHERE uuid = ? AND status = 'PROCESSING'")) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public int countDiscordLinks(String discordId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM auth_discord_links WHERE discord_user_id = ?")) {
                statement.setString(1, discordId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    public boolean unlinkDiscordAccount(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM auth_discord_links WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean unlinkDiscordAccount(UUID playerId, String discordId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM auth_discord_links WHERE uuid = ? AND discord_user_id = ?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, discordId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public LinkResult linkDiscordAccount(UUID playerId, String discordId, int discordLimit) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM auth_accounts WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return LinkResult.ACCOUNT_NOT_REGISTERED;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT discord_user_id FROM auth_discord_links WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return result.getString("discord_user_id").equals(discordId)
                                ? LinkResult.ALREADY_LINKED : LinkResult.DIFFERENT_DISCORD_ACCOUNT;
                    }
                }
            }
            if (discordLimit > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM auth_discord_links WHERE discord_user_id = ?")) {
                    statement.setString(1, discordId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && result.getInt(1) >= discordLimit) return LinkResult.DISCORD_LIMIT_REACHED;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO auth_discord_links (uuid, discord_user_id, linked_at) VALUES (?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, discordId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return LinkResult.LINKED;
        });
    }

    public boolean queueDiscordLinkReward(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO auth_discord_link_rewards (uuid, status, queued_at) "
                            + "VALUES (?, 'PENDING', ?)")) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, System.currentTimeMillis());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean claimPendingDiscordLinkReward(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_link_rewards SET status = 'PROCESSING' "
                            + "WHERE uuid = ? AND status = 'PENDING'")) {
                statement.setString(1, playerId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void completeDiscordLinkReward(UUID playerId) throws SQLException {
        database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_link_rewards SET status = 'DONE' "
                            + "WHERE uuid = ? AND status = 'PROCESSING'")) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public Optional<String> discordLinkRewardStatus(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status FROM auth_discord_link_rewards WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(result.getString("status")) : Optional.empty();
                }
            }
        });
    }

    public void failDiscordLinkReward(UUID playerId) throws SQLException {
        database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_link_rewards SET status = 'FAILED' "
                            + "WHERE uuid = ? AND status = 'PROCESSING'")) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public boolean requeueFailedDiscordLinkReward(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_link_rewards SET status = 'PENDING' "
                            + "WHERE uuid = ? AND status = 'FAILED'")) {
                statement.setString(1, playerId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void requeueDiscordLinkReward(UUID playerId) throws SQLException {
        database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_discord_link_rewards SET status = 'PENDING' "
                            + "WHERE uuid = ? AND status = 'PROCESSING'")) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void resetDiscordLinkReward(UUID playerId) throws SQLException {
        database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM auth_discord_link_rewards WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public int countDiscordLinkConflicts(int limit) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM (SELECT discord_user_id FROM auth_discord_links "
                            + "GROUP BY discord_user_id HAVING COUNT(*) > ?)")) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    public boolean updatePassword(UUID playerId, String encodedHash) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auth_accounts SET password_hash = ? WHERE uuid = ? AND method = 'PASSWORD'")) {
                statement.setString(1, encodedHash);
                statement.setString(2, playerId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean deleteAccount(UUID playerId) throws SQLException {
        return database.auth(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM auth_accounts WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    private void saveIpHash(java.sql.Connection connection, UUID playerId, String ipHash) throws SQLException {
        if (ipHash == null) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO auth_ip_accounts (uuid, ip_hash, updated_at) VALUES (?, ?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, ipHash);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public enum RegistrationResult {
        CREATED,
        ALREADY_REGISTERED,
        IP_LIMIT_REACHED,
        DISCORD_LIMIT_REACHED,
        IP_ADDRESS_UNAVAILABLE
    }

    public enum LinkResult {
        LINKED,
        ALREADY_LINKED,
        ACCOUNT_NOT_REGISTERED,
        DIFFERENT_DISCORD_ACCOUNT,
        DISCORD_LIMIT_REACHED
    }

    public enum DailyClaimStatus {
        ACCEPTED,
        ALREADY_PENDING,
        COOLDOWN
    }

    public record DailyClaimReservation(DailyClaimStatus status, long nextClaimAt) {
    }
}