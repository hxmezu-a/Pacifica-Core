package com.districtx.pacificacore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a player's calculated Pacifica level increases. */
public final class PlayerLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int previousLevel;
    private final int newLevel;

    /**
     * Creates a level-up event.
     *
     * @param player player whose level increased
     * @param previousLevel level before the increase
     * @param newLevel calculated level after the increase
     */
    public PlayerLevelUpEvent(Player player, int previousLevel, int newLevel) {
        this.player = player;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    /** @return player whose level increased */
    public Player getPlayer() { return player; }

    /** @return level before the increase */
    public int getPreviousLevel() { return previousLevel; }

    /** @return level after the increase */
    public int getNewLevel() { return newLevel; }

    /** @return Bukkit handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static Bukkit handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}