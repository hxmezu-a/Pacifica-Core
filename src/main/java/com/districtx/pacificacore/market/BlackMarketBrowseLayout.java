package com.districtx.pacificacore.market;

import java.util.List;
import java.util.Set;

public final class BlackMarketBrowseLayout {
    public static final Set<Integer> BROWSE_GLASS_SLOTS = Set.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9,
            17, 18, 26, 27, 35, 36, 37,
            38, 39, 40, 41, 42, 43, 44,
            45, 46, 48, 50, 52, 53
    );

    public static final Set<Integer> CONTROL_SLOTS = Set.of(0, 47, 49, 51);

    public static final List<Integer> LISTING_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    public static final Set<Integer> MY_OFFERS_GLASS_SLOTS = Set.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35
    );

    private BlackMarketBrowseLayout() {
    }

    public static boolean isGlassSlot(int slot) {
        return BROWSE_GLASS_SLOTS.contains(slot);
    }
}