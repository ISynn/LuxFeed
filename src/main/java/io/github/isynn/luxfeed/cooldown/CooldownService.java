package io.github.isynn.luxfeed.cooldown;

import io.github.isynn.luxfeed.database.SQLiteDatabase;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Checks and records {@code /feed} cooldowns.
 *
 * <p>Database work runs on {@code databaseExecutor}, which must run one task at a time: checking the
 * last use and recording the new one then form a single step, so two {@code /feed} sent at the same
 * time cannot both pass the cooldown.
 */
public final class CooldownService {

    private final CooldownSettings settings;
    private final SQLiteDatabase database;
    private final Executor databaseExecutor;
    private final Clock clock;

    public CooldownService(CooldownSettings settings, SQLiteDatabase database, Executor databaseExecutor,
                           Clock clock) {
        this.settings = settings;
        this.database = database;
        this.databaseExecutor = databaseExecutor;
        this.clock = clock;
    }

    public Duration cooldownFor(Predicate<String> hasPermission) {
        return settings.cooldownFor(hasPermission);
    }

    /**
     * Records a use now if the player's cooldown is over.
     *
     * <p>The returned future fails with the underlying {@link SQLException} if the database cannot be
     * read or written; in that case nothing is recorded.
     */
    public CompletableFuture<UseAttempt> tryUse(UUID playerId, Duration cooldown) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return attemptUse(playerId, cooldown);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    /** Undoes a granted use that could not be completed, restoring the previous one if any. */
    public CompletableFuture<Void> cancel(UseAttempt.Granted granted) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (granted.previousUse().isPresent()) {
                    database.saveLastUse(granted.playerId(), granted.previousUse().get());
                } else {
                    database.deleteLastUse(granted.playerId());
                }
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    /**
     * Time left before the next use. Never longer than {@code cooldown}, even if the stored time is in
     * the future because the system clock was moved back.
     */
    static Duration remaining(Instant lastUse, Duration cooldown, Instant now) {
        Duration remaining = Duration.between(now, lastUse.plus(cooldown));
        if (remaining.isNegative()) {
            return Duration.ZERO;
        }
        return remaining.compareTo(cooldown) > 0 ? cooldown : remaining;
    }

    private UseAttempt attemptUse(UUID playerId, Duration cooldown) throws SQLException {
        Instant now = clock.instant();
        Optional<Instant> lastUse = database.findLastUse(playerId);
        if (lastUse.isPresent()) {
            Duration remaining = remaining(lastUse.get(), cooldown, now);
            if (remaining.isPositive()) {
                return new UseAttempt.OnCooldown(remaining);
            }
        }
        database.saveLastUse(playerId, now);
        return new UseAttempt.Granted(playerId, now, lastUse);
    }
}
