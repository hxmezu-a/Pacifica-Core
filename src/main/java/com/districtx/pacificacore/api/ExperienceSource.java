package com.districtx.pacificacore.api;

/** Identifies why permanent Pacifica experience was granted. */
public enum ExperienceSource {
    /** Experience granted for killing another player. */
    PLAYER_KILL,
    /** Experience granted to a player who dies while Pacifica-II reports them in combat. */
    PVP_DEATH,
    /** Experience granted from Pacifica-LootSystem. */
    LOOT,
    /** Experience purchased through the Daily XP feature. */
    DAILY_XP,
    /** Experience granted by an administrator. */
    ADMIN,
    /** Experience granted by another plugin using the public API. */
    API,
    /** Experience granted for another or unspecified reason. */
    OTHER
}