package io.github.isynn.luxfeed.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @TempDir
    Path folder;

    @Test
    void timestampSurvivesReopeningTheDatabase() throws SQLException {
        Path file = folder.resolve("luxfeed.db");
        Instant usedAt = Instant.parse("2026-10-30T12:34:56.789Z");

        try (SQLiteDatabase database = new SQLiteDatabase(file)) {
            assertEquals(Optional.empty(), database.findLastUse(PLAYER));
            database.saveLastUse(PLAYER, Instant.parse("2026-10-30T10:00:00Z"));
            database.saveLastUse(PLAYER, usedAt);
        }

        try (SQLiteDatabase reopened = new SQLiteDatabase(file)) {
            assertEquals(Optional.of(usedAt), reopened.findLastUse(PLAYER));
            reopened.deleteLastUse(PLAYER);
            assertEquals(Optional.empty(), reopened.findLastUse(PLAYER));
        }
    }

    @Test
    void unusableFileFailsWithSqlException() throws Exception {
        try (SQLiteDatabase database = new SQLiteDatabase(Files.createDirectory(folder.resolve("dir.db")))) {
            assertThrows(SQLException.class, () -> database.saveLastUse(PLAYER, Instant.now()));
        }
    }
}
