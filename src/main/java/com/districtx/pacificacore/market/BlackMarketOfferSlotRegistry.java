package com.districtx.pacificacore.market;

import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public final class BlackMarketOfferSlotRegistry {
    private static final Map<Integer, Integer> PHYSICAL_TO_LOGICAL = Map.ofEntries(
            Map.entry(10, 1),
            Map.entry(11, 2),
            Map.entry(12, 3),
            Map.entry(13, 4),
            Map.entry(14, 5),
            Map.entry(15, 6),
            Map.entry(16, 7),
            Map.entry(19, 8),
            Map.entry(20, 9),
            Map.entry(21, 10),
            Map.entry(22, 11),
            Map.entry(23, 12),
            Map.entry(24, 13),
            Map.entry(25, 14)
    );

    private static final Map<Integer, Integer> LOGICAL_TO_PHYSICAL = PHYSICAL_TO_LOGICAL.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    public OptionalInt getLogicalSlot(int physicalSlot) {
        Integer logicalSlot = PHYSICAL_TO_LOGICAL.get(physicalSlot);
        return logicalSlot == null ? OptionalInt.empty() : OptionalInt.of(logicalSlot);
    }

    public OptionalInt getLogicalOfferSlotNumber(int physicalSlot) {
        return getLogicalSlot(physicalSlot);
    }

    public OptionalInt getPhysicalSlot(int logicalSlot) {
        Integer physicalSlot = LOGICAL_TO_PHYSICAL.get(logicalSlot);
        return physicalSlot == null ? OptionalInt.empty() : OptionalInt.of(physicalSlot);
    }

    public boolean isOfferSlot(int physicalSlot) {
        return PHYSICAL_TO_LOGICAL.containsKey(physicalSlot);
    }

    public int getMaximumLogicalSlot() {
        return PHYSICAL_TO_LOGICAL.size();
    }
}