package io.github.isynn.luxfeed.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.isynn.luxfeed.database.SQLiteDatabase;
import io.github.isynn.luxfeed.testing.LogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownServiceTest {

    private static final UUID PLAYER = UUID.fromString("2f2a9c4e-1b7d-4c55-9a3f-0c1d2e3f4a5b");
    private static final Duration ONE_HOUR = Duration.ofMinutes(60);
    private static final Instant START = Instant.parse("2026-10-30T12:00:00Z");

    @TempDir
    Path folder;

    private final MutableClock clock = new MutableClock(START);
    private ExecutorService databaseExecutor;
    private SQLiteDatabase database;
    private CooldownService service;

    @BeforeEach
    void setUp() {
        databaseExecutor = Executors.newSingleThreadExecutor();
        database = new SQLiteDatabase(folder.resolve("luxfeed.db"));
        service = newService(database);
    }

    @AfterEach
    void tearDown() throws Exception {
        databaseExecutor.shutdown();
        databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
        database.close();
    }

    @Test
    void firstUseIsGrantedAndRecorded() throws SQLException {
        UseAttempt attempt = service.tryUse(PLAYER, ONE_HOUR).join();

        assertEquals(new UseAttempt.Granted(PLAYER, START, Optional.empty()), attempt);
        assertEquals(Optional.of(START), database.findLastUse(PLAYER));
    }

    @Test
    void useBeforeExpiryIsRefusedWithTheRemainingTimeAndNotRecorded() throws SQLException {
        service.tryUse(PLAYER, ONE_HOUR).join();
        clock.advance(Duration.ofMinutes(20));

        UseAttempt attempt = service.tryUse(PLAYER, ONE_HOUR).join();

        assertEquals(new UseAttempt.OnCooldown(Duration.ofMinutes(40)), attempt);
        assertEquals(Optional.of(START), database.findLastUse(PLAYER));
    }

    @Test
    void useOneMillisecondBeforeExpiryIsRefused() {
        service.tryUse(PLAYER, ONE_HOUR).join();
        clock.advance(ONE_HOUR.minusMillis(1));

        assertEquals(new UseAttempt.OnCooldown(Duration.ofMillis(1)), service.tryUse(PLAYER, ONE_HOUR).join());
    }

    @Test
    void useIsGrantedOnceTheCooldownHasExpired() {
        service.tryUse(PLAYER, ONE_HOUR).join();
        clock.advance(ONE_HOUR);

        UseAttempt attempt = service.tryUse(PLAYER, ONE_HOUR).join();

        assertEquals(new UseAttempt.Granted(PLAYER, START.plus(ONE_HOUR), Optional.of(START)), attempt);
    }

    @Test
    void zeroCooldownNeverRefuses() {
        service.tryUse(PLAYER, Duration.ZERO).join();

        assertInstanceOf(UseAttempt.Granted.class, service.tryUse(PLAYER, Duration.ZERO).join());
    }

    @Test
    void cooldownSurvivesARestart() throws SQLException {
        service.tryUse(PLAYER, ONE_HOUR).join();
        database.close();
        clock.advance(Duration.ofMinutes(10));

        try (SQLiteDatabase reopened = new SQLiteDatabase(folder.resolve("luxfeed.db"))) {
            UseAttempt attempt = newService(reopened).tryUse(PLAYER, ONE_HOUR).join();

            assertEquals(new UseAttempt.OnCooldown(Duration.ofMinutes(50)), attempt);
        }
    }

    @Test
    void cancellingAFirstUseRemovesTheRecord() throws SQLException {
        UseAttempt.Granted granted = (UseAttempt.Granted) service.tryUse(PLAYER, ONE_HOUR).join();

        service.cancel(granted).join();

        assertEquals(Optional.empty(), database.findLastUse(PLAYER));
    }

    @Test
    void cancellingRestoresThePreviousUse() throws SQLException {
        service.tryUse(PLAYER, ONE_HOUR).join();
        clock.advance(Duration.ofHours(2));
        UseAttempt.Granted granted = (UseAttempt.Granted) service.tryUse(PLAYER, ONE_HOUR).join();

        service.cancel(granted).join();

        assertEquals(Optional.of(START), database.findLastUse(PLAYER));
    }

    @Test
    void simultaneousAttemptsGrantASingleUse() throws InterruptedException {
        int attempts = 16;
        ExecutorService callers = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<UseAttempt>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(CompletableFuture.supplyAsync(() -> {
                    awaitQuietly(start);
                    return service.tryUse(PLAYER, ONE_HOUR).join();
                }, callers));
            }
            start.countDown();

            long granted = results.stream()
                    .map(CompletableFuture::join)
                    .filter(UseAttempt.Granted.class::isInstance)
                    .count();
            assertEquals(1, granted);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void databaseFailureFailsTheAttempt() throws Exception {
        Path directory = Files.createDirectory(folder.resolve("unusable.db"));
        try (SQLiteDatabase broken = new SQLiteDatabase(directory)) {
            CompletableFuture<UseAttempt> attempt = newService(broken).tryUse(PLAYER, ONE_HOUR);

            CompletionException failure = assertThrows(CompletionException.class, attempt::join);
            assertInstanceOf(SQLException.class, failure.getCause());
        }
    }

    @Test
    void remainingTimeNeverExceedsTheCooldownWhenTheClockWentBack() {
        Instant lastUseInTheFuture = START.plus(Duration.ofHours(5));

        assertEquals(ONE_HOUR, CooldownService.remaining(lastUseInTheFuture, ONE_HOUR, START));
    }

    private CooldownService newService(SQLiteDatabase storage) {
        CooldownSettings settings = CooldownSettings.fromConfig(null, new LogCapture().logger());
        return new CooldownService(settings, storage, databaseExecutor, clock);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** A clock whose time only moves when the test says so. */
    private static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
