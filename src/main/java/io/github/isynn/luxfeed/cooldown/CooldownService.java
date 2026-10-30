package io.github.isynn.luxfeed.cooldown;

import io.github.isynn.luxfeed.database.SQLiteDatabase;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads the cooldown tiers from config.yml, then checks and records {@code /feed} uses.
 *
 * <p>Database work runs on {@code databaseExecutor}, which must run one task at a time: reading the
 * last use and recording the new one then form a single step, so two {@code /feed} sent at the same
 * time cannot both pass the cooldown.
 */
public final class CooldownService {

    public static final Duration FALLBACK_DURATION = Duration.ofMinutes(60);
    private static final String DEFAULT_TIER = "default";

    public record Tier(String permission, Duration duration) {
    }

    public sealed interface Result {

        /** The cooldown was over and the use has been recorded. */
        record Granted(UUID playerId, Instant usedAt, Optional<Instant> previousUse) implements Result {
        }

        /** The cooldown is still running; nothing was recorded. */
        record OnCooldown(Duration remaining) implements Result {
        }
    }

    private final List<Tier> tiers = new ArrayList<>();
    private final Duration defaultDuration;
    private final SQLiteDatabase database;
    private final Executor databaseExecutor;
    private final Clock clock;

    /**
     * @param config the {@code cooldowns} section of config.yml, or {@code null} if absent. Invalid
     *               tiers are ignored with a warning; without a valid {@code default} tier, 60 minutes
     *               is used.
     */
    public CooldownService(ConfigurationSection config, Logger logger, SQLiteDatabase database,
                           Executor databaseExecutor, Clock clock) {
        this.database = database;
        this.databaseExecutor = databaseExecutor;
        this.clock = clock;

        Duration configuredDefault = null;
        for (String name : config == null ? List.<String>of() : config.getKeys(false)) {
            String permission = config.getString(name + ".permission");
            Object minutes = config.get(name + ".duration-minutes");
            if (permission == null || permission.isBlank() || !(minutes instanceof Integer value) || value < 0) {
                logger.warning("Ignoring 'cooldowns." + name + "' in config.yml: it needs a 'permission' and a"
                        + " whole number of 'duration-minutes' (0 or more).");
                continue;
            }
            tiers.add(new Tier(permission, Duration.ofMinutes(value)));
            if (name.equals(DEFAULT_TIER)) {
                configuredDefault = Duration.ofMinutes(value);
            }
        }
        if (configuredDefault == null) {
            logger.warning("No valid 'cooldowns.default' in config.yml, using " + FALLBACK_DURATION.toMinutes()
                    + " minutes.");
            configuredDefault = FALLBACK_DURATION;
        }
        defaultDuration = configuredDefault;
    }

    /** Shortest cooldown among the tiers the player has the permission for, otherwise the default one. */
    public Duration cooldownFor(Predicate<String> hasPermission) {
        return tiers.stream()
                .filter(tier -> hasPermission.test(tier.permission()))
                .map(Tier::duration)
                .min(Duration::compareTo)
                .orElse(defaultDuration);
    }

    /** Records a use now if the cooldown is over. Fails with the {@link SQLException} on database errors. */
    public CompletableFuture<Result> tryUse(UUID playerId, Duration cooldown) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Instant now = clock.instant();
                Optional<Instant> lastUse = database.findLastUse(playerId);
                if (lastUse.isPresent()) {
                    Duration remaining = Duration.between(now, lastUse.get().plus(cooldown));
                    if (remaining.isPositive()) {
                        // Capped in case the system clock was moved back since the last use.
                        return new Result.OnCooldown(remaining.compareTo(cooldown) > 0 ? cooldown : remaining);
                    }
                }
                database.saveLastUse(playerId, now);
                return new Result.Granted(playerId, now, lastUse);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    /** Undoes a granted use that could not be completed, restoring the previous one if any. */
    public CompletableFuture<Void> cancel(Result.Granted granted) {
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
}
