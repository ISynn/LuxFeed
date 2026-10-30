package io.github.isynn.luxfeed.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isynn.luxfeed.combat.CombatCheck;
import io.github.isynn.luxfeed.cooldown.CooldownService;
import io.github.isynn.luxfeed.cooldown.CooldownSettings;
import io.github.isynn.luxfeed.database.SQLiteDatabase;
import io.github.isynn.luxfeed.messages.Message;
import io.github.isynn.luxfeed.messages.MessageService;
import io.github.isynn.luxfeed.messages.TestMessages;
import io.github.isynn.luxfeed.testing.LogCapture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the command with a real SQLite file. Both executors run tasks immediately on the calling
 * thread, so each command completes before {@code execute} returns.
 */
class FeedCommandTest {

    private static final Executor IMMEDIATE = Runnable::run;
    private static final UUID PLAYER_ID = UUID.fromString("2f2a9c4e-1b7d-4c55-9a3f-0c1d2e3f4a5b");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @TempDir
    Path folder;

    private final MessageService messages = TestMessages.bundled("en");
    private final LogCapture log = new LogCapture();
    private final Player player = mock(Player.class);
    private boolean inCombat;
    private SQLiteDatabase database;

    @BeforeEach
    void setUp() {
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(FeedCommand.PERMISSION)).thenReturn(true);
        database = new SQLiteDatabase(folder.resolve("luxfeed.db"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        database.close();
    }

    @Test
    void consoleIsToldTheCommandIsForPlayersOnly() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        command().execute(sourceOf(console), new String[0]);

        verify(console).sendMessage(messages.get(Message.PLAYERS_ONLY));
    }

    @Test
    void playerWithoutPermissionIsRefused() throws SQLException {
        when(player.hasPermission(FeedCommand.PERMISSION)).thenReturn(false);

        command().execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(messages.get(Message.NO_PERMISSION));
        verifyNotFed();
        assertEquals(Optional.empty(), database.findLastUse(PLAYER_ID));
    }

    @Test
    void playerInCombatIsRefusedAndNoUseIsRecorded() throws SQLException {
        inCombat = true;

        command().execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(messages.get(Message.FEED_IN_COMBAT));
        verifyNotFed();
        assertEquals(Optional.empty(), database.findLastUse(PLAYER_ID));
    }

    @Test
    void restoresHungerAndSaturationAndRecordsTheUse() throws SQLException {
        command().execute(sourceOf(player), new String[0]);

        verify(player).setFoodLevel(20);
        verify(player).setSaturation(20.0F);
        verify(player).sendMessage(messages.get(Message.FEED_SUCCESS));
        assertEquals(Optional.of(NOW), database.findLastUse(PLAYER_ID));
    }

    @Test
    void secondUseDuringTheCooldownIsRefusedWithTheRemainingTime() {
        FeedCommand command = command();

        command.execute(sourceOf(player), new String[0]);
        command.execute(sourceOf(player), new String[0]);

        verify(player, times(1)).setFoodLevel(anyInt());
        verify(player).sendMessage(cooldownMessage(Duration.ofMinutes(60)));
    }

    @Test
    void cooldownPermissionShortensTheWait() {
        when(player.hasPermission("luxfeed.cooldown.vip")).thenReturn(true);
        FeedCommand command = command();

        command.execute(sourceOf(player), new String[0]);
        command.execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(cooldownMessage(Duration.ofMinutes(30)));
    }

    @Test
    void databaseErrorReportsAnErrorAndDoesNotFeed() throws SQLException {
        database.close();

        command().execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(messages.get(Message.FEED_ERROR));
        verifyNotFed();
        assertTrue(log.contains("Could not check or record the /feed cooldown of Steve"));
    }

    @Test
    void playerLeavingBeforeTheUseIsStoredIsNotFedAndKeepsNoCooldown() throws SQLException {
        when(player.isOnline()).thenReturn(false);

        command().execute(sourceOf(player), new String[0]);

        verifyNotFed();
        assertEquals(Optional.empty(), database.findLastUse(PLAYER_ID));
    }

    @Test
    void combatStartingWhileTheUseIsStoredCancelsIt() throws SQLException {
        AtomicInteger checks = new AtomicInteger();
        FeedCommand command = command(checked -> checks.incrementAndGet() > 1);

        command.execute(sourceOf(player), new String[0]);

        verifyNotFed();
        verify(player).sendMessage(messages.get(Message.FEED_IN_COMBAT));
        assertEquals(Optional.empty(), database.findLastUse(PLAYER_ID));
    }

    private FeedCommand command() {
        return command(checked -> inCombat);
    }

    private FeedCommand command(CombatCheck combatCheck) {
        CooldownService cooldowns = new CooldownService(exampleSettings(), database, IMMEDIATE,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new FeedCommand(messages, combatCheck, cooldowns, IMMEDIATE, log.logger());
    }

    private CooldownSettings exampleSettings() {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString("""
                    cooldowns:
                      default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
                      vip: {permission: "luxfeed.cooldown.vip", duration-minutes: 30}
                    """);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException(exception);
        }
        return CooldownSettings.fromConfig(configuration.getConfigurationSection("cooldowns"), log.logger());
    }

    private Component cooldownMessage(Duration remaining) {
        return messages.get(Message.FEED_COOLDOWN, Placeholder.component("remaining", messages.duration(remaining)));
    }

    private void verifyNotFed() {
        verify(player, never()).setFoodLevel(anyInt());
        verify(player, never()).setSaturation(anyFloat());
    }

    private static CommandSourceStack sourceOf(CommandSender sender) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }
}
