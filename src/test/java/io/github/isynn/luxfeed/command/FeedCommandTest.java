package io.github.isynn.luxfeed.command;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isynn.luxfeed.messages.Message;
import io.github.isynn.luxfeed.messages.MessageService;
import io.github.isynn.luxfeed.messages.TestMessages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeedCommandTest {

    private final MessageService messages = TestMessages.bundled("en");
    private final Player player = mock(Player.class);
    private boolean inCombat;
    private FeedCommand command;

    @BeforeEach
    void setUp() {
        when(player.hasPermission(FeedCommand.PERMISSION)).thenReturn(true);
        command = new FeedCommand(messages, checkedPlayer -> inCombat);
    }

    @Test
    void consoleIsToldTheCommandIsForPlayersOnly() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        command.execute(sourceOf(console), new String[0]);

        verify(console).sendMessage(messages.get(Message.PLAYERS_ONLY));
    }

    @Test
    void playerWithoutPermissionIsRefused() {
        when(player.hasPermission(FeedCommand.PERMISSION)).thenReturn(false);

        command.execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(messages.get(Message.NO_PERMISSION));
        verifyNotFed();
    }

    @Test
    void playerInCombatIsRefused() {
        inCombat = true;

        command.execute(sourceOf(player), new String[0]);

        verify(player).sendMessage(messages.get(Message.FEED_IN_COMBAT));
        verifyNotFed();
    }

    @Test
    void restoresHungerAndSaturation() {
        command.execute(sourceOf(player), new String[0]);

        verify(player).setFoodLevel(20);
        verify(player).setSaturation(20.0F);
        verify(player).sendMessage(messages.get(Message.FEED_SUCCESS));
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
