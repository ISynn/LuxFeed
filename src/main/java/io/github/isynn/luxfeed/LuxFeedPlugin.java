package io.github.isynn.luxfeed;

import io.github.isynn.luxfeed.messages.MessageService;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuxFeedPlugin extends JavaPlugin {

    private MessageService messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultLanguageFiles();
        messages = MessageService.load(getConfig().getString("language"),
                getDataFolder().toPath().resolve("lang"), this::getResource, getLogger());
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
