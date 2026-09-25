package com.districtx.pacificacore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a player crosses the configured Prestige unlock level. */
public final class PrestigeUnlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int unlockLevel;

    public PrestigeUnlockEvent(Player player, int unlockLevel) {
        this.player = player;
        this.unlockLevel = unlockLevel;
    }

    public Player getPlayer() { return player; }
    public int getUnlockLevel() { return unlockLevel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}