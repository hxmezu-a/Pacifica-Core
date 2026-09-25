package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.util.OptionalInt;

/** Public controller API for Pacifica Level and Prestige menus. */
public interface LevelMenuService {
    /** Opens the main six-row Level menu. */
    void openMainMenu(Player player);
    /** Opens one of the five permanent 1-100 progression pages. */
    void openProgressionMenu(Player player, int page);
    /** Opens the separate Prestige menu when unlocked. */
    void openPrestigeMenu(Player player);
    /** Refreshes an open Level menu using the player's current progression state. */
    void refresh(Player player);
    /** Maps an official progression slot to its level, or returns empty for other slots. */
    OptionalInt getLevelForSlot(int page, int slot);
}