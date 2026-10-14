# Pacifica-Core Player Level API

Other plugins can obtain the progression service through Bukkit's services manager or through
`PacificaCore.getAPI().getPlayerLevelService()`. Keep XP awards on this service: it applies the
configured rank bonus, runs the gain event, persists the result, fires progression events, grants
rewards, and refreshes online displays through the same path as internal awards.

```java
import com.districtx.pacificacore.api.ExperienceGainResult;
import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.PlayerLevelSnapshot;
import java.math.BigDecimal;
import java.util.UUID;

PlayerLevelService levels = getServer().getServicesManager().load(PlayerLevelService.class);
if (levels == null) return;

UUID playerId = player.getUniqueId();
PlayerLevelSnapshot snapshot = levels.getProgressionSnapshot(playerId);
BigDecimal totalXp = snapshot.totalExperience();
BigDecimal currentLevelXp = snapshot.experienceIntoCurrentLevel();
BigDecimal nextLevelCost = snapshot.experienceRequiredForNextLevel();
BigDecimal xpStillNeeded = snapshot.experienceToNextLevel();

ExperienceGainResult result = levels.addExperience(playerId, 25.5, ExperienceSource.API);
if (result.isLevelUp()) {
    player.sendMessage("Pacifica level: " + result.getNewLevel());
}
```

`PlayerLevelSnapshot` is an immutable record. Its XP totals and requirements use `BigDecimal`,
matching persisted precision; the progress fraction is normalized from 0.0 to 1.0. Existing
`double` methods and award overloads remain available for source compatibility. The service also
exposes `getLevel`, `getTotalExperience`, `getCurrentLevelExperience`,
`getExperienceRequiredForNextLevel(UUID)`, `getExperienceToNextLevel`, and
`canAdvanceToNextLevel` for individual lookups. The latter reports whether the integer level value
has a representable next level; levels themselves are derived immediately from total XP.

Progression events remain Bukkit events and can be handled with ordinary listeners, including
`PlayerExperienceGainEvent`, `PlayerExperienceGrantedEvent`, `PlayerLevelChangeEvent`, and
`PlayerLevelUpEvent`.

The progression GUI keeps its configured minimum page count and expands navigation through the
page containing the player's current level, so levels above 100 can be viewed and refreshed.

## High-level progression behavior

The shipped curve uses base XP 100 and growth rate 1.25. With this exponential curve, 70,000 total
XP is approximately level 24, while level 100 requires roughly 1.5 trillion total XP. The level
calculator and text-backed database do not impose a 70,000 XP or level-100 cap. The steep configured
growth rate explains the perceived slowdown. Changing that curve would recalculate levels from
existing XP totals, so server owners should treat curve changes as a progression rebalance rather
than a data-preserving bug fix.

Regression scenarios to verify are exact thresholds at levels 100 and 101, continued progression
beyond 70,000 XP, carrying XP across multiple levels, persistence after relogging, and normalized
high-level XP-bar progress.