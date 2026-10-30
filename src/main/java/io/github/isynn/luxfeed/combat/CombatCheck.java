package io.github.isynn.luxfeed.combat;

import org.bukkit.entity.Player;

/**
 * Tells whether a player is currently in combat. {@code /feed} is refused while this returns {@code true}.
 *
 * <p>LuxFeed does not track combat itself: an implementation is expected to delegate to an external
 * combat system. Implementations are called on the server main thread.
 */
@FunctionalInterface
public interface CombatCheck {

    boolean isInCombat(Player player);
}
