package com.districtx.pacificacore.api.event;

import com.districtx.pacificacore.api.ExperienceGainResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired once after an experience gain has been successfully persisted. */
public final class PlayerExperienceGrantedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ExperienceGainResult result;

    public PlayerExperienceGrantedEvent(ExperienceGainResult result) {
        this.result = result;
    }

    /** @return authoritative result of the persisted experience transaction */
    public ExperienceGainResult getResult() { return result; }

    @Override public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}