package io.github.isynn.luxfeed.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.isynn.luxfeed.database.SQLiteDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownServiceTest {

    private static final UUID PLAYER = UUID.fromString("2f2a9c4e-1b7d-4c55-9a3f-0c1d2e3f4a5b");
    private static final Instant START = Instant.parse("2026-10-30T12:00:00Z");
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final String CONFIG = """
            default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
            vip: {permission: "luxfeed.cooldown.vip", duration-minutes: 30}
            premium: {permission: "luxfeed.cooldown.premium", duration-minutes: 15}
            """;

    @TempDir
    Path folder;

    private SQLiteDatabase database;

    @AfterEach
    void closeDatabase() throws SQLException {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void defaultCooldownAppliesWithoutTierPermission() {
        assertEquals(ONE_HOUR, service(CONFIG, START).cooldownFor(Set.<String>of()::contains));
    }

    @Test
    void shortestCooldownWinsWhenSeveralPermissionsMatch() {
        Set<String> permissions =
                Set.of("luxfeed.cooldown.default", "luxfeed.cooldown.vip", "luxfeed.cooldown.premium");

        assertEquals(Duration.ofMinutes(15), service(CONFIG, START).cooldownFor(permissions::contains));
    }

    @Test
    void invalidTiersAreIgnoredAndInvalidDefaultFallsBackToSixtyMinutes() {
        CooldownService service = service("""
                default: {permission: "luxfeed.cooldown.default", duration-minutes: -10}
                vip: {permission: "luxfeed.cooldown.vip", duration-minutes: "abc"}
                """, START);

        assertEquals(CooldownService.FALLBACK_DURATION, service.cooldownFor(Set.of("luxfeed.cooldown.vip")::contains));
    }

    @Test
    void firstUseIsGrantedAndStored() throws SQLException {
        CooldownService.Result result = service(CONFIG, START).tryUse(PLAYER, ONE_HOUR).join();

        assertEquals(new CooldownService.Result.Granted(PLAYER, START, Optional.empty()), result);
        assertEquals(Optional.of(START), database.findLastUse(PLAYER));
    }

    @Test
    void useBeforeExpiryIsRefusedWithTheRemainingTime() {
        service(CONFIG, START).tryUse(PLAYER, ONE_HOUR).join();
        CooldownService later = service(CONFIG, START.plus(Duration.ofMinutes(20)));

        CooldownService.Result result = later.tryUse(PLAYER, ONE_HOUR).join();

        assertEquals(new CooldownService.Result.OnCooldown(Duration.ofMinutes(40)), result);
    }

    @Test
    void useIsGrantedOnceTheCooldownHasExpired() {
        service(CONFIG, START).tryUse(PLAYER, ONE_HOUR).join();

        CooldownService.Result result = service(CONFIG, START.plus(ONE_HOUR)).tryUse(PLAYER, ONE_HOUR).join();

        assertInstanceOf(CooldownService.Result.Granted.class, result);
    }

    @Test
    void simultaneousAttemptsGrantASingleUse() {
        ExecutorService databaseThread = Executors.newSingleThreadExecutor();
        try {
            CooldownService service = new CooldownService(section(CONFIG), Logger.getAnonymousLogger(),
                    database(), databaseThread, Clock.fixed(START, ZoneOffset.UTC));

            List<CompletableFuture<CooldownService.Result>> attempts = IntStream.range(0, 20)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> service.tryUse(PLAYER, ONE_HOUR).join()))
                    .toList();

            long granted = attempts.stream().map(CompletableFuture::join)
                    .filter(CooldownService.Result.Granted.class::isInstance).count();
            assertEquals(1, granted);
        } finally {
            databaseThread.shutdownNow();
        }
    }

    @Test
    void databaseErrorFailsTheAttempt() throws Exception {
        database = new SQLiteDatabase(Files.createDirectory(folder.resolve("not-a-file.db")));
        CooldownService service = new CooldownService(section(CONFIG), Logger.getAnonymousLogger(), database,
                Runnable::run, Clock.fixed(START, ZoneOffset.UTC));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> service.tryUse(PLAYER, ONE_HOUR).join());
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    /** Service reading and writing the same test database, with its clock stopped at {@code now}. */
    private CooldownService service(String config, Instant now) {
        return new CooldownService(section(config), Logger.getAnonymousLogger(), database(), Runnable::run,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private SQLiteDatabase database() {
        if (database == null) {
            database = new SQLiteDatabase(folder.resolve("luxfeed.db"));
        }
        return database;
    }

    private static YamlConfiguration section(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException(exception);
        }
        return configuration;
    }
}
