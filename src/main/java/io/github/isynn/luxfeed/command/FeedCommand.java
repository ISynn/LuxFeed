package io.github.isynn.luxfeed.command;

import io.github.isynn.luxfeed.combat.CombatCheck;
import io.github.isynn.luxfeed.cooldown.CooldownService;
import io.github.isynn.luxfeed.messages.MessageService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * {@code /feed}: fully restores the hunger and saturation of the player running it.
 *
 * <p>Permission and combat are checked on the main thread, the cooldown is checked and the use recorded
 * on the database thread, and the player is fed back on the main thread once the use is stored.
 */
public final class FeedCommand implements BasicCommand {

    public static final String PERMISSION = "luxfeed.feed";

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
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only"));
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(messages.get("no-permission"));
            return;
        }
        if (combatCheck.isInCombat(player)) {
            player.sendMessage(messages.get("feed.in-combat"));
            return;
        }

        Duration cooldown = cooldowns.cooldownFor(player::hasPermission);
        cooldowns.tryUse(player.getUniqueId(), cooldown)
                .whenCompleteAsync((result, failure) -> complete(player, result, failure), mainThread);
    }

    private void complete(Player player, CooldownService.Result result, Throwable failure) {
        if (failure != null) {
            logger.log(Level.SEVERE, "Could not check or record the /feed cooldown of " + player.getName(), failure);
            player.sendMessage(messages.get("feed.error"));
            return;
        }

        switch (result) {
            case CooldownService.Result.OnCooldown(Duration remaining) -> player.sendMessage(
                    messages.get("feed.cooldown", Placeholder.component("remaining", messages.duration(remaining))));
            case CooldownService.Result.Granted granted -> {
                // The player may have left or entered combat while the database was being updated.
                boolean online = player.isOnline();
                if (!online || combatCheck.isInCombat(player)) {
                    cooldowns.cancel(granted).exceptionally(error -> {
                        logger.log(Level.SEVERE, "Could not cancel the /feed use of " + player.getName(), error);
                        return null;
                    });
                    if (online) {
                        player.sendMessage(messages.get("feed.in-combat"));
                    }
                    return;
                }
                player.setFoodLevel(20);
                player.setSaturation(20.0F);
                player.setExhaustion(0.0F);
                player.sendMessage(messages.get("feed.success"));
            }
        }
    }
}
