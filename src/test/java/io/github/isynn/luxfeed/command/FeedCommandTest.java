package io.github.isynn.luxfeed.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isynn.luxfeed.cooldown.CooldownService;
import io.github.isynn.luxfeed.database.SQLiteDatabase;
import io.github.isynn.luxfeed.messages.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs /feed against a real SQLite file; database and main-thread tasks run immediately. */
class FeedCommandTest {

    private static final UUID PLAYER_ID = UUID.fromString("2f2a9c4e-1b7d-4c55-9a3f-0c1d2e3f4a5b");

    @TempDir
    Path folder;

    private final MessageService messages = new MessageService("en", new File("no-editable-files"),
            FeedCommandTest.class.getClassLoader()::getResourceAsStream, Logger.getAnonymousLogger());
    private final Player player = mock(Player.class);
    private SQLiteDatabase database;
    private boolean inCombat;
    private FeedCommand command;

    @BeforeEach
    void setUp() {
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(FeedCommand.PERMISSION)).thenReturn(true);

        database = new SQLiteDatabase(folder.resolve("luxfeed.db"));
        CooldownService cooldowns = new CooldownService(null, Logger.getAnonymousLogger(), database, Runnable::run,
                Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC));
        command = new FeedCommand(messages, checked -> inCombat, cooldowns, Runnable::run,
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() throws SQLException {
        database.close();
    }

    @Test
    void consoleIsRefused() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        command.execute(sourceOf(console), new String[0]);

        verify(console).sendMessage(messages.get("players-only"));
    }

    @Test
    void playerInCombatIsRefusedAndNoCooldownIsRecorded() throws SQLException {
        inCombat = true;

        command.execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(messages.get("feed.in-combat"));
        verify(player, never()).setFoodLevel(anyInt());
        assertEquals(Optional.empty(), database.findLastUse(PLAYER_ID));
    }

    @Test
    void feedsThePlayerThenRefusesDuringTheCooldown() {
        command.execute(sourceOf(player), new String[0]);
        command.execute(sourceOf(player), new String[0]);

        verify(player, times(1)).setFoodLevel(20);
        verify(player, times(1)).setSaturation(20.0F);
        verify(player).sendMessage(messages.get("feed.success"));
        verify(player).sendMessage(messages.get("feed.cooldown",
                Placeholder.component("remaining", messages.duration(CooldownService.FALLBACK_DURATION))));
    }

    private static CommandSourceStack sourceOf(CommandSender sender) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }
}
