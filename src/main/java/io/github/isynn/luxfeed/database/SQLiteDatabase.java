package io.github.isynn.luxfeed.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.sqlite.SQLiteDataSource;

/**
 * Stores the time of each player's last successful {@code /feed} in a SQLite file.
 *
 * <p>All methods perform blocking I/O and must not be called from the server main thread. The
 * connection is opened lazily and reopened on the next call if opening it failed, so a temporary
 * problem (such as a locked file) does not require a restart.
 */
public final class SQLiteDatabase implements AutoCloseable {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS feed_usage (
                player_uuid  TEXT    NOT NULL PRIMARY KEY,
                last_used_at INTEGER NOT NULL
            )""";
    private static final String SELECT_LAST_USE = "SELECT last_used_at FROM feed_usage WHERE player_uuid = ?";
    private static final String UPSERT_LAST_USE = """
            INSERT INTO feed_usage (player_uuid, last_used_at) VALUES (?, ?)
            ON CONFLICT (player_uuid) DO UPDATE SET last_used_at = excluded.last_used_at""";
    private static final String DELETE_LAST_USE = "DELETE FROM feed_usage WHERE player_uuid = ?";

    private final Path file;
    private Connection connection;
    private boolean closed;

    public SQLiteDatabase(Path file) {
        this.file = file.toAbsolutePath();
    }

    /** Opens the connection and creates the table if needed; does nothing if already connected. */
    public synchronized void connect() throws SQLException {
        if (closed) {
            throw new SQLException("The database has been closed.");
        }
        if (connection != null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException exception) {
            throw new SQLException("Could not create the folder of " + file + ".", exception);
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + file);
        Connection opened = dataSource.getConnection();
        try (Statement statement = opened.createStatement()) {
            statement.executeUpdate(CREATE_TABLE);
        } catch (SQLException exception) {
            closeAfterFailure(opened, exception);
            throw exception;
        }
        connection = opened;
    }

    /** Time of the player's last successful use, if any. */
    public synchronized Optional<Instant> findLastUse(UUID playerId) throws SQLException {
        connect();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_LAST_USE)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(Instant.ofEpochMilli(result.getLong(1)))
                        : Optional.empty();
            }
        }
    }

    public synchronized void saveLastUse(UUID playerId, Instant usedAt) throws SQLException {
        connect();
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_LAST_USE)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, usedAt.toEpochMilli());
            statement.executeUpdate();
        }
    }

    public synchronized void deleteLastUse(UUID playerId) throws SQLException {
        connect();
        try (PreparedStatement statement = connection.prepareStatement(DELETE_LAST_USE)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    /** Closes the connection; later calls fail instead of reopening it. */
    @Override
    public synchronized void close() throws SQLException {
        closed = true;
        if (connection != null) {
            Connection current = connection;
            connection = null;
            current.close();
        }
    }

    private static void closeAfterFailure(Connection connection, SQLException cause) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            cause.addSuppressed(closeFailure);
        }
    }
}
