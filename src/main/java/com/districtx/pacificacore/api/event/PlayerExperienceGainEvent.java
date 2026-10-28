package com.districtx.pacificacore.api.event;

import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.api.LevelProgression;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before permanent Pacifica experience is granted to an online player. */
public final class PlayerExperienceGainEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ExperienceSource source;
    private final double previousExperience;
    private final int previousLevel;
    private final LevelProgression progression;
    private double amount;
    private boolean cancelled;

    /**
     * Creates the pre-grant event.
     *
     * @param player rewarded player
     * @param amount proposed non-negative gain
     * @param source reason for the gain
     * @param previousExperience experience before the proposed gain
     * @param previousLevel level before the proposed gain
     * @param progression progression calculator used by the service
     */
    public PlayerExperienceGainEvent(Player player, double amount, ExperienceSource source,
                                     double previousExperience, int previousLevel,
                                     LevelProgression progression) {
        this.player = player;
        this.amount = amount;
        this.source = source;
        this.previousExperience = previousExperience;
        this.previousLevel = previousLevel;
        this.progression = progression;
    }

    /** @return player receiving the proposed gain */
    public Player getPlayer() { return player; }

    /** @return mutable proposed gain */
    public double getAmount() { return amount; }

    /**
     * Changes the proposed gain. Negative and non-finite values are ignored.
     *
     * @param amount new non-negative gain
     */
    public void setAmount(double amount) {
        if (Double.isFinite(amount) && amount >= 0.0) this.amount = amount;
    }

    /** @return source of the proposed gain */
    public ExperienceSource getSource() { return source; }

    /** @return experience before the proposed gain */
    public double getPreviousExperience() { return previousExperience; }

    /** @return projected experience after the current proposed gain */
    public double getExperienceAfterGain() { return previousExperience + amount; }

    /** @return level before the proposed gain */
    public int getPreviousLevel() { return previousLevel; }

    /** @return projected level after the current proposed gain */
    public int getLevelAfterGain() { return progression.getLevel(getExperienceAfterGain()); }

    /** @return whether the proposed gain is cancelled */
    @Override public boolean isCancelled() { return cancelled; }

    /** @param cancelled whether the proposed gain should be cancelled */
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** @return Bukkit handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static Bukkit handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}