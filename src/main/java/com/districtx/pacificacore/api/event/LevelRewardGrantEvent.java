package com.districtx.pacificacore.api.event;

import com.districtx.pacificacore.api.LevelReward;
import com.districtx.pacificacore.api.LevelRewardContext;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a level reward is claimed and executed. */
public final class LevelRewardGrantEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final LevelReward reward;
    private final LevelRewardContext context;
    private boolean cancelled;

    public LevelRewardGrantEvent(Player player, LevelReward reward, LevelRewardContext context) {
        this.player = player;
        this.reward = reward;
        this.context = context;
    }

    public Player getPlayer() { return player; }
    public LevelReward getReward() { return reward; }
    public LevelRewardContext getContext() { return context; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}