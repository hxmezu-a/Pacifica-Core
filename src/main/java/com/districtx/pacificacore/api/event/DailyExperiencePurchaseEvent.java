package com.districtx.pacificacore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired immediately before the economy withdrawal for a Daily XP purchase. */
public final class DailyExperiencePurchaseEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final double experience;
    private final double price;
    private boolean cancelled;

    public DailyExperiencePurchaseEvent(Player player, double experience, double price) {
        this.player = player;
        this.experience = experience;
        this.price = price;
    }

    public Player getPlayer() { return player; }
    public double getExperience() { return experience; }
    public double getPrice() { return price; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}