package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.market.BlackMarketCategory;
import com.districtx.pacificacore.market.BlackMarketBid;
import com.districtx.pacificacore.market.BlackMarketEscrow;
import com.districtx.pacificacore.market.BlackMarketFilter;
import com.districtx.pacificacore.market.BlackMarketItemSerializer;
import com.districtx.pacificacore.market.BlackMarketOffer;
import com.districtx.pacificacore.market.BlackMarketOfferType;
import com.districtx.pacificacore.market.BlackMarketSort;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BlackMarketRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public BlackMarketRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean create(BlackMarketOffer offer) {
        try {
            String sql = "INSERT INTO black_market_offers "
                    + "(offer_id, seller_uuid, category, offer_type, item_data, original_quantity, "
                        + "remaining_quantity, price, created_at, expires_at, status, fulfilled_quantity, reserved_funds, minimum_bid, logical_offer_slot) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)";
            String data = BlackMarketItemSerializer.serialize(offer.getItem());
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, offer.getId().toString());
                    statement.setString(2, offer.getSeller().toString());
                    statement.setString(3, offer.getCategory().name());
                    statement.setString(4, offer.getType().name());
                    statement.setString(5, data);
                    statement.setInt(6, offer.getOriginalQuantity());
                    statement.setInt(7, offer.getRemainingQuantity());
                    statement.setString(8, offer.getPrice().toPlainString());
                    statement.setLong(9, offer.getCreatedAt());
                    statement.setLong(10, offer.getExpiresAt());
                    statement.setInt(11, offer.getFulfilledQuantity());
                    statement.setString(12, offer.getReservedFunds().toPlainString());
                    statement.setString(13, offer.getMinimumBid().toPlainString());
                    if (offer.getLogicalOfferSlot() == null) statement.setNull(14, java.sql.Types.INTEGER);
                    else statement.setInt(14, offer.getLogicalOfferSlot());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | IOException exception) {
            plugin.getLogger().warning("Could not create Black Market offer: " + exception.getMessage());
            return false;
        }
    }

    public BlackMarketOffer find(UUID id) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM black_market_offers WHERE offer_id = ? AND status = 'ACTIVE'")) {
                    statement.setString(1, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? readSafely(result) : null;
                    }
                }
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not load Black Market offer: " + exception.getMessage());
            return null;
        }
    }

    public BlackMarketOffer findAny(UUID id) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM black_market_offers WHERE offer_id = ?")) {
                    statement.setString(1, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? readSafely(result) : null;
                    }
                }
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not load Black Market offer: " + exception.getMessage());
            return null;
        }
    }

    public List<BlackMarketOffer> findActive(BlackMarketCategory category, BlackMarketFilter filter,
                                              BlackMarketSort sort, long now) {
        List<BlackMarketOffer> offers = new ArrayList<>();
        try {
            database.itemPrices(connection -> {
                StringBuilder sql = new StringBuilder("SELECT * FROM black_market_offers "
                        + "WHERE status = 'ACTIVE' AND category = ? AND expires_at > ?");
                if (filter != BlackMarketFilter.ALL) sql.append(" AND offer_type = ?");
                sql.append(" ORDER BY ");
                if (sort == BlackMarketSort.HIGHEST_PRICE) sql.append("CAST(price AS DECIMAL) DESC");
                else if (sort == BlackMarketSort.MOST_TIME) sql.append("expires_at DESC");
                else if (sort == BlackMarketSort.LEAST_TIME) sql.append("expires_at ASC");
                else sql.append("CAST(price AS DECIMAL) ASC");
                try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                    int index = 1;
                    statement.setString(index++, category.name());
                    statement.setLong(index++, now);
                    if (filter != BlackMarketFilter.ALL) statement.setString(index, filter.name());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) offers.add(readSafely(result));
                    }
                }
                return null;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not load Black Market offers: " + exception.getMessage());
        }
        return offers;
    }

    public boolean changeRemaining(UUID id, int expected, int updated) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_offers SET remaining_quantity = ?, fulfilled_quantity = original_quantity - ?, status = ? "
                                + "WHERE offer_id = ? AND status = 'ACTIVE' AND remaining_quantity = ?")) {
                    statement.setInt(1, updated);
                    statement.setInt(2, updated);
                    statement.setString(3, updated == 0 ? "SOLD" : "ACTIVE");
                    statement.setString(4, id.toString());
                    statement.setInt(5, expected);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not update Black Market offer: " + exception.getMessage());
            return false;
        }
    }

    public boolean changeBuyState(UUID id, int expectedRemaining, int updatedRemaining,
                                  BigDecimal expectedFunds, BigDecimal updatedFunds) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_offers SET remaining_quantity = ?, fulfilled_quantity = original_quantity - ?, "
                                + "reserved_funds = ?, status = ? WHERE offer_id = ? AND status = 'ACTIVE' "
                                + "AND remaining_quantity = ? AND reserved_funds = ?")) {
                    statement.setInt(1, updatedRemaining);
                    statement.setInt(2, updatedRemaining);
                    statement.setString(3, updatedFunds.toPlainString());
                    statement.setString(4, updatedRemaining == 0 ? "CLAIMABLE" : "ACTIVE");
                    statement.setString(5, id.toString());
                    statement.setInt(6, expectedRemaining);
                    statement.setString(7, expectedFunds.toPlainString());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not update Black Market buy offer: " + exception.getMessage());
            return false;
        }
    }

    public boolean addBuyEscrow(UUID offerId, org.bukkit.inventory.ItemStack item, int quantity) {
        try {
            String data = BlackMarketItemSerializer.serialize(item);
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO black_market_buy_escrow (offer_id, item_data, quantity) VALUES (?, ?, ?) "
                                + "ON CONFLICT(offer_id) DO UPDATE SET item_data = excluded.item_data, quantity = quantity + excluded.quantity")) {
                    statement.setString(1, offerId.toString());
                    statement.setString(2, data);
                    statement.setInt(3, quantity);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | IOException exception) {
            plugin.getLogger().warning("Could not store Black Market buy escrow: " + exception.getMessage());
            return false;
        }
    }

    public BlackMarketEscrow findBuyEscrow(UUID offerId) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_data, quantity FROM black_market_buy_escrow WHERE offer_id = ?")) {
                    statement.setString(1, offerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return null;
                        try {
                            return new BlackMarketEscrow(BlackMarketItemSerializer.deserialize(result.getString("item_data")),
                                    result.getInt("quantity"));
                        } catch (IOException | ClassNotFoundException exception) {
                            throw new SQLException("Could not deserialize Black Market buy escrow", exception);
                        }
                    }
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load Black Market buy escrow: " + exception.getMessage());
            return null;
        }
    }

    public boolean clearBuyEscrow(UUID offerId) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM black_market_buy_escrow WHERE offer_id = ?")) {
                    statement.setString(1, offerId.toString());
                    statement.executeUpdate();
                    return true;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not clear Black Market buy escrow: " + exception.getMessage());
            return false;
        }
    }

    public boolean placeBid(UUID id, int expectedRemaining, BigDecimal expectedFunds,
                            UUID expectedBidder, UUID bidder, BigDecimal amount) {
        try {
            return database.itemPrices(connection -> {
                String condition = expectedBidder == null ? " AND highest_bidder_uuid IS NULL" : " AND highest_bidder_uuid = ?";
                String sql = "UPDATE black_market_offers SET reserved_funds = ?, highest_bidder_uuid = ? "
                        + "WHERE offer_id = ? AND offer_type = 'AUCTION' AND status = 'ACTIVE' "
                        + "AND expires_at > ? AND remaining_quantity = ? AND reserved_funds = ?" + condition;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    int index = 1;
                    statement.setString(index++, amount.toPlainString());
                    statement.setString(index++, bidder.toString());
                    statement.setString(index++, id.toString());
                    statement.setLong(index++, System.currentTimeMillis());
                    statement.setInt(index++, expectedRemaining);
                    statement.setString(index++, expectedFunds.toPlainString());
                    if (expectedBidder != null) statement.setString(index, expectedBidder.toString());
                    if (statement.executeUpdate() != 1) return false;
                }
                if (expectedBidder != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE black_market_bids SET refunded = 1 WHERE offer_id = ? AND bidder_uuid = ? AND refunded = 0")) {
                        statement.setString(1, id.toString());
                        statement.setString(2, expectedBidder.toString());
                        statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO black_market_bids (bid_id, offer_id, bidder_uuid, amount, created_at, refunded) VALUES (?, ?, ?, ?, ?, 0)")) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, id.toString());
                    statement.setString(3, bidder.toString());
                    statement.setString(4, amount.toPlainString());
                    statement.setLong(5, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                return true;
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not place Black Market bid: " + exception.getMessage());
            return false;
        }
    }

    public List<BlackMarketBid> findBids(UUID offerId) {
        List<BlackMarketBid> bids = new ArrayList<>();
        try {
            database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM black_market_bids WHERE offer_id = ? ORDER BY created_at ASC")) {
                    statement.setString(1, offerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) bids.add(new BlackMarketBid(
                                UUID.fromString(result.getString("bid_id")), offerId,
                                UUID.fromString(result.getString("bidder_uuid")),
                                new BigDecimal(result.getString("amount")), result.getLong("created_at"),
                                result.getInt("refunded") != 0));
                    }
                }
                return null;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not load Black Market bids: " + exception.getMessage());
        }
        return bids;
    }

    public boolean markBidRefunded(UUID bidId) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_bids SET refunded = 1 WHERE bid_id = ? AND refunded = 0")) {
                    statement.setString(1, bidId.toString());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not mark Black Market bid refunded: " + exception.getMessage());
            return false;
        }
    }

    public boolean markBidsRefunded(UUID offerId, UUID bidder) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_bids SET refunded = 1 WHERE offer_id = ? AND bidder_uuid = ? AND refunded = 0")) {
                    statement.setString(1, offerId.toString());
                    statement.setString(2, bidder.toString());
                    statement.executeUpdate();
                    return true;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not mark outbid Black Market bids: " + exception.getMessage());
            return false;
        }
    }

    public boolean claim(UUID id, int expectedQuantity, int claimedQuantity) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_offers SET claimed_quantity = ?, status = 'CLAIMED' "
                                + "WHERE offer_id = ? AND status IN ('EXPIRED', 'CLAIMABLE', 'CANCELLED') AND claimed_quantity = ?")) {
                    statement.setInt(1, claimedQuantity);
                    statement.setString(2, id.toString());
                    statement.setInt(3, expectedQuantity);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not claim Black Market offer: " + exception.getMessage());
            return false;
        }
    }

    public boolean setStatus(UUID id, UUID seller, String status) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_offers SET status = ? WHERE offer_id = ? AND seller_uuid = ? AND status = 'ACTIVE'")) {
                    statement.setString(1, status);
                    statement.setString(2, id.toString());
                    statement.setString(3, seller.toString());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not change Black Market offer status: " + exception.getMessage());
            return false;
        }
    }

    public int expire(long now) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE black_market_offers SET status = 'EXPIRED' "
                                + "WHERE status = 'ACTIVE' AND expires_at <= ?")) {
                    statement.setLong(1, now);
                    return statement.executeUpdate();
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not expire Black Market offers: " + exception.getMessage());
            return 0;
        }
    }

    public List<BlackMarketOffer> expireBuyOffers(long now) {
        List<BlackMarketOffer> expired = new ArrayList<>();
        try {
            database.itemPrices(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT * FROM black_market_offers WHERE status = 'ACTIVE' AND offer_type = 'BUY' AND expires_at <= ?")) {
                    select.setLong(1, now);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) expired.add(readSafely(result));
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE black_market_offers SET status = 'EXPIRED' WHERE status = 'ACTIVE' "
                                + "AND offer_type = 'BUY' AND expires_at <= ?")) {
                    update.setLong(1, now);
                    update.executeUpdate();
                }
                return null;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not expire Black Market buy offers: " + exception.getMessage());
        }
        return expired;
    }

    public List<BlackMarketOffer> expireAuctions(long now) {
        List<BlackMarketOffer> expired = new ArrayList<>();
        try {
            database.itemPrices(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT * FROM black_market_offers WHERE status = 'ACTIVE' AND offer_type = 'AUCTION' AND expires_at <= ?")) {
                    select.setLong(1, now);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) expired.add(readSafely(result));
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE black_market_offers SET status = 'EXPIRED' WHERE status = 'ACTIVE' "
                                + "AND offer_type = 'AUCTION' AND expires_at <= ?")) {
                    update.setLong(1, now);
                    update.executeUpdate();
                }
                return null;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not expire Black Market auctions: " + exception.getMessage());
        }
        return expired;
    }

    public int countActive(long now) {
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM black_market_offers WHERE status = 'ACTIVE' AND expires_at > ?")) {
                    statement.setLong(1, now);
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? result.getInt(1) : 0;
                    }
                }
            });
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not count Black Market offers: " + exception.getMessage());
            return 0;
        }
    }

    public List<BlackMarketOffer> findSeller(UUID seller) {
        List<BlackMarketOffer> offers = new ArrayList<>();
        try {
            database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM black_market_offers WHERE status NOT IN ('CANCELLED', 'CLAIMED') "
                                + "AND (((seller_uuid = ? AND NOT (offer_type = 'AUCTION' AND highest_bidder_uuid IS NOT NULL)) "
                                + "AND NOT (status = 'CLAIMABLE' AND ((offer_type = 'SELL' AND remaining_quantity = 0) "
                                + "OR (offer_type = 'BUY' AND fulfilled_quantity = 0)))) "
                                + "OR (highest_bidder_uuid = ? AND offer_type = 'AUCTION' AND status IN ('EXPIRED', 'CLAIMABLE'))) "
                                + "ORDER BY created_at DESC")) {
                    statement.setString(1, seller.toString());
                    statement.setString(2, seller.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) offers.add(readSafely(result));
                    }
                }
                return null;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not load player Black Market offers: " + exception.getMessage());
        }
        return offers;
    }

    private BlackMarketOffer read(ResultSet result) throws SQLException, IOException, ClassNotFoundException {
        int logicalOfferSlotValue = result.getInt("logical_offer_slot");
        Integer logicalOfferSlot = result.wasNull() ? null : logicalOfferSlotValue;
        return new BlackMarketOffer(
                UUID.fromString(result.getString("offer_id")),
                UUID.fromString(result.getString("seller_uuid")),
                BlackMarketCategory.valueOf(result.getString("category")),
                BlackMarketOfferType.valueOf(result.getString("offer_type")),
                BlackMarketItemSerializer.deserialize(result.getString("item_data")),
                result.getInt("original_quantity"), result.getInt("remaining_quantity"),
                new BigDecimal(result.getString("price")), result.getLong("created_at"),
                result.getLong("expires_at"), result.getInt("fulfilled_quantity"),
                new BigDecimal(result.getString("reserved_funds")), new BigDecimal(result.getString("minimum_bid")),
                result.getString("highest_bidder_uuid") == null ? null : UUID.fromString(result.getString("highest_bidder_uuid")),
                result.getString("status"), result.getInt("claimed_quantity"), logicalOfferSlot);
    }

    private BlackMarketOffer readSafely(ResultSet result) throws SQLException {
        try {
            return read(result);
        } catch (IOException | ClassNotFoundException exception) {
            throw new SQLException("Could not deserialize Black Market item", exception);
        }
    }
}