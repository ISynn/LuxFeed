package io.github.isynn.luxfeed.command;

import io.github.isynn.luxfeed.messages.Message;
import io.github.isynn.luxfeed.messages.MessageService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /feed}: fully restores the hunger and saturation of the player running it.
 */
public final class FeedCommand implements BasicCommand {

    public static final String PERMISSION = "luxfeed.feed";

    private static final int MAX_FOOD_LEVEL = 20;
    private static final float MAX_SATURATION = 20.0F;

    private final MessageService messages;

    public FeedCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get(Message.PLAYERS_ONLY));
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(messages.get(Message.NO_PERMISSION));
            return;
        }

        feed(player);
        player.sendMessage(messages.get(Message.FEED_SUCCESS));
    }

    private static void feed(Player player) {
        player.setFoodLevel(MAX_FOOD_LEVEL);
        player.setSaturation(MAX_SATURATION);
        player.setExhaustion(0.0F);
    }
}
