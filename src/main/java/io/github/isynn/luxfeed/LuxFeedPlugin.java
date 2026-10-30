package io.github.isynn.luxfeed;

import io.github.isynn.luxfeed.combat.CombatCheck;
import io.github.isynn.luxfeed.combat.StubCombatCheck;
import io.github.isynn.luxfeed.command.FeedCommand;
import io.github.isynn.luxfeed.messages.Message;
import io.github.isynn.luxfeed.messages.MessageService;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuxFeedPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultLanguageFiles();
        MessageService messages = MessageService.load(getConfig().getString("language"),
                getDataFolder().toPath().resolve("lang"), this::getResource, getLogger());

        // No combat system is connected yet: swap this for an adapter to the network's combat plugin.
        CombatCheck combatCheck = new StubCombatCheck();

        registerCommand("feed", messages.plain(Message.COMMAND_DESCRIPTION), new FeedCommand(messages, combatCheck));
    }

    @Override
    public void onDisable() {
    }

    /** Copies the bundled language files once so server owners can edit them; existing files are kept. */
    private void saveDefaultLanguageFiles() {
        for (String language : MessageService.SUPPORTED_LANGUAGES) {
            String path = MessageService.resourcePath(language);
            if (!new File(getDataFolder(), path).exists()) {
                saveResource(path, false);
            }
        }
    }
}
