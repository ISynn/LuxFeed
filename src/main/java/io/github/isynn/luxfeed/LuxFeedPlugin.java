package io.github.isynn.luxfeed;

import io.github.isynn.luxfeed.combat.CombatCheck;
import io.github.isynn.luxfeed.combat.StubCombatCheck;
import io.github.isynn.luxfeed.command.FeedCommand;
import io.github.isynn.luxfeed.cooldown.CooldownService;
import io.github.isynn.luxfeed.database.SQLiteDatabase;
import io.github.isynn.luxfeed.messages.MessageService;
import java.io.File;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuxFeedPlugin extends JavaPlugin {

    private ExecutorService databaseExecutor;
    private SQLiteDatabase database;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String language : MessageService.LANGUAGES) {
            if (!new File(getDataFolder(), MessageService.resourcePath(language)).exists()) {
                saveResource(MessageService.resourcePath(language), false);
            }
        }
        MessageService messages = new MessageService(getConfig().getString("language"),
                new File(getDataFolder(), "lang"), this::getResource, getLogger());

        // A single thread runs every database task, one after another (see CooldownService).
        databaseExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("LuxFeed-Database").daemon().factory());
        database = new SQLiteDatabase(getDataFolder().toPath().resolve("luxfeed.db"));
        databaseExecutor.execute(() -> {
            try {
                database.connect();
            } catch (SQLException exception) {
                getLogger().log(Level.SEVERE, "Could not open luxfeed.db; /feed will report an error.", exception);
            }
        });
        CooldownService cooldowns = new CooldownService(getConfig().getConfigurationSection("cooldowns"),
                getLogger(), database, databaseExecutor, Clock.systemUTC());

        // No combat system is connected yet: replace this with an adapter to the network's combat plugin.
        CombatCheck combatCheck = new StubCombatCheck();

        registerCommand("feed", new FeedCommand(messages, combatCheck, cooldowns,
                getServer().getScheduler().getMainThreadExecutor(this), getLogger()));
    }

    @Override
    public void onDisable() {
        if (databaseExecutor == null) {
            return;
        }
        databaseExecutor.shutdown();
        try {
            databaseExecutor.awaitTermination(10, TimeUnit.SECONDS);
            database.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (SQLException exception) {
            getLogger().log(Level.WARNING, "Could not close luxfeed.db cleanly.", exception);
        }
    }
}
