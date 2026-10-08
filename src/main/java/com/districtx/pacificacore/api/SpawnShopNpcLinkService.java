package com.districtx.pacificacore.api;

import java.util.Collection;
import java.util.Optional;

public interface SpawnShopNpcLinkService {
    boolean linkNpcToShop(String npcId, String shopId);
    boolean unlinkNpc(String npcId);
    Optional<String> getLinkedShop(String npcId);
    boolean isLinked(String npcId);
    Collection<String> getNpcsLinkedToShop(String shopId);
}