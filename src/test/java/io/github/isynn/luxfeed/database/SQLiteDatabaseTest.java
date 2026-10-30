package io.github.isynn.luxfeed.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLiteDatabaseTest {

    private static final UUID PLAYER = UUID.fromString("2f2a9c4e-1b7d-4c55-9a3f-0c1d2e3f4a5b");
    private static final UUID OTHER_PLAYER = UUID.fromString("7b6a5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d");

    @TempDir
    Path folder;

    @Test
    void createsTheDatabaseFileAndFolder() throws SQLException {
        Path file = folder.resolve("nested").resolve("luxfeed.db");

        try (SQLiteDatabase database = new SQLiteDatabase(file)) {
            database.connect();
        }

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void playerWithoutRecordHasNoLastUse() throws SQLException {
        try (SQLiteDatabase database = new SQLiteDatabase(folder.resolve("luxfeed.db"))) {
            assertEquals(Optional.empty(), database.findLastUse(PLAYER));
        }
    }

    @Test
    void savedTimestampSurvivesReopeningTheDatabase() throws SQLException {
        Path file = folder.resolve("luxfeed.db");
        Instant usedAt = Instant.parse("2026-10-30T12:34:56.789Z");

        try (SQLiteDatabase database = new SQLiteDatabase(file)) {
            database.saveLastUse(PLAYER, usedAt);
        }

        try (SQLiteDatabase reopened = new SQLiteDatabase(file)) {
            assertEquals(Optional.of(usedAt), reopened.findLastUse(PLAYER));
            assertEquals(Optional.empty(), reopened.findLastUse(OTHER_PLAYER));
        }
    }

    @Test
    void savingAgainReplacesThePreviousTimestamp() throws SQLException {
        Instant later = Instant.parse("2026-10-30T13:00:00Z");

        try (SQLiteDatabase database = new SQLiteDatabase(folder.resolve("luxfeed.db"))) {
            database.saveLastUse(PLAYER, Instant.parse("2026-10-30T12:00:00Z"));
            database.saveLastUse(PLAYER, later);

            assertEquals(Optional.of(later), database.findLastUse(PLAYER));
        }
    }

    @Test
    void deleteRemovesOnlyThatPlayer() throws SQLException {
        Instant usedAt = Instant.parse("2026-10-30T12:00:00Z");

        try (SQLiteDatabase database = new SQLiteDatabase(folder.resolve("luxfeed.db"))) {
            database.saveLastUse(PLAYER, usedAt);
            database.saveLastUse(OTHER_PLAYER, usedAt);
            database.deleteLastUse(PLAYER);

            assertEquals(Optional.empty(), database.findLastUse(PLAYER));
            assertEquals(Optional.of(usedAt), database.findLastUse(OTHER_PLAYER));
        }
    }

    @Test
    void failsWithSqlExceptionWhenTheFileCannotBeOpened() throws IOException {
        Path directory = Files.createDirectory(folder.resolve("not-a-file.db"));

        try (SQLiteDatabase database = new SQLiteDatabase(directory)) {
            assertThrows(SQLException.class, () -> database.findLastUse(PLAYER));
            assertThrows(SQLException.class, () -> database.saveLastUse(PLAYER, Instant.now()));
        } catch (SQLException exception) {
            throw new AssertionError("Closing a never-opened database must not fail", exception);
        }
    }

    @Test
    void refusesOperationsAfterClose() throws SQLException {
        SQLiteDatabase database = new SQLiteDatabase(folder.resolve("luxfeed.db"));
        database.saveLastUse(PLAYER, Instant.now());
        database.close();

        assertThrows(SQLException.class, () -> database.findLastUse(PLAYER));
    }
}
