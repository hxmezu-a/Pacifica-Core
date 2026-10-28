package com.districtx.pacificacore.api;

import java.util.List;
import java.util.OptionalInt;

public interface SpawnShopSlotService {
    List<Integer> getAvailableItemSlots(String shopId);
    OptionalInt getFirstAvailableItemSlot(String shopId);
    boolean isReservedSlot(int slot);
    boolean isShopItemSlot(int slot);
}