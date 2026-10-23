package com.districtx.pacificacore.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuthSystemService {
    /** Returns true only for the current connected session after successful authentication. */
    boolean isAuthenticated(UUID playerId);

    /** Checks persistent registration state without exposing account credentials. */
    CompletableFuture<Boolean> isRegistered(UUID playerId);

    /** Retrieves the saved method without exposing its stored credential data. */
    CompletableFuture<Optional<AuthenticationMethod>> getAuthenticationMethod(UUID playerId);

    /** Revokes the current session and requires an online player to authenticate again. */
    void invalidateSession(UUID playerId);
}