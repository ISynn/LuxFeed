package io.github.isynn.luxfeed.combat;

import org.bukkit.entity.Player;

/**
 * Placeholder used while no combat system is connected.
 *
 * <p>It performs no detection whatsoever and always reports that the player is <strong>not</strong>
 * in combat, so {@code /feed} is never refused for combat reasons. Replace it with an implementation
 * backed by a real combat system to enable that restriction.
 */
public final class StubCombatCheck implements CombatCheck {

    @Override
    public boolean isInCombat(Player player) {
        return false;
    }
}
