package io.github.isynn.luxfeed.command;

import io.github.isynn.luxfeed.combat.CombatCheck;
import io.github.isynn.luxfeed.cooldown.CooldownService;
import io.github.isynn.luxfeed.cooldown.UseAttempt;
import io.github.isynn.luxfeed.messages.Message;
import io.github.isynn.luxfeed.messages.MessageService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /feed}: fully restores the hunger and saturation of the player running it.
 *
 * <p>Permission and combat are checked on the main thread. The cooldown is then checked and the use
 * recorded on the database thread, and the player is fed back on the main thread only once the use
 * has been stored.
 */
public final class FeedCommand implements BasicCommand {

    public static final String PERMISSION = "luxfeed.feed";

    private static final int MAX_FOOD_LEVEL = 20;
    private static final float MAX_SATURATION = 20.0F;

    private final MessageService messages;
    private final CombatCheck combatCheck;
    private final CooldownService cooldowns;
    private final Executor mainThread;
    private final Logger logger;

    public FeedCommand(MessageService messages, CombatCheck combatCheck, CooldownService cooldowns,
                       Executor mainThread, Logger logger) {
        this.messages = messages;
        this.combatCheck = combatCheck;
        this.cooldowns = cooldowns;
        this.mainThread = mainThread;
        this.logger = logger;
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
        if (combatCheck.isInCombat(player)) {
            player.sendMessage(messages.get(Message.FEED_IN_COMBAT));
            return;
        }

        Duration cooldown = cooldowns.cooldownFor(player::hasPermission);
        cooldowns.tryUse(player.getUniqueId(), cooldown)
                .whenCompleteAsync((attempt, failure) -> complete(player, attempt, failure), mainThread);
    }

    private void complete(Player player, UseAttempt attempt, Throwable failure) {
        if (failure != null) {
            logger.log(Level.SEVERE, "Could not check or record the /feed cooldown of " + player.getName()
                    + "; the player was not fed.", failure);
            player.sendMessage(messages.get(Message.FEED_ERROR));
            return;
        }

        switch (attempt) {
            case UseAttempt.OnCooldown(Duration remaining) -> player.sendMessage(messages.get(
                    Message.FEED_COOLDOWN, Placeholder.component("remaining", messages.duration(remaining))));
            case UseAttempt.Granted granted -> {
                // The player may have left or entered combat while the database was being updated.
                if (!player.isOnline()) {
                    cancel(granted, player);
                    return;
                }
                if (combatCheck.isInCombat(player)) {
                    cancel(granted, player);
                    player.sendMessage(messages.get(Message.FEED_IN_COMBAT));
                    return;
                }
                feed(player);
                player.sendMessage(messages.get(Message.FEED_SUCCESS));
            }
        }
    }

    private void cancel(UseAttempt.Granted granted, Player player) {
        cooldowns.cancel(granted).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not cancel the /feed use recorded for " + player.getName()
                    + "; their cooldown runs although they were not fed.", failure);
            return null;
        });
    }

    private static void feed(Player player) {
        player.setFoodLevel(MAX_FOOD_LEVEL);
        player.setSaturation(MAX_SATURATION);
        player.setExhaustion(0.0F);
    }
}
