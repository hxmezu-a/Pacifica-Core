package com.districtx.pacificacore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Cancellable notification for a future Prestige operation. */
public final class PrestigeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int previousPrestige;
    private final int newPrestige;
    private boolean cancelled;

    public PrestigeEvent(Player player, int previousPrestige, int newPrestige) {
        this.player = player;
        this.previousPrestige = previousPrestige;
        this.newPrestige = newPrestige;
    }

    public Player getPlayer() { return player; }
    public int getPreviousPrestige() { return previousPrestige; }
    public int getNewPrestige() { return newPrestige; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}