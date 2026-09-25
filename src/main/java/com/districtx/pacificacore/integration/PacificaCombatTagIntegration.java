package com.districtx.pacificacore.integration;

import com.districtx.pacificacore.PacificaCore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Optional reflective bridge to Pacifica-II's public CombatTag service. */
public final class PacificaCombatTagIntegration {
    private static final String API_CLASS = "com.districtx.pacifica.api.combat.CombatTagAPI";
    private final PacificaCore plugin;

    /**
     * Creates the optional CombatTag bridge.
     *
     * @param plugin owning Pacifica-Core plugin
     */
    public PacificaCombatTagIntegration(PacificaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Queries Pacifica-II's registered CombatTag API without depending on its classes at startup.
     *
     * @param player player to check
     * @return true only when Pacifica-II reports an active combat tag
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean isInCombat(Player player) {
        if (player == null) return false;
        Plugin pacificaTwo = plugin.getServer().getPluginManager().getPlugin("Pacifica-II");
        if (pacificaTwo == null || !pacificaTwo.isEnabled()) return false;
        try {
            Class<?> apiClass = pacificaTwo.getClass().getClassLoader().loadClass(API_CLASS);
            Object registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) apiClass);
            if (registration == null) return false;
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            Object result = apiClass.getMethod("isInCombat", Player.class).invoke(provider, player);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }
}