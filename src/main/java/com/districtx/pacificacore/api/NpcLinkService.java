package com.districtx.pacificacore.api;

import java.util.Optional;

public interface NpcLinkService {
    void link(String featureId, String npcId);
    void unlink(String featureId);
    Optional<String> getLinkedNpcId(String featureId);
    boolean isLinked(String featureId);
    boolean isLinkedTo(String featureId, String npcId);
}