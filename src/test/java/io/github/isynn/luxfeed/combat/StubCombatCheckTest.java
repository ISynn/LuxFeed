package io.github.isynn.luxfeed.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StubCombatCheckTest {

    @Test
    void neverReportsCombatAndDoesNotInspectThePlayer() {
        Player player = mock(Player.class);

        assertFalse(new StubCombatCheck().isInCombat(player));
        verifyNoInteractions(player);
    }
}
