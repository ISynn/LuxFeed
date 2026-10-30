package io.github.isynn.luxfeed.cooldown;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Outcome of {@link CooldownService#tryUse}. */
public sealed interface UseAttempt {

    /**
     * The cooldown was over and the use has been recorded.
     *
     * @param previousUse use recorded before this one, restored if the use is cancelled
     */
    record Granted(UUID playerId, Instant usedAt, Optional<Instant> previousUse) implements UseAttempt {
    }

    /** The cooldown is still running; nothing was recorded. */
    record OnCooldown(Duration remaining) implements UseAttempt {
    }
}
