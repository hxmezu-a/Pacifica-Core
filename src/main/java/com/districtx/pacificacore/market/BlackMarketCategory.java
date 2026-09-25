package com.districtx.pacificacore.market;

public enum BlackMarketCategory {
    WEAPONS("Weapons", "DIAMOND_SWORD"),
    ARMOR("Armor", "DIAMOND_HELMET"),
    PLAYER_HEADS("Players Heads", "PLAYER_HEAD"),
    RARES("Rares", "EGG"),
    CHEAT_CODES("Cheat Codes", "BEACON"),
    COSMETICS("Cosmetics", "BUCKET"),
    MISC("Mics / Others", "CLOCK");

    private final String displayName;
    private final String icon;

    BlackMarketCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
}